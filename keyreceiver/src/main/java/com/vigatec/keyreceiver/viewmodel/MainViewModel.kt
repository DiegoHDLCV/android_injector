package com.vigatec.keyreceiver.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.communication.base.EnumCommConfBaudRate
import com.vigatec.communication.base.EnumCommConfDataBits
import com.vigatec.communication.base.EnumCommConfParity
import com.vigatec.communication.base.IComController
import com.vigatec.communication.libraries.CommunicationSDKManager
import com.vigatec.communication.libraries.aisino.AisinoCommunicationManager
import com.vigatec.config.CommProtocol
import com.vigatec.config.SystemConfig
import com.vigatec.format.*
import com.vigatec.format.base.IMessageFormatter
import com.vigatec.format.base.IMessageParser
import com.vigatec.manufacturer.KeySDKManager
import com.vigatec.manufacturer.ManufacturerHardwareManager
import com.vigatec.manufacturer.base.controllers.ped.IPedController
import com.vigatec.manufacturer.base.controllers.ped.PedKeyException
import com.vigatec.manufacturer.base.models.KeyAlgorithm
import com.vigatec.manufacturer.base.models.KeyAlgorithm as GenericKeyAlgorithm
import com.vigatec.manufacturer.base.models.PedKeyData
import com.vigatec.persistence.repository.InjectedKeyRepository
import com.vigatec.keyreceiver.ui.events.UiEvent
import com.vigatec.communication.polling.CommLog
import com.vigatec.keyreceiver.util.UsbCableDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

import com.vigatec.manufacturer.base.models.KeyType as GenericKeyType
import java.util.UUID
import java.io.IOException

enum class ConnectionStatus {
    DISCONNECTED,
    INITIALIZING,
    OPENING,
    LISTENING,
    CLOSING,
    ERROR
}

/**
 * Representa un evento de inyección de llave para mostrar en el feed visual
 */
data class InjectionEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val keyType: String,           // "KEK", "KTK", "Operacional", etc.
    val slot: String,              // Número de slot donde se inyectó
    val success: Boolean,          // true = éxito, false = fallo
    val kcv: String,               // KCV de la llave inyectada
    val algorithm: String = ""     // TDES, AES, etc. (opcional)
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"

        // USB Error codes that indicate cable disconnection
        private const val USB_GET_STATUS_FAILED = "USB get_status request failed"
        private const val USB_PIPE_ERROR = "USB pipe error"
        private const val USB_NOT_CONNECTED = "not connected"
    }

    private val TAG = MainViewModel.TAG

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _rawReceivedData = MutableStateFlow("")
    val rawReceivedData = _rawReceivedData.asStateFlow()

    private val _cableConnected = MutableStateFlow(false)
    val cableConnected = _cableConnected.asStateFlow()

    // Feed de inyecciones recientes (máximo 5 elementos)
    private val _recentInjections = MutableStateFlow<List<InjectionEvent>>(emptyList())
    val recentInjections = _recentInjections.asStateFlow()
    private val MAX_FEED_ITEMS = 5

    private var comController: IComController? = null
    private var pedController: IPedController? = null
    private var listeningJob: Job? = null
    private var cableDetectionJob: Job? = null
    private val connectionMutex = Mutex()
    
    private lateinit var messageParser: IMessageParser
    private lateinit var messageFormatter: IMessageFormatter
    
    // Detector de cable USB usando múltiples métodos
    private val usbCableDetector = UsbCableDetector(application.applicationContext)

    init {
        Log.i(TAG, "=== INICIALIZANDO MAINVIEWMODEL ===")
        Log.i(TAG, "Configuración inicial:")
        Log.i(TAG, "  - Manager seleccionado: ${SystemConfig.managerSelected}")
        Log.i(TAG, "  - Protocolo seleccionado: ${SystemConfig.commProtocolSelected}")
        Log.i(TAG, "  - Rol del dispositivo: ${SystemConfig.deviceRole}")

        setupProtocolHandlers()
        startCableDetection()  // Iniciar detección de cable automáticamente
        
        // Verificación automática de llaves instaladas al iniciar
        performAutomaticKeyVerification()

        Log.i(TAG, "✓ MainViewModel inicializado completamente")
        Log.i(TAG, "================================================")
    }

    private fun ensureComControllerIsReady(): Boolean {
        if (comController == null) {
            Log.d(TAG, "comController es nulo, intentando obtenerlo de CommunicationSDKManager...")
            CommLog.d(TAG, "Intentando obtener comController…")
            comController = CommunicationSDKManager.getComController()
        }
        if (comController == null) {
            handleError("El controlador de comunicación no está disponible.")
            CommLog.e(TAG, "comController no disponible")
            return false
        }
        return true
    }

    private fun ensurePedControllerIsReady(): Boolean {
        if (pedController == null) {
            Log.d(TAG, "pedController es nulo, intentando obtenerlo de KeySDKManager...")
            pedController = KeySDKManager.getPedController()
        }
        if (pedController == null) {
            handleError("El controlador PED no está disponible.")
            return false
        }
        return true
    }

    private fun setupProtocolHandlers() {
        Log.i(TAG, "=== SETUP PROTOCOL HANDLERS ===")
        Log.i(TAG, "Protocolo seleccionado: ${SystemConfig.commProtocolSelected}")
        
        messageParser = when (SystemConfig.commProtocolSelected) {
            CommProtocol.LEGACY -> {
                Log.i(TAG, "Creando LegacyMessageParser")
                LegacyMessageParser()
            }
            CommProtocol.FUTUREX -> {
                Log.i(TAG, "Creando FuturexMessageParser")
                FuturexMessageParser()
            }
        }
        
        messageFormatter = when (SystemConfig.commProtocolSelected) {
            CommProtocol.LEGACY -> {
                Log.i(TAG, "Usando LegacyMessageFormatter")
                LegacyMessageFormatter
            }
            CommProtocol.FUTUREX -> {
                Log.i(TAG, "Usando FuturexMessageFormatter")
                FuturexMessageFormatter
            }
        }
        
        Log.i(TAG, "✓ Parser configurado: ${messageParser::class.simpleName}")
        Log.i(TAG, "✓ Formatter configurado: ${messageFormatter::class.simpleName}")
        Log.i(TAG, "================================================")
    }

    fun setProtocol(protocol: CommProtocol) = viewModelScope.launch {
        connectionMutex.withLock {
            if (SystemConfig.commProtocolSelected == protocol) return@launch
            Log.i(TAG, "Solicitud para cambiar protocolo a $protocol.")
            stopListeningInternal()
            SystemConfig.commProtocolSelected = protocol
            setupProtocolHandlers()
            _snackbarEvent.emit("Protocolo cambiado a $protocol.")
        }
    }

    private fun handleError(message: String, e: Throwable? = null) {
        Log.e(TAG, message, e)
        _connectionStatus.value = ConnectionStatus.ERROR
        viewModelScope.launch { _snackbarEvent.emit("Error: $message") }
    }

    /**
     * Determina si una excepción es una desconexión USB recuperable
     * @return true si es una desconexión USB (cable desconectado)
     */
    private fun isUsbDisconnectError(e: Exception): Boolean {
        val message = e.message ?: ""
        return when {
            message.contains(USB_GET_STATUS_FAILED, ignoreCase = true) -> true
            message.contains(USB_PIPE_ERROR, ignoreCase = true) -> true
            message.contains(USB_NOT_CONNECTED, ignoreCase = true) -> true
            e.cause?.message?.contains(USB_GET_STATUS_FAILED, ignoreCase = true) == true -> true
            e is IOException && message.contains("USB", ignoreCase = true) -> true
            e is IOException && message.contains("pipe", ignoreCase = true) -> true
            else -> false
        }
    }

    fun startListening(
        baudRate: EnumCommConfBaudRate = EnumCommConfBaudRate.BPS_9600,
        parity: EnumCommConfParity = EnumCommConfParity.NOPAR,
        dataBits: EnumCommConfDataBits = EnumCommConfDataBits.DB_8
    ) = viewModelScope.launch {
        Log.i(TAG, "=== START LISTENING SOLICITADO ===")
        Log.i(TAG, "Estado actual: ${_connectionStatus.value}")
        Log.i(TAG, "Parser configurado: ${if (::messageParser.isInitialized) messageParser::class.simpleName else "NO INICIALIZADO"}")
        Log.i(TAG, "Formatter configurado: ${if (::messageFormatter.isInitialized) messageFormatter::class.simpleName else "NO INICIALIZADO"}")
        
        connectionMutex.withLock {
            if (listeningJob?.isActive == true) {
                Log.w(TAG, "startListening: La escucha ya está activa, cancelando nueva solicitud.")
                _snackbarEvent.emit("La escucha ya está activa.")
                return@withLock
            }
            if (!ensureComControllerIsReady()) return@withLock
            val effectiveBaud = if (SystemConfig.managerSelected.name == "AISINO") {
                val b = AisinoCommunicationManager.getSelectedBaudEnum()
                Log.i(TAG, "Alineando baud con auto-scan AISINO: ${b.name}")
                b
            } else baudRate
            startListeningInternal(effectiveBaud, parity, dataBits)
        }
    }

    private fun startListeningInternal(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ) {
        Log.d(TAG, "startListeningInternal: Intentando iniciar la escucha interna.")
        if (listeningJob?.isActive == true) {
            Log.w(TAG, "startListeningInternal: La escucha ya está activa, cancelando nueva solicitud.")
            return
        }

        listeningJob = viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "startListeningInternal: Lanzando job de escucha en Dispatchers.IO.")
            var readAttempts = 0  // Declare before try so it's accessible in finally
            try {
                _connectionStatus.value = ConnectionStatus.INITIALIZING
                Log.d(TAG, "startListeningInternal: Estado de conexión cambiado a INITIALIZING.")

                var openAttempts = 0
                var openRes = -1
                val maxAttempts = 3

                while (openAttempts < maxAttempts && openRes != 0) {
                    openAttempts++
                    Log.i(TAG, "Intento de conexión #$openAttempts de $maxAttempts")

                    comController!!.init(baudRate, parity, dataBits)
                    Log.d(TAG, "comController inicializado (intento #$openAttempts)")

                    openRes = comController!!.open()
                    Log.i(TAG, "open() intento #$openAttempts => $openRes")
                    CommLog.d(TAG, "open() intento #$openAttempts => $openRes")

                    if (openRes == 0) {
                        Log.i(TAG, "¡Puerto abierto exitosamente en intento #$openAttempts!")
                        break
                    } else {
                        Log.w(TAG, "Fallo al abrir puerto en intento #$openAttempts: código $openRes")
                        if (openAttempts < maxAttempts) {
                            Log.i(TAG, "Esperando antes del siguiente intento...")
                            kotlinx.coroutines.delay(2000)
                        }
                    }
                }

                if (openRes != 0) {
                    val errorMsg = when (openRes) {
                        -1 -> "Error genérico (-1) - Puerto no disponible o en uso"
                        -2 -> "Error de permisos (-2) - Verifique permisos USB"
                        -3 -> "Puerto no encontrado (-3) - Dispositivo no conectado"
                        -4 -> "Puerto ya abierto (-4) - Recurso en uso"
                        else -> "Error desconocido ($openRes)"
                    }
                    throw Exception("No se pudo abrir el puerto tras $maxAttempts intentos. $errorMsg")
                }

                _connectionStatus.value = ConnectionStatus.LISTENING
                Log.i(TAG, "¡Conexión establecida! Escuchando en protocolo ${SystemConfig.commProtocolSelected}.")
                _snackbarEvent.emit("Conexión establecida tras $openAttempts intento(s).")

                val buffer = ByteArray(1024)
                var silentReads = 0
                val loopStartTime = System.currentTimeMillis()

                try {
                    while (isActive) {
                        readAttempts++
                        val readStartTime = System.currentTimeMillis()

                        // 🔧 MEJORA: Usar timeout de 1000ms (más corto que antes)
                        // Esto debería esperar ~1 segundo por lectura
                        val bytesRead = try {
                            comController!!.readData(buffer.size, buffer, 1000)
                        } catch (usbError: Exception) {
                            // 🔍 Detectar si es una desconexión USB (error recuperable)
                            if (isUsbDisconnectError(usbError)) {
                                Log.w(TAG, "⚠️ Desconexión USB detectada en readData() intento #$readAttempts: ${usbError.message}")
                                CommLog.w(TAG, "Desconexión USB durante lectura: ${usbError.message}")
                                // Salir gracefully del loop - dejar que el finally lo cierre
                                break
                            } else {
                                // Otros errores se relanza
                                Log.e(TAG, "❌ EXCEPCIÓN NO-USB en readData() intento #$readAttempts: ${usbError.message}", usbError)
                                throw usbError
                            }
                        }
                        val readDuration = System.currentTimeMillis() - readStartTime

                        // 🔍 DEBUG: Solo loguear cada 100 intentos OR si hay datos/errores
                        if (readAttempts % 100 == 0) {
                            val elapsed = System.currentTimeMillis() - loopStartTime
                            Log.d(TAG, "🔄 ReadAttempt #$readAttempts (${elapsed}ms): bytesRead=$bytesRead, duration=${readDuration}ms")
                        }

                        // 🚨 WARNING: Si readData retorna inmediatamente (duration < 50ms), algo está mal
                        if (readDuration < 50 && bytesRead == 0) {
                            // readData no está esperando el timeout - agregar delay preventivo
                            kotlinx.coroutines.delay(50)
                        }

                        if (bytesRead > 0) {
                            silentReads = 0
                            val received = buffer.copyOf(bytesRead)
                            val receivedString = String(received, Charsets.US_ASCII)
                            val hexString = received.joinToString("") { "%02X".format(it) }

                            val newData = "RX [${System.currentTimeMillis()}]: HEX($hexString) ASCII('$receivedString')\n"
                            _rawReceivedData.value += newData

                            Log.d(TAG, "RX ${bytesRead}B: ${hexString.take(40)}...")
                            CommLog.i(TAG, "RX ${bytesRead}B: $hexString")

                            try {
                                messageParser.appendData(received)

                                var parsedMessage = messageParser.nextMessage()
                                var messageCount = 0

                                while (parsedMessage != null) {
                                    messageCount++
                                    Log.i(TAG, "✓ Mensaje parseado: ${parsedMessage::class.simpleName}")
                                    processParsedCommand(parsedMessage)
                                    parsedMessage = messageParser.nextMessage()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error procesando datos: ${e.message}")
                            }

                            _snackbarEvent.emit("Datos recibidos: ${bytesRead} bytes")
                        } else {
                            silentReads++
                            // ⚠️ DESHABILITADO: El re-scan automático cierra/reabre el puerto
                            // y causa pérdida de datos en comunicación Aisino-to-Aisino

                            // ⚠️ SOLO loguear si hay código de error REAL (no timeout normal)
                            if (bytesRead < 0) {
                                Log.w(TAG, "⚠️ readData error code: $bytesRead (attempt #$readAttempts)")
                            }
                        }
                    }
                } catch (loopException: Exception) {
                    // 🔍 Verificar si la excepción es una desconexión USB
                    if (isUsbDisconnectError(loopException)) {
                        // Desconexión USB - detener gracefully sin error crítico
                        Log.w(TAG, "⚠️ Deteniendo escucha por desconexión USB: ${loopException.message}")
                        CommLog.w(TAG, "Loop de lectura terminado por desconexión USB")
                        // No relanzar - dejar que el finally maneje el cierre
                    } else {
                        // Otras excepciones - relanzar para manejador de errores críticos
                        Log.e(TAG, "❌ EXCEPCIÓN CRÍTICA en el loop de lectura (intento #$readAttempts): ${loopException.message}", loopException)
                        throw loopException
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "startListeningInternal: Excepción durante la escucha: ${e.message}", e)
                    handleError("Error de conexión: ${e.message}", e)
                } else {
                    Log.i(TAG, "startListeningInternal: Job de escucha cancelado, ignorando excepción.", e)
                }
            } finally {
                Log.d(TAG, "startListeningInternal: Closing port after $readAttempts attempts")
                val closeRes = comController?.close()
                CommLog.d(TAG, "close() => $closeRes")
                kotlinx.coroutines.delay(500)
                if (_connectionStatus.value != ConnectionStatus.ERROR) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
                Log.i(TAG, "Listening closed.")
            }
        }
    }

    fun stopListening() = viewModelScope.launch {
        connectionMutex.withLock {
            stopListeningInternal()
        }
    }

    private suspend fun stopListeningInternal() {
        if (listeningJob?.isActive != true) return
        _connectionStatus.value = ConnectionStatus.CLOSING
        listeningJob?.cancel()
        listeningJob?.join()
        listeningJob = null
        kotlinx.coroutines.delay(500) // Dar tiempo al SO para liberar el puerto
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        Log.i(TAG, "Conexión detenida.")
        _snackbarEvent.emit("Conexión cerrada.")
    }

    private fun processParsedCommand(message: ParsedMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Procesando mensaje parseado: $message")
            when (message) {
                is LegacyMessage -> {
                    when (message.command) {
                        "0100" -> {
                            Log.d(TAG, "📥 POLL (0100) recibido desde MasterPOS")
                            _snackbarEvent.emit("POLL recibido - Respondiendo...")
                            handlePollRequest()
                        }
                        else -> Log.d(TAG, "Comando Legacy ${message.command} no manejado")
                    }
                }
                is InjectSymmetricKeyCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Inyectar Llave")
                    handleFuturexInjectKey(message)
                }
                is ReadSerialCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Leer Serial")
                    handleReadSerial(message)
                }
                is WriteSerialCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Escribir Serial")
                    handleWriteSerial(message)
                }
                is DeleteKeyCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Eliminar TODAS las Llaves")
                    handleDeleteAllKeys(message)
                }
                is DeleteSingleKeyCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Eliminar Llave en Slot ${message.keySlot}")
                    handleDeleteSingleKey(message)
                }
                is UninstallAppCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Desinstalar KeyReceiver")
                    handleUninstallApp(message)
                }
                is ValidateDeviceBrandCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Validar Marca del Dispositivo")
                    handleValidateDeviceBrand(message)
                }
                is InjectSymmetricKeyResponse -> {
                    // 📤 RESPUESTA ENVIADA: Confirmación de que el keyreceiver envió correctamente la respuesta
                    // Esto también valida que el serial y modelo se están enviando correctamente
                    Log.i(TAG, "✅ Respuesta de Inyección procesada:")
                    Log.i(TAG, "   - Código: ${message.responseCode}")
                    Log.i(TAG, "   - Serial: ${message.deviceSerial}")
                    Log.i(TAG, "   - Modelo: ${message.deviceModel}")
                    Log.i(TAG, "   - Checksum: ${message.keyChecksum}")
                    CommLog.i(TAG, "Respuesta de inyección enviada - Serial: ${message.deviceSerial}, Modelo: ${message.deviceModel}")
                    _snackbarEvent.emit("✅ Respuesta de inyección enviada correctamente")
                }
                is UninstallAppResponse -> {
                    // 📤 RESPUESTA DE DESINSTALACIÓN ENVIADA: El keyreceiver envió la confirmación al injector
                    Log.i(TAG, "✅ Respuesta de Desinstalación procesada:")
                    Log.i(TAG, "   - Código: ${message.responseCode}")
                    Log.i(TAG, "   - Serial: ${message.deviceSerial}")
                    Log.i(TAG, "   - Modelo: ${message.deviceModel}")
                    CommLog.i(TAG, "Respuesta de desinstalación enviada")
                    _snackbarEvent.emit("✅ Respuesta de desinstalación enviada")
                }
                else -> Log.d(TAG, "Comando ${message::class.simpleName} recibido pero no manejado.")
            }
        }
    }

    private fun sendData(data: ByteArray) {
        if (!ensureComControllerIsReady() || _connectionStatus.value != ConnectionStatus.LISTENING) {
            viewModelScope.launch { _snackbarEvent.emit("Error: Puerto no listo para enviar") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.v(TAG, "RAW_SERIAL_OUT (HEX): ${data.toHexString(true)} (ASCII: '${String(data, Charsets.US_ASCII).replace("\u0002", "<STX>").replace("\u0003", "<ETX>")}')")
                comController!!.write(data, 1000)
            } catch (e: Exception) {
                handleError("Excepción al enviar datos", e)
            }
        }
    }

    private suspend fun handleDeleteAllKeys(command: DeleteKeyCommand) {
        if (!ensurePedControllerIsReady()) {
            handleError("Eliminación cancelada: PedController no está listo.")
            val errorResponse = messageFormatter.format("05", listOf(FuturexErrorCode.DEVICE_IS_BUSY.code))
            sendData(errorResponse)
            return
        }

        var responseCode = FuturexErrorCode.SUCCESSFUL.code
        var logMessage = ""

        val deletionResult = try {
            Log.i(TAG, "handleDeleteAllKeys: Iniciando proceso para eliminar TODAS las llaves (Comando ${command.rawPayload}).")
            val result = pedController!!.deleteAllKeys()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { pedController?.releasePed() } catch (_: Exception) {}
        }

        deletionResult.onSuccess { success ->
            if (success) {
                logMessage = "Todas las llaves han sido eliminadas exitosamente del PED."
                responseCode = FuturexErrorCode.SUCCESSFUL.code
                Log.i(TAG, logMessage)

                Log.d(TAG, "Sincronizando la base de datos local: eliminando todos los registros de llaves.")
                injectedKeyRepository.deleteAllKeys()

            } else {
                logMessage = "El PED informó que la eliminación de llaves no fue exitosa (retornó false)."
                responseCode = FuturexErrorCode.KEY_DELETION_FAILED.code
                Log.w(TAG, logMessage)
            }
        }.onFailure { e ->
            logMessage = "Error durante la eliminación de llaves: ${e.message}"
            responseCode = when(e) {
                is PedKeyException -> FuturexErrorCode.KEY_DELETION_FAILED.code
                else -> FuturexErrorCode.DEVICE_IS_BUSY.code
            }
            Log.e(TAG, "Excepción procesando borrado total: $logMessage", e)
        }

        val response = messageFormatter.format("05", listOf(responseCode))
        sendData(response)
        viewModelScope.launch { _snackbarEvent.emit(logMessage) }
    }

    private suspend fun handleDeleteSingleKey(command: DeleteSingleKeyCommand) {
        if (!ensurePedControllerIsReady()) {
            handleError("Eliminación cancelada: PedController no está listo.")
            val errorResponse = messageFormatter.format("06", listOf(FuturexErrorCode.DEVICE_IS_BUSY.code))
            sendData(errorResponse)
            return
        }

        var logMessage: String
        var responseCode = FuturexErrorCode.SUCCESSFUL.code

        try {
            Log.i(TAG, "handleDeleteSingleKey: Solicitud para borrar llave en slot ${command.keySlot} tipo ${command.keyTypeHex}.")

            val genericKeyType = mapFuturexKeyTypeToGeneric(command.keyTypeHex, "00") // Default subtipo para comando de eliminación

            val keyInDb = injectedKeyRepository.getKeyBySlotAndType(command.keySlot, genericKeyType.name)
                ?: throw PedKeyException("No se encontró registro en BD para la llave en slot ${command.keySlot} tipo ${genericKeyType.name}.")

            val successPed = pedController!!.deleteKey(command.keySlot, genericKeyType)
            if (!successPed) {
                throw PedKeyException("El PED retornó 'false' al intentar borrar la llave del slot ${command.keySlot}.")
            }

            injectedKeyRepository.deleteKey(keyInDb)

            logMessage = "Llave en slot ${command.keySlot} eliminada exitosamente del PED y la BD."
            Log.i(TAG, logMessage)

        } catch (e: Exception) {
            logMessage = e.message ?: "Error inesperado durante el borrado específico."
            responseCode = when(e) {
                is PedKeyException -> FuturexErrorCode.KEY_DELETION_FAILED.code
                else -> FuturexErrorCode.DEVICE_IS_BUSY.code
            }
            Log.e(TAG, "Falló la eliminación de la llave en el slot ${command.keySlot}", e)
        } finally {
            try { pedController?.releasePed() } catch (_: Exception) {}
        }

        val response = messageFormatter.format("06", listOf(responseCode))
        sendData(response)
        viewModelScope.launch { _snackbarEvent.emit(logMessage) }
    }

    private suspend fun handleFuturexInjectKey(command: InjectSymmetricKeyCommand) {
        if (!ensurePedControllerIsReady()) {
            handleError("Inyección cancelada: PedController no está listo.")
            val errorResponse = messageFormatter.format("02", listOf(FuturexErrorCode.DEVICE_IS_BUSY.code, "0000"))
            sendData(errorResponse)
            return
        }

        var responseCode = FuturexErrorCode.SUCCESSFUL.code
        var logMessage = ""
        var injectionStatus = "UNKNOWN"
        val genericKeyType = mapFuturexKeyTypeToGeneric(command.keyType, command.keySubType)
        val genericAlgorithm = mapAlgorithmCodeToGeneric(command.keyAlgorithm)

        val injectionResult = try {
            Log.i(TAG, "handleFuturexInjectKey: Iniciando proceso para slot ${command.keySlot} | Tipo: $genericKeyType | Encryption: ${command.encryptionType}")

            Log.d(TAG, "Procediendo con la inyección en slot ${command.keySlot}...")

            when (command.encryptionType) {
                "00" -> {
                    Log.d(TAG, "Manejando EncryptionType 00: Carga en Claro")
                    val keyDataBytes = command.keyHex.hexToByteArray()

                    when (genericKeyType) {
                        GenericKeyType.MASTER_KEY, GenericKeyType.TRANSPORT_KEY -> {
                            Log.d(TAG, "Inyectando Master/Transport Key en claro usando writeKeyPlain.")
                            pedController!!.writeKeyPlain(command.keySlot, genericKeyType, genericAlgorithm, keyDataBytes, command.keyChecksum.hexToByteArray())
                        }
                        GenericKeyType.DUKPT_INITIAL_KEY -> {
                            Log.d(TAG, "Inyectando DUKPT Initial Key en claro usando writeDukptInitialKey.")
                            pedController!!.writeDukptInitialKey(command.keySlot, genericAlgorithm, keyDataBytes, command.ksn.hexToByteArray(), command.keyChecksum)
                        }
                        else -> {
                            throw PedKeyException("Rechazado: Intento de cargar una llave de trabajo (${genericKeyType.name}) en claro (EncryptionType 00). Las llaves de trabajo deben venir cifradas.")
                        }
                    }
                }
                "01" -> {
                    Log.d(TAG, "Manejando EncryptionType 01: Cifrado bajo KTK pre-cargada")

                    // DEFENSIVO: Validar slot de KTK
                    var validKtkSlot = command.ktkSlot
                    if (validKtkSlot < 0) {
                        Log.w(TAG, "⚠️ Slot de KTK inválido: ${command.ktkSlot}. Usando slot 0 por defecto.")
                        validKtkSlot = 0
                    }

                    val ktkFromDb = injectedKeyRepository.getKeyBySlotAndType(validKtkSlot, GenericKeyType.TRANSPORT_KEY.name) ?: injectedKeyRepository.getKeyBySlotAndType(validKtkSlot, GenericKeyType.MASTER_KEY.name)
                    if (ktkFromDb == null) throw PedKeyException("KTK pre-cargada en slot $validKtkSlot no encontrada.")
                    if (!ktkFromDb.kcv.take(4).equals(command.ktkChecksum.take(4), ignoreCase = true)) throw PedKeyException("El KCV de la KTK en BD ('${ktkFromDb.kcv.take(4)}') no coincide con el del comando ('${command.ktkChecksum.take(4)}').")

                    // Obtener algoritmo de la KTK
                    val ktkAlgorithm = try {
                        KeyAlgorithm.valueOf(ktkFromDb.keyAlgorithm)
                    } catch (_: Exception) {
                        Log.w(TAG, "No se pudo obtener algoritmo de KTK: ${ktkFromDb.keyAlgorithm}, usando genérico como fallback")
                        genericAlgorithm  // Fallback al algoritmo de la llave destino
                    }
                    Log.d(TAG, "Algoritmo de KTK: $ktkAlgorithm")

                    val encryptedKeyBytes = command.keyHex.hexToByteArray()

                    when (genericKeyType) {
                        GenericKeyType.DUKPT_INITIAL_KEY -> {
                            Log.d(TAG, "Llamando a 'writeDukptInitialKeyEncrypted' para una llave DUKPT.")
                            pedController!!.writeDukptInitialKeyEncrypted(command.keySlot, genericAlgorithm, encryptedKeyBytes, command.ksn.hexToByteArray(), validKtkSlot, command.keyChecksum)
                        }
                        GenericKeyType.WORKING_PIN_KEY,
                        GenericKeyType.WORKING_MAC_KEY,
                        GenericKeyType.WORKING_DATA_KEY -> {
                            Log.d(TAG, "Llamando a 'writeKey' para una llave de trabajo cifrada.")
                            val keyData = PedKeyData(keyBytes = encryptedKeyBytes, kcv = command.keyChecksum.hexToByteArray())
                            pedController!!.writeKey(
                                keyIndex = command.keySlot,
                                keyType = genericKeyType,
                                keyAlgorithm = genericAlgorithm,
                                keyData = keyData,
                                transportKeyIndex = validKtkSlot,
                                transportKeyType = GenericKeyType.TRANSPORT_KEY,
                                transportKeyAlgorithm = ktkAlgorithm  // Pasar algoritmo de KTK
                            )
                        }
                        else -> {
                            throw PedKeyException("Tipo de llave cifrada no manejado: $genericKeyType")
                        }
                    }
                }
                "04" -> {
                    Log.d(TAG, "Manejando EncryptionType 04: DUKPT TR-31 (AES)")
                    
                    // Validar que sea una llave DUKPT
                    if (genericKeyType != GenericKeyType.DUKPT_INITIAL_KEY) {
                        throw PedKeyException("EncryptionType 04 solo soporta DUKPT_INITIAL_KEY, recibido: $genericKeyType")
                    }
                    
                    // Validar KSN
                    if (command.ksn.length != 20) {
                        throw PedKeyException("KSN debe tener 20 caracteres para DUKPT, recibido: ${command.ksn.length}")
                    }
                    
                    // Obtener KBPK (Key Block Protection Key) del slot especificado
                    val kbpkSlot = command.ktkSlot
                    val kbpkFromDb = injectedKeyRepository.getKeyBySlotAndType(kbpkSlot, GenericKeyType.TRANSPORT_KEY.name)
                        ?: injectedKeyRepository.getKeyBySlotAndType(kbpkSlot, GenericKeyType.MASTER_KEY.name)
                    
                    if (kbpkFromDb == null) {
                        throw PedKeyException("KBPK no encontrada en slot $kbpkSlot. Debe inyectarse primero.")
                    }
                    
                    Log.d(TAG, "KBPK encontrada en BD:")
                    Log.d(TAG, "  - Slot: ${kbpkFromDb.keySlot}")
                    Log.d(TAG, "  - KCV: ${kbpkFromDb.kcv}")
                    Log.d(TAG, "  - Algoritmo: ${kbpkFromDb.keyAlgorithm}")
                    
                    // Parsear formato TR-31
                    val tr31Data = parseTR31Format(command.keyHex)
                    
                    // Determinar tipo DUKPT basado en algoritmo
                    val dukptType = mapAlgorithmToDukptType(genericAlgorithm)
                    
                    Log.d(TAG, "=== DUKPT TR-31 INJECTION ===")
                    Log.d(TAG, "KBPK Slot: $kbpkSlot")
                    Log.d(TAG, "IPEK Slot: ${command.keySlot}")
                    Log.d(TAG, "DUKPT Type: $dukptType")
                    Log.d(TAG, "KSN: ${command.ksn}")
                    Log.d(TAG, "TR-31 Header: ${tr31Data.first.joinToString("") { "%02X".format(it) }}")
                    Log.d(TAG, "TR-31 Data: ${tr31Data.second.joinToString("") { "%02X".format(it) }}")

                    // Llamar al método específico de DUKPT TR-31
                    val currentPedController = pedController
                    if (currentPedController is com.vigatec.manufacturer.libraries.newpos.wrapper.NewposPedController) {
                        currentPedController.writeDukptIPEK(
                            kbpkIndex = kbpkSlot,
                            ipekIndex = command.keySlot,
                            dukptType = dukptType,
                            ksn = command.ksn.hexToByteArray(),
                            ipekHeader = tr31Data.first,
                            ipekData = tr31Data.second
                        )
                    } else {
                        throw PedKeyException("DUKPT TR-31 solo soportado en NewPOS PED")
                    }
                }
                "02" -> {
                    Log.d(TAG, "Manejando EncryptionType 02: Llave cifrada con KTK (inyección segura por hardware)")

                    // DEFENSIVO: Validar slot de KTK
                    var validKtkSlot02 = command.ktkSlot
                    if (validKtkSlot02 < 0) {
                        Log.w(TAG, "⚠️ Slot de KTK inválido: ${command.ktkSlot}. Usando slot 0 por defecto.")
                        validKtkSlot02 = 0
                    }

                    // Obtener la KTK de la base de datos SOLO para validar el KCV
                    // La llave NO será descifrada en software - el PED lo hará por hardware
                    // Buscar primero TRANSPORT_KEY, luego MASTER_KEY como fallback (igual que EncryptionType 01)
                    val ktkFromDb = injectedKeyRepository.getKeyBySlotAndType(validKtkSlot02, GenericKeyType.TRANSPORT_KEY.name)
                        ?: injectedKeyRepository.getKeyBySlotAndType(validKtkSlot02, GenericKeyType.MASTER_KEY.name)
                    if (ktkFromDb == null) {
                        Log.e(TAG, "❌ KTK no encontrada en slot $validKtkSlot02")
                        Log.e(TAG, "   Buscando TRANSPORT_KEY y MASTER_KEY en slot $validKtkSlot02")
                        // Intentar listar todas las llaves en ese slot para debugging
                        try {
                            val allKeys = injectedKeyRepository.getAllInjectedKeysSync()
                            val keysInSlot = allKeys.filter { it.keySlot == validKtkSlot02 }
                            Log.e(TAG, "   Llaves encontradas en slot $validKtkSlot02: ${keysInSlot.size}")
                            keysInSlot.forEach { key ->
                                Log.e(TAG, "     - Slot: ${key.keySlot}, Tipo: ${key.keyType}, KCV: ${key.kcv}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "   Error al listar llaves para debugging: ${e.message}")
                        }
                        throw PedKeyException("KTK no encontrada en slot $validKtkSlot02. Debe inyectarse primero.")
                    }

                    Log.d(TAG, "KTK encontrada en BD:")
                    Log.d(TAG, "  - Slot: ${ktkFromDb.keySlot}")
                    Log.d(TAG, "  - KCV: ${ktkFromDb.kcv}")
                    Log.d(TAG, "  - Algoritmo: ${ktkFromDb.keyAlgorithm}")

                    // Obtener el algoritmo de la KTK para pasarlo al PED
                    val ktkAlgorithm = try {
                        KeyAlgorithm.valueOf(ktkFromDb.keyAlgorithm)
                    } catch (_: Exception) {
                        Log.w(TAG, "No se pudo obtener algoritmo de KTK: ${ktkFromDb.keyAlgorithm}, usando genérico como fallback")
                        genericAlgorithm
                    }
                    Log.d(TAG, "Algoritmo de KTK: $ktkAlgorithm")

                    // Validar que el KCV de la KTK coincida con el esperado
                    if (!ktkFromDb.kcv.take(4).equals(command.ktkChecksum.take(4), ignoreCase = true)) {
                        throw PedKeyException("El KCV de la KTK en BD ('${ktkFromDb.kcv.take(4)}') no coincide con el esperado en el comando ('${command.ktkChecksum.take(4)}')")
                    }

                    Log.d(TAG, "=== INYECCIÓN SEGURA POR HARDWARE ===")
                    Log.d(TAG, "Llave cifrada (nunca se descifra en software):")
                    Log.d(TAG, "  - Datos cifrados: ${command.keyHex}")
                    Log.d(TAG, "  - KCV esperado: ${command.keyChecksum}")
                    Log.d(TAG, "  - Slot KTK: $validKtkSlot02")
                    Log.d(TAG, "  - KCV KTK: ${command.ktkChecksum}")
                    Log.d(TAG, "El PED descifrará la llave usando la KTK del slot $validKtkSlot02")

                    // ⭐ INYECCIÓN SEGURA: La llave se envía CIFRADA al PED
                    // El descifrado ocurre dentro del HSM/PED usando la KTK ya inyectada
                    // La llave NUNCA está en claro en la memoria de la aplicación
                    pedController!!.writeKey(
                        keyIndex = command.keySlot,
                        keyType = genericKeyType,
                        keyAlgorithm = genericAlgorithm,
                        keyData = PedKeyData(
                            keyBytes = command.keyHex.hexToByteArray(),        // Llave CIFRADA
                            kcv = command.keyChecksum.hexToByteArray()         // KCV de la llave descifrada
                        ),
                        transportKeyIndex = validKtkSlot02,                    // Slot de la KTK
                        transportKeyType = GenericKeyType.TRANSPORT_KEY,       // Tipo: Transport Key
                        transportKeyAlgorithm = ktkAlgorithm                   // Algoritmo de KTK
                    )

                    Log.d(TAG, "✓ Llave cifrada inyectada exitosamente en slot ${command.keySlot} usando descifrado por hardware")
                }
                "05" -> {
                    Log.d(TAG, "=== EncryptionType 05: DUKPT IPEK Plaintext ===")
                    Log.d(TAG, "Inyectando IPEK DUKPT sin cifrado (solo para testing)")
                    Log.d(TAG, "  - Slot: ${command.keySlot}")
                    Log.d(TAG, "  - Algoritmo: $genericAlgorithm")
                    Log.d(TAG, "  - KSN: ${command.ksn}")
                    Log.d(TAG, "  - IPEK length: ${command.keyHex.length / 2} bytes")

                    // Validar que KSN no esté vacío
                    if (command.ksn.isBlank() || command.ksn == "00000000000000000000") {
                        throw PedKeyException("KSN inválido o vacío para DUKPT: ${command.ksn}")
                    }

                    // VALIDACIÓN DE COMPATIBILIDAD CON EL DISPOSITIVO
                    // Aisino PED solo soporta 3DES DUKPT (16 bytes), NO soporta AES DUKPT
                    val pedControllerName = pedController!!::class.simpleName
                    var effectiveAlgorithm = genericAlgorithm
                    val ipekBytes = command.keyHex.hexToByteArray()

                    if (pedControllerName?.contains("Aisino") == true &&
                        genericAlgorithm in listOf(
                            GenericKeyAlgorithm.AES_128,
                            GenericKeyAlgorithm.AES_192,
                            GenericKeyAlgorithm.AES_256
                        )) {

                        // VALIDACIÓN CRÍTICA: Rechazar claves más grandes que 16 bytes
                        // Nunca truncar silenciosamente - es un riesgo de seguridad
                        if (ipekBytes.size > 16) {
                            Log.e(TAG, "❌ INCOMPATIBILIDAD CRÍTICA DE SEGURIDAD")
                            Log.e(TAG, "   Dispositivo Aisino: Solo soporta 3DES (16 bytes máximo)")
                            Log.e(TAG, "   Clave solicitada: $genericAlgorithm (${ipekBytes.size} bytes)")
                            Log.e(TAG, "   Slot: ${command.keySlot}")
                            Log.e(TAG, "   RAZÓN DEL RECHAZO:")
                            Log.e(TAG, "   - Truncar la clave causaría pérdida de entropía")
                            Log.e(TAG, "   - Violaría políticas de seguridad PCI-DSS")
                            Log.e(TAG, "   - Cambiaría el algoritmo original sin auditoría clara")
                            Log.e(TAG, "   SOLUCIÓN:")
                            Log.e(TAG, "   - Generar nuevas claves en tamaño compatible (16 bytes)")
                            Log.e(TAG, "   - O usar dispositivo que soporte $genericAlgorithm")
                            throw PedKeyException(
                                "❌ INYECCIÓN RECHAZADA - INCOMPATIBILIDAD CRÍTICA\n\n" +
                                "Dispositivo: Aisino PED (solo soporta 3DES / 16 bytes)\n" +
                                "Clave solicitada: $genericAlgorithm (${ipekBytes.size} bytes)\n" +
                                "Slot: ${command.keySlot}\n\n" +
                                "NO se pueden truncar claves silenciosamente por razones de seguridad.\n" +
                                "Genere nuevas claves en formato 3DES (16 bytes) o use otro dispositivo."
                            )
                        }

                        // Solo convertir si tamaño es compatible (16 bytes)
                        Log.w(TAG, "⚠️ ADVERTENCIA: Aisino PED NO soporta AES DUKPT")
                        Log.w(TAG, "   Se convertirá automáticamente a 3DES DUKPT (DES_TRIPLE)")
                        Log.w(TAG, "   Algoritmo solicitado: $genericAlgorithm (${ipekBytes.size} bytes)")
                        Log.w(TAG, "   Algoritmo a usar: DES_TRIPLE (16 bytes)")
                        effectiveAlgorithm = GenericKeyAlgorithm.DES_TRIPLE
                    }

                    // VALIDACIÓN LONGITUD IPEK SEGÚN ALGORITMO
                    val expectedLength = when (effectiveAlgorithm) {
                        GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> 16 // Ambos 3DES usan 16 bytes para DUKPT
                        GenericKeyAlgorithm.AES_128 -> 16
                        GenericKeyAlgorithm.AES_192 -> 24
                        GenericKeyAlgorithm.AES_256 -> 32
                        else -> throw PedKeyException("Algoritmo no soportado para DUKPT: $effectiveAlgorithm")
                    }

                    if (ipekBytes.size != expectedLength) {
                        throw PedKeyException(
                            "Longitud de IPEK incorrecta para $effectiveAlgorithm: " +
                            "recibido ${ipekBytes.size} bytes, esperado $expectedLength bytes. " +
                            "Para DUKPT 3DES (2TDEA y 3TDEA) siempre se usan 16 bytes."
                        )
                    }

                    // ✓ VALIDACIÓN CRÍTICA: Rango de slots DUKPT (1-10 para Aisino/Vanstone)
                    val dukptSlot = command.keySlot
                    if (pedControllerName?.contains("Aisino") == true || pedControllerName?.contains("Vanstone") == true) {
                        if (dukptSlot < 1 || dukptSlot > 10) {
                            Log.e(TAG, "❌ SLOT DUKPT FUERA DE RANGO SOPORTADO")
                            Log.e(TAG, "   Slot solicitado: $dukptSlot")
                            Log.e(TAG, "   Rango soportado para Aisino/Vanstone: 1-10")
                            Log.e(TAG, "   Documento oficial: PedDukptWriteTIK_Api parámetro GroupIdx: '1-10, DUKPT key group index'")
                            throw PedKeyException(
                                "❌ SLOT DUKPT INVÁLIDO\n\n" +
                                "Dispositivo: Aisino/Vanstone PED\n" +
                                "Slot solicitado: $dukptSlot\n" +
                                "Rango permitido: 1-10\n\n" +
                                "Los slots DUKPT en Aisino/Vanstone solo pueden estar en el rango 1-10.\n" +
                                "Revise el slot de inyección. Si usa slot 20 o superior, " +
                                "asigne el DUKPT a un slot entre 1-10 en su lugar."
                            )
                        }
                        Log.i(TAG, "✓ Slot DUKPT válido: $dukptSlot (dentro del rango 1-10)")
                    }

                    // INYECCIÓN DUKPT PLAINTEXT:
                    // La IPEK se envía en texto plano al PED
                    // Este método es SOLO para testing - NO usar en producción

                    // CONVERSIÓN KSN: Futurex usa 10 bytes (20 hex chars)
                    // Para AES: NewPOS espera 12 bytes (agrega 2 bytes de ceros al inicio)
                    // Para 3DES: ambos esperan 10 bytes (sin padding)
                    val ksnBytes = command.ksn.hexToByteArray()
                    val needsKsnPadding = effectiveAlgorithm in listOf(
                        GenericKeyAlgorithm.AES_128,
                        GenericKeyAlgorithm.AES_192,
                        GenericKeyAlgorithm.AES_256
                    )

                    val ksnForInjection = if (needsKsnPadding) {
                        val ksnPadded = ByteArray(12)
                        System.arraycopy(ksnBytes, 0, ksnPadded, 2, ksnBytes.size)
                        Log.d(TAG, "KSN Futurex: ${command.ksn} (${ksnBytes.size} bytes)")
                        Log.d(TAG, "KSN Padded para AES: ${ksnPadded.joinToString("") { "%02X".format(it) }} (${ksnPadded.size} bytes)")
                        ksnPadded
                    } else {
                        Log.d(TAG, "KSN Futurex: ${command.ksn} (${ksnBytes.size} bytes)")
                        Log.d(TAG, "KSN sin padding para 3DES: ${ksnBytes.joinToString("") { "%02X".format(it) }} (${ksnBytes.size} bytes)")
                        ksnBytes
                    }

                    // Seleccionar el método correcto según el algoritmo efectivo
                    // Para AES: usar createDukptAESKey (NewPOS)
                    // Para DES: usar writeDukptInitialKey (ambos Aisino y Newpos)
                    if (effectiveAlgorithm in listOf(GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256)) {
                        // Usar createDukptAESKey para algoritmos AES
                        pedController!!.createDukptAESKey(
                            keyIndex = command.keySlot,
                            keyAlgorithm = effectiveAlgorithm,
                            ipekBytes = ipekBytes,
                            ksnBytes = ksnForInjection,
                            kcvBytes = if (command.keyChecksum.isNotBlank())
                                command.keyChecksum.hexToByteArray()
                            else
                                null
                        )
                    } else {
                        // Usar writeDukptInitialKey para algoritmos DES (3DES, etc)
                        pedController!!.writeDukptInitialKey(
                            groupIndex = command.keySlot,
                            keyAlgorithm = effectiveAlgorithm,
                            keyBytes = ipekBytes,
                            initialKsn = ksnForInjection,
                            keyChecksum = if (command.keyChecksum.isNotBlank())
                                command.keyChecksum
                            else
                                null
                        )
                    }

                    Log.d(TAG, "✓ IPEK DUKPT inyectada exitosamente en slot ${command.keySlot}")
                    if (effectiveAlgorithm != genericAlgorithm) {
                        Log.w(TAG, "⚠️ NOTA: Algoritmo convertido de $genericAlgorithm a $effectiveAlgorithm (compatibilidad con dispositivo)")
                    }
                    Log.w(TAG, "⚠️ ADVERTENCIA: IPEK enviada en plaintext - SOLO USAR PARA TESTING")
                }
                else -> throw PedKeyException("Tipo de encriptación '${command.encryptionType}' no soportado.")
            }
            Result.success("Inyección en slot ${command.keySlot} procesada exitosamente.")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { pedController?.releasePed() } catch (_: Exception) {}
        }

        injectionResult.onSuccess {
            logMessage = it
            injectionStatus = if (it.contains("omitida")) "SKIPPED" else "SUCCESSFUL"
            responseCode = FuturexErrorCode.SUCCESSFUL.code
        }.onFailure { e ->
            logMessage = e.message ?: "Error inesperado."
            injectionStatus = "FAILED"
            responseCode = when(e) {
                is PedKeyException -> FuturexErrorCode.INVALID_KEY_TYPE.code
                else -> FuturexErrorCode.DEVICE_IS_BUSY.code
            }
            Log.e(TAG, "Error procesando inyección: $logMessage (Código: $responseCode)", e)
        }

        if (injectionStatus != "SKIPPED") {
            // Para KTK (TRANSPORT_KEY), guardar con datos para poder descifrar posteriormente
            if (genericKeyType == GenericKeyType.TRANSPORT_KEY) {
                Log.d(TAG, "=== GUARDANDO KTK EN BD ===")
                Log.d(TAG, "Datos a guardar:")
                Log.d(TAG, "  - keySlot: ${command.keySlot}")
                Log.d(TAG, "  - keyType: ${genericKeyType.name}")
                Log.d(TAG, "  - keyAlgorithm: ${genericAlgorithm.name}")
                Log.d(TAG, "  - kcv: ${command.keyChecksum}")
                Log.d(TAG, "  - keyData length: ${command.keyHex.length / 2} bytes")
                Log.d(TAG, "  - status: $injectionStatus")
                Log.d(TAG, "  - isKEK: true")
                Log.d(TAG, "  - kekType: KEK_TRANSPORT")
                Log.d(TAG, "  - customName: KTK Slot ${command.keySlot}")
                
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = command.keySlot,
                    keyType = genericKeyType.name,
                    keyAlgorithm = genericAlgorithm.name,
                    kcv = command.keyChecksum,
                    keyData = command.keyHex, // Guardar los datos de la KTK
                    status = injectionStatus,
                    isKEK = true, // Marcar como KEK
                    kekType = "KEK_TRANSPORT",
                    customName = "KTK Slot ${command.keySlot}"
                )
                Log.i(TAG, "KTK guardada con datos completos para descifrado posterior")
            } else {
                injectedKeyRepository.recordKeyInjection(
                    keySlot = command.keySlot,
                    keyType = genericKeyType.name,
                    keyAlgorithm = genericAlgorithm.name,
                    kcv = command.keyChecksum,
                    status = injectionStatus
                )
            }
            Log.i(TAG, "Resultado de inyección para slot ${command.keySlot} registrado en la BD como: $injectionStatus")

            // Agregar al feed visual con información detallada del algoritmo
            val algorithmDetail = getAlgorithmDetail(command.keyAlgorithm)
            addInjectionToFeed(
                InjectionEvent(
                    keyType = genericKeyType.name,
                    slot = command.keySlot.toString(),
                    success = injectionStatus == "SUCCESSFUL",
                    kcv = command.keyChecksum, // AÑADIDO
                    algorithm = algorithmDetail
                )
            )
        }

        // NUEVO: Obtener información del dispositivo receptor usando ManufacturerHardwareManager
        // Esto permite obtener información específica del fabricante (Aisino, NewPOS, etc.)
        val deviceSerial = try {
            ManufacturerHardwareManager.getSerialNumber()
                .takeIf { it.isNotEmpty() }
                ?.padEnd(16, '0')
                ?.take(16)
                ?: "UNKNOWN_SERIAL".padEnd(16, '0')
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener el serial del dispositivo: ${e.message}")
            "UNKNOWN_SERIAL".padEnd(16, '0')
        }

        val deviceModel = try {
            ManufacturerHardwareManager.getModelName()
                .takeIf { it.isNotEmpty() }
                ?.replace(" ", "_")
                ?: "UNKNOWN_MODEL"
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener el modelo del dispositivo: ${e.message}")
            "UNKNOWN_MODEL"
        }
        Log.d(TAG, "Información del dispositivo obtenida - Serial: $deviceSerial, Modelo: $deviceModel")

        val response = messageFormatter.format("02", listOf(responseCode, command.keyChecksum, deviceSerial, deviceModel))
        sendData(response)
        viewModelScope.launch { _snackbarEvent.emit(logMessage) }
        
        // NUEVO: Auto-eliminarse si esta es la última llave y la inyección fue exitosa
        if (injectionStatus == "SUCCESSFUL" && 
            command.totalKeys > 0 && 
            command.currentKeyIndex > 0 && 
            command.currentKeyIndex == command.totalKeys) {
            Log.i(TAG, "=== ÚLTIMA LLAVE INYECTADA - INICIANDO AUTO-ELIMINACIÓN ===")
            Log.i(TAG, "Total de llaves: ${command.totalKeys}")
            Log.i(TAG, "Índice actual: ${command.currentKeyIndex}")
            Log.i(TAG, "Esta es la última llave, auto-eliminando KeyReceiver...")
            
            // Auto-eliminarse después de un delay para asegurar que la respuesta se envíe completamente
            viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(2000)
                autoUninstallAfterLastKey()
            }
        }
    }
    
    /**
     * Auto-elimina la aplicación KeyReceiver después de inyectar la última llave.
     * Similar a handleUninstallApp pero sin esperar comando del injector.
     */
    private suspend fun autoUninstallAfterLastKey() {
        Log.i(TAG, "=== AUTO-ELIMINACIÓN DESPUÉS DE ÚLTIMA LLAVE ===")
        
        try {
            val appContext = getApplication<Application>()
            val packageName = appContext.packageName
            
            Log.i(TAG, "Paquete a desinstalar: $packageName")
            Log.i(TAG, "Manufacturer: ${SystemConfig.managerSelected}")
            
            _snackbarEvent.emit("Última llave inyectada - Auto-eliminando KeyReceiver...")
            
            // Utilizar el controlador de sistema del fabricante para desinstalación silenciosa
            val uninstallResult = ManufacturerHardwareManager.systemController().silentUninstall(packageName)
            
            if (uninstallResult) {
                Log.i(TAG, "✓ Auto-eliminación completada exitosamente por SDK del fabricante")
                _snackbarEvent.emit("✓ KeyReceiver auto-eliminado exitosamente")
            } else {
                Log.w(TAG, "⚠️ Auto-eliminación reportó false desde SDK del fabricante")
                _snackbarEvent.emit("⚠️ Auto-eliminación no completó (pero se envió comando)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error durante auto-eliminación: ${e.message}", e)
            _snackbarEvent.emit("Error en auto-eliminación: ${e.message}")
        }
        
        Log.i(TAG, "================================================")
    }

    private fun handleReadSerial(command: ReadSerialCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Manejando comando para leer número de serie: $command")

            val deviceSerialNumber = "123456789ABCDEFG"

            if (deviceSerialNumber.length != 16) {
                Log.e(TAG, "El número de serie del dispositivo no tiene 16 caracteres. No se puede responder.")
                return@launch
            }

            val responsePayload = messageFormatter.format(
                "03",
                listOf(
                    FuturexErrorCode.SUCCESSFUL.code, // "00"
                    deviceSerialNumber
                )
            )

            sendData(responsePayload)
            _snackbarEvent.emit("Respondiendo con N/S: $deviceSerialNumber")
            Log.i(TAG, "Respuesta de número de serie enviada.")
        }
    }

    private fun handleWriteSerial(command: WriteSerialCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Manejando comando para escribir número de serie: $command")
            Log.i(TAG, "Número de serie a escribir: ${command.serialNumber}")

            var responseCode = FuturexErrorCode.SUCCESSFUL.code
            var logMessage = ""

            try {
                // Validar longitud del número de serie
                if (command.serialNumber.length != 16) {
                    throw Exception("Número de serie inválido: debe tener 16 caracteres (recibido: ${command.serialNumber.length})")
                }

                // En una implementación real, aquí se escribiría el número de serie en memoria no volátil
                // Por ahora, solo simulamos el proceso
                Log.i(TAG, "Simulando escritura del número de serie: ${command.serialNumber}")
                
                // TODO: Implementar escritura real del número de serie según el hardware
                // Esto podría involucrar escribir a EEPROM, flash, o un archivo de configuración

                logMessage = "Número de serie '${command.serialNumber}' escrito exitosamente"
                Log.i(TAG, logMessage)

            } catch (e: Exception) {
                logMessage = "Error escribiendo número de serie: ${e.message}"
                responseCode = FuturexErrorCode.DEVICE_IS_BUSY.code
                Log.e(TAG, logMessage, e)
            }

            val responsePayload = messageFormatter.format(
                "04",
                listOf(responseCode)
            )

            sendData(responsePayload)
            _snackbarEvent.emit(logMessage)
            Log.i(TAG, "Respuesta de escritura de número de serie enviada con código: $responseCode")
        }
    }
    
    private fun handlePollRequest() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "📤 Enviando respuesta POLL (0110) a MasterPOS (forzado Legacy)...")
                val pollResponse = LegacyMessageFormatter.format("0110", "ACK")
                sendData(pollResponse)
                Log.d(TAG, "✅ Respuesta POLL enviada exitosamente")
                _snackbarEvent.emit("Respuesta POLL enviada")
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar respuesta POLL", e)
                _snackbarEvent.emit("Error al responder POLL")
            }
        }
    }

    private fun mapFuturexKeyTypeToGeneric(futurexKeyType: String, keySubType: String): GenericKeyType {
        Log.i(TAG, "Mapeando tipo Futurex '$futurexKeyType' con subtipo '$keySubType'")
        
        return when (futurexKeyType) {
            "01", "0F" -> GenericKeyType.MASTER_KEY
            "06" -> GenericKeyType.TRANSPORT_KEY
            
            // Mapeo específico según el tipo de dispositivo
            "05", "04", "0C" -> {
                // Usar keySubType para determinar el tipo específico
                when (keySubType) {
                    "01" -> {
                        Log.i(TAG, "Tipo $futurexKeyType con SubType $keySubType -> WORKING_PIN_KEY")
                        GenericKeyType.WORKING_PIN_KEY
                    }
                    "02" -> {
                        Log.i(TAG, "Tipo $futurexKeyType con SubType $keySubType -> WORKING_MAC_KEY")
                        GenericKeyType.WORKING_MAC_KEY
                    }
                    "03" -> {
                        Log.i(TAG, "Tipo $futurexKeyType con SubType $keySubType -> WORKING_DATA_KEY")
                        GenericKeyType.WORKING_DATA_KEY
                    }
                    else -> {
                        Log.i(TAG, "Tipo $futurexKeyType con SubType $keySubType -> MASTER_KEY (fallback)")
                        GenericKeyType.MASTER_KEY
                    }
                }
            }
            
            // DUKPT types
            "02", "03", "08", "0B", "10" -> GenericKeyType.DUKPT_INITIAL_KEY
            
            else -> throw PedKeyException("Tipo de llave Futurex no soportado: $futurexKeyType")
        }
    }

    /**
     * Mapea el código de algoritmo Futurex al tipo genérico KeyAlgorithm
     * Códigos:
     * - 00 = 3DES-112 (16 bytes, 2 keys)
     * - 01 = 3DES-168 (24 bytes, 3 keys)
     * - 02 = AES-128 (16 bytes)
     * - 03 = AES-192 (24 bytes)
     * - 04 = AES-256 (32 bytes)
     */
    private fun mapAlgorithmCodeToGeneric(algorithmCode: String): KeyAlgorithm {
        return when (algorithmCode) {
            "00" -> KeyAlgorithm.DES_DOUBLE   // 3DES-112 (2 keys, K1=K3)
            "01" -> KeyAlgorithm.DES_TRIPLE   // 3DES-168 (3 keys)
            "02" -> KeyAlgorithm.AES_128      // AES-128
            "03" -> KeyAlgorithm.AES_192      // AES-192
            "04" -> KeyAlgorithm.AES_256      // AES-256
            else -> {
                Log.w(TAG, "Código de algoritmo desconocido: $algorithmCode, usando 3DES por defecto")
                KeyAlgorithm.DES_TRIPLE
            }
        }
    }

    /**
     * Retorna el nombre detallado del algoritmo para mostrar en UI
     */
    private fun getAlgorithmDetail(algorithmCode: String): String {
        return when (algorithmCode) {
            "00" -> "3DES-112"
            "01" -> "3DES-168"
            "02" -> "AES-128"
            "03" -> "AES-192"
            "04" -> "AES-256"
            else -> "UNKNOWN"
        }
    }

    private fun startCableDetection() {
        Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ INICIANDO DETECCIÓN AUTOMÁTICA DE CABLE USB")
        Log.i(TAG, "║ Estrategia: Hysteresis para evitar falsos positivos")
        Log.i(TAG, "╠══════════════════════════════════════════════════════════════")

        cableDetectionJob = viewModelScope.launch(Dispatchers.IO) {
            var consecutiveDetectionsToChange = 0
            val HYSTERESIS_THRESHOLD = 2 // Requiere 2 detecciones consistentes para cambiar estado
            val DETECTION_INTERVAL_CONNECTED = 3000L // 3s cuando hay cable
            val DETECTION_INTERVAL_DISCONNECTED = 5000L // 5s cuando no hay cable (menos sensible)

            while (isActive) {
                try {
                    val detected = detectCableConnection()
                    val currentState = _cableConnected.value

                    // Si la detección coincide con el estado actual, resetear contador
                    if (detected == currentState) {
                        consecutiveDetectionsToChange = 0
                    } else {
                        // Si cambia, incrementar contador
                        consecutiveDetectionsToChange++

                        // Solo cambiar estado si alcanza hysteresis threshold
                        if (consecutiveDetectionsToChange >= HYSTERESIS_THRESHOLD) {
                            _cableConnected.value = detected
                            consecutiveDetectionsToChange = 0

                            if (detected) {
                                Log.i(TAG, "║ ✅ CABLE USB DETECTADO (confirmado $HYSTERESIS_THRESHOLD veces)!")
                                Log.i(TAG, "║    El usuario puede iniciar la escucha manualmente")
                                CommLog.i(TAG, "🔌 ✅ CABLE USB CONECTADO - Listo para comunicación")
                                _snackbarEvent.emit("Cable USB detectado. Pulse 'Iniciar Escucha' para comenzar.")
                            } else {
                                Log.w(TAG, "⚠️ CABLE USB DESCONECTADO (confirmado $HYSTERESIS_THRESHOLD veces)")
                                CommLog.w(TAG, "⚠️ CABLE USB DESCONECTADO")
                                
                                // Si está escuchando o en cualquier estado activo, detener automáticamente
                                val currentStatus = _connectionStatus.value
                                if (currentStatus == ConnectionStatus.LISTENING ||
                                    currentStatus == ConnectionStatus.INITIALIZING ||
                                    currentStatus == ConnectionStatus.OPENING) {
                                    Log.i(TAG, "║ 🔴 Deteniendo escucha automáticamente por desconexión del cable")
                                    CommLog.i(TAG, "🔴 Deteniendo escucha automáticamente por desconexión del cable")
                                    viewModelScope.launch {
                                        connectionMutex.withLock {
                                            stopListeningInternal()
                                        }
                                    }
                                    _snackbarEvent.emit("Cable desconectado. Escucha detenida automáticamente.")
                                } else {
                                    CommLog.d(TAG, "Cable desconectado pero no hay escucha activa")
                                }
                            }
                        } else {
                            // Registro de transición pendiente (no se hizo el cambio aún)
                            Log.d(TAG, "║ 🔄 Detección transitoria (${consecutiveDetectionsToChange}/$HYSTERESIS_THRESHOLD): $detected != $currentState")
                        }
                    }

                    // Adaptable interval: más frecuente si no hay cable, menos frecuente si hay cable
                    val delay = if (currentState) DETECTION_INTERVAL_CONNECTED else DETECTION_INTERVAL_DISCONNECTED
                    kotlinx.coroutines.delay(delay)

                } catch (e: Exception) {
                    Log.e(TAG, "║ ❌ Error en detección de cable", e)
                    consecutiveDetectionsToChange = 0 // Resetear contador en error
                    kotlinx.coroutines.delay(5000) // Esperar más tiempo si hay error
                }
            }
        }

        Log.i(TAG, "║ ✓ Job de detección de cable iniciado (hysteresis mode)")
        Log.i(TAG, "╚══════════════════════════════════════════════════════════════")
    }

    private fun detectCableConnection(): Boolean {
        // Si está en proceso de conectar/cerrar, mantener estado anterior
        if (_connectionStatus.value == ConnectionStatus.INITIALIZING ||
            _connectionStatus.value == ConnectionStatus.OPENING ||
            _connectionStatus.value == ConnectionStatus.CLOSING) {
            Log.v(TAG, "║ 🔍 Detección: En transición, manteniendo estado actual")
            return _cableConnected.value
        }

        // SIEMPRE hacer detección real del cable, incluso si está LISTENING
        // para detectar desconexiones mientras se escucha
        return try {
            CommLog.d(TAG, "🔍 Iniciando detección de cable USB (4 métodos)...")
            
            // NUEVA DETECCIÓN: Usar 4 métodos diferentes para mayor confiabilidad
            // Método 1: UsbManager (detecta dispositivos USB físicamente conectados) - MÁS CONFIABLE
            val method1Result = usbCableDetector.detectUsingUsbManager()
            
            // Método 2: Verificar nodos de dispositivo en /dev/ con permisos de acceso
            val method2Result = usbCableDetector.detectUsingDeviceNodes()
            
            // Método 3: Archivos del sistema /sys/bus/usb con interfaz serial
            val method3Result = usbCableDetector.detectUsingSystemFiles()
            
            // Método 4: Puertos TTY USB en /sys/class/tty/
            val method4Result = usbCableDetector.detectUsingTtyClass()
            
            // Contar cuántos métodos detectaron
            val methodsCount = listOf(method1Result, method2Result, method3Result, method4Result).count { it }
            
            // LÓGICA MÁS ESTRICTA: Cable presente si AL MENOS 2 de 4 métodos lo detectan
            // O si el método 1 (UsbManager - más confiable) lo detecta
            val detected = methodsCount >= 2 || method1Result
            
            // Mostrar qué métodos específicos detectaron
            val detectingMethods = mutableListOf<String>()
            if (method1Result) detectingMethods.add("UsbManager")
            if (method2Result) detectingMethods.add("/dev/")
            if (method3Result) detectingMethods.add("/sys/bus/usb")
            if (method4Result) detectingMethods.add("/sys/class/tty")
            
            if (detected) {
                val methodsList = detectingMethods.joinToString(", ")
                CommLog.i(TAG, "✅ Cable USB DETECTADO ($methodsCount/4 métodos)")
                CommLog.d(TAG, "  → Métodos que detectaron: $methodsList")
            } else {
                CommLog.w(TAG, "⚠️ Cable USB NO DETECTADO ($methodsCount/4 métodos, requiere ≥2)")
                if (methodsCount == 1) {
                    CommLog.w(TAG, "  → Solo 1 método detectó: ${detectingMethods.firstOrNull() ?: "ninguno"} (insuficiente)")
                }
            }
            
            detected
            
        } catch (e: Exception) {
            CommLog.e(TAG, "❌ Excepción en detección: ${e.message}")
            false
        }
    }

    override fun onCleared() {
        Log.i(TAG, "ViewModel onCleared: Deteniendo escucha y liberando...")
        viewModelScope.launch {
            cableDetectionJob?.cancel()
            cableDetectionJob?.join()
            stopListeningInternal()
            pedController?.releasePed()
        }
        super.onCleared()
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "La cadena HEX debe tener una longitud par." }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(addSpace: Boolean = false): String {
        val separator = if (addSpace) " " else ""
        return joinToString(separator) { "%02X".format(it) }
    }

    /**
     * Agrega un evento de inyección al feed visual
     * Mantiene solo los últimos MAX_FEED_ITEMS elementos
     */
    private fun addInjectionToFeed(event: InjectionEvent) {
        val currentList = _recentInjections.value.toMutableList()
        // Agregar al inicio de la lista (más reciente primero)
        currentList.add(0, event)
        // Mantener solo los últimos MAX_FEED_ITEMS
        if (currentList.size > MAX_FEED_ITEMS) {
            currentList.removeAt(currentList.size - 1)
        }
        _recentInjections.value = currentList
        Log.d(TAG, "Feed actualizado: ${currentList.size} elementos | Último: ${event.keyType} en slot ${event.slot}")
    }

    /**
     * Limpia el feed visual de inyecciones recientes
     * Solo afecta la visualización, NO toca la base de datos
     */
    fun clearRecentInjectionsFeed() {
        _recentInjections.value = emptyList()
        Log.d(TAG, "Feed de inyecciones recientes limpiado")
        viewModelScope.launch {
            _snackbarEvent.emit("Historial visual limpiado")
        }
    }

    // MÉTODOS DE ENVÍO (Comentados - no se usan en la nueva UI, pero se preservan para futuras funcionalidades)

    /*
    fun sendAck() = viewModelScope.launch {
        connectionMutex.withLock {
            if (!ensureComControllerIsReady()) return@withLock
            if (_connectionStatus.value != ConnectionStatus.LISTENING) {
                _snackbarEvent.emit("No hay conexión activa para enviar ACK")
                return@withLock
            }

            try {
                val ackData = byteArrayOf(0x06)
                comController!!.write(ackData, 1000)
                _snackbarEvent.emit("ACK enviado: 06")
            } catch (e: Exception) {
                handleError("Error enviando ACK", e)
            }
        }
    }

    fun sendCustomData(data: String) = viewModelScope.launch {
        connectionMutex.withLock {
            if (!ensureComControllerIsReady()) return@withLock
            if (_connectionStatus.value != ConnectionStatus.LISTENING) {
                _snackbarEvent.emit("No hay conexión activa para enviar datos")
                return@withLock
            }

            try {
                val dataBytes = data.toByteArray(Charsets.US_ASCII)
                comController!!.write(dataBytes, 1000)
                _snackbarEvent.emit("Datos enviados: $data")
            } catch (e: Exception) {
                handleError("Error enviando datos", e)
            }
        }
    }
    */

    /**
     * Parsea formato TR-31 para extraer header y datos
     */
    private fun parseTR31Format(keyHex: String): Pair<ByteArray, ByteArray> {
        val keyBytes = keyHex.hexToByteArray()
        
        // TR-31 tiene un header fijo de 16 bytes seguido de los datos
        // Para simplificar, asumimos que los primeros 16 bytes son el header
        // y el resto son los datos cifrados
        if (keyBytes.size < 16) {
            throw PedKeyException("Datos TR-31 insuficientes: ${keyBytes.size} bytes")
        }
        
        val header = keyBytes.take(16).toByteArray()
        val data = keyBytes.drop(16).toByteArray()
        
        Log.d(TAG, "TR-31 parseado: Header=${header.size} bytes, Data=${data.size} bytes")
        return Pair(header, data)
    }
    
    /**
     * Mapea algoritmo genérico a DukptType de NewPOS
     */
    private fun mapAlgorithmToDukptType(algorithm: KeyAlgorithm): com.pos.device.ped.DukptType {
        return when (algorithm) {
            KeyAlgorithm.AES_128 -> com.pos.device.ped.DukptType.DUKPT_TYPE_AES128
            KeyAlgorithm.AES_192 -> com.pos.device.ped.DukptType.DUKPT_TYPE_AES192
            KeyAlgorithm.AES_256 -> com.pos.device.ped.DukptType.DUKPT_TYPE_AES256
            KeyAlgorithm.DES_DOUBLE -> com.pos.device.ped.DukptType.DUKPT_TYPE_2TDEA
            KeyAlgorithm.DES_TRIPLE -> com.pos.device.ped.DukptType.DUKPT_TYPE_3TDEA
            else -> throw PedKeyException("Algoritmo no soportado para DUKPT: $algorithm")
        }
    }

    /**
     * Realiza verificación automática de llaves instaladas al iniciar la aplicación
     */
    private fun performAutomaticKeyVerification() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
                Log.i(TAG, "║ INICIANDO VERIFICACIÓN AUTOMÁTICA DE LLAVES")
                Log.i(TAG, "╠══════════════════════════════════════════════════════════════")

                // Esperar a que el SDK esté completamente inicializado usando StateFlow
                // En lugar de polling, nos suscribimos al flujo de estado del manager
                val maxWaitTime = 10000L // 10 segundos máximo timeout
                val startTime = System.currentTimeMillis()

                Log.i(TAG, "║ ⏳ Esperando a que KeySDKManager esté listo...")

                // Intentar usar StateFlow primero
                val initState = KeySDKManager.getInitializationState()
                var isReady = false

                if (initState != null) {
                    // Usar StateFlow para esperar el evento de inicialización lista
                    isReady = initState.value

                    if (!isReady) {
                        // Esperar con timeout o hasta que esté listo
                        initState.collect { ready ->
                            if (ready) {
                                isReady = true
                            }
                            val elapsedTime = System.currentTimeMillis() - startTime
                            if (elapsedTime >= maxWaitTime || isReady) {
                                // Salir de la recolección
                                return@collect
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "║ getInitializationState() no disponible, usando método polling...")
                    // Fallback a polling si getInitializationState() no está disponible
                    while (!KeySDKManager.isAisinoReady()) {
                        val elapsedTime = System.currentTimeMillis() - startTime
                        if (elapsedTime >= maxWaitTime) {
                            Log.w(TAG, "║ ⚠️  TIMEOUT: KeySDKManager no estuvo listo en ${maxWaitTime}ms")
                            Log.w(TAG, "║ PED Controller no disponible para verificación automática")
                            Log.i(TAG, "╚══════════════════════════════════════════════════════════════")
                            return@launch
                        }
                        kotlinx.coroutines.delay(100)
                    }
                    isReady = true // Si llegamos aquí sin timeout, está listo
                }

                val elapsedTime = System.currentTimeMillis() - startTime
                if (!isReady) {
                    Log.w(TAG, "║ ⚠️  TIMEOUT: KeySDKManager no estuvo listo en ${maxWaitTime}ms")
                    Log.w(TAG, "║ PED Controller no disponible para verificación automática")
                    Log.i(TAG, "╚══════════════════════════════════════════════════════════════")
                    return@launch
                }

                Log.i(TAG, "║ ✓ KeySDKManager está listo (${elapsedTime}ms de espera)")

                val pedController = KeySDKManager.getPedController()
                if (pedController == null) {
                    Log.w(TAG, "║ ⚠️  PED Controller es null después de verificar que está listo")
                    Log.i(TAG, "╚══════════════════════════════════════════════════════════════")
                    return@launch
                }
                
                Log.i(TAG, "Iniciando verificación automática de llaves instaladas...")
                
                val installedKeys = mutableListOf<String>()
                val maxSlots = 16
                
                for (slot in 0 until maxSlots) {
                    try {
                        // Intentar verificar si hay llave en este slot
                        val hasKey = checkSlotForKey(slot)
                        if (hasKey) {
                            installedKeys.add("Slot $slot")
                            Log.d(TAG, "✓ Llave encontrada en slot $slot")
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Slot $slot vacío o error: ${e.message}")
                    }
                }
                
                if (installedKeys.isNotEmpty()) {
                    Log.i(TAG, "Llaves instaladas encontradas: ${installedKeys.joinToString(", ")}")
                    _snackbarEvent.emit("🔍 Verificación automática: ${installedKeys.size} llaves encontradas")
                } else {
                    Log.i(TAG, "No se encontraron llaves instaladas")
                }
                
                Log.i(TAG, "=== VERIFICACIÓN AUTOMÁTICA COMPLETADA ===")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error en verificación automática de llaves", e)
            }
        }
    }
    
    /**
     * Verifica si hay una llave en un slot específico
     * NOTA: Implementación simplificada que no puede verificar llaves reales
     */
    private fun checkSlotForKey(slot: Int): Boolean {
        return try {
            // IMPLEMENTACIÓN SIMPLIFICADA:
            // En un dispositivo real, aquí deberías usar métodos específicos del PED
            // para verificar si hay llaves instaladas sin crearlas

            // Por ahora, simulamos que NO hay llaves instaladas
            // Esto evita que se detecten llaves falsas
            Log.d(TAG, "Verificación de slot $slot: No implementado (simulando vacío)")
            false

        } catch (_: Exception) {
            Log.d(TAG, "Error verificando slot $slot")
            false
        }
    }

    /**
     * Maneja el comando de desinstalación de la aplicación KeyReceiver (Comando "07")
     * El dispositivo responde con confirmación ANTES de auto-desinstalarse.
     *
     * Utiliza ManufacturerHardwareManager.systemController() para acceder a los SDKs nativos
     * de cada fabricante (AISINO, NEWPOS, UROVO) para desinstalación silenciosa.
     */
    private suspend fun handleUninstallApp(command: UninstallAppCommand) {
        Log.i(TAG, "=== COMANDO DE DESINSTALACIÓN RECIBIDO (07) ===")
        Log.i(TAG, "Confirmationtoken: ${command.confirmationToken}")

        var responseCode = FuturexErrorCode.SUCCESSFUL.code
        var uninstallResult = false

        try {
            // PASO 1: Enviar ACK inmediato (0x06) para confirmar recepción del comando
            Log.i(TAG, "Enviando ACK de recepción (0x06)...")
            val ackData = byteArrayOf(0x06)
            sendData(ackData)
            Log.i(TAG, "ACK enviado correctamente")

            // PASO 2: Pequeña pausa para asegurar que el ACK se transmita
            kotlinx.coroutines.delay(100)

            // PASO 3: Enviar respuesta de confirmación de desinstalación ANTES de desinstalar
            Log.i(TAG, "Enviando respuesta de confirmación de desinstalación...")

            val deviceSerial = android.os.Build.SERIAL ?: "UNKNOWN"
            val deviceModel = android.os.Build.MODEL ?: "UNKNOWN"

            val response = messageFormatter.format("07", listOf(responseCode, deviceSerial, deviceModel))
            sendData(response)

            Log.i(TAG, "Respuesta de confirmación enviada correctamente")
            _snackbarEvent.emit("Desinstalación confirmada - Removiendo app...")

            // PASO 4: Esperar 2 segundos para asegurar que la respuesta se envíe completamente antes de desinstalar
            kotlinx.coroutines.delay(2000)

            // PASO 5: Proceder con la desinstalación usando el SDK del fabricante
            Log.i(TAG, "=== INICIANDO DESINSTALACIÓN VÍA MANUFACTURER SDK ===")
            Log.i(TAG, "Manufacturer: ${SystemConfig.managerSelected}")

            val appContext = getApplication<Application>()
            val packageName = appContext.packageName

            Log.i(TAG, "Paquete a desinstalar: $packageName")

            // Utilizar el controlador de sistema del fabricante para desinstalación silenciosa
            uninstallResult = ManufacturerHardwareManager.systemController().silentUninstall(packageName)

            if (uninstallResult) {
                Log.i(TAG, "✓ Desinstalación completada exitosamente por SDK del fabricante")
                _snackbarEvent.emit("✓ Aplicación desinstalada por SDK del fabricante")
            } else {
                Log.w(TAG, "⚠️ Desinstalación reportó false desde SDK del fabricante")
                _snackbarEvent.emit("⚠️ Desinstalación no completó (pero se envió comando)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error durante manejo de desinstalación: ${e.message}", e)
            responseCode = FuturexErrorCode.DEVICE_IS_BUSY.code
            val errorResponse = messageFormatter.format("07", listOf(responseCode))
            try {
                sendData(errorResponse)
            } catch (err: Exception) {
                Log.e(TAG, "Error enviando respuesta de error: ${err.message}")
            }
            _snackbarEvent.emit("Error en desinstalación: ${e.message}")
        }
    }

    /**
     * Maneja el comando 08 de validación de marca del dispositivo.
     * Obtiene la marca real del dispositivo y la compara con la esperada en el perfil.
     *
     * @param command Comando de validación de marca recibido desde el Injector
     */
    private suspend fun handleValidateDeviceBrand(command: ValidateDeviceBrandCommand) {
        Log.i(TAG, "=== COMANDO DE VALIDACIÓN DE MARCA RECIBIDO (08) ===")
        Log.i(TAG, "Marca esperada: ${command.expectedDeviceType}")

        var responseCode = FuturexErrorCode.SUCCESSFUL.code
        var actualDeviceType = ""

        try {
            // Obtener la marca real del dispositivo usando el manufacturer hardware manager
            Log.i(TAG, "Obteniendo marca real del dispositivo...")
            val realManufacturer = SystemConfig.managerSelected
            actualDeviceType = com.vigatec.config.manufacturerToDeviceTypeCode(realManufacturer)

            Log.i(TAG, "Marca real del dispositivo: ${realManufacturer.name} (código: $actualDeviceType)")
            Log.i(TAG, "Marca esperada en el perfil (código): ${command.expectedDeviceType}")

            // Comparar marcas
            if (command.expectedDeviceType == actualDeviceType) {
                Log.i(TAG, "✓ Validación exitosa - Las marcas coinciden")
                responseCode = FuturexErrorCode.SUCCESSFUL.code
            } else {
                // Mismatch detectado
                Log.w(TAG, "⚠️ Mismatch de marca detectado:")
                Log.w(TAG, "  - Esperada (código): ${command.expectedDeviceType}")
                Log.w(TAG, "  - Real (código): $actualDeviceType")
                responseCode = FuturexErrorCode.DEVICE_BRAND_MISMATCH.code
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error durante validación de marca", e)
            responseCode = FuturexErrorCode.DEVICE_IS_BUSY.code
            _snackbarEvent.emit("Error en validación de marca: ${e.message}")
        }

        // Enviar respuesta
        Log.i(TAG, "Enviando respuesta de validación...")
        Log.i(TAG, "  - Código de respuesta: $responseCode")
        Log.i(TAG, "  - Marca real: $actualDeviceType")

        val response = messageFormatter.format("08", listOf(responseCode, actualDeviceType))
        sendData(response)

        Log.i(TAG, "Respuesta de validación enviada correctamente")
        _snackbarEvent.emit("Respuesta de validación de marca enviada")
    }

}
