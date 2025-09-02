// Archivo: com/example/persistence/repository/InjectedKeyRepository.kt (Modificado)

package com.example.persistence.repository

import android.util.Log
import com.example.persistence.dao.InjectedKeyDao
import com.example.persistence.entities.InjectedKeyEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class InjectedKeyRepository @Inject constructor(
    private val injectedKeyDao: InjectedKeyDao
) {
    private val TAG = "InjectedKeyRepository"

    fun getAllInjectedKeys(): Flow<List<InjectedKeyEntity>> {
        return injectedKeyDao.getAllInjectedKeys()
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
     */
    suspend fun recordKeyInjectionWithData(
        keySlot: Int,
        keyType: String,
        keyAlgorithm: String,
        kcv: String,
        keyData: String,
        status: String = "SUCCESSFUL"
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
            
            val injectedKey = InjectedKeyEntity(
                keySlot = keySlot,
                keyType = keyType,
                keyAlgorithm = keyAlgorithm,
                kcv = kcv,
                keyData = keyData,
                status = status,
                injectionTimestamp = System.currentTimeMillis(),
            )
            
            // Para llaves de ceremonia, usar insertIfNotExists para evitar sobrescritura
            val insertedId = if (keyType == "CEREMONY_KEY") {
                Log.i(TAG, "Llave de ceremonia detectada - usando insertIfNotExists para evitar sobrescritura")
                injectedKeyDao.insertIfNotExists(injectedKey)
            } else {
                injectedKeyDao.insertOrUpdate(injectedKey)
                injectedKey.id
            }
            
            if (insertedId > 0) {
                Log.i(TAG, "✓ Llave registrada exitosamente en base de datos con ID: $insertedId")
                Log.i(TAG, "✓ Datos de la llave guardados: ${keyData.length / 2} bytes")
                Log.i(TAG, "✓ KCV validado: $kcv")
            } else {
                Log.w(TAG, "⚠️ Llave con KCV $kcv ya existe - no se sobrescribió")
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
        return injectedKeyDao.getKeyBySlotAndType(slot, type)
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
     */
    suspend fun getKeyByKcv(kcv: String): InjectedKeyEntity? {
        return injectedKeyDao.getKeyByKcv(kcv)
    }

}