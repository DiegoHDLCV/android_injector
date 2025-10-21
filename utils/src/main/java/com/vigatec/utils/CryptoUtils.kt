package com.vigatec.utils

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object KcvCalculator {

    val TAG = "KcvCalculator"

    fun calculateKcv(keyHex: String): String {
        try {
            Log.d(TAG, "=== Calculando KCV ===")
            Log.d(TAG, "Clave hex entrada: $keyHex")

            val keyBytes = hexStringToByteArray(keyHex)
            Log.d(TAG, "Clave bytes: ${keyBytes.joinToString(" ") { "%02X".format(it) }}")
            Log.d(TAG, "Longitud clave: ${keyBytes.size} bytes")

            val algorithm: String
            val finalKeyBytes: ByteArray

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

    // Función alternativa que prueba diferentes métodos para comparar
    fun calculateKcvComparison(keyHex: String): Map<String, String> {
        Log.d(TAG, "=== COMPARANDO MÉTODOS KCV ===")
        val results = mutableMapOf<String, String>()

        try {
            val keyBytes = hexStringToByteArray(keyHex)

            if (keyBytes.size == 16) {
                // Método 1: 16 bytes directo (NUEVO)
                try {
                    val keySpec = SecretKeySpec(keyBytes, "DESede")
                    val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec)

                    val data = ByteArray(8) { 0x00 }
                    val encrypted = cipher.doFinal(data)
                    val kcv = encrypted.copyOfRange(0, 3).joinToString("") { "%02X".format(it) }

                    results["16_bytes_directo"] = kcv
                    Log.d(TAG, "Método 16 bytes directo: $kcv")

                } catch (e: Exception) {
                    Log.e(TAG, "Error método 16 bytes directo: ${e.message}")
                    results["16_bytes_directo"] = "ERROR"
                }

                // Método 2: Expansión K1K2K1 (ANTERIOR)
                try {
                    val k1 = keyBytes.copyOfRange(0, 8)
                    val k2 = keyBytes.copyOfRange(8, 16)
                    val expandedKey = k1 + k2 + k1

                    val keySpec = SecretKeySpec(expandedKey, "DESede")
                    val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec)

                    val data = ByteArray(8) { 0x00 }
                    val encrypted = cipher.doFinal(data)
                    val kcv = encrypted.copyOfRange(0, 3).joinToString("") { "%02X".format(it) }

                    results["expansion_K1K2K1"] = kcv
                    Log.d(TAG, "Método expansión K1K2K1: $kcv")

                } catch (e: Exception) {
                    Log.e(TAG, "Error método expansión: ${e.message}")
                    results["expansion_K1K2K1"] = "ERROR"
                }

                // Método 3: Solo DES con K1
                try {
                    val k1 = keyBytes.copyOfRange(0, 8)
                    val keySpec = SecretKeySpec(k1, "DES")
                    val cipher = Cipher.getInstance("DES/ECB/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec)

                    val data = ByteArray(8) { 0x00 }
                    val encrypted = cipher.doFinal(data)
                    val kcv = encrypted.copyOfRange(0, 3).joinToString("") { "%02X".format(it) }

                    results["DES_solo_K1"] = kcv
                    Log.d(TAG, "Método DES solo K1: $kcv")

                } catch (e: Exception) {
                    Log.e(TAG, "Error método DES K1: ${e.message}")
                    results["DES_solo_K1"] = "ERROR"
                }
            }

            Log.d(TAG, "=== RESULTADOS COMPARACIÓN ===")
            results.forEach { (metodo, resultado) ->
                Log.d(TAG, "$metodo: $resultado")
                if (resultado == "CBB14C") {
                    Log.d(TAG, "*** MÉTODO CORRECTO ENCONTRADO: $metodo ***")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error en comparación: ${e.message}")
        }

        return results
    }

    // Función auxiliar para convertir hex string a ByteArray
    fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "").uppercase()
        require(cleanHex.length % 2 == 0) { "Hex string debe tener longitud par" }

        return cleanHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    // Extensión para convertir ByteArray a hex string
    private fun ByteArray.toHexString(): String {
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

    // Función de prueba para verificar que ahora da CBB14C
    fun testKcvWithTargetKey() {
        Log.d(TAG, "=== PRUEBA CON CLAVE OBJETIVO ===")
        val testKey = "6C096C2884EB17CF7144D890E1D456CC"
        val expectedKcv = "CBB14C"

        Log.d(TAG, "Clave: $testKey")
        Log.d(TAG, "KCV esperado: $expectedKcv")

        // Método principal (ahora modificado)
        val mainResult = calculateKcv(testKey)
        Log.d(TAG, "KCV calculado (método principal): $mainResult")
        Log.d(TAG, "¿Coincide?: ${mainResult.equals(expectedKcv, ignoreCase = true)}")

        // Comparación de métodos
        calculateKcvComparison(testKey)
    }
}

/**
 * Utilidad para cifrar y descifrar datos usando 3DES (Triple DES)
 * Se usa para cifrar llaves con la KEK (Key Encryption Key)
 */
object TripleDESCrypto {

    private val TAG = "TripleDESCrypto"

    /**
     * Cifra datos usando 3DES con una KEK
     *
     * @param plainData Datos en claro (hex string)
     * @param kekData Llave de cifrado KEK (hex string)
     * @return Datos cifrados (hex string)
     */
    fun encryptWithKEK(plainData: String, kekData: String): String {
        try {
            Log.d(TAG, "=== CIFRANDO CON KEK/KTK ===")
            Log.d(TAG, "Datos en claro (hex): ${plainData.take(32)}... (${plainData.length / 2} bytes)")
            Log.d(TAG, "KEK/KTK (hex): ${kekData.take(32)}... (${kekData.length / 2} bytes)")

            val plainBytes = KcvCalculator.hexStringToByteArray(plainData)
            val kekBytes = KcvCalculator.hexStringToByteArray(kekData)

            Log.d(TAG, "Longitud datos: ${plainBytes.size} bytes")
            Log.d(TAG, "Longitud KEK/KTK: ${kekBytes.size} bytes")

            // Validar longitud de KEK/KTK - soportar tanto 3DES como AES
            if (kekBytes.size != 16 && kekBytes.size != 24 && kekBytes.size != 32) {
                throw IllegalArgumentException("KEK/KTK debe ser de 16, 24 o 32 bytes, recibido: ${kekBytes.size}")
            }

            // Determinar algoritmo según longitud
            val (algorithm, algorithmName, blockSize) = when (kekBytes.size) {
                16, 24 -> {
                    Log.d(TAG, "Detectado: 3DES (${kekBytes.size} bytes)")
                    Triple("DESede/ECB/NoPadding", "DESede", 8)
                }
                32 -> {
                    Log.d(TAG, "Detectado: AES-256 (32 bytes)")
                    Triple("AES/ECB/NoPadding", "AES", 16)
                }
                else -> throw IllegalArgumentException("Longitud inesperada: ${kekBytes.size}")
            }

            Log.d(TAG, "Algoritmo: $algorithmName (bloque de $blockSize bytes)")

            // Ajustar datos al tamaño de bloque si es necesario (padding con ceros)
            val paddedBytes = if (plainBytes.size % blockSize != 0) {
                val paddedSize = ((plainBytes.size / blockSize) + 1) * blockSize
                Log.d(TAG, "Aplicando padding: ${plainBytes.size} -> $paddedSize bytes")
                plainBytes.copyOf(paddedSize) // Padding con ceros
            } else {
                plainBytes
            }

            Log.d(TAG, "Datos a cifrar: ${paddedBytes.size} bytes (después de padding)")

            // Crear cipher con el algoritmo apropiado
            val keySpec = SecretKeySpec(kekBytes, algorithmName)
            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)

            // Cifrar
            val encryptedBytes = cipher.doFinal(paddedBytes)
            val encryptedHex = encryptedBytes.joinToString("") { "%02X".format(it) }

            Log.d(TAG, "Datos cifrados (hex): ${encryptedHex.take(32)}...")
            Log.d(TAG, "Longitud cifrada: ${encryptedBytes.size} bytes")
            Log.d(TAG, "✓ Cifrado exitoso")

            return encryptedHex

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al cifrar con KEK: ${e.message}", e)
            throw e
        }
    }

    /**
     * Descifra datos usando 3DES con una KEK
     *
     * @param encryptedData Datos cifrados (hex string)
     * @param kekData Llave de descifrado KEK (hex string)
     * @return Datos en claro (hex string)
     */
    fun decryptWithKEK(encryptedData: String, kekData: String): String {
        try {
            Log.d(TAG, "=== DESCIFRANDO CON KEK/KTK ===")
            Log.d(TAG, "Datos cifrados (hex): ${encryptedData.take(32)}... (${encryptedData.length / 2} bytes)")
            Log.d(TAG, "KEK/KTK (hex): ${kekData.take(32)}... (${kekData.length / 2} bytes)")

            val encryptedBytes = KcvCalculator.hexStringToByteArray(encryptedData)
            val kekBytes = KcvCalculator.hexStringToByteArray(kekData)

            Log.d(TAG, "Longitud cifrada: ${encryptedBytes.size} bytes")
            Log.d(TAG, "Longitud KEK/KTK: ${kekBytes.size} bytes")

            // Validar longitud de KEK/KTK - soportar tanto 3DES como AES
            if (kekBytes.size != 16 && kekBytes.size != 24 && kekBytes.size != 32) {
                throw IllegalArgumentException("KEK/KTK debe ser de 16, 24 o 32 bytes, recibido: ${kekBytes.size}")
            }

            // Determinar algoritmo según longitud
            val (algorithm, algorithmName, blockSize) = when (kekBytes.size) {
                16, 24 -> {
                    Log.d(TAG, "Detectado: 3DES (${kekBytes.size} bytes)")
                    Triple("DESede/ECB/NoPadding", "DESede", 8)
                }
                32 -> {
                    Log.d(TAG, "Detectado: AES-256 (32 bytes)")
                    Triple("AES/ECB/NoPadding", "AES", 16)
                }
                else -> throw IllegalArgumentException("Longitud inesperada: ${kekBytes.size}")
            }

            // Validar que los datos sean múltiplo del tamaño de bloque
            if (encryptedBytes.size % blockSize != 0) {
                throw IllegalArgumentException("Los datos cifrados deben ser múltiplo de $blockSize bytes, recibido: ${encryptedBytes.size}")
            }

            Log.d(TAG, "Algoritmo: $algorithmName (bloque de $blockSize bytes)")

            // Crear cipher con el algoritmo apropiado
            val keySpec = SecretKeySpec(kekBytes, algorithmName)
            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.DECRYPT_MODE, keySpec)

            // Descifrar
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            // Remover padding de ceros si es necesario
            // (el dispositivo debe saber la longitud original de la llave)
            val decryptedHex = decryptedBytes.joinToString("") { "%02X".format(it) }

            Log.d(TAG, "Datos descifrados (hex): ${decryptedHex.take(32)}...")
            Log.d(TAG, "Longitud descifrada: ${decryptedBytes.size} bytes")
            Log.d(TAG, "✓ Descifrado exitoso")

            return decryptedHex

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al descifrar con KEK: ${e.message}", e)
            throw e
        }
    }

    /**
     * Cifra una llave completa para envío al SubPOS
     * Incluye validaciones y logs detallados
     *
     * @param keyData Datos de la llave en claro (hex)
     * @param kekData KEK para cifrar (hex)
     * @param keyKcv KCV de la llave para validación
     * @return Llave cifrada (hex)
     */
    fun encryptKeyForTransmission(keyData: String, kekData: String, keyKcv: String): String {
        try {
            Log.d(TAG, "=== CIFRANDO LLAVE PARA TRANSMISIÓN ===")
            Log.d(TAG, "KCV de la llave: $keyKcv")

            // Validar que la llave sea de longitud válida (3DES y AES)
            val keyBytes = KcvCalculator.hexStringToByteArray(keyData)
            if (keyBytes.size !in listOf(8, 16, 24, 32, 48)) {
                throw IllegalArgumentException("Llave debe ser de 8, 16, 24, 32 o 48 bytes (3DES/AES), recibido: ${keyBytes.size}")
            }

            // Cifrar
            val encryptedKey = encryptWithKEK(keyData, kekData)

            // Validar KCV después de cifrado (el KCV original debe ser preservado)
            Log.d(TAG, "✓ Llave cifrada exitosamente")
            Log.d(TAG, "  - KCV original: $keyKcv")
            Log.d(TAG, "  - Longitud original: ${keyBytes.size} bytes")
            Log.d(TAG, "  - Longitud cifrada: ${encryptedKey.length / 2} bytes")

            return encryptedKey

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al cifrar llave para transmisión: ${e.message}", e)
            throw e
        }
    }

    /**
     * Descifra una llave recibida cifrada desde el inyector
     * Incluye validaciones y logs detallados
     *
     * @param encryptedKeyData Datos de la llave cifrada (hex)
     * @param kekData KEK/KTK para descifrar (hex)
     * @param expectedKcv KCV esperado de la llave descifrada
     * @return Llave descifrada (hex)
     */
    fun decryptKeyAfterTransmission(encryptedKeyData: String, kekData: String, expectedKcv: String): String {
        try {
            Log.d(TAG, "=== DESCIFRANDO LLAVE RECIBIDA ===")
            Log.d(TAG, "KCV esperado: $expectedKcv")

            // Descifrar
            val decryptedKey = decryptWithKEK(encryptedKeyData, kekData)

            // El descifrado puede tener padding, necesitamos removerlo
            // Probamos diferentes longitudes válidas: 8, 16, 24, 32, 48 bytes
            val decryptedBytes = KcvCalculator.hexStringToByteArray(decryptedKey)
            Log.d(TAG, "Longitud descifrada (con posible padding): ${decryptedBytes.size} bytes")

            // Validar KCV para determinar la longitud real
            val validLengths = listOf(8, 16, 24, 32, 48)
            var actualKeyHex: String? = null

            for (length in validLengths) {
                if (length <= decryptedBytes.size) {
                    val candidateKey = decryptedBytes.copyOfRange(0, length)
                    val candidateKeyHex = candidateKey.joinToString("") { "%02X".format(it) }
                    val candidateKcv = KcvCalculator.calculateKcv(candidateKeyHex)

                    Log.d(TAG, "Probando longitud $length bytes: KCV = $candidateKcv")

                    if (candidateKcv.startsWith(expectedKcv, ignoreCase = true)) {
                        actualKeyHex = candidateKeyHex
                        Log.d(TAG, "✓ KCV coincide con longitud $length bytes")
                        break
                    }
                }
            }

            if (actualKeyHex == null) {
                throw IllegalArgumentException("No se pudo validar la llave descifrada. KCV esperado: $expectedKcv")
            }

            Log.d(TAG, "✓ Llave descifrada y validada exitosamente")
            Log.d(TAG, "  - Longitud real: ${actualKeyHex.length / 2} bytes")
            Log.d(TAG, "  - KCV validado: $expectedKcv")

            return actualKeyHex

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al descifrar llave recibida: ${e.message}", e)
            throw e
        }
    }

    /**
     * Prueba de cifrado/descifrado round-trip
     * Útil para validar que el cifrado y descifrado funcionan correctamente
     */
    fun testRoundTrip() {
        Log.d(TAG, "=== PRUEBA ROUND-TRIP DE CIFRADO ===")

        // Datos de prueba
        val testKey = "AABBCCDDEEFF00112233445566778899" // 16 bytes (llave a cifrar)
        val testKEK = "6C096C2884EB17CF7144D890E1D456CC" // 16 bytes (KEK)

        Log.d(TAG, "Llave original: $testKey")
        Log.d(TAG, "KEK: $testKEK")

        // Cifrar
        val encrypted = encryptWithKEK(testKey, testKEK)
        Log.d(TAG, "Llave cifrada: $encrypted")

        // Descifrar
        val decrypted = decryptWithKEK(encrypted, testKEK)
        Log.d(TAG, "Llave descifrada: $decrypted")

        // Validar
        val success = testKey.equals(decrypted, ignoreCase = true)
        Log.d(TAG, if (success) "✓ ROUND-TRIP EXITOSO" else "✗ ROUND-TRIP FALLIDO")
        Log.d(TAG, "Original:    $testKey")
        Log.d(TAG, "Descifrado:  $decrypted")
        Log.d(TAG, "¿Coincide?: $success")
    }
}