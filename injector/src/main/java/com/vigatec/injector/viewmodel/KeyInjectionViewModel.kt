@file:Suppress("DEPRECATION")

package com.vigatec.injector.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.communication.base.EnumCommConfBaudRate
import com.vigatec.communication.base.EnumCommConfDataBits
import com.vigatec.communication.base.EnumCommConfParity
import com.vigatec.communication.base.IComController
import com.vigatec.communication.libraries.CommunicationSDKManager
import com.vigatec.config.CommProtocol
import com.vigatec.config.SystemConfig
import com.vigatec.format.*
import com.vigatec.format.base.IMessageFormatter
import com.vigatec.format.base.IMessageParser
import com.vigatec.persistence.entities.InjectedKeyEntity
import com.vigatec.persistence.entities.KeyConfiguration
import com.vigatec.persistence.entities.ProfileEntity
import com.vigatec.persistence.repository.InjectedKeyRepository
import com.vigatec.persistence.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

enum class InjectionStatus {
    IDLE,
    CONNECTING,
    INJECTING,
    SUCCESS,
    ERROR,
    @Suppress("UNUSED") COMPLETED
}

data class KeyInjectionState(
    val status: InjectionStatus = InjectionStatus.IDLE,
    val currentProfile: ProfileEntity? = null,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val progress: Float = 0f,
    val log: String = "",
    val error: String? = null,
    val showInjectionModal: Boolean = false,
    val cableConnected: Boolean = false  // Nuevo: estado de conexión de cable
)

@HiltViewModel
class KeyInjectionViewModel @Inject constructor(
    @Suppress("UNUSED") private val profileRepository: ProfileRepository,
    private val injectedKeyRepository: InjectedKeyRepository,
    private val kekManager: com.vigatec.injector.manager.KEKManager,
    private val injectionLogger: com.vigatec.injector.util.InjectionLogger,
    @Suppress("UNUSED") private val application: android.app.Application
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
    private val pollingService = com.vigatec.communication.polling.PollingService()
    
    // Username del usuario actual para los logs
    private var currentUsername: String = "system"

    // Detección de cable USB
    private var cableDetectionJob: kotlinx.coroutines.Job? = null

    init {
        Log.d(TAG, "Inicializando KeyInjectionViewModel")
        setupProtocolHandlers()
        initializePollingService()
    }
    
    private fun initializePollingService() {
        viewModelScope.launch {
            try {
                pollingService.initialize()
                Log.d(TAG, "✓ PollingService inicializado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al inicializar PollingService", e)
            }
        }
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

        Log.d(TAG, "Protocolo: ${SystemConfig.commProtocolSelected}")
    }

    fun showInjectionModal(profile: ProfileEntity, username: String = "system") {
        Log.i(TAG, "=== MOSTRANDO MODAL DE INYECCIÓN FUTUREX ===")
        Log.i(TAG, "Perfil seleccionado: ${profile.name}")
        Log.i(TAG, "Usuario: $username")
        Log.i(TAG, "Configuraciones de llave: ${profile.keyConfigurations.size}")
        profile.keyConfigurations.forEachIndexed { index, config ->
            Log.i(TAG, "  ${index + 1}. ${config.usage} - Slot: ${config.slot} - Tipo: ${config.keyType}")
        }

        currentUsername = username

        _state.value = _state.value.copy(
            showInjectionModal = true,
            currentProfile = profile,
            totalSteps = profile.keyConfigurations.size
        )

        // Iniciar monitoreo de cable
        startCableDetection()

        Log.i(TAG, "✓ Modal de inyección mostrado")
        Log.i(TAG, "================================================")
    }
    
    @Suppress("UNUSED")
    fun setCurrentUsername(username: String) {
        currentUsername = username
        Log.i(TAG, "Usuario establecido para logs: $username")
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
            Log.i(TAG, "¿Usa KTK?: ${profile.useKTK}")
            if (profile.useKTK) {
                Log.i(TAG, "KTK seleccionada (KCV): ${profile.selectedKTKKcv}")
            }
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

                // === PASO 1: SIEMPRE INYECTAR KTK SELECCIONADA ===
                if (profile.useKTK && profile.selectedKTKKcv.isNotEmpty()) {
                    Log.i(TAG, "=== INYECTANDO KTK SELECCIONADA ===")
                    _state.value = _state.value.copy(
                        log = _state.value.log + "Inyectando KTK seleccionada para cifrado...\n"
                    )

                    // Obtener la KTK de la base de datos
                    val kek = injectedKeyRepository.getKeyByKcv(profile.selectedKTKKcv)

                    if (kek == null) {
                        Log.e(TAG, "KTK no encontrada con KCV: ${profile.selectedKTKKcv}")
                        throw Exception("KTK seleccionada no encontrada en la base de datos")
                    }

                    // NUEVA VALIDACIÓN: Verificar que coincida con la KTK activa
                    val activeKTK = kekManager.getActiveKEKEntity()
                    if (activeKTK != null && activeKTK.kcv != kek.kcv) {
                        val errorMsg = """
                            Inconsistencia detectada:
                            - KEK seleccionada en perfil: ${kek.kcv} (${kek.customName})
                            - KTK activa en el sistema: ${activeKTK.kcv} (${activeKTK.customName})
                            
                            La KEK del perfil debe coincidir con la KTK activa.
                            Por favor, selecciona la KTK correcta en el perfil o activa la KEK apropiada.
                        """.trimIndent()
                        
                        Log.e(TAG, errorMsg)
                        throw Exception(errorMsg)
                    }

                    Log.i(TAG, "KTK encontrada:")
                    Log.i(TAG, "  - KCV: ${kek.kcv}")
                    Log.i(TAG, "  - Nombre: ${kek.customName.ifEmpty { "Sin nombre" }}")
                    Log.i(TAG, "  - Estado: ${kek.status}")
                    Log.i(TAG, "  - Es KEK: ${kek.isKEK}")

                    // SIEMPRE inyectar la KTK seleccionada (sin verificar estado exported)
                    Log.i(TAG, "Inyectando KTK al SubPOS (siempre requerida)...")
                    _state.value = _state.value.copy(
                        log = _state.value.log + "Inyectando KTK al SubPOS (requerida para cada inyección)...\n"
                    )

                    // Exportar la KEK (slot fijo 00 para KEK, en claro)
                    try {
                        exportKEKToDevice(kek)

                        Log.i(TAG, "✓ KTK inyectada exitosamente")
                        _state.value = _state.value.copy(
                            log = _state.value.log + "✓ KTK inyectada exitosamente al slot 00\n"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al inyectar KTK", e)
                        throw Exception("Error al inyectar KTK: ${e.message}")
                    }

                    Log.i(TAG, "================================================")
                } else {
                    Log.i(TAG, "Perfil no usa KEK - las llaves se enviarán en claro")
                    _state.value = _state.value.copy(
                        log = _state.value.log + "Perfil sin KEK - enviando llaves en claro\n"
                    )
                }

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
                Log.i(TAG, "Perfil inyectado: ${profile.name}")
                Log.i(TAG, "Usuario: $currentUsername")
                Log.i(TAG, "Total de llaves inyectadas: ${profile.keyConfigurations.size}")
                Log.i(TAG, "✓ Todos los logs de inyección han sido registrados en la base de datos")
                Log.i(TAG, "El Dashboard debería actualizar el contador automáticamente")
                
                _state.value = _state.value.copy(
                    status = InjectionStatus.SUCCESS,
                    log = _state.value.log + "¡Inyección completada exitosamente!\n"
                )
                
                _snackbarEvent.emit("Inyección de llaves completada")

            } catch (e: Exception) {
                Log.e(TAG, "Error durante la inyección de llaves", e)

                // Registrar el error en los logs de inyección
                try {
                    injectionLogger.logError(
                        commandSent = "N/A - Error antes de enviar comando",
                        responseReceived = "N/A - Error en validación o preparación",
                        username = currentUsername,
                        profileName = profile.name,
                        keyType = "N/A",
                        keySlot = -1,
                        notes = "Error durante la inyección: ${e.message}\nStackTrace: ${e.stackTraceToString().take(500)}"
                    )
                    Log.i(TAG, "✓ Error registrado en logs de inyección")
                } catch (logError: Exception) {
                    Log.e(TAG, "Error al registrar log de fallo", logError)
                }

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

        try {
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

            // Procesar respuesta y registrar en logs
            Log.i(TAG, "Procesando respuesta del dispositivo...")
            processInjectionResponse(response, keyConfig, injectionCommand)

            Log.i(TAG, "=== INYECCIÓN DE LLAVE FUTUREX COMPLETADA ===")

        } catch (e: Exception) {
            Log.e(TAG, "Error durante inyección de llave individual", e)

            // Registrar el error específico en los logs de inyección
            try {
                injectionLogger.logError(
                    commandSent = "Error antes de enviar comando",
                    responseReceived = "Error en validación",
                    username = currentUsername,
                    profileName = _state.value.currentProfile?.name ?: "Desconocido",
                    keyType = keyConfig.keyType,
                    keySlot = keyConfig.slot.toIntOrNull() ?: -1,
                    notes = """
                        Error al inyectar ${keyConfig.usage}:
                        - Slot: ${keyConfig.slot}
                        - Tipo: ${keyConfig.keyType}
                        - KCV seleccionada: ${keyConfig.selectedKey}
                        - Error: ${e.message}
                        - Causa: ${e.cause?.message ?: "N/A"}
                    """.trimIndent()
                )
                Log.i(TAG, "✓ Error específico registrado en logs de inyección")
            } catch (logError: Exception) {
                Log.e(TAG, "Error al registrar log específico de fallo", logError)
            }

            // Re-lanzar la excepción para que sea capturada por el catch principal
            throw e
        }
    }

    private fun buildInjectionCommand(keyConfig: KeyConfiguration, selectedKey: InjectedKeyEntity): ByteArray {
        // Validaciones adicionales
        if (selectedKey.keyData.isEmpty()) {
            throw Exception("No se puede construir comando para llave sin datos")
        }

        val keyLengthBytes = selectedKey.keyData.length / 2
        // Validación de longitud: Soportar 3DES (8, 16, 24 bytes) y AES (16, 24, 32, 48 bytes)
        val validLengths = listOf(8, 16, 24, 32, 48)
        if (keyLengthBytes !in validLengths) {
            throw Exception("Longitud de llave inválida: $keyLengthBytes bytes. Longitudes válidas: $validLengths")
        }

        // === DETECTAR SI ES DUKPT PLAINTEXT (EncryptionType 05) ===
        val isDukptPlaintext = isDukptPlaintextKey(keyConfig, selectedKey)

        // === INTEGRACIÓN CON KTK (CONDICIONAL SEGÚN TIPO DE LLAVE) ===
        Log.i(TAG, "=== VERIFICANDO KTK PARA INYECCIÓN ===")
        val hasActiveKTK = kotlinx.coroutines.runBlocking { kekManager.hasActiveKEK() }
        val ktkData = kotlinx.coroutines.runBlocking { kekManager.getActiveKEKData() }
        val ktkKcv = kotlinx.coroutines.runBlocking { kekManager.getActiveKEKKcv() }
        var ktkSlot = kotlinx.coroutines.runBlocking { kekManager.getActiveKEKSlot() } ?: 0

        // CRÍTICO: Validar que el slot de KTK sea válido (>= 0)
        // Si es negativo, establecer como 0 por defecto
        if (ktkSlot < 0) {
            Log.w(TAG, "⚠️ Slot de KTK inválido: $ktkSlot. Usando slot 0 por defecto.")
            ktkSlot = 0
        }

        Log.i(TAG, "¿Hay KTK activa?: $hasActiveKTK")
        if (hasActiveKTK) {
            Log.i(TAG, "  - KCV de KTK: $ktkKcv")
            Log.i(TAG, "  - Slot de KTK: $ktkSlot (validado)")
        }

        // VALIDACIÓN CONDICIONAL DE KTK:
        // - DUKPT Plaintext (EncryptionType 05): NO requiere KTK (se envía sin cifrar)
        // - Otras llaves: SÍ requieren KTK (se envían cifradas con EncryptionType 02)
        if (isDukptPlaintext) {
            Log.i(TAG, "✓ Modo DUKPT Plaintext (EncryptionType 05) - KTK NO requerida")
            Log.w(TAG, "⚠️ ADVERTENCIA: IPEK se enviará en plaintext. SOLO para testing, NO usar en producción")
        } else {
            if (!hasActiveKTK || ktkData == null || ktkKcv == null) {
                throw Exception("KTK (Key Transfer Key) es obligatoria para inyectar llaves cifradas. Para DUKPT plaintext (testing), asegúrate que sea una llave DUKPT sin cifrado.")
            }
        }

        // Decidir tipo de encriptación y datos de llave
        val encryptionType: String
        val finalKeyData: String
        val ktkSlotStr: String
        val ktkChecksum: String
        var ktkAlgorithm: String // Algoritmo de la KTK (vacío para plaintext)

        if (isDukptPlaintext) {
            // === MODO DUKPT PLAINTEXT (EncryptionType 05) ===
            // La IPEK se envía sin cifrar, directamente en claro
            Log.i(TAG, "=== MODO DUKPT PLAINTEXT (EncryptionType 05) ===")
            Log.i(TAG, "Enviando IPEK DUKPT sin cifrado...")

            // Usar la llave tal cual, sin cifrar
            finalKeyData = selectedKey.keyData

            encryptionType = "05" // DUKPT Plaintext (sin cifrado)
            ktkSlotStr = "00"      // No se usa KTK en plaintext
            ktkChecksum = "0000"   // No se usa checksum de KTK
            // ktkAlgorithm no se usa en modo DUKPT plaintext

            Log.i(TAG, "✓ IPEK DUKPT lista para enviar en plaintext")
            Log.i(TAG, "  - Datos (primeros 32): ${selectedKey.keyData.take(32)}...")
            Log.i(TAG, "  - Tipo de encriptación: $encryptionType (Sin cifrado - TESTING ONLY)")
            Log.i(TAG, "  - Slot de KTK: No usado")
            Log.i(TAG, "  - Checksum de KTK: No usado")
            Log.w(TAG, "  - ⚠️ ADVERTENCIA: IPEK enviada en plaintext. NO usar en producción")
        } else {
            // === MODO CIFRADO CON KTK (EncryptionType 02) ===
            // Usar KTK para cifrar la llave
            // La KTK ya fue enviada previamente con encryptionType "00" (en claro)
            // Ahora enviamos la llave cifrada con encryptionType "02"
            Log.i(TAG, "=== MODO CIFRADO CON KTK (YA INYECTADA) ===")
            Log.i(TAG, "Cifrando llave con KTK antes de enviar...")

            // En este punto, sabemos que ktkData y ktkKcv no son null (validados arriba)
            val safeKtkData = ktkData!! // Garantizado no-null por la validación anterior
            val safeKtkKcv = ktkKcv!!   // Garantizado no-null por la validación anterior

            // Detectar algoritmo de la KTK basado en su longitud
            val ktkLengthBytes = safeKtkData.length / 2
            ktkAlgorithm = when (ktkLengthBytes) {
                8 -> "3DES (Single DES - 8 bytes)"
                16 -> "3DES (Double length - 16 bytes) o AES-128"
                24 -> "3DES (Triple length - 24 bytes)"
                32 -> "AES-256"
                48 -> "AES-256 (Triple length - 48 bytes)"
                else -> "Desconocido ($ktkLengthBytes bytes)"
            }

            // Detectar algoritmo de la llave operacional (usando variable existente)
            val operationalKeyAlgorithm = when (keyLengthBytes) {
                8 -> "3DES (Single DES - 8 bytes)"
                16 -> "3DES (Double length - 16 bytes) o AES-128"
                24 -> "3DES (Triple length - 24 bytes)"
                32 -> "AES-256"
                48 -> "AES-256 (Triple length - 48 bytes)"
                else -> "Desconocido ($keyLengthBytes bytes)"
            }

            Log.i(TAG, "=== INFORMACIÓN DE KTK ===")
            Log.i(TAG, "  - Algoritmo KTK: $ktkAlgorithm")
            Log.i(TAG, "  - Longitud KTK: $ktkLengthBytes bytes (${safeKtkData.length} caracteres hex)")
            Log.i(TAG, "  - KCV de KTK: $safeKtkKcv")
            Log.i(TAG, "  - Primeros 32 caracteres: ${safeKtkData.take(32)}...")
            Log.i(TAG, "")
            Log.i(TAG, "=== INFORMACIÓN DE LLAVE OPERACIONAL ===")
            Log.i(TAG, "  - Algoritmo llave: $operationalKeyAlgorithm")
            Log.i(TAG, "  - Longitud llave: $keyLengthBytes bytes (${selectedKey.keyData.length} caracteres hex)")
            Log.i(TAG, "  - KCV de llave: ${selectedKey.kcv}")
            Log.i(TAG, "  - Tipo de llave: ${keyConfig.keyType}")
            Log.i(TAG, "  - Slot destino: ${keyConfig.slot}")
            Log.i(TAG, "  - Primeros 32 caracteres: ${selectedKey.keyData.take(32)}...")
            Log.i(TAG, "")
            Log.i(TAG, "=== INICIANDO CIFRADO ===")
            Log.i(TAG, "Llamando a TripleDESCrypto.encryptKeyForTransmission()...")

            try {
                // Cifrar la llave con la KTK usando TripleDESCrypto
                finalKeyData = com.vigatec.utils.TripleDESCrypto.encryptKeyForTransmission(
                    keyData = selectedKey.keyData,
                    kekData = safeKtkData,
                    keyKcv = selectedKey.kcv
                )

                encryptionType = "02" // Llave cifrada (KTK ya fue inyectada previamente)
                ktkSlotStr = ktkSlot.toString().padStart(2, '0') // Slot donde está la KTK
                ktkChecksum = safeKtkKcv.take(4)

                Log.i(TAG, "✓ Llave cifrada exitosamente")
                Log.i(TAG, "  - Datos originales (primeros 32): ${selectedKey.keyData.take(32)}...")
                Log.i(TAG, "  - Datos cifrados (primeros 32): ${finalKeyData.take(32)}...")
                Log.i(TAG, "  - Tipo de encriptación: $encryptionType (Cifrada con KTK en slot $ktkSlotStr)")
                Log.i(TAG, "  - Slot de KTK: $ktkSlotStr")
                Log.i(TAG, "  - Checksum de KTK: $ktkChecksum")
                Log.i(TAG, "  - IMPORTANTE: La KTK ya debe estar inyectada en el dispositivo")

            } catch (e: Exception) {
                Log.e(TAG, "✗ Error al cifrar llave con KTK: ${e.message}", e)
                throw Exception("Error al cifrar llave con KTK: ${e.message}")
            }
        }

        // NUEVO: Recalcular longitud basándose en datos cifrados
        val actualKeyLengthBytes = finalKeyData.length / 2
        Log.i(TAG, "✓ Llave cifrada exitosamente")
        Log.i(TAG, "  - Longitud original: $keyLengthBytes bytes")
        Log.i(TAG, "  - Longitud cifrada (con padding): $actualKeyLengthBytes bytes")
        
        Log.i(TAG, "=== ANÁLISIS DE LONGITUDES ===")
        Log.i(TAG, "  - Llave original: $keyLengthBytes bytes (${keyLengthBytes * 2} caracteres hex)")
        Log.i(TAG, "  - Llave cifrada: ${finalKeyData.length / 2} bytes (${finalKeyData.length} caracteres hex)")
        Log.i(TAG, "  - Padding aplicado: ${(finalKeyData.length / 2) - keyLengthBytes} bytes")
        Log.i(TAG, "  - KeyLength que se enviará: ${String.format("%03X", finalKeyData.length / 2)} (${finalKeyData.length / 2} bytes)")

        // Detectar algoritmo basado en la información almacenada en la base de datos
        val keyAlgorithm = detectKeyAlgorithmFromEntity(selectedKey, keyLengthBytes, keyConfig.keyType)
        
        // Detectar subtipo de llave basado en el tipo
        val keySubType = detectKeySubType(keyConfig.keyType)

        // Construir comando "02" de Futurex para inyección de llave simétrica
        val command = "02" // Comando de inyección simétrica
        val version = "01" // Versión del comando
        val keySlot = keyConfig.slot.padStart(2, '0') // Slot de la llave
        val keyType = mapKeyTypeToFuturex(keyConfig.keyType) // Tipo de llave
        val keyChecksum = selectedKey.kcv.take(4) // Checksum de la llave (ORIGINAL, no cambia)
        
        // KSN: Para llaves DUKPT usar KSN del perfil o generar uno, para otras llaves usar zeros
        val ksn = if (isDukptKeyType(keyType)) {
            if (keyConfig.ksn.isNotEmpty() && keyConfig.ksn.length == 20) {
                Log.i(TAG, "Usando KSN proporcionado en el perfil: ${keyConfig.ksn}")
                keyConfig.ksn.uppercase()
            } else {
                Log.i(TAG, "KSN no válido en perfil, generando automáticamente...")
                generateKsn(keyConfig, selectedKey) // Generar KSN para llaves DUKPT
            }
        } else {
            "00000000000000000000" // KSN por defecto para llaves no-DUKPT
        }
        
        // CRÍTICO: La longitud debe ser en formato ASCII HEX según documentación Futurex
        // Ejemplo: 16 bytes = "010", 32 bytes = "020", 48 bytes = "030"
        // IMPORTANTE: Usar la longitud ACTUAL (cifrada) en lugar de la original
        val keyLengthForProtocol = finalKeyData.length / 2
        val keyLength = String.format("%03X", keyLengthForProtocol) // Longitud en ASCII HEX (3 dígitos)''
        // IMPORTANTE: Usar finalKeyData (que puede estar cifrado o en claro según KEK)
        val keyHex = finalKeyData // Datos de la llave (cifrados con KEK o en claro)

        // Logs detallados de la estructura Futurex
        Log.i(TAG, "=== ESTRUCTURA FUTUREX PARA INYECCIÓN DE LLAVE ===")
        Log.i(TAG, "Comando: $command (Inyección de llave simétrica)")
        Log.i(TAG, "Versión: $version")
        Log.i(TAG, "Slot de llave: $keySlot (${keyConfig.slot})")
        Log.i(TAG, "Slot KTK: $ktkSlotStr")
        Log.i(TAG, "Tipo de llave: $keyType (${keyConfig.keyType})")
        Log.i(TAG, "Tipo de encriptación: $encryptionType (${if (isDukptPlaintext) "DUKPT Plaintext" else "Cifrado con KTK"})")
        Log.i(TAG, "Algoritmo de llave: $keyAlgorithm (${getAlgorithmDescription(keyAlgorithm)})")
        Log.i(TAG, "Checksum de llave: $keyChecksum (KCV: ${selectedKey.kcv})")
        Log.i(TAG, "Checksum KTK: $ktkChecksum")
        Log.i(TAG, "KSN: $ksn (20 caracteres)")
        if (isDukptPlaintext) {
            Log.i(TAG, "Longitud KTK: No aplicable (DUKPT plaintext sin KTK)")
        } else if (ktkData != null) {
            Log.i(TAG, "Longitud KTK: ${String.format("%03X", ktkData.length / 2)} (${ktkData.length / 2} bytes)")
        } else {
            Log.i(TAG, "Longitud KTK: No disponible (KTK no está cargada)")
        }
        Log.i(TAG, "Longitud de llave: $keyLength ($keyLengthBytes bytes)")
        Log.i(TAG, "  - Formato: ASCII HEX (3 dígitos)")
        Log.i(TAG, "  - Valor: '$keyLength'")
        Log.i(TAG, "  - Validación: ${if (keyLength.length == 3 && keyLength.all { it.isLetterOrDigit() }) "✓ Válido" else "✗ Inválido"}")
        Log.i(TAG, "Datos de llave cifrada (hex): ${keyHex.take(64)}${if (keyHex.length > 64) "..." else ""}")
        Log.i(TAG, "Longitud total del payload: ${command.length + version.length + keySlot.length + ktkSlotStr.length + keyType.length + encryptionType.length + keyChecksum.length + ktkChecksum.length + ksn.length + keyLength.length + keyHex.length} caracteres (sin incluir KTK)")
        
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
        val payload = command + version + keySlot + ktkSlotStr + keyType + encryptionType + keyAlgorithm + keySubType +
                     keyChecksum + ktkChecksum + ksn + keyLength + keyHex

        Log.i(TAG, "=== PAYLOAD FINAL FUTUREX ===")
        Log.i(TAG, "Payload completo: $payload")
        Log.i(TAG, "Validación del payload:")
        Log.i(TAG, "  - Comando: $command")
        Log.i(TAG, "  - Versión: $version")
        Log.i(TAG, "  - Slot: $keySlot")
        Log.i(TAG, "  - KTK Slot: $ktkSlotStr")
        Log.i(TAG, "  - Tipo: $keyType")
        Log.i(TAG, "  - Encriptación: $encryptionType")
        Log.i(TAG, "  - Algoritmo: $keyAlgorithm")
        Log.i(TAG, "  - Subtipo: $keySubType")
        Log.i(TAG, "  - Checksum: $keyChecksum")
        Log.i(TAG, "  - KTK Checksum: $ktkChecksum")
        Log.i(TAG, "  - KSN: $ksn")
        Log.i(TAG, "  - Longitud: $keyLength (${keyLengthBytes} bytes)")
        Log.i(TAG, "  - Datos: ${keyHex.take(32)}...")
        Log.i(TAG, "================================================")

        // Construir el payload manualmente para Futurex
        val payloadString = command + version + keySlot + ktkSlotStr + keyType + encryptionType + keyAlgorithm + keySubType +
                           keyChecksum + ktkChecksum + ksn + keyLength + keyHex

        Log.i(TAG, "=== PAYLOAD MANUAL FUTUREX ===")
        Log.i(TAG, "Payload string: $payloadString")
        Log.i(TAG, "Longitud payload: ${payloadString.length} caracteres")
        Log.i(TAG, "================================================")

        // DEBUG: Verificar cálculo del LRC
        // IMPORTANTE: Para encryptionType 02, la KTK NO va en el payload de este comando
        // La KTK se envía en un comando separado ANTES de este
        val fields = listOf(version, keySlot, ktkSlotStr, keyType, encryptionType, keyAlgorithm, keySubType,
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
     * Nota: El parámetro 'command' siempre es "02" en este contexto, pero se mantiene como parámetro
     * para mantener la flexibilidad del método de debug.
     */
    @Suppress("SameParameterValue")
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

        // Usar .contains() para detectar el tipo de llave en lugar de comparación exacta
        val keyTypeUpper = keyType.uppercase()
        val mappedType = when {
            // PIN, MAC, DATA: Tipos de llaves operacionales
            keyTypeUpper.contains("PIN") -> "05"    // PIN Encryption Key
            keyTypeUpper.contains("MAC") -> "04"    // MAC Key
            keyTypeUpper.contains("DATA") -> "0C"   // Data Encryption Key

            // DUKPT: Llaves derivadas con KSN
            keyTypeUpper.contains("DUKPT") && keyTypeUpper.contains("AES") && keyTypeUpper.contains("IPEK") -> "0B"   // DUKPT AES IPEK
            keyTypeUpper.contains("DUKPT") && keyTypeUpper.contains("IPEK") -> "03"   // DUKPT 3DES IPEK
            keyTypeUpper.contains("DUKPT") && keyTypeUpper.contains("AES") -> "10"  // DUKPT AES BDK
            keyTypeUpper.contains("DUKPT") -> "08"  // DUKPT 3DES BDK

            // IPEK standalone (sin DUKPT en el nombre)
            keyTypeUpper.contains("IPEK") && keyTypeUpper.contains("AES") -> "0B"   // AES IPEK
            keyTypeUpper.contains("IPEK") -> "03"   // 3DES IPEK

            // Master/Transport Keys
            keyTypeUpper.contains("TDES") || keyTypeUpper.contains("3DES") -> "01"  // Master Session Key (3DES)
            keyTypeUpper.contains("AES") -> "01"    // Master Session Key (AES)

            // Default
            else -> "01" // Default to Master Session Key
        }

        Log.d(TAG, "Tipo mapeado: '$keyType' -> '$mappedType'")
        Log.d(TAG, "  - Detección: keyTypeUpper='$keyTypeUpper'")
        Log.d(TAG, "  - Descripción: ${getKeyTypeDescription(mappedType)}")
        return mappedType
    }

    /**
     * Verifica si el tipo de llave es DUKPT y requiere KSN
     */
    private fun isDukptKeyType(keyType: String): Boolean {
        return when (keyType) {
            "02", "03", "08", "0B", "10" -> true // Tipos DUKPT según manual Futurex
            else -> false
        }
    }

    /**
     * Verifica si el tipo de llave configurado en el perfil es DUKPT
     */
    private fun isDukptKeyTypeFromConfig(keyType: String): Boolean {
        return keyType.uppercase().contains("DUKPT")
    }

    /**
     * Genera un KSN (Key Serial Number) para llaves DUKPT
     * El KSN debe ser 20 caracteres hexadecimales según el manual Futurex
     */
    private fun generateKsn(keyConfig: KeyConfiguration, selectedKey: InjectedKeyEntity): String {
        Log.i(TAG, "=== GENERANDO KSN PARA LLAVE DUKPT ===")
        
        // Por defecto, usar el KCV como base para generar un KSN único
        // En un entorno real, el KSN debería ser proporcionado por el sistema de gestión de llaves
        val baseKsn = selectedKey.kcv.padEnd(16, '0').take(16) // Base de 16 caracteres
        val suffix = String.format("%04X", keyConfig.slot) // Suffix de 4 caracteres basado en slot
        val ksn = (baseKsn + suffix).uppercase()
        
        Log.i(TAG, "KSN generado:")
        Log.i(TAG, "  - Base (KCV): ${selectedKey.kcv}")
        Log.i(TAG, "  - Slot: ${keyConfig.slot}")
        Log.i(TAG, "  - KSN final: $ksn")
        Log.i(TAG, "  - Longitud: ${ksn.length} caracteres")
        
        if (ksn.length != 20) {
            Log.w(TAG, "Ajustando longitud del KSN a 20 caracteres")
            return ksn.padEnd(20, '0').take(20)
        }
        
        Log.i(TAG, "✓ KSN generado exitosamente: $ksn")
        Log.i(TAG, "================================================")
        
        return ksn
    }

    private fun getKeyTypeDescription(keyType: String): String {
        return when (keyType) {
            "01" -> "Master Session Key (TDES/3DES/AES)"
            "02" -> "DUKPT Initial Key (Solo pruebas)"
            "03" -> "DUKPT 3DES IPEK (Llave inicial DUKPT 3DES)"
            "04" -> "MAC Key"
            "05" -> "PIN Encryption Key"
            "06" -> "Key Transfer Key (KTK)"
            "08" -> "DUKPT 3DES BDK (Base Derivation Key)"
            "0B" -> "DUKPT AES IPEK (Llave inicial DUKPT AES)"
            "0C" -> "Data Encryption Key"
            "10" -> "DUKPT AES BDK (Base Derivation Key AES)"
            else -> "Tipo desconocido ($keyType)"
        }
    }

    /**
     * Detecta el algoritmo de la llave basado en la información almacenada en la base de datos
     * Códigos:
     * - 00 = 3DES-112 (16 bytes, 2 keys)
     * - 01 = 3DES-168 (24 bytes, 3 keys)
     * - 02 = AES-128 (16 bytes)
     * - 03 = AES-192 (24 bytes)
     * - 04 = AES-256 (32 bytes)
     */
    private fun detectKeyAlgorithmFromEntity(keyEntity: InjectedKeyEntity, keyLengthBytes: Int, keyType: String): String {
        Log.i(TAG, "=== DETECTANDO ALGORITMO DE LLAVE ===")
        Log.i(TAG, "  - KCV: ${keyEntity.kcv}")
        Log.i(TAG, "  - Tipo: $keyType")
        Log.i(TAG, "  - Longitud: $keyLengthBytes bytes")
        Log.i(TAG, "  - Algoritmo en BD: ${keyEntity.keyAlgorithm}")
        
        // Si la entidad tiene información del algoritmo, usarla
        if (keyEntity.keyAlgorithm.isNotEmpty() && keyEntity.keyAlgorithm != "UNASSIGNED") {
            val algorithmCode = when (keyEntity.keyAlgorithm.uppercase()) {
                "AES_128", "AES128" -> "02" // AES-128
                "AES_192", "AES192" -> "03" // AES-192
                "AES_256", "AES256" -> "04" // AES-256
                "DES_TRIPLE", "TDES", "3DES" -> when (keyLengthBytes) {
                    16 -> "00" // 3DES-112 (2 keys)
                    24 -> "01" // 3DES-168 (3 keys)
                    else -> "01" // Default 3DES-168
                }
                else -> {
                    Log.w(TAG, "Algoritmo desconocido en BD: ${keyEntity.keyAlgorithm}, detectando por longitud...")
                    detectKeyAlgorithmByLength(keyLengthBytes, keyType)
                }
            }
            Log.i(TAG, "  - Algoritmo detectado desde BD: ${getAlgorithmDescription(algorithmCode)}")
            return algorithmCode
        }
        
        // Si no hay información en BD, detectar por longitud y tipo
        Log.i(TAG, "  - Sin información de algoritmo en BD, detectando por longitud...")
        return detectKeyAlgorithmByLength(keyLengthBytes, keyType)
    }

    /**
     * Detecta el algoritmo de la llave basado en su longitud y tipo (método de fallback)
     */
    private fun detectKeyAlgorithmByLength(keyLengthBytes: Int, keyType: String): String {
        val keyTypeUpper = keyType.uppercase()

        // Si el tipo contiene AES explícitamente, usar algoritmo AES
        if (keyTypeUpper.contains("AES")) {
            return when (keyLengthBytes) {
                16 -> "02" // AES-128
                24 -> "03" // AES-192
                32 -> "04" // AES-256
                else -> "02" // Default AES-128
            }
        }

        // Si el tipo contiene TDES o 3DES explícitamente, o es tipo genérico, usar 3DES
        return when (keyLengthBytes) {
            16 -> "00" // 3DES-112 (2 keys)
            24 -> "01" // 3DES-168 (3 keys)
            32 -> "04" // Si es 32 bytes y no es AES, asumir AES-256
            else -> "01" // Default 3DES-168
        }
    }

    /**
     * Detecta el subtipo de llave basado en el tipo de llave
     * Códigos:
     * - 00 = Generic/Master Key
     * - 01 = Working PIN Key
     * - 02 = Working MAC Key
     * - 03 = Working DATA Key
     * - 04 = DUKPT Key
     */
    private fun detectKeySubType(keyType: String): String {
        val keyTypeUpper = keyType.uppercase()
        return when {
            keyTypeUpper.contains("WORKING") && keyTypeUpper.contains("PIN") -> "01"
            keyTypeUpper.contains("WORKING") && keyTypeUpper.contains("MAC") -> "02"
            keyTypeUpper.contains("WORKING") && keyTypeUpper.contains("DATA") -> "03"
            keyTypeUpper.contains("DUKPT") -> "04"
            else -> "00"  // Generic/Master
        }
    }

    /**
     * Retorna la descripción legible del código de algoritmo
     */
    private fun getAlgorithmDescription(algorithmCode: String): String {
        return when (algorithmCode) {
            "00" -> "3DES-112 (16 bytes, 2 keys)"
            "01" -> "3DES-168 (24 bytes, 3 keys)"
            "02" -> "AES-128 (16 bytes)"
            "03" -> "AES-192 (24 bytes)"
            "04" -> "AES-256 (32 bytes)"
            else -> "Desconocido ($algorithmCode)"
        }
    }

    private fun sendData(data: ByteArray) {
        if (comController == null) {
            throw Exception("Controlador de comunicación no inicializado")
        }

        val result = comController!!.write(data, 1000)
        if (result < 0) {
            Log.e(TAG, "❌ Error al enviar datos: $result")
            throw Exception("Error al enviar datos: $result")
        }

        Log.i(TAG, "✓ Enviados ${result} bytes: ${data.toHexString().take(40)}...")
    }

    private fun waitForResponse(): ByteArray {
        Log.i(TAG, "Esperando respuesta (timeout: 10s)...")

        val buffer = ByteArray(1024)
        val bytesRead = comController!!.readData(buffer.size, buffer, 10000)

        if (bytesRead <= 0) {
            Log.e(TAG, "❌ Timeout o error al leer respuesta: $bytesRead bytes leídos")
            throw Exception("Timeout o error al leer respuesta")
        }

        val response = buffer.copyOf(bytesRead)
        Log.i(TAG, "✓ Recibidos $bytesRead bytes: ${response.toHexString().take(40)}...")

        return response
    }

    private fun processInjectionResponse(response: ByteArray, keyConfig: KeyConfiguration, @Suppress("UNUSED_PARAMETER") commandSent: ByteArray) {
        Log.i(TAG, "=== PROCESANDO RESPUESTA FUTUREX ===")
        Log.i(TAG, "Configuración de llave: ${keyConfig.usage} (Slot: ${keyConfig.slot})")
        Log.i(TAG, "Respuesta recibida: ${response.size} bytes")
        
        val profile = _state.value.currentProfile
        val commandHex = commandSent.toHexString()
        val responseHex = response.toHexString()
        
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
                    
                    // Registrar log de inyección exitosa
                    injectionLogger.logSuccess(
                        commandSent = commandHex,
                        responseReceived = responseHex,
                        username = currentUsername,
                        profileName = profile?.name ?: "Desconocido",
                        keyType = keyConfig.keyType,
                        keySlot = keyConfig.slot.toIntOrNull() ?: -1,
                        notes = "Uso: ${keyConfig.usage}, KCV: ${parsedMessage.keyChecksum}"
                    )
                } else {
                    val errorCode = FuturexErrorCode.fromCode(parsedMessage.responseCode)
                    val errorMsg = errorCode?.description ?: "Error desconocido"
                    Log.e(TAG, "✗ Error en inyección de ${keyConfig.usage}: $errorMsg (Código: ${parsedMessage.responseCode})")
                    
                    // Registrar log de inyección fallida
                    injectionLogger.logFailure(
                        commandSent = commandHex,
                        responseReceived = responseHex,
                        username = currentUsername,
                        profileName = profile?.name ?: "Desconocido",
                        keyType = keyConfig.keyType,
                        keySlot = keyConfig.slot.toIntOrNull() ?: -1,
                        notes = "Error: $errorMsg (Código: ${parsedMessage.responseCode})"
                    )
                    
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
                
                // Registrar log de error por respuesta inesperada
                injectionLogger.logInjection(
                    commandSent = commandHex,
                    responseReceived = responseHex,
                    operationStatus = "ERROR",
                    username = currentUsername,
                    profileName = profile?.name ?: "Desconocido",
                    keyType = keyConfig.keyType,
                    keySlot = keyConfig.slot.toIntOrNull() ?: -1,
                    notes = "Respuesta inesperada: ${parsedMessage?.javaClass?.simpleName}"
                )
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

        // Usar contains() para detectar el tipo de llave en lugar de comparación exacta
        val keyTypeUpper = keyConfig.keyType.uppercase()
        val validLengths = when {
            // PIN, MAC, DATA: Soportan 3DES (16, 24 bytes) y AES (16, 24, 32 bytes)
            keyTypeUpper.contains("PIN") || keyTypeUpper.contains("MAC") || keyTypeUpper.contains("DATA") -> {
                listOf(16, 24, 32) // 128, 192, 256 bits - Incluye 3DES de 24 bytes
            }
            // 3DES/TDES: Soportan Single (8), Double (16), Triple (24) y variantes extendidas
            keyTypeUpper.contains("TDES") || keyTypeUpper.contains("3DES") -> {
                listOf(8, 16, 24, 32, 48) // Todas las variantes 3DES
            }
            // AES: Soportan AES-128, AES-192, AES-256
            keyTypeUpper.contains("AES") -> {
                listOf(16, 24, 32) // 128, 192, 256 bits
            }
            // DUKPT: Soportan múltiples longitudes
            keyTypeUpper.contains("DUKPT") -> {
                listOf(16, 24, 32, 48) // Todas las longitudes comunes
            }
            // Por defecto: Permisivo con todas las longitudes estándar
            else -> listOf(8, 16, 24, 32, 48)
        }

        if (keyLengthBytes !in validLengths) {
            throw Exception("Longitud de llave inválida para ${keyConfig.keyType}: $keyLengthBytes bytes. Válidas: $validLengths")
        }

        // Verificar KSN para llaves DUKPT
        if (isDukptKeyTypeFromConfig(keyConfig.keyType)) {
            if (keyConfig.ksn.isNotEmpty() && keyConfig.ksn.length != 20) {
                throw Exception("KSN inválido para llave DUKPT: debe tener exactamente 20 caracteres hexadecimales")
            }
            if (keyConfig.ksn.isNotEmpty() && !keyConfig.ksn.matches(Regex("^[0-9A-Fa-f]+$"))) {
                throw Exception("KSN contiene caracteres inválidos: solo se permiten dígitos hexadecimales")
            }
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

    /**
     * Envía comando para leer el número de serie del dispositivo
     */
    @Suppress("UNUSED")
    fun readDeviceSerial(): String? {
        if (comController == null) {
            Log.e(TAG, "No se puede leer número de serie: controlador no inicializado")
            return null
        }

        return try {
            Log.i(TAG, "=== LEYENDO NÚMERO DE SERIE FUTUREX ===")
            
            // Construir comando 03 para leer número de serie
            val command = "03"
            val version = "01"
            val readSerialCommand = messageFormatter!!.format(command, listOf(version))
            
            Log.i(TAG, "Enviando comando de lectura de serial...")
            sendData(readSerialCommand)
            
            // Esperar respuesta
            val response = waitForResponse()
            
            // Parsear respuesta
            messageParser!!.appendData(response)
            val parsedMessage = messageParser!!.nextMessage()
            
            when (parsedMessage) {
                is InjectSymmetricKeyResponse -> {
                    if (parsedMessage.responseCode == "00" && parsedMessage.rawPayload.length >= 6) {
                        val serialNumber = parsedMessage.rawPayload.substring(4)
                        Log.i(TAG, "✓ Número de serie leído: $serialNumber")
                        return serialNumber
                    } else {
                        Log.e(TAG, "Error leyendo serial: ${parsedMessage.responseCode}")
                        return null
                    }
                }
                else -> {
                    Log.e(TAG, "Respuesta inesperada al leer serial: $parsedMessage")
                    return null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción leyendo número de serie", e)
            null
        } finally {
            Log.i(TAG, "================================================")
        }
    }

    /**
     * Envía comando para escribir el número de serie del dispositivo
     */
    @Suppress("UNUSED")
    fun writeDeviceSerial(serialNumber: String): Boolean {
        if (comController == null) {
            Log.e(TAG, "No se puede escribir número de serie: controlador no inicializado")
            return false
        }

        if (serialNumber.length != 16) {
            Log.e(TAG, "Número de serie inválido: debe tener 16 caracteres")
            return false
        }

        return try {
            Log.i(TAG, "=== ESCRIBIENDO NÚMERO DE SERIE FUTUREX ===")
            Log.i(TAG, "Número de serie: $serialNumber")
            
            // Construir comando 04 para escribir número de serie
            val command = "04"
            val version = "01"
            val writeSerialCommand = messageFormatter!!.format(command, listOf(version, serialNumber))
            
            Log.i(TAG, "Enviando comando de escritura de serial...")
            sendData(writeSerialCommand)
            
            // Esperar respuesta
            val response = waitForResponse()
            
            // Parsear respuesta
            messageParser!!.appendData(response)
            val parsedMessage = messageParser!!.nextMessage()
            
            when (parsedMessage) {
                is InjectSymmetricKeyResponse -> {
                    val success = parsedMessage.responseCode == "00"
                    if (success) {
                        Log.i(TAG, "✓ Número de serie escrito exitosamente")
                    } else {
                        Log.e(TAG, "Error escribiendo serial: ${parsedMessage.responseCode}")
                    }
                    return success
                }
                else -> {
                    Log.e(TAG, "Respuesta inesperada al escribir serial: $parsedMessage")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción escribiendo número de serie", e)
            false
        } finally {
            Log.i(TAG, "================================================")
        }
    }

    /**
     * Envía comando para eliminar todas las llaves del dispositivo
     */
    @Suppress("UNUSED")
    fun deleteAllKeys(): Boolean {
        if (comController == null) {
            Log.e(TAG, "No se puede eliminar llaves: controlador no inicializado")
            return false
        }

        return try {
            Log.i(TAG, "=== ELIMINANDO TODAS LAS LLAVES FUTUREX ===")
            
            // Construir comando 05 para eliminar todas las llaves
            val command = "05"
            val version = "01"
            val deleteAllCommand = messageFormatter!!.format(command, listOf(version))
            
            Log.i(TAG, "Enviando comando de eliminación total...")
            sendData(deleteAllCommand)
            
            // Esperar respuesta
            val response = waitForResponse()
            
            // Parsear respuesta
            messageParser!!.appendData(response)
            val parsedMessage = messageParser!!.nextMessage()
            
            when (parsedMessage) {
                is InjectSymmetricKeyResponse -> {
                    val success = parsedMessage.responseCode == "00"
                    if (success) {
                        Log.i(TAG, "✓ Todas las llaves eliminadas exitosamente")
                    } else {
                        Log.e(TAG, "Error eliminando llaves: ${parsedMessage.responseCode}")
                    }
                    return success
                }
                else -> {
                    Log.e(TAG, "Respuesta inesperada al eliminar llaves: $parsedMessage")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción eliminando todas las llaves", e)
            false
        } finally {
            Log.i(TAG, "================================================")
        }
    }

    /**
     * Envía comando para eliminar una llave específica
     */
    @Suppress("UNUSED")
    fun deleteSingleKey(keySlot: Int, keyType: String): Boolean {
        if (comController == null) {
            Log.e(TAG, "No se puede eliminar llave: controlador no inicializado")
            return false
        }

        return try {
            Log.i(TAG, "=== ELIMINANDO LLAVE ESPECÍFICA FUTUREX ===")
            Log.i(TAG, "Slot: $keySlot, Tipo: $keyType")
            
            // Construir comando 06 para eliminar llave específica
            val command = "06"
            val version = "01"
            val slotHex = keySlot.toString(16).padStart(2, '0').uppercase()
            val typeHex = mapKeyTypeToFuturex(keyType)
            
            val deleteSingleCommand = messageFormatter!!.format(command, listOf(version, slotHex, typeHex))
            
            Log.i(TAG, "Enviando comando de eliminación específica...")
            sendData(deleteSingleCommand)
            
            // Esperar respuesta
            val response = waitForResponse()
            
            // Parsear respuesta
            messageParser!!.appendData(response)
            val parsedMessage = messageParser!!.nextMessage()
            
            when (parsedMessage) {
                is InjectSymmetricKeyResponse -> {
                    val success = parsedMessage.responseCode == "00"
                    if (success) {
                        Log.i(TAG, "✓ Llave en slot $keySlot eliminada exitosamente")
                    } else {
                        Log.e(TAG, "Error eliminando llave: ${parsedMessage.responseCode}")
                    }
                    return success
                }
                else -> {
                    Log.e(TAG, "Respuesta inesperada al eliminar llave: $parsedMessage")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción eliminando llave específica", e)
            false
        } finally {
            Log.i(TAG, "================================================")
        }
    }

    /**
     * Exporta una KEK al dispositivo SubPOS en claro (sin cifrar)
     * La KEK se envía al slot 00 (fijo para KEKs) usando el comando de inyección Futurex
     */
    private fun exportKEKToDevice(kek: InjectedKeyEntity) {
        Log.i(TAG, "=== EXPORTANDO KEK AL SUBPOS ===")
        Log.i(TAG, "KEK a exportar:")
        Log.i(TAG, "  - KCV: ${kek.kcv}")
        Log.i(TAG, "  - Nombre: ${kek.customName.ifEmpty { "Sin nombre" }}")
        Log.i(TAG, "  - Longitud: ${kek.keyData.length / 2} bytes")

        // Validar que la KEK tenga datos
        if (kek.keyData.isEmpty()) {
            throw Exception("KEK no tiene datos para exportar")
        }

        val keyLengthBytes = kek.keyData.length / 2
        if (keyLengthBytes !in listOf(16, 24, 32)) {
            throw Exception("Longitud de KEK inválida: $keyLengthBytes bytes")
        }

        // Construir comando Futurex para exportar KEK
        // IMPORTANTE: KEK siempre va en CLARO (encryptionType = "00")
        val command = "02" // Comando de inyección simétrica
        val version = "01"
        val keySlot = "00" // Slot fijo para KEK
        val ktkSlotStr = "00" // No hay KTK superior (KEK se envía en claro)
        val keyType = "06" // Tipo 06 = Key Transfer Key (KTK/KEK)
        val encryptionType = "00" // EN CLARO (sin cifrar)

        // Detectar algoritmo basado en longitud
        val keyAlgorithm = detectKeyAlgorithmByLength(keyLengthBytes, kek.customName)
        
        // Detectar keySubType para KEK (siempre "00" para Transport Key)
        val keySubType = "00" // KEK/KTK es tipo genérico

        val keyChecksum = kek.kcv.take(4)
        val ktkChecksum = "0000" // No hay KTK superior
        val ksn = "00000000000000000000" // KEK no usa KSN
        val keyLength = String.format("%03X", keyLengthBytes)
        val keyHex = kek.keyData

        Log.i(TAG, "=== ESTRUCTURA FUTUREX PARA EXPORTACIÓN DE KEK ===")
        Log.i(TAG, "Comando: $command")
        Log.i(TAG, "Versión: $version")
        Log.i(TAG, "Slot de llave: $keySlot (fijo para KEK)")
        Log.i(TAG, "Slot KTK: $ktkSlotStr (no aplica)")
        Log.i(TAG, "Tipo de llave: $keyType (KEK/KTK)")
        Log.i(TAG, "Tipo de encriptación: $encryptionType (EN CLARO)")
        Log.i(TAG, "Algoritmo detectado: $keyAlgorithm (${getAlgorithmDescription(keyAlgorithm)})")
        Log.i(TAG, "Subtipo de llave: $keySubType (Transport Key)")
        Log.i(TAG, "Checksum de llave: $keyChecksum")
        Log.i(TAG, "Checksum KTK: $ktkChecksum (no aplica)")
        Log.i(TAG, "KSN: $ksn (no aplica)")
        Log.i(TAG, "Longitud de llave: $keyLength ($keyLengthBytes bytes)")
        Log.i(TAG, "Datos de KEK (primeros 32 bytes): ${keyHex.take(64)}...")

        // Formatear comando Futurex
        val fields = listOf(version, keySlot, ktkSlotStr, keyType, encryptionType, keyAlgorithm, keySubType,
                           keyChecksum, ktkChecksum, ksn, keyLength, keyHex)
        val formattedCommand = messageFormatter!!.format(command, fields)

        Log.i(TAG, "Comando formateado: ${formattedCommand.size} bytes")
        Log.i(TAG, "Enviando KEK al SubPOS...")

        // Enviar comando
        sendData(formattedCommand)

        // Esperar respuesta
        Log.i(TAG, "Esperando respuesta del dispositivo...")
        val response = waitForResponse()

        // Procesar respuesta
        Log.i(TAG, "Procesando respuesta...")
        messageParser!!.appendData(response)
        val parsedMessage = messageParser!!.nextMessage()

        when (parsedMessage) {
            is InjectSymmetricKeyResponse -> {
                if (parsedMessage.responseCode == "00") {
                    Log.i(TAG, "✓ KEK exportada exitosamente al SubPOS")
                    Log.i(TAG, "  - Slot: 00")
                    Log.i(TAG, "  - KCV: ${kek.kcv}")
                } else {
                    val errorCode = FuturexErrorCode.fromCode(parsedMessage.responseCode)
                    val errorMsg = errorCode?.description ?: "Error desconocido"
                    Log.e(TAG, "✗ Error al exportar KEK: $errorMsg (Código: ${parsedMessage.responseCode})")
                    throw Exception("Error al exportar KEK: $errorMsg")
                }
            }
            else -> {
                Log.e(TAG, "✗ Respuesta inesperada al exportar KEK: $parsedMessage")
                throw Exception("Respuesta inesperada al exportar KEK")
            }
        }

        Log.i(TAG, "================================================")
    }

    /**
     * Detecta si una llave es DUKPT plaintext (EncryptionType 05)
     *
     * DUKPT plaintext se usa para testing solamente:
     * - Requiere llave de tipo DUKPT Initial Key (IPEK)
     * - La IPEK se envía sin cifrar (plaintext)
     * - NO requiere KTK para la inyección
     * - Se valida que tenga un KSN válido (20 caracteres hex)
     *
     * @return true si es DUKPT plaintext, false si es otra llave
     */
    private fun isDukptPlaintextKey(keyConfig: KeyConfiguration, @Suppress("UNUSED_PARAMETER") selectedKey: InjectedKeyEntity): Boolean {
        // Detectar si es tipo DUKPT
        val isDukptType = keyConfig.keyType.contains("DUKPT", ignoreCase = true) &&
                         keyConfig.keyType.contains("IPEK", ignoreCase = true)

        // Detectar si tiene KSN válido (20 caracteres para DUKPT)
        val hasValidKsn = keyConfig.ksn.length == 20 &&
                         keyConfig.ksn.matches(Regex("[0-9A-Fa-f]{20}"))

        // Es DUKPT plaintext si: es tipo DUKPT IPEK y tiene KSN válido
        val isDukptPlaintext = isDukptType && hasValidKsn

        Log.i(TAG, "=== DETECTANDO TIPO DE INYECCIÓN ===")
        Log.i(TAG, "  - Tipo de llave: ${keyConfig.keyType}")
        Log.i(TAG, "  - ¿Es DUKPT IPEK?: $isDukptType")
        Log.i(TAG, "  - KSN: ${keyConfig.ksn}")
        Log.i(TAG, "  - ¿KSN válido (20 hex)?: $hasValidKsn")
        Log.i(TAG, "  - ¿Es DUKPT Plaintext?: $isDukptPlaintext")

        return isDukptPlaintext
    }

    /**
     * Inicia el monitoreo de detección de cable USB con hysteresis
     * Similar a la implementación en MainViewModel.kt del módulo keyreceiver
     */
    private fun startCableDetection() {
        // Cancelar job anterior si existe
        cableDetectionJob?.cancel()

        cableDetectionJob = viewModelScope.launch(Dispatchers.IO) {
            var consecutiveDetectionsToChange = 0
            val HYSTERESIS_THRESHOLD = 2              // Requiere 2 detecciones para cambiar
            val DETECTION_INTERVAL_CONNECTED = 3000L  // 3s cuando hay cable
            val DETECTION_INTERVAL_DISCONNECTED = 5000L // 5s cuando no hay cable

            Log.i(TAG, "🔌 Iniciando monitoreo de cable USB...")

            while (isActive) {
                try {
                    val detected = detectCableConnection()
                    val currentState = _state.value.cableConnected

                    // Si cambia → incrementar contador
                    if (detected != currentState) {
                        consecutiveDetectionsToChange++
                        Log.d(TAG, "Cambio de estado detectado: $consecutiveDetectionsToChange/$HYSTERESIS_THRESHOLD")

                        if (consecutiveDetectionsToChange >= HYSTERESIS_THRESHOLD) {
                            _state.value = _state.value.copy(cableConnected = detected)
                            Log.i(TAG, if (detected)
                                "✅ CABLE USB DETECTADO - Listo para inyección"
                            else
                                "⚠️ CABLE USB DESCONECTADO"
                            )
                        }
                    } else {
                        consecutiveDetectionsToChange = 0
                    }

                    // Ajustar intervalo según estado del cable
                    val delay = if (detected) DETECTION_INTERVAL_CONNECTED else DETECTION_INTERVAL_DISCONNECTED
                    kotlinx.coroutines.delay(delay)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error en detección de cable: ${e.message}")
                    kotlinx.coroutines.delay(DETECTION_INTERVAL_DISCONNECTED)
                }
            }
        }
    }

    /**
     * Detecta si hay un cable USB conectado verificando puertos seriales del sistema
     * Métodos múltiples para mayor confiabilidad:
     * 1. Buscar archivos de dispositivo USB (/dev/ttyUSB*, /dev/ttyACM*, etc.)
     * 2. Verificar información del sistema (/sys/bus/usb/devices)
     */
    private fun detectCableConnection(): Boolean {
        return try {
            // Método 1: Verificar archivos de dispositivo USB
            val usbDevices = arrayOf(
                "/dev/ttyUSB0", "/dev/ttyUSB1", "/dev/ttyUSB2",
                "/dev/ttyACM0", "/dev/ttyACM1", "/dev/ttyACM2",
                "/dev/ttyGS0"
            )

            val hasDeviceNode = usbDevices.any { devicePath ->
                val file = java.io.File(devicePath)
                file.exists()
            }

            if (hasDeviceNode) {
                Log.d(TAG, "✓ Cable USB detectado (dispositivo)")
                return true
            }

            // Método 2: Verificar información del kernel en /sys
            val sysPath = "/sys/bus/usb/devices"
            val sysDir = java.io.File(sysPath)
            val hasUsbDevices = sysDir.exists() && sysDir.listFiles()?.isNotEmpty() == true

            if (hasUsbDevices) {
                Log.d(TAG, "✓ Cable USB detectado (kernel)")
                return true
            }

            Log.d(TAG, "⚠️ Cable USB no detectado")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error detectando cable: ${e.message}")
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Detener monitoreo de cable al limpiar el ViewModel
        cableDetectionJob?.cancel()
        Log.d(TAG, "KeyInjectionViewModel cleared")
    }
}