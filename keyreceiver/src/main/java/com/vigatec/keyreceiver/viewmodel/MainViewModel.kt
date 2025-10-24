package com.vigatec.keyreceiver.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.example.communication.libraries.CommunicationSDKManager
import com.example.communication.libraries.aisino.AisinoCommunicationManager
import com.example.config.CommProtocol
import com.example.config.SystemConfig
import com.example.format.*
import com.example.format.base.IMessageFormatter
import com.example.format.base.IMessageParser
import com.example.manufacturer.KeySDKManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.base.controllers.ped.PedKeyException
import com.example.manufacturer.base.models.KeyAlgorithm
import com.example.manufacturer.base.models.KeyAlgorithm as GenericKeyAlgorithm
import com.example.manufacturer.base.models.PedKeyData
import com.example.persistence.repository.InjectedKeyRepository
import com.vigatec.keyreceiver.ui.events.UiEvent
import com.example.communication.polling.CommLog
import com.vigatec.utils.FormatUtils
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
import java.nio.charset.Charset
import javax.inject.Inject

import com.example.manufacturer.base.models.KeyType as GenericKeyType
import java.util.UUID

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

    private val TAG = "MainViewModel"

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
                var anyDataEver = false
                while (isActive) {
                    val bytesRead = comController!!.readData(buffer.size, buffer, 1000)
                    if (bytesRead > 0) {
                        anyDataEver = true
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
                        // Solo se hace auto-scan al inicializar, NO durante la escucha
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
                Log.d(TAG, "startListeningInternal: Bloque finally de la escucha. Cerrando comController si está abierto.")
                val closeRes = comController?.close()
                CommLog.d(TAG, "close() => $closeRes")
                kotlinx.coroutines.delay(500) // Dar tiempo al SO para liberar el puerto
                if (_connectionStatus.value != ConnectionStatus.ERROR) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
                Log.i(TAG, "startListeningInternal: Hilo de escucha finalizado.")
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
                    } catch (e: Exception) {
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
                    if (currentPedController is com.example.manufacturer.libraries.newpos.wrapper.NewposPedController) {
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
                    val ktkFromDb = injectedKeyRepository.getKeyBySlotAndType(validKtkSlot02, GenericKeyType.TRANSPORT_KEY.name)
                    if (ktkFromDb == null) {
                        throw PedKeyException("KTK no encontrada en slot $validKtkSlot02. Debe inyectarse primero.")
                    }

                    Log.d(TAG, "KTK encontrada en BD:")
                    Log.d(TAG, "  - Slot: ${ktkFromDb.keySlot}")
                    Log.d(TAG, "  - KCV: ${ktkFromDb.kcv}")
                    Log.d(TAG, "  - Algoritmo: ${ktkFromDb.keyAlgorithm}")

                    // Obtener el algoritmo de la KTK para pasarlo al PED
                    val ktkAlgorithm = try {
                        KeyAlgorithm.valueOf(ktkFromDb.keyAlgorithm)
                    } catch (e: Exception) {
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

                    // VALIDACIÓN LONGITUD IPEK SEGÚN ALGORITMO
                    val ipekBytes = command.keyHex.hexToByteArray()
                    val expectedLength = when (genericAlgorithm) {
                        GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> 16 // Ambos 3DES usan 16 bytes para DUKPT
                        GenericKeyAlgorithm.AES_128 -> 16
                        GenericKeyAlgorithm.AES_192 -> 24
                        GenericKeyAlgorithm.AES_256 -> 32
                        else -> throw PedKeyException("Algoritmo no soportado para DUKPT: $genericAlgorithm")
                    }

                    if (ipekBytes.size != expectedLength) {
                        throw PedKeyException(
                            "Longitud de IPEK incorrecta para $genericAlgorithm: " +
                            "recibido ${ipekBytes.size} bytes, esperado $expectedLength bytes. " +
                            "Para DUKPT 3DES (2TDEA y 3TDEA) siempre se usan 16 bytes."
                        )
                    }

                    // INYECCIÓN DUKPT PLAINTEXT:
                    // La IPEK se envía en texto plano al PED
                    // Este método es SOLO para testing - NO usar en producción

                    // CONVERSIÓN KSN: Futurex usa 10 bytes (20 hex chars)
                    // Para AES: NewPOS espera 12 bytes (agrega 2 bytes de ceros al inicio)
                    // Para 3DES: NewPOS espera 10 bytes (sin padding)
                    val ksnBytes = command.ksn.hexToByteArray()
                    val needsKsnPadding = genericAlgorithm in listOf(
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

                    pedController!!.createDukptAESKey(
                        keyIndex = command.keySlot,
                        keyAlgorithm = genericAlgorithm,
                        ipekBytes = ipekBytes,
                        ksnBytes = ksnForInjection,
                        kcvBytes = if (command.keyChecksum.isNotBlank())
                            command.keyChecksum.hexToByteArray()
                        else
                            null
                    )

                    Log.d(TAG, "✓ IPEK DUKPT inyectada exitosamente en slot ${command.keySlot}")
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

        val response = messageFormatter.format("02", listOf(responseCode, command.keyChecksum))
        sendData(response)
        viewModelScope.launch { _snackbarEvent.emit(logMessage) }
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

    private fun unwrapTr31Payload(encryptedPayload: ByteArray): ByteArray {
        if (encryptedPayload.size < 2) throw IllegalArgumentException("Payload TR-31 inválido, muy corto.")

        val keyLengthInBits = (encryptedPayload[0].toInt() and 0xFF shl 8) or (encryptedPayload[1].toInt() and 0xFF)
        val keyLengthInBytes = keyLengthInBits / 8
        val expectedPayloadSize = 2 + keyLengthInBytes

        Log.d(TAG, "TR-31 unwrap: Payload total: ${encryptedPayload.size} bytes. Longitud de llave declarada: $keyLengthInBits bits ($keyLengthInBytes bytes).")

        if (encryptedPayload.size < expectedPayloadSize) {
            throw IllegalArgumentException("Payload TR-31 inconsistente. Se necesitan ${expectedPayloadSize} bytes, pero solo hay ${encryptedPayload.size}.")
        }

        val pureEncryptedKey = encryptedPayload.copyOfRange(2, expectedPayloadSize)
        Log.d(TAG, "Llave pura extraída: ${pureEncryptedKey.size} bytes.")
        return pureEncryptedKey
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

    private fun parseTr31Block(tr31String: String): Tr31KeyBlock {
        Log.d(TAG, "Iniciando parseo de bloque TR-31...")
        class Tr31Reader(private val payload: String) {
            private var cursor = 0
            fun read(length: Int): String {
                if (cursor + length > payload.length) {
                    Log.e(TAG, "TR31Reader: Intento de leer $length chars desde la posición $cursor, pero solo quedan ${payload.length - cursor} chars.")
                    throw IndexOutOfBoundsException("Fin de payload inesperado en TR-31.")
                }
                val field = payload.substring(cursor, cursor + length)
                Log.v(TAG, "TR31Reader: Leído($length): '$field'")
                cursor += length
                return field
            }
        }

        val reader = Tr31Reader(tr31String)
        val versionId = reader.read(1).first()
        val blockLength = reader.read(4).toInt()
        val keyUsage = reader.read(2)
        val algorithm = reader.read(1).first()
        val modeOfUse = reader.read(1).first()
        val keyVersionNumber = reader.read(2)
        val exportability = reader.read(1).first()
        val numberOfOptionalBlocks = reader.read(2).toIntOrNull() ?: 0
        reader.read(1) // Key Context
        reader.read(1) // Reserved

        var optionalBlocksDataLength = 0
        val optionalBlocks = mutableListOf<Tr31OptionalBlock>()
        repeat(numberOfOptionalBlocks) {
            val blockId = reader.read(2)
            val blockDataLength = reader.read(2).toIntOrNull() ?: 0
            val blockData = reader.read(blockDataLength)
            optionalBlocks.add(Tr31OptionalBlock(blockId, blockData))
            optionalBlocksDataLength += (4 + blockDataLength)
        }

        val headerLength = 16 + optionalBlocksDataLength
        val remainingDataString = tr31String.substring(headerLength)
        val remainingBytes = remainingDataString.hexToByteArray()

        val macSizeInBytes = 8
        if (remainingBytes.size < macSizeInBytes) throw IllegalArgumentException("Datos restantes insuficientes para MAC.")

        val mac = remainingBytes.takeLast(macSizeInBytes).toByteArray()
        val encryptedPayload = remainingBytes.dropLast(macSizeInBytes).toByteArray()

        Log.d(TAG, "TR-31 Parseado: Payload Cifrado (${encryptedPayload.size} bytes), MAC (${mac.size} bytes)")

        return Tr31KeyBlock(tr31String, versionId, blockLength, keyUsage, algorithm, modeOfUse, keyVersionNumber, exportability, optionalBlocks, encryptedPayload, mac)
    }

    private fun startCableDetection() {
        Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ INICIANDO DETECCIÓN AUTOMÁTICA DE CABLE USB")
        Log.i(TAG, "╠══════════════════════════════════════════════════════════════")

        cableDetectionJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val detected = detectCableConnection()

                    if (detected != _cableConnected.value) {
                        _cableConnected.value = detected

                        if (detected) {
                            Log.i(TAG, "║ ✅ CABLE USB DETECTADO!")
                            Log.i(TAG, "║    El usuario puede iniciar la escucha manualmente")
                            CommLog.i(TAG, "🔌 ✅ CABLE USB CONECTADO - Listo para comunicación")
                            _snackbarEvent.emit("Cable USB detectado. Pulse 'Iniciar Escucha' para comenzar.")
                        } else {
                            Log.w(TAG, "⚠️ CABLE USB DESCONECTADO (sin cancelar listening)")
                            CommLog.w(TAG, "⚠️ CABLE USB DESCONECTADO - Pero listening continúa activo")
                            // 🔴 CRÍTICO: NO cancelar el listening automáticamente
                            // La detección de cable USB Aisino puede ser inconsistente/falsos positivos
                            // Permitir que el listening continúe esperando datos
                            // El usuario puede detener manualmente si es necesario
                        }
                    }

                    // Chequear cada 3 segundos
                    kotlinx.coroutines.delay(3000)

                } catch (e: Exception) {
                    Log.e(TAG, "║ ❌ Error en detección de cable", e)
                    kotlinx.coroutines.delay(5000) // Esperar más tiempo si hay error
                }
            }
        }

        Log.i(TAG, "║ ✓ Job de detección de cable iniciado")
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
                        val hasKey = checkSlotForKey(pedController, slot)
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
    private suspend fun checkSlotForKey(pedController: IPedController, slot: Int): Boolean {
        return try {
            // IMPLEMENTACIÓN SIMPLIFICADA:
            // En un dispositivo real, aquí deberías usar métodos específicos del PED
            // para verificar si hay llaves instaladas sin crearlas
            
            // Por ahora, simulamos que NO hay llaves instaladas
            // Esto evita que se detecten llaves falsas
            Log.d(TAG, "Verificación de slot $slot: No implementado (simulando vacío)")
            false
            
        } catch (e: Exception) {
            Log.d(TAG, "Error verificando slot $slot: ${e.message}")
            false
        }
    }
}
