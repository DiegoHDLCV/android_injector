package com.example.manufacturer.base.models

/**
 * Defines the cryptographic algorithm associated with a key or operation.
 */
enum class KeyAlgorithm {
    DES_SINGLE,       // 8-byte key (may have parity bits)
    DES_DOUBLE,       // 16-byte key (TDES with K1=K3)
    DES_TRIPLE,       // 16 or 24-byte key (TDES)
    AES_128,          // 16-byte key
    AES_192,          // 24-byte key
    AES_256,          // 32-byte key
    SM4,              // 16-byte key (Chinese standard)
    RSA               // Generic RSA, size often implicit or defined elsewhere
    // Add others like ECC if needed and commonly supported
}