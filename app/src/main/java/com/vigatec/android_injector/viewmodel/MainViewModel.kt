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
import com.example.manufacturer.KeySDKManager // <<< Importar KeySDKManager
import com.example.manufacturer.base.controllers.ped.IPedController // <<< Importar IPedController
import com.example.manufacturer.base.controllers.ped.PedKeyException

import com.example.manufacturer.base.models.KeyAlgorithm
import com.example.manufacturer.base.models.KeyType
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

    // --- MODIFICADO --- Cambiado a _rawReceivedData
    private val _rawReceivedData = MutableStateFlow<String>("")
    val rawReceivedData = _rawReceivedData.asStateFlow()

    // --- Controladores y Parser ---
    private var comController: IComController? = null
    private var pedController: IPedController? = null // <<< NUEVO: Controlador PED
    private var listeningJob: Job? = null
    private val messageParser = SerialMessageParser() // <<< NUEVO: Instancia del Parser

    init {
        // Obtener controlador de comunicación
        comController = CommunicationSDKManager.getComController()
        if (comController == null) {
            handleError("Error al obtener controlador de comunicación")
        } else {
            Log.i(TAG, "IComController obtenido exitosamente.")
        }

        // <<< NUEVO: Obtener controlador PED ---
        // Asumiendo que KeySDKManager ya está inicializado (esto debería manejarse
        // en una capa de inicialización de la app, aquí lo obtenemos directamente).
        // ¡¡Asegúrate que KeySDKManager.initialize() se llame antes!!
        pedController = KeySDKManager.getPedController()
        if (pedController == null) {
            handleError("Error al obtener controlador PED")
        } else {
            Log.i(TAG, "IPedController obtenido exitosamente.")
            // Podrías llamar a initializePed aquí si es necesario
            viewModelScope.launch {
                try {
                    pedController?.initializePed()
                    Log.i(TAG, "IPedController inicializado.")
                } catch (e: Exception) {
                    handleError("Error al inicializar PED: ${e.message}")
                }
            }
        }
    }

    // --- Función de Ayuda para Errores ---
    private fun handleError(message: String, e: Exception? = null) {
        Log.e(TAG, message, e)
        _connectionStatus.value = ConnectionStatus.ERROR
        viewModelScope.launch { _snackbarEvent.emit(message) }
    }

    // --- Lógica de Escucha ---
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
                            // --- MODIFICADO: Usar parser ---
                            Log.d(TAG, "Datos recibidos ($bytesRead bytes): ${received.toHexString()}")
                            _rawReceivedData.value = _rawReceivedData.value + String(received, Charsets.US_ASCII) // Muestra en UI

                            messageParser.appendData(received)
                            var parsedMessage: SerialMessage?
                            do {
                                parsedMessage = messageParser.nextMessage()
                                parsedMessage?.let { processParsedCommand(it) }
                            } while (parsedMessage != null && isActive)
                            // --- FIN MODIFICADO ---
                        }
                        bytesRead == -6 -> Log.d(TAG, "Timeout de lectura, continuando escucha...") // ERROR_READ_TIMEOUT
                        bytesRead < 0 -> throw Exception("Error de lectura: $bytesRead")
                    }
                    delay(50) // Pequeña pausa para no sobrecargar CPU
                }

            } catch (e: Exception) {
                if (isActive) { // Solo mostrar error si no fue cancelado
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
            Log.i(TAG, "Escucha cancelada.")
        }
        // El 'finally' del bucle se encargará de cerrar si es necesario.
        // Forzamos el cierre si no estaba escuchando pero no desconectado.
        if (_connectionStatus.value != ConnectionStatus.LISTENING && _connectionStatus.value != ConnectionStatus.DISCONNECTED) {
            comController?.close()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
        listeningJob = null
    }

    // --- Lógica de Envío ---
    fun sendData(data: ByteArray) {
        if (comController == null || _connectionStatus.value != ConnectionStatus.LISTENING) {
            Log.e(TAG, "No se puede enviar: Puerto no listo.")
            viewModelScope.launch { _snackbarEvent.emit("Error: Puerto no listo para enviar") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Enviando ${data.size} bytes: ${data.toHexString()}")
                val bytesWritten = comController!!.write(data, 1000)
                if (bytesWritten < 0) {
                    _snackbarEvent.emit("Error al enviar datos: $bytesWritten")
                } else {
                    Log.i(TAG, "Se enviaron $bytesWritten bytes.")
                    // _snackbarEvent.emit("Datos enviados") // Opcional, puede ser ruidoso
                }
            } catch (e: Exception) {
                handleError("Excepción al enviar datos", e)
            }
        }
    }

    // --- <<< LÓGICA DE PROCESAMIENTO DE COMANDOS >>> ---
    private fun processParsedCommand(message: SerialMessage) {
        viewModelScope.launch { // Usar coroutine para llamadas suspend y no bloquear
            Log.i(TAG, "Procesando Comando: ${message.command} | Datos: ${message.fields}")
            _snackbarEvent.emit("Recibido: ${message.command}")

            try {
                if (pedController == null) {
                    sendResponse(message.command, "E1") // E1 = Error PED
                    return@launch
                }

                when (message.command) {
                    "0700" -> handleLoadMainKey(message) // Cargar Llave Maestra
                    "0720" -> handleLoadWorkKey(message) // Cargar Llave Trabajo
                    "0770" -> handleLoadTr31(message) // Cargar TR-31 (¡Implementación pendiente!)
                    "PING" -> sendResponse("PONG", "OK")
                    else -> {
                        Log.w(TAG, "Comando desconocido: ${message.command}")
                        sendResponse(message.command, "01") // 01 = Comando no soportado
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando comando ${message.command}", e)
                sendResponse(message.command, "99") // 99 = Error general
            }
        }
    }

    private suspend fun handleLoadMainKey(message: SerialMessage) {
        val command = message.command
        try {
            if (message.fields.size < 2) throw IllegalArgumentException("Campos insuficientes")
            val keyId = message.fields[0].toInt()
            val keyHex = message.fields[1]
            // val checkValueHex = if (message.fields.size > 2) message.fields[2] else null // Urovo no usa KCV para claro

            val keyBytes = keyHex.hexToByteArray()

            // ¡¡IMPORTANTE!! Debes saber el algoritmo. Asumimos TDES por defecto.
            // Podrías añadir otro campo al comando serial para especificarlo.
            val success = pedController!!.writeKeyPlain(
                keyIndex = keyId,
                keyType = KeyType.MASTER_KEY,
                keyAlgorithm = KeyAlgorithm.DES_TRIPLE, // ¡Asunción!
                keyBytes = keyBytes
            )

            if (success) sendResponse(command.toResponseCode(), "00")
            else sendResponse(command.toResponseCode(), "E2") // E2 = Error Escritura Llave

        } catch (e: NumberFormatException) {
            Log.e(TAG, "$command: keyId no es un número", e)
            sendResponse(command.toResponseCode(), "09") // 09 = Formato Incorrecto
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "$command: ${e.message}", e)
            sendResponse(command.toResponseCode(), "09") // 09 = Formato Incorrecto
        } catch (e: PedKeyException) {
            Log.e(TAG, "$command: Error PED", e)
            sendResponse(command.toResponseCode(), "E2")
        }
    }

    private suspend fun handleLoadWorkKey(message: SerialMessage) {
        // TODO: Implementar lógica similar a handleLoadMainKey,
        //       parseando keyType, mkId, wkId, keyHex, kcvHex
        //       y llamando a pedController!!.writeKey(...)
        //       ¡Maneja errores y envía respuestas!
        Log.w(TAG, "handleLoadWorkKey no implementado.")
        sendResponse(message.command.toResponseCode(), "01")
    }

    private suspend fun handleLoadTr31(message: SerialMessage) {
        // TODO: ¡Esta es la parte compleja!
        //       Necesitarás:
        //       1. Parsear el bloque TR-31 (key_slot, key_usage, tr31_block_hex)
        //       2. Probablemente usar una librería para parsear el bloque TR-31.
        //       3. Determinar qué función de Urovo usar (¿loadEncryptMainKey?, ¿loadDukptBlob?).
        //       4. Llamar a pedController con los datos correctos.
        Log.w(TAG, "handleLoadTr31 no implementado.")
        sendResponse(message.command.toResponseCode(), "01")
    }


    // --- <<< Funciones de Envío de Respuesta >>> ---
    private fun sendResponse(command: String, field: String) {
        val responseBytes = SerialMessageFormatter.format(command, field)
        sendData(responseBytes)
    }

    private fun sendResponse(command: String, fields: List<String>) {
        val responseBytes = SerialMessageFormatter.format(command, fields)
        sendData(responseBytes)
    }

    // --- <<< Utilidades >>> ---
    fun navigate(event: UiEvent) {
        viewModelScope.launch { _uiEvent.emit(event) }
    }

    override fun onCleared() {
        Log.i(TAG, "ViewModel onCleared: Deteniendo escucha y liberando...")
        stopListening()
        comController?.close() // Asegurar cierre
        // pedController?.releasePed() // Opcional, si KeySDKManager no lo hace.
        super.onCleared()
    }

    // Helper para convertir hex a bytes (¡Añadir validación si es necesario!)
    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    // Helper para convertir comando de request a response (ej. 0700 -> 0710)
    private fun String.toResponseCode(): String {
        return try {
            val num = this.toInt()
            (num + 10).toString().padStart(4, '0')
        } catch (e: Exception) {
            this + "R" // Fallback
        }
    }

    // Helper para mostrar bytes en logs
    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
}