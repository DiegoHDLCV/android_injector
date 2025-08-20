package com.vigatec.android_injector.viewmodel

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
import com.example.manufacturer.base.models.PedKeyData
import com.example.persistence.repository.InjectedKeyRepository
import com.vigatec.android_injector.ui.events.UiEvent
import com.example.communication.polling.CommLog
import com.vigatec.utils.FormatUtils
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

enum class ConnectionStatus {
    DISCONNECTED,
    INITIALIZING,
    OPENING,
    LISTENING,
    CLOSING,
    ERROR
}

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

    private var comController: IComController? = null
    private var pedController: IPedController? = null
    private var listeningJob: Job? = null
    private val connectionMutex = Mutex()
    
    private lateinit var messageParser: IMessageParser
    private lateinit var messageFormatter: IMessageFormatter

    init {
        Log.i(TAG, "=== INICIALIZANDO MAINVIEWMODEL ===")
        Log.i(TAG, "Configuración inicial:")
        Log.i(TAG, "  - Manager seleccionado: ${SystemConfig.managerSelected}")
        Log.i(TAG, "  - Protocolo seleccionado: ${SystemConfig.commProtocolSelected}")
        Log.i(TAG, "  - Rol del dispositivo: ${SystemConfig.deviceRole}")
        
        setupProtocolHandlers()
        
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

                        Log.v(TAG, "RAW_SERIAL_IN (HEX): $hexString (ASCII: '$receivedString')")
                        CommLog.i(TAG, "RX ${bytesRead}B: $hexString")

                        // ⚠️ CRÍTICO: Enviar datos al parser para procesamiento
                        Log.i(TAG, "=== PROCESANDO DATOS RECIBIDOS ===")
                        Log.i(TAG, "Bytes recibidos: ${received.size}")
                        Log.i(TAG, "Parser configurado: ${messageParser::class.simpleName}")
                        Log.i(TAG, "Protocolo actual: ${SystemConfig.commProtocolSelected}")
                        
                        try {
                            Log.i(TAG, "Enviando datos al parser Futurex...")
                            messageParser.appendData(received)
                            Log.i(TAG, "✓ Datos enviados al parser")
                            
                            // Procesar mensajes parseados
                            Log.i(TAG, "Intentando parsear mensajes...")
                            var parsedMessage = messageParser.nextMessage()
                            var messageCount = 0
                            
                            while (parsedMessage != null) {
                                messageCount++
                                Log.i(TAG, "Mensaje parseado #$messageCount: $parsedMessage")
                                processParsedCommand(parsedMessage)
                                parsedMessage = messageParser.nextMessage()
                            }
                            
                            if (messageCount == 0) {
                                Log.i(TAG, "⚠️ No se pudo parsear ningún mensaje completo")
                            } else {
                                Log.i(TAG, "✓ Se procesaron $messageCount mensajes")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "✗ Error procesando datos: ${e.message}", e)
                        }
                        
                        Log.i(TAG, "================================================")

                        _snackbarEvent.emit("Datos recibidos: ${bytesRead} bytes")
                    } else {
                        silentReads++
                        if (!anyDataEver && silentReads % 5 == 0) {
                            //Log.i(TAG, "${silentReads} lecturas silenciosas AISINO - intentando re-scan")
                            CommunicationSDKManager.rescanIfSupported()
                            comController = CommunicationSDKManager.getComController()
                            if (comController != null) {
                                comController!!.init(baudRate, parity, dataBits)
                                comController!!.open()
                                //Log.i(TAG, "Re-scan aplicado y puerto reabierto")
                            }
                        }
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

            val genericKeyType = mapFuturexKeyTypeToGeneric(command.keyTypeHex)

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
        val genericKeyType = mapFuturexKeyTypeToGeneric(command.keyType)
        val genericAlgorithm = KeyAlgorithm.DES_TRIPLE

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
                    val ktkFromDb = injectedKeyRepository.getKeyBySlotAndType(command.ktkSlot, GenericKeyType.TRANSPORT_KEY.name) ?: injectedKeyRepository.getKeyBySlotAndType(command.ktkSlot, GenericKeyType.MASTER_KEY.name)
                    if (ktkFromDb == null) throw PedKeyException("KTK pre-cargada en slot ${command.ktkSlot} no encontrada.")
                    if (!ktkFromDb.kcv.take(4).equals(command.ktkChecksum.take(4), ignoreCase = true)) throw PedKeyException("El KCV de la KTK en BD ('${ktkFromDb.kcv.take(4)}') no coincide con el del comando ('${command.ktkChecksum.take(4)}').")

                    val encryptedKeyBytes = command.keyHex.hexToByteArray()

                    when (genericKeyType) {
                        GenericKeyType.DUKPT_INITIAL_KEY -> {
                            Log.d(TAG, "Llamando a 'writeDukptInitialKeyEncrypted' para una llave DUKPT.")
                            pedController!!.writeDukptInitialKeyEncrypted(command.keySlot, genericAlgorithm, encryptedKeyBytes, command.ksn.hexToByteArray(), command.ktkSlot, command.keyChecksum)
                        }
                        GenericKeyType.WORKING_PIN_KEY,
                        GenericKeyType.WORKING_MAC_KEY,
                        GenericKeyType.WORKING_DATA_ENCRYPTION_KEY -> {
                            Log.d(TAG, "Llamando a 'writeKey' para una llave de trabajo cifrada.")
                            val keyData = PedKeyData(keyBytes = encryptedKeyBytes, kcv = command.keyChecksum.hexToByteArray())
                            pedController!!.writeKey(
                                keyIndex = command.keySlot,
                                keyType = genericKeyType,
                                keyAlgorithm = genericAlgorithm,
                                keyData = keyData,
                                transportKeyIndex = command.ktkSlot,
                                transportKeyType = GenericKeyType.TRANSPORT_KEY
                            )
                        }
                        else -> {
                            throw PedKeyException("Tipo de llave cifrada no manejado: $genericKeyType")
                        }
                    }
                }
                "02" -> {
                    Log.d(TAG, "Manejando EncryptionType 02: Cifrado con KTK en claro")
                    if (command.ktkHex == null) throw PedKeyException("Falta la KTK en claro para el tipo de cifrado 02.")

                    Log.d(TAG, "Paso 1/2: Inyectando KTK en claro en slot ${command.ktkSlot}")
                    pedController!!.writeKeyPlain(
                        command.ktkSlot,
                        GenericKeyType.TRANSPORT_KEY,
                        genericAlgorithm,
                        command.ktkHex!!.hexToByteArray(),
                        command.ktkChecksum.hexToByteArray()
                    )

                    Log.d(TAG, "Paso 2/2: Parseando TR-31 para inyectar la llave final")
                    val tr31Block = parseTr31Block(command.keyHex)
                    val encryptedKey = unwrapTr31Payload(tr31Block.encryptedPayload)

                    pedController!!.writeDukptInitialKeyEncrypted(command.keySlot, genericAlgorithm, encryptedKey, command.ksn.hexToByteArray(), command.ktkSlot, command.keyChecksum)
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
            injectedKeyRepository.recordKeyInjection(
                keySlot = command.keySlot,
                keyType = genericKeyType.name,
                keyAlgorithm = genericAlgorithm.name,
                kcv = command.keyChecksum,
                status = injectionStatus
            )
            Log.i(TAG, "Resultado de inyección para slot ${command.keySlot} registrado en la BD como: $injectionStatus")
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

    private fun mapFuturexKeyTypeToGeneric(futurexKeyType: String): GenericKeyType {
        return when (futurexKeyType) {
            "01", "0F" -> GenericKeyType.MASTER_KEY
            "06" -> GenericKeyType.TRANSPORT_KEY
            "05" -> GenericKeyType.WORKING_PIN_KEY
            "04" -> GenericKeyType.WORKING_MAC_KEY
            "0C" -> GenericKeyType.WORKING_DATA_ENCRYPTION_KEY
            "03", "08" -> GenericKeyType.DUKPT_INITIAL_KEY
            else -> throw PedKeyException("Tipo de llave Futurex no soportado: $futurexKeyType")
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

    override fun onCleared() {
        Log.i(TAG, "ViewModel onCleared: Deteniendo escucha y liberando...")
        viewModelScope.launch {
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
}
