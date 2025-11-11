// Archivo: com/example/persistence/dao/InjectionLogDao.kt

package com.vigatec.persistence.dao

import androidx.room.*
import com.vigatec.persistence.entities.InjectionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InjectionLogDao {

    /**
     * Inserta un nuevo registro de log.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: InjectionLogEntity): Long

    /**
     * Inserta múltiples registros de log.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<InjectionLogEntity>)

    /**
     * Obtiene todos los logs ordenados por timestamp descendente.
     */
    @Query("SELECT * FROM injection_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<InjectionLogEntity>>

    /**
     * Obtiene logs filtrados por usuario.
     */
    @Query("SELECT * FROM injection_logs WHERE username = :username ORDER BY timestamp DESC")
    fun getLogsByUsername(username: String): Flow<List<InjectionLogEntity>>

    /**
     * Obtiene logs filtrados por perfil.
     */
    @Query("SELECT * FROM injection_logs WHERE profileName = :profileName ORDER BY timestamp DESC")
    fun getLogsByProfile(profileName: String): Flow<List<InjectionLogEntity>>

    /**
     * Obtiene logs filtrados por rango de fechas.
     */
    @Query("SELECT * FROM injection_logs WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp ORDER BY timestamp DESC")
    fun getLogsByDateRange(startTimestamp: Long, endTimestamp: Long): Flow<List<InjectionLogEntity>>

    /**
     * Obtiene logs con múltiples filtros (usuario, perfil, rango de fechas).
     */
    @Query("""
        SELECT * FROM injection_logs
        WHERE (:username IS NULL OR username = :username)
        AND (:profileName IS NULL OR profileName = :profileName)
        AND (:startTimestamp IS NULL OR timestamp >= :startTimestamp)
        AND (:endTimestamp IS NULL OR timestamp <= :endTimestamp)
        ORDER BY timestamp DESC
    """)
    fun getLogsWithFilters(
        username: String?,
        profileName: String?,
        startTimestamp: Long?,
        endTimestamp: Long?
    ): Flow<List<InjectionLogEntity>>

    /**
     * Obtiene logs por estado de operación.
     */
    @Query("SELECT * FROM injection_logs WHERE operationStatus = :status ORDER BY timestamp DESC")
    fun getLogsByStatus(status: String): Flow<List<InjectionLogEntity>>

    /**
     * Cuenta el total de logs registrados.
     */
    @Query("SELECT COUNT(*) FROM injection_logs")
    suspend fun getLogsCount(): Int

    /**
     * Cuenta logs exitosos para un usuario específico.
     */
    @Query("SELECT COUNT(*) FROM injection_logs WHERE username = :username AND operationStatus = 'SUCCESS'")
    suspend fun getSuccessfulLogsCountByUser(username: String): Int

    /**
     * Obtiene un log específico por ID.
     */
    @Query("SELECT * FROM injection_logs WHERE id = :logId LIMIT 1")
    suspend fun getLogById(logId: Long): InjectionLogEntity?

    /**
     * Elimina un log específico.
     */
    @Delete
    suspend fun deleteLog(log: InjectionLogEntity)

    /**
     * Elimina logs más antiguos que una fecha específica.
     */
    @Query("DELETE FROM injection_logs WHERE timestamp < :timestamp")
    suspend fun deleteLogsOlderThan(timestamp: Long): Int

    /**
     * Elimina todos los logs (usar con precaución).
     */
    @Query("DELETE FROM injection_logs")
    suspend fun deleteAllLogs()

    /**
     * Obtiene los usuarios únicos que tienen logs registrados.
     */
    @Query("SELECT DISTINCT username FROM injection_logs ORDER BY username ASC")
    suspend fun getDistinctUsernames(): List<String>

    /**
     * Obtiene los perfiles únicos que tienen logs registrados.
     */
    @Query("SELECT DISTINCT profileName FROM injection_logs ORDER BY profileName ASC")
    suspend fun getDistinctProfiles(): List<String>
}
