// Archivo: com/example/persistence/dao/InjectedKeyDao.kt

package com.example.persistence.dao

import androidx.room.*
import com.example.persistence.entities.InjectedKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InjectedKeyDao {

    /**
     * Inserta una nueva entidad de llave. Si ya existe una llave con el mismo
     * slot y tipo (gracias al índice único en la entidad), la reemplaza.
     * Esto es útil para actualizar el estado si una llave se reinyecta.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(key: InjectedKeyEntity)

    /**
     * Obtiene todas las llaves inyectadas de la base de datos, ordenadas por
     * fecha de inyección descendente, y las emite como un Flow para que la UI
     * se actualice automáticamente.
     */
    @Query("SELECT * FROM injected_keys ORDER BY injectionTimestamp DESC")
    fun getAllInjectedKeys(): Flow<List<InjectedKeyEntity>>

    /**
     * Busca una llave específica por su slot y tipo.
     */
    @Query("SELECT * FROM injected_keys WHERE keySlot = :slot AND keyType = :type LIMIT 1")
    suspend fun getKeyBySlotAndType(slot: Int, type: String): InjectedKeyEntity?

    /**
     * NUEVA FUNCIÓN: Borra una entidad de llave específica. Room utilizará el
     * primary key del objeto para encontrar y eliminar el registro correcto.
     */
    @Delete
    suspend fun delete(key: InjectedKeyEntity)

    /**
     * NUEVA FUNCIÓN: Elimina todos los registros de la tabla de llaves.
     */
    @Query("DELETE FROM injected_keys")
    suspend fun deleteAll()

    /**
     * NUEVA FUNCIÓN: Actualiza el campo 'status' para todos los registros en la tabla.
     * Útil para marcar todas las llaves como "DELETING" al iniciar un borrado masivo.
     * @param newStatus El nuevo estado que tendrán todas las llaves.
     */
    @Query("UPDATE injected_keys SET status = :newStatus")
    suspend fun updateStatusForAllKeys(newStatus: String)

    @Query("UPDATE injected_keys SET status = :newStatus WHERE id = :keyId")
    suspend fun updateKeyStatusById(keyId: Long, newStatus: String)
}