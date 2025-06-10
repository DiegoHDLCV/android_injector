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
                    when {
                        bytesRead > 0 -> {
                            val received = buffer.copyOf(bytesRead)
                            Log.v(TAG, "RAW_SERIAL_IN (HEX): ${received.toHexString()}")
                            messageParser.appendData(received)
                            var parsedMessage: SerialMessage?
                            do {
                                parsedMessage = messageParser.nextMessage()
                                parsedMessage?.let { processParsedCommand(it) }
                            } while (parsedMessage != null && isActive)
                        }
                        bytesRead < 0 && bytesRead != -6 -> { // -6 es timeout, cualquier otro es un error
                            throw Exception("Error crítico de lectura de puerto (código: $bytesRead)")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) { // Solo manejar errores si la corutina no fue cancelada explícitamente
                    handleError("Error de conexión: ${e.message}", e)
                }
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

    private fun processParsedCommand(message: SerialMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Procesando Comando: ${message.command} | Datos: ${message.fields.joinToString("|")}")
            _snackbarEvent.emit("Recibido CMD: ${message.command}")

            when (SystemConfig.commProtocolSelected) {
                CommProtocol.LEGACY -> {
                    Log.d(TAG, "Procesando como comando Legacy...")
                }
                CommProtocol.FUTUREX -> {
                    when (message.command) {
                        "03" -> handleFuturexReadSerial(message)
                        "04" -> handleFuturexWriteSerial(message)

                        else -> {
                            Log.w(TAG, "Comando Futurex desconocido recibido: ${message.command}")
                        }
                    }
                }
            }
        }
    }

    private fun handleFuturexReadSerial(message: SerialMessage) {
        Log.d(TAG, "Manejando comando Futurex '03': Read Serial Number.")
        try {
            val commandVersion = message.fields.firstOrNull()
            if (commandVersion != "01") {
                Log.w(TAG, "Versión de comando '03' no soportada: $commandVersion. Se esperaba '01'.")
                val errorResponse = messageFormatter.format("03", listOf("02"))
                sendData(errorResponse)
                return
            }

            // --- CORRECCIÓN CLAVE ---
            // El número de serie ahora tiene 16 caracteres.
            val serialNumber = "VGT1234567890SNX" // 16 caracteres

            val responsePayloadFields = listOf("00", serialNumber)
            val successResponse = messageFormatter.format("03", responsePayloadFields)

            sendData(successResponse)

            viewModelScope.launch {
                _snackbarEvent.emit("Respuesta a CMD '03' enviada.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar o responder al comando '03'", e)
            val generalErrorResponse = messageFormatter.format("03", listOf("05"))
            sendData(generalErrorResponse)
        }
    }

    private fun handleFuturexWriteSerial(message: SerialMessage) {
        Log.d(TAG, "Manejando comando Futurex '04': Write Serial Number.")

        var responseCode = "00" // Código de éxito por defecto
        var logMessage = "Número de serie actualizado correctamente."

        try {
            // El payload del mensaje '04' es <VERSION><SERIAL>
            val payload = message.fields.firstOrNull() ?: ""

            if (payload.length != 18) { // 2 para versión + 16 para serial
                throw IllegalArgumentException("Longitud de payload para comando '04' incorrecta. Esperado: 18, Recibido: ${payload.length}")
            }

            val commandVersion = payload.substring(0, 2)
            if (commandVersion != "01") {
                throw IllegalArgumentException("Versión de comando '04' no soportada: $commandVersion")
            }

            val serialToWrite = payload.substring(2)
            Log.i(TAG, "Solicitud para escribir el serial: $serialToWrite")

            // --- LÓGICA DE ESCRITURA REAL ---
            // Aquí iría la llamada al SDK del fabricante para guardar el serial.
            // Por ejemplo: pedController?.writeSerialNumber(serialToWrite)
            // Como no lo tenemos, simulamos el éxito.
            if (serialToWrite.contains("ERROR")) { // Simular un error para probar
                throw Exception("Fallo simulado al escribir en memoria segura.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar comando '04'", e)
            logMessage = "Fallo al escribir el número de serie."
            // Asigna un código de error del manual, ej: 0x0C (Invalid key slot) o uno genérico
            responseCode = "0C"
        } finally {
            // Enviar siempre una respuesta, ya sea de éxito o de error.
            // La respuesta a '04' es el comando '04' y el código de estado.
            val response = messageFormatter.format("04", listOf(responseCode))
            sendData(response)
            viewModelScope.launch { _snackbarEvent.emit(logMessage) }
        }
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

    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }
}
