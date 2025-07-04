package com.example.format

import android.util.Log
import com.vigatec.utils.FormatUtils

class SerialMessageParser {

    private val TAG = "SerialMessageParser"
    private val buffer = mutableListOf<Byte>()

    companion object {
        const val STX: Byte = 0x02
        const val ETX: Byte = 0x03
        const val SEPARATOR = '|'
    }

    /**
     * Añade nuevos datos recibidos al buffer interno.
     * @param newData Los bytes recién llegados del puerto serie.
     */
    fun appendData(newData: ByteArray) {
        buffer.addAll(newData.toList())
        Log.d(TAG, "Buffer actualizado (${buffer.size} bytes): ${buffer.toByteArray().toHexString()}")
    }

    /**
     * Intenta extraer el próximo mensaje completo y válido del buffer.
     * Si encuentra uno, lo devuelve y lo elimina del buffer.
     * Si no hay mensaje completo o es inválido, devuelve null.
     * @return Un [SerialMessage] o null.
     */
    fun nextMessage(): SerialMessage? {
        while (buffer.isNotEmpty()) {
            val stxIndex = buffer.indexOf(STX)

            // 1. Buscar STX
            if (stxIndex == -1) {
                Log.w(TAG, "No se encontró STX, limpiando buffer.")
                buffer.clear() // No hay inicio de mensaje, limpiar todo
                return null
            }

            // Descartar bytes basura antes del STX
            if (stxIndex > 0) {
                Log.w(TAG, "Descartando $stxIndex bytes antes de STX.")
                for (i in 0 until stxIndex) {
                    buffer.removeAt(0)
                }
            }

            // 2. Buscar ETX después de STX
            val etxIndex = buffer.indexOf(ETX)
            if (etxIndex == -1) {
                Log.d(TAG, "STX encontrado, pero no ETX. Esperando más datos.")
                return null // Mensaje incompleto
            }

            // 3. Verificar si tenemos el LRC (1 byte después de ETX)
            if (buffer.size <= etxIndex + 1) {
                Log.d(TAG, "ETX encontrado, pero falta LRC. Esperando más datos.")
                return null // Mensaje incompleto
            }

            // 4. Extraer el mensaje potencial (STX hasta LRC)
            val potentialMessageBytes = buffer.subList(0, etxIndex + 2).toByteArray()
            val messageContentBytes = potentialMessageBytes.sliceArray(1 until etxIndex) // Desde después de STX hasta antes de ETX
            val receivedLrc = potentialMessageBytes[etxIndex + 1] // El byte después de ETX

            // 5. Calcular y Verificar LRC
            // El LRC se calcula sobre [COMMAND + SEPARATOR + DATA + ETX]
            val bytesForLrc = buffer.subList(1, etxIndex + 1).toByteArray()
            val calculatedLrc = FormatUtils.calculateLrc(bytesForLrc)

            if (receivedLrc != calculatedLrc) {
                Log.e(TAG, "¡Error de LRC! Recibido: ${receivedLrc.toHexString()}, Calculado: ${calculatedLrc.toHexString()}. Mensaje: ${potentialMessageBytes.toHexString()}. Descartando...")
                // Eliminar el mensaje inválido (STX hasta LRC) y seguir buscando
                for (i in 0 until etxIndex + 2) {
                    buffer.removeAt(0)
                }
                continue // Vuelve a intentar parsear desde el principio del buffer
            }

            // 6. Si LRC es correcto, parsear Comando y Datos
            return try {
                val contentString = String(messageContentBytes, Charsets.US_ASCII)
                val parts = contentString.split(SEPARATOR, limit = 2) // Dividir en Comando y el resto

                if (parts.size < 2 || parts[0].length != 4) {
                    throw IllegalArgumentException("Formato inválido: Se esperaba COMMAND(4)|DATA. Recibido: '$contentString'")
                }

                val command = parts[0]
                val dataFields = parts[1].split(SEPARATOR) // Dividir los datos

                val parsedMessage = SerialMessage(command, dataFields)
                Log.i(TAG, "Mensaje parseado exitosamente: $parsedMessage")

                // Eliminar el mensaje parseado del buffer
                for (i in 0 until etxIndex + 2) {
                    buffer.removeAt(0)
                }
                parsedMessage // Devolver el mensaje

            } catch (e: Exception) {
                Log.e(TAG, "Error al parsear contenido del mensaje: ${e.message}. Mensaje: ${potentialMessageBytes.toHexString()}. Descartando...")
                // Eliminar el mensaje inválido y seguir buscando
                for (i in 0 until etxIndex + 2) {
                    buffer.removeAt(0)
                }
                continue // Vuelve a intentar parsear
            }
        }
        return null // No hay más datos o mensajes válidos en el buffer
    }

    // Funciones de ayuda para depuración (opcional)
    private fun Byte.toHexString(): String = "0x%02X".format(this)
    private fun ByteArray.toHexString(): String = joinToString(" ") { it.toHexString() }
}