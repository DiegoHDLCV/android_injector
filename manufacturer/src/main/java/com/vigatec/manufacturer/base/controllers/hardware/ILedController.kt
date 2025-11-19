package com.vigatec.manufacturer.base.controllers.hardware

/**
 * Interface for controlling the device LED indicators.
 */
interface ILedController {
    /**
     * Turn on an LED
     * @param ledType Type of LED (e.g., "POWER", "STATUS", "ERROR")
     * @param color Color in ARGB format (if supported)
     */
    fun turnOn(ledType: String, color: Int = 0xFFFFFFFF.toInt())

    /**
     * Turn off an LED
     * @param ledType Type of LED
     */
    fun turnOff(ledType: String)

    /**
     * Blink an LED
     * @param ledType Type of LED
     * @param durationMs Duration of blink in milliseconds
     * @param intervalMs Interval between blinks in milliseconds
     */
    fun blink(ledType: String, durationMs: Int = 1000, intervalMs: Int = 500)
}
