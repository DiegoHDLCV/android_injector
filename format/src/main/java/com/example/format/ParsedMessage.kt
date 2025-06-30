package com.example.format

import android.util.Log // Importa la clase Log

/**
 * Interfaz base sellada para cualquier mensaje decodificado de un protocolo.
 */

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

