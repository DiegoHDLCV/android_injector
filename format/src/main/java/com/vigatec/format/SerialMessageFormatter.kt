package com.vigatec.format

import com.vigatec.utils.FormatUtils

object SerialMessageFormatter {

    private const val STX: Byte = 0x02
    private const val ETX: Byte = 0x03
    private const val SEPARATOR = '|'

    /**
     * Formatea un comando y sus datos en un ByteArray listo para enviar.
     * @param command El código de comando de respuesta (ej: "0710").
     * @param fields La lista de campos de datos para la respuesta.
     * @return Un ByteArray con el mensaje completo (STX...ETX + LRC).
     */
    fun format(command: String, fields: List<String>): ByteArray {
        if (command.length != 4) {
            throw IllegalArgumentException("El comando debe tener 4 caracteres.")
        }

        val dataString = fields.joinToString(SEPARATOR.toString())
        val contentString = "$command$SEPARATOR$dataString"
        val contentBytes = contentString.toByteArray(Charsets.US_ASCII)

        val etxByte = byteArrayOf(ETX)
        val bytesForLrc = contentBytes + etxByte
        val lrc = FormatUtils.calculateLrc(bytesForLrc)

        return byteArrayOf(STX) + contentBytes + etxByte + byteArrayOf(lrc)
    }

    /**
     * Sobrecarga para respuestas simples con un solo campo de datos.
     */
    fun format(command: String, singleField: String): ByteArray {
        return format(command, listOf(singleField))
    }

    /**
     * Sobrecarga para respuestas sin datos (solo comando y código de respuesta).
     * Por ejemplo, un ACK simple podría usar esto.
     */
    fun format(command: String): ByteArray {
        return format(command, emptyList())
    }
}