package com.vigatec.communication.libraries.aisino.wrapper

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.vigatec.communication.base.EnumCommConfBaudRate
import com.vigatec.communication.base.EnumCommConfDataBits
import com.vigatec.communication.base.EnumCommConfParity
import com.vigatec.communication.base.IComController
import com.vigatec.communication.libraries.aisino.manager.AisinoUsbDeviceManager
import com.vanstone.trans.api.Rs232Api
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

/**
 * AisinoComController HÍBRIDO con detección PARALELA de múltiples estrategias
 *
 * ESTRATEGIA PARALELA (Race Condition):
 * Lanza simultáneamente 3 métodos de detección y usa el primero que tenga éxito:
 *
 * 1. Puertos virtuales Linux (ttyUSB0/ttyACM0/ttyGS0)
 *    → Compatible con Newpos y dispositivos USB-serial estándar ✅
 *    → Permite acceso compartido (múltiples procesos) ✅
 *
 * 2. Cable CH340 especial (Vendor ID: 0x1A86)
 *    → Para comunicación Aisino-Aisino con cable especial ✅
 *    → Detección automática del chip CH340 ✅
 *
 * 3. USB Host API (dispositivos Aisino estándar)
 *    → Detección automática por Vendor ID ✅
 *    → Estándar USB (no propietario) ✅
 *
 * 4. Fallback a Rs232Api (comportamiento original)
 *    → Solo si todas las estrategias paralelas fallan
 *    → Acceso exclusivo (limitación)
 *
 * VENTAJAS:
 * - Detección automática sin configuración manual
 * - El primer método exitoso gana la "carrera"
 * - Soporta tanto Aisino-Aisino (CH340) como Aisino-Newpos (USB OTG)
 * - Máxima velocidad de detección
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

    // Para cable CH340 especial (NUEVO)
    private var ch340Detector: com.vigatec.communication.libraries.ch340.CH340CableDetector? = null
    private var usingCH340Cable: Boolean = false

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
     * Intenta abrir puerto usando detección paralela de múltiples estrategias
     * Si todas fallan, fallback a Rs232Api.PortOpen_Api(comport)
     */
    override fun open(): Int {
        if (isOpen) {
            Log.d(TAG, "Puerto ya abierto")
            return SUCCESS
        }

        try {
            Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ AISINO COM OPEN - Detección Paralela")
            Log.i(TAG, "╠══════════════════════════════════════════════════════════════")

            // ESTRATEGIA PARALELA: Intentar las 3 estrategias simultáneamente
            val parallelSuccess = runBlocking {
                tryOpenParallel()
            }

            if (parallelSuccess) {
                Log.i(TAG, "║ ✅ Puerto abierto exitosamente mediante detección paralela")
                when {
                    usingVirtualPort -> Log.i(TAG, "║ ✓ Modo: Puerto Virtual ($virtualPortPath)")
                    usingCH340Cable -> Log.i(TAG, "║ ✓ Modo: Cable CH340")
                    usingUsbHost -> Log.i(TAG, "║ ✓ Modo: USB Host API")
                }
                Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                return SUCCESS
            }

            // FALLBACK: Si todas las estrategias paralelas fallan, usar Rs232Api
            Log.i(TAG, "║ Todas las estrategias paralelas fallaron")
            Log.i(TAG, "║ Intentando fallback Rs232Api (Puerto $comport)...")

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
            usingUsbHost = false
            usingCH340Cable = false
            isOpen = true
            Log.i(TAG, "║ ✓ Puerto Rs232 $comport abierto (${storedBaudRate}bps)")
            Log.i(TAG, "║ ⚠️ Advertencia: Usando Puerto 0 (acceso exclusivo)")
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
                usingCH340Cable -> {
                    val bytesWritten = ch340Detector?.writeData(data) ?: ERROR_NOT_OPEN
                    if (bytesWritten > 0) {
                        Log.i(TAG, "📤 TX cable CH340: $bytesWritten bytes")
                    }
                    bytesWritten
                }
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
                usingCH340Cable -> {
                    // Pass timeout to CH340 read with default of 50ms polling
                    val data = ch340Detector?.readData(expectedLen, timeout)
                    if (data != null && data.isNotEmpty()) {
                        val bytesRead = minOf(data.size, buffer.size)
                        data.copyInto(buffer, 0, 0, bytesRead)
                        val hexData = buffer.take(bytesRead).joinToString("") { "%02X".format(it) }
                        Log.i(TAG, "📥 RX cable CH340: $bytesRead bytes - $hexData")
                        bytesRead
                    } else {
                        0
                    }
                }
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
     * Estrategia de detección paralela con race condition
     * Lanza las 3 estrategias simultáneamente y usa la primera que tenga éxito
     *
     * Estrategias en paralelo:
     * 1. Puertos virtuales (para Newpos y USB-serial estándar)
     * 2. Cable CH340 (para Aisino-Aisino con cable especial)
     * 3. USB Host API (para dispositivos Aisino estándar)
     */
    private suspend fun tryOpenParallel(): Boolean {
        return coroutineScope {
            Log.i(TAG, "║ Iniciando detección paralela de puertos...")

            // Lanzar las 3 estrategias en paralelo
            val virtualPortDeferred = async { tryOpenVirtualPortsAsync() }
            val ch340Deferred = async {
                // Solo intentar CH340 si hay contexto
                if (context != null) tryDetectCH340CableAsync() else false
            }
            val usbHostDeferred = async {
                // Solo intentar USB Host si hay contexto
                if (context != null) tryOpenUsbHostAsync() else false
            }

            // Esperar a que cualquiera de las 3 termine con éxito
            // Usar select para race condition
            val result = select<Boolean> {
                virtualPortDeferred.onAwait { success ->
                    if (success) {
                        Log.i(TAG, "║ 🏆 GANADOR: Puerto Virtual")
                        // Cancelar las otras tareas
                        ch340Deferred.cancel()
                        usbHostDeferred.cancel()
                        true
                    } else {
                        false
                    }
                }

                ch340Deferred.onAwait { success ->
                    if (success) {
                        Log.i(TAG, "║ 🏆 GANADOR: Cable CH340")
                        // Cancelar las otras tareas
                        virtualPortDeferred.cancel()
                        usbHostDeferred.cancel()
                        true
                    } else {
                        false
                    }
                }

                usbHostDeferred.onAwait { success ->
                    if (success) {
                        Log.i(TAG, "║ 🏆 GANADOR: USB Host API")
                        // Cancelar las otras tareas
                        virtualPortDeferred.cancel()
                        ch340Deferred.cancel()
                        true
                    } else {
                        false
                    }
                }
            }

            // Si select no encontró ganador, esperar a que todas terminen
            if (!result) {
                val allResults = awaitAll(virtualPortDeferred, ch340Deferred, usbHostDeferred)
                Log.d(TAG, "║ Resultados finales: Virtual=${allResults[0]}, CH340=${allResults[1]}, USB=${allResults[2]}")
                allResults.any { it }
            } else {
                result
            }
        }
    }

    /**
     * Intentar abrir mediante USB Host API (VERSIÓN ASYNC SIN CH340)
     * Solo detecta dispositivos Aisino estándar por vendor ID
     */
    private suspend fun tryOpenUsbHostAsync(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "║ [USB] Detectando dispositivos USB Aisino...")
                val usbManager = context!!.getSystemService(Context.USB_SERVICE) as UsbManager
                val deviceManager = AisinoUsbDeviceManager(context!!)
                val devices = deviceManager.findAisinoDevices()

                if (devices.isEmpty()) {
                    Log.d(TAG, "║ [USB] ✗ No hay dispositivos Aisino USB")
                    return@withContext false
                }

                val device = devices[0].device
                if (!deviceManager.hasPermission(device)) {
                    Log.d(TAG, "║ [USB] ✗ Sin permiso USB para ${device.deviceName}")
                    return@withContext false
                }

                val controller = deviceManager.createController(device)
                val initResult = controller.init(
                    EnumCommConfBaudRate.BPS_115200,
                    EnumCommConfParity.NOPAR,
                    EnumCommConfDataBits.DB_8
                )

                if (initResult != SUCCESS) {
                    Log.d(TAG, "║ [USB] ✗ Error inicializando controlador USB")
                    return@withContext false
                }

                val openResult = controller.open()
                if (openResult == SUCCESS) {
                    usbController = controller
                    usingUsbHost = true
                    isOpen = true
                    Log.i(TAG, "║ [USB] ✅ USB Host abierto: ${device.deviceName}")
                    true
                } else {
                    Log.d(TAG, "║ [USB] ✗ Error abriendo USB: $openResult")
                    false
                }
            } catch (e: Exception) {
                Log.d(TAG, "║ [USB] Error general: ${e.message}")
                false
            }
        }
    }

    /**
     * Intentar abrir puertos virtuales Linux (VERSIÓN ASYNC)
     * Compatible con Newpos y otros dispositivos USB-serial estándar
     */
    private suspend fun tryOpenVirtualPortsAsync(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "║ [VIRTUAL] Detectando puertos virtuales...")

                for ((portPath, portName) in VIRTUAL_PORTS) {
                    try {
                        val portFile = File(portPath)
                        if (portFile.exists() && portFile.canRead() && portFile.canWrite()) {
                            Log.i(TAG, "║ [VIRTUAL] ✓ Puerto encontrado: $portPath")

                            // Abrir puerto virtual como FileInputStream/OutputStream
                            virtualPortInputStream = portFile.inputStream()
                            virtualPortOutputStream = portFile.outputStream()

                            usingVirtualPort = true
                            virtualPortPath = portPath
                            isOpen = true

                            Log.i(TAG, "║ [VIRTUAL] ✅ Puerto virtual abierto: $portName")
                            return@withContext true
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "║ [VIRTUAL] ✗ $portName no disponible: ${e.message}")
                    }
                }

                Log.d(TAG, "║ [VIRTUAL] ✗ No hay puertos virtuales disponibles")
                false
            } catch (e: Exception) {
                Log.d(TAG, "║ [VIRTUAL] Error general: ${e.message}")
                false
            }
        }
    }

    /**
     * Detectar y usar cable especial CH340 (VERSIÓN ASYNC)
     * Esto permite comunicación Aisino-Aisino a través de cable con chip CH340
     */
    private suspend fun tryDetectCH340CableAsync(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "║ [CH340] Detectando cable CH340...")
                // Crear instancia de CH340CableDetector
                val detector = com.vigatec.communication.libraries.ch340.CH340CableDetector(context!!)

                // Ejecutar detección de forma asíncrona
                val detected = detector.detectCable()

                if (detected) {
                    Log.i(TAG, "║ [CH340] ✅ Cable CH340 detectado y listo")
                    // Forzar parámetros compatibles con CH340 (115200 8N1) para mantener sincronía con NewPOS
                    if (storedBaudRate != 115200) {
                        Log.i(TAG, "║ [CH340] Ajustando baud rate de ${storedBaudRate} a 115200 para compatibilidad")
                    }
                    storedBaudRate = 115200
                    storedDataBits = 8
                    storedParity = 0
                    storedStopBits = 1
                    detector.configure(storedBaudRate, storedDataBits, storedStopBits, storedParity, 0)

                    ch340Detector = detector
                    usingCH340Cable = true
                    isOpen = true
                    Log.i(TAG, "║ [CH340] ✓ Configurado: ${storedBaudRate}bps ${storedDataBits}N${storedStopBits}")
                    true
                } else {
                    Log.d(TAG, "║ [CH340] ✗ Cable CH340 no encontrado")
                    false
                }
            } catch (e: Exception) {
                Log.d(TAG, "║ [CH340] Error detectando: ${e.message}")
                false
            }
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
                usingCH340Cable -> {
                    try {
                        ch340Detector?.close()
                        Log.d(TAG, "✓ Cable CH340 cerrado")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Error al cerrar cable CH340: ${e.message}")
                    }
                    ch340Detector = null
                    usingCH340Cable = false
                    isOpen = false
                    SUCCESS
                }
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
