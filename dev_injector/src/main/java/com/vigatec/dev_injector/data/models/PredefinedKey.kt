package com.vigatec.dev_injector.data.models

import com.example.manufacturer.base.models.KeyAlgorithm
import com.example.manufacturer.base.models.KeyType

data class PredefinedKey(
    val name: String,
    val keyType: KeyType,
    val keyIndex: Int,
    val keyAlgorithm: KeyAlgorithm,
    val keyBytes: ByteArray,
    val description: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PredefinedKey

        if (name != other.name) return false
        if (keyType != other.keyType) return false
        if (keyIndex != other.keyIndex) return false
        if (keyAlgorithm != other.keyAlgorithm) return false
        if (!keyBytes.contentEquals(other.keyBytes)) return false
        if (description != other.description) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + keyType.hashCode()
        result = 31 * result + keyIndex
        result = 31 * result + keyAlgorithm.hashCode()
        result = 31 * result + keyBytes.contentHashCode()
        result = 31 * result + description.hashCode()
        return result
    }
}

object PredefinedKeys {
    
    fun String.hexToBytes(): ByteArray {
        return this.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    val MASTER_KEY = PredefinedKey(
        name = "Master Key",
        keyType = KeyType.MASTER_KEY,
        keyIndex = 10,
        keyAlgorithm = KeyAlgorithm.DES_TRIPLE,
        keyBytes = "CB79E0898F2907C24A13516BEAE904A2".hexToBytes(),
        description = "Llave maestra principal para cifrado"
    )
    
    val TRANSPORT_KEY = PredefinedKey(
        name = "Transport Key",
        keyType = KeyType.TRANSPORT_KEY,
        keyIndex = 15,
        keyAlgorithm = KeyAlgorithm.DES_TRIPLE,
        keyBytes = "A1B2C3D4E5F60708090A0B0C0D0E0F10".hexToBytes(),
        description = "Llave de transporte para comunicaciones seguras"
    )
    
    val DATA_ENCRYPTION_KEY = PredefinedKey(
        name = "Data Encryption Key",
        keyType = KeyType.WORKING_DATA_KEY,
        keyIndex = 11,
        keyAlgorithm = KeyAlgorithm.DES_TRIPLE,
        keyBytes = "892FF24F80C13461760E1349083862D9".hexToBytes(),
        description = "Llave de trabajo para cifrado de datos"
    )
    
    val MAC_KEY = PredefinedKey(
        name = "MAC Key",
        keyType = KeyType.WORKING_MAC_KEY,
        keyIndex = 12,
        keyAlgorithm = KeyAlgorithm.DES_TRIPLE,
        keyBytes = "F2AD8CA77AFD85C168A2DA022CD9F751".hexToBytes(),
        description = "Llave de trabajo para códigos de autenticación"
    )
    
    val PIN_KEY = PredefinedKey(
        name = "PIN Key",
        keyType = KeyType.WORKING_PIN_KEY,
        keyIndex = 13,
        keyAlgorithm = KeyAlgorithm.DES_TRIPLE,
        keyBytes = "A46137A25289EFFD10C7EF0E0E167FA8".hexToBytes(),
        description = "Llave de trabajo para cifrado de PIN"
    )
    
    val IPEK_KEY = PredefinedKey(
        name = "IPEK Key",
        keyType = KeyType.MASTER_KEY, // O el tipo apropiado para DUKPT
        keyIndex = 1,
        keyAlgorithm = KeyAlgorithm.DES_TRIPLE,
        keyBytes = "63A1EA98B4342A20B29E326FFF50742E".hexToBytes(),
        description = "Initial PIN Encryption Key para DUKPT"
    )
    
    val ALL_KEYS = listOf(
        MASTER_KEY,
        TRANSPORT_KEY,
        DATA_ENCRYPTION_KEY,
        MAC_KEY,
        PIN_KEY,
        IPEK_KEY
    )
    
    // Constantes adicionales del test
    val INITIAL_KSN = "F8765432100000000000".hexToBytes()
    val SAMPLE_PAN = "4000123456789010"
    val PLAINTEXT_FOR_CIPHER = "0123456789ABCDEF".toByteArray(Charsets.UTF_8)
    val DUKPT_GROUP_INDEX = 1
}