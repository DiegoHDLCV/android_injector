package com.example.format

/**
 * Interfaz base sellada para cualquier mensaje decodificado de un protocolo.
 */
sealed interface ParsedMessage

/**
 * Modelo para los comandos del protocolo "Legacy".
 */
data class LegacyMessage(
    val command: String,
    val fields: List<String>
) : ParsedMessage

/**
 * Interfaz base para todos los comandos y respuestas del protocolo Futurex.
 */
sealed interface FuturexMessage : ParsedMessage {
    val rawPayload: String // Guardamos el payload para depuración.
}

// --- COMANDOS (Enviados desde el Host al Dispositivo) ---

sealed interface FuturexCommand : FuturexMessage

data class ReadSerialCommand(
    override val rawPayload: String,
    val version: String
) : FuturexCommand

data class WriteSerialCommand(
    override val rawPayload: String,
    val version: String,
    val serialNumber: String
) : FuturexCommand

data class InjectSymmetricKeyCommand(
    override val rawPayload: String,
    val version: String,
    val keySlot: Int,
    val ktkSlot: Int,
    val keyType: String,
    val encryptionType: String,
    val keyChecksum: String,
    val ktkChecksum: String,
    val ksn: String,
    val keyHex: String,
    val ktkHex: String?
) : FuturexCommand {
    val isTr31: Boolean get() = keyHex.firstOrNull()?.isLetter() ?: false
}

// --- RESPUESTAS (Recibidas desde el Dispositivo) ---

sealed interface FuturexResponse : FuturexMessage

/**
 * Modelo para la respuesta al comando "02" (InjectSymmetricKey).
 */
data class InjectSymmetricKeyResponse(
    override val rawPayload: String,
    val responseCode: String,
    val keyChecksum: String // El checksum devuelto por el dispositivo.
) : FuturexResponse

/**
 * Modelo para la respuesta al comando "03" (ReadSerial).
 * (Añadir si se necesita parsear la respuesta del serial number)
 */
// data class ReadSerialResponse(...) : FuturexResponse


// --- OTROS ---

data class UnknownCommand(
    override val rawPayload: String,
    val commandCode: String
) : FuturexCommand // Se trata como un comando entrante

data class ParseError(
    override val rawPayload: String,
    val error: String
) : FuturexMessage // Puede ser comando o respuesta

// Clases de datos para TR-31 (sin cambios)
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
