package com.vigatec.manufacturer.base.models

/**
 * Specifies the intended usage for a key derived within a DUKPT scheme.
 * Needed for some PED APIs (like Aisino's PedDukptCalcSym_Api).
 */
enum class DukptKeyVariant {
    /** Key derived for PIN encryption/decryption. */
    PIN,
    /** Key derived for MAC calculation/verification. */
    MAC,
    /** Key derived for data encryption (e.g., request data, bi-directional data). */
    DATA_ENCRYPT,
    /** Key derived for data decryption (e.g., response data). */
    DATA_DECRYPT
}