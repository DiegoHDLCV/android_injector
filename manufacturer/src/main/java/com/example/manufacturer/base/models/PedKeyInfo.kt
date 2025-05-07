package com.example.manufacturer.base.models

/**
 * Basic information about a stored key.
 *
 * @property index The storage index of the key.
 * @property type The general purpose/type of the key.
 * @property algorithm The cryptographic algorithm associated with the key (optional, might not always be available).
 */
data class PedKeyInfo(
    val index: Int,
    val type: KeyType,
    val algorithm: KeyAlgorithm?
)