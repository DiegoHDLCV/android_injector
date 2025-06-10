package com.example.format

import com.example.format.base.IMessageFormatter
import com.vigatec.utils.FormatUtils
import java.nio.charset.Charset

/**
 * Implementación del formateador para el protocolo "Legacy".
 * Construye un formato: <STX>COMMAND(4)|DATA...<ETX><LRC>
 */
object LegacyMessageFormatter : IMessageFormatter {

    private const val STX: Byte = 0x02
    private const val ETX: Byte = 0x03
    private const val SEPARATOR = '|'

    override fun format(command: String, fields: List<String>): ByteArray {
        if (command.length != 4) {
            throw IllegalArgumentException("El comando para el protocolo Legacy debe tener 4 caracteres.")
        }

        val dataString = fields.joinToString(SEPARATOR.toString())
        // Asegurarse de que el separador esté presente incluso si no hay datos
        val contentString = "$command$SEPARATOR$dataString"
        val contentBytes = contentString.toByteArray(Charsets.US_ASCII)

        val etxByte = byteArrayOf(ETX)
        val bytesForLrc = contentBytes + etxByte
        val lrc = FormatUtils.calculateLrc(bytesForLrc)

        return byteArrayOf(STX) + contentBytes + etxByte + byteArrayOf(lrc)
    }

    override fun format(command: String, singleField: String): ByteArray {
        return format(command, listOf(singleField))
    }

    override fun format(command: String): ByteArray {
        return format(command, emptyList())
    }
}
