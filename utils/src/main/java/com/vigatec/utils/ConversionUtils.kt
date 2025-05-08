package com.vigatec.utils

object ConversionUtils {
     fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }
}