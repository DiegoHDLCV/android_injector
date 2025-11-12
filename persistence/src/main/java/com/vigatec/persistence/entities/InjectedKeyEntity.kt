// file: com/example/persistence/entities/InjectedKeyEntity.kt

package com.vigatec.persistence.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tipo de KEK (Key Encryption Key)
 */
enum class KEKType {
    NONE,           // No es una KEK
    KEK_STORAGE,    // KEK que cifra llaves en el almacén local
    KEK_TRANSPORT   // KTK (Key Transport Key) que cifra llaves para transmisión a SubPOS
}

/**
 * Representa los metadatos de una llave criptográfica que ha sido inyectada en el PED.
 * Almacena tanto los metadatos como los datos de la llave para su gestión y auditoría.
 *
 * Estrategia de índices:
 * - KCV único: No permite guardar la misma llave (mismo KCV) dos veces, sin importar su propósito
 *   Esta es una restricción de negocio: evita duplicados inadvertidos en la ceremonia
 * - Para búsquedas rápidas se mantiene índice en keySlot + keyType
 */
@Entity(
    tableName = "injected_keys",
    indices = [
        Index(value = ["keySlot", "keyType"], unique = false), // Índice para búsquedas rápidas
        Index(value = ["kcv"], unique = true) // KCV único - previene duplicados de la misma llave física
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
    @Deprecated("Usar encryptedKeyData en su lugar. Este campo se mantiene para compatibilidad con versiones antiguas.")
    val keyData: String = "",   // DEPRECATED: Los datos de la llave en formato hexadecimal (SIN CIFRAR - usar solo para migración)

    // Datos de la llave CIFRADOS con KEK Storage (AES-256-GCM)
    val encryptedKeyData: String = "",  // Datos cifrados en formato hexadecimal
    val encryptionIV: String = "",      // Vector de inicialización (IV) - 12 bytes en hex (24 caracteres)
    val encryptionAuthTag: String = "", // Tag de autenticación GCM - 16 bytes en hex (32 caracteres)

    // Metadatos de auditoría
    val injectionTimestamp: Long = System.currentTimeMillis(),
    val status: String,         // Estado de la última operación (ej: "SUCCESSFUL", "FAILED", "ACTIVE", "EXPORTED", "INACTIVE").

    // Campos para KEK y personalización
    val isKEK: Boolean = false, // Flag para identificar si es una KEK (Key Encryption Key) - DEPRECATED: usar kekType
    val kekType: String = "NONE", // Tipo de KEK: NONE, KEK_STORAGE, KEK_TRANSPORT (KTK)
    val customName: String = "" // Nombre personalizado para la llave (opcional)
) {
    /**
     * Verifica si la llave está cifrada (tiene encryptedKeyData)
     */
    fun isEncrypted(): Boolean = encryptedKeyData.isNotEmpty()

    /**
     * Verifica si la llave está en formato legacy (keyData sin cifrar)
     */
    @Suppress("DEPRECATION")
    fun isLegacy(): Boolean = keyData.isNotEmpty() && encryptedKeyData.isEmpty()

    /**
     * Verifica si esta llave es KEK Storage
     */
    fun isKEKStorage(): Boolean = kekType == "KEK_STORAGE"

    /**
     * Verifica si esta llave es KEK Transport (KTK)
     */
    fun isKEKTransport(): Boolean = kekType == "KEK_TRANSPORT"

    /**
     * Obtiene el tipo de KEK como enum
     */
    fun getKEKTypeEnum(): KEKType = try {
        KEKType.valueOf(kekType)
    } catch (e: Exception) {
        KEKType.NONE
    }
}