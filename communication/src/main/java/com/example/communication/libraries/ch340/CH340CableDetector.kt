package com.example.communication.libraries.ch340

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import cn.wch.ch34xuartdriver.CH34xUARTDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CH340 USB Cable Detection Wrapper
 *
 * Detects and initializes USB cables with embedded CH340/CH341 chips
 * for device-to-device communication (e.g., Aisino-Aisino via special USB cable)
 *
 * Features:
 * - Modern Kotlin API wrapping legacy CH34xUARTDriver
 * - Automatic USB host support detection
 * - Device enumeration and permission handling
 * - UART configuration and communication
 * - Thread-safe operations with coroutines
 *
 * Usage:
 * ```kotlin
 * val detector = CH340CableDetector(context)
 * if (detector.detectCable()) {
 *     detector.configure(115200, 8, 1, 0, 0)
 *     val data = detector.readData(256)
 *     detector.writeData(data)
 *     detector.close()
 * }
 * ```
 */
class CH340CableDetector(private val context: Context) {

    companion object {
        private const val TAG = "CH340CableDetector"

        // CH340 Device Constants
        // Vendor ID: 0x1A86 (WCH - Nanjing Qinheng Microelectronics)
        private const val CH340_VENDOR_ID = 0x1A86

        // Product IDs
        private const val CH340_PRODUCT_ID_1 = 0x7523  // CH340 (most common)
        private const val CH340_PRODUCT_ID_2 = 0x5523  // CH341
        private const val CH340_PRODUCT_ID_3 = 0x5512  // CH340/CH342 variant

        // Error codes
        private const val ERR_USB_NOT_SUPPORTED = -100
        private const val ERR_DEVICE_NOT_FOUND = -101
        private const val ERR_INIT_FAILED = -102
        private const val ERR_NOT_CONNECTED = -103
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var driver: CH34xUARTDriver? = null
    private var isConnected = false

    /**
     * Detect CH340 cable
     *
     * Checks for USB Host support and enumerates CH340 devices
     *
     * @return true if cable detected and initialized
     */
    suspend fun detectCable(): Boolean = withContext(Dispatchers.Default) {
        try {
            Log.i(TAG, "╔═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ CH340 CABLE DETECTION")
            Log.i(TAG, "╠═══════════════════════════════════════════════════════════════")

            // Step 1: Check USB Host support
            if (!hasUsbHostSupport()) {
                Log.e(TAG, "║ ❌ Device does not support USB Host mode")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                return@withContext false
            }
            Log.i(TAG, "║ ✓ USB Host mode supported")

            // Step 2: Create driver instance
            driver = CH34xUARTDriver(
                usbManager,
                context,
                "cn.wch.wchusbdriver.USB_PERMISSION"
            )
            Log.i(TAG, "║ ✓ CH340 driver created")

            // Step 3: Enumerate CH340 devices
            val result = driver!!.ResumeUsbList()
            when (result) {
                0 -> {
                    Log.i(TAG, "║ ✓ CH340 device found and opened")
                    // Device found, but need to initialize UART
                }
                -1 -> {
                    Log.e(TAG, "║ ❌ Failed to open CH340 device")
                    Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                    return@withContext false
                }
                else -> {
                    // Permission not granted yet (user will be prompted)
                    Log.i(TAG, "║ ⏳ Waiting for USB permission...")
                    driver!!.ResumeUsbPermission()
                    return@withContext false
                }
            }

            // Step 4: Initialize UART
            if (!driver!!.UartInit()) {
                Log.e(TAG, "║ ❌ Failed to initialize UART")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                driver!!.CloseDevice()
                return@withContext false
            }
            Log.i(TAG, "║ ✓ UART initialized")

            // Step 5: Verify connection
            if (!driver!!.isConnected()) {
                Log.e(TAG, "║ ❌ Cable connected but not responding")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                driver!!.CloseDevice()
                return@withContext false
            }
            Log.i(TAG, "║ ✓ Cable verified as responsive")

            isConnected = true
            Log.i(TAG, "║ ✅ CH340 CABLE DETECTED AND READY")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during CH340 detection: ${e.message}", e)
            isConnected = false
            false
        }
    }

    /**
     * Configure UART communication parameters
     *
     * @param baudRate Baud rate (300-921600, typically 115200)
     * @param dataBits Data bits (5-8, typically 8)
     * @param stopBits Stop bits (1 or 2, typically 1)
     * @param parity Parity (0=none, 1=odd, 2=even, 3=mark, 4=space)
     * @param flowControl Flow control (0=none, 1=CTS/RTS)
     * @return true if configuration successful
     */
    fun configure(
        baudRate: Int = 115200,
        dataBits: Int = 8,
        stopBits: Int = 1,
        parity: Int = 0,
        flowControl: Int = 0
    ): Boolean {
        if (!isConnected || driver == null) {
            Log.e(TAG, "❌ Cable not connected, cannot configure")
            return false
        }

        try {
            Log.d(TAG, "🔧 Configuring UART: ${baudRate}bps, ${dataBits}N${stopBits}")

            val result = driver!!.SetConfig(
                baudRate,
                dataBits.toByte(),
                stopBits.toByte(),
                parity.toByte(),
                flowControl.toByte()
            )

            if (!result) {
                Log.e(TAG, "❌ Failed to configure UART parameters")
                return false
            }

            Log.d(TAG, "✓ UART configured successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception configuring UART: ${e.message}", e)
            return false
        }
    }

    /**
     * Set communication timeouts
     *
     * @param readTimeout Read timeout in milliseconds
     * @param writeTimeout Write timeout in milliseconds
     * @return true if successful
     */
    fun setTimeouts(readTimeout: Int = 1000, writeTimeout: Int = 1000): Boolean {
        if (!isConnected || driver == null) {
            Log.e(TAG, "❌ Cable not connected, cannot set timeouts")
            return false
        }

        return try {
            driver!!.SetTimeOut(readTimeout, writeTimeout)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception setting timeouts: ${e.message}", e)
            false
        }
    }

    /**
     * Read data from cable
     *
     * Blocking call that reads available data with configured timeout
     *
     * @param bufferSize Maximum bytes to read
     * @return ByteArray of data read, empty array if no data or timeout
     */
    fun readData(bufferSize: Int = 256): ByteArray {
        if (!isConnected || driver == null) {
            Log.e(TAG, "❌ Cable not connected, cannot read")
            return ByteArray(0)
        }

        return try {
            val buffer = ByteArray(bufferSize)
            val bytesRead = driver!!.ReadData(buffer, bufferSize)

            if (bytesRead > 0) {
                val data = buffer.copyOf(bytesRead)
                val hexString = toHexString(data)
                Log.d(TAG, "📥 Read ${bytesRead} bytes: $hexString")
                data
            } else if (bytesRead < 0) {
                Log.d(TAG, "⏱️ Read timeout or error: $bytesRead")
                ByteArray(0)
            } else {
                ByteArray(0)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception reading data: ${e.message}", e)
            ByteArray(0)
        }
    }

    /**
     * Write data to cable
     *
     * @param data ByteArray to write
     * @return Number of bytes actually written, -1 on error
     */
    fun writeData(data: ByteArray): Int {
        if (!isConnected || driver == null) {
            Log.e(TAG, "❌ Cable not connected, cannot write")
            return -1
        }

        if (data.isEmpty()) {
            return 0
        }

        return try {
            val bytesWritten = driver!!.WriteData(data, data.size)
            if (bytesWritten > 0) {
                val hexString = toHexString(data)
                Log.d(TAG, "📤 Wrote ${bytesWritten} bytes: $hexString")
            } else {
                Log.e(TAG, "❌ Write failed: returned $bytesWritten")
            }
            bytesWritten

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception writing data: ${e.message}", e)
            -1
        }
    }

    /**
     * Get connection status
     *
     * @return true if cable is connected and ready
     */
    fun isConnected(): Boolean {
        return isConnected && driver != null && driver!!.isConnected()
    }

    /**
     * Close cable connection
     *
     * Releases USB device and cleans up resources
     */
    fun close() {
        try {
            if (driver != null) {
                driver!!.CloseDevice()
                Log.d(TAG, "✓ CH340 cable closed")
            }
            isConnected = false
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Exception closing cable: ${e.message}", e)
        }
    }

    /**
     * Get device information
     *
     * @return Human-readable device description
     */
    fun getDeviceInfo(): String {
        if (driver == null) {
            return "CH340 device not initialized"
        }

        val device = try {
            driver!!.EnumerateDevice()
        } catch (e: Exception) {
            return "Error getting device info: ${e.message}"
        }

        return if (device != null) {
            """
            ═══════════════════════════════════════════════════════
            CH340 USB Device Information:
            - Device Name: ${device.deviceName}
            - Vendor ID: 0x${device.vendorId.toString(16).uppercase()}
            - Product ID: 0x${device.productId.toString(16).uppercase()}
            - Interface Count: ${device.interfaceCount}
            - Serial Number: ${device.serialNumber ?: "N/A"}
            ═══════════════════════════════════════════════════════
            """.trimIndent()
        } else {
            "CH340 device enumeration returned null"
        }
    }

    /**
     * Check if device has USB Host support
     *
     * Some devices don't support USB Host mode, making cable detection impossible
     */
    private fun hasUsbHostSupport(): Boolean {
        return try {
            driver?.UsbFeatureSupported() ?: false
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not determine USB Host support: ${e.message}")
            false
        }
    }

    /**
     * Convert byte array to hex string for logging
     */
    private fun toHexString(data: ByteArray): String {
        return data.joinToString(" ") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
    }
}
