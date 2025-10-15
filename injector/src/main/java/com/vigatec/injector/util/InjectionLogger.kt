package com.vigatec.injector.util

import com.example.persistence.entities.InjectionLogEntity
import com.example.persistence.repository.InjectionLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utilidad para registrar logs de operaciones de inyección.
 * Esta clase facilita el registro de comandos enviados y respuestas recibidas
 * durante el proceso de inyección de llaves.
 */
@Singleton
class InjectionLogger @Inject constructor(
    private val injectionLogRepository: InjectionLogRepository
) {

    /**
     * Registra un log de inyección.
     *
     * @param commandSent Comando enviado al dispositivo
     * @param responseReceived Respuesta recibida del dispositivo
     * @param operationStatus Estado de la operación (SUCCESS, FAILED, ERROR)
     * @param username Usuario que realizó la operación
     * @param profileName Nombre del perfil utilizado
     * @param keyType Tipo de llave (opcional)
     * @param keySlot Slot de la llave (opcional, -1 si no aplica)
     * @param deviceInfo Información del dispositivo (opcional)
     * @param notes Notas adicionales (opcional)
     */
    fun logInjection(
        commandSent: String,
        responseReceived: String,
        operationStatus: String,
        username: String,
        profileName: String,
        keyType: String = "",
        keySlot: Int = -1,
        deviceInfo: String = "",
        notes: String = ""
    ) {
        // Usar un scope de IO para no bloquear el hilo principal
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val logEntity = InjectionLogEntity(
                    commandSent = commandSent,
                    responseReceived = responseReceived,
                    operationStatus = operationStatus,
                    username = username,
                    profileName = profileName,
                    keyType = keyType,
                    keySlot = keySlot,
                    timestamp = System.currentTimeMillis(),
                    deviceInfo = deviceInfo,
                    notes = notes
                )
                injectionLogRepository.insertLog(logEntity)
            } catch (e: Exception) {
                // Log silencioso en caso de error
                android.util.Log.e("InjectionLogger", "Error al registrar log: ${e.message}")
            }
        }
    }

    /**
     * Registra un log de inyección exitosa.
     */
    fun logSuccess(
        commandSent: String,
        responseReceived: String,
        username: String,
        profileName: String,
        keyType: String = "",
        keySlot: Int = -1,
        notes: String = ""
    ) {
        logInjection(
            commandSent = commandSent,
            responseReceived = responseReceived,
            operationStatus = "SUCCESS",
            username = username,
            profileName = profileName,
            keyType = keyType,
            keySlot = keySlot,
            notes = notes
        )
    }

    /**
     * Registra un log de inyección fallida.
     */
    fun logFailure(
        commandSent: String,
        responseReceived: String,
        username: String,
        profileName: String,
        keyType: String = "",
        keySlot: Int = -1,
        notes: String = ""
    ) {
        logInjection(
            commandSent = commandSent,
            responseReceived = responseReceived,
            operationStatus = "FAILED",
            username = username,
            profileName = profileName,
            keyType = keyType,
            keySlot = keySlot,
            notes = notes
        )
    }

    /**
     * Registra un log de error en la operación.
     */
    fun logError(
        commandSent: String,
        responseReceived: String,
        username: String,
        profileName: String,
        keyType: String = "",
        keySlot: Int = -1,
        notes: String = ""
    ) {
        logInjection(
            commandSent = commandSent,
            responseReceived = responseReceived,
            operationStatus = "ERROR",
            username = username,
            profileName = profileName,
            keyType = keyType,
            keySlot = keySlot,
            notes = notes
        )
    }
}
