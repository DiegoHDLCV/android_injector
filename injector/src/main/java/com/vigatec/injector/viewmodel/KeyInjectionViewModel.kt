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
    
    // Instancia del servicio de polling
    private val pollingService = com.example.communication.polling.PollingService()

    init {
        Log.i(TAG, "=== INICIALIZANDO KEYINJECTIONVIEWMODEL FUTUREX ===")
        Log.i(TAG, "Configurando manejadores de protocolo...")
        setupProtocolHandlers()
        
        Log.i(TAG, "Inicializando servicio de polling...")
        // La inicialización de SDKs se centraliza en SplashViewModel mediante SDKInitManager
        // Aquí solo inicializamos PollingService asumiendo SDK ya inicializado
        initializePollingService()
        
        Log.i(TAG, "✓ KeyInjectionViewModel inicializado exitosamente")
        Log.i(TAG, "================================================")
    }
    
    private fun initializePollingService() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "=== INICIALIZANDO SERVICIO DE POLLING FUTUREX ===")
                
                // Inicializar el servicio de polling
                Log.i(TAG, "Inicializando PollingService...")
                pollingService.initialize()
                Log.i(TAG, "✓ PollingService inicializado exitosamente en KeyInjectionViewModel")
                Log.i(TAG, "================================================")
                
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error al inicializar PollingService", e)
            }
        }
    }

    private fun setupProtocolHandlers() {
        Log.i(TAG, "=== CONFIGURANDO MANEJADORES DE PROTOCOLO FUTUREX ===")
        Log.i(TAG, "Protocolo de comunicación seleccionado: ${SystemConfig.commProtocolSelected}")
        
        messageParser = when (SystemConfig.commProtocolSelected) {
            CommProtocol.LEGACY -> {
                Log.i(TAG, "Configurando parser para protocolo LEGACY")
                LegacyMessageParser()
            }
            CommProtocol.FUTUREX -> {
                Log.i(TAG, "Configurando parser para protocolo FUTUREX")
                FuturexMessageParser()
            }
        }
        
        messageFormatter = when (SystemConfig.commProtocolSelected) {
            CommProtocol.LEGACY -> {
                Log.i(TAG, "Configurando formatter para protocolo LEGACY")
                LegacyMessageFormatter
            }
            CommProtocol.FUTUREX -> {
                Log.i(TAG, "Configurando formatter para protocolo FUTUREX")
                FuturexMessageFormatter
            }
        }
        
        Log.i(TAG, "✓ Protocolo de comunicación establecido en: ${SystemConfig.commProtocolSelected}")
        Log.i(TAG, "✓ Parser configurado: ${messageParser?.javaClass?.simpleName}")
        Log.i(TAG, "✓ Formatter configurado: ${messageFormatter?.javaClass?.simpleName}")
        Log.i(TAG, "================================================")
    }

    fun showInjectionModal(profile: ProfileEntity) {
        Log.i(TAG, "=== MOSTRANDO MODAL DE INYECCIÓN FUTUREX ===")
        Log.i(TAG, "Perfil seleccionado: ${profile.name}")
        Log.i(TAG, "Configuraciones de llave: ${profile.keyConfigurations.size}")
        profile.keyConfigurations.forEachIndexed { index, config ->
            Log.i(TAG, "  ${index + 1}. ${config.usage} - Slot: ${config.slot} - Tipo: ${config.keyType}")
        }
        
        _state.value = _state.value.copy(
            showInjectionModal = true,
            currentProfile = profile,
            totalSteps = profile.keyConfigurations.size
        )
        
        Log.i(TAG, "✓ Modal de inyección mostrado")
        Log.i(TAG, "================================================")
    }

    fun hideInjectionModal() {
        Log.i(TAG, "=== OCULTANDO MODAL DE INYECCIÓN FUTUREX ===")
        
        _state.value = _state.value.copy(
            showInjectionModal = false,
            currentProfile = null,
            status = InjectionStatus.IDLE,
            currentStep = 0,
            progress = 0f,
            log = "",
            error = null
        )
        
        Log.i(TAG, "✓ Modal de inyección ocultado")
        Log.i(TAG, "================================================")
    }

    fun startKeyInjection() {
        viewModelScope.launch {
            val profile = _state.value.currentProfile ?: return@launch
            val keyConfigs = profile.keyConfigurations
            
            Log.i(TAG, "=== INICIANDO PROCESO DE INYECCIÓN FUTUREX ===")
            Log.i(TAG, "Perfil: ${profile.name}")
            Log.i(TAG, "Configuraciones de llave: ${keyConfigs.size}")
            keyConfigs.forEachIndexed { index, config ->
                Log.i(TAG, "  ${index + 1}. ${config.usage} - Slot: ${config.slot} - Tipo: ${config.keyType}")
            }
            
            if (keyConfigs.isEmpty()) {
                Log.w(TAG, "El perfil no tiene configuraciones de llaves")
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
                // Verificar si el polling está activo y detenerlo
                if (pollingService.isPollingActive.value) {
                    Log.i(TAG, "Deteniendo polling antes de iniciar inyección...")
                    _state.value = _state.value.copy(
                        log = _state.value.log + "Deteniendo polling para iniciar inyección...\n"
                    )
                    pollingService.stopPolling()
                    
                    // Esperar un momento para asegurar que el puerto esté libre
                    kotlinx.coroutines.delay(1000)
                }
                
                // Inicializar comunicación
                Log.i(TAG, "Inicializando comunicación serial...")
                initializeCommunication()
                
                _state.value = _state.value.copy(
                    status = InjectionStatus.INJECTING,
                    log = _state.value.log + "Conexión establecida. Iniciando inyección...\n"
                )

                // Procesar cada configuración de llave
                for ((index, keyConfig) in keyConfigs.withIndex()) {
                    Log.i(TAG, "=== PROCESANDO LLAVE ${index + 1}/${keyConfigs.size} ===")
                    Log.i(TAG, "Uso: ${keyConfig.usage}")
                    Log.i(TAG, "Slot: ${keyConfig.slot}")
                    Log.i(TAG, "Tipo: ${keyConfig.keyType}")
                    
                    _state.value = _state.value.copy(
                        currentStep = index + 1,
                        progress = (index + 1).toFloat() / keyConfigs.size,
                        log = _state.value.log + "Inyectando llave ${index + 1}/${keyConfigs.size}: ${keyConfig.usage}\n"
                    )

                    injectKey(keyConfig)
                    
                    // Pequeña pausa entre inyecciones
                    kotlinx.coroutines.delay(500)
                }

                Log.i(TAG, "=== INYECCIÓN FUTUREX COMPLETADA EXITOSAMENTE ===")
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
                Log.i(TAG, "Cerrando comunicación...")
                closeCommunication()
                
                // Reiniciar el polling después de la inyección
                viewModelScope.launch {
                    Log.i(TAG, "Reiniciando polling después de la inyección...")
                    _state.value = _state.value.copy(
                        log = _state.value.log + "Reiniciando polling...\n"
                    )
                    kotlinx.coroutines.delay(1000) // Esperar un momento
                    restartPolling()
                }
            }
        }
    }
    
    private fun restartPolling() {
        Log.i(TAG, "=== REINICIANDO POLLING FUTUREX ===")
        Log.i(TAG, "Esta función debe ser llamada desde el DashboardViewModel")
        Log.i(TAG, "para reiniciar el polling después de la inyección")
        Log.i(TAG, "================================================")
    }

    private suspend fun initializeCommunication() {
        connectionMutex.withLock {
            try {
                Log.i(TAG, "=== INICIALIZANDO COMUNICACIÓN SERIAL FUTUREX ===")
                Log.i(TAG, "Protocolo seleccionado: ${SystemConfig.commProtocolSelected}")
                Log.i(TAG, "Parser configurado: ${messageParser?.javaClass?.simpleName}")
                Log.i(TAG, "Formatter configurado: ${messageFormatter?.javaClass?.simpleName}")
                
                // Obtener el controlador de comunicación
                Log.i(TAG, "Obteniendo controlador de comunicación...")
                comController = CommunicationSDKManager.getComController()
                    ?: throw Exception("No se pudo obtener el controlador de comunicación")

                Log.i(TAG, "Controlador obtenido: ${comController!!.javaClass.simpleName}")

                // Configurar y abrir la conexión
                Log.i(TAG, "Configurando comunicación serial:")
                Log.i(TAG, "  - Baud Rate: 115200")
                Log.i(TAG, "  - Paridad: NOPAR")
                Log.i(TAG, "  - Bits de datos: 8")
                
                comController!!.init(
                    EnumCommConfBaudRate.BPS_115200,
                    EnumCommConfParity.NOPAR,
                    EnumCommConfDataBits.DB_8
                )
                
                Log.i(TAG, "Abriendo conexión serial...")
                val openResult = comController!!.open()
                if (openResult != 0) {
                    Log.e(TAG, "Error al abrir la conexión serial: $openResult")
                    throw Exception("Error al abrir la conexión serial: $openResult")
                }

                Log.i(TAG, "✓ Comunicación inicializada exitosamente")
                Log.i(TAG, "================================================")
                
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error al inicializar la comunicación", e)
                throw e
            }
        }
    }

    private suspend fun injectKey(keyConfig: KeyConfiguration) {
        Log.i(TAG, "=== INICIANDO INYECCIÓN DE LLAVE FUTUREX ===")
        Log.i(TAG, "Configuración de llave:")
        Log.i(TAG, "  - Uso: ${keyConfig.usage}")
        Log.i(TAG, "  - Slot: ${keyConfig.slot}")
        Log.i(TAG, "  - Tipo: ${keyConfig.keyType}")
        Log.i(TAG, "  - Llave seleccionada: ${keyConfig.selectedKey}")
        
        val selectedKey = injectedKeyRepository.getKeyByKcv(keyConfig.selectedKey)
            ?: throw Exception("Llave con KCV ${keyConfig.selectedKey} no encontrada")

        Log.i(TAG, "Llave encontrada en base de datos:")
        Log.i(TAG, "  - KCV: ${selectedKey.kcv}")
        Log.i(TAG, "  - Longitud de datos: ${selectedKey.keyData.length / 2} bytes")
        Log.i(TAG, "  - Datos (primeros 32 bytes): ${selectedKey.keyData.take(64)}")

        // Validar integridad de la llave
        validateKeyIntegrity(selectedKey, keyConfig)

        // Mostrar detalles completos de la llave
        logKeyDetails(selectedKey, keyConfig)

        // Construir el comando de inyección según el protocolo Futurex
        Log.i(TAG, "Construyendo comando Futurex...")
        val injectionCommand = buildInjectionCommand(keyConfig, selectedKey)
        
        Log.i(TAG, "Comando construido exitosamente:")
        Log.i(TAG, "  - Tamaño: ${injectionCommand.size} bytes")
        Log.i(TAG, "  - Datos (hex): ${injectionCommand.toHexString()}")
        
        // Enviar comando
        Log.i(TAG, "Enviando comando al dispositivo...")
        sendData(injectionCommand)
        
        // Esperar respuesta
        Log.i(TAG, "Esperando respuesta del dispositivo...")
        val response = waitForResponse()
        
        // Procesar respuesta
        Log.i(TAG, "Procesando respuesta del dispositivo...")
        processInjectionResponse(response, keyConfig)
        
        Log.i(TAG, "=== INYECCIÓN DE LLAVE FUTUREX COMPLETADA ===")
    }

    private fun buildInjectionCommand(keyConfig: KeyConfiguration, selectedKey: InjectedKeyEntity): ByteArray {
        // Validaciones adicionales
        if (selectedKey.keyData.isEmpty()) {
            throw Exception("No se puede construir comando para llave sin datos")
        }

        val keyLengthBytes = selectedKey.keyData.length / 2
        if (keyLengthBytes !in listOf(16, 32, 48)) {
            throw Exception("Longitud de llave inválida: $keyLengthBytes bytes. Debe ser 16, 32 o 48 bytes.")
        }

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
        // CRÍTICO: La longitud debe ser en formato ASCII HEX según documentación Futurex
        // Ejemplo: 16 bytes = "010", 32 bytes = "020", 48 bytes = "030"
        val keyLength = String.format("%03X", keyLengthBytes) // Longitud en ASCII HEX (3 dígitos)
        val keyHex = selectedKey.keyData // Datos de la llave en hex

        // Logs detallados de la estructura Futurex
        Log.i(TAG, "=== ESTRUCTURA FUTUREX PARA INYECCIÓN DE LLAVE ===")
        Log.i(TAG, "Comando: $command (Inyección de llave simétrica)")
        Log.i(TAG, "Versión: $version")
        Log.i(TAG, "Slot de llave: $keySlot (${keyConfig.slot})")
        Log.i(TAG, "Slot KTK: $ktkSlot")
        Log.i(TAG, "Tipo de llave: $keyType (${keyConfig.keyType})")
        Log.i(TAG, "Tipo de encriptación: $encryptionType (Carga en claro)")
        Log.i(TAG, "Checksum de llave: $keyChecksum (KCV: ${selectedKey.kcv})")
        Log.i(TAG, "Checksum KTK: $ktkChecksum")
        Log.i(TAG, "KSN: $ksn (20 caracteres)")
        Log.i(TAG, "Longitud de llave: $keyLength ($keyLengthBytes bytes)")
        Log.i(TAG, "  - Formato: ASCII HEX (3 dígitos)")
        Log.i(TAG, "  - Valor: '$keyLength'")
        Log.i(TAG, "  - Validación: ${if (keyLength.length == 3 && keyLength.all { it.isLetterOrDigit() }) "✓ Válido" else "✗ Inválido"}")
        Log.i(TAG, "Datos de llave (hex): ${keyHex.take(64)}${if (keyHex.length > 64) "..." else ""}")
        Log.i(TAG, "Longitud total del payload: ${command.length + version.length + keySlot.length + ktkSlot.length + keyType.length + encryptionType.length + keyChecksum.length + ktkChecksum.length + ksn.length + keyLength.length + keyHex.length} caracteres")
        
        // Log de mapeo de tipo de llave
        Log.i(TAG, "=== MAPEO DE TIPO DE LLAVE FUTUREX ===")
        Log.i(TAG, "Tipo original: ${keyConfig.keyType}")
        Log.i(TAG, "Tipo mapeado: $keyType")
        Log.i(TAG, "Descripción: ${getKeyTypeDescription(keyType)}")

        // Validar que la longitud esté en formato ASCII HEX correcto
        if (keyLength.length != 3 || !keyLength.all { it.isLetterOrDigit() }) {
            throw Exception("Formato de longitud inválido: '$keyLength'. Debe ser 3 caracteres ASCII HEX.")
        }
        
        // Para el protocolo Futurex, concatenamos todo en un solo string
        val payload = command + version + keySlot + ktkSlot + keyType + encryptionType + 
                     keyChecksum + ktkChecksum + ksn + keyLength + keyHex

        Log.i(TAG, "=== PAYLOAD FINAL FUTUREX ===")
        Log.i(TAG, "Payload completo: $payload")
        Log.i(TAG, "Validación del payload:")
        Log.i(TAG, "  - Comando: $command")
        Log.i(TAG, "  - Versión: $version")
        Log.i(TAG, "  - Slot: $keySlot")
        Log.i(TAG, "  - KTK Slot: $ktkSlot")
        Log.i(TAG, "  - Tipo: $keyType")
        Log.i(TAG, "  - Encriptación: $encryptionType")
        Log.i(TAG, "  - Checksum: $keyChecksum")
        Log.i(TAG, "  - KTK Checksum: $ktkChecksum")
        Log.i(TAG, "  - KSN: $ksn")
        Log.i(TAG, "  - Longitud: $keyLength (${keyLengthBytes} bytes)")
        Log.i(TAG, "  - Datos: ${keyHex.take(32)}...")
        Log.i(TAG, "================================================")

        // Construir el payload manualmente para Futurex
        val payloadString = command + version + keySlot + ktkSlot + keyType + encryptionType + 
                           keyChecksum + ktkChecksum + ksn + keyLength + keyHex
        
        Log.i(TAG, "=== PAYLOAD MANUAL FUTUREX ===")
        Log.i(TAG, "Payload string: $payloadString")
        Log.i(TAG, "Longitud payload: ${payloadString.length} caracteres")
        Log.i(TAG, "================================================")
        
        // DEBUG: Verificar cálculo del LRC
        val fields = listOf(version, keySlot, ktkSlot, keyType, encryptionType, 
                           keyChecksum, ktkChecksum, ksn, keyLength, keyHex)
        debugLrcCalculation(command, fields)
        
        // Usar el formateador Futurex directamente
        val formattedData = messageFormatter!!.format(command, fields)
        
        Log.i(TAG, "=== DATOS FORMATEADOS FUTUREX ===")
        Log.i(TAG, "Tamaño total: ${formattedData.size} bytes")
        Log.i(TAG, "STX: 0x${formattedData[0].toString(16).uppercase()}")
        Log.i(TAG, "Payload: ${formattedData.sliceArray(1 until formattedData.size - 2).toString(Charsets.US_ASCII)}")
        Log.i(TAG, "ETX: 0x${formattedData[formattedData.size - 2].toString(16).uppercase()}")
        Log.i(TAG, "LRC: 0x${formattedData[formattedData.size - 1].toString(16).uppercase()}")
        Log.i(TAG, "================================================")
        
        return formattedData
    }

    /**
     * MÉTODO DE DEBUG: Verifica el cálculo del LRC para un comando Futurex
     */
    private fun debugLrcCalculation(command: String, fields: List<String>) {
        Log.i(TAG, "=== DEBUG LRC CALCULATION ===")
        
        // Construir payload manualmente
        val payloadString = command + fields.joinToString("")
        val payloadBytes = payloadString.toByteArray(Charsets.US_ASCII)
        val etxByte = byteArrayOf(0x03)
        val bytesForLrc = payloadBytes + etxByte
        
        Log.i(TAG, "Payload string: $payloadString")
        Log.i(TAG, "Payload bytes: ${payloadBytes.toHexString()}")
        Log.i(TAG, "Bytes para LRC: ${bytesForLrc.toHexString()}")
        
        // Calcular LRC manualmente
        var lrc: Byte = 0
        for (byte in bytesForLrc) {
            lrc = (lrc.toInt() xor byte.toInt()).toByte()
        }
        
        Log.i(TAG, "LRC calculado manualmente: 0x${lrc.toString(16).uppercase()}")
        Log.i(TAG, "================================================")
    }

    private fun mapKeyTypeToFuturex(keyType: String): String {
        Log.d(TAG, "Mapeando tipo de llave: '$keyType' -> Futurex")
        
        val mappedType = when (keyType.uppercase()) {
            "PIN" -> "05" // PIN Encryption Key
            "MAC" -> "04" // MAC Key
            "TDES", "3DES" -> "01" // Master Session Key
            "DUKPT" -> "08" // DUKPT 3DES BDK Key
            "DATA" -> "0C" // Data Encryption Key
            else -> "01" // Default to Master Session Key
        }
        
        Log.d(TAG, "Tipo mapeado: '$keyType' -> '$mappedType'")
        return mappedType
    }

    private fun getKeyTypeDescription(keyType: String): String {
        return when (keyType) {
            "01" -> "Master Session Key (TDES/3DES)"
            "02" -> "PIN Encryption Key"
            "03" -> "MAC Key"
            "04" -> "MAC Key (alternativo)"
            "05" -> "PIN Encryption Key (alternativo)"
            "08" -> "DUKPT 3DES BDK Key"
            "0B" -> "DUKPT AES BDK Key"
            "0C" -> "Data Encryption Key"
            "10" -> "DUKPT AES Session Key"
            else -> "Tipo desconocido"
        }
    }

    private suspend fun sendData(data: ByteArray) {
        if (comController == null) {
            throw Exception("Controlador de comunicación no inicializado")
        }

        Log.i(TAG, "=== ENVIANDO DATOS FUTUREX ===")
        Log.i(TAG, "Tamaño de datos: ${data.size} bytes")
        Log.i(TAG, "Datos en hexadecimal: ${data.toHexString()}")
        Log.i(TAG, "Datos en ASCII: ${String(data, Charsets.US_ASCII)}")
        
        val result = comController!!.write(data, 1000)
        if (result < 0) {
            Log.e(TAG, "Error al enviar datos: $result")
            throw Exception("Error al enviar datos: $result")
        }

        Log.i(TAG, "Datos enviados exitosamente: ${result} bytes escritos")
        Log.i(TAG, "================================================")
    }

    private suspend fun waitForResponse(): ByteArray {
        Log.i(TAG, "=== ESPERANDO RESPUESTA FUTUREX ===")
        Log.i(TAG, "Timeout configurado: 10000ms")
        
        val buffer = ByteArray(1024)
        val bytesRead = comController!!.readData(buffer.size, buffer, 10000)
        
        if (bytesRead <= 0) {
            Log.e(TAG, "Timeout o error al leer respuesta: $bytesRead bytes leídos")
            throw Exception("Timeout o error al leer respuesta")
        }

        val response = buffer.copyOf(bytesRead)
        Log.i(TAG, "Respuesta recibida exitosamente:")
        Log.i(TAG, "  - Bytes leídos: $bytesRead")
        Log.i(TAG, "  - Datos en hexadecimal: ${response.toHexString()}")
        Log.i(TAG, "  - Datos en ASCII: ${String(response, Charsets.US_ASCII)}")
        Log.i(TAG, "================================================")
        
        return response
    }

    private fun processInjectionResponse(response: ByteArray, keyConfig: KeyConfiguration) {
        Log.i(TAG, "=== PROCESANDO RESPUESTA FUTUREX ===")
        Log.i(TAG, "Configuración de llave: ${keyConfig.usage} (Slot: ${keyConfig.slot})")
        Log.i(TAG, "Respuesta recibida: ${response.size} bytes")
        
        // Agregar datos al parser
        Log.i(TAG, "Agregando datos al parser Futurex...")
        messageParser!!.appendData(response)
        
        // Intentar parsear la respuesta
        Log.i(TAG, "Parseando respuesta del dispositivo...")
        val parsedMessage = messageParser!!.nextMessage()
        
        when (parsedMessage) {
            is InjectSymmetricKeyResponse -> {
                Log.i(TAG, "Respuesta parseada como InjectSymmetricKeyResponse:")
                Log.i(TAG, "  - Código de respuesta: ${parsedMessage.responseCode}")
                Log.i(TAG, "  - Checksum de llave: ${parsedMessage.keyChecksum}")
                Log.i(TAG, "  - Payload completo: ${parsedMessage.rawPayload}")
                
                if (parsedMessage.responseCode == "00") {
                    Log.i(TAG, "✓ Inyección exitosa para ${keyConfig.usage}")
                    _state.value = _state.value.copy(
                        log = _state.value.log + "✓ ${keyConfig.usage}: Inyectada exitosamente\n"
                    )
                } else {
                    val errorCode = FuturexErrorCode.fromCode(parsedMessage.responseCode)
                    val errorMsg = errorCode?.description ?: "Error desconocido"
                    Log.e(TAG, "✗ Error en inyección de ${keyConfig.usage}: $errorMsg (Código: ${parsedMessage.responseCode})")
                    throw Exception("Error en inyección de ${keyConfig.usage}: $errorMsg")
                }
            }
            else -> {
                Log.w(TAG, "Respuesta inesperada: ${parsedMessage?.javaClass?.simpleName}")
                if (parsedMessage != null) {
                    // Intentar acceder a rawPayload de forma segura
                    val payload = try {
                        (parsedMessage as? FuturexMessage)?.rawPayload ?: "No disponible"
                    } catch (e: Exception) {
                        "Error al acceder: ${e.message}"
                    }
                    Log.w(TAG, "Payload: $payload")
                } else {
                    Log.w(TAG, "Payload: N/A (mensaje null)")
                }
            }
        }
        
        Log.i(TAG, "================================================")
    }

    private suspend fun closeCommunication() {
        connectionMutex.withLock {
            try {
                Log.i(TAG, "=== CERRANDO COMUNICACIÓN SERIAL FUTUREX ===")
                
                if (comController != null) {
                    Log.i(TAG, "Cerrando controlador de comunicación...")
                    comController!!.close()
                    comController = null
                    Log.i(TAG, "✓ Comunicación cerrada exitosamente")
                } else {
                    Log.i(TAG, "Controlador de comunicación ya estaba cerrado")
                }
                
                Log.i(TAG, "================================================")
                
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error al cerrar la comunicación", e)
            }
        }
    }

    private fun ByteArray.toHexString(): String {
        val hexString = joinToString(" ") { "0x%02X".format(it) }
        Log.v(TAG, "Conversión a hexadecimal: ${this.size} bytes -> '$hexString'")
        return hexString
    }

    private fun validateKeyIntegrity(selectedKey: InjectedKeyEntity, keyConfig: KeyConfiguration) {
        Log.i(TAG, "=== VALIDANDO INTEGRIDAD DE LLAVE FUTUREX ===")
        
        // Verificar que la llave tenga datos
        if (selectedKey.keyData.isEmpty()) {
            throw Exception("La llave con KCV ${selectedKey.kcv} no tiene datos")
        }

        // Verificar que el KCV no esté vacío
        if (selectedKey.kcv.isEmpty()) {
            throw Exception("La llave no tiene KCV válido")
        }

        // Verificar longitud de la llave
        val keyLengthBytes = selectedKey.keyData.length / 2
        val validLengths = when (keyConfig.keyType.uppercase()) {
            "TDES", "3DES" -> listOf(16, 32, 48) // 128, 256, 384 bits
            "AES" -> listOf(16, 24, 32) // 128, 192, 256 bits
            "DUKPT" -> listOf(16, 32, 48) // 128, 256, 384 bits
            else -> listOf(16, 32, 48) // Longitudes por defecto
        }

        if (keyLengthBytes !in validLengths) {
            throw Exception("Longitud de llave inválida para ${keyConfig.keyType}: $keyLengthBytes bytes. Válidas: $validLengths")
        }

        // Verificar que keyData sea hexadecimal válido
        if (!selectedKey.keyData.matches(Regex("^[0-9A-Fa-f]+$"))) {
            throw Exception("Los datos de la llave no son hexadecimales válidos")
        }

        // Verificar que el KCV sea hexadecimal válido
        if (!selectedKey.kcv.matches(Regex("^[0-9A-Fa-f]+$"))) {
            throw Exception("El KCV no es hexadecimal válido")
        }

        Log.i(TAG, "✓ Integridad de llave validada:")
        Log.i(TAG, "  - KCV: ${selectedKey.kcv}")
        Log.i(TAG, "  - Longitud: $keyLengthBytes bytes")
        Log.i(TAG, "  - Tipo: ${keyConfig.keyType}")
        Log.i(TAG, "  - Datos válidos: Sí")
        Log.i(TAG, "================================================")
    }

    private fun logKeyDetails(selectedKey: InjectedKeyEntity, keyConfig: KeyConfiguration) {
        Log.i(TAG, "=== DETALLES DE LLAVE FUTUREX ===")
        Log.i(TAG, "Información de la llave:")
        Log.i(TAG, "  - KCV: ${selectedKey.kcv}")
        Log.i(TAG, "  - Longitud en caracteres hex: ${selectedKey.keyData.length}")
        Log.i(TAG, "  - Longitud en bytes: ${selectedKey.keyData.length / 2}")
        Log.i(TAG, "  - Datos completos: ${selectedKey.keyData}")
        
        // Mostrar datos en formato legible
        val keyBytes = selectedKey.keyData.chunked(2)
        Log.i(TAG, "  - Datos por bytes:")
        keyBytes.forEachIndexed { index, byte ->
            if (index % 8 == 0) Log.i(TAG, "    ${index.toString().padStart(2, '0')}: $byte")
            else Log.i(TAG, "        $byte")
        }
        
        Log.i(TAG, "Configuración de inyección:")
        Log.i(TAG, "  - Uso: ${keyConfig.usage}")
        Log.i(TAG, "  - Slot: ${keyConfig.slot}")
        Log.i(TAG, "  - Tipo: ${keyConfig.keyType}")
        Log.i(TAG, "================================================")
    }
} 