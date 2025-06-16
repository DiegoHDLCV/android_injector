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

    // --- Flows para UI y Eventos ---
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()
    private val _rawReceivedData = MutableStateFlow("")
    val rawReceivedData = _rawReceivedData.asStateFlow()

    // --- Controladores y Conexión ---
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
        when (SystemConfig.commProtocolSelected) {
            CommProtocol.LEGACY -> {
                messageParser = LegacyMessageParser()
                messageFormatter = LegacyMessageFormatter
                Log.i(TAG, "Protocolo de comunicación establecido en: LEGACY")
            }
            CommProtocol.FUTUREX -> {
                messageParser = FuturexMessageParser()
                messageFormatter = FuturexMessageFormatter
                Log.i(TAG, "Protocolo de comunicación establecido en: FUTUREX")
            }
        }
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

    private fun handleError(message: String, e: Exception? = null) {
        Log.e(TAG, message, e)
        _connectionStatus.value = ConnectionStatus.ERROR
        viewModelScope.launch { _snackbarEvent.emit("Error: $message") }
    }

    fun startListening(
        baudRate: EnumCommConfBaudRate = EnumCommConfBaudRate.BPS_9600,
        parity: EnumCommConfParity = EnumCommConfParity.NOPAR,
        dataBits: EnumCommConfDataBits = EnumCommConfDataBits.DB_8
    ) = viewModelScope.launch {
        connectionMutex.withLock {
            if (!ensureComControllerIsReady()) {
                return@withLock
            }
            startListeningInternal(baudRate, parity, dataBits)
        }
    }

    fun stopListening() = viewModelScope.launch {
        connectionMutex.withLock {
            stopListeningInternal()
        }
    }

    private fun startListeningInternal(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ) {
        if (listeningJob?.isActive == true) {
            Log.w(TAG, "startListeningInternal llamado pero la escucha ya está activa.")
            return
        }

        listeningJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _connectionStatus.value = ConnectionStatus.INITIALIZING
                comController!!.init(baudRate, parity, dataBits)
                comController!!.open()
                _connectionStatus.value = ConnectionStatus.LISTENING
                Log.i(TAG, "¡Conexión establecida! Escuchando en protocolo ${SystemConfig.commProtocolSelected}.")
                _snackbarEvent.emit("Conexión establecida.")

                val buffer = ByteArray(1024)
                while (isActive) {
                    val bytesRead = comController!!.readData(buffer.size, buffer, 5000)
                    if (bytesRead > 0) {
                        val received = buffer.copyOf(bytesRead)
                        _rawReceivedData.value += String(received, Charsets.US_ASCII)
                        Log.v(TAG, "RAW_SERIAL_IN (HEX): ${received.toHexString()}")
                        messageParser.appendData(received)
                        var parsedMessage: ParsedMessage?
                        do {
                            parsedMessage = messageParser.nextMessage()
                            parsedMessage?.let { processParsedCommand(it) }
                        } while (parsedMessage != null && isActive)
                    } else if (bytesRead < 0 && bytesRead != -6) {
                        throw Exception("Error crítico de lectura de puerto (código: $bytesRead)")
                    }
                }
            } catch (e: Exception) {
                if (isActive) handleError("Error de conexión: ${e.message}", e)
            } finally {
                Log.i(TAG, "Bucle de escucha finalizado.")
                comController?.close()
                if (_connectionStatus.value != ConnectionStatus.ERROR) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
            }
        }
    }

    private suspend fun stopListeningInternal() {
        if (listeningJob?.isActive != true) {
            return
        }
        _connectionStatus.value = ConnectionStatus.CLOSING
        listeningJob?.cancel()
        listeningJob?.join()
        listeningJob = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        Log.i(TAG, "Conexión detenida.")
        _snackbarEvent.emit("Conexión cerrada.")
    }

    fun sendData(data: ByteArray) {
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

    private fun processParsedCommand(message: ParsedMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Procesando mensaje parseado: $message")

            when (message) {
                is InjectSymmetricKeyCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Inyectar Llave")
                    handleFuturexInjectKey(message)
                }
                is ReadSerialCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Leer Serial")
                    handleFuturexReadSerial(message)
                }
                is WriteSerialCommand -> {
                    _snackbarEvent.emit("Recibido CMD: Escribir Serial")
                    handleFuturexWriteSerial(message)
                }
                is InjectSymmetricKeyResponse -> {
                    _snackbarEvent.emit("Recibida RESPUESTA a Inyección")
                    handleInjectKeyResponse(message)
                }
                is UnknownCommand -> {
                    Log.w(TAG, "Comando Futurex desconocido recibido: ${message.commandCode}")
                    val errorResponse = messageFormatter.format(message.commandCode, FuturexErrorCode.INVALID_COMMAND.code)
                    sendData(errorResponse)
                }
                is ParseError -> Log.e(TAG, "Error de parseo para Futurex: ${message.error}")
                else -> Log.d(TAG, "Mensaje parseado no requiere acción: ${message::class.simpleName}")
            }
        }
    }

    private fun handleInjectKeyResponse(response: InjectSymmetricKeyResponse) {
        val errorCode = FuturexErrorCode.fromCode(response.responseCode)
        val responseMessage = if (errorCode == FuturexErrorCode.SUCCESSFUL) {
            "Dispositivo confirmó inyección exitosa con KCV: ${response.keyChecksum}"
        } else {
            "Dispositivo reportó error: ${errorCode?.description ?: "Código desconocido"} (${response.responseCode})"
        }

        Log.i(TAG, "Respuesta de inyección manejada: $responseMessage")
        viewModelScope.launch {
            _snackbarEvent.emit(responseMessage)
        }
    }

    // --- FUNCIÓN DE INYECCIÓN TOTALMENTE MODIFICADA ---
    private suspend fun handleFuturexInjectKey(command: InjectSymmetricKeyCommand) {
        // Asegurarse de que el controlador PED esté listo antes de continuar.
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
            Log.i(TAG, "handleFuturexInjectKey: Iniciando proceso de inyección para slot ${command.keySlot}")

            // --- INICIO DE LA NUEVA LÓGICA DE VERIFICACIÓN ---

            // PASO 1: VERIFICAR EL ESTADO REAL DEL HARDWARE
            Log.d(TAG, "Verificando hardware: ¿El slot ${command.keySlot} para tipo $genericKeyType está ocupado?")
            val slotOccupied = pedController!!.isKeyPresent(command.keySlot, genericKeyType)
            Log.d(TAG, "Respuesta del hardware: slotOccupied = $slotOccupied")

            // PASO 2: VERIFICAR LA BASE DE DATOS LOCAL
            Log.d(TAG, "Verificando BD local para slot ${command.keySlot} y tipo ${genericKeyType.name}")
            val keyInDb = injectedKeyRepository.getKeyBySlotAndType(command.keySlot, genericKeyType.name)
            Log.d(TAG, "Respuesta de la BD: keyInDb = ${keyInDb != null}")

            // PASO 3: LÓGICA DE DECISIÓN
            if (slotOccupied) {
                // El hardware dice que el slot está OCUPADO.
                Log.w(TAG, "Conflicto Potencial: El hardware reporta el slot ${command.keySlot} como OCUPADO.")
                if (keyInDb == null) {
                    // Escenario de "Llave Fantasma": Existe en el hardware pero no en nuestra BD.
                    // Esta es una condición de error grave que debe ser manejada.
                    throw PedKeyException("Conflicto Crítico: El slot ${command.keySlot} está ocupado en el dispositivo, pero no registrado localmente. Se requiere intervención manual.")
                } else {
                    // El slot está ocupado y tenemos un registro. ¿Son la misma llave?
                    // Comparamos usando el Key Checksum Value (KCV).
                    if (keyInDb.kcv.take(4).equals(command.keyChecksum.take(4), ignoreCase = true)) {
                        // Es la misma llave. La operación es idempotente. No hacemos nada.
                        Log.i(TAG, "La misma llave (KCV: ${command.keyChecksum}) ya existe en el slot ${command.keySlot}. Inyección omitida.")
                        return@runCatching "La misma llave ya existe. Inyección omitida."
                    } else {
                        // Se intenta inyectar una LLAVE DIFERENTE en un slot ocupado.
                        throw PedKeyException("Conflicto de Llave: El slot ${command.keySlot} ya contiene una llave diferente (KCV no coincide).")
                    }
                }
            } else {
                // El hardware dice que el slot está LIBRE.
                Log.i(TAG, "Verificación de hardware OK: El slot ${command.keySlot} está libre.")
                if (keyInDb != null) {
                    // La BD está desactualizada. Tenía un registro para un slot que ahora está vacío.
                    Log.w(TAG, "Inconsistencia de datos detectada: Se encontró un registro obsoleto para el slot ${command.keySlot}. Se eliminará.")
                    injectedKeyRepository.deleteKey(keyInDb)
                }

                // --- FIN DE LA NUEVA LÓGICA DE VERIFICACIÓN ---

                // El slot está libre, proceder con la inyección como antes.
                Log.d(TAG, "Procediendo con la inyección en el slot ${command.keySlot}...")
                // Validar KTK si la encriptación lo requiere
                if (command.encryptionType == "01" || command.encryptionType == "02") {
                    val ktkFromDb = injectedKeyRepository.getKeyBySlotAndType(command.ktkSlot, GenericKeyType.TRANSPORT_KEY.name)
                    if (ktkFromDb == null) {
                        if (command.encryptionType == "01") throw PedKeyException("KTK pre-cargada en slot ${command.ktkSlot} no encontrada.")
                    } else {
                        if (!ktkFromDb.kcv.take(4).equals(command.ktkChecksum.take(4), ignoreCase = true)) {
                            throw PedKeyException("El KCV de la KTK en la BD no coincide con el de la solicitud.")
                        }
                    }
                }

                val keyDataBytes = command.keyHex.hexToByteArray()
                if (genericKeyType == GenericKeyType.DUKPT_INITIAL_KEY) {
                    val ksnBytes = command.ksn.hexToByteArray()
                    if (command.encryptionType == "00") {
                        pedController!!.writeDukptInitialKey(command.keySlot, genericAlgorithm, keyDataBytes, ksnBytes, command.keyChecksum)
                    } else {
                        if (command.encryptionType == "02") {
                            if (command.ktkHex == null) throw PedKeyException("Falta la KTK en claro para el tipo de cifrado 02.")
                            pedController!!.writeKeyPlain(command.ktkSlot, GenericKeyType.TRANSPORT_KEY, genericAlgorithm, command.ktkHex!!.hexToByteArray(), null)
                        }
                        pedController!!.writeDukptInitialKeyEncrypted(command.keySlot, genericAlgorithm, keyDataBytes, ksnBytes, command.ktkSlot, command.keyChecksum)
                    }
                } else {
                    when (command.encryptionType) {
                        "00" -> pedController!!.writeKeyPlain(command.keySlot, genericKeyType, genericAlgorithm, keyDataBytes, null)
                        "01" -> pedController!!.writeKey(command.keySlot, genericKeyType, genericAlgorithm, PedKeyData(keyDataBytes), command.ktkSlot, GenericKeyType.TRANSPORT_KEY)
                        "02" -> {
                            if (command.ktkHex == null) throw PedKeyException("Falta la KTK en claro para el tipo de cifrado 02.")
                            pedController!!.writeKeyPlain(command.ktkSlot, GenericKeyType.TRANSPORT_KEY, genericAlgorithm, command.ktkHex!!.hexToByteArray(), null)
                            pedController!!.writeKey(command.keySlot, genericKeyType, genericAlgorithm, PedKeyData(keyDataBytes), command.ktkSlot, GenericKeyType.TRANSPORT_KEY)
                        }
                        else -> throw PedKeyException("Tipo de encriptación '${command.encryptionType}' no soportado.")
                    }
                }
                "Inyección de clave en slot ${command.keySlot} procesada exitosamente."
            }
        }

        injectionResult.onSuccess { resultMessage ->
            logMessage = resultMessage
            injectionStatus = if (resultMessage.contains("omitida")) "SKIPPED" else "SUCCESSFUL"
            responseCode = FuturexErrorCode.SUCCESSFUL.code
        }.onFailure { e ->
            logMessage = e.message ?: "Error inesperado."
            responseCode = (e as? PedKeyException)?.cause?.message ?: FuturexErrorCode.INVALID_KEY_TYPE.code
            injectionStatus = "FAILED"
            Log.e(TAG, "Error procesando inyección: $logMessage (Código: $responseCode)", e)
        }

        // Solo registrar en la BD si la inyección NO se omitió
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

        val response = messageFormatter.format("02", listOf(responseCode, "0000"))
        sendData(response)
        viewModelScope.launch { _snackbarEvent.emit(logMessage) }
    }

    private fun handleFuturexReadSerial(command: ReadSerialCommand) {
        Log.d(TAG, "Manejando ReadSerialCommand (v${command.version})")
        // Lógica de implementación
    }

    private fun handleFuturexWriteSerial(command: WriteSerialCommand) {
        Log.d(TAG, "Manejando WriteSerialCommand (v${command.version})")
        // Lógica de implementación
    }

    private fun mapFuturexKeyTypeToGeneric(futurexKeyType: String): GenericKeyType {
        return when (futurexKeyType) {
            "01" -> GenericKeyType.MASTER_KEY
            "05" -> GenericKeyType.WORKING_PIN_KEY
            "04" -> GenericKeyType.WORKING_MAC_KEY
            "0C" -> GenericKeyType.WORKING_DATA_ENCRYPTION_KEY
            "06" -> GenericKeyType.TRANSPORT_KEY
            "0F" -> GenericKeyType.MASTER_KEY
            "03", "08" -> GenericKeyType.DUKPT_INITIAL_KEY
            else -> throw PedKeyException("Tipo de llave Futurex no soportado: $futurexKeyType")
        }
    }

    private fun parseTr31Block(tr31String: String): Tr31KeyBlock {
        // Lógica para parsear TR-31
        class Tr31Reader(private val payload: String) {
            private var cursor = 0
            fun read(length: Int): String {
                if (cursor + length > payload.length) throw IndexOutOfBoundsException("Fin de payload inesperado en TR-31.")
                val field = payload.substring(cursor, cursor + length)
                cursor += length
                return field
            }
            fun remaining(): String = payload.substring(cursor)
        }
        val reader = Tr31Reader(tr31String)
        val versionId = reader.read(1).first()
        val blockLength = reader.read(4).toInt()
        val keyUsage = reader.read(2)
        val algorithm = reader.read(1).first()
        val modeOfUse = reader.read(1).first()
        val keyVersionNumber = reader.read(2)
        val exportability = reader.read(1).first()
        val numberOfOptionalBlocks = reader.read(2).toInt()
        val keyContext = reader.read(1).first()
        reader.read(1)
        val optionalBlocks = mutableListOf<Tr31OptionalBlock>()
        repeat(numberOfOptionalBlocks) {
            val blockId = reader.read(2)
            val blockLength = reader.read(2).toInt()
            val blockData = reader.read(blockLength)
            optionalBlocks.add(Tr31OptionalBlock(blockId, blockData))
        }
        val remainingDataString = reader.remaining()
        val remainingBytes = remainingDataString.hexToByteArray()
        val macSize = 8
        if (remainingBytes.size < macSize) throw IllegalArgumentException("Los datos restantes del bloque TR-31 son insuficientes para contener un MAC.")
        val mac = remainingBytes.takeLast(macSize).toByteArray()
        val encryptedPayload = remainingBytes.dropLast(macSize).toByteArray()
        return Tr31KeyBlock(rawBlock=tr31String, versionId=versionId, blockLength=blockLength, keyUsage=keyUsage, algorithm=algorithm, modeOfUse=modeOfUse, keyVersionNumber=keyVersionNumber, exportability=exportability, optionalBlocks=optionalBlocks, encryptedPayload=encryptedPayload, mac=mac)
    }

    override fun onCleared() {
        Log.i(TAG, "ViewModel onCleared: Deteniendo escucha y liberando...")
        viewModelScope.launch {
            connectionMutex.withLock {
                stopListeningInternal()
            }
            pedController?.releasePed()
        }
        super.onCleared()
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "La cadena HEX debe tener una longitud par." }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }
}