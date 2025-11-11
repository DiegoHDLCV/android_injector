// file: com/example/persistence/entities/InjectionLogEntity.kt

package com.vigatec.persistence.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa un registro de log de una operación de inyección.
 * Almacena información sobre el comando enviado, la respuesta recibida,
 * y metadatos asociados para auditoría y análisis.
 */
@Entity(
    tableName = "injection_logs",
    indices = [
        Index(value = ["timestamp"], unique = false),
        Index(value = ["username"], unique = false),
        Index(value = ["profileName"], unique = false)
    ]
)
data class InjectionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Datos de la operación
    val commandSent: String,        // Comando enviado al dispositivo
    val responseReceived: String,   // Respuesta recibida del dispositivo
    val operationStatus: String,    // Estado de la operación (SUCCESS, FAILED, ERROR)

    // Metadatos de contexto
    val username: String,           // Usuario que realizó la operación
    val profileName: String,        // Nombre del perfil utilizado
    val keyType: String = "",       // Tipo de llave (opcional, para mayor contexto)
    val keySlot: Int = -1,          // Slot de la llave (opcional, -1 si no aplica)

    // Metadatos de auditoría
    val timestamp: Long = System.currentTimeMillis(),  // Timestamp de la operación
    val deviceInfo: String = "",    // Información del dispositivo (opcional)
    val notes: String = ""          // Notas adicionales (opcional)
)
