package com.example.format

import android.util.Log // Importa la clase Log

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
) : ParsedMessage {
    init {
        Log.d("LegacyMessage", "LegacyMessage creado: Comando='$command', Campos=${fields.joinToString()}")
    }
}

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
) : FuturexCommand {
    init {
        Log.d("ReadSerialCommand", "ReadSerialCommand creado: Versión='$version', RawPayload='$rawPayload'")
    }
}

data class WriteSerialCommand(
    override val rawPayload: String,
    val version: String,
    val serialNumber: String
) : FuturexCommand {
    init {
        Log.d("WriteSerialCommand", "WriteSerialCommand creado: Versión='$version', SerialNumber='$serialNumber', RawPayload='$rawPayload'")
    }
}

data class DeleteKeyCommand(
    override val rawPayload: String,
    val version: String
) : FuturexMessage

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

    init {
        Log.d("InjectSymmetricKeyCommand", "InjectSymmetricKeyCommand creado:")
        Log.d("InjectSymmetricKeyCommand", "  Versión='$version'")
        Log.d("InjectSymmetricKeyCommand", "  KeySlot=$keySlot")
        Log.d("InjectSymmetricKeyCommand", "  KtkSlot=$ktkSlot")
        Log.d("InjectSymmetricKeyCommand", "  KeyType='$keyType'")
        Log.d("InjectSymmetricKeyCommand", "  EncryptionType='$encryptionType'")
        Log.d("InjectSymmetricKeyCommand", "  KeyChecksum='$keyChecksum'")
        Log.d("InjectSymmetricKeyCommand", "  KtkChecksum='$ktkChecksum'")
        Log.d("InjectSymmetricKeyCommand", "  KSN='$ksn'")
        Log.d("InjectSymmetricKeyCommand", "  KeyHex='${keyHex.take(20)}...' (longitud: ${keyHex.length})") // Log solo los primeros 20 chars
        Log.d("InjectSymmetricKeyCommand", "  KtkHex='${ktkHex?.take(20)}...' (longitud: ${ktkHex?.length ?: 0})") // Log solo los primeros 20 chars
        Log.d("InjectSymmetricKeyCommand", "  IsTr31=$isTr31")
        Log.d("InjectSymmetricKeyCommand", "  RawPayload='$rawPayload'")
    }
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
) : FuturexResponse {
    init {
        Log.d("InjectSymmetricKeyResponse", "InjectSymmetricKeyResponse creado:")
        Log.d("InjectSymmetricKeyResponse", "  ResponseCode='$responseCode'")
        Log.d("InjectSymmetricKeyResponse", "  KeyChecksum='$keyChecksum'")
        Log.d("InjectSymmetricKeyResponse", "  RawPayload='$rawPayload'")
    }
}

/**
 * Modelo para la respuesta al comando "03" (ReadSerial).
 * (Añadir si se necesita parsear la respuesta del serial number)
 */
// data class ReadSerialResponse(...) : FuturexResponse


// --- OTROS ---

data class UnknownCommand(
    override val rawPayload: String,
    val commandCode: String
) : FuturexCommand { // Se trata como un comando entrante
    init {
        Log.w("UnknownCommand", "UnknownCommand creado: Código de comando desconocido='$commandCode', RawPayload='$rawPayload'")
    }
}

data class ParseError(
    override val rawPayload: String,
    val error: String
) : FuturexMessage { // Puede ser comando o respuesta
    init {
        Log.e("ParseError", "ParseError creado: Error='$error', RawPayload='$rawPayload'")
    }
}

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
) {
    init {
        Log.d("Tr31KeyBlock", "Tr31KeyBlock creado:")
        Log.d("Tr31KeyBlock", "  VersionId='$versionId'")
        Log.d("Tr31KeyBlock", "  BlockLength=$blockLength")
        Log.d("Tr31KeyBlock", "  KeyUsage='$keyUsage'")
        Log.d("Tr31KeyBlock", "  Algorithm='$algorithm'")
        Log.d("Tr31KeyBlock", "  ModeOfUse='$modeOfUse'")
        Log.d("Tr31KeyBlock", "  KeyVersionNumber='$keyVersionNumber'")
        Log.d("Tr31KeyBlock", "  Exportability='$exportability'")
        Log.d("Tr31KeyBlock", "  OptionalBlocks (${optionalBlocks.size}): ${optionalBlocks.joinToString { it.id }}")
        Log.d("Tr31KeyBlock", "  EncryptedPayload (longitud: ${encryptedPayload.size} bytes): ${encryptedPayload.toHexString()}")
        Log.d("Tr31KeyBlock", "  MAC (longitud: ${mac.size} bytes): ${mac.toHexString()}")
        Log.d("Tr31KeyBlock", "  RawBlock='${rawBlock.take(50)}...' (longitud: ${rawBlock.length})")
    }

    // Función de extensión local para convertir ByteArray a String hexadecimal
    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }
}

data class Tr31OptionalBlock(val id: String, val data: String) {
    init {
        Log.d("Tr31OptionalBlock", "Tr31OptionalBlock creado: ID='$id', Data='${data.take(20)}...' (longitud: ${data.length})")
    }
}