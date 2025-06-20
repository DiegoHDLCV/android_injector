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
    }

    override fun appendData(newData: ByteArray) {
        buffer.addAll(newData.toList())
        Log.d(TAG, "Buffer actualizado (${buffer.size} bytes): ${buffer.toByteArray().toHexString()}")
    }

    override fun nextMessage(): FuturexMessage? { // Devuelve la interfaz base FuturexMessage
        while (buffer.isNotEmpty()) {
            val (payloadBytes, messageLength) = findAndValidateFrame() ?: continue

            val fullPayload = String(payloadBytes, Charsets.US_ASCII)
            val commandCode = if (fullPayload.length >= 2) fullPayload.substring(0, 2) else ""

            val parsedMessage = try {
                // Heurística para diferenciar comando de respuesta para el código "02"
                // El comando de inyección siempre es largo, la respuesta es corta.
                if (commandCode == "02" && fullPayload.length <= 8) {
                    parseInjectSymmetricKeyResponse(fullPayload)
                } else {
                    // Lógica para parsear comandos enviados al dispositivo
                    when (commandCode) {
                        "02" -> parseInjectSymmetricKeyCommand(fullPayload)
                        "03" -> parseReadSerialCommand(fullPayload)
                        "04" -> parseWriteSerialCommand(fullPayload)
                        else -> UnknownCommand(fullPayload, commandCode)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parseando payload para comando/respuesta '$commandCode'", e)
                ParseError(fullPayload, e.message ?: "Error desconocido durante el parseo.")
            }

            for (i in 0 until messageLength) buffer.removeAt(0)
            return parsedMessage
        }
        return null
    }

    private fun findAndValidateFrame(): Pair<ByteArray, Int>? {
        val stxIndex = buffer.indexOf(STX)
        if (stxIndex == -1) { buffer.clear(); return null }
        if (stxIndex > 0) { (0 until stxIndex).forEach { buffer.removeAt(0) } }

        val etxIndex = buffer.indexOf(ETX)
        if (etxIndex == -1) return null

        if (buffer.size <= etxIndex + 1) return null // Falta LRC

        val frameSize = etxIndex + 2
        val receivedLrc = buffer[etxIndex + 1]
        val bytesForLrc = buffer.subList(1, etxIndex + 1).toByteArray()
        val calculatedLrc = FormatUtils.calculateLrc(bytesForLrc)

        if (receivedLrc != calculatedLrc) {
            Log.e(TAG, "¡Error de LRC! Descartando marco inválido.")
            (0 until frameSize).forEach { buffer.removeAt(0) }
            return null
        }

        val payloadBytes = buffer.subList(1, etxIndex).toByteArray()
        return Pair(payloadBytes, frameSize)
    }

    // --- PARSERS DE COMANDOS (Host -> Dispositivo) ---

    private fun parseReadSerialCommand(fullPayload: String) = ReadSerialCommand(
        rawPayload = fullPayload,
        version = fullPayload.substring(2, 4)
    )

    private fun parseWriteSerialCommand(fullPayload: String) = WriteSerialCommand(
        rawPayload = fullPayload,
        version = fullPayload.substring(2, 4),
        serialNumber = fullPayload.substring(4, 20)
    )

    private fun parseInjectSymmetricKeyCommand(fullPayload: String): InjectSymmetricKeyCommand {
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir código de comando

        val version = reader.read(2)
        val keySlot = reader.read(2).toInt(16) // Hex a Int
        val ktkSlot = reader.read(2).toInt(16) // Hex a Int
        val keyType = reader.read(2)
        val encryptionType = reader.read(2)
        val keyChecksum = reader.read(4)
        val ktkChecksum = reader.read(4)
        val ksn = reader.read(20)
        val keyLength = reader.read(3).toInt() // Decimal a Int
        val keyHex = reader.read(keyLength)

        val ktkHex = if (encryptionType == "02") {
            val ktkLength = reader.read(3).toInt()
            reader.read(ktkLength)
        } else null

        return InjectSymmetricKeyCommand(fullPayload, version, keySlot, ktkSlot, keyType, encryptionType, keyChecksum, ktkChecksum, ksn, keyHex, ktkHex)
    }

    // --- PARSERS DE RESPUESTAS (Dispositivo -> Host) ---

    private fun parseInjectSymmetricKeyResponse(fullPayload: String): InjectSymmetricKeyResponse {
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir código de comando '02'

        val responseCode = reader.read(2)
        val keyChecksum = reader.read(4)

        return InjectSymmetricKeyResponse(
            rawPayload = fullPayload,
            responseCode = responseCode,
            keyChecksum = keyChecksum
        )
    }

    private class PayloadReader(private val payload: String) {
        private var cursor = 0
        fun read(length: Int): String {
            if (cursor + length > payload.length) throw IndexOutOfBoundsException("Fin de payload inesperado.")
            val field = payload.substring(cursor, cursor + length)
            cursor += length
            return field
        }
    }

    private fun Byte.toHexString(): String = "0x%02X".format(this)
    private fun ByteArray.toHexString(): String = joinToString(" ") { it.toHexString() }
}
