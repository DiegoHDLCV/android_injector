package com.example.format

/**
 * Representa un comando decodificado del protocolo serial.
 * @param command El código de comando de 4 dígitos (ej: "0700").
 * @param fields La lista de campos de datos recibidos.
 */
data class SerialMessage(
    val command: String,
    val fields: List<String>
)