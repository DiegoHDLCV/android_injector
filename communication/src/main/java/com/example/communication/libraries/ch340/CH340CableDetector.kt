package com.example.communication.libraries.ch340

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import java.io.IOException
import android.hardware.usb.UsbDeviceConnection
import android.os.Build
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.resume

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
    private val usbPermissionAction: String = "${context.packageName}.USB_PERMISSION"

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

            // Step 1.5: Verificar si hay dispositivos USB conectados antes de intentar detectarlos
            val deviceList = usbManager.deviceList
            Log.d(TAG, "║ Dispositivos USB detectados por UsbManager: ${deviceList.size}")
            if (deviceList.isEmpty()) {
                Log.w(TAG, "║ ⚠️ No hay dispositivos USB detectados por UsbManager")
                Log.w(TAG, "║    Posibles causas:")
                Log.w(TAG, "║    1. El dispositivo está en modo ADB (conectado a PC)")
                Log.w(TAG, "║    2. No se está usando un cable OTG en el dispositivo HOST")
                Log.w(TAG, "║    3. El cable CH340 no está conectado")
                Log.w(TAG, "║    SOLUCIÓN: Desconecta el cable USB de la PC y usa un cable OTG")
            } else {
                deviceList.values.forEach { device ->
                    Log.d(TAG, "║    - ${device.deviceName} (VID: 0x${device.vendorId.toString(16)}, PID: 0x${device.productId.toString(16)})")
                }
            }

            // Step 2: Enumerate available USB devices using usb-serial-for-android
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.isEmpty()) {
                Log.e(TAG, "║ ❌ No USB serial devices found")
                if (deviceList.isEmpty()) {
                    Log.e(TAG, "║ ⚠️ IMPORTANTE: No hay dispositivos USB detectados")
                    Log.e(TAG, "║    El dispositivo puede estar en modo ADB (conectado a PC)")
                    Log.e(TAG, "║    Para usar CH340, el dispositivo debe estar en modo HOST")
                    Log.e(TAG, "║    SOLUCIÓN:")
                    Log.e(TAG, "║    1. Desconecta el cable USB de la PC")
                    Log.e(TAG, "║    2. Conecta un cable OTG al dispositivo")
                    Log.e(TAG, "║    3. Conecta el cable CH340 al adaptador OTG")
                } else {
                    Log.e(TAG, "║ ⚠️ Hay dispositivos USB pero no son seriales compatibles")
                    Log.e(TAG, "║    Verifica que el cable CH340 esté correctamente conectado")
                }
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

                // Step 4: Request USB permission and verify it's granted
                Log.i(TAG, "║ 🔐 Verificando/solicitando permiso USB para ${device.deviceName}...")
                if (!ensureUsbPermission(device)) {
                    Log.w(TAG, "║ ⚠️ USB permission was not granted for ${device.deviceName}")
                    continue
                }

                // Double-check permission before opening device
                if (!usbManager.hasPermission(device)) {
                    Log.e(TAG, "║ ❌ ERROR: ensureUsbPermission retornó true pero hasPermission es false!")
                    Log.e(TAG, "║    Esto no debería pasar. Reintentando solicitud de permiso...")
                    if (!ensureUsbPermission(device)) {
                        Log.e(TAG, "║ ❌ Segundo intento de permiso también falló")
                        continue
                    }
                }

                Log.i(TAG, "║ ✅ Permiso USB confirmado, abriendo dispositivo...")
                val connection = openDeviceWithRetry(device)
                if (connection == null) {
                    Log.e(TAG, "║ ❌ Unable to open USB connection (permission granted but connection null)")
                    Log.e(TAG, "║    Verificando permiso nuevamente...")
                    if (!usbManager.hasPermission(device)) {
                        Log.e(TAG, "║ ❌ El permiso se perdió después de ensureUsbPermission!")
                    }
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

    private suspend fun ensureUsbPermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "║ ✓ USB permission already granted for ${device.deviceName}")
            return true
        }

        Log.i(TAG, "║ ⚠️ USB permission not granted for ${device.deviceName}, requesting...")
        Log.i(TAG, "║    Action: $usbPermissionAction")
        Log.i(TAG, "║    Device ID: ${device.deviceId}")

        return try {
            withTimeout(20000) { // 20 segundos para dar tiempo al usuario de responder
                suspendCancellableCoroutine { continuation ->
                    val filter = IntentFilter(usbPermissionAction)
                    var receiverRegistered = false
                    var permissionResolved = false
                    val lock = Any()
                    
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            Log.i(TAG, "║ 📨 Broadcast recibido: action=${intent.action}")
                            Log.d(TAG, "║    Esperado: $usbPermissionAction")
                            
                            if (intent.action != usbPermissionAction) {
                                Log.d(TAG, "║ ⚠️ Acción no coincide (${intent.action} != $usbPermissionAction), ignorando")
                                return
                            }

                            val intentDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                            Log.d(TAG, "║ 📨 Device ID recibido: ${intentDevice?.deviceId}, esperado: ${device.deviceId}")
                            
                            if (intentDevice?.deviceId != device.deviceId) {
                                Log.d(TAG, "║ ⚠️ Device ID no coincide (${intentDevice?.deviceId} != ${device.deviceId}), ignorando")
                                return
                            }

                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            Log.i(TAG, "║ 📨 Permiso USB ${if (granted) "✅ OTORGADO" else "❌ DENEGADO"} para ${device.deviceName}")

                            synchronized(lock) {
                                if (!permissionResolved && !continuation.isCompleted) {
                                    permissionResolved = true
                                    if (receiverRegistered) {
                                        try {
                                            context.unregisterReceiver(this)
                                            receiverRegistered = false
                                            Log.d(TAG, "║ ✓ BroadcastReceiver desregistrado")
                                        } catch (_: IllegalArgumentException) {
                                        }
                                    }
                                    continuation.resume(granted)
                                }
                            }
                        }
                    }

                    // Usar RECEIVER_EXPORTED para permitir ejecución en background (requerido para Android 13+)
                    val registerFlags =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Context.RECEIVER_EXPORTED // Cambiado a EXPORTED para permitir ejecución en background
                        } else {
                            0
                        }

                    try {
                        context.registerReceiver(receiver, filter, registerFlags)
                        receiverRegistered = true
                        Log.d(TAG, "║ ✓ BroadcastReceiver registrado para acción: $usbPermissionAction")
                    } catch (e: Exception) {
                        Log.e(TAG, "║ ❌ Error registrando receiver: ${e.message}")
                        continuation.resume(false)
                        return@suspendCancellableCoroutine
                    }

                    continuation.invokeOnCancellation {
                        if (receiverRegistered) {
                            try {
                                context.unregisterReceiver(receiver)
                                receiverRegistered = false
                            } catch (_: IllegalArgumentException) {
                            }
                        }
                    }

                    val permissionIntent = try {
                        PendingIntent.getBroadcast(
                            context,
                            device.deviceId,
                            Intent(usbPermissionAction).apply {
                                putExtra(UsbManager.EXTRA_DEVICE, device)
                            },
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "║ ❌ Error creando PendingIntent: ${e.message}")
                        if (receiverRegistered) {
                            try {
                                context.unregisterReceiver(receiver)
                            } catch (_: IllegalArgumentException) {
                            }
                        }
                        continuation.resume(false)
                        return@suspendCancellableCoroutine
                    }

                    Log.i(TAG, "║ 📤 Solicitando permiso USB...")
                    Log.i(TAG, "║    ⚠️ IMPORTANTE: El diálogo de permiso debería aparecer ahora")
                    Log.i(TAG, "║    Si no aparece, ve a Configuración > Apps > ${context.packageName} > Permisos USB")
                    
                    try {
                        usbManager.requestPermission(device, permissionIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "║ ❌ Error en requestPermission: ${e.message}", e)
                        if (receiverRegistered) {
                            try {
                                context.unregisterReceiver(receiver)
                            } catch (_: IllegalArgumentException) {
                            }
                        }
                        continuation.resume(false)
                        return@suspendCancellableCoroutine
                    }

                    // Verificar periódicamente si el permiso se otorgó (incluso si no recibimos el broadcast)
                    // Esto maneja el caso donde el UsbPermissionReceiver del manifest otorga el permiso
                    GlobalScope.launch(Dispatchers.IO) {
                        var checkCount = 0
                        val maxChecks = 100 // 20 segundos / 200ms = 100 checks
                        
                        while (!permissionResolved && checkCount < maxChecks && !continuation.isCompleted) {
                            delay(200)
                            checkCount++
                            
                            if (usbManager.hasPermission(device)) {
                                synchronized(lock) {
                                    if (!permissionResolved && !continuation.isCompleted) {
                                        permissionResolved = true
                                        Log.i(TAG, "║ ✓ Permiso otorgado (verificado después de ${checkCount * 200}ms)")
                                        if (receiverRegistered) {
                                            try {
                                                context.unregisterReceiver(receiver)
                                                receiverRegistered = false
                                                Log.d(TAG, "║ ✓ BroadcastReceiver desregistrado")
                                            } catch (_: IllegalArgumentException) {
                                            }
                                        }
                                        continuation.resume(true)
                                    }
                                }
                                return@launch
                            }
                            
                            // Log cada 5 segundos para no saturar
                            if (checkCount % 25 == 0) {
                                Log.d(TAG, "║ ⏳ Esperando permiso... (${checkCount * 200}ms transcurridos)")
                            }
                        }
                        
                        if (!permissionResolved && !continuation.isCompleted) {
                            Log.d(TAG, "║ ⏳ Esperando respuesta del usuario (máximo 20 segundos)...")
                            Log.d(TAG, "║    El diálogo de permiso debería estar visible en la pantalla")
                        }
                    }
                }
            }.also { granted ->
                if (granted) {
                    Log.i(TAG, "║ ✅ USB permission granted for ${device.deviceName}")
                } else {
                    Log.w(TAG, "║ ❌ USB permission denied or timeout for ${device.deviceName}")
                    Log.w(TAG, "║    SOLUCIÓN: Ve a Configuración > Apps > ${context.packageName} > Permisos USB")
                    Log.w(TAG, "║    y otorga el permiso manualmente, luego vuelve a intentar")
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.w(TAG, "║ ❌ USB permission request timed out (20s) for ${device.deviceName}")
            Log.w(TAG, "║    SOLUCIÓN: Ve a Configuración > Apps > ${context.packageName} > Permisos USB")
            Log.w(TAG, "║    y otorga el permiso manualmente, luego vuelve a intentar")
            false
        } catch (e: Exception) {
            Log.e(TAG, "║ ❌ Error solicitando permiso USB: ${e.message}", e)
            false
        }
    }

    private suspend fun openDeviceWithRetry(device: UsbDevice): UsbDeviceConnection? {
        Log.d(TAG, "║ 🔓 Intentando abrir dispositivo USB (máximo 3 intentos)...")
        Log.d(TAG, "║    Verificando permiso antes de abrir: ${usbManager.hasPermission(device)}")
        
        repeat(3) { attempt ->
            try {
                Log.d(TAG, "║    Intento ${attempt + 1}/3: abriendo dispositivo...")
                val connection = usbManager.openDevice(device)
                if (connection != null) {
                    Log.i(TAG, "║ ✅ Dispositivo abierto exitosamente en intento ${attempt + 1}")
                    return connection
                }
                Log.w(TAG, "║ ⚠️ openDevice retornó null en intento ${attempt + 1}, esperando 200ms...")
                delay(200)
            } catch (e: SecurityException) {
                Log.e(TAG, "║ ❌ SecurityException en intento ${attempt + 1}: ${e.message}")
                Log.e(TAG, "║    Verificando permiso después del error: ${usbManager.hasPermission(device)}")
                Log.e(TAG, "║    Device ID: ${device.deviceId}, Device Name: ${device.deviceName}")
                if (attempt < 2) {
                    Log.w(TAG, "║    Reintentando después de 500ms...")
                    delay(500)
                } else {
                    Log.e(TAG, "║    Todos los intentos fallaron con SecurityException")
                    return null
                }
            } catch (e: Exception) {
                Log.e(TAG, "║ ❌ Error inesperado abriendo dispositivo: ${e.javaClass.simpleName}: ${e.message}")
                return null
            }
        }
        Log.e(TAG, "║ ❌ No se pudo abrir el dispositivo después de 3 intentos")
        return null
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
