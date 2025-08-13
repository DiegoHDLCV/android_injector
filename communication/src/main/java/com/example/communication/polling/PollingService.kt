package com.example.communication.polling

import android.util.Log
import com.example.communication.base.IComController
import com.example.communication.libraries.CommunicationSDKManager
import com.example.format.LegacyMessage
import com.example.format.LegacyMessageFormatter
import com.example.format.LegacyMessageParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

/**
 * Servicio para manejar el polling de comunicación entre MasterPOS (Injector) y SubPOS (app)
 */
class PollingService {
    private val TAG = "PollingService"
    
    // Estados del servicio
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _isPollingActive = MutableStateFlow(false)
    val isPollingActive: StateFlow<Boolean> = _isPollingActive.asStateFlow()
    
    // Controlador de comunicación
    private var comController: IComController? = null
    
    // Parser para mensajes recibidos
    private val messageParser = LegacyMessageParser()
    
    // Job para el polling
    private var pollingJob: Job? = null
    
    // Callbacks para mensajes recibidos
    private var onMessageReceived: ((LegacyMessage) -> Unit)? = null
    
    // Intervalo de polling en milisegundos
    private val POLLING_INTERVAL = 2000L // 2 segundos
    private val RESPONSE_TIMEOUT = 5000L // 5 segundos para timeout
    
    /**
     * Inicializa el servicio de polling
     */
    suspend fun initialize() {
        Log.d(TAG, "Inicializando PollingService...")
        try {
            // Obtener el controlador de comunicación
            comController = CommunicationSDKManager.getComController()
            if (comController == null) {
                Log.e(TAG, "No se pudo obtener el controlador de comunicación")
                return
            }
            
            Log.d(TAG, "PollingService inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar PollingService", e)
        }
    }
    
    /**
     * Inicia el polling desde el MasterPOS (Injector)
     */
    fun startMasterPolling(onConnectionStatusChanged: (Boolean) -> Unit) {
        if (_isPollingActive.value) {
            Log.w(TAG, "El polling ya está activo")
            return
        }
        
        Log.d(TAG, "Iniciando polling desde MasterPOS...")
        _isPollingActive.value = true
        
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (_isPollingActive.value) {
                try {
                    Log.d(TAG, "Enviando mensaje POLL (0100)...")
                    
                    // Formatear mensaje POLL (0100)
                    val pollMessage = LegacyMessageFormatter.format("0100", "POLL")
                    
                    // Abrir puerto si es necesario
                    // Intentamos leer para verificar si el puerto está abierto
                    val testBuffer = ByteArray(1)
                    val testRead = comController?.readData(1, testBuffer, 100)
                    if (testRead == -4) { // ERROR_NOT_OPEN
                        Log.d(TAG, "Abriendo puerto de comunicación...")
                        val openResult = comController!!.open()
                        Log.d(TAG, "Resultado open(): $openResult")
                    } else if ((testRead ?: 0) < 0) {
                        Log.w(TAG, "readData prueba devolvió código de error: $testRead")
                    }
                    
                    // Enviar mensaje
                    val written = comController!!.write(pollMessage, 1000)
                    Log.d(TAG, "📤 Enviado POLL (${pollMessage.size} bytes, write()=$written): ${pollMessage.toHexString()}")
                    
                    // Esperar respuesta con timeout
                    val responseReceived = withTimeoutOrNull(RESPONSE_TIMEOUT) {
                        waitForPollResponse()
                    }
                    
                    // Actualizar estado de conexión
                    val connected = responseReceived != null
                    if (_isConnected.value != connected) {
                        _isConnected.value = connected
                        withContext(Dispatchers.Main) {
                            onConnectionStatusChanged(connected)
                        }
                    }
                    
                    if (connected) {
                        Log.d(TAG, "✅ Respuesta POLL recibida - SubPOS conectado")
                    } else {
                        Log.w(TAG, "⚠️ Timeout esperando respuesta POLL - SubPOS no responde")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error durante el polling", e)
                    if (_isConnected.value) {
                        _isConnected.value = false
                        withContext(Dispatchers.Main) {
                            onConnectionStatusChanged(false)
                        }
                    }
                }
                
                // Esperar antes del siguiente poll
                delay(POLLING_INTERVAL)
            }
        }
    }
    
    /**
     * Inicia la escucha de polling desde el SubPOS (app)
     */
    fun startSubPOSListening(onPollReceived: () -> Unit) {
        Log.d(TAG, "Iniciando escucha en SubPOS...")
        _isPollingActive.value = true
        
        // Configurar callback para mensajes recibidos
        onMessageReceived = { message ->
            if (message.command == "0100") {
                Log.d(TAG, "📥 Mensaje POLL recibido, enviando respuesta...")
                onPollReceived()
                respondToPoll()
            }
        }
        
        // Iniciar lectura continua del puerto
        CoroutineScope(Dispatchers.IO).launch {
            startReading()
        }
    }
    
    /**
     * Detiene el polling
     */
    fun stopPolling() {
        Log.d(TAG, "Deteniendo polling...")
        _isPollingActive.value = false
        _isConnected.value = false
        pollingJob?.cancel()
        pollingJob = null
        
        try {
            val result = comController?.close()
            Log.d(TAG, "Resultado close(): $result")
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar el puerto", e)
        }
    }
    
    /**
     * Envía una respuesta POLL (0110) desde el SubPOS
     */
    private fun respondToPoll() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                                 // Formatear mensaje de respuesta POLL (0110)
                val responseMessage = LegacyMessageFormatter.format("0110", "ACK")
                
                // Enviar respuesta
                val written = comController?.write(responseMessage, 1000) ?: -1
                Log.d(TAG, "📤 Respuesta POLL enviada (${responseMessage.size} bytes, write()=$written): ${responseMessage.toHexString()}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar respuesta POLL", e)
            }
        }
    }
    
    /**
     * Espera una respuesta POLL (0110)
     */
    private suspend fun waitForPollResponse(): Boolean = suspendCancellableCoroutine { cont ->
        var received = false
        
        // Configurar callback temporal para esta respuesta
        val originalCallback = onMessageReceived
        onMessageReceived = { message ->
            if (message.command == "0110" && !received) {
                received = true
                Log.d(TAG, "📥 Respuesta POLL (0110) recibida")
                cont.resume(true) {}
            }
        }
        
        // Leer datos del puerto
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val buffer = ByteArray(1024)
                val bytesRead = comController?.readData(1024, buffer, 1000) ?: 0
                
                if (bytesRead > 0) {
                    Log.d(TAG, "Datos recibidos: ${bytesRead} bytes")
                    messageParser.appendData(buffer.sliceArray(0 until bytesRead))
                    
                    // Procesar mensajes
                    var message = messageParser.nextMessage()
                    while (message != null && message is LegacyMessage) {
                        Log.d(TAG, "Mensaje parseado: $message")
                        onMessageReceived?.invoke(message)
                        message = messageParser.nextMessage()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al leer respuesta", e)
            }
        }
        
        // Restaurar callback original cuando se cancele
        cont.invokeOnCancellation {
            onMessageReceived = originalCallback
        }
    }
    
    /**
     * Lee continuamente del puerto (para SubPOS)
     */
    private suspend fun startReading() {
        try {
            // Verificar si el puerto está abierto
            val testBuffer = ByteArray(1)
            val testRead = comController?.readData(1, testBuffer, 100)
            if (testRead == -4) { // ERROR_NOT_OPEN
                Log.d(TAG, "Abriendo puerto para lectura...")
                comController!!.open()
            }
            
            val buffer = ByteArray(1024)
            
            while (_isPollingActive.value) {
                try {
                val bytesRead = comController?.readData(1024, buffer, 100) ?: 0
                    
                    if (bytesRead > 0) {
                        Log.d(TAG, "📥 Datos recibidos: ${bytesRead} bytes - ${buffer.sliceArray(0 until bytesRead).toHexString()}")
                        messageParser.appendData(buffer.sliceArray(0 until bytesRead))
                        
                        // Procesar mensajes
                        var message = messageParser.nextMessage()
                        while (message != null && message is LegacyMessage) {
                            Log.d(TAG, "📨 Mensaje parseado: $message")
                            onMessageReceived?.invoke(message)
                            message = messageParser.nextMessage()
                        }
                    }
                    
                    delay(100) // Pequeña pausa para no saturar el CPU
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error al leer del puerto", e)
                    delay(1000) // Esperar más tiempo en caso de error
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fatal en lectura continua", e)
        }
    }
    
    /**
     * Configura el callback para mensajes no relacionados con polling
     */
    fun setMessageCallback(callback: (LegacyMessage) -> Unit) {
        val originalCallback = onMessageReceived
        onMessageReceived = { message ->
            // Filtrar mensajes de polling
            if (message.command != "0100" && message.command != "0110") {
                callback(message)
            }
            // Mantener el comportamiento de polling si está activo
            originalCallback?.invoke(message)
        }
    }
    
    /**
     * Verifica si el servicio está listo para enviar mensajes (sin polling)
     */
    fun isReadyForMessaging(): Boolean {
        return comController != null && !_isPollingActive.value
    }
    
    // Extensión para convertir ByteArray a string hexadecimal
    private fun ByteArray.toHexString(): String = joinToString(" ") { "0x%02X".format(it) }
} 