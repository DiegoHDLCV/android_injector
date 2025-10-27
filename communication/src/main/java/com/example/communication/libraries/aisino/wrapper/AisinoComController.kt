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
import java.io.InputStream
import java.io.OutputStream
import java.io.File

/**
 * AisinoComController HÍBRIDO con triple estrategia
 *
 * ESTRATEGIA:
 * 1. Intenta puertos virtuales Linux (ttyUSB0/ttyACM0/ttyGS0)
 *    → Permite acceso compartido (Aisino-Aisino paralelo) ✅
 *
 * 2. Intenta USB Host API si contexto disponible
 *    → Detección automática de dispositivos ✅
 *    → Estándar USB (no propietario) ✅
 *
 * 3. Fallback a Rs232Api (comportamiento original)
 *    → Compatible con todos los Aisino ✅
 *    → Acceso exclusivo (limitación)
 *
 * VENTAJA: Combina lo mejor de cada estrategia
 */
class AisinoComController(
    private val comport: Int = 0,
    private val context: Context? = null  // Opcional: para USB Host API
) : IComController {

    companion object {
        private const val TAG = "AisinoComController"
        private const val AISINO_SUCCESS = 0
        private const val AISINO_ERROR = -1

        // Errores de IComController
        private const val SUCCESS = 0
        private const val ERROR_ALREADY_OPEN = -1
        private const val ERROR_OPEN_FAILED = -3
        private const val ERROR_NOT_OPEN = -4
        private const val ERROR_WRITE_FAILED = -5
        private const val ERROR_READ_TIMEOUT_OR_FAILURE = -6
        private const val ERROR_CLOSE_FAILED = -8
        private const val ERROR_GENERAL_EXCEPTION = -99
        private const val ERROR_SET_BAUD_FAILED = -10

        // Puertos virtuales Linux (como NewPOS)
        private val VIRTUAL_PORTS = listOf(
            Pair("/dev/ttyUSB0", "ttyUSB0 (id=7)"),   // Puerto USB CDC virtual
            Pair("/dev/ttyACM0", "ttyACM0 (id=8)"),   // Puerto USB ACM virtual
            Pair("/dev/ttyGS0", "ttyGS0 (id=6)")      // Puerto USB Gadget Serial virtual
        )
    }

    private var isOpen: Boolean = false
    private var storedBaudRate: Int = 9600
    private var storedDataBits: Int = 8
    private var storedParity: Int = 0
    private var storedStopBits: Int = 1

    // Para puertos virtuales
    private var virtualPortInputStream: InputStream? = null
    private var virtualPortOutputStream: OutputStream? = null
    private var usingVirtualPort: Boolean = false
    private var virtualPortPath: String = ""

    // Para USB Host API
    private var usbController: AisinoUsbComController? = null
    private var usingUsbHost: Boolean = false

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

    private fun mapDataBits(dataBits: EnumCommConfDataBits): Int {
        return when (dataBits) {
            EnumCommConfDataBits.DB_7 -> 7
            EnumCommConfDataBits.DB_8 -> 8
        }
    }

    private fun mapParity(parity: EnumCommConfParity): Int {
        return when (parity) {
            EnumCommConfParity.NOPAR -> 0
            EnumCommConfParity.EVEN -> 2
            EnumCommConfParity.ODD -> 1
        }
    }

    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        if (isOpen) {
            Log.w(TAG, "⚠️ Puerto ya abierto al inicializar")
        }
        this.storedBaudRate = mapBaudRate(baudRate)
        this.storedDataBits = mapDataBits(dataBits)
        this.storedParity = mapParity(parity)
        Log.d(TAG, "Init puerto: ${storedBaudRate}bps ${storedDataBits}N${storedStopBits}")
        return SUCCESS
    }

    /**
     * Intenta abrir un puerto virtual Linux como NewPOS
     * Si todos fallan, fallback a Rs232Api.PortOpen_Api(comport)
     */
    override fun open(): Int {
        if (isOpen) {
            Log.d(TAG, "Puerto ya abierto")
            return SUCCESS
        }

        try {
            // PASO 1: Intentar puertos virtuales Linux (como NewPOS)
            Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ AISINO COM OPEN - Intentando puertos virtuales")
            Log.i(TAG, "╠══════════════════════════════════════════════════════════════")

            for ((portPath, portName) in VIRTUAL_PORTS) {
                Log.i(TAG, "║ 🔍 Intentando $portName...")
                try {
                    val portFile = File(portPath)
                    if (portFile.exists() && portFile.canRead() && portFile.canWrite()) {
                        Log.i(TAG, "║ ✓ Puerto virtual encontrado: $portPath")

                        // Abrir puerto virtual como FileInputStream/OutputStream
                        virtualPortInputStream = portFile.inputStream()
                        virtualPortOutputStream = portFile.outputStream()

                        usingVirtualPort = true
                        virtualPortPath = portPath
                        isOpen = true

                        Log.i(TAG, "║ ✅ Puerto virtual abierto exitosamente")
                        Log.i(TAG, "║ ✓ Usando puerto virtual: $portName ($portPath)")
                        Log.i(TAG, "║ ✅ VENTAJA: Acceso compartido permitido (múltiples procesos)")
                        Log.d(TAG, "╚══════════════════════════════════════════════════════════════")

                        return SUCCESS
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "║ ⚠️ $portName no disponible: ${e.message}")
                }
            }

            // PASO 2: Intentar USB Host API si contexto disponible
            if (context != null) {
                Log.i(TAG, "║ [2/3] Intentando USB Host API...")
                val usbResult = tryOpenUsbHost()
                if (usbResult == SUCCESS) {
                    Log.i(TAG, "║ ✅ Usando USB Host API")
                    Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                    return SUCCESS
                }
            } else {
                Log.d(TAG, "║ [2/3] Omitiendo USB Host (sin contexto)")
            }

            // PASO 3: Si todos fallan, fallback a Rs232Api (comportamiento original)
            Log.i(TAG, "║ [3/3] Intentando fallback Rs232Api...")
            Log.i(TAG, "║ Intentando Puerto 0 (Rs232Api.PortOpen_Api)...")

            var result = Rs232Api.PortOpen_Api(comport)
            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "║ ❌ Error al abrir puerto Rs232 $comport: $result")
                Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                return ERROR_OPEN_FAILED
            }

            Rs232Api.PortReset_Api(comport)
            result = Rs232Api.PortSetBaud_Api(comport, storedBaudRate, storedDataBits, storedParity, storedStopBits)

            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "║ ❌ Error al configurar baud puerto $comport: $result")
                Rs232Api.PortClose_Api(comport)
                Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                return ERROR_SET_BAUD_FAILED
            }

            usingVirtualPort = false
            isOpen = true
            Log.i(TAG, "║ ✓ Puerto Rs232 $comport abierto (${storedBaudRate}bps)")
            Log.i(TAG, "║ ⚠️ Advertencia: Usando Puerto 0 (acceso exclusivo, sin compartir)")
            Log.i(TAG, "║ NOTA: Para Aisino-Aisino, considere usar puertos virtuales")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            return SUCCESS

        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al abrir puerto: ${e.message}")
            isOpen = false
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        if (!isOpen) {
            Log.e(TAG, "❌ Puerto no abierto para escritura")
            return ERROR_NOT_OPEN
        }
        if (data.isEmpty()) {
            return 0
        }

        try {
            return when {
                usingUsbHost -> usbController?.write(data, timeout) ?: ERROR_NOT_OPEN
                usingVirtualPort -> {
                    virtualPortOutputStream?.write(data)
                    virtualPortOutputStream?.flush()
                    Log.i(TAG, "📤 TX puerto virtual: ${data.size} bytes")
                    data.size
                }
                else -> {
                    val result = Rs232Api.PortSends_Api(comport, data, data.size)
                    if (result == AISINO_SUCCESS) {
                        Log.i(TAG, "📤 TX puerto $comport: ${data.size} bytes")
                        data.size
                    } else {
                        Log.e(TAG, "❌ Error TX puerto $comport: $result")
                        ERROR_WRITE_FAILED
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción TX: ${e.message}")
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        if (!isOpen) {
            Log.e(TAG, "❌ Puerto no abierto para lectura")
            return ERROR_NOT_OPEN
        }
        if (buffer.isEmpty() || expectedLen <= 0) {
            return 0
        }

        try {
            return when {
                usingUsbHost -> usbController?.readData(expectedLen, buffer, timeout)
                    ?: ERROR_NOT_OPEN
                usingVirtualPort -> {
                    val bytesRead = virtualPortInputStream?.read(buffer, 0, minOf(expectedLen, buffer.size)) ?: 0
                    if (bytesRead > 0) {
                        val hexData = buffer.take(bytesRead).joinToString("") { "%02X".format(it) }
                        Log.i(TAG, "📥 RX puerto virtual: $bytesRead bytes - $hexData")
                    }
                    bytesRead
                }
                else -> {
                    val bytesRead = Rs232Api.PortRecv_Api(comport, buffer, expectedLen, timeout)

                    if (bytesRead < 0) {
                        ERROR_READ_TIMEOUT_OR_FAILURE
                    } else {
                        if (bytesRead > 0) {
                            val hexData = buffer.take(bytesRead).joinToString("") { "%02X".format(it) }
                            Log.i(TAG, "📥 RX puerto $comport: $bytesRead bytes - $hexData")
                        }
                        bytesRead
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción RX: ${e.message}")
            return ERROR_GENERAL_EXCEPTION
        }
    }

    /**
     * Intentar abrir mediante USB Host API
     *
     * @return SUCCESS si se abre correctamente, ERROR_OPEN_FAILED si falla
     */
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

    /**
     * Cerrar puerto, delegando según el tipo
     */
    override fun close(): Int {
        if (!isOpen) {
            return SUCCESS
        }

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
                    val result = Rs232Api.PortClose_Api(comport)
                    isOpen = false

                    if (result != AISINO_SUCCESS) {
                        Log.w(TAG, "⚠️ Error al cerrar puerto $comport: $result")
                        return ERROR_CLOSE_FAILED
                    }

                    try {
                        Rs232Api.PortReset_Api(comport)
                        Log.d(TAG, "✓ Puerto $comport reseteado")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Error al resetear: ${e.message}")
                    }

                    SUCCESS
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al cerrar: ${e.message}")
            isOpen = false
            ERROR_GENERAL_EXCEPTION
        }
    }
}
