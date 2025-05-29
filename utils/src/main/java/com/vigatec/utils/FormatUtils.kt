package com.vigatec.utils

object FormatUtils {
    /**
     * Calcula el LRC (XOR) para un array de bytes.
     * @param data Los bytes sobre los que calcular el LRC.
     * @return El byte resultante del LRC.
     */
    fun calculateLrc(data: ByteArray): Byte {
        var lrc: Byte = 0
        for (byte in data) {
            lrc = (lrc.toInt() xor byte.toInt()).toByte()
        }
        return lrc
    }
}