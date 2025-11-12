package com.vigatec.config

import android.util.Log

// El enum que proporcionaste
enum class EnumManufacturer {
    NEWPOS,
    AISINO,
    UROVO,
    INGENICO,
    PAX,
    UNKNOWN
}


/**
 * Determina el fabricante basándose en el nombre del dispositivo.
 *
 * @param deviceName El nombre del dispositivo (ej: "NEWPOS NEW9220", "Vanstone A90 Pro").
 * @return El [EnumManufacturer] correspondiente. Devuelve [UNKNOWN] si no se encuentra una coincidencia.
 */
fun getManufacturerFromString(deviceName: String): EnumManufacturer {
    Log.d("DeviceCheck", "Revisando nombre de dispositivo: '$deviceName'") // <-- Buen log para mantener
    return when {
        // --- CORRECCIÓN AQUÍ ---
        // Ahora busca "NEWPOS" O el modelo específico "NEW9220"
        deviceName.contains("NEWPOS", ignoreCase = true) -> EnumManufacturer.NEWPOS
        deviceName.contains("NEW9220", ignoreCase = true) -> EnumManufacturer.NEWPOS // <-- AÑADE ESTA LÍNEA
        deviceName.contains("NEW9830", ignoreCase = true) -> EnumManufacturer.NEWPOS
        deviceName.contains("NEW9220", ignoreCase = true) -> EnumManufacturer.NEWPOS
        deviceName.contains("NEW9310", ignoreCase = true) -> EnumManufacturer.NEWPOS


        // Lógica para Vanstone/Aisino
        deviceName.contains("Vanstone", ignoreCase = true) -> EnumManufacturer.AISINO
        deviceName.contains("Aisino", ignoreCase = true) -> EnumManufacturer.AISINO
        deviceName.contains("A90 Pro", ignoreCase = true) -> EnumManufacturer.AISINO
        deviceName.contains("A75 Pro", ignoreCase = true) -> EnumManufacturer.AISINO
        deviceName.contains("A99", ignoreCase = true) -> EnumManufacturer.AISINO


        // Otros fabricantes
        deviceName.contains("UROVO", ignoreCase = true) -> EnumManufacturer.UROVO

        else -> EnumManufacturer.UNKNOWN
    }
}