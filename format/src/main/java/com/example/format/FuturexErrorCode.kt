package com.example.format

/**
 * Enum que representa todos los códigos de error posibles del protocolo Futurex.
 * Proporciona una forma centralizada y segura de manejar los códigos de respuesta.
 */
enum class FuturexErrorCode(val code: String, val description: String) {
    SUCCESSFUL("00", "Successful"),
    INVALID_COMMAND("01", "Invalid command"),
    INVALID_COMMAND_VERSION("02", "Invalid command version"),
    INVALID_LENGTH("03", "Invalid length"),
    UNSUPPORTED_CHARACTERS("04", "Unsupported characters"),
    DEVICE_IS_BUSY("05", "Device is busy"),
    NOT_IN_INJECTION_MODE("06", "Not in injection mode"),
    DEVICE_IS_IN_TAMPER("07", "Device is in tamper"),
    BAD_LRC("08", "Bad LRC"),
    DUPLICATE_KEY("09", "Duplicate key"),
    DUPLICATE_KSN("0A", "Duplicate KSN"),
    KEY_DELETION_FAILED("0B", "Key deletion failed"),
    INVALID_KEY_SLOT("0C", "Invalid key slot"),
    INVALID_KTK_SLOT("0D", "Invalid KTK slot"),
    MISSING_KTK("0E", "Missing KTK"),
    KEY_SLOT_NOT_EMPTY("0F", "Key slot not empty"),
    INVALID_KEY_TYPE("10", "Invalid key type"),
    INVALID_KEY_ENCRYPTION_TYPE("11", "Invalid key encryption type"),
    INVALID_KEY_CHECKSUM("12", "Invalid key checksum"),
    INVALID_KTK_CHECKSUM("13", "Invalid KTK checksum"),
    INVALID_KSN("14", "Invalid KSN"),
    INVALID_KEY_LENGTH("15", "Invalid key length"),
    INVALID_KTK_LENGTH("16", "Invalid KTK length"),
    INVALID_TR31_VERSION("17", "Invalid TR-31 version"),
    INVALID_KEY_USAGE("18", "Invalid key usage"),
    INVALID_ALGORITHM("19", "Invalid algorithm"),
    INVALID_MODE_OF_USE("1A", "Invalid mode of use"),
    MAC_VERIFICATION_FAILED("1B", "MAC verification failed"),
    DECRYPTION_FAILED("1C", "Decryption failed");

    companion object {
        /**
         * Busca un FuturexErrorCode por su código hexadecimal.
         * @param code El código de 2 caracteres (ej: "0A", "1C").
         * @return El enum correspondiente o null si no se encuentra.
         */
        fun fromCode(code: String): FuturexErrorCode? {
            // .entries es más eficiente que .values() en los enums modernos
            return entries.find { it.code.equals(code, ignoreCase = true) }
        }
    }
}
