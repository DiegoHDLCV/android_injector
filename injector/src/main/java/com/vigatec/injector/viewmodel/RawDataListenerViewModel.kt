package com.vigatec.injector.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.example.communication.libraries.CommunicationSDKManager
import com.example.communication.libraries.aisino.AisinoCommunicationManager
import com.example.config.SystemConfig
import com.example.communication.polling.CommLog
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

enum class ListenerStatus {
    DISCONNECTED,
    INITIALIZING,
    OPENING,
    LISTENING,
    CLOSING,
    ERROR
}

@HiltViewModel
class RawDataListenerViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val TAG = "RawDataListenerViewModel"

    // --- Flows y estados ---
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    private val _connectionStatus = MutableStateFlow(ListenerStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _rawReceivedData = MutableStateFlow("")
    val rawReceivedData = _rawReceivedData.asStateFlow()

    private val _sentData = MutableStateFlow("")
    val sentData = _sentData.asStateFlow()

    private var comController: IComController? = null
    private var listeningJob: Job? = null
    private val connectionMutex = Mutex()

    init {
        Log.i(TAG, "RawDataListenerViewModel creado.")
    }

    private fun ensureComControllerIsReady(): Boolean {
        if (comController == null) {
            Log.d(TAG, "comController es nulo, intentando obtenerlo de CommunicationSDKManager...")
            CommLog.d(TAG, "Intentando obtener comController…")
            comController = CommunicationSDKManager.getComController()
        }
        if (comController == null) {
            handleError("El controlador de comunicación no está disponible.")
            CommLog.e(TAG, "comController no disponible")
            return false
        }
        return true
    }

    private fun handleError(message: String, e: Throwable? = null) {
        Log.e(TAG, message, e)
        _connectionStatus.value = ListenerStatus.ERROR
        viewModelScope.launch { _snackbarEvent.emit("Error: $message") }
    }

    fun startListening(
        baudRate: EnumCommConfBaudRate = EnumCommConfBaudRate.BPS_9600,
        parity: EnumCommConfParity = EnumCommConfParity.NOPAR,
        dataBits: EnumCommConfDataBits = EnumCommConfDataBits.DB_8
    ) = viewModelScope.launch {
        connectionMutex.withLock {
            if (!ensureComControllerIsReady()) return@withLock
            val effectiveBaud = if (SystemConfig.managerSelected.name == "AISINO") {
                val b = AisinoCommunicationManager.getSelectedBaudEnum()
                Log.i(TAG, "Alineando baud con auto-scan AISINO: ${b.name}")
                b
            } else baudRate
            startListeningInternal(effectiveBaud, parity, dataBits)
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
                _connectionStatus.value = ListenerStatus.INITIALIZING
                Log.d(TAG, "startListeningInternal: Estado de conexión cambiado a INITIALIZING.")

                // DIAGNÓSTICO: Verificar estado del sistema antes de abrir
                Log.i(TAG, "=== DIAGNÓSTICO DE CONEXIÓN INJECTOR ===")
                Log.i(TAG, "Fabricante detectado: ${SystemConfig.managerSelected}")
                Log.i(TAG, "Rol del dispositivo: ${SystemConfig.deviceRole}")
                Log.i(TAG, "Protocolo seleccionado: ${SystemConfig.commProtocolSelected}")
                Log.i(TAG, "Parámetros: baudRate=$baudRate, parity=$parity, dataBits=$dataBits")

                // Intentar reinicializar el SDK si el primer intento falla
                var openAttempts = 0
                var openRes = -1
                val maxAttempts = 3

                while (openAttempts < maxAttempts && openRes != 0) {
                    openAttempts++
                    Log.i(TAG, "Intento de conexión #$openAttempts de $maxAttempts")

                    // SDK ya fue inicializado en Splash; evitar re-inicializaciones aquí
                    // Solo intentamos cerrar/abrir el puerto en reintentos

                    // Inicializar controlador
                    comController!!.init(baudRate, parity, dataBits)
                    Log.d(TAG, "comController inicializado (intento #$openAttempts)")

                    // Intentar abrir puerto
                    openRes = comController!!.open()
                    Log.i(TAG, "open() intento #$openAttempts => $openRes")
                    CommLog.d(TAG, "open() intento #$openAttempts => $openRes")

                    if (openRes == 0) {
                        Log.i(TAG, "¡Puerto abierto exitosamente en intento #$openAttempts!")
                        break
                    } else {
                        Log.w(TAG, "Fallo al abrir puerto en intento #$openAttempts: código $openRes")
                        if (openAttempts < maxAttempts) {
                            Log.i(TAG, "Esperando antes del siguiente intento...")
                            kotlinx.coroutines.delay(2000) // Esperar 2 segundos antes del siguiente intento
                        }
                    }
                }

                if (openRes != 0) {
                    val errorMsg = when (openRes) {
                        -1 -> "Error genérico (-1) - Puerto no disponible o en uso"
                        -2 -> "Error de permisos (-2) - Verifique permisos USB"
                        -3 -> "Puerto no encontrado (-3) - Dispositivo no conectado"
                        -4 -> "Puerto ya abierto (-4) - Recurso en uso"
                        else -> "Error desconocido ($openRes)"
                    }
                    throw Exception("No se pudo abrir el puerto tras $maxAttempts intentos. $errorMsg")
                }

                _connectionStatus.value = ListenerStatus.LISTENING
                Log.i(TAG, "¡Conexión establecida! Escuchando datos raw.")
                _snackbarEvent.emit("Conexión establecida tras $openAttempts intento(s).")

                val buffer = ByteArray(1024)
                var silentReads = 0
                var anyDataEver = false
                var pingSent = false
                while (isActive) {
                    val bytesRead = comController!!.readData(buffer.size, buffer, 1000)
                    if (bytesRead > 0) {
                        val received = buffer.copyOf(bytesRead)
                        val receivedString = String(received, Charsets.US_ASCII)
                        val hexString = received.joinToString("") { "%02X".format(it) }

                        // Actualizar datos recibidos
                        val newData = "RX [${System.currentTimeMillis()}]: HEX($hexString) ASCII('$receivedString')\n"
                        _rawReceivedData.value += newData

                        Log.v(TAG, "RAW_SERIAL_IN (HEX): $hexString (ASCII: '$receivedString')")
                        CommLog.i(TAG, "RX ${bytesRead}B: $hexString")
                        anyDataEver = true
                        silentReads = 0

                        // Emitir notificación de datos recibidos
                        _snackbarEvent.emit("Datos recibidos: ${bytesRead} bytes")

                    } else {
                        silentReads++
                        if (!pingSent && SystemConfig.managerSelected.name == "AISINO") {
                            // Enviar ping 0x06 una vez si aún no ha llegado nada
                            try {
                                val ping = byteArrayOf(0x06)
                                val w = comController!!.write(ping, 200)
                                Log.d(TAG, "Ping inicial (0x06) enviado a AISINO write=$w")
                            } catch (e: Exception) {
                                Log.w(TAG, "Fallo enviando ping inicial: ${e.message}")
                            }
                            pingSent = true
                        }
                        if (!anyDataEver && silentReads % 5 == 0) {
                            //Log.i(TAG, "${silentReads} lecturas silenciosas AISINO - intentando re-scan")
                            CommunicationSDKManager.rescanIfSupported()
                            // Re-obtener controller tras rescan
                            comController = CommunicationSDKManager.getComController()
                            if (comController != null) {
                                comController!!.init(baudRate, parity, dataBits)
                                comController!!.open()
                                //Log.i(TAG, "Re-scan aplicado y puerto reabierto")
                            }
                        }
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
                val closeRes = comController?.close()
                CommLog.d(TAG, "close() => $closeRes")
                if (_connectionStatus.value != ListenerStatus.ERROR) {
                    _connectionStatus.value = ListenerStatus.DISCONNECTED
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
        _connectionStatus.value = ListenerStatus.CLOSING
        listeningJob?.cancel()
        listeningJob?.join()
        listeningJob = null
        _connectionStatus.value = ListenerStatus.DISCONNECTED
        Log.i(TAG, "Conexión detenida.")
        _snackbarEvent.emit("Conexión cerrada.")
    }

    fun sendAck() = viewModelScope.launch {
        connectionMutex.withLock {
            if (!ensureComControllerIsReady()) return@withLock
            if (_connectionStatus.value != ListenerStatus.LISTENING) {
                _snackbarEvent.emit("No hay conexión activa para enviar ACK")
                return@withLock
            }

            try {
                // Enviar un ACK simple (0x06)
                val ackData = byteArrayOf(0x06)
                val written = comController!!.write(ackData, 1000)
                val hexString = ackData.joinToString("") { "%02X".format(it) }

                val newData = "TX [${System.currentTimeMillis()}]: ACK(${hexString})\n"
                _sentData.value += newData

                Log.d(TAG, "ACK enviado: $hexString, resultado write: $written")
                CommLog.i(TAG, "TX ACK: $hexString (write=$written)")
                _snackbarEvent.emit("ACK enviado: $hexString")

            } catch (e: Exception) {
                Log.e(TAG, "Error enviando ACK: ${e.message}", e)
                _snackbarEvent.emit("Error enviando ACK: ${e.message}")
            }
        }
    }

    fun sendCustomData(data: String) = viewModelScope.launch {
        connectionMutex.withLock {
            if (!ensureComControllerIsReady()) return@withLock
            if (_connectionStatus.value != ListenerStatus.LISTENING) {
                _snackbarEvent.emit("No hay conexión activa para enviar datos")
                return@withLock
            }

            try {
                // Convertir string a bytes (ASCII)
                val dataBytes = data.toByteArray(Charsets.US_ASCII)
                val written = comController!!.write(dataBytes, 1000)
                val hexString = dataBytes.joinToString("") { "%02X".format(it) }

                val newData = "TX [${System.currentTimeMillis()}]: HEX($hexString) ASCII('$data')\n"
                _sentData.value += newData

                Log.d(TAG, "Datos enviados: $hexString, resultado write: $written")
                CommLog.i(TAG, "TX: $hexString (write=$written)")
                _snackbarEvent.emit("Datos enviados: ${dataBytes.size} bytes")

            } catch (e: Exception) {
                Log.e(TAG, "Error enviando datos: ${e.message}", e)
                _snackbarEvent.emit("Error enviando datos: ${e.message}")
            }
        }
    }

    fun clearReceivedData() {
        _rawReceivedData.value = ""
    }

    fun clearSentData() {
        _sentData.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel limpiado, deteniendo escucha...")
        viewModelScope.launch {
            stopListeningInternal()
        }
    }
}
