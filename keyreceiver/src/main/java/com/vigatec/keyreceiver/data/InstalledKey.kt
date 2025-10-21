package com.vigatec.keyreceiver.data

/**
 * Representa una llave instalada en el dispositivo
 */
data class InstalledKey(
    val slot: Int,
    val kcv: String,
    val keyType: String,
    val isActive: Boolean = true
)
