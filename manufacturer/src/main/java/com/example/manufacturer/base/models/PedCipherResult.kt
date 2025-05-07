package com.example.manufacturer.base.models

/**
 * Result of a successful encryption or decryption request.
 *
 * @property resultData The resulting encrypted or decrypted data.
 * @property finalDukptInfo If DUKPT was used, contains the KSN (and possibly counter) after the transaction.
 */
data class PedCipherResult(
    val resultData: ByteArray,
    val finalDukptInfo: DukptInfo? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PedCipherResult

        if (!resultData.contentEquals(other.resultData)) return false
        if (finalDukptInfo != other.finalDukptInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = resultData.contentHashCode()
        result = 31 * result + (finalDukptInfo?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "PedCipherResult(resultData=[${resultData.size} bytes], finalDukptInfo=$finalDukptInfo)"
    }
}