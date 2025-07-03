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
        keyData: String = "",
        status: String
    ) {
        val newRecord = InjectedKeyEntity(
            keySlot = keySlot,
            keyType = keyType,
            keyAlgorithm = keyAlgorithm,
            kcv = kcv,
            keyData = keyData,
            status = status
        )
        injectedKeyDao.insertOrUpdate(newRecord)
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
        injectedKeyDao.delete(key)
    }

    /**
     * Borra todas las llaves de la base de datos.
     * Es una operación destructiva que limpia toda la tabla.
     */
    suspend fun deleteAllKeys() {
        // --- LOG AÑADIDO ---
        Log.i(TAG, "deleteAllKeys: Solicitud recibida para borrar todos los registros del DAO.")
        injectedKeyDao.deleteAll()
        // --- LOG AÑADIDO ---
        Log.d(TAG, "deleteAllKeys: Llamada a injectedKeyDao.deleteAll() completada.")
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