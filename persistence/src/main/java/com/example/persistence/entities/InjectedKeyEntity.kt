// file: com/example/persistence/entities/InjectedKeyEntity.kt

package com.example.persistence.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa los metadatos de una llave criptográfica que ha sido inyectada en el PED.
 * Almacena tanto los metadatos como los datos de la llave para su gestión y auditoría.
 * - Solo una llave por slot/tipo (índice único en keySlot+keyType)
 * - Permite la misma llave (KCV) en diferentes slots (índice no único en kcv)
 */
@Entity(
    tableName = "injected_keys",
    indices = [
        Index(value = ["keySlot", "keyType"], unique = true), // Solo una llave por slot/tipo
        Index(value = ["kcv"], unique = false) // Permite KCV duplicados para la misma llave en diferentes slots
    ]
)
data class InjectedKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Datos de la inyección
    val keySlot: Int,           // La posición/índice donde se inyectó la llave en el PED.
    val keyType: String,        // El tipo de llave (ej: "MASTER_KEY", "WORKING_PIN_KEY").
    val keyAlgorithm: String,   // El algoritmo (ej: "DES_TRIPLE", "AES").
    val kcv: String,            // El Key Checksum Value (en formato HEX) para verificación.

    // Datos de la llave (en formato HEX)
    val keyData: String = "",   // Los datos de la llave en formato hexadecimal

    // Metadatos de auditoría
    val injectionTimestamp: Long = System.currentTimeMillis(),
    val status: String,         // Estado de la última operación (ej: "SUCCESSFUL", "FAILED", "ACTIVE", "EXPORTED", "INACTIVE").

    // Campos para KEK y personalización
    val isKEK: Boolean = false, // Flag para identificar si es una KEK (Key Encryption Key)
    val customName: String = "" // Nombre personalizado para la llave (opcional)
)