package com.vigatec.format

import android.util.Log
import com.vigatec.format.base.IMessageParser
import com.vigatec.utils.FormatUtils



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

    override fun nextMessage(): FuturexMessage? {
        while (buffer.isNotEmpty()) {
            val (payloadBytes, frameSize) = findAndValidateFrame() ?: return null

            val fullPayload = String(payloadBytes, Charsets.US_ASCII)
            val commandCode = if (fullPayload.length >= 2) fullPayload.substring(0, 2) else ""

            val parsedMessage = try {
                when (commandCode) {
                    "02" -> {
                        // 🔍 MEJORADA HEURÍSTICA para diferenciar respuesta vs comando
                        //
                        // RESPUESTA (estructura corta):
                        //   02 + [errorCode 2 chars] + [checksum 4 chars] + [serial 16 chars opt] + [model opt]
                        //   Longitud típica: 8-50 caracteres
                        //
                        // COMANDO (estructura larga):
                        //   02 + [version 2] + [slot 2] + [ktkslot 2] + [type 2] + [encryption 2] +
                        //   [algorithm 2] + [subtype 2] + [checksum 4] + [ktkchk 4] + [ksn 20] + [keylen 3] + [keydata variable]
                        //   Longitud típica: 80+ caracteres (siempre mucho más largo)

                        val payloadLength = fullPayload.length
                        val potentialErrorCode = if (fullPayload.length >= 4) fullPayload.substring(2, 4) else ""
                        val isValidErrorCode = FuturexErrorCode.fromCode(potentialErrorCode) != null

                        // HEURÍSTICA MEJORADA:
                        // 1. Si longitud > 60: Es COMANDO (comandos siempre son largos)
                        // 2. Si longitud <= 60 Y tiene código error válido: Es RESPUESTA
                        // 3. Si longitud <= 60 Y NO tiene código error válido: Es COMANDO malformado
                        val isResponse = payloadLength <= 60 && isValidErrorCode

                        Log.d(TAG, "Diferenciador código 02: length=$payloadLength, errorCode='$potentialErrorCode', valid=$isValidErrorCode, isResponse=$isResponse")

                        if (isResponse) parseInjectSymmetricKeyResponse(fullPayload)
                        else parseInjectSymmetricKeyCommand(fullPayload)
                    }
                    "00", "01" -> parseLegacyCommands(fullPayload, commandCode)
                    "03" -> parseReadSerialCommand(fullPayload)
                    "04" -> parseWriteSerialCommand(fullPayload)
                    "05" -> parseDeleteKeyCommand(fullPayload) // Comando para borrado total
                    // --- INICIO: NUEVO CASO PARA BORRADO ESPECÍFICO ---
                    "06" -> parseDeleteSingleKeyCommand(fullPayload)
                    // --- FIN: NUEVO CASO PARA BORRADO ESPECÍFICO ---
                    // --- INICIO: NUEVO CASO PARA DESINSTALACIÓN DE APP ---
                    "07" -> {
                        // Heurística: Si longitud <= 60 es respuesta, si es mayor es comando
                        val payloadLength = fullPayload.length
                        val potentialErrorCode = if (fullPayload.length >= 4) fullPayload.substring(2, 4) else ""
                        val isValidErrorCode = FuturexErrorCode.fromCode(potentialErrorCode) != null
                        val isResponse = payloadLength <= 60 && isValidErrorCode

                        if (isResponse) parseUninstallAppResponse(fullPayload)
                        else parseUninstallAppCommand(fullPayload)
                    }
                    // --- FIN: NUEVO CASO PARA DESINSTALACIÓN DE APP ---
                    // --- INICIO: NUEVO CASO PARA VALIDACIÓN DE MARCA DE DISPOSITIVO ---
                    "08" -> {
                        // Heurística: Si longitud <= 20 es respuesta (08 + 2 chars + 2 chars), si es mayor es comando
                        val payloadLength = fullPayload.length
                        val potentialErrorCode = if (fullPayload.length >= 4) fullPayload.substring(2, 4) else ""
                        val isValidErrorCode = FuturexErrorCode.fromCode(potentialErrorCode) != null
                        val isResponse = payloadLength <= 20 && isValidErrorCode

                        if (isResponse) parseValidateDeviceBrandResponse(fullPayload)
                        else parseValidateDeviceBrandCommand(fullPayload)
                    }
                    // --- FIN: NUEVO CASO PARA VALIDACIÓN DE MARCA DE DISPOSITIVO ---
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
        
        // ⚠️ TEMPORALMENTE DESHABILITADO: Validación del LRC para debugging
        // TODO: Rehabilitar después de identificar el problema del LRC
        if (receivedLrc != calculatedLrc) {
            Log.w(TAG, "⚠️ LRC incorrecto detectado, pero continuando para debugging...")
            Log.w(TAG, "  - LRC recibido: 0x${receivedLrc.toString(16).uppercase()}")
            Log.w(TAG, "  - LRC calculado: 0x${calculatedLrc.toString(16).uppercase()}")
            Log.w(TAG, "  - Diferencia: 0x${(receivedLrc.toInt() xor calculatedLrc.toInt()).toString(16).uppercase()}")
            Log.w(TAG, "  - Continuando con el parseo del comando...")
        } else {
            Log.i(TAG, "✓ LRC válido")
        }

        val payloadBytes = buffer.subList(1, etxIndex).toByteArray()
        
        // Logs adicionales para debugging del LRC
        Log.i(TAG, "=== FRAME PARSEADO (LRC validación deshabilitada) ===")
        Log.i(TAG, "STX encontrado en índice: $stxIndex")
        Log.i(TAG, "ETX encontrado en índice: $etxIndex")
        Log.i(TAG, "Frame size: $frameSize")
        Log.i(TAG, "Payload bytes: ${payloadBytes.toHexString()}")
        Log.i(TAG, "Payload ASCII: ${String(payloadBytes, Charsets.US_ASCII)}")
        Log.i(TAG, "================================================")
        
        return Pair(payloadBytes, frameSize)
    }

    // --- PARSERS PARA CADA COMANDO ---

    private fun parseInjectSymmetricKeyCommand(fullPayload: String): InjectSymmetricKeyCommand {
        Log.i(TAG, "=== PARSEANDO COMANDO DE INYECCIÓN '02' ===")
        Log.i(TAG, "Payload completo: $fullPayload")
        Log.i(TAG, "Longitud del payload: ${fullPayload.length} caracteres")
        
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir Command "02"

        val version = reader.read(2).also { Log.i(TAG, "  - Versión: '$it'") }
        val keySlot = reader.read(2).also { Log.i(TAG, "  - KeySlot: '$it' (${it.toInt(10)})") }.toInt(10)
        val ktkSlot = reader.read(2).also { Log.i(TAG, "  - KtkSlot: '$it' (${it.toInt(10)})") }.toInt(10)
        val keyType = reader.read(2).also { Log.i(TAG, "  - KeyType: '$it'") }
        val encryptionType = reader.read(2).also { Log.i(TAG, "  - EncryptionType: '$it'") }
        val keyAlgorithm = reader.read(2).also { Log.i(TAG, "  - KeyAlgorithm: '$it'") }  // NUEVO CAMPO
        val keySubType = reader.read(2).also { Log.i(TAG, "  - KeySubType: '$it'") }  // NUEVO CAMPO
        val keyChecksum = reader.read(4).also { Log.i(TAG, "  - KeyChecksum: '$it'") }
        val ktkChecksum = reader.read(4).also { Log.i(TAG, "  - KtkChecksum: '$it'") }

        Log.i(TAG, "  - Leyendo KSN (20 caracteres)...")
        val ksn = reader.read(20).also { Log.i(TAG, "  - KSN: '$it'") }

        Log.i(TAG, "  - Leyendo longitud de llave (3 caracteres)...")
        val keyLengthStr = reader.read(3)
        val keyLengthBytes = keyLengthStr.toInt(16)
        val keyLengthChars = keyLengthBytes * 2
        Log.i(TAG, "  - KeyLength: '$keyLengthStr' -> $keyLengthBytes bytes -> $keyLengthChars caracteres")

        Log.i(TAG, "  - Leyendo datos de la llave ($keyLengthChars caracteres)...")
        val keyHex = reader.read(keyLengthChars)
        Log.i(TAG, "  - KeyHex: ${keyHex.take(64)}${if (keyHex.length > 64) "..." else ""}")

        // NOTA: Para encryptionType "02", la KTK ya fue enviada previamente con encryptionType "00"
        // NO se parsea KTK del payload
        val ktkHex: String? = null

        // NUEVO: Leer campos opcionales de información de inyección (totalKeys y currentKeyIndex)
        // Estos campos son opcionales para mantener compatibilidad con comandos antiguos
        var totalKeys = 0
        var currentKeyIndex = 0
        
        // Verificar si hay más datos después de keyHex (6 caracteres: 3 para totalKeys + 3 para currentKeyIndex)
        // Usar hasMore() para verificar sin lanzar excepción
        if (reader.hasMore(6)) {
            try {
                val totalKeysStr = reader.read(3)
                val currentKeyIndexStr = reader.read(3)
                totalKeys = totalKeysStr.toInt(10)
                currentKeyIndex = currentKeyIndexStr.toInt(10)
                Log.i(TAG, "  - TotalKeys: $totalKeys")
                Log.i(TAG, "  - CurrentKeyIndex: $currentKeyIndex")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error parseando campos opcionales de inyección: ${e.message}")
                // Mantener valores por defecto (0) si hay error
            }
        } else {
            Log.d(TAG, "  - Campos opcionales de inyección no presentes (comando antiguo)")
        }

        Log.i(TAG, "=== RESUMEN DEL COMANDO PARSEADO ===")
        Log.i(TAG, "  - Comando: 02 (Inyección de llave simétrica)")
        Log.i(TAG, "  - Versión: $version")
        Log.i(TAG, "  - KeySlot: $keySlot")
        Log.i(TAG, "  - KtkSlot: $ktkSlot")
        Log.i(TAG, "  - KeyType: $keyType")
        Log.i(TAG, "  - EncryptionType: $encryptionType")
        Log.i(TAG, "  - KeyAlgorithm: $keyAlgorithm")
        Log.i(TAG, "  - KeySubType: $keySubType")
        Log.i(TAG, "  - KeyChecksum: $keyChecksum")
        Log.i(TAG, "  - KtkChecksum: $ktkChecksum")
        Log.i(TAG, "  - KSN: $ksn")
        Log.i(TAG, "  - KeyLength: $keyLengthStr ($keyLengthBytes bytes)")
        Log.i(TAG, "  - KeyHex: ${keyHex.take(32)}...")
        if (totalKeys > 0) {
            Log.i(TAG, "  - TotalKeys: $totalKeys")
            Log.i(TAG, "  - CurrentKeyIndex: $currentKeyIndex (${if (currentKeyIndex == totalKeys) "ÚLTIMA LLAVE" else "Llave $currentKeyIndex de $totalKeys"})")
        }
        Log.i(TAG, "✓ Parseo de comando '02' completado exitosamente")
        Log.i(TAG, "================================================")
        
        return InjectSymmetricKeyCommand(fullPayload, version, keySlot, ktkSlot, keyType, encryptionType, keyAlgorithm, keySubType, keyChecksum, ktkChecksum, ksn, keyHex, ktkHex, totalKeys, currentKeyIndex)
    }

    private fun parseLegacyCommands(fullPayload: String, commandCode: String): InjectSymmetricKeyCommand {
        Log.d(TAG, "Parseando Comando Legacy '$commandCode'")
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir código de comando

        val keySlot = reader.read(2).toInt(10)

        val ksn = if (commandCode == "00") reader.read(20) else "00000000000000000000"

        val keyHex = reader.readRemaining()

        return InjectSymmetricKeyCommand(
            rawPayload = fullPayload, version = "LG", keySlot = keySlot, ktkSlot = 0,
            keyType = if (commandCode == "00") "02" else "01", encryptionType = "00",
            keyAlgorithm = "00",  // Legacy: default 3DES
            keySubType = "00",    // Legacy: default Generic/Master
            keyChecksum = "0000", ktkChecksum = "0000", ksn = ksn, keyHex = keyHex, ktkHex = null
        )
    }

    private fun parseReadSerialCommand(fullPayload: String) = ReadSerialCommand(fullPayload, fullPayload.substring(2, 4))
    private fun parseWriteSerialCommand(fullPayload: String) = WriteSerialCommand(fullPayload, fullPayload.substring(2, 4), fullPayload.substring(4, 20))
    private fun parseDeleteKeyCommand(fullPayload: String) = DeleteKeyCommand(fullPayload, fullPayload.substring(2, 4))

    // --- INICIO: NUEVA FUNCIÓN DE PARSEO ---
    private fun parseDeleteSingleKeyCommand(fullPayload: String): DeleteSingleKeyCommand {
        Log.d(TAG, "Parseando Comando Personalizado de Borrado Específico '06'")
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir Command "06"

        val version = reader.read(2)
        val keySlot = reader.read(2).toInt(10) // Lee el slot y lo convierte a Int (decimal)
        val keyTypeHex = reader.read(2) // Lee el tipo de llave como string HEX

        return DeleteSingleKeyCommand(fullPayload, version, keySlot, keyTypeHex)
    }
    // --- FIN: NUEVA FUNCIÓN DE PARSEO ---

    // --- INICIO: FUNCIONES DE PARSEO PARA DESINSTALACIÓN DE APP (COMANDO "07") ---
    private fun parseUninstallAppCommand(fullPayload: String): UninstallAppCommand {
        Log.d(TAG, "Parseando Comando de Desinstalación de App '07'")
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir Command "07"

        val version = reader.read(2)
        val confirmationToken = reader.readRemaining() // Token de confirmación

        Log.i(TAG, "Comando '07' parseado - Version: $version, ConfirmationToken: $confirmationToken")

        return UninstallAppCommand(fullPayload, version, confirmationToken)
    }

    private fun parseUninstallAppResponse(fullPayload: String): UninstallAppResponse {
        Log.d(TAG, "Parseando Respuesta de Desinstalación de App '07'")
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir comando "07"
        val responseCode = reader.read(2)

        // Leer información del dispositivo receptor si está disponible
        val deviceSerial = if (reader.hasMore(16)) reader.read(16) else ""
        val deviceModel = if (reader.hasMore()) reader.readRemaining() else ""

        Log.i(TAG, "Respuesta '07' parseada - Code: $responseCode, Serial: $deviceSerial, Model: $deviceModel")

        return UninstallAppResponse(fullPayload, responseCode, deviceSerial, deviceModel)
    }
    // --- FIN: FUNCIONES DE PARSEO PARA DESINSTALACIÓN DE APP ---

    // --- INICIO: FUNCIONES DE PARSEO PARA VALIDACIÓN DE MARCA DE DISPOSITIVO (COMANDO "08") ---
    private fun parseValidateDeviceBrandCommand(fullPayload: String): ValidateDeviceBrandCommand {
        Log.d(TAG, "Parseando Comando de Validación de Marca '08'")
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir Command "08"

        val version = reader.read(2)
        val expectedDeviceType = reader.read(2)

        Log.i(TAG, "Comando '08' parseado - Version: $version, ExpectedDeviceType: $expectedDeviceType")

        return ValidateDeviceBrandCommand(fullPayload, version, expectedDeviceType)
    }

    private fun parseValidateDeviceBrandResponse(fullPayload: String): ValidateDeviceBrandResponse {
        Log.d(TAG, "Parseando Respuesta de Validación de Marca '08'")
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir comando "08"
        val responseCode = reader.read(2)
        val actualDeviceType = if (reader.hasMore(2)) reader.read(2) else ""

        Log.i(TAG, "Respuesta '08' parseada - Code: $responseCode, ActualDeviceType: $actualDeviceType")

        return ValidateDeviceBrandResponse(fullPayload, responseCode, actualDeviceType)
    }
    // --- FIN: FUNCIONES DE PARSEO PARA VALIDACIÓN DE MARCA DE DISPOSITIVO ---

    private fun parseInjectSymmetricKeyResponse(fullPayload: String): InjectSymmetricKeyResponse {
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir comando "02"
        val responseCode = reader.read(2)
        val keyChecksum = reader.read(4)

        // NUEVO: Leer información del dispositivo receptor si está disponible (retrocompatibilidad)
        val deviceSerial = if (reader.hasMore(16)) reader.read(16) else ""
        val deviceModel = if (reader.hasMore()) reader.readRemaining() else ""

        Log.d(TAG, "Response parseado - Code: $responseCode, Checksum: $keyChecksum, Serial: $deviceSerial, Model: $deviceModel")

        return InjectSymmetricKeyResponse(fullPayload, responseCode, keyChecksum, deviceSerial, deviceModel)
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

        // NUEVO: Verificar si hay al menos N caracteres disponibles
        fun hasMore(length: Int = 1): Boolean {
            return cursor + length <= payload.length
        }
        
        // NUEVO: Obtener cantidad de caracteres restantes
        fun remaining(): Int {
            return payload.length - cursor
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "0x%02X".format(it) }
}
