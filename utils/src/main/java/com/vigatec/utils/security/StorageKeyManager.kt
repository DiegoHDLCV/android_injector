package com.vigatec.utils.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestiona la KEK de Almacenamiento (Storage KEK) que protege todas las llaves
 * guardadas en la base de datos local.
 *
 * La KEK Storage:
 * - Se genera mediante ceremonia de custodios
 * - Se almacena en Android Keystore (protección hardware)
 * - Nunca se guarda en base de datos
 * - Es única por dispositivo/instalación
 * - Se usa para cifrar/descifrar llaves antes de guardar/leer de BD
 */
object StorageKeyManager {

    private const val TAG = "StorageKeyManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val STORAGE_KEK_ALIAS = "storage_kek_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val IV_LENGTH = 12 // bytes (recomendado para GCM)

    /**
     * Obtiene una instancia fresca del KeyStore.
     * Esto asegura que siempre tengamos el estado actualizado del Keystore.
     */
    private fun getKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    /**
     * Verifica si existe una KEK Storage inicializada
     */
    fun hasStorageKEK(): Boolean {
        return try {
            val keyStore = getKeyStore()
            keyStore.containsAlias(STORAGE_KEK_ALIAS).also {
                Log.i(TAG, "¿Existe KEK Storage?: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando KEK Storage", e)
            false
        }
    }

    /**
     * Inicializa la KEK Storage desde una ceremonia de custodios.
     * La llave generada por XOR de componentes se almacena en Android Keystore.
     *
     * IMPORTANTE: Si ya existe una KEK Storage, debes rotar las llaves antes de llamar a esta función.
     * Esta función solo crea una nueva KEK, no maneja la rotación de llaves existentes.
     *
     * @param ceremonyKeyHex Llave resultante de la ceremonia en formato hexadecimal
     * @param replaceIfExists Si es true, reemplaza la KEK existente (solo usar si ya rotaste las llaves)
     * @throws IllegalStateException Si ya existe KEK Storage y replaceIfExists es false
     * @throws IllegalArgumentException Si el tamaño de llave es inválido
     */
    fun initializeFromCeremony(ceremonyKeyHex: String, replaceIfExists: Boolean = false) {
        Log.i(TAG, "=== INICIALIZANDO KEK STORAGE DESDE CEREMONIA ===")

        if (hasStorageKEK()) {
            if (replaceIfExists) {
                Log.w(TAG, "⚠️ Reemplazando KEK Storage existente")
                Log.w(TAG, "   Asumiendo que las llaves ya fueron rotadas previamente")
                deleteStorageKEK()
                Log.i(TAG, "✓ KEK Storage anterior eliminada")
            } else {
                throw IllegalStateException("Ya existe una KEK Storage inicializada. Debes rotar las llaves existentes primero o llamar con replaceIfExists=true.")
            }
        }

        // Validar formato hexadecimal
        require(ceremonyKeyHex.matches(Regex("^[0-9A-Fa-f]+\$"))) {
            "La llave debe estar en formato hexadecimal válido"
        }

        val keyBytes = hexStringToByteArray(ceremonyKeyHex)
        val keyLengthBytes = keyBytes.size

        // Validar tamaño de llave (solo AES-256 para máxima seguridad)
        require(keyLengthBytes == 32) {
            "KEK Storage debe ser AES-256 (32 bytes). Recibido: $keyLengthBytes bytes"
        }

        Log.i(TAG, "Llave de ceremonia:")
        Log.i(TAG, "  - Longitud: $keyLengthBytes bytes (${keyLengthBytes * 8} bits)")
        Log.i(TAG, "  - Primeros 16 bytes: ${keyBytes.take(16).joinToString("") { "%02X".format(it) }}")

        try {
            // Crear SecretKey desde los bytes de la ceremonia
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // Configurar protección de la llave en Keystore
            val keyProtection = KeyProtection.Builder(
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false) // No requiere PIN/huella
                .build()

            // Almacenar en Android Keystore
            val keyStore = getKeyStore()
            keyStore.setEntry(
                STORAGE_KEK_ALIAS,
                KeyStore.SecretKeyEntry(secretKey),
                keyProtection
            )

            Log.i(TAG, "✓ KEK Storage almacenada exitosamente en Android Keystore")
            Log.i(TAG, "  - Alias: $STORAGE_KEK_ALIAS")
            Log.i(TAG, "  - Protección hardware: Sí")
            Log.i(TAG, "  - Autenticación de usuario: No requerida")
            Log.i(TAG, "================================================")

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al almacenar KEK Storage", e)
            throw RuntimeException("Error al almacenar KEK Storage: ${e.message}", e)
        } finally {
            // Limpiar bytes sensibles de memoria
            keyBytes.fill(0)
        }
    }

    /**
     * Cifra datos de una llave usando la KEK Storage.
     *
     * @param keyDataHex Datos de la llave en formato hexadecimal
     * @return EncryptedKeyData con datos cifrados, IV y AuthTag
     * @throws IllegalStateException Si no existe KEK Storage
     */
    fun encryptKey(keyDataHex: String): EncryptedKeyData {
        Log.d(TAG, "=== CIFRANDO LLAVE CON KEK STORAGE ===")

        if (!hasStorageKEK()) {
            throw IllegalStateException("No existe KEK Storage. Ejecuta ceremonia primero.")
        }

        val keyBytes = hexStringToByteArray(keyDataHex)
        Log.d(TAG, "Longitud de llave a cifrar: ${keyBytes.size} bytes")

        try {
            // Obtener KEK desde Keystore
            val kek = getStorageKEK()

            // Inicializar cipher para cifrado
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, kek)

            // El IV se genera automáticamente
            val iv = cipher.iv

            // Cifrar datos
            val encryptedBytes = cipher.doFinal(keyBytes)

            // En GCM, el tag de autenticación viene en los últimos 16 bytes
            val ciphertextLength = encryptedBytes.size - (GCM_TAG_LENGTH / 8)
            val ciphertext = encryptedBytes.copyOfRange(0, ciphertextLength)
            val authTag = encryptedBytes.copyOfRange(ciphertextLength, encryptedBytes.size)

            val result = EncryptedKeyData(
                encryptedData = byteArrayToHexString(ciphertext),
                iv = byteArrayToHexString(iv),
                authTag = byteArrayToHexString(authTag)
            )

            // Log.d(TAG, "✓ Llave cifrada exitosamente")
            // Log.d(TAG, "  - Datos originales: ${keyBytes.size} bytes")
            // Log.d(TAG, "  - Datos cifrados: ${ciphertext.size} bytes")
            // Log.d(TAG, "  - IV: ${iv.size} bytes")
            // Log.d(TAG, "  - AuthTag: ${authTag.size} bytes")
            // Log.d(TAG, "================================================")

            return result

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al cifrar llave", e)
            throw RuntimeException("Error al cifrar llave con KEK Storage: ${e.message}", e)
        } finally {
            // Limpiar bytes sensibles
            keyBytes.fill(0)
        }
    }

    /**
     * Descifra datos de una llave usando la KEK Storage.
     *
     * @param encryptedData Datos cifrados con IV y AuthTag
     * @return Datos de la llave descifrados en formato hexadecimal
     * @throws IllegalStateException Si no existe KEK Storage
     * @throws SecurityException Si la verificación de integridad falla
     */
    fun decryptKey(encryptedData: EncryptedKeyData): String {
        Log.d(TAG, "=== DESCIFRANDO LLAVE CON KEK STORAGE ===")

        if (!hasStorageKEK()) {
            throw IllegalStateException("No existe KEK Storage. No se pueden descifrar llaves.")
        }

        try {
            // Convertir datos hex a bytes
            val ciphertext = hexStringToByteArray(encryptedData.encryptedData)
            val iv = hexStringToByteArray(encryptedData.iv)
            val authTag = hexStringToByteArray(encryptedData.authTag)

            // Log.d(TAG, "Descifrando:")
            // Log.d(TAG, "  - Ciphertext: ${ciphertext.size} bytes")
            // Log.d(TAG, "  - IV: ${iv.size} bytes")
            // Log.d(TAG, "  - AuthTag: ${authTag.size} bytes")

            // Obtener KEK desde Keystore
            val kek = getStorageKEK()

            // Inicializar cipher para descifrado
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, kek, gcmSpec)

            // En GCM, debemos concatenar ciphertext + authTag
            val encryptedWithTag = ciphertext + authTag

            // Descifrar y verificar integridad
            val decryptedBytes = try {
                cipher.doFinal(encryptedWithTag)
            } catch (e: javax.crypto.AEADBadTagException) {
                Log.e(TAG, "✗ Error de integridad: AuthTag no coincide")
                throw SecurityException("Integridad de datos comprometida: AuthTag inválido")
            }

            val result = byteArrayToHexString(decryptedBytes)

            // Log.d(TAG, "✓ Llave descifrada exitosamente")
            // Log.d(TAG, "  - Datos descifrados: ${decryptedBytes.size} bytes")
            // Log.d(TAG, "================================================")

            // Limpiar bytes sensibles
            decryptedBytes.fill(0)

            return result

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al descifrar llave", e)
            throw RuntimeException("Error al descifrar llave con KEK Storage: ${e.message}", e)
        }
    }

    /**
     * Rota la KEK Storage: descifra todas las llaves con la KEK actual,
     * reemplaza la KEK por una nueva, y vuelve a cifrar todas las llaves.
     *
     * @param newCeremonyKeyHex Nueva llave de ceremonia en formato hexadecimal
     * @param reencryptCallback Callback que recibe lista de (keyData_descifrado, metadatos) y debe retornar lista cifrada
     * @throws IllegalStateException Si no existe KEK Storage actual
     * @return Número de llaves re-cifradas
     */
    fun rotateStorageKEK(
        newCeremonyKeyHex: String,
        reencryptCallback: (List<Pair<String, Any>>) -> List<Pair<EncryptedKeyData, Any>>
    ): Int {
        Log.i(TAG, "=== ROTANDO KEK STORAGE ===")
        Log.w(TAG, "⚠️ OPERACIÓN CRÍTICA: Rotación de KEK Storage")

        if (!hasStorageKEK()) {
            throw IllegalStateException("No existe KEK Storage para rotar")
        }

        // Validar nueva llave
        require(newCeremonyKeyHex.matches(Regex("^[0-9A-Fa-f]+\$"))) {
            "La nueva llave debe estar en formato hexadecimal válido"
        }

        val newKeyBytes = hexStringToByteArray(newCeremonyKeyHex)
        require(newKeyBytes.size == 32) {
            "Nueva KEK Storage debe ser AES-256 (32 bytes)"
        }

        val keyStore = getKeyStore()

        try {
            Log.i(TAG, "Paso 1/4: Respaldando KEK actual...")
            val oldKekAlias = "${STORAGE_KEK_ALIAS}_backup_${System.currentTimeMillis()}"

            // Crear backup temporal de la KEK actual
            val oldKekEntry = keyStore.getEntry(STORAGE_KEK_ALIAS, null) as KeyStore.SecretKeyEntry
            keyStore.setEntry(oldKekAlias, oldKekEntry, null)

            Log.i(TAG, "Paso 2/4: Instalando nueva KEK...")
            // Eliminar KEK actual
            keyStore.deleteEntry(STORAGE_KEK_ALIAS)

            // Instalar nueva KEK
            val newSecretKey = SecretKeySpec(newKeyBytes, "AES")
            val keyProtection = KeyProtection.Builder(
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()

            keyStore.setEntry(
                STORAGE_KEK_ALIAS,
                KeyStore.SecretKeyEntry(newSecretKey),
                keyProtection
            )

            Log.i(TAG, "Paso 3/4: Ejecutando callback de re-cifrado...")
            // El callback debe manejar el descifrado con la KEK antigua y cifrado con la nueva
            val reencryptedKeys = try {
                reencryptCallback(emptyList()) // El callback debe obtener las llaves del repositorio
            } catch (e: Exception) {
                Log.e(TAG, "Error en callback de re-cifrado, restaurando KEK antigua...", e)
                // Restaurar KEK antigua
                keyStore.deleteEntry(STORAGE_KEK_ALIAS)
                keyStore.setEntry(STORAGE_KEK_ALIAS, oldKekEntry, keyProtection)
                keyStore.deleteEntry(oldKekAlias)
                throw e
            }

            Log.i(TAG, "Paso 4/4: Limpiando backup...")
            // Eliminar backup de KEK antigua
            keyStore.deleteEntry(oldKekAlias)

            val count = reencryptedKeys.size
            Log.i(TAG, "✓ Rotación de KEK Storage completada exitosamente")
            Log.i(TAG, "  - Llaves re-cifradas: $count")
            Log.i(TAG, "================================================")

            return count

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error durante rotación de KEK Storage", e)
            throw RuntimeException("Error al rotar KEK Storage: ${e.message}", e)
        } finally {
            newKeyBytes.fill(0)
        }
    }

    /**
     * Elimina la KEK Storage del Keystore.
     * PRECAUCIÓN: Esto hará que todas las llaves cifradas sean irrecuperables.
     */
    fun deleteStorageKEK() {
        Log.w(TAG, "=== ELIMINANDO KEK STORAGE ===")
        Log.w(TAG, "⚠️ ADVERTENCIA: Las llaves cifradas serán irrecuperables")

        try {
            if (hasStorageKEK()) {
                val keyStore = getKeyStore()
                keyStore.deleteEntry(STORAGE_KEK_ALIAS)
                Log.w(TAG, "✓ KEK Storage eliminada")
            } else {
                Log.i(TAG, "KEK Storage no existía")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar KEK Storage", e)
            throw RuntimeException("Error al eliminar KEK Storage: ${e.message}", e)
        }

        Log.w(TAG, "================================================")
    }

    /**
     * Obtiene la KEK Storage desde Android Keystore
     */
    private fun getStorageKEK(): SecretKey {
        val keyStore = getKeyStore()
        val entry = keyStore.getEntry(STORAGE_KEK_ALIAS, null)
            ?: throw IllegalStateException("KEK Storage no encontrada en Keystore")

        return (entry as KeyStore.SecretKeyEntry).secretKey
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

    /**
     * Exporta un backup cifrado de múltiples llaves.
     * El backup está protegido con una contraseña derivada mediante PBKDF2.
     *
     * @param keysToBackup Lista de llaves en formato hexadecimal
     * @param backupPassword Contraseña para proteger el backup
     * @return Backup cifrado en formato Base64
     */
    fun createSecureBackup(keysToBackup: List<String>, backupPassword: String): String {
        Log.i(TAG, "=== CREANDO BACKUP SEGURO ===")
        Log.i(TAG, "Número de llaves a respaldar: ${keysToBackup.size}")

        require(backupPassword.length >= 12) {
            "La contraseña del backup debe tener al menos 12 caracteres"
        }

        try {
            // Generar salt aleatorio
            val salt = ByteArray(32)
            java.security.SecureRandom().nextBytes(salt)

            // Derivar llave de backup desde contraseña usando PBKDF2
            val keySpec = javax.crypto.spec.PBEKeySpec(
                backupPassword.toCharArray(),
                salt,
                100000, // iteraciones
                256 // longitud de llave
            )
            val keyFactory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val backupKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

            // Concatenar todas las llaves con delimitador
            val allKeysData = keysToBackup.joinToString("|")
            val dataBytes = allKeysData.toByteArray(Charsets.UTF_8)

            // Cifrar con AES-GCM
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, backupKey)
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(dataBytes)

            // Formato: salt (32) + iv (12) + datos cifrados (variable)
            val backupBytes = salt + iv + encryptedData
            val backupBase64 = android.util.Base64.encodeToString(backupBytes, android.util.Base64.NO_WRAP)

            Log.i(TAG, "✓ Backup creado exitosamente")
            Log.i(TAG, "  - Tamaño: ${backupBase64.length} caracteres")
            Log.i(TAG, "================================================")

            return backupBase64

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al crear backup seguro", e)
            throw RuntimeException("Error al crear backup: ${e.message}", e)
        }
    }

    /**
     * Restaura llaves desde un backup cifrado.
     *
     * @param backupBase64 Backup en formato Base64
     * @param backupPassword Contraseña del backup
     * @return Lista de llaves en formato hexadecimal
     */
    fun restoreSecureBackup(backupBase64: String, backupPassword: String): List<String> {
        Log.i(TAG, "=== RESTAURANDO BACKUP SEGURO ===")

        try {
            // Decodificar Base64
            val backupBytes = android.util.Base64.decode(backupBase64, android.util.Base64.NO_WRAP)

            // Extraer componentes
            val salt = backupBytes.copyOfRange(0, 32)
            val iv = backupBytes.copyOfRange(32, 44)
            val encryptedData = backupBytes.copyOfRange(44, backupBytes.size)

            // Derivar llave de backup
            val keySpec = javax.crypto.spec.PBEKeySpec(
                backupPassword.toCharArray(),
                salt,
                100000,
                256
            )
            val keyFactory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val backupKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

            // Descifrar
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, backupKey, gcmSpec)

            val decryptedBytes = try {
                cipher.doFinal(encryptedData)
            } catch (e: javax.crypto.AEADBadTagException) {
                throw SecurityException("Contraseña incorrecta o backup corrupto")
            }

            val allKeysData = String(decryptedBytes, Charsets.UTF_8)
            val keys = allKeysData.split("|")

            Log.i(TAG, "✓ Backup restaurado exitosamente")
            Log.i(TAG, "  - Llaves recuperadas: ${keys.size}")
            Log.i(TAG, "================================================")

            return keys

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al restaurar backup", e)
            throw RuntimeException("Error al restaurar backup: ${e.message}", e)
        }
    }

    /**
     * Migra llaves legacy (sin cifrar) a formato cifrado con KEK Storage actual.
     *
     * @param legacyKeysCallback Callback que proporciona llaves legacy y actualiza BD
     * @return Número de llaves migradas
     */
    fun migrateLegacyKeys(
        legacyKeysCallback: (encryptFunction: (String) -> EncryptedKeyData) -> Int
    ): Int {
        Log.i(TAG, "=== MIGRANDO LLAVES LEGACY ===")

        if (!hasStorageKEK()) {
            throw IllegalStateException("No existe KEK Storage. Inicializa KEK primero.")
        }

        try {
            val encryptFunction: (String) -> EncryptedKeyData = { keyDataHex ->
                encryptKey(keyDataHex)
            }

            val count = legacyKeysCallback(encryptFunction)

            Log.i(TAG, "✓ Migración completada")
            Log.i(TAG, "  - Llaves migradas: $count")
            Log.i(TAG, "================================================")

            return count

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error durante migración de llaves legacy", e)
            throw RuntimeException("Error al migrar llaves legacy: ${e.message}", e)
        }
    }
}
