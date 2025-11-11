package com.vigatec.manufacturer.base.models

/**
 * Represents DUKPT (Derived Unique Key Per Transaction) status information.
 *
 * @property ksn Key Serial Number, identifying the initial key and transaction counter state.
 * @property counter Optional transaction counter derived from the KSN. Implementations might only use KSN.
 */
data class DukptInfo(
    val ksn: ByteArray,
    val counter: Int? = null // Counter might be implicit in KSN, depends on implementation
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DukptInfo

        if (!ksn.contentEquals(other.ksn)) return false
        if (counter != other.counter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ksn.contentHashCode()
        result = 31 * result + (counter ?: 0)
        return result
    }

    override fun toString(): String {
        // Avoid logging KSN directly if sensitive
        return "DukptInfo(ksn=[${ksn.size} bytes], counter=$counter)"
    }
}