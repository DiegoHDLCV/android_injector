package com.example.config


object SystemConfig {
    // Esta variable debe ser configurada al inicio de la aplicación
    // o basada en la detección del hardware.
    // Por ejemplo, para probar, puedes asignarla directamente:
    var managerSelected: EnumManufacturer = EnumManufacturer.UROVO
    var keyCombinationMethod: KeyCombinationMethod = KeyCombinationMethod.XOR_PLACEHOLDER // Valor por defecto
}