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
