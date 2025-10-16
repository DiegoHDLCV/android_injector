package com.vigatec.utils.security

/**
 * Representa datos de una llave cifrada con AES-256-GCM.
 *
 * @property encryptedData Los datos cifrados en formato hexadecimal
 * @property iv Vector de inicialización (IV) en formato hexadecimal (12 bytes = 24 caracteres hex)
 * @property authTag Tag de autenticación para verificar integridad (16 bytes = 32 caracteres hex)
 */
data class EncryptedKeyData(
    val encryptedData: String,
    val iv: String,
    val authTag: String
) {
    init {
        require(encryptedData.isNotEmpty()) { "encryptedData no puede estar vacío" }
        require(iv.length == 24) { "IV debe tener 24 caracteres hexadecimales (12 bytes)" }
        require(authTag.length == 32) { "AuthTag debe tener 32 caracteres hexadecimales (16 bytes)" }
        require(encryptedData.matches(Regex("^[0-9A-Fa-f]+\$"))) { "encryptedData debe ser hexadecimal válido" }
        require(iv.matches(Regex("^[0-9A-Fa-f]+\$"))) { "IV debe ser hexadecimal válido" }
        require(authTag.matches(Regex("^[0-9A-Fa-f]+\$"))) { "AuthTag debe ser hexadecimal válido" }
    }

    /**
     * Convierte los datos cifrados completos a un string concatenado para almacenamiento
     */
    fun toStorageString(): String = "$encryptedData|$iv|$authTag"

    companion object {
        /**
         * Crea EncryptedKeyData desde un string de almacenamiento
         */
        fun fromStorageString(storageString: String): EncryptedKeyData {
            val parts = storageString.split("|")
            require(parts.size == 3) { "Formato de storage string inválido" }
            return EncryptedKeyData(
                encryptedData = parts[0],
                iv = parts[1],
                authTag = parts[2]
            )
        }
    }
}
