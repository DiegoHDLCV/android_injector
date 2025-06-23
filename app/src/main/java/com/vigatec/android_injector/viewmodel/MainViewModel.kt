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

    // --- Flows, Controladores, etc. ---
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()

    // --- PROPIEDAD AÑADIDA QUE FALTABA ---
    private val _rawReceivedData = MutableStateFlow("")
    val rawReceivedData = _rawReceivedData.asStateFlow()

    private var comController: IComController? = null
    private var pedController: IPedController? = null
    private var listeningJob: Job? = null
    private val connectionMutex = Mutex()
    private lateinit var messageParser: IMessageParser
    private lateinit var messageFormatter: IMessageFormatter

    init {
        setupProtocolHandlers()
        Log.i(TAG, "MainViewModel creado.")
    }

    private fun ensureComControllerIsReady(): Boolean {
        if (comController == null) {
            Log.d(TAG, "comController es nulo, intentando obtenerlo de CommunicationSDKManager...")
            comController = CommunicationSDKManager.getComController()
        }
        if (comController == null) {
            handleError("El controlador de comunicación no está disponible.")
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
        messageParser = when (SystemConfig.commProtocolSelected) {
            CommProtocol.LEGACY -> LegacyMessageParser()
            CommProtocol.FUTUREX -> FuturexMessageParser()
        }
        messageFormatter = when (SystemConfig.commProtocolSelected) {
            CommProtocol.LEGACY -> LegacyMessageFormatter
            CommProtocol.FUTUREX -> FuturexMessageFormatter
        }
        Log.i(TAG, "Protocolo de comunicación establecido en: ${SystemConfig.commProtocolSelected}")
    }

    // --- FUNCIÓN AÑADIDA QUE FALTABA ---
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

    // --- FUNCIÓN AÑADIDA QUE FALTABA ---
    fun startListening(
        baudRate: EnumCommConfBaudRate = EnumCommConfBaudRate.BPS_9600,
        parity: EnumCommConfParity = EnumCommConfParity.NOPAR,
        dataBits: EnumCommConfDataBits = EnumCommConfDataBits.DB_8
    ) = viewModelScope.launch {
        connectionMutex.withLock {
            if (!ensureComControllerIsReady()) return@withLock
            startListeningInternal(baudRate, parity, dataBits)
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
                comController!!.init(baudRate, parity, dataBits)
                Log.d(TAG, "startListeningInternal: comController inicializado.")
                comController!!.open()
                Log.d(TAG, "startListeningInternal: Puerto de comunicación abierto.")
                _connectionStatus.value = ConnectionStatus.LISTENING
                Log.i(TAG, "¡Conexión establecida! Escuchando en protocolo ${SystemConfig.commProtocolSelected}.")
                _snackbarEvent.emit("Conexión establecida.")

                val buffer = ByteArray(1024)
                while (isActive) {
                    Log.v(TAG, "startListeningInternal: Esperando datos (búfer de ${buffer.size} bytes)...")
                    val bytesRead = comController!!.readData(buffer.size, buffer, 5000)
                    if (bytesRead > 0) {
                        val received = buffer.copyOf(bytesRead)
                        val receivedString = String(received, Charsets.US_ASCII)
                        _rawReceivedData.value += receivedString // Actualiza el log en la UI
                        Log.v(TAG, "RAW_SERIAL_IN (HEX): ${received.toHexString(true)} (ASCII: '$receivedString')")
                        messageParser.appendData(received)
                        var parsedMessage: ParsedMessage?
                        do {
                            parsedMessage = messageParser.nextMessage()
                            parsedMessage?.let {
                                Log.d(TAG, "startListeningInternal: Mensaje parseado encontrado: ${it::class.simpleName}.")
                                processParsedCommand(it)
                            } ?: Log.v(TAG, "startListeningInternal: No hay más mensajes completos para parsear en el buffer.")
                        } while (parsedMessage != null && isActive)
                    } else if (bytesRead < 0 && bytesRead != -6) {
                        Log.e(TAG, "startListeningInternal: Error crítico de lectura de puerto (código: $bytesRead)")
                        throw Exception("Error crítico de lectura de puerto (código: $bytesRead)")
                    } else if (bytesRead == 0) {
                        Log.v(TAG, "startListeningInternal: No se leyeron bytes en este ciclo (timeout).")
                    } else if (bytesRead == -6) {
                        Log.v(TAG, "startListeningInternal: Error -6 (Posiblemente puerto no disponible o desconectado), reintentando.")
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
                comController?.close()
                if (_connectionStatus.value != ConnectionStatus.ERROR) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    Log.d(TAG, "startListeningInternal: Estado de conexión cambiado a DISCONNECTED (no hubo error previo).")
                } else {
                    Log.d(TAG, "startListeningInternal: Estado de conexión se mantuvo en ERROR.")
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
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        Log.i(TAG, "Conexión detenida.")
        _snackbarEvent.emit("Conexión cerrada.")
    }

    private fun processParsedCommand(message: ParsedMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Procesando mensaje parseado: $message")
            when (message) {
                is InjectSymmetricKeyCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Inyectar Llave")
                    handleFuturexInjectKey(message)
                }
                //... otros casos
                else -> {
                    Log.d(TAG, "Comando ${message::class.simpleName} recibido pero no manejado.")
                }
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
                comController!!.write(data, 1000)
            } catch (e: Exception) {
                handleError("Excepción al enviar datos", e)
            }
        }
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

        val injectionResult = runCatching {
            Log.i(TAG, "handleFuturexInjectKey: Iniciando proceso para slot ${command.keySlot} | Tipo: $genericKeyType | Encryption: ${command.encryptionType}")

            // --- Lógica de Verificación (puedes añadirla aquí) ---

            Log.d(TAG, "Procediendo con la inyección en slot ${command.keySlot}...")

            when (command.encryptionType) {
                "00" -> {
                    Log.d(TAG, "Manejando EncryptionType 00: Carga en Claro")
                    val keyDataBytes = command.keyHex.hexToByteArray()
                    if (genericKeyType == GenericKeyType.DUKPT_INITIAL_KEY) {
                        pedController!!.writeDukptInitialKey(command.keySlot, genericAlgorithm, keyDataBytes, command.ksn.hexToByteArray(), command.keyChecksum)
                    } else {
                        pedController!!.writeKeyPlain(command.keySlot, genericKeyType, genericAlgorithm, keyDataBytes, command.keyChecksum?.hexToByteArray())
                    }
                }
                "01" -> {
                    Log.d(TAG, "Manejando EncryptionType 01: Cifrado bajo KTK pre-cargada")
                    val ktkFromDb = injectedKeyRepository.getKeyBySlotAndType(command.ktkSlot, GenericKeyType.TRANSPORT_KEY.name) ?: injectedKeyRepository.getKeyBySlotAndType(command.ktkSlot, GenericKeyType.MASTER_KEY.name)
                    if (ktkFromDb == null) throw PedKeyException("KTK pre-cargada en slot ${command.ktkSlot} no encontrada.")
                    if (!ktkFromDb.kcv.take(4).equals(command.ktkChecksum.take(4), ignoreCase = true)) throw PedKeyException("El KCV de la KTK en BD no coincide.")

                    //val tr31Block = parseTr31Block(command.keyHex)
                    //val encryptedKey = unwrapTr31Payload(tr31Block.encryptedPayload)
                    val encryptedKey = command.keyHex.hexToByteArray()


                    pedController!!.writeDukptInitialKeyEncrypted(command.keySlot, genericAlgorithm, encryptedKey, command.ksn.hexToByteArray(), command.ktkSlot, command.keyChecksum)
                }
                "02" -> {
                    Log.d(TAG, "Manejando EncryptionType 02: Cifrado con KTK en claro")
                    if (command.ktkHex == null) throw PedKeyException("Falta la KTK en claro para el tipo de cifrado 02.")

                    Log.d(TAG, "Paso 1/2: Inyectando KTK en claro en slot ${command.ktkSlot}")
                    pedController!!.writeKeyPlain(command.ktkSlot, GenericKeyType.TRANSPORT_KEY, genericAlgorithm, command.ktkHex!!.hexToByteArray(), command.ktkChecksum.hexToByteArray())

                    Log.d(TAG, "Paso 2/2: Parseando TR-31 para inyectar la llave final")
                    val tr31Block = parseTr31Block(command.keyHex)
                    val encryptedKey = unwrapTr31Payload(tr31Block.encryptedPayload)

                    pedController!!.writeDukptInitialKeyEncrypted(command.keySlot, genericAlgorithm, encryptedKey, command.ksn.hexToByteArray(), command.ktkSlot, command.keyChecksum)
                }
                else -> throw PedKeyException("Tipo de encriptación '${command.encryptionType}' no soportado.")
            }
            "Inyección en slot ${command.keySlot} procesada exitosamente."
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
            connectionMutex.withLock { stopListeningInternal() }
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
}