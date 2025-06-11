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
import com.vigatec.android_injector.ui.events.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // La variable _rawReceivedData es privada para que solo el ViewModel pueda modificarla.
    private val _rawReceivedData = MutableStateFlow("")
    // Se añade la variable pública (sin el guion bajo) para que la UI pueda observarla.
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

        comController = CommunicationSDKManager.getComController()
        if (comController == null) {
            handleError("Error al obtener controlador de comunicación")
        } else {
            Log.i(TAG, "IComController obtenido exitosamente.")
        }

        pedController = KeySDKManager.getPedController()
        if (pedController == null) {
            handleError("Error al obtener controlador PED")
        } else {
            Log.i(TAG, "IPedController obtenido exitosamente.")
            viewModelScope.launch {
                try {
                    pedController?.initializePed(application)
                    Log.i(TAG, "IPedController inicializado (o intento realizado).")
                } catch (e: Exception) {
                    handleError("Error al inicializar PED: ${e.message}")
                }
            }
        }
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

            // Detener la conexión actual y esperar a que termine completamente.
            stopListeningInternal()

            // Actualizar la configuración y los handlers del protocolo.
            SystemConfig.commProtocolSelected = protocol
            setupProtocolHandlers()

            _snackbarEvent.emit("Protocolo cambiado a $protocol.")
            // No reinicia la conexión automáticamente. El usuario debe iniciarla de nuevo.
            // Esto da un control más predecible. Si se quisiera reiniciar, aquí se llamaría a startListeningInternal().
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
                val initResult = comController?.init(baudRate, parity, dataBits)
                if (initResult != 0) throw Exception("Fallo al inicializar ComController (código: $initResult)")

                _connectionStatus.value = ConnectionStatus.OPENING
                val openResult = comController?.open()
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

                        val receivedAsText = String(received, Charset.defaultCharset())
                        _rawReceivedData.value += receivedAsText

                        messageParser.appendData(received)

                        // --- CORRECCIÓN AQUÍ ---
                        // La variable ahora es del tipo de la interfaz base 'ParsedMessage'.
                        var parsedMessage: ParsedMessage?
                        do {
                            parsedMessage = messageParser.nextMessage()
                            // Ahora 'it' es de tipo ParsedMessage, que es lo que 'processParsedCommand' espera.
                            parsedMessage?.let { processParsedCommand(it) }
                        } while (parsedMessage != null && isActive)
                        // --- FIN DE LA CORRECCIÓN ---

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
            comController?.close() // Intenta cerrar por si acaso quedó en un estado inconsistente
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            return
        }

        Log.i(TAG, "Deteniendo conexión...")
        _connectionStatus.value = ConnectionStatus.CLOSING
        _snackbarEvent.emit("Cerrando conexión...")

        listeningJob?.cancel() // Envía la señal de cancelación
        listeningJob?.join()   // Espera a que la corutina (incluyendo su bloque finally) termine

        listeningJob = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        Log.i(TAG, "Conexión detenida y recursos liberados.")
        _snackbarEvent.emit("Conexión cerrada.")
    }

    fun sendData(data: ByteArray) {
        if (comController == null || _connectionStatus.value != ConnectionStatus.LISTENING) {
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
            // Logueamos el objeto completo. Gracias a 'data class', el toString() es muy legible.
            Log.i(TAG, "Procesando comando tipado: $message")
            _snackbarEvent.emit("Recibido CMD: ${message::class.simpleName}")

            // El 'when' ahora es sobre el TIPO de objeto, no sobre un string.
            // Es más seguro y el compilador nos avisará si falta algún caso.
            when (message) {
                is LegacyMessage -> {
                    Log.d(TAG, "Procesando como comando Legacy...")
                    // Aquí iría tu lógica para comandos Legacy
                }

                // --- MANEJO DE COMANDOS FUTUREX ---
                is ReadSerialCommand -> handleFuturexReadSerial(message)
                is WriteSerialCommand -> handleFuturexWriteSerial(message)
                is InjectSymmetricKeyCommand -> handleFuturexInjectKey(message)

                is UnknownCommand -> {
                    Log.w(TAG, "Comando Futurex desconocido recibido: ${message.commandCode}")
                    val errorResponse = messageFormatter.format(message.commandCode, "01") // Invalid Command
                    sendData(errorResponse)
                }
                is ParseError -> {
                    Log.e(TAG, "Error de parseo para comando Futurex: ${message.error}")
                    // Aquí podrías decidir enviar una respuesta de error genérico si es necesario
                }
            }
        }
    }

    private suspend fun handleFuturexInjectKey(command: InjectSymmetricKeyCommand) {
        Log.d(TAG, "Manejando InjectSymmetricKeyCommand en slot ${command.keySlot}")

        var responseCode = "00" // Éxito por defecto
        var responseKeyChecksum = "0000" // Checksum de la llave inyectada para la respuesta
        var logMessage: String

        try {
            // Paso 1: Validar el tipo de encriptación. Por ahora, solo soportamos "00" (en claro).
            if (command.encryptionType != "00") {
                throw PedKeyException("Tipo de encriptación '${command.encryptionType}' no soportado.", PedKeyException("11")) // 0x11 = Invalid key encryption type
            }

            // Paso 2: Mapear el tipo de llave y algoritmo del protocolo a nuestro modelo genérico.
            val genericKeyType = mapFuturexKeyTypeToGeneric(command.keyType)
            val genericAlgorithm = KeyAlgorithm.DES_TRIPLE // Asumimos TDES como default. Podría venir del comando en futuras versiones.

            // Paso 3: Convertir la llave de HEX a ByteArray.
            val keyBytes = command.keyHex.hexToByteArray()

            // Paso 4: Validar el checksum de la llave recibida (simulado).
            // En una implementación real, calcularías el checksum de `keyBytes`.
            val calculatedKeyChecksum = calculateChecksum(keyBytes)
            if (calculatedKeyChecksum.uppercase() != command.keyChecksum.uppercase()) {
                throw PedKeyException("Checksum de la llave no coincide. Recibido: ${command.keyChecksum}, Calculado: $calculatedKeyChecksum", PedKeyException("12")) // 0x12 = Invalid key checksum
            }

            // Paso 5: Llamar al controlador del PED para inyectar la llave.
            Log.i(TAG, "Inyectando llave en claro: Índice=${command.keySlot}, Tipo=${genericKeyType}, Algoritmo=${genericAlgorithm}")

            pedController!!.writeKeyPlain(
                keyIndex = command.keySlot,
                keyType = genericKeyType,
                keyAlgorithm = genericAlgorithm,
                keyBytes = keyBytes,
                kcvBytes = null // El comando 02 no incluye un KCV explícito, el PED lo puede generar.
            )

            // Paso 6: Si la inyección fue exitosa, calcular el checksum para la respuesta.
            // El manual especifica cómo se calcula. Lo simulamos aquí.
            responseKeyChecksum = calculateChecksum(keyBytes).uppercase()
            logMessage = "Inyección de clave en slot ${command.keySlot} exitosa."

        } catch (e: PedKeyException) {
            logMessage = e.message ?: "Error desconocido en PED."
            // Usamos el código de error que encapsulamos en la excepción.
            responseCode = e.cause?.message ?: "10" // 0x10 = Invalid key type (default)
            Log.e(TAG, "Error de PED procesando CMD '02': $logMessage (Código: $responseCode)")
        } catch (e: Exception) {
            logMessage = e.message ?: "Error inesperado."
            responseCode = "05" // 0x05 = Device is busy (error genérico)
            Log.e(TAG, "Error general procesando CMD '02': $logMessage", e)
        }

        // Paso Final: Enviar siempre la respuesta al host.
        val response = messageFormatter.format("02", listOf(responseCode, responseKeyChecksum))
        sendData(response)
        viewModelScope.launch { _snackbarEvent.emit(logMessage) }
    }

    private fun handleFuturexReadSerial(command: ReadSerialCommand) {
        Log.d(TAG, "Manejando ReadSerialCommand (v${command.version})")

        if (command.version != "01") {
            Log.w(TAG, "Versión de comando '03' no soportada: ${command.version}.")
            sendData(messageFormatter.format("03", listOf("02"))) // Invalid command version
            return
        }

        try {
            val serialNumber = "VGT1234567890SNX" // Valor de prueba
            val response = messageFormatter.format("03", listOf("00", serialNumber))
            sendData(response)
            viewModelScope.launch { _snackbarEvent.emit("Respuesta a CMD '03' enviada.") }
        } catch (e: Exception) {
            handleError("Error al responder a CMD '03'", e)
            sendData(messageFormatter.format("03", listOf("05"))) // Device busy
        }
    }

    private fun handleFuturexWriteSerial(command: WriteSerialCommand) {
        Log.d(TAG, "Manejando WriteSerialCommand (v${command.version}) para S/N: ${command.serialNumber}")

        var responseCode = "00"
        try {
            if (command.version != "01") throw IllegalArgumentException("Versión de comando '04' no soportada.")

            Log.i(TAG, "SIMULACIÓN: Escribiendo serial '${command.serialNumber}' en el dispositivo.")
            // Aquí iría la llamada real al SDK: pedController?.writeSerialNumber(command.serialNumber)

        } catch (e: Exception) {
            handleError("Error al procesar CMD '04'", e)
            responseCode = "0C" // Invalid key slot (error genérico de escritura)
        } finally {
            // El manual indica que la respuesta a '04' es '04' + código de estado
            val response = messageFormatter.format("04", listOf(responseCode))
            sendData(response)
        }
    }

    /**
     * Mapea el código de tipo de llave del protocolo Futurex a un enum genérico.
     */
    private fun mapFuturexKeyTypeToGeneric(futurexKeyType: String): GenericKeyType {
        return when (futurexKeyType) {
            "01" -> GenericKeyType.MASTER_KEY // Master Session Key
            "05" -> GenericKeyType.WORKING_PIN_KEY
            "04" -> GenericKeyType.WORKING_MAC_KEY
            "0C" -> GenericKeyType.WORKING_DATA_ENCRYPTION_KEY
            "06" -> GenericKeyType.TRANSPORT_KEY // Key Transfer Key
            "0F" -> GenericKeyType.MASTER_KEY // Terminal Master Key
            "03" -> GenericKeyType.DUKPT_INITIAL_KEY // DUKPT BDK (se inyecta como IPEK)
            "08" -> GenericKeyType.DUKPT_INITIAL_KEY // DUKPT 3DES BDK (se inyecta como IPEK)
            else -> throw PedKeyException("Tipo de llave Futurex no soportado: $futurexKeyType", PedKeyException("10")) // 0x10
        }
    }

    /**
     * Simula el cálculo del checksum de 4 dígitos hexadecimales.
     * En una implementación real, esto usaría una librería de criptografía.
     */
    private fun calculateChecksum(keyBytes: ByteArray): String {
        // Ejemplo simple: XOR de los primeros 2 bytes, formateado a 4 caracteres hex.
        if (keyBytes.isEmpty()) return "0000"
        val byte1 = keyBytes[0].toInt() and 0xFF
        val byte2 = if (keyBytes.size > 1) keyBytes[1].toInt() and 0xFF else 0
        val result = byte1 xor byte2
        return "%04X".format(result and 0xFFFF)
    }



    override fun onCleared() {
        Log.i(TAG, "ViewModel onCleared: Deteniendo escucha y liberando...")
        // La llamada a stopListening ahora es síncrona dentro de la corutina
        viewModelScope.launch {
            connectionMutex.withLock {
                stopListeningInternal()
            }
            pedController?.releasePed()
        }
        super.onCleared()
    }

    /**
     * Convierte un String hexadecimal a un ByteArray.
     */
    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "La cadena HEX debe tener longitud par." }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }
}
