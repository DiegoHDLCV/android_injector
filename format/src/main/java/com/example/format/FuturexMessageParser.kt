// com/example/format/FuturexMessageParser.kt
package com.example.format

import android.util.Log
import com.example.format.base.IMessageParser
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

    override fun nextMessage(): SerialMessage? {
        while (buffer.isNotEmpty()) {
            val stxIndex = buffer.indexOf(STX)
            if (stxIndex == -1) {
                buffer.clear()
                return null
            }
            if (stxIndex > 0) {
                for (i in 0 until stxIndex) buffer.removeAt(0)
            }

            val etxIndex = buffer.indexOf(ETX)
            if (etxIndex == -1) return null

            if (buffer.size <= etxIndex + 1) return null

            val potentialMessageBytes = buffer.subList(0, etxIndex + 2).toByteArray()
            val payloadBytes = potentialMessageBytes.sliceArray(1 until etxIndex)
            val receivedLrc = potentialMessageBytes.last()

            // LRC en Futurex se calcula sobre el payload + ETX
            val bytesForLrc = buffer.subList(1, etxIndex + 1).toByteArray()
            val calculatedLrc = FormatUtils.calculateLrc(bytesForLrc)

            if (receivedLrc != calculatedLrc) {
                Log.e(TAG, "¡Error de LRC! Recibido: ${receivedLrc.toHexString()}, Calculado: ${calculatedLrc.toHexString()}. Descartando...")
                for (i in 0 until etxIndex + 2) buffer.removeAt(0)
                continue
            }

            // Parseo específico de Futurex
            return try {
                if (payloadBytes.isEmpty()) throw IllegalArgumentException("Payload de Futurex está vacío.")

                // Convención: Comando son los 2 primeros caracteres, el resto son datos.
                val payloadString = String(payloadBytes, Charsets.US_ASCII)
                val command = payloadString.substring(0, 2)
                val data = payloadString.substring(2)

                val parsedMessage = SerialMessage(command, listOf(data))
                Log.i(TAG, "Mensaje Futurex parseado: $parsedMessage")

                for (i in 0 until etxIndex + 2) buffer.removeAt(0)
                parsedMessage

            } catch (e: Exception) {
                Log.e(TAG, "Error al parsear payload Futurex: ${e.message}. Descartando...")
                for (i in 0 until etxIndex + 2) buffer.removeAt(0)
                continue
            }
        }
        return null
    }

    private fun Byte.toHexString(): String = "0x%02X".format(this)
    private fun ByteArray.toHexString(): String = joinToString(" ") { it.toHexString() }
}