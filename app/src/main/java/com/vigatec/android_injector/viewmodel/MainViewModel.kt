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
// import com.google.gson.Gson // Ya no es necesario para la respuesta de GetKeyInfo
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
    // private val gson = Gson() // No se necesita para la respuesta pipeline de GetKeyInfo

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
                    Log.e(TAG, "pedController es nulo, no se puede procesar el comando ${message.command}")
                    sendResponse(message.command.toResponseCode(), "E1")
                    return@launch
                }
                when (message.command) {
                    "0700" -> handleLoadMainKey(message)
                    "0720" -> handleLoadWorkKey(message)
                    "0740" -> handleLoadKek(message)
                    "0770" -> handleLoadTr31(message)
                    "0800" -> handleGetKeyInfo(message)
                    "PING" -> sendResponse("PONG", "OK")
                    else -> {
                        Log.w(TAG, "Comando desconocido recibido: ${message.command}")
                        sendResponse(message.command.toResponseCode(), "01")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando comando ${message.command}", e)
                sendResponse(message.command.toResponseCode(), "99")
            }
        }
    }

    // --- Handler para GetKeyInfo (Comando 0800) ---
    private suspend fun handleGetKeyInfo(message: SerialMessage) {
        val requestCommand = message.command // "0800"
        val responseCommandCode = requestCommand.toResponseCode() // Debería ser "0810"

        val responseDataFields = mutableListOf<String>() // Para construir la respuesta tipo pipeline
        var androidResponseCode = "99" // Código de error por defecto

        try {
            if (message.fields.size < 4) {
                throw IllegalArgumentException("Campos insuficientes para GetKeyInfo. Esperados 4, recibidos ${message.fields.size}: ${message.fields.joinToString("|")}")
            }

            val keyId = message.fields[0].toIntOrNull()
                ?: throw NumberFormatException("key_id '${message.fields[0]}' no es un entero válido.")
            val keyTypeStr = message.fields[1]
            val isDukpt = message.fields[2].equals("true", ignoreCase = true)
            val groupIndexStr = message.fields[3]

            val groupIndex = if (isDukpt && groupIndexStr.isNotBlank()) {
                groupIndexStr.toIntOrNull() ?: throw NumberFormatException("group_index '${groupIndexStr}' no es un entero válido para DUKPT.")
            } else if (isDukpt && groupIndexStr.isBlank()) {
                throw IllegalArgumentException("group_index es requerido y no puede estar vacío para consulta DUKPT.")
            } else {
                null
            }

            Log.i(TAG, "$requestCommand: Solicitud GetKeyInfo: keyId=$keyId, keyTypeStr=$keyTypeStr, isDukpt=$isDukpt, groupIndex=$groupIndex")

            val genericKeyType = mapStringToGenericKeyType(keyTypeStr)

            if (isDukpt) {
                if (groupIndex == null) {
                    throw IllegalArgumentException("group_index es estrictamente requerido para consulta DUKPT.")
                }
                Log.d(TAG, "Consultando DUKPT info para grupo: $groupIndex")
                val dukptInfo = pedController?.getDukptInfo(groupIndex)
                if (dukptInfo != null) {
                    responseDataFields.add(true.toString()) // exists
                    responseDataFields.add(groupIndex.toString()) // group_index
                    responseDataFields.add(dukptInfo.ksn.toHexString()) // ksn_hex
                    responseDataFields.add(dukptInfo.counter?.toString() ?: "") // counter (vacío si null)
                    androidResponseCode = "00"
                } else {
                    responseDataFields.add(false.toString()) // exists
                    responseDataFields.add(groupIndex.toString()) // group_index
                    responseDataFields.add("") // ksn_hex
                    responseDataFields.add("") // counter
                    responseDataFields.add("DUKPT group not found or error")
                    androidResponseCode = "04"
                }
            } else { // Llave estándar
                Log.d(TAG, "Consultando Key info para: KI=$keyId, Type=$genericKeyType")
                val keyInfo = pedController?.getKeyInfo(keyId, genericKeyType)
                if (keyInfo != null) {
                    responseDataFields.add(true.toString()) // exists
                    responseDataFields.add(keyInfo.index.toString())// key_id
                    responseDataFields.add(keyInfo.type.name) // key_type_name
                    responseDataFields.add(keyInfo.algorithm?.name ?: "") // algorithm_name (vacío si null)
                    androidResponseCode = "00"
                } else {
                    responseDataFields.add(false.toString()) // exists
                    responseDataFields.add(keyId.toString()) // key_id
                    responseDataFields.add(genericKeyType.name) // key_type_name
                    responseDataFields.add("") // algorithm_name
                    responseDataFields.add("Key not found or error")
                    androidResponseCode = "04"
                }
            }

            val finalResponseFieldsForSerial = mutableListOf(androidResponseCode)
            finalResponseFieldsForSerial.addAll(responseDataFields)

            Log.i(TAG, "$responseCommandCode: Respuesta preparada: ${finalResponseFieldsForSerial.joinToString("|")}")
            sendResponse(responseCommandCode, finalResponseFieldsForSerial)

        } catch (e: PedKeyException) {
            Log.e(TAG, "$requestCommand: PedKeyException procesando GetKeyInfo: ${e.message}", e)
            androidResponseCode = "E2"
            sendResponse(responseCommandCode, listOf(androidResponseCode, "Error PED: ${e.message}"))
        }
        catch (e: IllegalArgumentException) {
            Log.e(TAG, "$requestCommand: IllegalArgumentException procesando GetKeyInfo: ${e.message}", e)
            androidResponseCode = "09"
            sendResponse(responseCommandCode, listOf(androidResponseCode, "Argumento inválido: ${e.message}"))
        }
        catch (e: Exception) {
            Log.e(TAG, "$requestCommand: Excepción general procesando GetKeyInfo: ${e.message}", e)
            androidResponseCode = "99"
            sendResponse(responseCommandCode, listOf(androidResponseCode, "Error general: ${e.message}"))
        }
    }


    private suspend fun handleLoadKek(message: SerialMessage) {
        val command = message.command
        val responseCommand = command.toResponseCode()
        try {
            if (message.fields.size < 3) throw IllegalArgumentException("Campos insuficientes para KEK (key_id, algorithm, key_hex). Recibido: ${message.fields.joinToString("|")}")

            val keyId = message.fields[0].toIntOrNull() ?: throw NumberFormatException("key_id '${message.fields[0]}' inválido")
            val algorithmStr = message.fields[1]
            val keyHex = message.fields[2]
            val checkValueHex = if (message.fields.size > 3 && message.fields[3].isNotBlank()) message.fields[3] else null

            val kcvBytes = checkValueHex?.hexToByteArray()
            val keyBytes = keyHex.hexToByteArray()

            Log.i(TAG, "$command: Procesando KEK: keyId=$keyId, algorithm=$algorithmStr, keyHex (len=${keyBytes.size}), kcvHex=$checkValueHex")

            val actualKeyAlgorithm = mapStringToKeyAlgorithm(algorithmStr, keyBytes.size)

            val success = pedController!!.writeKeyPlain(
                keyIndex = keyId,
                keyType = KeyType.TRANSPORT_KEY,
                keyAlgorithm = actualKeyAlgorithm,
                keyBytes = keyBytes,
                kcvBytes = kcvBytes
            )
            sendResponse(responseCommand, if (success) "00" else "E2")
        } catch (e: Exception) {
            handleCommandException(command, e, "KEK")
        }
    }

    private fun mapStringToKeyAlgorithm(algorithmStr: String, keyLengthBytes: Int): KeyAlgorithm {
        val expectedLength: Int
        val keyAlgorithmEnum = when (algorithmStr.uppercase()) {
            "AES-128" -> { expectedLength = 16; KeyAlgorithm.AES_128 }
            "AES-192" -> { expectedLength = 24; KeyAlgorithm.AES_192 }
            "AES-256" -> { expectedLength = 32; KeyAlgorithm.AES_256 }
            "DES", "DES-SINGLE" -> { expectedLength = 8; KeyAlgorithm.DES_SINGLE }
            "2TDEA", "TDES-2KEY" -> { expectedLength = 16; KeyAlgorithm.DES_TRIPLE }
            "3TDEA", "TDES-3KEY" -> { expectedLength = 24; KeyAlgorithm.DES_TRIPLE }
            else -> throw IllegalArgumentException("Algoritmo '$algorithmStr' no reconocido.")
        }

        if (keyLengthBytes != expectedLength) {
            throw IllegalArgumentException("La longitud de la llave ($keyLengthBytes bytes) no coincide con la esperada para el algoritmo $algorithmStr ($expectedLength bytes).")
        }
        return keyAlgorithmEnum
    }

    private suspend fun handleLoadMainKey(message: SerialMessage) {
        val command = message.command
        val responseCommand = command.toResponseCode()
        try {
            if (message.fields.size < 2) throw IllegalArgumentException("Campos insuficientes para Main Key (key_id, key_hex). Recibido: ${message.fields.joinToString("|")}")
            val keyId = message.fields[0].toIntOrNull() ?: throw NumberFormatException("key_id '${message.fields[0]}' inválido")
            val keyHex = message.fields[1]
            val checkValueHex = if (message.fields.size > 2 && message.fields[2].isNotBlank()) message.fields[2] else null
            val kcvBytes = checkValueHex?.hexToByteArray()
            val keyBytes = keyHex.hexToByteArray()

            Log.i(TAG, "$command: Procesando MainKey: keyId=$keyId, keyHex (len=${keyBytes.size}), kcvHex=$checkValueHex")

            val actualKeyAlgorithm = determineKeyAlgorithmFromLength(keyBytes, command)

            val success = pedController!!.writeKeyPlain(
                keyIndex = keyId,
                keyType = KeyType.MASTER_KEY,
                keyAlgorithm = actualKeyAlgorithm,
                keyBytes = keyBytes,
                kcvBytes = kcvBytes
            )
            sendResponse(responseCommand, if (success) "00" else "E2")
        } catch (e: Exception) {
            handleCommandException(command, e, "MainKey")
        }
    }

    private suspend fun handleLoadWorkKey(message: SerialMessage) {
        val command = message.command
        val responseCommand = command.toResponseCode()
        try {
            if (message.fields.size < 4) throw IllegalArgumentException("Campos insuficientes (key_type, mk_id, wk_id, key_hex). Recibido: ${message.fields.joinToString("|")}")

            val keyTypeInt = message.fields[0].toIntOrNull() ?: throw NumberFormatException("key_type '${message.fields[0]}' inválido")
            val mkId = message.fields[1].toIntOrNull() ?: throw NumberFormatException("mk_id '${message.fields[1]}' inválido")
            val wkId = message.fields[2].toIntOrNull() ?: throw NumberFormatException("wk_id '${message.fields[2]}' inválido")
            val keyHex = message.fields[3]
            val checkValueHex = if (message.fields.size > 4 && message.fields[4].isNotBlank()) message.fields[4] else null
            val kcvBytes = checkValueHex?.hexToByteArray()
            val keyBytes = keyHex.hexToByteArray()

            val keyTypeToLoad = mapIntToWorkKeyType(keyTypeInt)
            val transportKeyTypeActual = determineTransportKeyType(mkId)
            val keyAlgorithmToLoad = determineKeyAlgorithmFromLength(keyBytes, command, isEncryptedKey = true, keyTypeToLoad = keyTypeToLoad)

            Log.i(TAG, "$command: Cargando Llave Cifrada: KeyTypeToLoad=$keyTypeToLoad (val SDK=$keyTypeInt), MK_ID=$mkId (Type=$transportKeyTypeActual), WK_ID=$wkId, Algorithm=$keyAlgorithmToLoad KeyHexLen=${keyBytes.size}, KCV=$checkValueHex")

            val success = pedController!!.writeKey(
                keyIndex = wkId,
                keyType = keyTypeToLoad,
                keyAlgorithm = keyAlgorithmToLoad,
                keyData = PedKeyData(keyBytes, kcvBytes),
                transportKeyIndex = mkId,
                transportKeyType = transportKeyTypeActual
            )
            sendResponse(responseCommand, if (success) "00" else "E2")
        } catch (e: Exception) {
            handleCommandException(command, e, "WorkKey")
        }
    }

    private suspend fun handleLoadTr31(message: SerialMessage) {
        Log.w(TAG, "handleLoadTr31 no implementado todavía.")
        sendResponse(message.command.toResponseCode(), "01")
    }

    private fun determineKeyAlgorithmFromLength(keyBytes: ByteArray, commandName: String, isEncryptedKey: Boolean = false, keyTypeToLoad: KeyType? = null): KeyAlgorithm {
        val context = if(isEncryptedKey) "llave cifrada" else "llave en claro"
        val keyLength = keyBytes.size
        Log.d(TAG, "$commandName: Determinando algoritmo por longitud ($keyLength bytes) para $context")

        val determinedAlgorithm = when (keyLength) {
            16 -> {
                if (keyTypeToLoad == KeyType.MASTER_KEY && isEncryptedKey) {
                    Log.d(TAG, "Asumiendo AES_128 para MasterKey cifrada de 16 bytes")
                    KeyAlgorithm.AES_128
                } else {
                    Log.w(TAG, "$commandName: Longitud de llave de 16 bytes. Asumiendo AES-128 por defecto. Esto es ambiguo (podría ser 2TDEA).")
                    KeyAlgorithm.AES_128
                }
            }
            24 -> KeyAlgorithm.AES_192
            32 -> KeyAlgorithm.AES_256
            8 -> KeyAlgorithm.DES_SINGLE
            else -> throw IllegalArgumentException("Longitud de $context ($keyLength bytes) no soportada para comando $commandName sin algoritmo explícito.")
        }
        Log.i(TAG, "$commandName: Determined KeyAlgorithm (fallback por longitud): $determinedAlgorithm for $context")
        return determinedAlgorithm
    }

    private fun mapIntToWorkKeyType(keyTypeIntFromMessage: Int): KeyType {
        return when (keyTypeIntFromMessage) {
            0 -> KeyType.MASTER_KEY
            1 -> KeyType.WORKING_MAC_KEY
            2 -> KeyType.WORKING_PIN_KEY
            3 -> KeyType.WORKING_DATA_ENCRYPTION_KEY
            else -> throw IllegalArgumentException("Valor de 'key_type_from_message': $keyTypeIntFromMessage no es un tipo de llave de trabajo soportado.")
        }
    }

    private fun mapStringToGenericKeyType(keyTypeStrFromMessage: String): KeyType {
        return try {
            KeyType.valueOf(keyTypeStrFromMessage.uppercase())
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error mapeando string a KeyType: '$keyTypeStrFromMessage'", e)
            throw IllegalArgumentException("Tipo de llave desconocido: '$keyTypeStrFromMessage'")
        }
    }

    private fun determineTransportKeyType(mkId: Int): KeyType {
        return if (mkId == KEK_SLOT_ID_PRIMARY) {
            KeyType.TRANSPORT_KEY
        } else {
            KeyType.MASTER_KEY
        }
    }

    private suspend fun handleCommandException(command: String, e: Exception, keyContext: String) {
        val responseCommandCode = command.toResponseCode()
        Log.e(TAG, "$command -> $responseCommandCode: Error procesando $keyContext: ${e.message}", e)

        val errorFields = mutableListOf<String>() // Cambiado para enviar como lista de strings
        val finalAndroidErrorCode: String

        when (e) {
            is NumberFormatException, is IllegalArgumentException -> {
                finalAndroidErrorCode = "09" // Formato Incorrecto
                errorFields.add("Formato o argumento incorrecto: ${e.message}")
            }
            is PedKeyException -> {
                finalAndroidErrorCode = "E2" // Error PED
                errorFields.add("Error PED: ${e.message}")
            }
            else -> {
                finalAndroidErrorCode = "99" // Error General
                errorFields.add("Error general inesperado: ${e.message}")
            }
        }
        // Enviar el código de error y luego los campos de detalle del error
        sendResponse(responseCommandCode, listOf(finalAndroidErrorCode) + errorFields)
    }

    private fun sendResponse(responseCommandCode: String, field: String) {
        val responseBytes = SerialMessageFormatter.format(responseCommandCode, field)
        sendData(responseBytes)
    }

    private fun sendResponse(responseCommandCode: String, fields: List<String>) {
        val responseBytes = SerialMessageFormatter.format(responseCommandCode, fields)
        sendData(responseBytes)
    }

    fun navigate(event: UiEvent) {
        viewModelScope.launch { _uiEvent.emit(event) }
    }

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
                // Considera si esta es la lógica correcta para todos los casos
                // Por ejemplo, si un comando es 0741, esto daría 0751.
                // Si la respuesta a 0741 fuera, por ejemplo, 0742, esta lógica no aplicaría.
                (num + 10).toString().padStart(4, '0')
            }
        } catch (e: NumberFormatException) {
            Log.w(TAG, "No se pudo convertir comando '$this' a número para generar código de respuesta. Usando fallback.")
            this + "R"
        }
    }
}