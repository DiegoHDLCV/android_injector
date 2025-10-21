// Archivo: com/example/persistence/dao/InjectedKeyDao.kt

package com.example.persistence.dao

import androidx.room.*
import com.example.persistence.entities.InjectedKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InjectedKeyDao {

    /**
     * Inserta una nueva entidad de llave. Si ya existe una llave con el mismo
     * KCV (gracias al índice único en KCV), la reemplaza.
     * Esto es útil para actualizar el estado si una llave se reinyecta.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(key: InjectedKeyEntity)

    /**
     * Inserta una nueva entidad de llave. Si ya existe una llave con el mismo
     * KCV, la ignora (no la sobrescribe). Útil para llaves de ceremonia.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(key: InjectedKeyEntity): Long

    /**
     * Actualiza una llave existente en la base de datos.
     * Útil para re-cifrar llaves durante rotación de KEK.
     */
    @Update
    suspend fun update(key: InjectedKeyEntity)

    /**
     * Obtiene todas las llaves inyectadas de la base de datos, ordenadas por
     * fecha de inyección descendente, y las emite como un Flow para que la UI
     * se actualice automáticamente cuando cambien los datos.
     */
    @Query("SELECT * FROM injected_keys ORDER BY injectionTimestamp DESC")
    fun getAllInjectedKeys(): Flow<List<InjectedKeyEntity>>

    /**
     * Obtiene todas las llaves inyectadas de forma síncrona.
     * Útil para operaciones de migración y backup.
     */
    @Query("SELECT * FROM injected_keys ORDER BY injectionTimestamp DESC")
    suspend fun getAllInjectedKeysSync(): List<InjectedKeyEntity>

    /**
     * Busca una llave específica por su slot y tipo.
     */
    @Query("SELECT * FROM injected_keys WHERE keySlot = :slot AND keyType = :type LIMIT 1")
    suspend fun getKeyBySlotAndType(slot: Int, type: String): InjectedKeyEntity?

    /**
     * Cuenta las inyecciones realizadas desde una fecha específica.
     * Útil para obtener estadísticas de inyecciones del día actual.
     */
    @Query("SELECT COUNT(*) FROM injected_keys WHERE injectionTimestamp >= :startOfDay")
    suspend fun getInjectionCountToday(startOfDay: Long): Int

    /**
     * Cuenta las llaves que fueron inyectadas exitosamente.
     * Útil para estadísticas de éxito en las inyecciones.
     */
    @Query("SELECT COUNT(*) FROM injected_keys WHERE status = 'SUCCESSFUL'")
    suspend fun getSuccessfulInjectionCount(): Int

    /**
     * Elimina una llave específica por su ID.
     */
    @Delete
    suspend fun deleteKey(key: InjectedKeyEntity)

    /**
     * Elimina una llave específica por su ID.
     */
    @Query("DELETE FROM injected_keys WHERE id = :keyId")
    suspend fun deleteKey(keyId: Long)

    /**
     * Elimina todas las llaves de la base de datos.
     * Operación destructiva que limpia toda la tabla.
     */
    @Query("DELETE FROM injected_keys")
    suspend fun deleteAllKeys()

    /**
     * Obtiene una llave específica por su KCV (Key Check Value).
     */
    @Query("SELECT * FROM injected_keys WHERE kcv = :kcv LIMIT 1")
    suspend fun getKeyByKcv(kcv: String): InjectedKeyEntity?

    /**
     * NUEVA FUNCIÓN: Actualiza el campo 'status' para todos los registros en la tabla.
     * Útil para marcar todas las llaves como "DELETING" al iniciar un borrado masivo.
     * @param newStatus El nuevo estado que tendrán todas las llaves.
     */
    @Query("UPDATE injected_keys SET status = :newStatus")
    suspend fun updateStatusForAllKeys(newStatus: String)

    @Query("UPDATE injected_keys SET status = :newStatus WHERE id = :keyId")
    suspend fun updateKeyStatusById(keyId: Long, newStatus: String)

    /**
     * Limpia el flag isKEK de todas las llaves (establece todas como operacionales).
     * Útil antes de establecer una nueva KEK.
     */
    @Query("UPDATE injected_keys SET isKEK = 0 WHERE isKEK = 1")
    suspend fun clearAllKEKFlags()

    /**
     * Establece una llave específica como KEK activa.
     * Marca la llave como KEK y establece su estado como ACTIVE.
     */
    @Query("UPDATE injected_keys SET isKEK = 1, status = 'ACTIVE' WHERE kcv = :kcv")
    suspend fun setKeyAsKEK(kcv: String)

    /**
     * Quita el flag KEK de una llave específica.
     * La llave vuelve a ser operacional manteniendo su estado original.
     */
    @Query("UPDATE injected_keys SET isKEK = 0 WHERE kcv = :kcv")
    suspend fun removeKeyAsKEK(kcv: String)

    /**
     * Obtiene la llave que está actualmente marcada como KEK activa.
     * Solo puede haber una KEK activa a la vez.
     */
    @Query("SELECT * FROM injected_keys WHERE isKEK = 1 LIMIT 1")
    suspend fun getCurrentKEK(): InjectedKeyEntity?

}