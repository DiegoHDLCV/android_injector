// Archivo: com/example/persistence/repository/InjectionLogRepository.kt

package com.vigatec.persistence.repository

import com.vigatec.persistence.dao.InjectionLogDao
import com.vigatec.persistence.entities.InjectionLogEntity
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
    @Suppress("UNUSED")
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
    @Suppress("UNUSED")
    fun getLogsByUsername(username: String): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getLogsByUsername(username)
    }

    /**
     * Obtiene logs filtrados por perfil.
     */
    @Suppress("UNUSED")
    fun getLogsByProfile(profileName: String): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getLogsByProfile(profileName)
    }

    /**
     * Obtiene logs filtrados por rango de fechas.
     */
    @Suppress("UNUSED")
    fun getLogsByDateRange(startTimestamp: Long, endTimestamp: Long): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getLogsByDateRange(startTimestamp, endTimestamp)
    }

    /**
     * Obtiene logs con múltiples filtros aplicados.
     */
    @Suppress("UNUSED")
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
    @Suppress("UNUSED")
    fun getLogsByStatus(status: String): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getLogsByStatus(status)
    }

    /**
     * Cuenta el total de logs registrados.
     */
    @Suppress("UNUSED")
    suspend fun getLogsCount(): Int {
        return injectionLogDao.getLogsCount()
    }

    /**
     * Cuenta logs exitosos para un usuario específico.
     */
    @Suppress("UNUSED")
    suspend fun getSuccessfulLogsCountByUser(username: String): Int {
        return injectionLogDao.getSuccessfulLogsCountByUser(username)
    }

    /**
     * Cuenta inyecciones exitosas realizadas desde una fecha específica.
     * Cuenta perfiles únicos inyectados hoy (no llaves individuales).
     * Útil para obtener estadísticas de inyecciones del día actual.
     */
    @Suppress("UNUSED")
    suspend fun getSuccessfulInjectionCountToday(startOfDay: Long): Int {
        return injectionLogDao.getSuccessfulInjectionCountToday(startOfDay)
    }

    /**
     * Obtiene logs exitosos desde una fecha específica como Flow.
     * Útil para observar cambios en tiempo real en el Dashboard.
     * El ViewModel puede mapear este Flow para contar perfiles únicos.
     */
    fun getSuccessfulLogsSince(startOfDay: Long): Flow<List<InjectionLogEntity>> {
        return injectionLogDao.getSuccessfulLogsSince(startOfDay)
    }

    /**
     * Obtiene un log específico por ID.
     */
    @Suppress("UNUSED")
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
    @Suppress("UNUSED")
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

    /**
     * Obtiene logs paginados para optimizar el rendimiento.
     * @param pageSize Número de logs por página
     * @param pageNumber Número de página (0-indexed)
     */
    suspend fun getLogsPaged(pageSize: Int, pageNumber: Int): List<InjectionLogEntity> {
        return injectionLogDao.getLogsPaged(limit = pageSize, offset = pageNumber * pageSize)
    }

    /**
     * Obtiene logs paginados con filtros aplicados.
     */
    suspend fun getLogsWithFiltersPaged(
        username: String? = null,
        profileName: String? = null,
        startTimestamp: Long? = null,
        endTimestamp: Long? = null,
        pageSize: Int,
        pageNumber: Int
    ): List<InjectionLogEntity> {
        return injectionLogDao.getLogsWithFiltersPaged(
            username = username,
            profileName = profileName,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            limit = pageSize,
            offset = pageNumber * pageSize
        )
    }

    /**
     * Cuenta el total de logs con filtros aplicados.
     */
    suspend fun getLogsCountWithFilters(
        username: String? = null,
        profileName: String? = null,
        startTimestamp: Long? = null,
        endTimestamp: Long? = null
    ): Int {
        return injectionLogDao.getLogsCountWithFilters(
            username = username,
            profileName = profileName,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp
        )
    }
}
