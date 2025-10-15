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
    var aisinoCandidatePorts: List<Int> = listOf(0, 1)
    // Orden: intenta 9600 primero (más común), luego 115200
    var aisinoCandidateBauds: List<Int> = listOf(9600, 115200)

    // Helpers
    fun isMaster(): Boolean = deviceRole == DeviceRole.MASTER
    fun isSubPOS(): Boolean = deviceRole == DeviceRole.SUBPOS
}