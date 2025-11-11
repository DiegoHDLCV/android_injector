// com/example/config/SystemConfig.kt

package com.example.config

import android.os.Build
import android.util.Log

// --- NUEVO ---
/**
 * Define los protocolos de comunicación serial soportados.
 */
enum class CommProtocol {
    LEGACY, // El protocolo original que usa '|'
    FUTUREX // El nuevo protocolo del manual
}

// --- NUEVO: Rol del dispositivo ---
enum class DeviceRole { MASTER, SUBPOS }

object SystemConfig {

    init {
        Log.d("DeviceCheck", "El Build.MODEL del dispositivo es: '${Build.MODEL}'")
    }
    var managerSelected: EnumManufacturer = getManufacturerFromString(Build.MODEL)
    var keyCombinationMethod: KeyCombinationMethod = KeyCombinationMethod.XOR_PLACEHOLDER

    // --- NUEVO: Variable para seleccionar el protocolo ---
    var commProtocolSelected: CommProtocol = CommProtocol.FUTUREX

    // --- NUEVO: Rol (por defecto SUBPOS para evitar polling accidental) ---
    @Volatile var deviceRole: DeviceRole = DeviceRole.SUBPOS

    // --- NUEVO: Candidatos de puertos y baudios para Aisino (pueden ajustarse vía futura pantalla de settings) ---
    // IMPORTANTE: Los puertos disponibles varían según el dispositivo:
    // - A90 Pro: Solo puerto 0 está disponible
    // - Otros dispositivos Aisino: Puertos 0, 1 o 6, 7, 8 según el modelo
    // El puerto 0 es el más universal y es el que funciona en A90 Pro
    var aisinoCandidatePorts: List<Int> = listOf(0, 1, 7, 8, 6)  // Puerto 0 primero (A90 Pro), luego alternativas
    // Orden: intenta 115200 primero (requerido por CH340), luego 9600 como fallback legacy
    var aisinoCandidateBauds: List<Int> = listOf(115200, 9600)

    // Helpers
    fun isMaster(): Boolean = deviceRole == DeviceRole.MASTER
    fun isSubPOS(): Boolean = deviceRole == DeviceRole.SUBPOS
}