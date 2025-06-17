package com.example.format

import android.util.Log
import com.example.format.base.IMessageParser
import com.vigatec.utils.FormatUtils
import java.nio.charset.Charset

class FuturexMessageParser : IMessageParser {
    private val TAG = "FuturexMessageParser"
    private val buffer = mutableListOf<Byte>()

    companion object {
        const val STX: Byte = 0x02
        const val ETX: Byte = 0x03

        // Key Types que usan KSN según la Tabla 10 del manual de Futurex
        val KEY_TYPES_WITH_KSN = setOf("02", "03", "08", "0B", "10")
    }

    override fun appendData(newData: ByteArray) {
        buffer.addAll(newData.toList())
        Log.d(TAG, "Buffer actualizado (${buffer.size} bytes): ${buffer.toByteArray().toHexString()}")
    }

    override fun nextMessage(): FuturexMessage? {
        while (buffer.isNotEmpty()) {
            val (payloadBytes, frameSize) = findAndValidateFrame() ?: return null

            val fullPayload = String(payloadBytes, Charsets.US_ASCII)
            val commandCode = if (fullPayload.length >= 2) fullPayload.substring(0, 2) else ""

            val parsedMessage = try {
                when (commandCode) {
                    "02" -> {
                        if (fullPayload.length <= 8) parseInjectSymmetricKeyResponse(fullPayload)
                        else parseInjectSymmetricKeyCommand(fullPayload)
                    }
                    "00", "01" -> parseLegacyCommands(fullPayload, commandCode)
                    "03" -> parseReadSerialCommand(fullPayload)
                    "04" -> parseWriteSerialCommand(fullPayload)
                    "05" -> parseDeleteKeyCommand(fullPayload)
                    else -> UnknownCommand(fullPayload, commandCode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parseando payload para comando '$commandCode'", e)
                ParseError(fullPayload, e.message ?: "Error desconocido durante el parseo.")
            }

            repeat(frameSize) { buffer.removeAt(0) }
            return parsedMessage
        }
        return null
    }

    private fun findAndValidateFrame(): Pair<ByteArray, Int>? {
        val stxIndex = buffer.indexOf(STX)
        if (stxIndex == -1) {
            buffer.clear()
            return null
        }
        if (stxIndex > 0) {
            repeat(stxIndex) { buffer.removeAt(0) }
        }

        val etxIndex = buffer.indexOf(ETX)
        if (etxIndex == -1) return null

        if (buffer.size <= etxIndex + 1) return null

        val frameSize = etxIndex + 2
        val receivedLrc = buffer[etxIndex + 1]
        val bytesForLrc = buffer.subList(1, etxIndex + 1).toByteArray()
        val calculatedLrc = FormatUtils.calculateLrc(bytesForLrc)

        Log.d(TAG, "LRC recibido: ${receivedLrc.toInt() and 0xFF}, calculado: ${calculatedLrc.toInt() and 0xFF}")
        if (receivedLrc != calculatedLrc) {
            Log.e(TAG, "¡Error de LRC! Descartando marco inválido.")
            repeat(frameSize) { buffer.removeAt(0) }
            return null
        }

        val payloadBytes = buffer.subList(1, etxIndex).toByteArray()
        return Pair(payloadBytes, frameSize)
    }

    // --- PARSERS PARA CADA COMANDO ---

    private fun parseInjectSymmetricKeyCommand(fullPayload: String): InjectSymmetricKeyCommand {
        Log.d(TAG, "Parseando Comando Moderno de Inyección '02'")
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir Command "02"

        val version = reader.read(2).also { Log.d(TAG, "[FuturexParser] Version: '$it'") }
        val keySlot = reader.read(2).also { Log.d(TAG, "[FuturexParser] KeySlot: '$it'") }.toInt(16)
        val ktkSlot = reader.read(2).also { Log.d(TAG, "[FuturexParser] KtkSlot: '$it'") }.toInt(16)
        val keyType = reader.read(2).also { Log.d(TAG, "[FuturexParser] KeyType: '$it'") }
        val encryptionType = reader.read(2).also { Log.d(TAG, "[FuturexParser] EncryptionType: '$it'") }
        val keyChecksum = reader.read(4).also { Log.d(TAG, "[FuturexParser] KeyChecksum: '$it'") }
        val ktkChecksum = reader.read(4).also { Log.d(TAG, "[FuturexParser] KtkChecksum: '$it'") }

        // El campo KSN es opcional y depende del KeyType
        Log.d(TAG, "Leyendo campo KSN de 20 caracteres (obligatorio en comando 02)...")
        val ksn = reader.read(20).also { Log.d(TAG, "[FuturexParser] KSN: '$it'") }

        val keyLengthStr = reader.read(3)
        val keyLengthBytes = keyLengthStr.toInt(16)
        val keyLengthChars = keyLengthBytes * 2
        Log.d(TAG, "[FuturexParser] KeyLength: '$keyLengthStr' -> $keyLengthBytes bytes. Leyendo $keyLengthChars caracteres.")

        val keyHex = reader.read(keyLengthChars)
        Log.d(TAG, "[FuturexParser] KeyHex: ${keyHex.take(64)}...")

        // El KTK en claro solo existe si EncryptionType es "02"
        val ktkHex = if (encryptionType == "02") {
            Log.d(TAG, "EncryptionType es '02', parseando KTK en claro.")
            val ktkLengthStr = reader.read(3)
            val ktkLengthBytes = ktkLengthStr.toInt(16)
            val ktkLengthChars = ktkLengthBytes * 2
            reader.read(ktkLengthChars)
        } else {
            null
        }

        Log.d(TAG, "Parseo de comando '02' completado exitosamente.")
        return InjectSymmetricKeyCommand(fullPayload, version, keySlot, ktkSlot, keyType, encryptionType, keyChecksum, ktkChecksum, ksn, keyHex, ktkHex)
    }

    private fun parseLegacyCommands(fullPayload: String, commandCode: String): InjectSymmetricKeyCommand {
        Log.d(TAG, "Parseando Comando Legacy '$commandCode'")
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir código de comando

        val keySlot = reader.read(2).toInt(16)

        val ksn = if (commandCode == "00") reader.read(20) else "00000000000000000000"

        val keyHex = reader.readRemaining()

        return InjectSymmetricKeyCommand(
            rawPayload = fullPayload, version = "LG", keySlot = keySlot, ktkSlot = 0,
            keyType = if (commandCode == "00") "02" else "01", encryptionType = "00",
            keyChecksum = "0000", ktkChecksum = "0000", ksn = ksn, keyHex = keyHex, ktkHex = null
        )
    }

    private fun parseReadSerialCommand(fullPayload: String) = ReadSerialCommand(fullPayload, fullPayload.substring(2, 4))
    private fun parseWriteSerialCommand(fullPayload: String) = WriteSerialCommand(fullPayload, fullPayload.substring(2, 4), fullPayload.substring(4, 20))
    private fun parseDeleteKeyCommand(fullPayload: String) = DeleteKeyCommand(fullPayload, fullPayload.substring(2, 4))

    private fun parseInjectSymmetricKeyResponse(fullPayload: String): InjectSymmetricKeyResponse {
        val reader = PayloadReader(fullPayload)
        reader.read(2)
        val responseCode = reader.read(2)
        val keyChecksum = reader.read(4)
        return InjectSymmetricKeyResponse(fullPayload, responseCode, keyChecksum)
    }

    private class PayloadReader(private val payload: String) {
        private var cursor = 0
        fun read(length: Int): String {
            if (cursor + length > payload.length) {
                Log.e("PayloadReader", "Intento de leer $length chars desde la pos $cursor. Payload len: ${payload.length}")
                throw IndexOutOfBoundsException("Fin de payload inesperado.")
            }
            val field = payload.substring(cursor, cursor + length)
            cursor += length
            return field
        }

        fun readRemaining(): String {
            val field = payload.substring(cursor)
            cursor = payload.length
            return field
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "0x%02X".format(it) }
}