package com.example.manufacturer.base.models

/**
 * Parameters for requesting encryption or decryption.
 *
 * @property keyIndex The index of the cryptographic key.
 * @property keyType The type of the key (e.g., WORKING_DATA_ENCRYPTION_KEY, RSA_PRIVATE_KEY).
 * @property data The data to be encrypted or decrypted.
 * @property algorithm The cryptographic algorithm to use (must match the key type).
 * @property mode The block cipher mode (ECB, CBC). Null for algorithms like RSA.
 * @property iv Optional Initialization Vector for modes like CBC.
 * @property encrypt Set to true for encryption, false for decryption.
 * @property isDukpt Whether this request uses a DUKPT key scheme.
 * @property dukptGroupIndex If isDukpt is true, the DUKPT group index to use.
 * @property dukptIncrementKsn If isDukpt is true, whether to increment the KSN after this operation.
 * @property dukptKeyVariant If isDukpt is true, specifies which derived key variant (PIN, MAC, DATA) to use.
 */
data class PedCipherRequest(
    val keyIndex: Int,
    val keyType: KeyType,
    val data: ByteArray,
    val algorithm: KeyAlgorithm,
    val mode: BlockCipherMode?,
    val iv: ByteArray?,
    val encrypt: Boolean,
    val isDukpt: Boolean = false,
    val dukptGroupIndex: Int? = null, // Use instead of keyIndex/keyType if isDukpt=true
    val dukptIncrementKsn: Boolean = true,
    val dukptKeyVariant: DukptKeyVariant? = DukptKeyVariant.DATA_ENCRYPT // Default to data variant for cipher ops
) {
    // equals/hashCode needed if comparing requests, including byte arrays
}