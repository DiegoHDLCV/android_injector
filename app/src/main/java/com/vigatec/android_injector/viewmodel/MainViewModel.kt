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
    private val application: Application // Guardamos application para usarlo después
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
        // El bloque init ahora es ligero y seguro. No intenta obtener los controllers.
        setupProtocolHandlers()
        Log.i(TAG, "MainViewModel creado. Los controllers se obtendrán 'Just-In-Time'.")
    }

    /**
     * Obtiene una instancia del comController de forma segura.
     * Retorna true si el controlador está listo, false si no.
     */
    private fun ensureComControllerIsReady(): Boolean {
        if (comController == null) {
            Log.d(TAG, "comController es nulo, intentando obtenerlo de CommunicationSDKManager...")
            comController = CommunicationSDKManager.getComController()
        }
        if (comController == null) {
            handleError("El controlador de comunicación no está disponible. ¿Se inicializó el SDK?")
            return false
        }
        return true
    }

    /**
     * Obtiene una instancia del pedController de forma segura.
     * Retorna true si el controlador está listo, false si no.
     */
    private fun ensurePedControllerIsReady(): Boolean {
        if (pedController == null) {
            Log.d(TAG, "pedController es nulo, intentando obtenerlo de KeySDKManager...")
            pedController = KeySDKManager.getPedController()
        }
        if (pedController == null) {
            handleError("El controlador PED no está disponible. ¿Se inicializó el SDK?")
            return false
        }
        return true
    }

    /**
     * Inicializa el parser y formatter correctos según la configuración del sistema.
     */
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

    /**
     * Cambia el protocolo de comunicación y reinicia la conexión de forma segura y sincronizada.
     */
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

    /**
     * Inicia el ciclo de vida de la conexión de forma segura. Público para ser llamado desde la UI.
     */
    fun startListening(
        baudRate: EnumCommConfBaudRate = EnumCommConfBaudRate.BPS_9600,
        parity: EnumCommConfParity = EnumCommConfParity.NOPAR,
        dataBits: EnumCommConfDataBits = EnumCommConfDataBits.DB_8
    ) = viewModelScope.launch {
        connectionMutex.withLock {
            // Se añade la guarda para obtener el controlador justo antes de usarlo
            if (!ensureComControllerIsReady()) {
                return@withLock
            }
            startListeningInternal(baudRate, parity, dataBits)
        }
    }

    /**
     * Detiene el ciclo de vida de la conexión de forma segura. Público para ser llamado desde la UI.
     */
    fun stopListening() = viewModelScope.launch {
        connectionMutex.withLock {
            stopListeningInternal()
        }
    }

    /**
     * Lógica interna para iniciar la escucha. Debe ser llamado dentro de un Mutex para evitar concurrencia.
     * Ahora asume que `comController` no es nulo.
     */
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
                Log.i(TAG, "Iniciando conexión...")
                _snackbarEvent.emit("Iniciando conexión...")
                val initResult = comController!!.init(baudRate, parity, dataBits)
                if (initResult != 0) throw Exception("Fallo al inicializar ComController (código: $initResult)")

                _connectionStatus.value = ConnectionStatus.OPENING
                val openResult = comController!!.open()
                if (openResult != 0) throw Exception("Fallo al abrir puerto (código: $openResult)")

                _connectionStatus.value = ConnectionStatus.LISTENING
                Log.i(TAG, "¡Conexión establecida! Escuchando en protocolo ${SystemConfig.commProtocolSelected}.")
                _snackbarEvent.emit("Conexión establecida.")

                val buffer = ByteArray(1024)
                while (isActive) {
                    val bytesRead = comController!!.readData(buffer.size, buffer, 5000)
                    if (bytesRead > 0) {
                        val received = buffer.copyOf(bytesRead)
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
                Log.i(TAG, "Bucle de escucha finalizado. Limpiando recursos...")
                comController?.close()
                if (_connectionStatus.value != ConnectionStatus.ERROR) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
            }
        }
    }

    /**
     * Lógica interna para detener la escucha. Asegura que la corutina termine completamente.
     */
    private suspend fun stopListeningInternal() {
        if (listeningJob?.isActive != true) {
            Log.d(TAG, "stopListeningInternal llamado pero no hay escucha activa.")
            comController?.close()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            return
        }

        Log.i(TAG, "Deteniendo conexión...")
        _connectionStatus.value = ConnectionStatus.CLOSING
        _snackbarEvent.emit("Cerrando conexión...")

        listeningJob?.cancel()
        listeningJob?.join()

        listeningJob = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        Log.i(TAG, "Conexión detenida y recursos liberados.")
        _snackbarEvent.emit("Conexión cerrada.")
    }

    fun sendData(data: ByteArray) {
        if (!ensureComControllerIsReady() || _connectionStatus.value != ConnectionStatus.LISTENING) {
            Log.e(TAG, "No se puede enviar: Puerto no listo o controlador nulo.")
            viewModelScope.launch { _snackbarEvent.emit("Error: Puerto no listo para enviar") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Enviando ${data.size} bytes: ${data.toHexString()}")
                val bytesWritten = comController!!.write(data, 1000)
                if (bytesWritten < 0) {
                    Log.e(TAG,"Error al enviar datos: $bytesWritten")
                    _snackbarEvent.emit("Error al enviar datos: $bytesWritten")
                } else {
                    Log.i(TAG, "Se enviaron $bytesWritten bytes correctamente.")
                }
            } catch (e: Exception) {
                handleError("Excepción al enviar datos", e)
            }
        }
    }

    private fun processParsedCommand(message: ParsedMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Procesando comando tipado: $message")
            _snackbarEvent.emit("Recibido CMD: ${message::class.simpleName}")

            when (message) {
                is LegacyMessage -> {
                    Log.d(TAG, "Procesando como comando Legacy...")
                }
                is ReadSerialCommand -> handleFuturexReadSerial(message)
                is WriteSerialCommand -> handleFuturexWriteSerial(message)
                is InjectSymmetricKeyCommand -> handleFuturexInjectKey(message)

                is UnknownCommand -> {
                    Log.w(TAG, "Comando Futurex desconocido recibido: ${message.commandCode}")
                    val errorResponse = messageFormatter.format(message.commandCode, "01")
                    sendData(errorResponse)
                }
                is ParseError -> {
                    Log.e(TAG, "Error de parseo para comando Futurex: ${message.error}")
                }
            }
        }
    }

    private suspend fun handleFuturexInjectKey(command: InjectSymmetricKeyCommand) {
        Log.d(TAG, "Manejando InjectSymmetricKeyCommand para KeyType ${command.keyType}")

        if (!ensurePedControllerIsReady()) {
            val errorResponse = messageFormatter.format("02", "05") // Device is busy
            sendData(errorResponse)
            return
        }

        var responseCode = "00"
        var responseKeyChecksum = "0000"
        var logMessage: String

        try {
            val genericKeyType = mapFuturexKeyTypeToGeneric(command.keyType)
            Log.d(TAG, "Tipo de llave mapeado a PED: $genericKeyType")
            val genericAlgorithm = KeyAlgorithm.DES_TRIPLE

            Log.d(TAG, "Extrayendo bytes de clave desde keyHex.")
            val keyDataBytes = command.keyHex.hexToByteArray()
            Log.d(TAG, "Bytes de datos de clave extraídos (${keyDataBytes.size} bytes).")


            // Paso 2: Ahora que tenemos los bytes correctos, decidimos cómo inyectarlos
            // basándonos en si es una clave DUKPT o una clave simétrica.
            if (genericKeyType == GenericKeyType.DUKPT_INITIAL_KEY) {
                // --- LÓGICA REFINADA Y FINAL PARA DUKPT IPEK ---
                Log.i(TAG, "Iniciando proceso para clave DUKPT (IPEK)...")
                val ksnBytes = command.ksn.hexToByteArray()

                // Si la IPEK viene cifrada (tipos 01 o 02), el PED debe usar la KTK para descifrarla.
                // Si viene en claro (tipo 00), se inyecta directamente.
                if (command.encryptionType == "00") {
                    Log.d(TAG, "IPEK en claro. Llamando a writeDukptInitialKey.")
                    pedController!!.writeDukptInitialKey(
                        groupIndex = command.keySlot,
                        keyAlgorithm = genericAlgorithm,
                        keyBytes = keyDataBytes,
                        initialKsn = ksnBytes,
                        keyChecksum = command.keyChecksum
                    )
                } else { // encryptionType es "01" o "02"
                    Log.d(TAG, "IPEK cifrada. Llamando a writeDukptInitialKeyEncrypted.")

                    // Si es tipo "02", primero cargamos la KTK que viene en el mensaje.
                    if (command.encryptionType == "02") {
                        if (command.ktkHex == null) throw PedKeyException("Falta la KTK en claro para el tipo de cifrado 02.", PedKeyException("0E"))

                        Log.d(TAG, "Cargando KTK en claro en la ranura ${command.ktkSlot} para ser usada en la inyección de IPEK.")
                        pedController!!.writeKeyPlain(
                            keyIndex = command.ktkSlot,
                            keyType = GenericKeyType.TRANSPORT_KEY,
                            keyAlgorithm = genericAlgorithm,
                            keyBytes = command.ktkHex!!.hexToByteArray(),
                            kcvBytes = null
                        )
                    }

                    // Ahora llamamos a la función que le indica al PED que use la KTK para descifrar la IPEK.
                    // Esta es una nueva función que debes añadir a tu interfaz IPedController y su implementación en AisinoPedController.
                    pedController!!.writeDukptInitialKeyEncrypted(
                        groupIndex = command.keySlot,
                        keyAlgorithm = genericAlgorithm,
                        encryptedIpek = keyDataBytes,
                        initialKsn = ksnBytes,
                        transportKeyIndex = command.ktkSlot,
                        keyChecksum = command.keyChecksum
                    )
                }
            } else {
                // --- LÓGICA PARA CLAVES SIMÉTRICAS (Funciona con keyDataBytes) ---
                Log.i(TAG, "Inyectando llave simétrica...")
                when (command.encryptionType) {
                    "00" -> {
                        Log.i(TAG, "Inyectando llave simétrica en claro (Tipo 00)")
                        pedController!!.writeKeyPlain(
                            keyIndex = command.keySlot,
                            keyType = genericKeyType,
                            keyAlgorithm = genericAlgorithm,
                            keyBytes = keyDataBytes,
                            kcvBytes = null
                        )
                    }
                    "01" -> {
                        Log.i(TAG, "Inyectando llave simétrica con KTK pre-cargada (Tipo 01)")
                        pedController!!.writeKey(
                            keyIndex = command.keySlot,
                            keyType = genericKeyType,
                            keyAlgorithm = genericAlgorithm,
                            keyData = PedKeyData(keyBytes = keyDataBytes),
                            transportKeyIndex = command.ktkSlot,
                            transportKeyType = GenericKeyType.TRANSPORT_KEY
                        )
                    }
                    "02" -> {
                        Log.i(TAG, "Inyectando llave simétrica y KTK en claro (Tipo 02)")
                        if (command.ktkHex == null) {
                            throw PedKeyException("Falta la KTK en claro para el tipo de cifrado 02.", PedKeyException("0E"))
                        }
                        Log.d(TAG, "SIMÉTRICO: Paso 1: Inyectando KTK en claro en la ranura ${command.ktkSlot}")
                        pedController!!.writeKeyPlain(
                            keyIndex = command.ktkSlot,
                            keyType = GenericKeyType.TRANSPORT_KEY,
                            keyAlgorithm = genericAlgorithm,
                            keyBytes = command.ktkHex!!.hexToByteArray(),
                            kcvBytes = null
                        )
                        Log.d(TAG, "SIMÉTRICO: Paso 2: Inyectando llave cifrada en ranura ${command.keySlot} usando KTK de ranura ${command.ktkSlot}")
                        pedController!!.writeKey(
                            keyIndex = command.keySlot,
                            keyType = genericKeyType,
                            keyAlgorithm = genericAlgorithm,
                            keyData = PedKeyData(keyBytes = keyDataBytes),
                            transportKeyIndex = command.ktkSlot,
                            transportKeyType = GenericKeyType.TRANSPORT_KEY
                        )
                    }
                    else -> throw PedKeyException("Tipo de encriptación '${command.encryptionType}' no soportado.", PedKeyException("11"))
                }
            }

            logMessage = "Inyección de clave en slot ${command.keySlot} procesada exitosamente."

        } catch (e: Exception) {
            logMessage = e.message ?: "Error inesperado."
            responseCode = (e as? PedKeyException)?.cause?.message ?: "10"
            Log.e(TAG, "Error de PED procesando CMD '02': $logMessage (Código: $responseCode)", e)
        }

        // Finalmente, envía la respuesta
        val response = messageFormatter.format("02", listOf(responseCode, responseKeyChecksum))
        sendData(response)
        viewModelScope.launch { _snackbarEvent.emit(logMessage) }
    }
    private fun handleFuturexReadSerial(command: ReadSerialCommand) {
        Log.d(TAG, "Manejando ReadSerialCommand (v${command.version})")
        if (command.version != "01") {
            Log.w(TAG, "Versión de comando '03' no soportada: ${command.version}.")
            sendData(messageFormatter.format("03", listOf("02")))
            return
        }

        try {
            val serialNumber = "VGT1234567890SNX" // Valor de prueba
            val response = messageFormatter.format("03", listOf("00", serialNumber))
            sendData(response)
            viewModelScope.launch { _snackbarEvent.emit("Respuesta a CMD '03' enviada.") }
        } catch (e: Exception) {
            handleError("Error al responder a CMD '03'", e)
            sendData(messageFormatter.format("03", listOf("05")))
        }
    }

    private fun handleFuturexWriteSerial(command: WriteSerialCommand) {
        Log.d(TAG, "Manejando WriteSerialCommand (v${command.version}) para S/N: ${command.serialNumber}")
        var responseCode = "00"
        try {
            if (command.version != "01") throw IllegalArgumentException("Versión de comando '04' no soportada.")
            Log.i(TAG, "SIMULACIÓN: Escribiendo serial '${command.serialNumber}' en el dispositivo.")
        } catch (e: Exception) {
            handleError("Error al procesar CMD '04'", e)
            responseCode = "0C"
        } finally {
            val response = messageFormatter.format("04", listOf(responseCode))
            sendData(response)
        }
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
            else -> throw PedKeyException("Tipo de llave Futurex no soportado: $futurexKeyType", PedKeyException("10"))
        }
    }

    /**
     * Parsea una cadena que representa un bloque de claves TR-31.
     * Extrae la cabecera, bloques opcionales, la carga útil cifrada y el MAC.
     */
    private fun parseTr31Block(tr31String: String): Tr31KeyBlock {
        // Clase interna para facilitar la lectura secuencial de la cadena
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

        // 1. Parsear la cabecera fija de 16 bytes
        val versionId = reader.read(1).first()
        val blockLength = reader.read(4).toInt() // Longitud es decimal
        val keyUsage = reader.read(2)
        val algorithm = reader.read(1).first()
        val modeOfUse = reader.read(1).first()
        val keyVersionNumber = reader.read(2)
        val exportability = reader.read(1).first()
        val numberOfOptionalBlocks = reader.read(2).toInt() // Número es decimal
        val keyContext = reader.read(1).first()
        reader.read(1) // Omitir campo reservado

        // 2. Parsear bloques opcionales
        val optionalBlocks = mutableListOf<Tr31OptionalBlock>()
        repeat(numberOfOptionalBlocks) {
            val blockId = reader.read(2)
            val blockLength = reader.read(2).toInt() // Longitud es decimal
            val blockData = reader.read(blockLength)
            optionalBlocks.add(Tr31OptionalBlock(blockId, blockData))
        }

        // 3. Extraer el resto: payload cifrado y MAC (estos son hexadecimales)
        val remainingDataString = reader.remaining()
        val remainingBytes = remainingDataString.hexToByteArray()

        // Asumimos un tamaño de MAC. Para TDES (Versión 'B'), suele ser de 8 bytes.
        // Una implementación más robusta podría determinar esto basado en el algoritmo.
        val macSize = 8
        if (remainingBytes.size < macSize) {
            throw IllegalArgumentException("Los datos restantes del bloque TR-31 son insuficientes para contener un MAC.")
        }

        val mac = remainingBytes.takeLast(macSize).toByteArray()
        val encryptedPayload = remainingBytes.dropLast(macSize).toByteArray()

        return Tr31KeyBlock(
            rawBlock = tr31String,
            versionId = versionId,
            blockLength = blockLength,
            keyUsage = keyUsage,
            algorithm = algorithm,
            modeOfUse = modeOfUse,
            keyVersionNumber = keyVersionNumber,
            exportability = exportability,
            optionalBlocks = optionalBlocks,
            encryptedPayload = encryptedPayload,
            mac = mac
        )
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