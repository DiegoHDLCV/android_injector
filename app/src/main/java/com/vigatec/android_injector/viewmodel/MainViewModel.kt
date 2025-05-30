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
import com.example.manufacturer.base.controllers.ped.PedKeyException
import com.example.manufacturer.base.models.KeyAlgorithm
import com.example.manufacturer.base.models.KeyType // Asegúrate que GenericKeyType se llame KeyType aquí
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

    private val _rawReceivedData = MutableStateFlow<String>("")
    val rawReceivedData = _rawReceivedData.asStateFlow()

    // --- Controladores y Parser ---
    private var comController: IComController? = null
    private var pedController: IPedController? = null
    private var listeningJob: Job? = null
    private val messageParser = SerialMessageParser()

    // Define un ID conocido para tu KEK si es constante, o gestiona cómo se conoce este ID.
    private val KEK_SLOT_ID_PRIMARY = 10 // Ejemplo, el ID que usas para tu KEK principal

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
                    pedController?.initializePed()
                    Log.i(TAG, "IPedController inicializado.")
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
                            Log.d(TAG, "Datos recibidos ($bytesRead bytes): ${received.toHexString()}")
                            _rawReceivedData.value = _rawReceivedData.value + String(received, Charsets.US_ASCII)

                            messageParser.appendData(received)
                            var parsedMessage: SerialMessage?
                            do {
                                parsedMessage = messageParser.nextMessage()
                                parsedMessage?.let { processParsedCommand(it) }
                            } while (parsedMessage != null && isActive)
                        }
                        bytesRead == -6 -> Log.d(TAG, "Timeout de lectura, continuando escucha...")
                        bytesRead < 0 -> throw Exception("Error de lectura: $bytesRead")
                    }
                    delay(50)
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
            Log.i(TAG, "Escucha cancelada.")
        }
        if (_connectionStatus.value != ConnectionStatus.LISTENING && _connectionStatus.value != ConnectionStatus.DISCONNECTED) {
            comController?.close()
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
        listeningJob = null
    }

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
                }
            } catch (e: Exception) {
                handleError("Excepción al enviar datos", e)
            }
        }
    }

    private fun processParsedCommand(message: SerialMessage) {
        viewModelScope.launch {
            Log.i(TAG, "Procesando Comando: ${message.command} | Datos: ${message.fields}")
            _snackbarEvent.emit("Recibido: ${message.command}")
            try {
                if (pedController == null) {
                    sendResponse(message.command.toResponseCode(), "E1") // E1 = Error PED no disponible
                    return@launch
                }
                when (message.command) {
                    "0700" -> handleLoadMainKey(message)
                    "0720" -> handleLoadWorkKey(message)
                    "0740" -> handleLoadKek(message)
                    "0770" -> handleLoadTr31(message)
                    "PING" -> sendResponse("PONG", "OK")
                    else -> {
                        Log.w(TAG, "Comando desconocido: ${message.command}")
                        sendResponse(message.command.toResponseCode(), "01") // Comando no soportado
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando comando ${message.command}", e)
                sendResponse(message.command.toResponseCode(), "99") // Error general
            }
        }
    }

    private suspend fun handleLoadKek(message: SerialMessage) {
        val command = message.command
        try {
            if (message.fields.size < 2) throw IllegalArgumentException("Campos insuficientes para KEK (key_id, key_hex)")
            val keyId = message.fields[0].toInt()
            val keyHex = message.fields[1]
            val checkValueHex = if (message.fields.size > 2 && message.fields[2].isNotBlank()) message.fields[2] else null
            val kcvBytes = checkValueHex?.hexToByteArray()
            val keyBytes = keyHex.hexToByteArray()

            Log.i(TAG, "$command: Procesando KEK: keyId=$keyId, keyHex (len=${keyBytes.size}), kcvHex=$checkValueHex")

            val actualKeyAlgorithm = determineKeyAlgorithm(keyBytes, command)

            // Usamos TRANSPORT_KEY para KEKs, que el UrovoPedController debería mapear a loadTEK.
            val success = pedController!!.writeKeyPlain(
                keyIndex = keyId,
                keyType = KeyType.TRANSPORT_KEY,
                keyAlgorithm = actualKeyAlgorithm,
                keyBytes = keyBytes,
                kcvBytes = kcvBytes
            )
            sendResponse(command.toResponseCode(), if (success) "00" else "E2")
        } catch (e: Exception) {
            handleCommandException(command, e, "KEK")
        }
    }

    private suspend fun handleLoadMainKey(message: SerialMessage) {
        val command = message.command
        try {
            if (message.fields.size < 2) throw IllegalArgumentException("Campos insuficientes para Main Key (key_id, key_hex)")
            val keyId = message.fields[0].toInt()
            val keyHex = message.fields[1]
            val checkValueHex = if (message.fields.size > 2 && message.fields[2].isNotBlank()) message.fields[2] else null
            val kcvBytes = checkValueHex?.hexToByteArray()
            val keyBytes = keyHex.hexToByteArray()

            Log.i(TAG, "$command: Procesando MainKey: keyId=$keyId, keyHex (len=${keyBytes.size}), kcvHex=$checkValueHex")

            val actualKeyAlgorithm = determineKeyAlgorithm(keyBytes, command)

            val success = pedController!!.writeKeyPlain(
                keyIndex = keyId,
                keyType = KeyType.MASTER_KEY,
                keyAlgorithm = actualKeyAlgorithm,
                keyBytes = keyBytes,
                kcvBytes = kcvBytes
            )
            sendResponse(command.toResponseCode(), if (success) "00" else "E2")
        } catch (e: Exception) {
            handleCommandException(command, e, "MainKey")
        }
    }

    private suspend fun handleLoadWorkKey(message: SerialMessage) {
        val command = message.command // Será "0720"
        try {
            if (message.fields.size < 4) throw IllegalArgumentException("Campos insuficientes (key_type, mk_id, wk_id, key_hex)")

            val keyTypeInt = message.fields[0].toInt()
            val mkId = message.fields[1].toInt()
            val wkId = message.fields[2].toInt()
            val keyHex = message.fields[3]
            val checkValueHex = if (message.fields.size > 4 && message.fields[4].isNotBlank()) message.fields[4] else null
            val kcvBytes = checkValueHex?.hexToByteArray()
            val keyBytes = keyHex.hexToByteArray() // Esta es la llave CIFRADA

            val keyTypeToLoad = mapIntToKeyType(keyTypeInt)
            val transportKeyTypeActual = determineTransportKeyType(mkId)
            val keyAlgorithmToLoad = determineKeyAlgorithm(keyBytes, command, isEncryptedKey = true, keyTypeToLoad = keyTypeToLoad)


            Log.i(TAG, "$command: Cargando Llave Cifrada: KeyTypeToLoad=$keyTypeToLoad (val SDK=$keyTypeInt), MK_ID=$mkId (Type=$transportKeyTypeActual), WK_ID=$wkId, Algorithm=$keyAlgorithmToLoad KeyHexLen=${keyBytes.size}, KCV=$checkValueHex")

            // `keyAlgorithm` aquí es el de la llave que se está cargando, NO el de la MK.
            // El IPedController.writeKey se encargará de llamar a setKeyAlgorithm para la llave a cargar si es necesario,
            // o el SDK de Urovo lo deduce.
            val success = pedController!!.writeKey(
                keyIndex = wkId,
                keyType = keyTypeToLoad,
                keyAlgorithm = keyAlgorithmToLoad, // Algoritmo de la llave que se está cargando
                keyData = PedKeyData(keyBytes, kcvBytes), // PedKeyData debe existir y tomar (keyCiphered, kcvOfClearKey)
                transportKeyIndex = mkId,
                transportKeyType = transportKeyTypeActual
            )
            sendResponse(command.toResponseCode(), if (success) "00" else "E2")
        } catch (e: Exception) {
            handleCommandException(command, e, "WorkKey")
        }
    }

    private suspend fun handleLoadTr31(message: SerialMessage) {
        Log.w(TAG, "handleLoadTr31 no implementado.")
        sendResponse(message.command.toResponseCode(), "01")
    }

    // --- Funciones Helper para Manejo de Comandos ---
    private fun determineKeyAlgorithm(keyBytes: ByteArray, commandName: String, isEncryptedKey: Boolean = false, keyTypeToLoad: KeyType? = null): KeyAlgorithm {
        val context = if(isEncryptedKey) "llave cifrada" else "llave en claro"
        val keyLength = keyBytes.size
        val determinedAlgorithm = when (keyLength) {
            16 -> if (keyTypeToLoad == KeyType.MASTER_KEY && isEncryptedKey) KeyAlgorithm.AES_128 else KeyAlgorithm.AES_128 // AES-128 para TMK o WK
            24 -> KeyAlgorithm.AES_192 // AES-192
            32 -> KeyAlgorithm.AES_256 // AES-256
            8 -> KeyAlgorithm.DES_SINGLE
            // Para llaves TDES dobles (16 bytes) o triples (24 bytes) que no son AES:
            // Podrías necesitar lógica adicional si una llave de 16 bytes puede ser TDES o AES.
            // Por ahora, si es de 16 bytes y no es MASTER_KEY cifrada, se asume AES_128.
            // Si fuera TDES, la longitud de keyBytes (cifrados) podría ser la misma que AES.
            else -> throw IllegalArgumentException("Longitud de $context ($keyLength bytes) no soportada para comando $commandName.")
        }
        Log.i(TAG, "$commandName: Determined KeyAlgorithm: $determinedAlgorithm for $context based on key length $keyLength")
        return determinedAlgorithm
    }

    private fun mapIntToKeyType(keyTypeInt: Int): KeyType {
        // Estos valores deben coincidir con la especificación del SDK de Urovo para el parámetro 'keyType' de `loadWorkKey`
        // 0-Main key, 1-MAC key, 2-PIN key, 3-TD key [cite: 12, 16, 52, 56]
        return when (keyTypeInt) {
            0 -> KeyType.MASTER_KEY // Ej: TMK cargada bajo KEK
            1 -> KeyType.WORKING_MAC_KEY
            2 -> KeyType.WORKING_PIN_KEY
            3 -> KeyType.WORKING_DATA_ENCRYPTION_KEY
            else -> throw IllegalArgumentException("Valor de 'key_type': $keyTypeInt no es un KeyType de Urovo soportado para llaves de trabajo.")
        }
    }

    private fun determineTransportKeyType(mkId: Int): KeyType {
        // Asume que KEK_SLOT_ID_PRIMARY es el slot donde se cargó la KEK.
        // La KEK es una TRANSPORT_KEY. Cualquier otra llave maestra (como una TMK) es MASTER_KEY.
        return if (mkId == KEK_SLOT_ID_PRIMARY) {
            KeyType.TRANSPORT_KEY
        } else {
            KeyType.MASTER_KEY
        }
    }

    private suspend fun handleCommandException(command: String, e: Exception, keyContext: String) {
        Log.e(TAG, "$command: Error procesando $keyContext: ${e.message}", e)
        val errorCode = when (e) {
            is NumberFormatException, is IllegalArgumentException -> "09" // Formato Incorrecto
            is PedKeyException -> "E2" // Error PED (puede contener SDK error code en e.message)
            else -> "99" // Error General
        }
        sendResponse(command.toResponseCode(), errorCode)
    }


    // --- Funciones de Envío de Respuesta ---
    private fun sendResponse(command: String, field: String) {
        val responseBytes = SerialMessageFormatter.format(command, field)
        sendData(responseBytes)
    }

    private fun sendResponse(command: String, fields: List<String>) {
        val responseBytes = SerialMessageFormatter.format(command, fields)
        sendData(responseBytes)
    }

    // --- Utilidades ---
    fun navigate(event: UiEvent) {
        viewModelScope.launch { _uiEvent.emit(event) }
    }

    override fun onCleared() {
        Log.i(TAG, "ViewModel onCleared: Deteniendo escucha y liberando...")
        stopListening()
        comController?.close()
        super.onCleared()
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun String.toResponseCode(): String {
        return try {
            val num = this.toInt()
            (num + 10).toString().padStart(4, '0')
        } catch (e: Exception) {
            this + "R" // Fallback
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
}