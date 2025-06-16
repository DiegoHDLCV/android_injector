package com.example.manufacturer.base.models

/**
 * Defines the mode of operation for block ciphers (DES, AES, SM4).
 */
enum class BlockCipherMode {
    ECB, // Electronic Codebook
    CBC  // Cipher Block Chaining
    // Add others like CFB, OFB if needed
}
