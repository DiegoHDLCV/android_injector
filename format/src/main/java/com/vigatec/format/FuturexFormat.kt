package com.vigatec.format


/**
 * Interfaz base sellada para cualquier mensaje decodificado de un protocolo.
 */
sealed interface ParsedMessage

/**
 * Interfaz base para todos los comandos y respuestas del protocolo Futurex.
 * Requiere que todas las implementaciones tengan un `rawPayload`.
 */
sealed interface FuturexMessage : ParsedMessage {
    val rawPayload: String
}

// --- CATEGORÍAS DE MENSAJES FUTUREX ---

/** Interfaz para comandos enviados desde el Host al Dispositivo. */
sealed interface FuturexCommand : FuturexMessage

/** Interfaz para respuestas enviadas desde el Dispositivo al Host. */
sealed interface FuturexResponse : FuturexMessage


// --- COMANDOS (Host -> Dispositivo) ---

data class ReadSerialCommand(
    override val rawPayload: String,
    val version: String
) : FuturexCommand

data class WriteSerialCommand(
    override val rawPayload: String,
    val version: String,
    val serialNumber: String
) : FuturexCommand

/**
 * Comando estándar de Futurex ("05") para eliminar TODAS las llaves.
 */
data class DeleteKeyCommand(
    override val rawPayload: String,
    val version: String
) : FuturexCommand

/**
 * Comando personalizado ("06") para eliminar una llave de un slot específico.
 */
data class DeleteSingleKeyCommand(
    override val rawPayload: String,
    val version: String,
    val keySlot: Int,
    val keyTypeHex: String
) : FuturexCommand

/**
 * Comando personalizado ("07") para desinstalar la aplicación KeyReceiver del dispositivo.
 * Solo funciona si la app es de sistema o tiene permisos DELETE_PACKAGES.
 */
data class UninstallAppCommand(
    override val rawPayload: String,
    val version: String,
    val confirmationToken: String  // Token de confirmación para validar origen
) : FuturexCommand

/**
 * Comando personalizado ("08") para validar que la marca del dispositivo POS
 * coincida con el perfil de inyección antes de proceder con la inyección de llaves.
 *
 * Formato: 08<version><expectedDeviceType>
 * - version: 2 caracteres (ej. "01")
 * - expectedDeviceType: 2 caracteres (00=AISINO, 01=NEWPOS, 02=UROVO, FF=UNKNOWN)
 */
data class ValidateDeviceBrandCommand(
    override val rawPayload: String,
    val version: String,
    val expectedDeviceType: String  // 2 chars: Marca esperada según el perfil
) : FuturexCommand

data class InjectSymmetricKeyCommand(
    override val rawPayload: String,
    val version: String,
    val keySlot: Int,
    val ktkSlot: Int,
    val keyType: String,
    val encryptionType: String,
    val keyAlgorithm: String,  // NUEVO: 00=3DES-112, 01=3DES-168, 02=AES-128, 03=AES-192, 04=AES-256
    val keySubType: String,    // NUEVO: 00=Generic/Master, 01=Working PIN, 02=Working MAC, 03=Working DATA, 04=DUKPT
    val keyChecksum: String,
    val ktkChecksum: String,
    val ksn: String,
    val keyHex: String,
    val ktkHex: String?,
    val totalKeys: Int = 0,        // NUEVO: Total de llaves a inyectar (0 = no especificado, para compatibilidad)
    val currentKeyIndex: Int = 0  // NUEVO: Índice de la llave actual (1-based, 0 = no especificado)
) : FuturexCommand

// --- RESPUESTAS (Dispositivo -> Host) ---

data class InjectSymmetricKeyResponse(
    override val rawPayload: String,
    val responseCode: String,
    val keyChecksum: String,
    val deviceSerial: String = "",      // NUEVO: Serial del dispositivo receptor
    val deviceModel: String = ""        // NUEVO: Modelo/nombre del dispositivo receptor
) : FuturexResponse

/**
 * Respuesta al comando de desinstalación de app ("07").
 * El dispositivo responde con confirmación ANTES de auto-desinstalarse.
 */
data class UninstallAppResponse(
    override val rawPayload: String,
    val responseCode: String,           // "00" = éxito, otro valor = error
    val deviceSerial: String = "",
    val deviceModel: String = ""
) : FuturexResponse

/**
 * Respuesta al comando de validación de marca ("08").
 *
 * Formato: 08<responseCode><actualDeviceType>
 * - responseCode: 2 caracteres ("00" = marca válida y coincide, otro = error)
 * - actualDeviceType: 2 caracteres (marca detectada en el dispositivo receptor)
 */
data class ValidateDeviceBrandResponse(
    override val rawPayload: String,
    val responseCode: String,           // "00" = coincide, "2A" = mismatch, otro = error
    val actualDeviceType: String = ""   // 2 chars: Marca detectada en el dispositivo
) : FuturexResponse


// --- OTROS ---

data class UnknownCommand(
    override val rawPayload: String,
    val commandCode: String
) : FuturexCommand

data class ParseError(
    override val rawPayload: String,
    val error: String
) : FuturexMessage

// --- CLASES AUXILIARES (Ej. TR-31) ---

data class Tr31KeyBlock(
    val rawBlock: String,
    val versionId: Char,
    val blockLength: Int,
    val keyUsage: String,
    val algorithm: Char,
    val modeOfUse: Char,
    val keyVersionNumber: String,
    val exportability: Char,
    val optionalBlocks: List<Tr31OptionalBlock>,
    val encryptedPayload: ByteArray,
    val mac: ByteArray
)

data class Tr31OptionalBlock(val id: String, val data: String)
