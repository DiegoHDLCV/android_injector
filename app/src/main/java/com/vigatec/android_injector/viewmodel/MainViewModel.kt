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
import com.example.format.SerialMessage
import com.example.format.SerialMessageFormatter
import com.example.format.SerialMessageParser
import com.example.manufacturer.KeySDKManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.base.controllers.ped.PedKeyException // Asumiendo que existe
import com.example.manufacturer.base.models.KeyAlgorithm
import com.example.manufacturer.base.models.KeyType // Nombre de tu enum para tipos de llave genéricos
import com.example.manufacturer.base.models.PedKeyData
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
import java.nio.charset.Charset // Para Charsets.US_ASCII
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

    private val _rawReceivedData = MutableStateFlow<String>("")
    val rawReceivedData = _rawReceivedData.asStateFlow()

    // --- Controladores y Parser ---
    private var comController: IComController? = null
    private var pedController: IPedController? = null
    private var listeningJob: Job? = null
    private val messageParser = SerialMessageParser()

    private val KEK_SLOT_ID_PRIMARY = 10 // Ejemplo

    init {
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

    private fun handleError(message: String, e: Exception? = null) {
        Log.e(TAG, message, e)
        _connectionStatus.value = ConnectionStatus.ERROR
        viewModelScope.launch { _snackbarEvent.emit(message) }
    }

    fun startListening(
        baudRate: EnumCommConfBaudRate = EnumCommConfBaudRate.BPS_9600,
        parity: EnumCommConfParity = EnumCommConfParity.NOPAR,
        dataBits: EnumCommConfDataBits = EnumCommConfDataBits.DB_8
    ) {
        if (comController == null) {
            handleError("No se puede iniciar la escucha: Controlador nulo.")
            return
        }
        if (listeningJob?.isActive == true) {
            Log.w(TAG, "La escucha ya está activa.")
            return
        }

        listeningJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _connectionStatus.value = ConnectionStatus.INITIALIZING
                Log.d(TAG, "Inicializando controlador Com...")
                val initResult = comController!!.init(baudRate, parity, dataBits)
                if (initResult != 0) throw Exception("Fallo al inicializar ComController: $initResult")

                _connectionStatus.value = ConnectionStatus.OPENING
                Log.d(TAG, "Abriendo puerto...")
                val openResult = comController!!.open()
                if (openResult != 0) throw Exception("Fallo al abrir puerto: $openResult")

                _connectionStatus.value = ConnectionStatus.LISTENING
                Log.i(TAG, "Puerto abierto y escuchando...")
                _snackbarEvent.emit("Puerto abierto, escuchando...")

                val buffer = ByteArray(1024)

                while (isActive) {
                    val bytesRead = comController!!.readData(buffer.size, buffer, 5000)
                    when {
                        bytesRead > 0 -> {
                            val received = buffer.copyOf(bytesRead)

                            // =================================================================
                            // ===           LOGGING ADICIONAL PARA DEPURACIÓN             ===
                            // =================================================================
                            // Imprime los bytes crudos en formato Hexadecimal para un análisis preciso.
                            // Se usa Log.v (Verbose) para que no sature el log en modo normal.
                            Log.v(TAG, "RAW_SERIAL_IN (HEX): ${received.toHexString()}")

                            // Intenta imprimir como texto para ver si es legible.
                            // Usamos 'replace' para no crashear con caracteres inválidos.
                            val decoder = Charsets.US_ASCII.newDecoder()
                            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
// Usamos el decodificador para convertir los bytes a un string de forma segura
                            val receivedAsText = decoder.decode(java.nio.ByteBuffer.wrap(received)).toString()

                            Log.v(TAG, "RAW_SERIAL_IN (TXT): $receivedAsText")
                            // =================================================================

                            // Actualiza el Flow para la UI
                            _rawReceivedData.value = _rawReceivedData.value + receivedAsText

                            // Pasa los datos al parser para su procesamiento
                            messageParser.appendData(received)
                            var parsedMessage: SerialMessage?
                            do {
                                parsedMessage = messageParser.nextMessage()
                                parsedMessage?.let { processParsedCommand(it) }
                            } while (parsedMessage != null && isActive)
                        }
                        bytesRead == -6 -> { /* Timeout de lectura, no es un error, solo informativo. */ }
                        bytesRead < 0 -> throw Exception("Error de lectura: $bytesRead")
                    }
                    delay(20) // Pequeño delay para no sobrecargar el CPU
                }
            } catch (e: Exception) {
                if (isActive) {
                    handleError("Error en bucle de escucha: ${e.message}", e)
                }
            } finally {
                if (_connectionStatus.value != ConnectionStatus.CLOSING && _connectionStatus.value != ConnectionStatus.DISCONNECTED) {
                    Log.d(TAG, "Bucle finalizado, cerrando puerto...")
                    comController?.close()
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
            }
        }
    }

    fun stopListening() {
        if (listeningJob?.isActive == true) {
            Log.i(TAG, "Deteniendo la escucha...")
            _connectionStatus.value = ConnectionStatus.CLOSING
            listeningJob?.cancel()
            Log.i(TAG, "Solicitud de cancelación de escucha enviada.")
        }
        if (_connectionStatus.value != ConnectionStatus.LISTENING && _connectionStatus.value != ConnectionStatus.DISCONNECTED) {
            comController?.close()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
        listeningJob = null
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
        viewModelScope.launch {
            Log.i(TAG, "Procesando Comando: ${message.command} | Datos: ${message.fields.joinToString("|")}")
            _snackbarEvent.emit("Recibido CMD: ${message.command}")
            try {
                if (pedController == null) {
                    Log.e(
                        TAG,
                        "pedController es nulo, no se puede procesar el comando ${message.command}"
                    )

                    return@launch
                }


            }catch ( e: PedKeyException){

            }
        }
    }

    private suspend fun handleGetKeyInfo(message: SerialMessage) {
        val requestCommand = message.command
        val responseCommandCode = requestCommand.toResponseCode()
        // ... (resto de la función sin cambios)
    }

    private suspend fun handleLoadKek(message: SerialMessage) {
        val command = message.command
        val responseCommand = command.toResponseCode()
        // ... (resto de la función sin cambios)
    }

    // ... (El resto de tus funciones handle, map, etc. no necesitan cambios)

    // --- Helper Functions ---


    override fun onCleared() {
        Log.i(TAG, "ViewModel onCleared: Deteniendo escucha y liberando...")
        stopListening()
        comController?.close()
        pedController?.releasePed()
        super.onCleared()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "La cadena hexadecimal debe tener una longitud par" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun String.toResponseCode(): String {
        return try {
            val num = this.toInt()
            if (this.endsWith("0")) {
                (num + 1).toString().padStart(4, '0')
            } else {
                (num + 10).toString().padStart(4, '0')
            }
        } catch (e: NumberFormatException) {
            Log.w(TAG, "No se pudo convertir comando '$this' a número para generar código de respuesta. Usando fallback.")
            this + "R"
        }
    }
}