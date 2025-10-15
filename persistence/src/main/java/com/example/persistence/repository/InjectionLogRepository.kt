// Archivo: com/example/persistence/repository/InjectionLogRepository.kt

package com.example.persistence.repository

import com.example.persistence.dao.InjectionLogDao
import com.example.persistence.entities.InjectionLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InjectionLogRepository @Inject constructor(
    private val injectionLogDao: InjectionLogDao
) {

    /**
     * Inserta un nuevo registro de log.
     */
    suspend fun insertLog(log: InjectionLogEntity): Long {
        return injectionLogDao.insertLog(log)
    }

    /**
     * Inserta múltiples registros de log.
     */
    suspend fun insertLogs(logs: List<InjectionLogEntity>) {
        injectionLogDao.insertLogs(logs)
    }

    /**
     * Obtiene todos los logs ordenados por timestamp descendente.
     */
    fun getAllLogs(): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getAllLogs()
    }

    /**
     * Obtiene logs filtrados por usuario.
     */
    fun getLogsByUsername(username: String): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getLogsByUsername(username)
    }

    /**
     * Obtiene logs filtrados por perfil.
     */
    fun getLogsByProfile(profileName: String): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getLogsByProfile(profileName)
    }

    /**
     * Obtiene logs filtrados por rango de fechas.
     */
    fun getLogsByDateRange(startTimestamp: Long, endTimestamp: Long): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getLogsByDateRange(startTimestamp, endTimestamp)
    }

    /**
     * Obtiene logs con múltiples filtros aplicados.
     */
    fun getLogsWithFilters(
        username: String? = null,
        profileName: String? = null,
        startTimestamp: Long? = null,
        endTimestamp: Long? = null
    ): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getLogsWithFilters(username, profileName, startTimestamp, endTimestamp)
    }

    /**
     * Obtiene logs por estado de operación.
     */
    fun getLogsByStatus(status: String): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getLogsByStatus(status)
    }

    /**
     * Cuenta el total de logs registrados.
     */
    suspend fun getLogsCount(): Int {
        return injectionLogDao.getLogsCount()
    }

    /**
     * Cuenta logs exitosos para un usuario específico.
     */
    suspend fun getSuccessfulLogsCountByUser(username: String): Int {
        return injectionLogDao.getSuccessfulLogsCountByUser(username)
    }

    /**
     * Obtiene un log específico por ID.
     */
    suspend fun getLogById(logId: Long): InjectionLogEntity? {
        return injectionLogDao.getLogById(logId)
    }

    /**
     * Elimina un log específico.
     */
    suspend fun deleteLog(log: InjectionLogEntity) {
        injectionLogDao.deleteLog(log)
    }

    /**
     * Elimina logs más antiguos que una fecha específica.
     */
    suspend fun deleteLogsOlderThan(timestamp: Long): Int {
        return injectionLogDao.deleteLogsOlderThan(timestamp)
    }

    /**
     * Elimina todos los logs (usar con precaución).
     */
    suspend fun deleteAllLogs() {
        injectionLogDao.deleteAllLogs()
    }

    /**
     * Obtiene los usuarios únicos que tienen logs registrados.
     */
    suspend fun getDistinctUsernames(): List<String> {
        return injectionLogDao.getDistinctUsernames()
    }

    /**
     * Obtiene los perfiles únicos que tienen logs registrados.
     */
    suspend fun getDistinctProfiles(): List<String> {
        return injectionLogDao.getDistinctProfiles()
    }
}
