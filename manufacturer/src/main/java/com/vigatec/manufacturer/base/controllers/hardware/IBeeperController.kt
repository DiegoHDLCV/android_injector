package com.vigatec.manufacturer.base.controllers.hardware

/**
 * Interface for controlling the device beeper/buzzer hardware.
 */
interface IBeeperController {
    /**
     * Make a beep sound
     * @param duration Duration in milliseconds
     * @param frequency Frequency in Hz (if supported)
     */
    fun beep(duration: Int = 100, frequency: Int = 1000)

    /**
     * Make multiple beeps
     * @param count Number of beeps
     * @param duration Duration of each beep in milliseconds
     * @param interval Interval between beeps in milliseconds
     */
    fun beepMultiple(count: Int, duration: Int = 100, interval: Int = 100)
}
