package com.example.format

/**
 * Interfaz base sellada para cualquier mensaje decodificado de un protocolo.
 * El uso de 'sealed' obliga a que los 'when' que la usan sean exhaustivos,
 * lo que previene que nos olvidemos de manejar un tipo de comando.
 */
sealed interface ParsedMessage

/**
 * Modelo para los comandos del protocolo "Legacy" (el que usa '|').
 */
data class LegacyMessage(
    val command: String,
    val fields: List<String>
) : ParsedMessage

/**
 * Interfaz base sellada para todos los comandos del protocolo Futurex.
 * También hereda de ParsedMessage para poder ser usada en el mismo flujo.
 */
sealed interface FuturexCommand : ParsedMessage {
    val rawPayload: String // Guardamos el payload para depuración.
}

/**
 * Modelo para el comando Futurex "03" (Read Serial Number).
 */
data class ReadSerialCommand(
    override val rawPayload: String,
    val version: String
) : FuturexCommand

/**
 * Modelo para el comando Futurex "04" (Write Serial Number).
 */
data class WriteSerialCommand(
    override val rawPayload: String,
    val version: String,
    val serialNumber: String
) : FuturexCommand

/**
 * Modelo para el comando Futurex "02" (Symmetric Key Injection).
 */
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
    val ktkHex: String? // Nullable porque solo existe si encryptionType es "02".
) : FuturexCommand

/**
 * Modelo para comandos desconocidos o no soportados.
 */
data class UnknownCommand(
    override val rawPayload: String,
    val commandCode: String
) : FuturexCommand

/**
 * Modelo para representar un error irrecuperable durante el parseo del payload.
 */
data class ParseError(
    override val rawPayload: String,
    val error: String
) : FuturexCommand
