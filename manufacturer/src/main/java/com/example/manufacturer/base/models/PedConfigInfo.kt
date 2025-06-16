package com.example.manufacturer.base.models

/**
 * Represents general configuration or identification information from the PED.
 *
 * @property serialNumber The device serial number.
 * @property firmwareVersion The firmware version running on the PED.
 * @property hardwareVersion The hardware version identifier.
 * @property modelIdentifier A string identifying the PED model.
 */
data class PedConfigInfo(
    val serialNumber: String?,
    val firmwareVersion: String?,
    val hardwareVersion: String? = null, // Example additional field
    val modelIdentifier: String? = null // Example
    // Add other common configuration details
)