package com.vigatec.injector.model

import com.vigatec.utils.enums.print.EnumFontSize

/**
 * Data class representing a key injection voucher to be printed.
 * Contains all information needed to generate a thermal printer receipt.
 */
data class VoucherData(
    // Device information
    val deviceSerial: String,
    val deviceModel: String,

    // Injection information
    val profileName: String,
    val username: String,
    val injectionDate: String, // Format: DD/MM/YYYY
    val injectionTime: String, // Format: HH:MM:SS
    val injectionStatus: String, // "EXITOSA", "PARCIAL", etc.

    // Keys information
    val keysInjected: List<InjectedKeyInfo>,
    val totalKeys: Int, // Total keys that should be injected
    val successfulKeys: Int = keysInjected.size // Keys successfully injected

) {
    /**
     * Represents a single key that was injected
     */
    data class InjectedKeyInfo(
        val keyUsage: String, // PIN, TRACK2, MAC, etc.
        val keySlot: String, // e.g., "01", "02", etc.
        val keyType: String, // 3DES, AES, etc.
        val kcv: String, // Key Check Value (last 6 characters typically)
        val status: String = "INYECTADA" // INYECTADA, ERROR, etc.
    )

    /**
     * Returns a formatted string for the QR code content
     * Format: Serial|Perfil|Fecha|CantidadLlaves
     */
    fun getQrCodeContent(): String {
        return com.google.gson.Gson().toJson(this)
    }

    /**
     * Returns a summary line for multi-column display
     * Format: "Llave 1/X"
     */
    fun getSummaryLine(): String {
        return "Llaves: $successfulKeys/$totalKeys"
    }
}
