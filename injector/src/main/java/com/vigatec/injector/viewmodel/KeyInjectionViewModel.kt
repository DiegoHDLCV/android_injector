package com.vigatec.injector.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
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
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.entities.KeyConfiguration
import com.example.persistence.entities.ProfileEntity
import com.example.persistence.repository.InjectedKeyRepository
import com.example.persistence.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.Charset
import javax.inject.Inject

enum class InjectionStatus {
    IDLE,
    CONNECTING,
    INJECTING,
    SUCCESS,
    ERROR,
    COMPLETED
}

data class KeyInjectionState(
    val status: InjectionStatus = InjectionStatus.IDLE,
    val currentProfile: ProfileEntity? = null,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val progress: Float = 0f,
    val log: String = "",
    val error: String? = null,
    val showInjectionModal: Boolean = false
)

@HiltViewModel
class KeyInjectionViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val injectedKeyRepository: InjectedKeyRepository,
    private val application: android.app.Application
) : ViewModel() {

    private val TAG = "KeyInjectionViewModel"
    
    private val _state = MutableStateFlow(KeyInjectionState())
    val state = _state.asStateFlow()
    
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    private var comController: IComController? = null
    private var messageParser: IMessageParser? = null
    private var messageFormatter: IMessageFormatter? = null
    private val connectionMutex = Mutex()

    init {
        setupProtocolHandlers()
    }

    private fun setupProtocolHandlers() {
        messageParser = when (SystemConfig.commProtocolSelected) {
            CommProtocol.LEGACY -> LegacyMessageParser()
            CommProtocol.FUTUREX -> FuturexMessageParser()
        }
        messageFormatter = when (SystemConfig.commProtocolSelected) {
            CommProtocol.LEGACY -> LegacyMessageFormatter
            CommProtocol.FUTUREX -> FuturexMessageFormatter
        }
        Log.i(TAG, "Protocolo de comunicación establecido en: ${SystemConfig.commProtocolSelected}")
    }

    fun showInjectionModal(profile: ProfileEntity) {
        _state.value = _state.value.copy(
            showInjectionModal = true,
            currentProfile = profile,
            totalSteps = profile.keyConfigurations.size
        )
    }

    fun hideInjectionModal() {
        _state.value = _state.value.copy(
            showInjectionModal = false,
            currentProfile = null,
            status = InjectionStatus.IDLE,
            currentStep = 0,
            progress = 0f,
            log = "",
            error = null
        )
    }

    fun startKeyInjection() {
        viewModelScope.launch {
            val profile = _state.value.currentProfile ?: return@launch
            val keyConfigs = profile.keyConfigurations
            
            if (keyConfigs.isEmpty()) {
                _snackbarEvent.emit("El perfil no tiene configuraciones de llaves")
                return@launch
            }

            _state.value = _state.value.copy(
                status = InjectionStatus.CONNECTING,
                currentStep = 0,
                progress = 0f,
                log = "Iniciando inyección de llaves para perfil: ${profile.name}\n"
            )

            try {
                // Inicializar comunicación
                initializeCommunication()
                
                _state.value = _state.value.copy(
                    status = InjectionStatus.INJECTING,
                    log = _state.value.log + "Conexión establecida. Iniciando inyección...\n"
                )

                // Procesar cada configuración de llave
                for ((index, keyConfig) in keyConfigs.withIndex()) {
                    _state.value = _state.value.copy(
                        currentStep = index + 1,
                        progress = (index + 1).toFloat() / keyConfigs.size,
                        log = _state.value.log + "Inyectando llave ${index + 1}/${keyConfigs.size}: ${keyConfig.usage}\n"
                    )

                    injectKey(keyConfig)
                    
                    // Pequeña pausa entre inyecciones
                    kotlinx.coroutines.delay(500)
                }

                _state.value = _state.value.copy(
                    status = InjectionStatus.SUCCESS,
                    log = _state.value.log + "¡Inyección completada exitosamente!\n"
                )
                
                _snackbarEvent.emit("Inyección de llaves completada")

            } catch (e: Exception) {
                Log.e(TAG, "Error durante la inyección de llaves", e)
                _state.value = _state.value.copy(
                    status = InjectionStatus.ERROR,
                    error = e.message ?: "Error desconocido",
                    log = _state.value.log + "Error: ${e.message}\n"
                )
                _snackbarEvent.emit("Error durante la inyección: ${e.message}")
            } finally {
                closeCommunication()
            }
        }
    }

    private suspend fun initializeCommunication() {
        connectionMutex.withLock {
            try {
                // Inicializar el SDK de comunicación
                CommunicationSDKManager.initialize(application)
                
                // Obtener el controlador de comunicación
                comController = CommunicationSDKManager.getComController()
                    ?: throw Exception("No se pudo obtener el controlador de comunicación")

                // Configurar y abrir la conexión
                comController!!.init(
                    EnumCommConfBaudRate.BPS_115200,
                    EnumCommConfParity.NOPAR,
                    EnumCommConfDataBits.DB_8
                )
                
                val openResult = comController!!.open()
                if (openResult != 0) {
                    throw Exception("Error al abrir la conexión serial: $openResult")
                }

                Log.i(TAG, "Comunicación inicializada exitosamente")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error al inicializar la comunicación", e)
                throw e
            }
        }
    }

    private suspend fun injectKey(keyConfig: KeyConfiguration) {
        val selectedKey = injectedKeyRepository.getKeyByKcv(keyConfig.selectedKey)
            ?: throw Exception("Llave con KCV ${keyConfig.selectedKey} no encontrada")

        // Construir el comando de inyección según el protocolo Futurex
        val injectionCommand = buildInjectionCommand(keyConfig, selectedKey)
        
        // Enviar comando
        sendData(injectionCommand)
        
        // Esperar respuesta
        val response = waitForResponse()
        
        // Procesar respuesta
        processInjectionResponse(response, keyConfig)
    }

    private fun buildInjectionCommand(keyConfig: KeyConfiguration, selectedKey: InjectedKeyEntity): ByteArray {
        // Construir comando "02" de Futurex para inyección de llave simétrica
        val command = "02" // Comando de inyección simétrica
        val version = "01" // Versión del comando
        val keySlot = keyConfig.slot.padStart(2, '0') // Slot de la llave
        val ktkSlot = "00" // Slot KTK (por defecto 0)
        val keyType = mapKeyTypeToFuturex(keyConfig.keyType) // Tipo de llave
        val encryptionType = "00" // Carga en claro por ahora
        val keyChecksum = selectedKey.kcv.take(4) // Checksum de la llave
        val ktkChecksum = "0000" // Checksum KTK (no usado en carga en claro)
        val ksn = "00000000000000000000" // KSN (20 caracteres)
        val keyLength = (selectedKey.keyData.length / 2).toString().padStart(3, '0') // Longitud en hex
        val keyHex = selectedKey.keyData // Datos de la llave en hex

        // Para el protocolo Futurex, concatenamos todo en un solo string
        val payload = command + version + keySlot + ktkSlot + keyType + encryptionType + 
                     keyChecksum + ktkChecksum + ksn + keyLength + keyHex

        return messageFormatter!!.format(command, listOf(
            version, keySlot, ktkSlot, keyType, encryptionType, 
            keyChecksum, ktkChecksum, ksn, keyLength, keyHex
        ))
    }

    private fun mapKeyTypeToFuturex(keyType: String): String {
        return when (keyType.uppercase()) {
            "PIN" -> "05" // PIN Encryption Key
            "MAC" -> "04" // MAC Key
            "TDES", "3DES" -> "01" // Master Session Key
            "DUKPT" -> "08" // DUKPT 3DES BDK Key
            "DATA" -> "0C" // Data Encryption Key
            else -> "01" // Default to Master Session Key
        }
    }

    private suspend fun sendData(data: ByteArray) {
        if (comController == null) {
            throw Exception("Controlador de comunicación no inicializado")
        }

        val result = comController!!.write(data, 1000)
        if (result < 0) {
            throw Exception("Error al enviar datos: $result")
        }

        Log.d(TAG, "Datos enviados: ${data.toHexString()}")
    }

    private suspend fun waitForResponse(): ByteArray {
        val buffer = ByteArray(1024)
        val bytesRead = comController!!.readData(buffer.size, buffer, 10000)
        
        if (bytesRead <= 0) {
            throw Exception("Timeout o error al leer respuesta")
        }

        val response = buffer.copyOf(bytesRead)
        Log.d(TAG, "Respuesta recibida: ${response.toHexString()}")
        return response
    }

    private fun processInjectionResponse(response: ByteArray, keyConfig: KeyConfiguration) {
        // Agregar datos al parser
        messageParser!!.appendData(response)
        
        // Intentar parsear la respuesta
        val parsedMessage = messageParser!!.nextMessage()
        
        when (parsedMessage) {
            is InjectSymmetricKeyResponse -> {
                if (parsedMessage.responseCode == "00") {
                    Log.i(TAG, "Inyección exitosa para ${keyConfig.usage}")
                    _state.value = _state.value.copy(
                        log = _state.value.log + "✓ ${keyConfig.usage}: Inyectada exitosamente\n"
                    )
                } else {
                    val errorCode = FuturexErrorCode.fromCode(parsedMessage.responseCode)
                    val errorMsg = errorCode?.description ?: "Error desconocido"
                    throw Exception("Error en inyección de ${keyConfig.usage}: $errorMsg")
                }
            }
            else -> {
                Log.w(TAG, "Respuesta inesperada: ${parsedMessage?.javaClass?.simpleName}")
            }
        }
    }

    private suspend fun closeCommunication() {
        connectionMutex.withLock {
            try {
                comController?.close()
                comController = null
                Log.i(TAG, "Comunicación cerrada")
            } catch (e: Exception) {
                Log.e(TAG, "Error al cerrar la comunicación", e)
            }
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "0x%02X".format(it) }
} 