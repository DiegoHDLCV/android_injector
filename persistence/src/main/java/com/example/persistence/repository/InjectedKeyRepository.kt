// Archivo: com/example/persistence/repository/InjectedKeyRepository.kt (Modificado)

package com.example.persistence.repository

import android.util.Log
import com.example.persistence.dao.InjectedKeyDao
import com.example.persistence.entities.InjectedKeyEntity
import com.vigatec.utils.security.StorageKeyManager
import com.vigatec.utils.security.EncryptedKeyData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class InjectedKeyRepository @Inject constructor(
    private val injectedKeyDao: InjectedKeyDao
) {
    private val TAG = "InjectedKeyRepository"

    /**
     * Obtiene todas las llaves inyectadas.
     * Si están cifradas y existe KEK Storage, las descifra automáticamente.
     */
    fun getAllInjectedKeys(): Flow<List<InjectedKeyEntity>> {
        return injectedKeyDao.getAllInjectedKeys().map { keys ->
            keys.map { key -> decryptKeyIfNeeded(key) }
        }
    }

    /**
     * Descifra una llave si está cifrada y existe KEK Storage.
     * Si es una llave legacy (sin cifrar), la retorna sin cambios.
     */
    @Suppress("DEPRECATION")
    private fun decryptKeyIfNeeded(key: InjectedKeyEntity): InjectedKeyEntity {
        // Si la llave no está cifrada o es legacy, devolverla sin cambios
        if (!key.isEncrypted()) {
            return key
        }

        // Si no existe KEK Storage, no podemos descifrar
        if (!StorageKeyManager.hasStorageKEK()) {
            Log.w(TAG, "Llave cifrada pero no existe KEK Storage: KCV=${key.kcv}")
            return key
        }

        return try {
            // Descifrar datos
            val encryptedData = EncryptedKeyData(
                encryptedData = key.encryptedKeyData,
                iv = key.encryptionIV,
                authTag = key.encryptionAuthTag
            )
            val decryptedKeyData = StorageKeyManager.decryptKey(encryptedData)

            // Retornar entidad con keyData descifrado
            key.copy(keyData = decryptedKeyData)
        } catch (e: Exception) {
            Log.e(TAG, "Error al descifrar llave KCV=${key.kcv}", e)
            key // Retornar llave sin descifrar en caso de error
        }
    }

    suspend fun recordKeyInjection(
        keySlot: Int,
        keyType: String,
        keyAlgorithm: String,
        kcv: String,
        status: String = "SUCCESSFUL"
    ) {
        try {
            val injectedKey = InjectedKeyEntity(
                keySlot = keySlot,
                keyType = keyType,
                keyAlgorithm = keyAlgorithm,
                kcv = kcv,
                status = status,
                injectionTimestamp = System.currentTimeMillis(),
            )
            injectedKeyDao.insertOrUpdate(injectedKey)
            Log.d(TAG, "Key injection recorded: Slot $keySlot, Type $keyType, Status $status")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording key injection", e)
            throw e
        }
    }

    /**
     * NUEVO MÉTODO: Registra la inyección de una llave con sus datos completos.
     * Este método es crucial para la ceremonia de llaves donde necesitamos guardar
     * la llave real, no solo el KCV.
     *
     * SEGURIDAD: Si existe KEK Storage, la llave se cifra automáticamente antes de guardarla.
     */
    suspend fun recordKeyInjectionWithData(
        keySlot: Int,
        keyType: String,
        keyAlgorithm: String,
        kcv: String,
        keyData: String,
        status: String = "SUCCESSFUL",
        isKEK: Boolean = false,
        kekType: String = "NONE",
        customName: String = ""
    ) {
        try {
            Log.i(TAG, "=== REGISTRANDO INYECCIÓN DE LLAVE CON DATOS COMPLETOS ===")
            Log.i(TAG, "Slot: $keySlot")
            Log.i(TAG, "Tipo: $keyType")
            Log.i(TAG, "Algoritmo: $keyAlgorithm")
            Log.i(TAG, "KCV: $kcv")
            Log.i(TAG, "Datos de llave (longitud): ${keyData.length / 2} bytes")
            Log.i(TAG, "Datos de llave (primeros 32 bytes): ${keyData.take(64)}")
            Log.i(TAG, "Estado: $status")
            Log.i(TAG, "Es KEK: $isKEK")
            Log.i(TAG, "Tipo de KEK: $kekType")
            Log.i(TAG, "Nombre personalizado: ${if (customName.isEmpty()) "(Sin nombre)" else customName}")

            // Determinar si debemos cifrar la llave
            val shouldEncrypt = StorageKeyManager.hasStorageKEK()
            Log.i(TAG, "¿Cifrar llave con KEK Storage?: $shouldEncrypt")

            val injectedKey = if (shouldEncrypt) {
                // Cifrar llave con KEK Storage
                Log.i(TAG, "Cifrando llave con KEK Storage...")
                val encryptedData = StorageKeyManager.encryptKey(keyData)

                InjectedKeyEntity(
                    keySlot = keySlot,
                    keyType = keyType,
                    keyAlgorithm = keyAlgorithm,
                    kcv = kcv,
                    keyData = "", // DEPRECATED - no se guarda en texto plano
                    encryptedKeyData = encryptedData.encryptedData,
                    encryptionIV = encryptedData.iv,
                    encryptionAuthTag = encryptedData.authTag,
                    status = status,
                    injectionTimestamp = System.currentTimeMillis(),
                    isKEK = isKEK,
                    kekType = kekType,
                    customName = customName
                )
            } else {
                // Guardar sin cifrar (modo legacy - no recomendado)
                Log.w(TAG, "⚠️ ADVERTENCIA: Guardando llave SIN CIFRAR (KEK Storage no disponible)")

                InjectedKeyEntity(
                    keySlot = keySlot,
                    keyType = keyType,
                    keyAlgorithm = keyAlgorithm,
                    kcv = kcv,
                    keyData = keyData, // Legacy: guardar en texto plano
                    encryptedKeyData = "",
                    encryptionIV = "",
                    encryptionAuthTag = "",
                    status = status,
                    injectionTimestamp = System.currentTimeMillis(),
                    isKEK = isKEK,
                    kekType = kekType,
                    customName = customName
                )
            }

            if (keyType == "CEREMONY_KEY") {
                Log.i(TAG, "Llave de ceremonia detectada - usando insertIfNotExists para evitar sobrescritura")
                val insertedId = injectedKeyDao.insertIfNotExists(injectedKey)
                if (insertedId > 0) {
                    Log.i(TAG, "✓ Llave de ceremonia registrada exitosamente con ID: $insertedId")
                    if (shouldEncrypt) {
                        Log.i(TAG, "✓ Llave cifrada y almacenada de forma segura")
                    }
                } else {
                    Log.w(TAG, "⚠️ Llave de ceremonia con KCV $kcv ya existe - no se sobrescribió")
                }
            } else {
                Log.i(TAG, "Llave regular detectada - usando insertOrUpdate para permitir actualizaciones")
                injectedKeyDao.insertOrUpdate(injectedKey)
                Log.i(TAG, "✓ Llave registrada/actualizada exitosamente en base de datos")
                if (shouldEncrypt) {
                    Log.i(TAG, "✓ Llave cifrada y almacenada de forma segura")
                } else {
                    Log.i(TAG, "✓ Datos de la llave guardados: ${keyData.length / 2} bytes (SIN CIFRAR)")
                }
                Log.i(TAG, "✓ KCV: $kcv")
            }
            Log.i(TAG, "================================================")

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al registrar inyección de llave con datos", e)
            Log.e(TAG, "Detalles del error:")
            Log.e(TAG, "  - Slot: $keySlot")
            Log.e(TAG, "  - Tipo: $keyType")
            Log.e(TAG, "  - KCV: $kcv")
            Log.e(TAG, "  - Longitud de datos: ${keyData.length / 2} bytes")
            throw e
        }
    }

    suspend fun insertOrUpdate(key: InjectedKeyEntity) {
        try {
            injectedKeyDao.insertOrUpdate(key)
            Log.d(TAG, "Key inserted/updated: ${key.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting/updating key", e)
            throw e
        }
    }

    suspend fun getInjectionCountToday(startOfDay: Long): Int {
        return try {
            injectedKeyDao.getInjectionCountToday(startOfDay)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting injection count for today", e)
            0
        }
    }

    suspend fun getSuccessfulInjectionCount(): Int {
        return try {
            injectedKeyDao.getSuccessfulInjectionCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting successful injection count", e)
            0
        }
    }

    suspend fun deleteKey(keyId: Long) {
        try {
            injectedKeyDao.deleteKey(keyId)
            Log.d(TAG, "Key deleted: $keyId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting key", e)
            throw e
        }
    }

    suspend fun deleteAllKeys() {
        try {
            injectedKeyDao.deleteAllKeys()
            Log.d(TAG, "All keys deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all keys", e)
            throw e
        }
    }

    suspend fun getKeyBySlotAndType(slot: Int, type: String): InjectedKeyEntity? {
        val key = injectedKeyDao.getKeyBySlotAndType(slot, type)
        return key?.let { decryptKeyIfNeeded(it) }
    }

    // --- FUNCIONES DE BORRADO MODIFICADAS / NUEVAS ---

    /**
     * Borra una llave específica de la base de datos.
     * Recibe la entidad completa para asegurar que se borra el registro correcto
     * usando su ID único.
     * @param key La entidad de la llave a eliminar.
     */
    suspend fun deleteKey(key: InjectedKeyEntity) {
        try {
            injectedKeyDao.deleteKey(key.id)
            Log.d(TAG, "Key deleted by entity: ${key.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting key by entity", e)
            throw e
        }
    }

    /**
     * NUEVA FUNCIÓN: Llama al DAO para actualizar el estado de todas las llaves.
     */
    suspend fun updateStatusForAllKeys(newStatus: String) {
        injectedKeyDao.updateStatusForAllKeys(newStatus)
    }

    suspend fun updateKeyStatus(key: InjectedKeyEntity, newStatus: String) {
        injectedKeyDao.updateKeyStatusById(key.id, newStatus)
    }

    /**
     * Busca una llave específica por su KCV (Key Check Value).
     * Descifra automáticamente si está cifrada.
     */
    suspend fun getKeyByKcv(kcv: String): InjectedKeyEntity? {
        val key = injectedKeyDao.getKeyByKcv(kcv)
        return key?.let { decryptKeyIfNeeded(it) }
    }

    /**
     * Actualiza el estado de una llave por su KCV.
     * Útil para marcar KEKs como EXPORTED o INACTIVE.
     */
    suspend fun updateKeyStatus(kcv: String, newStatus: String) {
        try {
            val key = injectedKeyDao.getKeyByKcv(kcv)
            if (key != null) {
                injectedKeyDao.updateKeyStatusById(key.id, newStatus)
                Log.d(TAG, "Estado de llave actualizado: KCV=$kcv, nuevo estado=$newStatus")
            } else {
                Log.w(TAG, "No se encontró llave con KCV=$kcv para actualizar estado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar estado de llave por KCV", e)
            throw e
        }
    }

    /**
     * Establece una llave específica como KTK activa.
     * Primero limpia cualquier KTK anterior y luego marca la nueva llave como KTK.
     */
    suspend fun setKeyAsKEK(kcv: String) {
        try {
            Log.i(TAG, "=== ESTABLECIENDO LLAVE COMO KTK ===")
            Log.i(TAG, "KCV de la nueva KTK: $kcv")
            
            // Primero limpiar cualquier KTK anterior
            injectedKeyDao.clearAllKEKFlags()
            Log.d(TAG, "KTK anterior desmarcada")
            
            // Establecer la nueva KTK
            injectedKeyDao.setKeyAsKEK(kcv)
            Log.i(TAG, "✓ Nueva KTK establecida: KCV=$kcv")
            Log.i(TAG, "================================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer KTK: KCV=$kcv", e)
            throw e
        }
    }

    /**
     * Quita el flag KTK de una llave específica.
     * La llave vuelve a ser operacional manteniendo su estado original.
     */
    suspend fun removeKeyAsKEK(kcv: String) {
        try {
            Log.i(TAG, "=== QUITANDO FLAG KTK ===")
            Log.i(TAG, "KCV de la llave: $kcv")
            
            injectedKeyDao.removeKeyAsKEK(kcv)
            Log.i(TAG, "✓ Flag KTK removido: KCV=$kcv")
            Log.i(TAG, "================================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al quitar flag KTK: KCV=$kcv", e)
            throw e
        }
    }

    /**
     * Obtiene la llave que está actualmente marcada como KTK activa.
     * Solo puede haber una KTK activa a la vez.
     * Descifra automáticamente si está cifrada.
     */
    suspend fun getCurrentKEK(): InjectedKeyEntity? {
        return try {
            val ktk = injectedKeyDao.getCurrentKEK()
            if (ktk != null) {
                Log.d(TAG, "KTK actual encontrada: KCV=${ktk.kcv}, Estado=${ktk.status}")
                decryptKeyIfNeeded(ktk)
            } else {
                Log.d(TAG, "No hay KTK activa actualmente")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener KTK actual", e)
            null
        }
    }

    /**
     * Migra todas las llaves legacy (sin cifrar) a formato cifrado.
     * Requiere que exista KEK Storage inicializada.
     *
     * @return Número de llaves migradas
     */
    @Suppress("DEPRECATION")
    suspend fun migrateLegacyKeysToEncrypted(): Int {
        Log.i(TAG, "=== INICIANDO MIGRACIÓN DE LLAVES LEGACY ===")

        if (!StorageKeyManager.hasStorageKEK()) {
            throw IllegalStateException("No existe KEK Storage. Inicializa KEK primero.")
        }

        var count = 0

        // Obtener todas las llaves
        val allKeys = injectedKeyDao.getAllInjectedKeysSync()

        allKeys.forEach { key ->
            // Si es legacy (tiene keyData pero no encryptedKeyData)
            if (key.isLegacy()) {
                try {
                    Log.i(TAG, "Migrando llave KCV=${key.kcv}...")

                    // Cifrar la llave
                    val encryptedData = StorageKeyManager.encryptKey(key.keyData)

                    // Actualizar en base de datos
                    val updatedKey = key.copy(
                        keyData = "", // Limpiar keyData legacy
                        encryptedKeyData = encryptedData.encryptedData,
                        encryptionIV = encryptedData.iv,
                        encryptionAuthTag = encryptedData.authTag
                    )

                    injectedKeyDao.insertOrUpdate(updatedKey)
                    count++

                    Log.i(TAG, "✓ Llave KCV=${key.kcv} migrada exitosamente")

                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error al migrar llave KCV=${key.kcv}", e)
                    // Continuar con la siguiente llave
                }
            }
        }

        Log.i(TAG, "✓ Migración completada: $count llaves migradas")
        return count
    }

    /**
     * Crea un backup cifrado de todas las llaves del almacén.
     *
     * @param backupPassword Contraseña para proteger el backup (mínimo 12 caracteres)
     * @return Backup en formato Base64
     */
    suspend fun createKeysBackup(backupPassword: String): String {
        Log.i(TAG, "=== CREANDO BACKUP DE LLAVES ===")

        val allKeys = injectedKeyDao.getAllInjectedKeysSync()
        val keysData = allKeys.map { key ->
            val keyData = if (key.isEncrypted()) {
                // Descifrar para backup
                val encData = EncryptedKeyData(
                    encryptedData = key.encryptedKeyData,
                    iv = key.encryptionIV,
                    authTag = key.encryptionAuthTag
                )
                StorageKeyManager.decryptKey(encData)
            } else {
                @Suppress("DEPRECATION")
                key.keyData
            }
            keyData
        }

        return StorageKeyManager.createSecureBackup(keysData, backupPassword)
    }

    /**
     * Obtiene el número de llaves legacy (sin cifrar) que necesitan migración.
     */
    @Suppress("DEPRECATION")
    suspend fun getLegacyKeysCount(): Int {
        val allKeys = injectedKeyDao.getAllInjectedKeysSync()
        return allKeys.count { it.isLegacy() }
    }

}