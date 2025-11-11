package com.vigatec.format

import android.util.Log
import com.vigatec.format.base.IMessageParser
import com.vigatec.utils.FormatUtils
import java.nio.charset.Charset

/**
 * Implementación del parser para el protocolo "Legacy".
 * Espera un formato: <STX>COMMAND(4)|DATA...<ETX><LRC>
 */
class LegacyMessageParser : IMessageParser {

    private val TAG = "LegacyMessageParser"
    private val buffer = mutableListOf<Byte>()

    companion object {
        const val STX: Byte = 0x02
        const val ETX: Byte = 0x03
        const val SEPARATOR = '|'
    }

    override fun appendData(newData: ByteArray) {
        buffer.addAll(newData.toList())
        Log.d(TAG, "Buffer actualizado (${buffer.size} bytes): ${buffer.toByteArray().toHexString()}")
    }

    /**
     * CORREGIDO: Ahora devuelve el tipo 'ParsedMessage' para cumplir con la interfaz.
     */
    override fun nextMessage(): ParsedMessage? {
        while (buffer.isNotEmpty()) {
            val stxIndex = buffer.indexOf(STX)

            if (stxIndex == -1) {
                Log.w(TAG, "No se encontró STX, limpiando buffer.")
                buffer.clear()
                return null
            }

            if (stxIndex > 0) {
                Log.w(TAG, "Descartando $stxIndex bytes basura antes de STX.")
                for (i in 0 until stxIndex) {
                    buffer.removeAt(0)
                }
            }

            val etxIndex = buffer.indexOf(ETX)
            if (etxIndex == -1) {
                Log.d(TAG, "STX encontrado, pero no ETX. Esperando más datos.")
                return null
            }

            if (buffer.size <= etxIndex + 1) {
                Log.d(TAG, "ETX encontrado, pero falta LRC. Esperando más datos.")
                return null
            }

            val potentialMessageBytes = buffer.subList(0, etxIndex + 2).toByteArray()
            val messageContentBytes = potentialMessageBytes.sliceArray(1 until etxIndex)
            val receivedLrc = potentialMessageBytes[etxIndex + 1]

            val bytesForLrc = buffer.subList(1, etxIndex + 1).toByteArray()
            val calculatedLrc = FormatUtils.calculateLrc(bytesForLrc)

            if (receivedLrc != calculatedLrc) {
                Log.e(TAG, "¡Error de LRC! Recibido: ${receivedLrc.toHexString()}, Calculado: ${calculatedLrc.toHexString()}. Descartando...")
                for (i in 0 until etxIndex + 2) {
                    buffer.removeAt(0)
                }
                continue
            }

            return try {
                val contentString = String(messageContentBytes, Charsets.US_ASCII)
                val parts = contentString.split(SEPARATOR, limit = 2)

                if (parts.size < 2 || parts[0].length != 4) {
                    throw IllegalArgumentException("Formato inválido: Se esperaba COMMAND(4)|DATA. Recibido: '$contentString'")
                }

                val command = parts[0]
                val dataFields = if (parts[1].isNotEmpty()) parts[1].split(SEPARATOR) else emptyList()

                // --- CORRECCIÓN CLAVE: Usa el nuevo 'data class' LegacyMessage ---
                val parsedMessage = LegacyMessage(command, dataFields)
                Log.i(TAG, "Mensaje Legacy parseado exitosamente: $parsedMessage")

                for (i in 0 until etxIndex + 2) {
                    buffer.removeAt(0)
                }
                parsedMessage

            } catch (e: Exception) {
                Log.e(TAG, "Error al parsear contenido del mensaje Legacy: ${e.message}. Descartando...")
                for (i in 0 until etxIndex + 2) {
                    buffer.removeAt(0)
                }
                continue
            }
        }
        return null
    }

    private fun Byte.toHexString(): String = "0x%02X".format(this)
    private fun ByteArray.toHexString(): String = joinToString(" ") { it.toHexString() }
}
