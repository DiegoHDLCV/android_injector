package com.vigatec.manufacturer.base.models

/**
 * Defines the general type or purpose of a cryptographic key within the PED.
 */
enum class KeyType {
    /** Master Key used to encrypt/derive other keys (e.g., TMK, MFK). */
    MASTER_KEY,

    /** Working Key specifically for PIN encryption/translation (e.g., PEK, TPK). */
    WORKING_PIN_KEY,

    /** Working Key specifically for MAC calculation/verification (e.g., MAK). */
    WORKING_MAC_KEY,

    /** Working Key specifically for data encryption/decryption (e.g., DEK, TDK). */
    WORKING_DATA_KEY,

    /** Initial Key for DUKPT schemes (e.g., IPEK, BDK). */
    DUKPT_INITIAL_KEY,

    /** Current working key derived within a DUKPT scheme (used implicitly by group index). */
    DUKPT_WORKING_KEY, // Might not be stored/managed explicitly by index

    /** RSA Private Key component. */
    RSA_PRIVATE_KEY,

    /** RSA Public Key component. */
    RSA_PUBLIC_KEY,

    /** Key Encryption Key used to transport other keys (e.g., KEK, TLK). */
    TRANSPORT_KEY
}