package com.vigatec.manufacturer.base.models

/**
 * Represents key material, potentially including a Key Check Value (KCV).
 *
 * @property keyBytes The actual cryptographic key bytes.
 * @property kcv Optional Key Check Value used to verify the key integrity/correctness.
 */
data class PedKeyData(
    val keyBytes: ByteArray,
    val kcv: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PedKeyData

        if (!keyBytes.contentEquals(other.keyBytes)) return false
        if (kcv != null) {
            if (other.kcv == null) return false
            if (!kcv.contentEquals(other.kcv)) return false
        } else if (other.kcv != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyBytes.contentHashCode()
        result = 31 * result + (kcv?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        // Avoid logging actual key bytes
        return "PedKeyData(keyBytes=[${keyBytes.size} bytes], kcv=[${kcv?.size ?: "null"} bytes])"
    }
}