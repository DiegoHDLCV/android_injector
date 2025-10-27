# 💻 Ejemplos Prácticos: Integración del Demo de Aisino

**Estado**: Código listo para copiar y adaptar
**Referencia**: INTEGRATION_STRATEGY_AISINO_DEMO.md

---

## 📌 RUTA A: Migración a USB Host API

### Ejemplo 1: CustomProber.java (Adaptado del Demo)

```java
// communication/src/main/java/com/example/communication/libraries/aisino/usb/
// CustomProber.java

package com.example.communication.libraries.aisino.usb;

public class CustomProber {

    private static UsbSerialProber customProber;

    public static UsbSerialProber getCustomProber() {
        if (customProber == null) {
            ProbeTable customTable = new ProbeTable();

            // Aisino A90 como dispositivo USB serial
            customTable.addProduct(0x05C6, 0x901D, CdcAcmSerialDriver.class);
            customTable.addProduct(0x05C6, 0x9020, CdcAcmSerialDriver.class);

            // Otros dispositivos CDC-ACM comunes
            customTable.addProduct(0x0483, 0x3744, CdcAcmSerialDriver.class);  // STM32

            customProber = new UsbSerialProber(customTable);
        }
        return customProber;
    }
}
```

### Ejemplo 2: AisinoUsbComController.kt

```kotlin
// communication/src/main/java/com/example/communication/libraries/aisino/wrapper/
// AisinoUsbComController.kt

package com.example.communication.libraries.aisino.wrapper

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.example.communication.libraries.aisino.usb.CdcAcmSerialDriver
import com.example.communication.libraries.aisino.usb.UsbSerialPort
import com.example.communication.libraries.aisino.usb.UsbSerialProber

/**
 * Controlador Aisino usando Android USB Host API (CDC-ACM)
 *
 * VENTAJAS:
 * - Estándar USB, no propietario
 * - Detección automática de dispositivos
 * - Múltiples puertos soportados
 * - Mejor integración con Android
 */
class AisinoUsbComController(
    private val context: Context,
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice
) : IComController {

    companion object {
        private const val TAG = "AisinoUsbComController"
        private const val SUCCESS = 0
        private const val ERROR_OPEN_FAILED = -3
        private const val ERROR_NOT_OPEN = -4
        private const val ERROR_WRITE_FAILED = -5
        private const val ERROR_READ_TIMEOUT = -6
        private const val ERROR_CLOSE_FAILED = -8
        private const val ERROR_GENERAL_EXCEPTION = -99
        private const val ERROR_SET_BAUD_FAILED = -10
    }

    private var usbSerialPort: UsbSerialPort? = null
    private var isOpen = false
    private var storedBaudRate = 115200
    private val writeLock = Any()

    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        this.storedBaudRate = mapBaudRate(baudRate)
        Log.d(TAG, "Init: ${storedBaudRate}bps")
        return SUCCESS
    }

    override fun open(): Int {
        if (isOpen) {
            Log.d(TAG, "Puerto ya abierto")
            return SUCCESS
        }

        try {
            Log.i(TAG, "╔═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ ABRIENDO PUERTO USB - ${usbDevice.deviceName}")
            Log.i(TAG, "╠═══════════════════════════════════════════════════════════════")

            // 1. Obtener driver
            val driver = UsbSerialProber.getDefaultProber().probeDevice(usbDevice)
                ?: run {
                    Log.d(TAG, "║ Default prober falló, intentando custom prober")
                    com.example.communication.libraries.aisino.usb.CustomProber
                        .getCustomProber().probeDevice(usbDevice)
                }

            if (driver == null) {
                Log.e(TAG, "║ ❌ No se encontró driver compatible para ${usbDevice.deviceName}")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                return ERROR_OPEN_FAILED
            }

            Log.i(TAG, "║ ✓ Driver encontrado: ${driver.javaClass.simpleName}")

            // 2. Obtener puerto
            if (driver.ports.isEmpty()) {
                Log.e(TAG, "║ ❌ El driver no tiene puertos disponibles")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                return ERROR_OPEN_FAILED
            }

            usbSerialPort = driver.ports[0]
            Log.i(TAG, "║ ✓ Puerto seleccionado: ${driver.ports.size} puerto(s) disponibles")

            // 3. Obtener conexión
            val usbConnection = usbManager.openDevice(driver.device)
            if (usbConnection == null) {
                Log.e(TAG, "║ ❌ No se pudo obtener conexión USB")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                return ERROR_OPEN_FAILED
            }

            Log.i(TAG, "║ ✓ Conexión USB abierta")

            // 4. Abrir puerto serial
            try {
                usbSerialPort!!.open(usbConnection)
                Log.i(TAG, "║ ✓ Puerto serial abierto")
            } catch (e: Exception) {
                Log.e(TAG, "║ ❌ Error al abrir puerto serial: ${e.message}")
                Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                return ERROR_OPEN_FAILED
            }

            // 5. Configurar parámetros
            try {
                usbSerialPort!!.setParameters(
                    storedBaudRate,
                    8,  // dataBits
                    1,  // stopBits
                    UsbSerialPort.PARITY_NONE
                )
                Log.i(TAG, "║ ✓ Parámetros: ${storedBaudRate}bps, 8N1")
            } catch (e: Exception) {
                Log.e(TAG, "║ ❌ Error al configurar parámetros: ${e.message}")
                try {
                    usbSerialPort!!.close()
                } catch (ignored: Exception) {}
                Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
                return ERROR_SET_BAUD_FAILED
            }

            isOpen = true
            Log.i(TAG, "║ ✅ Puerto USB abierto exitosamente")
            Log.i(TAG, "║ Dispositivo: ${usbDevice.deviceName}")
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
            return SUCCESS

        } catch (e: Exception) {
            Log.e(TAG, "║ ❌ Excepción al abrir: ${e.message}", e)
            Log.d(TAG, "╚═══════════════════════════════════════════════════════════════")
            isOpen = false
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun close(): Int {
        if (!isOpen) {
            return SUCCESS
        }

        try {
            Log.d(TAG, "Cerrando puerto USB...")
            usbSerialPort?.close()
            usbSerialPort = null
            isOpen = false
            Log.d(TAG, "✓ Puerto cerrado")
            return SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cerrando puerto: ${e.message}")
            isOpen = false
            return ERROR_CLOSE_FAILED
        }
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        if (!isOpen) {
            Log.e(TAG, "❌ Puerto no abierto para escribir")
            return ERROR_NOT_OPEN
        }

        if (data.isEmpty()) {
            return 0
        }

        synchronized(writeLock) {
            try {
                val bytesWritten = usbSerialPort!!.write(data, timeout)
                Log.i(TAG, "📤 TX: ${bytesWritten} bytes")
                return bytesWritten
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error escribiendo: ${e.message}")
                return ERROR_WRITE_FAILED
            }
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        if (!isOpen) {
            Log.e(TAG, "❌ Puerto no abierto para leer")
            return ERROR_NOT_OPEN
        }

        if (buffer.isEmpty()) {
            return 0
        }

        try {
            val bytesRead = usbSerialPort!!.read(buffer, timeout)

            if (bytesRead > 0) {
                val hexData = buffer.take(bytesRead).joinToString("") { "%02X".format(it) }
                Log.i(TAG, "📥 RX: ${bytesRead} bytes - $hexData")
            }

            return if (bytesRead < 0) ERROR_READ_TIMEOUT else bytesRead

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error leyendo: ${e.message}")
            return ERROR_GENERAL_EXCEPTION
        }
    }

    private fun mapBaudRate(baudRate: EnumCommConfBaudRate): Int {
        return when (baudRate) {
            EnumCommConfBaudRate.BPS_1200 -> 1200
            EnumCommConfBaudRate.BPS_2400 -> 2400
            EnumCommConfBaudRate.BPS_4800 -> 4800
            EnumCommConfBaudRate.BPS_9600 -> 9600
            EnumCommConfBaudRate.BPS_19200 -> 19200
            EnumCommConfBaudRate.BPS_38400 -> 38400
            EnumCommConfBaudRate.BPS_57600 -> 57600
            EnumCommConfBaudRate.BPS_115200 -> 115200
        }
    }
}
```

### Ejemplo 3: AisinoUsbDeviceManager.kt

```kotlin
// communication/src/main/java/com/example/communication/libraries/aisino/manager/
// AisinoUsbDeviceManager.kt

package com.example.communication.libraries.aisino.manager

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.communication.libraries.aisino.wrapper.AisinoUsbComController

/**
 * Gestor de dispositivos USB Aisino
 * Encuentra, lista y abre conexiones a dispositivos Aisino
 */
class AisinoUsbDeviceManager(private val context: Context) {

    companion object {
        // Vendor ID de Aisino (China Company)
        private const val AISINO_VENDOR_ID = 0x05C6

        // Product IDs para diferentes modelos A90
        private val SUPPORTED_PRODUCT_IDS = listOf(
            0x901D,  // A90 como USB serial estándar
            0x9020,  // A90 configuración alternativa
        )

        private const val TAG = "AisinoUsbDeviceManager"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    data class AisinoDevice(
        val device: UsbDevice,
        val name: String,
        val vendorId: Int,
        val productId: Int,
        val isConnected: Boolean = true
    )

    /**
     * Buscar todos los dispositivos Aisino conectados
     */
    fun findAisinoDevices(): List<AisinoDevice> {
        val devices = mutableListOf<AisinoDevice>()

        for (device in usbManager.deviceList.values) {
            if (device.vendorId == AISINO_VENDOR_ID &&
                SUPPORTED_PRODUCT_IDS.contains(device.productId)) {

                devices.add(AisinoDevice(
                    device = device,
                    name = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    isConnected = true
                ))

                Log.i(TAG, "✓ Encontrado: ${device.deviceName} " +
                    "(${String.format("0x%04X:0x%04X", device.vendorId, device.productId)})")
            }
        }

        if (devices.isEmpty()) {
            Log.w(TAG, "No se encontraron dispositivos Aisino")
        } else {
            Log.i(TAG, "Total: ${devices.size} dispositivo(s)")
        }

        return devices
    }

    /**
     * Verificar si hay permiso para un dispositivo
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * Solicitar permiso para un dispositivo
     */
    fun requestPermission(
        device: UsbDevice,
        pendingIntent: android.app.PendingIntent
    ) {
        Log.i(TAG, "Solicitando permiso para: ${device.deviceName}")
        usbManager.requestPermission(device, pendingIntent)
    }

    /**
     * Crear controlador para un dispositivo específico
     */
    fun createController(device: UsbDevice): AisinoUsbComController {
        Log.i(TAG, "Creando controlador para: ${device.deviceName}")
        return AisinoUsbComController(context, usbManager, device)
    }

    /**
     * Obtener información de un dispositivo en texto legible
     */
    fun getDeviceInfo(device: UsbDevice): String {
        return """
            Nombre: ${device.deviceName}
            Vendor: 0x${device.vendorId.toString(16)}
            Product: 0x${device.productId.toString(16)}
            Interfaces: ${device.interfaceCount}
            Serializador: ${device.serialNumber ?: "N/A"}
        """.trimIndent()
    }
}
```

---

## 📌 RUTA B: Híbrida

### Ejemplo: AisinoComController.kt Mejorado

```kotlin
// communication/src/main/java/com/example/communication/libraries/aisino/wrapper/
// AisinoComController.kt (VERSIÓN MEJORADA HÍBRIDA)

package com.example.communication.libraries.aisino.wrapper

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.example.communication.libraries.aisino.manager.AisinoUsbDeviceManager
import com.vanstone.trans.api.Rs232Api
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Controlador Aisino HÍBRIDO:
 * 1. Intenta puertos virtuales (compartido)
 * 2. Intenta USB Host API (si contexto + permiso disponible)
 * 3. Fallback a Rs232Api (original)
 */
class AisinoComController(
    private val comport: Int = 0,
    private val context: Context? = null  // Nuevo: contexto para USB
) : IComController {

    companion object {
        private const val TAG = "AisinoComController"
        private const val AISINO_SUCCESS = 0
        private const val AISINO_ERROR = -1

        private const val SUCCESS = 0
        private const val ERROR_ALREADY_OPEN = -1
        private const val ERROR_OPEN_FAILED = -3
        private const val ERROR_NOT_OPEN = -4
        private const val ERROR_WRITE_FAILED = -5
        private const val ERROR_READ_TIMEOUT_OR_FAILURE = -6
        private const val ERROR_CLOSE_FAILED = -8
        private const val ERROR_GENERAL_EXCEPTION = -99
        private const val ERROR_SET_BAUD_FAILED = -10

        // Puertos virtuales
        private val VIRTUAL_PORTS = listOf(
            Pair("/dev/ttyUSB0", "ttyUSB0 (id=7)"),
            Pair("/dev/ttyACM0", "ttyACM0 (id=8)"),
            Pair("/dev/ttyGS0", "ttyGS0 (id=6)")
        )
    }

    private var isOpen = false
    private var storedBaudRate = 9600
    private var storedDataBits = 8
    private var storedParity = 0
    private var storedStopBits = 1

    // Para puertos virtuales
    private var virtualPortInputStream: InputStream? = null
    private var virtualPortOutputStream: OutputStream? = null
    private var usingVirtualPort = false

    // Para USB Host API
    private var usbController: AisinoUsbComController? = null
    private var usingUsbHost = false

    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        if (isOpen) {
            Log.w(TAG, "⚠️ Puerto ya abierto")
        }
        this.storedBaudRate = mapBaudRate(baudRate)
        this.storedDataBits = mapDataBits(dataBits)
        this.storedParity = mapParity(parity)
        Log.d(TAG, "Init: ${storedBaudRate}bps ${storedDataBits}N$storedStopBits")
        return SUCCESS
    }

    override fun open(): Int {
        if (isOpen) {
            Log.d(TAG, "Puerto ya abierto")
            return SUCCESS
        }

        try {
            Log.i(TAG, "╔════════════════════════════════════════════════════════")
            Log.i(TAG, "║ INTENTANDO ESTRATEGIAS DE APERTURA")
            Log.i(TAG, "╠════════════════════════════════════════════════════════")

            // ESTRATEGIA 1: Puertos Virtuales
            Log.i(TAG, "║ [1/3] Intentando puertos virtuales...")
            val virtualResult = tryOpenVirtualPorts()
            if (virtualResult == SUCCESS) {
                Log.i(TAG, "║ ✅ Usando puertos virtuales")
                Log.d(TAG, "╚════════════════════════════════════════════════════════")
                return SUCCESS
            }

            // ESTRATEGIA 2: USB Host API (si contexto disponible)
            if (context != null) {
                Log.i(TAG, "║ [2/3] Intentando USB Host API...")
                val usbResult = tryOpenUsbHost()
                if (usbResult == SUCCESS) {
                    Log.i(TAG, "║ ✅ Usando USB Host API")
                    Log.d(TAG, "╚════════════════════════════════════════════════════════")
                    return SUCCESS
                }
            } else {
                Log.d(TAG, "║ [2/3] Omitiendo USB Host (sin contexto)")
            }

            // ESTRATEGIA 3: Fallback a Rs232Api
            Log.i(TAG, "║ [3/3] Intentando fallback Rs232Api...")
            val rs232Result = tryOpenRs232Api()
            if (rs232Result == SUCCESS) {
                Log.i(TAG, "║ ✅ Usando Rs232Api")
                Log.d(TAG, "╚════════════════════════════════════════════════════════")
                return SUCCESS
            }

            Log.e(TAG, "║ ❌ TODAS LAS ESTRATEGIAS FALLARON")
            Log.d(TAG, "╚════════════════════════════════════════════════════════")
            return ERROR_OPEN_FAILED

        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción: ${e.message}", e)
            isOpen = false
            return ERROR_GENERAL_EXCEPTION
        }
    }

    private fun tryOpenVirtualPorts(): Int {
        for ((portPath, portName) in VIRTUAL_PORTS) {
            try {
                val portFile = File(portPath)
                if (portFile.exists() && portFile.canRead() && portFile.canWrite()) {
                    virtualPortInputStream = portFile.inputStream()
                    virtualPortOutputStream = portFile.outputStream()
                    usingVirtualPort = true
                    isOpen = true

                    Log.i(TAG, "║ ✓ Puerto virtual: $portName ($portPath)")
                    Log.i(TAG, "║ ✅ Acceso compartido permitido")
                    return SUCCESS
                }
            } catch (e: Exception) {
                Log.d(TAG, "║ ⚠️ $portName: ${e.message}")
            }
        }
        return ERROR_OPEN_FAILED
    }

    private fun tryOpenUsbHost(): Int {
        return try {
            val usbManager = context!!.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceManager = AisinoUsbDeviceManager(context!!)
            val devices = deviceManager.findAisinoDevices()

            if (devices.isEmpty()) {
                Log.d(TAG, "║ ⚠️ No hay dispositivos Aisino USB")
                return ERROR_OPEN_FAILED
            }

            val device = devices[0].device
            if (!deviceManager.hasPermission(device)) {
                Log.d(TAG, "║ ⚠️ Sin permiso USB para ${device.deviceName}")
                return ERROR_OPEN_FAILED
            }

            usbController = deviceManager.createController(device)
            val result = usbController!!.init(
                EnumCommConfBaudRate.BPS_115200,
                EnumCommConfParity.NOPAR,
                EnumCommConfDataBits.DB_8
            )

            if (result != SUCCESS) {
                Log.d(TAG, "║ ⚠️ Error inicializando controlador USB")
                return ERROR_OPEN_FAILED
            }

            val openResult = usbController!!.open()
            if (openResult == SUCCESS) {
                usingUsbHost = true
                isOpen = true
                Log.i(TAG, "║ ✓ USB Host: ${device.deviceName}")
                SUCCESS
            } else {
                Log.d(TAG, "║ ⚠️ Error abriendo USB: $openResult")
                ERROR_OPEN_FAILED
            }

        } catch (e: Exception) {
            Log.d(TAG, "║ ⚠️ Excepción USB: ${e.message}")
            ERROR_GENERAL_EXCEPTION
        }
    }

    private fun tryOpenRs232Api(): Int {
        return try {
            var result = Rs232Api.PortOpen_Api(comport)
            if (result != AISINO_SUCCESS) {
                Log.d(TAG, "║ ⚠️ PortOpen error: $result")
                return ERROR_OPEN_FAILED
            }

            Rs232Api.PortReset_Api(comport)
            result = Rs232Api.PortSetBaud_Api(
                comport,
                storedBaudRate,
                storedDataBits,
                storedParity,
                storedStopBits
            )

            if (result != AISINO_SUCCESS) {
                Log.d(TAG, "║ ⚠️ PortSetBaud error: $result")
                Rs232Api.PortClose_Api(comport)
                return ERROR_SET_BAUD_FAILED
            }

            isOpen = true
            Log.i(TAG, "║ ✓ Rs232Api puerto $comport (${storedBaudRate}bps)")
            Log.i(TAG, "║ ⚠️ Acceso exclusivo (sin compartir)")
            SUCCESS

        } catch (e: Exception) {
            Log.d(TAG, "║ Excepción Rs232Api: ${e.message}")
            ERROR_GENERAL_EXCEPTION
        }
    }

    override fun close(): Int {
        if (!isOpen) return SUCCESS

        return try {
            when {
                usingUsbHost -> {
                    usbController?.close() ?: SUCCESS
                }
                usingVirtualPort -> {
                    virtualPortInputStream?.close()
                    virtualPortOutputStream?.close()
                    virtualPortInputStream = null
                    virtualPortOutputStream = null
                    isOpen = false
                    SUCCESS
                }
                else -> {
                    Rs232Api.PortClose_Api(comport)
                    try {
                        Rs232Api.PortReset_Api(comport)
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Error reseteando: ${e.message}")
                    }
                    isOpen = false
                    SUCCESS
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cerrando: ${e.message}")
            isOpen = false
            ERROR_CLOSE_FAILED
        }
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        if (!isOpen) return ERROR_NOT_OPEN
        if (data.isEmpty()) return 0

        return try {
            when {
                usingUsbHost -> usbController?.write(data, timeout) ?: ERROR_NOT_OPEN
                usingVirtualPort -> {
                    virtualPortOutputStream?.write(data)
                    virtualPortOutputStream?.flush()
                    Log.i(TAG, "📤 TX virtual: ${data.size} bytes")
                    data.size
                }
                else -> {
                    val result = Rs232Api.PortSends_Api(comport, data, data.size)
                    if (result == AISINO_SUCCESS) {
                        Log.i(TAG, "📤 TX Rs232: ${data.size} bytes")
                        data.size
                    } else {
                        ERROR_WRITE_FAILED
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error escribiendo: ${e.message}")
            ERROR_GENERAL_EXCEPTION
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        if (!isOpen) return ERROR_NOT_OPEN
        if (buffer.isEmpty()) return 0

        return try {
            when {
                usingUsbHost -> usbController?.readData(expectedLen, buffer, timeout)
                    ?: ERROR_NOT_OPEN
                usingVirtualPort -> {
                    val bytesRead = virtualPortInputStream?.read(buffer, 0,
                        minOf(expectedLen, buffer.size)) ?: 0
                    if (bytesRead > 0) {
                        val hexData = buffer.take(bytesRead)
                            .joinToString("") { "%02X".format(it) }
                        Log.i(TAG, "📥 RX virtual: $bytesRead bytes - $hexData")
                    }
                    bytesRead
                }
                else -> {
                    val bytesRead = Rs232Api.PortRecv_Api(
                        comport, buffer, expectedLen, timeout
                    )
                    if (bytesRead > 0) {
                        val hexData = buffer.take(bytesRead)
                            .joinToString("") { "%02X".format(it) }
                        Log.i(TAG, "📥 RX Rs232: $bytesRead bytes - $hexData")
                    }
                    if (bytesRead < 0) ERROR_READ_TIMEOUT_OR_FAILURE else bytesRead
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error leyendo: ${e.message}")
            ERROR_GENERAL_EXCEPTION
        }
    }

    // Mapeos de enums
    private fun mapBaudRate(baudRate: EnumCommConfBaudRate): Int = when (baudRate) {
        EnumCommConfBaudRate.BPS_1200 -> 1200
        EnumCommConfBaudRate.BPS_2400 -> 2400
        EnumCommConfBaudRate.BPS_4800 -> 4800
        EnumCommConfBaudRate.BPS_9600 -> 9600
        EnumCommConfBaudRate.BPS_19200 -> 19200
        EnumCommConfBaudRate.BPS_38400 -> 38400
        EnumCommConfBaudRate.BPS_57600 -> 57600
        EnumCommConfBaudRate.BPS_115200 -> 115200
    }

    private fun mapDataBits(dataBits: EnumCommConfDataBits): Int = when (dataBits) {
        EnumCommConfDataBits.DB_7 -> 7
        EnumCommConfDataBits.DB_8 -> 8
    }

    private fun mapParity(parity: EnumCommConfParity): Int = when (parity) {
        EnumCommConfParity.NOPAR -> 0
        EnumCommConfParity.EVEN -> 2
        EnumCommConfParity.ODD -> 1
    }
}
```

---

## 📌 RUTA C: Quick Wins

### Ejemplo 1: SerialInputOutputManager.kt

```kotlin
// communication/src/main/java/com/example/communication/libraries/aisino/util/
// SerialInputOutputManager.kt

package com.example.communication.libraries.aisino.util

import android.util.Log
import com.example.communication.base.IComController

/**
 * Gestor asíncrono de I/O para puertos seriales Aisino
 *
 * BENEFICIOS:
 * - Lectura automática en thread separado
 * - Callbacks para nuevos datos
 * - Mejor responsiveness de UI
 */
class SerialInputOutputManager(
    private val port: IComController,
    private val listener: Listener
) : Runnable {

    interface Listener {
        fun onNewData(data: ByteArray)
        fun onRunError(exception: Exception)
    }

    companion object {
        private const val TAG = "SerialIoManager"
        private const val READ_TIMEOUT = 100  // ms
        private const val BUFFER_SIZE = 4096
    }

    enum class State {
        STOPPED, RUNNING, STOPPING
    }

    private val readBuffer = ByteArray(BUFFER_SIZE)
    private var state = State.STOPPED
    private var thread: Thread? = null

    fun start() {
        if (state != State.STOPPED) {
            Log.w(TAG, "⚠️ Ya está corriendo")
            return
        }

        state = State.RUNNING
        thread = Thread(this, "AisinoSerialIoManager").apply {
            Log.d(TAG, "Iniciando thread de I/O")
            start()
        }
    }

    fun stop() {
        if (state == State.RUNNING) {
            state = State.STOPPING
            try {
                thread?.join(5000)
            } catch (e: Exception) {
                Log.w(TAG, "Error esperando thread: ${e.message}")
            }
            Log.d(TAG, "Thread detenido")
        }
    }

    override fun run() {
        Log.i(TAG, "🔄 Thread I/O iniciado")

        try {
            while (state == State.RUNNING) {
                // Leer datos
                val bytesRead = port.readData(
                    BUFFER_SIZE,
                    readBuffer,
                    READ_TIMEOUT
                )

                if (bytesRead > 0) {
                    // Nuevos datos
                    val data = readBuffer.copyOf(bytesRead)
                    listener.onNewData(data)
                } else if (bytesRead < 0 && bytesRead != -6) {
                    // Error (excepto timeout)
                    listener.onRunError(Exception("Read error: $bytesRead"))
                    break
                }
                // Si bytesRead == -6 (timeout), continuar
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción en I/O: ${e.message}", e)
            listener.onRunError(e)
        } finally {
            state = State.STOPPED
            Log.i(TAG, "⏹️ Thread I/O finalizado")
        }
    }

    fun getState(): State = state
}
```

**Uso:**

```kotlin
// En tu ViewModel o Activity

private val ioManager = SerialInputOutputManager(
    aisinoComController,
    object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) {
            // Procesar datos automáticamente
            val message = String(data, Charsets.UTF_8)
            Log.i(TAG, "Datos recibidos: $message")

            // Actualizar UI
            _receivedDataFlow.value = message
        }

        override fun onRunError(exception: Exception) {
            Log.e(TAG, "Error I/O", exception)
            _errorFlow.value = exception.message ?: "Unknown error"
        }
    }
)

fun connectPort() {
    aisinoComController.open()
    ioManager.start()  // Iniciar lectura automática
}

fun disconnectPort() {
    ioManager.stop()
    aisinoComController.close()
}
```

### Ejemplo 2: AisinoPortProber.kt

```kotlin
// communication/src/main/java/com/example/communication/libraries/aisino/util/
// AisinoPortProber.kt

package com.example.communication.libraries.aisino.util

import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.libraries.aisino.wrapper.AisinoComController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detector de puertos Aisino mediante prueba activa
 *
 * PROPÓSITO:
 * - Encontrar qué puerto tiene el cable conectado
 * - Fallback si detección por USB no funciona
 * - Verificar dispositivo respondiendo
 */
object AisinoPortProber {

    private const val TAG = "AisinoPortProber"

    // Puertos candidatos (típicamente 0-3 en Aisino)
    private val CANDIDATE_PORTS = listOf(0, 1, 2, 3)

    // Baud rate estándar (115200 es el más común)
    private val CANDIDATE_BAUDS = listOf(115200, 9600, 19200)

    data class ProbeResult(
        val port: Int,
        val baudRate: Int,
        val success: Boolean
    )

    /**
     * Probar puertos hasta encontrar uno que responda
     */
    suspend fun probePort(): ProbeResult? = withContext(Dispatchers.IO) {
        Log.i(TAG, "╔════════════════════════════════════════════════════════")
        Log.i(TAG, "║ PROBANDO PUERTOS AISINO")
        Log.i(TAG, "╠════════════════════════════════════════════════════════")

        for (port in CANDIDATE_PORTS) {
            for (baud in CANDIDATE_BAUDS) {
                Log.d(TAG, "║ Probando puerto $port @ ${baud}bps...")

                try {
                    val controller = AisinoComController(port)

                    // Inicializar
                    controller.init(
                        EnumCommConfBaudRate.fromInt(baud),
                        EnumCommConfParity.NOPAR,
                        EnumCommConfDataBits.DB_8
                    )

                    // Intentar abrir
                    if (controller.open() != 0) {
                        Log.d(TAG, "║   ⚠️ Error abriendo puerto")
                        continue
                    }

                    Log.d(TAG, "║   ✓ Puerto abierto")

                    // Intentar lectura rápida
                    val buffer = ByteArray(128)
                    val bytesRead = controller.readData(
                        128,
                        buffer,
                        500  // timeout 500ms
                    )

                    controller.close()

                    if (bytesRead > 0) {
                        Log.i(TAG, "║ ✅ Puerto $port @ ${baud}bps RESPONDIÓ")
                        Log.i(TAG, "║    Datos: ${String(buffer.take(bytesRead).toByteArray())}")
                        Log.d(TAG, "╚════════════════════════════════════════════════════════")
                        return@withContext ProbeResult(port, baud, true)
                    }

                } catch (e: Exception) {
                    Log.d(TAG, "║   ⚠️ Excepción: ${e.message}")
                }
            }
        }

        Log.w(TAG, "║ ❌ NO SE ENCONTRÓ PUERTO RESPONDIENDO")
        Log.d(TAG, "╚════════════════════════════════════════════════════════")
        null
    }

    /**
     * Extensión para compatibilidad con enums
     */
    private fun EnumCommConfBaudRate.Companion.fromInt(baudRate: Int): EnumCommConfBaudRate {
        return when (baudRate) {
            1200 -> EnumCommConfBaudRate.BPS_1200
            2400 -> EnumCommConfBaudRate.BPS_2400
            4800 -> EnumCommConfBaudRate.BPS_4800
            9600 -> EnumCommConfBaudRate.BPS_9600
            19200 -> EnumCommConfBaudRate.BPS_19200
            38400 -> EnumCommConfBaudRate.BPS_38400
            57600 -> EnumCommConfBaudRate.BPS_57600
            else -> EnumCommConfBaudRate.BPS_115200  // Default
        }
    }
}
```

**Uso en UsbCableDetector:**

```kotlin
class UsbCableDetector(
    private val listener: CableDetectionListener
) {

    companion object {
        private const val TAG = "UsbCableDetector"
    }

    interface CableDetectionListener {
        fun onCableConnected(port: Int)
        fun onCableDisconnected()
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                // Probar puertos
                val result = AisinoPortProber.probePort()

                if (result?.success == true) {
                    Log.i(TAG, "✅ Cable detectado en puerto ${result.port}")
                    listener.onCableConnected(result.port)
                } else {
                    Log.d(TAG, "❌ Sin cable detectado")
                    listener.onCableDisconnected()
                }

                delay(2000)  // Probar cada 2 segundos
            }
        }
    }
}
```

---

## 🎯 Resumen Rápido

**RUTA A**: Copiar drivers USB del demo + crear AisinoUsbComController + AisinoUsbDeviceManager
**RUTA B**: Mejorado AisinoComController con multi-estrategia + USB Host API opcional
**RUTA C**: SerialInputOutputManager + AisinoPortProber (sin cambiar arquitectura)

Cada ruta incluye código listo para copiar/adaptar.

