package com.vigatec.persistence.model

/**
 * Resultado de la validación de eliminación de una llave.
 * Indica si se puede eliminar y proporciona información detallada.
 */
data class KeyDeletionValidation(
    /** Indica si la llave puede ser eliminada */
    val canDelete: Boolean,
    /** Razón por la que no se puede eliminar (si aplica) */
    val reason: DeletionReason,
    /** Lista de nombres de perfiles que contienen esta llave */
    val assignedProfiles: List<String> = emptyList(),
    /** Indica si la llave es KEK Storage activa */
    val isActiveKEKStorage: Boolean = false,
    /** Indica si la llave es KTK activa */
    val isActiveKTK: Boolean = false
)

/**
 * Razón por la que no se puede eliminar una llave
 */
enum class DeletionReason {
    /** La llave se puede eliminar sin problemas */
    OK,
    /** La llave está siendo usada en uno o más perfiles */
    IN_USE_BY_PROFILES,
    /** La llave es la KEK Storage activa (usado para cifrar otras llaves) */
    IS_ACTIVE_KEK_STORAGE,
    /** La llave es la KTK activa (usado para transporte de llaves) */
    IS_ACTIVE_KTK,
    /** La llave está en uso en múltiples contextos */
    MULTIPLE_USES
}

/**
 * Resultado de la validación de eliminación de múltiples llaves.
 * Contiene información sobre qué llaves no se pueden eliminar y por qué.
 */
data class MultipleKeysDeletionValidation(
    /** Indica si todas las llaves pueden ser eliminadas */
    val canDeleteAll: Boolean,
    /** Lista de validaciones de llaves que no se pueden eliminar */
    val blockedKeys: List<BlockedKeyInfo> = emptyList(),
    /** Total de llaves validadas */
    val totalKeys: Int = 0,
    /** Total de llaves que se pueden eliminar */
    val deletableKeys: Int = 0
)

/**
 * Información sobre una llave que no se puede eliminar
 */
data class BlockedKeyInfo(
    /** KCV de la llave */
    val kcv: String,
    /** Tipo de llave */
    val keyType: String,
    /** Razón por la que no se puede eliminar */
    val reason: DeletionReason,
    /** Lista de perfiles que usan esta llave */
    val assignedProfiles: List<String> = emptyList(),
    /** Indica si es KEK Storage activa */
    val isActiveKEKStorage: Boolean = false,
    /** Indica si es KTK activa */
    val isActiveKTK: Boolean = false
)
