// Archivo: com/example/persistence/repository/InjectedKeyRepository.kt (Modificado)

package com.vigatec.persistence.repository

import android.util.Log
import com.vigatec.persistence.dao.InjectedKeyDao
import com.vigatec.persistence.entities.InjectedKeyEntity
import com.vigatec.persistence.model.DeletionReason
import com.vigatec.persistence.model.KeyDeletionValidation
import com.vigatec.persistence.model.MultipleKeysDeletionValidation
import com.vigatec.persistence.model.BlockedKeyInfo
import com.vigatec.utils.security.StorageKeyManager
import com.vigatec.utils.security.EncryptedKeyData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class InjectedKeyRepository @Inject constructor(
    private val injectedKeyDao: InjectedKeyDao,
    private val profileRepository: ProfileRepository
) {
    private val TAG = "InjectedKeyRepository"

    /**
     * Obtiene todas las llaves inyectadas.
     * Si están cifradas y existe KEK Storage, las descifra automáticamente.
     */
    fun getAllInjectedKeys(): Flow<List<InjectedKeyEntity>> {
        return injectedKeyDao.getAllInjectedKeys().map { keys ->
            Log.d(TAG, "=== CONSULTA BD - getAllInjectedKeys ===")
            Log.d(TAG, "Total de llaves consultadas: ${keys.size}")
            keys.forEachIndexed { index, key ->
                Log.d(TAG, "BD Consulta $index: Slot=${key.keySlot}, Tipo=${key.keyType}, isKEK=${key.isKEK}, kekType='${key.kekType}', KCV=${key.kcv}")
            }
            Log.d(TAG, "=== FIN CONSULTA BD ===")
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
            // IMPORTANTE: Las KEK Storage NO se cifran (se guardan en Android Keystore, no en BD cifradas)
            val shouldEncrypt = StorageKeyManager.hasStorageKEK() && kekType != "KEK_STORAGE"
            Log.i(TAG, "¿Cifrar llave con KEK Storage?: $shouldEncrypt")
            if (kekType == "KEK_STORAGE") {
                Log.i(TAG, "Esta es una KEK Storage - NO se cifrará (se guarda en Android Keystore)")
            }

            val injectedKey = if (shouldEncrypt) {
                // Cifrar llave con KEK Storage
                Log.i(TAG, "Cifrando llave con KEK Storage...")
                val encryptedData = StorageKeyManager.encryptKey(keyData)

                // Log de llave cifrada
                Log.i(TAG, "✓ Llave cifrada exitosamente:")
                Log.i(TAG, "  - Datos cifrados (primeros 64 bytes): ${encryptedData.encryptedData.take(128)}")
                Log.i(TAG, "  - IV (${encryptedData.iv.length / 2} bytes): ${encryptedData.iv}")
                Log.i(TAG, "  - AuthTag (${encryptedData.authTag.length / 2} bytes): ${encryptedData.authTag}")
                Log.i(TAG, "  - Longitud total cifrada: ${encryptedData.encryptedData.length / 2} bytes")

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

            // Log de la entidad antes de guardar
            Log.i(TAG, "=== ENTIDAD A GUARDAR ===")
            Log.i(TAG, "  - keySlot: ${injectedKey.keySlot}")
            Log.i(TAG, "  - keyType: ${injectedKey.keyType}")
            Log.i(TAG, "  - kekType: ${injectedKey.kekType}")
            Log.i(TAG, "  - isKEK: ${injectedKey.isKEK}")
            Log.i(TAG, "  - status: ${injectedKey.status}")
            Log.i(TAG, "=========================")

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
                Log.i(TAG, "Llave detectada - usando insertOrUpdate (KCVs duplicados permitidos por propósito)")
                injectedKeyDao.insertOrUpdate(injectedKey)
                Log.i(TAG, "✓ Llave registrada/actualizada exitosamente en base de datos")
                
                if (shouldEncrypt) {
                    Log.i(TAG, "✓ Llave cifrada y almacenada de forma segura")
                } else {
                    Log.i(TAG, "✓ Datos de la llave guardados: ${keyData.length / 2} bytes (SIN CIFRAR)")
                }
                Log.i(TAG, "✓ KCV: $kcv, kekType: ${injectedKey.kekType}")
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

    @Suppress("UNUSED")
    suspend fun getInjectionCountToday(startOfDay: Long): Int {
        return try {
            injectedKeyDao.getInjectionCountToday(startOfDay)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting injection count for today", e)
            0
        }
    }

    @Suppress("UNUSED")
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
     * Valida si una llave puede ser eliminada de forma segura.
     *
     * Verifica:
     * 1. Si la llave está siendo usada en algún perfil
     * 2. Si la llave es la KEK Storage activa
     * 3. Si la llave es la KTK activa
     *
     * @param key La llave a validar para eliminación
     * @return Validación con resultado y razón si no se puede eliminar
     */
    suspend fun validateKeyDeletion(key: InjectedKeyEntity): KeyDeletionValidation {
        return try {
            Log.d(TAG, "=== VALIDANDO ELIMINACIÓN DE LLAVE ===")
            Log.d(TAG, "KCV: ${key.kcv}, ID: ${key.id}, Tipo: ${key.keyType}")

            // 1. Verificar si está en perfiles
            val assignedProfiles = profileRepository.getProfileNamesByKeyKcv(key.kcv)
            Log.d(TAG, "Perfiles asignados: ${assignedProfiles.size}")
            assignedProfiles.forEach { Log.d(TAG, "  - Perfil: $it") }

            // 2. Verificar si es KEK Storage activa
            val isActiveKEKStorage = key.isKEKStorage() && key.status == "ACTIVE"
            Log.d(TAG, "¿Es KEK Storage activa?: $isActiveKEKStorage")

            // 3. Verificar si es KTK activa
            val currentKTK = getCurrentKTK()
            val isActiveKTK = currentKTK?.kcv == key.kcv && currentKTK.status == "ACTIVE"
            Log.d(TAG, "¿Es KTK activa?: $isActiveKTK")

            // Determinar si se puede eliminar
            val (canDelete, reason) = when {
                assignedProfiles.isNotEmpty() && (isActiveKEKStorage || isActiveKTK) -> {
                    Pair(false, DeletionReason.MULTIPLE_USES)
                }
                assignedProfiles.isNotEmpty() -> {
                    Pair(false, DeletionReason.IN_USE_BY_PROFILES)
                }
                isActiveKEKStorage -> {
                    Pair(false, DeletionReason.IS_ACTIVE_KEK_STORAGE)
                }
                isActiveKTK -> {
                    Pair(false, DeletionReason.IS_ACTIVE_KTK)
                }
                else -> {
                    Pair(true, DeletionReason.OK)
                }
            }

            Log.d(TAG, "Resultado: canDelete=$canDelete, reason=$reason")
            Log.d(TAG, "=== FIN VALIDACIÓN ===")

            KeyDeletionValidation(
                canDelete = canDelete,
                reason = reason,
                assignedProfiles = assignedProfiles,
                isActiveKEKStorage = isActiveKEKStorage,
                isActiveKTK = isActiveKTK
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error validando eliminación de llave KCV=${key.kcv}", e)
            // En caso de error, no permitir eliminación por seguridad
            KeyDeletionValidation(
                canDelete = false,
                reason = DeletionReason.OK,
                assignedProfiles = emptyList()
            )
        }
    }

    /**
     * Valida si todas las llaves pueden ser eliminadas de forma segura.
     * Verifica cada llave individualmente y retorna información sobre las que no se pueden eliminar.
     *
     * @return Validación con información sobre qué llaves no se pueden eliminar y por qué
     */
    suspend fun validateAllKeysDeletion(): MultipleKeysDeletionValidation {
        return try {
            Log.d(TAG, "=== VALIDANDO ELIMINACIÓN DE TODAS LAS LLAVES ===")
            
            // Obtener todas las llaves de forma síncrona
            val allKeys = injectedKeyDao.getAllInjectedKeysSync()
            Log.d(TAG, "Total de llaves a validar: ${allKeys.size}")
            
            val blockedKeys = mutableListOf<BlockedKeyInfo>()
            var deletableCount = 0
            
            // Validar cada llave
            allKeys.forEach { key ->
                val validation = validateKeyDeletion(key)
                
                if (!validation.canDelete) {
                    Log.d(TAG, "Llave bloqueada: KCV=${key.kcv}, Razón=${validation.reason}")
                    blockedKeys.add(
                        BlockedKeyInfo(
                            kcv = key.kcv,
                            keyType = key.keyType,
                            reason = validation.reason,
                            assignedProfiles = validation.assignedProfiles,
                            isActiveKEKStorage = validation.isActiveKEKStorage,
                            isActiveKTK = validation.isActiveKTK
                        )
                    )
                } else {
                    deletableCount++
                }
            }
            
            val canDeleteAll = blockedKeys.isEmpty()
            Log.d(TAG, "Resultado validación: canDeleteAll=$canDeleteAll, bloqueadas=${blockedKeys.size}, eliminables=$deletableCount")
            Log.d(TAG, "=== FIN VALIDACIÓN ===")
            
            MultipleKeysDeletionValidation(
                canDeleteAll = canDeleteAll,
                blockedKeys = blockedKeys,
                totalKeys = allKeys.size,
                deletableKeys = deletableCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error validando eliminación de todas las llaves", e)
            // En caso de error, no permitir eliminación por seguridad
            MultipleKeysDeletionValidation(
                canDeleteAll = false,
                blockedKeys = emptyList(),
                totalKeys = 0,
                deletableKeys = 0
            )
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
     * Establece una llave específica como KEK Storage activa.
     * Primero limpia cualquier KEK anterior y luego marca la nueva llave como KEK.
     * USADO POR EL MÓDULO INJECTOR PARA KEK STORAGE.
     */
    @Suppress("UNUSED")
    suspend fun setKeyAsKEK(kcv: String) {
        try {
            Log.i(TAG, "=== ESTABLECIENDO LLAVE COMO KEK STORAGE ===")
            Log.i(TAG, "KCV de la nueva KEK Storage: $kcv")
            
            // Primero limpiar cualquier KEK anterior
            injectedKeyDao.clearAllKEKFlags()
            Log.d(TAG, "KEK Storage anterior desmarcada")
            
            // Establecer la nueva KEK Storage
            injectedKeyDao.setKeyAsKEK(kcv)
            Log.i(TAG, "✓ Nueva KEK Storage establecida: KCV=$kcv")
            Log.i(TAG, "================================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer KEK Storage: KCV=$kcv", e)
            throw e
        }
    }

    /**
     * Establece una llave específica como KTK activa.
     * Primero limpia cualquier KTK anterior y luego marca la nueva llave como KTK.
     * USADO POR EL MÓDULO KEYRECEIVER PARA KTK.
     */
    suspend fun setKeyAsKTK(kcv: String) {
        try {
            Log.i(TAG, "=== ESTABLECIENDO LLAVE COMO KTK ===")
            Log.i(TAG, "KCV de la nueva KTK: $kcv")
            
            // Primero limpiar cualquier KTK anterior
            injectedKeyDao.clearAllKTKFlags()
            Log.d(TAG, "KTK anterior desmarcada")
            
            // Establecer la nueva KTK
            injectedKeyDao.setKeyAsKTK(kcv)
            Log.i(TAG, "✓ Nueva KTK establecida: KCV=$kcv")
            Log.i(TAG, "================================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer KTK: KCV=$kcv", e)
            throw e
        }
    }

    /**
     * Quita el flag KEK Storage de una llave específica.
     * La llave vuelve a ser operacional manteniendo su estado original.
     * USADO POR EL MÓDULO INJECTOR PARA KEK STORAGE.
     */
    @Suppress("UNUSED")
    suspend fun removeKeyAsKEK(kcv: String) {
        try {
            Log.i(TAG, "=== QUITANDO FLAG KEK STORAGE ===")
            Log.i(TAG, "KCV de la llave: $kcv")
            
            injectedKeyDao.removeKeyAsKEK(kcv)
            Log.i(TAG, "✓ Flag KEK Storage removido: KCV=$kcv")
            Log.i(TAG, "================================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al quitar flag KEK Storage: KCV=$kcv", e)
            throw e
        }
    }

    /**
     * Quita el flag KTK de una llave específica.
     * La llave vuelve a ser operacional manteniendo su estado original.
     * USADO POR EL MÓDULO KEYRECEIVER PARA KTK.
     */
    suspend fun removeKeyAsKTK(kcv: String) {
        try {
            Log.i(TAG, "=== QUITANDO FLAG KTK ===")
            Log.i(TAG, "KCV de la llave: $kcv")
            
            injectedKeyDao.removeKeyAsKTK(kcv)
            Log.i(TAG, "✓ Flag KTK removido: KCV=$kcv")
            Log.i(TAG, "================================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error al quitar flag KTK: KCV=$kcv", e)
            throw e
        }
    }

    /**
     * Obtiene la llave que está actualmente marcada como KEK Storage activa.
     * Solo puede haber una KEK Storage activa a la vez.
     * Descifra automáticamente si está cifrada.
     * USADO POR EL MÓDULO INJECTOR PARA KEK STORAGE.
     */
    @Suppress("UNUSED")
    suspend fun getCurrentKEK(): InjectedKeyEntity? {
        return try {
            val kek = injectedKeyDao.getCurrentKEK()
            if (kek != null) {
                Log.d(TAG, "KEK Storage actual encontrada: KCV=${kek.kcv}, Estado=${kek.status}")
                decryptKeyIfNeeded(kek)
            } else {
                Log.d(TAG, "No hay KEK Storage activa actualmente")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener KEK Storage actual", e)
            null
        }
    }

    /**
     * Obtiene la llave que está actualmente marcada como KTK activa.
     * Solo puede haber una KTK activa a la vez.
     * Descifra automáticamente si está cifrada.
     * USADO POR EL MÓDULO KEYRECEIVER PARA KTK.
     */
    suspend fun getCurrentKTK(): InjectedKeyEntity? {
        return try {
            val ktk = injectedKeyDao.getCurrentKTK()
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
    @Suppress("DEPRECATION", "UNUSED")
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
    @Suppress("UNUSED")
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
    @Suppress("DEPRECATION", "UNUSED")
    suspend fun getLegacyKeysCount(): Int {
        val allKeys = injectedKeyDao.getAllInjectedKeysSync()
        return allKeys.count { it.isLegacy() }
    }

    /**
     * Obtiene todas las llaves de forma síncrona.
     * Útil para operaciones de rotación de KEK y migración.
     */
    suspend fun getAllInjectedKeysSync(): List<InjectedKeyEntity> {
        return injectedKeyDao.getAllInjectedKeysSync()
    }

    /**
     * Actualiza una llave existente en la base de datos.
     * Útil para re-cifrar llaves durante rotación de KEK.
     */
    suspend fun updateInjectedKey(key: InjectedKeyEntity) {
        try {
            injectedKeyDao.update(key)
            Log.d(TAG, "Key updated: ${key.id}, KCV=${key.kcv}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating key", e)
            throw e
        }
    }

    /**
     * Verifica si ya existe una llave con el KCV especificado.
     * Útil para detectar duplicados antes de intentar guardar en ceremonia.
     * @return true si existe una llave con ese KCV, false en caso contrario
     */
    suspend fun existsKeyWithKcv(kcv: String): Boolean {
        return try {
            val count = injectedKeyDao.existsKeyWithKcv(kcv)
            Log.d(TAG, "Verificando llave con KCV $kcv: existe=${ count > 0}")
            count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando llave con KCV $kcv", e)
            false
        }
    }

}