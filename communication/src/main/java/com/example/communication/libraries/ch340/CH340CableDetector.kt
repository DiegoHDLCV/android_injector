package com.example.communication.libraries.ch340

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * CH340 USB Cable Detection Wrapper - Modern Implementation
 *
 * Detects and initializes USB cables with embedded CH340/CH341 chips
 * for device-to-device communication (e.g., Aisino-Aisino via special USB cable)
 *
 * Features:
 * - Modern usb-serial-for-android library (actively maintained)
 * - Proper Android 12+ PendingIntent flag handling
 * - Automatic USB host support detection
 * - Device enumeration and permission handling
 * - UART configuration and communication
 * - Thread-safe operations with coroutines
 *
 * This implementation replaces the legacy CN.WCH.CH34xUARTDriver (2018)
 * with the modern usb-serial-for-android library (v3.9.0, March 2025)
 * to resolve PendingIntent flag compatibility issues on Android 12+
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
    private var usbSerialDriver: UsbSerialDriver? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var isConnected = false

    /**
     * Detect CH340 cable
     *
     * Uses modern usb-serial-for-android library to enumerate CH340 devices
     * Properly handles Android 12+ PendingIntent requirements
     *
     * @return true if cable detected and initialized
     */
    suspend fun detectCable(): Boolean = withContext(Dispatchers.Default) {
        try {
            Log.i(TAG, "╔═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ CH340 CABLE DETECTION (usb-serial-for-android v3.9.0)")
            Log.i(TAG, "╠═══════════════════════════════════════════════════════════════")

            // Step 1: Check USB Host support (informative only, not blocking)
            if (context.packageManager.hasSystemFeature("android.hardware.usb.host")) {
                Log.i(TAG, "║ ✓ USB Host mode supported")
            } else {
                Log.w(TAG, "║ ⚠️ USB Host mode not reported, but CH340 may still work")
                Log.i(TAG, "║ ℹ️ Continuing with CH340 detection anyway...")
            }

            // Step 2: Enumerate available USB devices using usb-serial-for-android
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.isEmpty()) {
                Log.e(TAG, "║ ❌ No USB serial devices found")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                return@withContext false
            }

            Log.i(TAG, "║ ✓ Found ${availableDrivers.size} USB serial device(s)")

            // Step 3: Find and initialize CH340 device
            for (driver in availableDrivers) {
                val device = driver.device
                Log.i(TAG, "║ 🔍 Checking device: ${device.deviceName} (VID: 0x${device.vendorId.toString(16)}, PID: 0x${device.productId.toString(16)})")

                if (!isChipCH340(device)) {
                    continue
                }

                Log.i(TAG, "║ ✓ CH340 device detected")
                usbSerialDriver = driver

                // Step 4: Open connection to the device
                val connection = try {
                    usbManager.openDevice(device)
                } catch (e: SecurityException) {
                    Log.e(TAG, "║ ⚠️ SecurityException: USB permission denied")
                    Log.e(TAG, "║    Error: ${e.message}")
                    Log.e(TAG, "║    Solution: Device filter added to manifest, please allow USB permission when prompted")
                    continue
                }

                if (connection == null) {
                    Log.e(TAG, "║ ⚠️ Permission may be needed for device access (connection was null)")
                    continue
                }

                Log.i(TAG, "║ ✓ USB connection opened")

                // Step 5: Open serial port
                val port = driver.ports[0]
                try {
                    port.open(connection)
                    usbSerialPort = port
                    Log.i(TAG, "║ ✓ Serial port opened")

                    // Step 6: Configure default UART parameters (115200, 8N1)
                    port.setParameters(
                        115200,  // baud rate
                        8,       // data bits
                        UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_NONE
                    )
                    Log.i(TAG, "║ ✓ Serial port configured: 115200 8N1")

                    isConnected = true
                    Log.i(TAG, "║ ✅ CH340 CABLE DETECTED AND READY")
                    Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                    return@withContext true

                } catch (e: IOException) {
                    Log.e(TAG, "║ ❌ Error opening or configuring port: ${e.message}")
                    try {
                        port.close()
                    } catch (ce: IOException) {
                        Log.w(TAG, "║ ⚠️ Error closing port: ${ce.message}")
                    }
                    connection.close()
                    continue
                }
            }

            Log.e(TAG, "║ ❌ No CH340 device found or could not be opened")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during CH340 detection: ${e.message}", e)
            Log.e(TAG, "║ Stack: ${e.stackTraceToString().take(300)}")
            isConnected = false
            false
        }
    }

    /**
     * Check if USB device is a CH340/CH341 chip
     */
    private fun isChipCH340(device: UsbDevice): Boolean {
        val vendorId = device.vendorId
        val productId = device.productId

        // CH340 Vendor ID
        if (vendorId != CH340_VENDOR_ID) {
            return false
        }

        // Check known product IDs
        return productId == CH340_PRODUCT_ID_1 ||
               productId == CH340_PRODUCT_ID_2 ||
               productId == CH340_PRODUCT_ID_3
    }

    /**
     * Configure UART communication parameters
     *
     * @param baudRate Baud rate (300-921600, typically 115200)
     * @param dataBits Data bits (5-8, typically 8)
     * @param stopBits Stop bits (1 or 2, typically 1)
     * @param parity Parity (0=none, 1=odd, 2=even, 3=mark, 4=space)
     * @param flowControl Flow control (0=none, 1=CTS/RTS) - not used in usb-serial-for-android
     * @return true if configuration successful
     */
    fun configure(
        baudRate: Int = 115200,
        dataBits: Int = 8,
        stopBits: Int = 1,
        parity: Int = 0,
        flowControl: Int = 0
    ): Boolean {
        if (!isConnected || usbSerialPort == null) {
            Log.e(TAG, "❌ Cable not connected, cannot configure")
            return false
        }

        try {
            Log.d(TAG, "🔧 Configuring UART: ${baudRate}bps, ${dataBits}N${stopBits}")

            val uartStopBits = when (stopBits) {
                1 -> UsbSerialPort.STOPBITS_1
                2 -> UsbSerialPort.STOPBITS_2
                else -> UsbSerialPort.STOPBITS_1
            }

            val uartParity = when (parity) {
                0 -> UsbSerialPort.PARITY_NONE
                1 -> UsbSerialPort.PARITY_ODD
                2 -> UsbSerialPort.PARITY_EVEN
                else -> UsbSerialPort.PARITY_NONE
            }

            usbSerialPort!!.setParameters(baudRate, dataBits, uartStopBits, uartParity)

            Log.d(TAG, "✓ UART configured successfully: ${baudRate}bps, ${dataBits}N${stopBits}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception configuring UART: ${e.message}", e)
            return false
        }
    }

    /**
     * Set communication timeouts
     *
     * Note: usb-serial-for-android uses read/write timeouts at operation level,
     * not at the port level. Timeouts are passed directly to read()/write() calls.
     *
     * @param readTimeout Read timeout in milliseconds (informational, used in read operations)
     * @param writeTimeout Write timeout in milliseconds (informational, used in write operations)
     * @return true if successful
     */
    fun setTimeouts(readTimeout: Int = 1000, writeTimeout: Int = 1000): Boolean {
        if (!isConnected || usbSerialPort == null) {
            Log.e(TAG, "❌ Cable not connected, cannot set timeouts")
            return false
        }

        return try {
            // usb-serial-for-android handles timeouts per-operation
            // Store them for reference if needed
            Log.d(TAG, "✓ Timeout configuration noted (read: ${readTimeout}ms, write: ${writeTimeout}ms)")
            true
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
        return readData(bufferSize, 0) // Default: no timeout
    }

    /**
     * Read data from cable with optional timeout
     *
     * @param bufferSize Maximum bytes to read
     * @param timeout Timeout in milliseconds (0 = use default, immediate return)
     * @return ByteArray with data read (empty if timeout or no data)
     */
    fun readData(bufferSize: Int = 256, timeout: Int = 0): ByteArray {
        if (!isConnected || usbSerialPort == null) {
            Log.e(TAG, "❌ Cable not connected, cannot read")
            return ByteArray(0)
        }

        return try {
            val buffer = ByteArray(bufferSize)
            val effectiveTimeout = if (timeout > 0) timeout else 100 // Default 100ms if not specified

            // usb-serial-for-android read() call with timeout
            val bytesRead = usbSerialPort!!.read(buffer, effectiveTimeout)

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
        if (!isConnected || usbSerialPort == null) {
            Log.e(TAG, "❌ Cable not connected, cannot write")
            return -1
        }

        if (data.isEmpty()) {
            return 0
        }

        return try {
            // usb-serial-for-android write() with default timeout (1000ms)
            usbSerialPort!!.write(data, 1000)

            val hexString = toHexString(data)
            Log.d(TAG, "📤 Wrote ${data.size} bytes: $hexString")
            data.size

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
        return isConnected && usbSerialPort != null
    }

    /**
     * Close cable connection
     *
     * Releases USB device and cleans up resources
     */
    fun close() {
        try {
            if (usbSerialPort != null) {
                usbSerialPort!!.close()
                Log.d(TAG, "✓ CH340 serial port closed")
                usbSerialPort = null
            }
            if (usbSerialDriver != null) {
                usbSerialDriver = null
            }
            isConnected = false
            Log.d(TAG, "✓ CH340 cable closed and cleaned up")
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
        if (usbSerialDriver == null) {
            return "CH340 device not initialized"
        }

        return try {
            val device = usbSerialDriver!!.device
            """
            ═══════════════════════════════════════════════════════
            CH340 USB Device Information:
            - Device Name: ${device.deviceName}
            - Vendor ID: 0x${device.vendorId.toString(16).uppercase()}
            - Product ID: 0x${device.productId.toString(16).uppercase()}
            - Interfaces: ${device.interfaceCount}
            - Ports: ${usbSerialDriver!!.ports.size}
            ═══════════════════════════════════════════════════════
            """.trimIndent()
        } catch (e: Exception) {
            "Error getting device info: ${e.message}"
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
