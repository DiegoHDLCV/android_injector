package com.example.manufacturer.base.models

/**
 * Result of a successful PIN block request.
 *
 * @property pinBlock The encrypted PIN block.
 * @property finalDukptInfo If DUKPT was used, contains the KSN (and possibly counter) after the transaction.
 */
data class PedPinResult(
    val pinBlock: ByteArray,
    val finalDukptInfo: DukptInfo? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PedPinResult

        if (!pinBlock.contentEquals(other.pinBlock)) return false
        if (finalDukptInfo != other.finalDukptInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pinBlock.contentHashCode()
        result = 31 * result + (finalDukptInfo?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "PedPinResult(pinBlock=[${pinBlock.size} bytes], finalDukptInfo=$finalDukptInfo)"
    }
}