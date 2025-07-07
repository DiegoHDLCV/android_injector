// com/example/config/SystemConfig.kt

package com.example.config

// --- NUEVO ---
/**
 * Define los protocolos de comunicación serial soportados.
 */
enum class CommProtocol {
    LEGACY, // El protocolo original que usa '|'
    FUTUREX // El nuevo protocolo del manual
}

object SystemConfig {
    var managerSelected: EnumManufacturer = EnumManufacturer.NEWPOS
    var keyCombinationMethod: KeyCombinationMethod = KeyCombinationMethod.XOR_PLACEHOLDER

    // --- NUEVO: Variable para seleccionar el protocolo ---
    // Se puede cambiar desde la UI (ej. un menú de configuración)
    var commProtocolSelected: CommProtocol = CommProtocol.FUTUREX
}