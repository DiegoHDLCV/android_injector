package com.vigatec.utils

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object KcvCalculator {

    fun calculateKcv(keyHex: String): String {
        val keyBytes = hexStringToByteArray(keyHex)

        val algorithm: String
        val finalKeyBytes: ByteArray

        when (keyBytes.size) {
            8 -> { // DES
                algorithm = "DES/ECB/NoPadding"
                finalKeyBytes = keyBytes
            }
            16 -> { // 2-key 3DES
                algorithm = "DESede/ECB/NoPadding"
                finalKeyBytes = keyBytes + keyBytes.copyOfRange(0, 8)
            }
            24 -> { // 3-key 3DES
                algorithm = "DESede/ECB/NoPadding"
                finalKeyBytes = keyBytes
            }
            else -> throw IllegalArgumentException("Longitud de llave inválida: ${keyBytes.size}. Debe ser de 8, 16, o 24 bytes.")
        }

        val keySpec = SecretKeySpec(finalKeyBytes, algorithm.substringBefore('/'))
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)

        val data = ByteArray(8) { 0x00 }
        val encryptedData = cipher.doFinal(data)

        return encryptedData.copyOfRange(0, 3).toHexString()
    }

    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        require(len % 2 == 0) { "La cadena hexadecimal debe tener una longitud par." }
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }

    fun xorByteArrays(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) { "Los arrays de bytes deben tener la misma longitud para la operación XOR." }
        val result = ByteArray(a.size)
        for (i in a.indices) {
            result[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return result
    }
} 