package com.vigatec.utils

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object KcvCalculator {

    val TAG = "KcvCalculator"

    fun calculateKcv(keyHex: String, forceAlgorithm: String? = null): String {
        try {
            Log.d(TAG, "=== Calculando KCV ===")
            Log.d(TAG, "Clave hex entrada: $keyHex")
            if (forceAlgorithm != null) {
                Log.d(TAG, "Algoritmo forzado: $forceAlgorithm")
            }

            val keyBytes = hexStringToByteArray(keyHex)
            Log.d(TAG, "Clave bytes: ${keyBytes.joinToString(" ") { "%02X".format(it) }}")
            Log.d(TAG, "Longitud clave: ${keyBytes.size} bytes")

            val algorithm: String
            val finalKeyBytes: ByteArray

            when {
                forceAlgorithm == "AES_128" && keyBytes.size == 16 -> {
                    algorithm = "AES/ECB/NoPadding"
                    finalKeyBytes = keyBytes
                    Log.d(TAG, "Tipo: AES-128 (16 bytes, algoritmo forzado)")
                }
                forceAlgorithm == "AES_192" && keyBytes.size == 24 -> {
                    algorithm = "AES/ECB/NoPadding"
                    finalKeyBytes = keyBytes
                    Log.d(TAG, "Tipo: AES-192 (24 bytes, algoritmo forzado)")
                }
                forceAlgorithm == "AES_256" && keyBytes.size == 32 -> {
                    algorithm = "AES/ECB/NoPadding"
                    finalKeyBytes = keyBytes
                    Log.d(TAG, "Tipo: AES-256 (32 bytes, algoritmo forzado)")
                }
                forceAlgorithm == "DES_DOUBLE" && keyBytes.size == 16 -> {
                    algorithm = "DESede/ECB/NoPadding"
                    finalKeyBytes = keyBytes
                    Log.d(TAG, "Tipo: 3DES-112 (16 bytes, algoritmo forzado)")
                }
                forceAlgorithm == "DES_TRIPLE" && keyBytes.size == 24 -> {
                    algorithm = "DESede/ECB/NoPadding"
                    finalKeyBytes = keyBytes
                    Log.d(TAG, "Tipo: 3DES-168 (24 bytes, algoritmo forzado)")
                }
                else -> {
                    // Lógica original por defecto
                    when (keyBytes.size) {
                        8 -> { // DES
                            algorithm = "DES/ECB/NoPadding"
                            finalKeyBytes = keyBytes
                            Log.d(TAG, "Tipo: DES (8 bytes)")
                        }
                        16 -> { // 2-key 3DES o AES-128
                            // Por defecto asumimos 3DES para compatibilidad
                            algorithm = "DESede/ECB/NoPadding"
                            finalKeyBytes = keyBytes
                            Log.d(TAG, "Tipo: 2-key 3DES o AES-128 (16 bytes directo)")
                        }
                        24 -> { // 3-key 3DES o AES-192
                            algorithm = "DESede/ECB/NoPadding"
                            finalKeyBytes = keyBytes
                            Log.d(TAG, "Tipo: 3-key 3DES o AES-192 (24 bytes)")
                        }
                        32 -> { // AES-256
                            algorithm = "AES/ECB/NoPadding"
                            finalKeyBytes = keyBytes
                            Log.d(TAG, "Tipo: AES-256 (32 bytes)")
                        }
                        else -> {
                            Log.e(TAG, "Longitud de clave inválida: ${keyBytes.size} bytes")
                            throw IllegalArgumentException("Longitud de llave inválida: ${keyBytes.size}. Debe ser de 8, 16, 24 o 32 bytes.")
                        }
                    }
                }
            }

            Log.d(TAG, "Clave final: ${finalKeyBytes.joinToString(" ") { "%02X".format(it) }}")
            Log.d(TAG, "Algoritmo: $algorithm")

            // Crear especificación de clave
            val algorithmName = when (algorithm.substringBefore('/')) {
                "DES" -> "DES"
                "DESede" -> "DESede"
                "AES" -> "AES"
                else -> throw IllegalArgumentException("Algoritmo no soportado")
            }

            val keySpec = SecretKeySpec(finalKeyBytes, algorithmName)
            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)

            // Datos a encriptar: tamaño de bloque según algoritmo
            // DES/3DES: 8 bytes, AES: 16 bytes
            val blockSize = if (algorithmName == "AES") 16 else 8
            val data = ByteArray(blockSize) { 0x00 }
            Log.d(TAG, "Datos entrada: ${data.joinToString(" ") { "%02X".format(it) }}")

            val encryptedData = cipher.doFinal(data)
            Log.d(TAG, "Datos encriptados: ${encryptedData.joinToString(" ") { "%02X".format(it) }}")

            // KCV son los primeros 3 bytes en hexadecimal (6 caracteres)
            val kcv = encryptedData.copyOfRange(0, 3).joinToString("") { "%02X".format(it) }
            Log.d(TAG, "KCV final: $kcv")

            return kcv

        } catch (e: Exception) {
            Log.e(TAG, "Error calculando KCV: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            throw e
        }
    }

    fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "").uppercase()
        require(cleanHex.length % 2 == 0) { "Hex string debe tener longitud par" }

        return cleanHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    fun xorByteArrays(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) { "Los arrays deben tener la misma longitud: ${a.size} vs ${b.size}" }
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }
}

object TripleDESCrypto {

    private val TAG = "TripleDESCrypto"

    fun encryptWithKEK(plainData: String, kekData: String): String {
        try {
            Log.d(TAG, "=== CIFRANDO CON KEK/KTK ===")
            val plainBytes = KcvCalculator.hexStringToByteArray(plainData)
            val kekBytes = KcvCalculator.hexStringToByteArray(kekData)
            val (algorithm, algorithmName, blockSize) = when (kekBytes.size) {
                16, 24 -> Triple("DESede/ECB/NoPadding", "DESede", 8)
                32 -> Triple("AES/ECB/NoPadding", "AES", 16)
                else -> throw IllegalArgumentException("KEK/KTK debe ser de 16, 24 o 32 bytes, recibido: ${kekBytes.size}")
            }
            val paddedBytes = if (plainBytes.size % blockSize != 0) {
                plainBytes.copyOf(((plainBytes.size / blockSize) + 1) * blockSize)
            } else {
                plainBytes
            }
            val keySpec = SecretKeySpec(kekBytes, algorithmName)
            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encryptedBytes = cipher.doFinal(paddedBytes)
            return encryptedBytes.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al cifrar con KEK: ${e.message}", e)
            throw e
        }
    }

    fun decryptWithKEK(encryptedData: String, kekData: String): String {
        try {
            val encryptedBytes = KcvCalculator.hexStringToByteArray(encryptedData)
            val kekBytes = KcvCalculator.hexStringToByteArray(kekData)
            val (algorithm, algorithmName, blockSize) = when (kekBytes.size) {
                16, 24 -> Triple("DESede/ECB/NoPadding", "DESede", 8)
                32 -> Triple("AES/ECB/NoPadding", "AES", 16)
                else -> throw IllegalArgumentException("KEK/KTK debe ser de 16, 24 o 32 bytes, recibido: ${kekBytes.size}")
            }

            // Si los datos no son múltiplo del tamaño de bloque, rellenar con ceros (PKCS7)
            val paddedBytes = if (encryptedBytes.size % blockSize != 0) {
                Log.d(TAG, "Datos cifrados no son múltiplo de $blockSize: ${encryptedBytes.size} bytes")
                val paddedSize = ((encryptedBytes.size / blockSize) + 1) * blockSize
                val padLength = paddedSize - encryptedBytes.size
                Log.d(TAG, "Agregando $padLength bytes de padding para alcanzar $paddedSize bytes")
                encryptedBytes.copyOf(paddedSize)
            } else {
                encryptedBytes
            }

            val keySpec = SecretKeySpec(kekBytes, algorithmName)
            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decryptedBytes = cipher.doFinal(paddedBytes)
            return decryptedBytes.joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al descifrar con KEK: ${e.message}", e)
            throw e
        }
    }

    fun encryptKeyForTransmission(keyData: String, kekData: String, keyKcv: String): String {
        try {
            val keyBytes = KcvCalculator.hexStringToByteArray(keyData)
            if (keyBytes.size !in listOf(8, 16, 24, 32, 48)) {
                throw IllegalArgumentException("Llave debe ser de 8, 16, 24, 32 o 48 bytes (3DES/AES), recibido: ${keyBytes.size}")
            }
            return encryptWithKEK(keyData, kekData)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al cifrar llave para transmisión: ${e.message}", e)
            throw e
        }
    }

    fun decryptKeyAfterTransmission(encryptedKeyData: String, kekData: String, expectedKcv: String, originalKeyLengthBytes: Int, algorithmType: String? = null): String {
        try {
            Log.d(TAG, "=== DESCIFRANDO LLAVE RECIBIDA ===")
            Log.d(TAG, "KCV esperado: $expectedKcv")
            Log.d(TAG, "Longitud original esperada: $originalKeyLengthBytes bytes")
            if (algorithmType != null) {
                Log.d(TAG, "Tipo de algoritmo: $algorithmType")
            }

            val decryptedKeyWithPadding = decryptWithKEK(encryptedKeyData, kekData)
            val decryptedBytesWithPadding = KcvCalculator.hexStringToByteArray(decryptedKeyWithPadding)
            Log.d(TAG, "Longitud descifrada (con padding): ${decryptedBytesWithPadding.size} bytes")

            if (decryptedBytesWithPadding.size < originalKeyLengthBytes) {
                throw IllegalArgumentException("Error de integridad: los datos descifrados (${decryptedBytesWithPadding.size} bytes) son más cortos que la longitud original esperada ($originalKeyLengthBytes bytes).")
            }

            val actualKeyBytes = decryptedBytesWithPadding.copyOfRange(0, originalKeyLengthBytes)
            val actualKeyHex = actualKeyBytes.joinToString("") { "%02X".format(it) }
            Log.d(TAG, "Llave truncada a su tamaño original ($originalKeyLengthBytes bytes): ${actualKeyHex.take(64)}...")

            val calculatedKcv = KcvCalculator.calculateKcv(actualKeyHex, algorithmType)
            Log.d(TAG, "KCV calculado de la llave truncada: $calculatedKcv")

            if (!calculatedKcv.startsWith(expectedKcv, ignoreCase = true)) {
                Log.e(TAG, "¡FALLO DE VALIDACIÓN DE KCV!")
                Log.e(TAG, "  - KCV Esperado: $expectedKcv")
                Log.e(TAG, "  - KCV Calculado: $calculatedKcv")
                Log.e(TAG, "  - Llave (hex) usada para cálculo: $actualKeyHex")
                throw IllegalArgumentException("No se pudo validar la llave descifrada. KCV esperado: $expectedKcv, calculado: $calculatedKcv")
            }

            Log.d(TAG, "✓ Llave descifrada y validada exitosamente")
            return actualKeyHex

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al descifrar llave recibida: ${e.message}", e)
            throw e
        }
    }
}
