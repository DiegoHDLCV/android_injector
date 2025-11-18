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

/**
 * Convierte un EnumManufacturer a su código hexadecimal equivalente (2 caracteres).
 * Se utiliza para transmitir la marca del dispositivo en el protocolo Futurex Comando 08.
 * NOTA: Los códigos utilizan "A" como prefijo para evitar conflictos con códigos de error Futurex.
 *
 * @param manufacturer El fabricante a convertir.
 * @return El código hexadecimal de 2 caracteres (ej: "A0", "A1", "A2", "FF").
 */
fun manufacturerToDeviceTypeCode(manufacturer: EnumManufacturer): String {
    return when (manufacturer) {
        EnumManufacturer.AISINO -> "A0"
        EnumManufacturer.NEWPOS -> "A1"
        EnumManufacturer.UROVO -> "A2"
        else -> "FF"  // UNKNOWN
    }
}

/**
 * Convierte un código hexadecimal de marca a su EnumManufacturer equivalente.
 * Se utiliza para parsear la marca del dispositivo desde el protocolo Futurex Comando 08.
 *
 * @param code El código hexadecimal de 2 caracteres (ej: "A0", "A1", "A2", "FF").
 * @return El EnumManufacturer correspondiente.
 */
fun deviceTypeCodeToManufacturer(code: String): EnumManufacturer {
    return when (code) {
        "A0" -> EnumManufacturer.AISINO
        "A1" -> EnumManufacturer.NEWPOS
        "A2" -> EnumManufacturer.UROVO
        else -> EnumManufacturer.UNKNOWN
    }
}