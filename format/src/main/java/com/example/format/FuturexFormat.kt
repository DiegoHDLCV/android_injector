package com.example.format


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
) : FuturexCommand

// --- RESPUESTAS (Dispositivo -> Host) ---

data class InjectSymmetricKeyResponse(
    override val rawPayload: String,
    val responseCode: String,
    val keyChecksum: String
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
