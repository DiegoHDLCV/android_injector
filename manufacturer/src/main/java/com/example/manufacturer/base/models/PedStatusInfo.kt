package com.example.manufacturer.base.models

/**
 * Represents general status information from the PED.
 *
 * @property isTampered Indicates if the PED tamper detection has been triggered.
 * @property batteryLevel Approximate battery level (e.g., 0-100) if available, otherwise null.
 * @property errorMessage An optional error message string if the PED is in an error state.
 */
data class PedStatusInfo(
    val isTampered: Boolean,
    val batteryLevel: Int? = null, // Example additional field
    val errorMessage: String? = null // Example
    // Add other common status flags as needed
)