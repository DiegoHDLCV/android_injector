package com.example.format

import android.util.Log

/**
 * Objeto responsable de parsear un 'SerialMessage' genérico de Futurex
 * en un objeto de comando específico y fuertemente tipado.
 */
object FuturexCommandParser {

    private const val TAG = "FuturexCommandParser"

    // Clase de ayuda interna para leer el payload de forma segura sin gestionar cursores manualmente.
    private class PayloadReader(private val payload: String) {
        private var cursor = 0
        val remaining: Int get() = payload.length - cursor

        fun read(length: Int): String {
            if (cursor + length > payload.length) {
                throw IndexOutOfBoundsException("Intento de leer $length caracteres, pero solo quedan $remaining.")
            }
            val field = payload.substring(cursor, cursor + length)
            cursor += length
            return field
        }
    }

    /**
     * Función principal que recibe un mensaje y lo convierte al modelo de comando correcto.
     */
    fun parse(message: SerialMessage): FuturexCommand {
        val commandCode = message.command
        val dataPayload = message.fields.firstOrNull() ?: ""
        val fullPayload = commandCode + dataPayload // Reconstruir el payload completo

        return try {
            Log.d(TAG, "Parseando comando '$commandCode' con payload: $dataPayload")
            when (commandCode) {
                "02" -> parseInjectSymmetricKey(fullPayload)
                "03" -> parseReadSerial(fullPayload)
                "04" -> parseWriteSerial(fullPayload)
                else -> UnknownCommand(fullPayload, commandCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando payload para comando '$commandCode'", e)
            ParseError(fullPayload, e.message ?: "Error desconocido durante el parseo.")
        }
    }

    private fun parseReadSerial(fullPayload: String): ReadSerialCommand {
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir el código de comando '03'
        val version = reader.read(2)
        return ReadSerialCommand(rawPayload = fullPayload, version = version)
    }

    private fun parseWriteSerial(fullPayload: String): WriteSerialCommand {
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir '04'
        val version = reader.read(2)
        val serialNumber = reader.read(16)
        return WriteSerialCommand(rawPayload = fullPayload, version = version, serialNumber = serialNumber)
    }

    private fun parseInjectSymmetricKey(fullPayload: String): InjectSymmetricKeyCommand {
        val reader = PayloadReader(fullPayload)
        reader.read(2) // Omitir '02'

        val version = reader.read(2)
        val keySlot = reader.read(2).toInt()
        val ktkSlot = reader.read(2).toInt()
        val keyType = reader.read(2)
        val encryptionType = reader.read(2)
        val keyChecksum = reader.read(4)
        val ktkChecksum = reader.read(4)
        val ksn = reader.read(20)

        val keyLength = reader.read(3).toInt()
        val keyHex = reader.read(keyLength)

        val ktkHex: String? = if (encryptionType == "02") {
            val ktkLength = reader.read(3).toInt()
            reader.read(ktkLength)
        } else {
            null
        }

        return InjectSymmetricKeyCommand(
            rawPayload = fullPayload,
            version = version,
            keySlot = keySlot,
            ktkSlot = ktkSlot,
            keyType = keyType,
            encryptionType = encryptionType,
            keyChecksum = keyChecksum,
            ktkChecksum = ktkChecksum,
            ksn = ksn,
            keyHex = keyHex,
            ktkHex = ktkHex
        )
    }
}
