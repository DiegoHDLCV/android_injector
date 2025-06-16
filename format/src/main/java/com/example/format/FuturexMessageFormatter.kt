// com/example/format/FuturexMessageFormatter.kt
package com.example.format

import com.example.format.base.IMessageFormatter
import com.vigatec.utils.FormatUtils

object FuturexMessageFormatter : IMessageFormatter {

    private const val STX: Byte = 0x02
    private const val ETX: Byte = 0x03

    override fun format(command: String, fields: List<String>): ByteArray {
        // En Futurex, el payload es la concatenación directa de comando y datos.
        val payloadString = command + fields.joinToString("")
        val payloadBytes = payloadString.toByteArray(Charsets.US_ASCII)

        val etxByte = byteArrayOf(ETX)
        // LRC se calcula sobre el payload + ETX
        val bytesForLrc = payloadBytes + etxByte
        val lrc = FormatUtils.calculateLrc(bytesForLrc)

        return byteArrayOf(STX) + payloadBytes + etxByte + byteArrayOf(lrc)
    }

    override fun format(command: String, singleField: String): ByteArray {
        return format(command, listOf(singleField))
    }

    override fun format(command: String): ByteArray {
        return format(command, emptyList())
    }
}