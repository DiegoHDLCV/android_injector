package com.vigatec.communication.libraries.aisino

import android.app.Application
import android.content.Context
import android.util.Log
import com.vigatec.communication.base.EnumCommConfBaudRate
import com.vigatec.communication.base.IComController
import com.vigatec.communication.base.controllers.manager.ICommunicationManager
import com.vigatec.communication.libraries.aisino.wrapper.AisinoComController
import com.vanstone.appsdk.client.ISdkStatue
import com.vanstone.appsdk.client.SdkApi
import com.vanstone.trans.api.SystemApi
import com.vanstone.utils.CommonConvert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AisinoCommunicationManager : ICommunicationManager {

    private const val TAG = "AisinoCommManager"
    private var comControllerInstance: AisinoComController? = null
    @Volatile private var isInitialized = false // Para la inicialización de este manager
    private var applicationContext: Application? = null

    // Define el puerto serial a usar. Puede ser 0, 1 u otro según el dispositivo Aisino.
    // Este valor podría necesitar ser configurable o detectado. Por ahora, usamos 0 como default.
    // El manual de Vanstone, en SystemApi.DownLoadSn_Api, menciona "port - [in]Serial Port" y luego
    // en la descripción de ese método indica que "port != 0 && port != 1" es un error, sugiriendo que 0 y 1 son puertos válidos. [cite: 551]
    private const val DEFAULT_AISINO_COMPORT = 0

    // Variables para el puerto y baudios seleccionados por el auto-scan
    private var selectedPort: Int = DEFAULT_AISINO_COMPORT
    private var selectedBaud: Int = 9600

    @Volatile private var scanning = false
    @Volatile private var isInitializing = false
    private val initLock = Any()

    override suspend fun initialize(application: Application) {
        if (isInitialized) {
            Log.i(TAG, "AisinoCommunicationManager ya está inicializado.")
            return
        }
        synchronized(initLock) {
            if (isInitialized) return
            if (isInitializing) {
                Log.w(TAG, "initialize() llamado mientras ya se inicializa; esperando resultado")
                // espera activa breve (infrecuente) – evitar complicación de suspensión
                var spins = 0
                while (isInitializing && !isInitialized && spins < 100) {
                    try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    spins++
                }
                return
            }
            isInitializing = true
        }
        try {
            Log.i(TAG, "Inicializando AisinoCommunicationManager...")
            this.applicationContext = application
            Log.d(TAG, "Inicializando SDK de Vanstone para comunicación...")
            initializeVanstoneSDK(application)
            Log.i(TAG, "SDK de Vanstone inicializado correctamente")
            // Auto-scan de puertos/baudios
            autoScanPortsAndBauds()
            isInitialized = true
            Log.i(TAG, "AisinoCommunicationManager inicializado correctamente.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar SDK de Vanstone", e)
            throw e
        } finally {
            isInitializing = false
        }
    }

    // Safe re-scan para uso externo cuando no llegan datos
    fun safeRescanIfInitialized() {
        if (!isInitialized || isInitializing) return
        Log.i(TAG, "safeRescanIfInitialized: iniciando re-scan de puertos/baudios")
        comControllerInstance?.close()
        comControllerInstance = null
        autoScanPortsAndBauds()
    }

    private fun autoScanPortsAndBauds() {
        if (scanning) {
            return
        }
        scanning = true
        try {
            val ports = com.vigatec.config.SystemConfig.aisinoCandidatePorts
            val bauds = com.vigatec.config.SystemConfig.aisinoCandidateBauds

            // Intentar encontrar todos los puertos disponibles (sin logs por puerto)
            val availablePorts = mutableListOf<Int>()
            for (portNum in 0..15) {
                try {
                    val testController = AisinoComController(comport = portNum, context = applicationContext)
                    testController.init(
                        com.vigatec.communication.base.EnumCommConfBaudRate.BPS_115200,
                        com.vigatec.communication.base.EnumCommConfParity.NOPAR,
                        com.vigatec.communication.base.EnumCommConfDataBits.DB_8
                    )
                    val openRes = testController.open()
                    if (openRes == 0) {
                        availablePorts.add(portNum)
                        testController.close()
                    }
                } catch (e: Exception) {
                    // Puerto no disponible, continuar
                }
            }

            for (p in ports) {
                for (b in bauds) {
                    try {
                        val temp = AisinoComController(comport = p, context = applicationContext)
                        temp.init(
                            when (b) {
                                115200 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_115200
                                57600 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_57600
                                38400 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_38400
                                19200 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_19200
                                9600 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_9600
                                4800 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_4800
                                2400 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_2400
                                else -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_9600
                            },
                            com.vigatec.communication.base.EnumCommConfParity.NOPAR,
                            com.vigatec.communication.base.EnumCommConfDataBits.DB_8
                        )
                        val openRes = temp.open()
                        if (openRes != 0) {
                            continue
                        }
                        // Intento de lectura rápida
                        val buf = ByteArray(16)
                        val read = temp.readData(16, buf, 200)
                        // Criterio de selección: éxito de open; si recibe >0 bytes mejor
                        if (read > 0) {
                            Log.i(TAG, "AutoScan: seleccionó puerto $p @ ${b}bps (datos recibidos)")
                            selectedPort = p
                            selectedBaud = b
                            temp.close()
                            return
                        } else if (selectedPort == DEFAULT_AISINO_COMPORT) {
                            selectedPort = p
                            selectedBaud = b
                        }
                        temp.close()
                    } catch (e: Exception) {
                        // Silenciar excepciones de scan
                    }
                }
            }
            Log.d(TAG, "AutoScan: usando puerto $selectedPort @ ${selectedBaud}bps")
        } finally {
            scanning = false
        }
    }

    fun forceRescan() {
        Log.i(TAG, "Solicitando re-scan manual de puertos Aisino")
        comControllerInstance?.close()
        comControllerInstance = null
        autoScanPortsAndBauds()
    }

    @Synchronized
    override fun getComController(): IComController? {
        if (!isInitialized) {
            Log.e(TAG, "AisinoCommunicationManager no ha sido inicializado. Llama a initialize() primero.")
            return null
        }
        if (comControllerInstance == null) {
            Log.d(TAG, "Creando nueva instancia de AisinoComController para el puerto $selectedPort...")
            try {
                comControllerInstance = AisinoComController(comport = selectedPort, context = applicationContext)
                // Aplicar init con baud seleccionado
                val baudEnum = when (selectedBaud) {
                    115200 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_115200
                    57600 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_57600
                    38400 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_38400
                    19200 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_19200
                    9600 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_9600
                    4800 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_4800
                    2400 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_2400
                    1200 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_1200
                    else -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_9600
                }
                comControllerInstance!!.init(baudEnum, com.vigatec.communication.base.EnumCommConfParity.NOPAR, com.vigatec.communication.base.EnumCommConfDataBits.DB_8)
                Log.i(TAG, "Instancia de AisinoComController creada para puerto $selectedPort baud $selectedBaud.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al crear AisinoComController", e)
                comControllerInstance = null
            }
        }
        return comControllerInstance
    }

    override fun release() {
        Log.i(TAG, "Liberando AisinoCommunicationManager...")
        try {
            comControllerInstance?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar el puerto durante la liberación.", e)
        } finally {
            comControllerInstance = null
            applicationContext = null
            isInitialized = false
            Log.i(TAG, "AisinoCommunicationManager liberado.")
        }
    }

    // Variable para evitar inicializar el SDK múltiples veces
    @Volatile
    private var isVanstoneSDKInitialized = false

    private suspend fun initializeVanstoneSDK(application: Application) = withContext(Dispatchers.IO) {
        if (isVanstoneSDKInitialized) {
            Log.d(TAG, "SDK de Vanstone ya está inicializado, omitiendo.")
            return@withContext
        }

        val context: Context = application.applicationContext

        suspendCancellableCoroutine { continuation ->
            try {
                val curAppDir = context.filesDir.absolutePath
                val pathBytes = CommonConvert.StringToBytes("$curAppDir/\u0000")
                if (pathBytes == null) {
                    val ex = IllegalStateException("Error convirtiendo directorio a bytes para SystemInit_Api.")
                    Log.e(TAG, "Error: pathBytes es null", ex)
                    if (continuation.isActive) continuation.resumeWithException(ex)
                    return@suspendCancellableCoroutine
                }

                Log.d(TAG, "Llamando SystemApi.SystemInit_Api...")
                SystemApi.SystemInit_Api(0, pathBytes, context, object : ISdkStatue {
                    override fun sdkInitSuccessed() {
                        Log.i(TAG, "SystemApi.SystemInit_Api: Éxito. Inicializando SdkApi...")
                        if (!continuation.isActive) return

                        // Paso 2: Inicializar SdkApi
                        SdkApi.getInstance().init(context, object : ISdkStatue {
                            override fun sdkInitSuccessed() {
                                Log.i(TAG, "SdkApi.getInstance().init(): Éxito. Inicialización del SDK completa.")
                                isVanstoneSDKInitialized = true
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }

                            override fun sdkInitFailed() {
                                val ex = IllegalStateException("Falló la inicialización del SDK (SdkApi).")
                                Log.e(TAG, "Error: Falló la inicialización de SdkApi.", ex)
                                if (continuation.isActive) {
                                    continuation.resumeWithException(ex)
                                }
                            }
                        })
                    }

                    override fun sdkInitFailed() {
                        val ex = IllegalStateException("Falló la inicialización del SDK (SystemApi).")
                        Log.e(TAG, "Error: Falló la inicialización de SystemApi.", ex)
                        if (continuation.isActive) {
                            continuation.resumeWithException(ex)
                        }
                    }
                })
            } catch (e: Throwable) {
                Log.e(TAG, "Excepción durante la configuración de initializeVanstoneSDK", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    // Objeto Dummy interno para manejar casos no soportados (ya existe en el archivo original)
    private object DummyCommunicationManager : ICommunicationManager {
        override suspend fun initialize(application: Application) {
            Log.w("DummyCommManager", "Initialize llamado, pero no hace nada.")
        }

        override fun getComController(): IComController? {
            Log.w("DummyCommManager", "getComController llamado, devolviendo null.")
            return null
        }

        override fun release() {
            Log.w("DummyCommManager", "Release llamado, pero no hace nada.")
        }
    }

    fun getSelectedBaudEnum(): EnumCommConfBaudRate {
        return when (selectedBaud) {
            115200 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_115200
            57600 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_57600
            38400 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_38400
            19200 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_19200
            9600 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_9600
            4800 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_4800
            2400 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_2400
            1200 -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_1200
            else -> com.vigatec.communication.base.EnumCommConfBaudRate.BPS_9600
        }
    }
}