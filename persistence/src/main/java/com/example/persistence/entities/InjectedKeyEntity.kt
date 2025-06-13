// file: com/example/persistence/entities/InjectedKeyEntity.kt

package com.example.persistence.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa los metadatos de una llave criptográfica que ha sido inyectada en el PED.
 * NO almacena el valor de la llave en sí, solo la información para su gestión y auditoría.
 * Se usa un índice compuesto para asegurar que solo haya un registro por ranura y tipo de llave.
 */
@Entity(
    tableName = "injected_keys",
    indices = [Index(value = ["keySlot", "keyType"], unique = true)]
)
data class InjectedKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Datos de la inyección
    val keySlot: Int,           // La posición/índice donde se inyectó la llave en el PED.
    val keyType: String,        // El tipo de llave (ej: "MASTER_KEY", "WORKING_PIN_KEY").
    val keyAlgorithm: String,   // El algoritmo (ej: "DES_TRIPLE", "AES").
    val kcv: String,            // El Key Checksum Value (en formato HEX) para verificación.

    // Metadatos de auditoría
    val injectionTimestamp: Long = System.currentTimeMillis(),
    val status: String          // Estado de la última operación (ej: "SUCCESSFUL", "FAILED").
)