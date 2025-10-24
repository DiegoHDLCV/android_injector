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
    // Los puertos 6, 7, 8 corresponden a:
    // - 6: ttyGS0 (Gadget Serial)
    // - 7: ttyUSB0 (USB Serial)
    // - 8: ttyACM0 (ACM Serial - Communication Device Class)
    // Estos son los mismos puertos que usa NewPOS, asegurando compatibilidad con el mismo hardware
    var aisinoCandidatePorts: List<Int> = listOf(7, 8, 6)  // USB primero, luego ACM, luego Gadget (como NewPOS)
    // Orden: intenta 115200 primero (velocidad estándar para cables PED), luego 9600
    var aisinoCandidateBauds: List<Int> = listOf(115200, 9600)

    // Helpers
    fun isMaster(): Boolean = deviceRole == DeviceRole.MASTER
    fun isSubPOS(): Boolean = deviceRole == DeviceRole.SUBPOS
}