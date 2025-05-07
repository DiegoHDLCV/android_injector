package com.example.manufacturer.base.models

/**
 * Parameters for requesting a MAC calculation.
 *
 * @property keyIndex The index of the MAC key.
 * @property keyType The type of the MAC key (e.g., WORKING_MAC_KEY).
 * @property data The data over which the MAC should be calculated.
 * @property algorithm The MAC algorithm to use.
 * @property iv Optional Initialization Vector, required for some algorithms (e.g., CBC-MAC variants if not zero IV).
 * @property isDukpt Whether this request uses a DUKPT key scheme.
 * @property dukptGroupIndex If isDukpt is true, the DUKPT group index to use.
 * @property dukptIncrementKsn If isDukpt is true, whether to increment the KSN after this operation.
 */
data class PedMacRequest(
    val keyIndex: Int,
    val keyType: KeyType, // Typically WORKING_MAC_KEY or DUKPT group index
    val data: ByteArray,
    val algorithm: MacAlgorithm,
    val iv: ByteArray? = null,
    val isDukpt: Boolean = false,
    val dukptGroupIndex: Int? = null, // Use instead of keyIndex/keyType if isDukpt=true
    val dukptIncrementKsn: Boolean = true // Default to increment for MAC requests
) {
    // equals/hashCode needed if comparing requests, including byte arrays
}