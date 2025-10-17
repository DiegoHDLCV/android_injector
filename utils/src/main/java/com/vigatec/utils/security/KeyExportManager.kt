package com.vigatec.utils.security

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Datos de una llave para exportación/importación
 */
data class ExportKeyData(
    val kcv: String,
    val keySlot: Int,
    val keyType: String,
    val keyAlgorithm: String,
    val customName: String,
    val kekType: String,
    val encryptedKeyData: String,
    val encryptionIV: String,
    val encryptionAuthTag: String,
    val status: String,
    val injectionTimestamp: Long
)

/**
 * Resultado de una exportación
 */
data class ExportResult(
    val success: Boolean,
    val exportedJson: String = "",
    val keyCount: Int = 0,
    val filePath: String = "",
    val errorMessage: String = ""
)

/**
 * Resultado de una importación
 */
data class ImportResult(
    val success: Boolean,
    val importedKeys: List<ExportKeyData> = emptyList(),
    val skippedDuplicates: Int = 0,
    val errorMessage: String = ""
)

/**
 * Gestiona la exportación e importación segura del almacén de llaves.
 *
 * Características de seguridad:
 * - Doble cifrado: llaves cifradas con KEK Storage + archivo cifrado con passphrase
 * - PBKDF2-HMAC-SHA256 con 200,000 iteraciones para derivación de llave
 * - AES-256-GCM para cifrado autenticado
 * - Salt e IV aleatorios por exportación
 * - Validación de integridad con authTag
 */
object KeyExportManager {

    private const val TAG = "KeyExportManager"
    private const val VERSION = 1

    // Configuración criptográfica
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 200000 // Resistente a ataques de fuerza bruta
    private const val PBKDF2_KEY_LENGTH = 256 // bits

    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_IV_LENGTH = 12 // bytes

    private const val SALT_LENGTH = 32 // bytes

    // Requisitos de passphrase
    private const val MIN_PASSPHRASE_LENGTH = 16

    /**
     * Valida que la passphrase cumpla con los requisitos de seguridad.
     */
    fun validatePassphrase(passphrase: String): Pair<Boolean, String> {
        if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
            return false to "La passphrase debe tener al menos $MIN_PASSPHRASE_LENGTH caracteres"
        }

        val hasUpperCase = passphrase.any { it.isUpperCase() }
        val hasLowerCase = passphrase.any { it.isLowerCase() }
        val hasDigit = passphrase.any { it.isDigit() }
        val hasSpecial = passphrase.any { !it.isLetterOrDigit() }

        if (!hasUpperCase || !hasLowerCase || !hasDigit) {
            return false to "La passphrase debe contener mayúsculas, minúsculas y números"
        }

        return true to "Passphrase válida"
    }

    /**
     * Exporta un conjunto de llaves a formato JSON cifrado.
     *
     * @param keys Lista de llaves a exportar (ya cifradas con KEK Storage)
     * @param passphrase Passphrase para proteger la exportación
     * @param exportedBy Username del administrador que realiza la exportación
     * @param deviceId Identificador del dispositivo (puede ser hasheado)
     * @return ExportResult con el JSON cifrado o error
     */
    fun exportKeys(
        keys: List<ExportKeyData>,
        passphrase: String,
        exportedBy: String,
        deviceId: String
    ): ExportResult {
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        Log.i(TAG, "INICIANDO EXPORTACIÓN DE LLAVES")
        Log.i(TAG, "  - Número de llaves: ${keys.size}")
        Log.i(TAG, "  - Exportado por: $exportedBy")
        Log.i(TAG, "═══════════════════════════════════════════════════════════")

        return try {
            // Validar passphrase
            val (isValid, validationMessage) = validatePassphrase(passphrase)
            if (!isValid) {
                return ExportResult(
                    success = false,
                    errorMessage = validationMessage
                )
            }

            // Validar que haya llaves para exportar
            if (keys.isEmpty()) {
                return ExportResult(
                    success = false,
                    errorMessage = "No hay llaves para exportar"
                )
            }

            // Generar salt aleatorio
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)
            Log.d(TAG, "✓ Salt generado: ${salt.size} bytes")

            // Derivar llave de exportación usando PBKDF2
            val exportKey = deriveKey(passphrase, salt)
            Log.d(TAG, "✓ Llave de exportación derivada (PBKDF2, $PBKDF2_ITERATIONS iteraciones)")

            // Crear payload JSON interno con las llaves
            val payloadJson = createPayloadJson(keys)
            Log.d(TAG, "✓ Payload JSON creado: ${payloadJson.length} caracteres")

            // Cifrar payload con AES-256-GCM
            val encryptedData = encryptPayload(payloadJson, exportKey)
            Log.d(TAG, "✓ Payload cifrado con AES-256-GCM")

            // Crear JSON externo con metadata + datos cifrados
            val exportJson = createExportJson(
                salt = salt,
                encryptedData = encryptedData,
                keyCount = keys.size,
                exportedBy = exportedBy,
                deviceId = deviceId
            )

            Log.i(TAG, "✓ Exportación completada exitosamente")
            Log.i(TAG, "  - Tamaño del archivo: ${exportJson.length} caracteres")
            Log.i(TAG, "═══════════════════════════════════════════════════════════")

            ExportResult(
                success = true,
                exportedJson = exportJson,
                keyCount = keys.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error durante exportación", e)
            Log.e(TAG, "═══════════════════════════════════════════════════════════")
            ExportResult(
                success = false,
                errorMessage = "Error al exportar: ${e.message}"
            )
        }
    }

    /**
     * Importa llaves desde un archivo JSON cifrado.
     *
     * @param exportedJson Contenido del archivo JSON exportado
     * @param passphrase Passphrase usada en la exportación
     * @param existingKcvs Lista de KCVs existentes para detectar duplicados
     * @return ImportResult con las llaves importadas o error
     */
    fun importKeys(
        exportedJson: String,
        passphrase: String,
        existingKcvs: Set<String>
    ): ImportResult {
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        Log.i(TAG, "INICIANDO IMPORTACIÓN DE LLAVES")
        Log.i(TAG, "═══════════════════════════════════════════════════════════")

        return try {
            // Parsear JSON externo
            val exportData = JSONObject(exportedJson)
            val version = exportData.getInt("version")

            if (version != VERSION) {
                return ImportResult(
                    success = false,
                    errorMessage = "Versión de exportación no compatible: $version (esperada: $VERSION)"
                )
            }

            val salt = hexStringToByteArray(exportData.getString("salt"))
            val iv = hexStringToByteArray(exportData.getString("iv"))
            val authTag = hexStringToByteArray(exportData.getString("authTag"))
            val encryptedPayload = hexStringToByteArray(exportData.getString("encryptedPayload"))
            val keyCount = exportData.getInt("keyCount")

            Log.d(TAG, "Metadata de exportación:")
            Log.d(TAG, "  - Versión: $version")
            Log.d(TAG, "  - Fecha: ${exportData.getString("exportDate")}")
            Log.d(TAG, "  - Exportado por: ${exportData.getString("exportedBy")}")
            Log.d(TAG, "  - Número de llaves: $keyCount")

            // Derivar llave de importación
            val importKey = deriveKey(passphrase, salt)
            Log.d(TAG, "✓ Llave de importación derivada")

            // Descifrar payload
            val payloadJson = try {
                decryptPayload(encryptedPayload, importKey, iv, authTag)
            } catch (e: Exception) {
                return ImportResult(
                    success = false,
                    errorMessage = "Passphrase incorrecta o archivo corrupto"
                )
            }
            Log.d(TAG, "✓ Payload descifrado exitosamente")

            // Parsear payload y extraer llaves
            val payload = JSONObject(payloadJson)
            val keysArray = payload.getJSONArray("keys")
            val importedKeys = mutableListOf<ExportKeyData>()
            var skippedDuplicates = 0

            for (i in 0 until keysArray.length()) {
                val keyObj = keysArray.getJSONObject(i)
                val kcv = keyObj.getString("kcv")

                // Verificar duplicados por KCV
                if (existingKcvs.contains(kcv)) {
                    Log.w(TAG, "⚠️ Llave duplicada omitida: KCV=$kcv")
                    skippedDuplicates++
                    continue
                }

                val keyData = ExportKeyData(
                    kcv = kcv,
                    keySlot = keyObj.getInt("keySlot"),
                    keyType = keyObj.getString("keyType"),
                    keyAlgorithm = keyObj.getString("keyAlgorithm"),
                    customName = keyObj.optString("customName", ""),
                    kekType = keyObj.getString("kekType"),
                    encryptedKeyData = keyObj.getString("encryptedKeyData"),
                    encryptionIV = keyObj.getString("encryptionIV"),
                    encryptionAuthTag = keyObj.getString("encryptionAuthTag"),
                    status = keyObj.getString("status"),
                    injectionTimestamp = keyObj.getLong("injectionTimestamp")
                )
                importedKeys.add(keyData)
            }

            Log.i(TAG, "✓ Importación completada exitosamente")
            Log.i(TAG, "  - Llaves importadas: ${importedKeys.size}")
            Log.i(TAG, "  - Duplicados omitidos: $skippedDuplicates")
            Log.i(TAG, "═══════════════════════════════════════════════════════════")

            ImportResult(
                success = true,
                importedKeys = importedKeys,
                skippedDuplicates = skippedDuplicates
            )

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error durante importación", e)
            Log.e(TAG, "═══════════════════════════════════════════════════════════")
            ImportResult(
                success = false,
                errorMessage = "Error al importar: ${e.message}"
            )
        }
    }

    /**
     * Deriva una llave criptográfica desde una passphrase usando PBKDF2.
     */
    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val keySpec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH
        )
        val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Crea el JSON interno con el payload de llaves.
     */
    private fun createPayloadJson(keys: List<ExportKeyData>): String {
        val payload = JSONObject()
        payload.put("kekStorageExists", true)

        val keysArray = JSONArray()
        for (key in keys) {
            val keyObj = JSONObject()
            keyObj.put("kcv", key.kcv)
            keyObj.put("keySlot", key.keySlot)
            keyObj.put("keyType", key.keyType)
            keyObj.put("keyAlgorithm", key.keyAlgorithm)
            keyObj.put("customName", key.customName)
            keyObj.put("kekType", key.kekType)
            keyObj.put("encryptedKeyData", key.encryptedKeyData)
            keyObj.put("encryptionIV", key.encryptionIV)
            keyObj.put("encryptionAuthTag", key.encryptionAuthTag)
            keyObj.put("status", key.status)
            keyObj.put("injectionTimestamp", key.injectionTimestamp)
            keysArray.put(keyObj)
        }

        payload.put("keys", keysArray)
        return payload.toString()
    }

    /**
     * Datos cifrados con AES-GCM
     */
    private data class EncryptedPayload(
        val ciphertext: ByteArray,
        val iv: ByteArray,
        val authTag: ByteArray
    )

    /**
     * Cifra el payload JSON con AES-256-GCM.
     */
    private fun encryptPayload(payloadJson: String, key: SecretKeySpec): EncryptedPayload {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)
        val encryptedBytes = cipher.doFinal(payloadBytes)

        // En GCM, el authTag viene en los últimos 16 bytes
        val ciphertextLength = encryptedBytes.size - (GCM_TAG_LENGTH / 8)
        val ciphertext = encryptedBytes.copyOfRange(0, ciphertextLength)
        val authTag = encryptedBytes.copyOfRange(ciphertextLength, encryptedBytes.size)

        return EncryptedPayload(ciphertext, iv, authTag)
    }

    /**
     * Descifra el payload con AES-256-GCM.
     */
    private fun decryptPayload(
        ciphertext: ByteArray,
        key: SecretKeySpec,
        iv: ByteArray,
        authTag: ByteArray
    ): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        // Concatenar ciphertext + authTag
        val encryptedWithTag = ciphertext + authTag

        val decryptedBytes = cipher.doFinal(encryptedWithTag)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Crea el JSON externo con metadata y datos cifrados.
     */
    private fun createExportJson(
        salt: ByteArray,
        encryptedData: EncryptedPayload,
        keyCount: Int,
        exportedBy: String,
        deviceId: String
    ): String {
        val exportJson = JSONObject()
        exportJson.put("version", VERSION)
        exportJson.put("exportDate", java.time.Instant.now().toString())
        exportJson.put("exportedBy", exportedBy)
        exportJson.put("deviceId", deviceId)
        exportJson.put("keyCount", keyCount)
        exportJson.put("salt", byteArrayToHexString(salt))
        exportJson.put("iv", byteArrayToHexString(encryptedData.iv))
        exportJson.put("authTag", byteArrayToHexString(encryptedData.authTag))
        exportJson.put("encryptedPayload", byteArrayToHexString(encryptedData.ciphertext))

        return exportJson.toString(2) // Indent para legibilidad
    }

    // Utilidades de conversión

    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").uppercase()
        require(cleanHex.length % 2 == 0) { "String hexadecimal debe tener longitud par" }

        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
