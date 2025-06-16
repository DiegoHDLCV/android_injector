package com.example.manufacturer.base.models

/**
 * Result of a successful MAC calculation request.
 *
 * @property mac The calculated Message Authentication Code.
 * @property finalDukptInfo If DUKPT was used, contains the KSN (and possibly counter) after the transaction.
 */
data class PedMacResult(
    val mac: ByteArray,
    val finalDukptInfo: DukptInfo? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PedMacResult

        if (!mac.contentEquals(other.mac)) return false
        if (finalDukptInfo != other.finalDukptInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mac.contentHashCode()
        result = 31 * result + (finalDukptInfo?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "PedMacResult(mac=[${mac.size} bytes], finalDukptInfo=$finalDukptInfo)"
    }
}