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
                16 -> { // 2-key 3DES - USAR 16 BYTES DIRECTO
                    algorithm = "DESede/ECB/NoPadding"
                    // CAMBIO: Usar los 16 bytes directamente sin expansión
                    finalKeyBytes = keyBytes
                    Log.d(TAG, "Tipo: 2-key 3DES (16 bytes directo)")
                }
                24 -> { // 3-key 3DES
                    algorithm = "DESede/ECB/NoPadding"
                    finalKeyBytes = keyBytes
                    Log.d(TAG, "Tipo: 3-key 3DES (24 bytes)")
                }
                else -> {
                    Log.e(TAG, "Longitud de clave inválida: ${keyBytes.size} bytes")
                    throw IllegalArgumentException("Longitud de llave inválida: ${keyBytes.size}. Debe ser de 8, 16, o 24 bytes.")
                }
            }

            Log.d(TAG, "Clave final: ${finalKeyBytes.joinToString(" ") { "%02X".format(it) }}")
            Log.d(TAG, "Algoritmo: $algorithm")

            // Crear especificación de clave
            val algorithmName = when (algorithm.substringBefore('/')) {
                "DES" -> "DES"
                "DESede" -> "DESede"
                else -> throw IllegalArgumentException("Algoritmo no soportado")
            }

            val keySpec = SecretKeySpec(finalKeyBytes, algorithmName)
            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)

            // Datos a encriptar: 8 bytes de ceros (estándar para KCV)
            val data = ByteArray(8) { 0x00 }
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
    private fun hexStringToByteArray(hex: String): ByteArray {
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