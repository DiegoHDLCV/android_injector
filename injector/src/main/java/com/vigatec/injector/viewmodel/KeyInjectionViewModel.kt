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
import com.vigatec.config.manufacturerToDeviceTypeCode
import com.vigatec.config.deviceTypeCodeToManufacturer
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

enum class KeyInjectionItemStatus {
    PENDING,      // Pendiente de inyectar
    INJECTING,    // Actualmente inyectando
    INJECTED,    // Inyectada exitosamente
    ERROR         // Error al inyectar
}

data class KeyInjectionItem(
    val keyConfig: KeyConfiguration,
    val status: KeyInjectionItemStatus = KeyInjectionItemStatus.PENDING,
    val errorMessage: String? = null
)

data class KeyInjectionState(
    val status: InjectionStatus = InjectionStatus.IDLE,
    val currentProfile: ProfileEntity? = null,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val progress: Float = 0f,
    val log: String = "",
    val error: String? = null,
    val showInjectionModal: Boolean = false,
    val cableConnected: Boolean = false,  // Nuevo: estado de conexión de cable
    val keysToInject: List<KeyInjectionItem> = emptyList(),  // NUEVO: Lista de llaves preparadas para inyectar
    val isPrintingVoucher: Boolean = false, // NUEVO: Estado de carga para impresión
    val deviceSerial: String? = null, // NUEVO: Serial del dispositivo receptor
    val deviceModel: String? = null   // NUEVO: Modelo del dispositivo receptor
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

    // Evento para mostrar diálogo de confirmación de desinstalación
    private val _uninstallDialogEvent = MutableSharedFlow<Boolean>()
    val uninstallDialogEvent = _uninstallDialogEvent.asSharedFlow()

    // Evento para mostrar diálogo de validación de marca del dispositivo
    data class BrandMismatchEvent(
        val expectedBrand: String,
        val actualBrand: String
    )
    private val _brandMismatchDialogEvent = MutableSharedFlow<BrandMismatchEvent>()
    val brandMismatchDialogEvent = _brandMismatchDialogEvent.asSharedFlow()

    // Flag para controlar si el usuario confirmó continuar a pesar del mismatch
    private var userConfirmedBrandMismatch = false

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
    private val usbCableDetector = com.vigatec.injector.util.UsbCableDetector(application.applicationContext)

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

        // NUEVO: Preparar lista de llaves para inyectar
        val keysToInject = profile.keyConfigurations.map { keyConfig ->
            KeyInjectionItem(
                keyConfig = keyConfig,
                status = KeyInjectionItemStatus.PENDING
            )
        }

        _state.value = _state.value.copy(
            showInjectionModal = true,
            currentProfile = profile,
            totalSteps = profile.keyConfigurations.size,
            keysToInject = keysToInject
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

    /**
     * Maneja la respuesta del usuario sobre el diálogo de mismatch de marca.
     * @param confirmed true si el usuario decidió continuar, false si canceló
     */
    fun onBrandMismatchResponse(confirmed: Boolean) {
        Log.i(TAG, "Respuesta del usuario sobre mismatch de marca: confirmed=$confirmed")
        userConfirmedBrandMismatch = confirmed
    }

    /**
     * Valida que la marca del dispositivo receptor coincida con la esperada en el perfil.
     * Si hay mismatch, muestra un diálogo de advertencia al usuario.
     *
     * @return true si la validación fue exitosa o el usuario confirmó continuar, false si el usuario canceló
     */
    private suspend fun validateDeviceBrand(profile: ProfileEntity): Boolean {
        Log.i(TAG, "=== VALIDANDO MARCA DEL DISPOSITIVO ===")
        Log.i(TAG, "Marca esperada del perfil: ${profile.deviceType}")

        // Resetear el flag de confirmación
        userConfirmedBrandMismatch = false

        try {
            // Verificar si la comunicación está inicializada
            if (comController == null || messageFormatter == null || messageParser == null) {
                Log.w(TAG, "Comunicación no inicializada, omitiendo validación de marca")
                return true
            }

            // Construir comando 08 de validación
            val expectedDeviceTypeCode = manufacturerToDeviceTypeCode(
                com.vigatec.config.getManufacturerFromString(profile.deviceType)
            )

            Log.i(TAG, "Construyendo comando de validación de marca (08)...")
            Log.i(TAG, "  - Marca esperada: ${profile.deviceType}")
            Log.i(TAG, "  - Código de marca: $expectedDeviceTypeCode")

            // Construir comando: 08 + version(AA) + expectedDeviceType
            val commandBytes = messageFormatter!!.format("08", listOf("AA", expectedDeviceTypeCode))

            Log.i(TAG, "Enviando comando de validación...")
            sendData(commandBytes)

            // Esperar respuesta (timeout por defecto: 10 segundos)
            Log.i(TAG, "Esperando respuesta de validación...")
            val response = try {
                waitForResponse()
            } catch (e: Exception) {
                Log.w(TAG, "No se recibió respuesta del comando de validación: ${e.message}")
                return true  // Permitir continuar si no hay respuesta
            }

            // Parsear la respuesta
            messageParser!!.appendData(response)
            val parsedMessage = messageParser!!.nextMessage()
            if (parsedMessage is ValidateDeviceBrandResponse) {
                val responseCode = parsedMessage.responseCode
                val actualDeviceType = parsedMessage.actualDeviceType

                Log.i(TAG, "Respuesta de validación recibida:")
                Log.i(TAG, "  - Código de respuesta: $responseCode")
                Log.i(TAG, "  - Marca real del dispositivo: $actualDeviceType")

                // Verificar si la respuesta es exitosa (código "00")
                if (responseCode == "00") {
                    Log.i(TAG, "✓ Validación de marca exitosa - Las marcas coinciden")
                    return true
                } else if (responseCode == FuturexErrorCode.DEVICE_BRAND_MISMATCH.code) {
                    // Mismatch detectado
                    val actualBrandEnum = deviceTypeCodeToManufacturer(actualDeviceType)

                    Log.w(TAG, "⚠️ Mismatch de marca detectado:")
                    Log.w(TAG, "  - Esperada: ${profile.deviceType}")
                    Log.w(TAG, "  - Real: ${actualBrandEnum.name}")

                    // Emitir evento de diálogo
                    _brandMismatchDialogEvent.emit(
                        BrandMismatchEvent(
                            expectedBrand = profile.deviceType,
                            actualBrand = actualBrandEnum.name
                        )
                    )

                    // Esperar respuesta del usuario (máximo 30 segundos)
                    var waited = 0
                    while (waited < 30000 && !userConfirmedBrandMismatch) {
                        kotlinx.coroutines.delay(100)
                        waited += 100
                    }

                    if (userConfirmedBrandMismatch) {
                        Log.i(TAG, "Usuario confirmó continuar a pesar del mismatch")
                        return true
                    } else {
                        Log.i(TAG, "Usuario canceló la inyección debido al mismatch")
                        return false
                    }
                } else {
                    // Otro error
                    val errorDesc = FuturexErrorCode.fromCode(responseCode)?.description ?: "Error desconocido"
                    Log.e(TAG, "Error en validación de marca: $errorDesc")
                    return true  // Permitir continuar si hay otro tipo de error
                }
            } else {
                Log.w(TAG, "Respuesta no es de tipo ValidateDeviceBrandResponse: ${parsedMessage?.javaClass?.simpleName}")
                return true  // Permitir continuar si no podemos parsear
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error durante validación de marca", e)
            return true  // Permitir continuar si hay error en la validación
        }
    }

    fun startKeyInjection() {
        viewModelScope.launch {
            val profile = _state.value.currentProfile ?: return@launch
            val keyConfigs = profile.keyConfigurations

            val injectionStartTime = System.currentTimeMillis()
            Log.i(TAG, "╔═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ === INICIANDO PROCESO DE INYECCIÓN FUTUREX ===")
            Log.i(TAG, "║ Timestamp: $injectionStartTime")
            Log.i(TAG, "║ Perfil: ${profile.name}")
            Log.i(TAG, "║ ¿Usa KTK?: ${profile.useKTK}")
            if (profile.useKTK) {
                Log.i(TAG, "║ KTK seleccionada (KCV): ${profile.selectedKTKKcv}")
            }
            Log.i(TAG, "║ Configuraciones de llave: ${keyConfigs.size}")
            keyConfigs.forEachIndexed { index, config ->
                Log.i(TAG, "║   ${index + 1}. ${config.usage} - Slot: ${config.slot} - Tipo: ${config.keyType}")
            }
            Log.i(TAG, "╚═══════════════════════════════════════════════════════════════")

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
                    log = _state.value.log + "Conexión establecida.\n"
                )

                // === PASO 0: VALIDAR MARCA DEL DISPOSITIVO ===
                Log.i(TAG, "=== VALIDANDO MARCA DEL DISPOSITIVO ===")
                _state.value = _state.value.copy(
                    log = _state.value.log + "Validando marca del dispositivo...\n"
                )

                val brandValidationResult = validateDeviceBrand(profile)
                if (!brandValidationResult) {
                    Log.i(TAG, "Usuario canceló la inyección debido a mismatch de marca")
                    _state.value = _state.value.copy(
                        status = InjectionStatus.IDLE,
                        log = _state.value.log + "Inyección cancelada por el usuario (mismatch de marca)\n"
                    )
                    _snackbarEvent.emit("Inyección cancelada por el usuario")
                    return@launch
                }

                _state.value = _state.value.copy(
                    log = _state.value.log + "✓ Validación de marca completada. Iniciando inyección...\n"
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
                val totalKeys = keyConfigs.size
                for ((index, keyConfig) in keyConfigs.withIndex()) {
                    val currentKeyIndex = index + 1
                    Log.i(TAG, "=== PROCESANDO LLAVE $currentKeyIndex/$totalKeys ===")
                    Log.i(TAG, "Uso: ${keyConfig.usage}")
                    Log.i(TAG, "Slot: ${keyConfig.slot}")
                    Log.i(TAG, "Tipo: ${keyConfig.keyType}")
                    
                    // NUEVO: Actualizar estado de la llave a "inyectando"
                    val updatedKeys = _state.value.keysToInject.mapIndexed { idx, item ->
                        if (idx == index) {
                            item.copy(status = KeyInjectionItemStatus.INJECTING)
                        } else {
                            item
                        }
                    }
                    
                    _state.value = _state.value.copy(
                        currentStep = currentKeyIndex,
                        progress = currentKeyIndex.toFloat() / totalKeys,
                        log = _state.value.log + "Inyectando llave $currentKeyIndex/$totalKeys: ${keyConfig.usage}\n",
                        keysToInject = updatedKeys
                    )

                    try {
                        injectKey(keyConfig, totalKeys, currentKeyIndex)
                        
                        // NUEVO: Actualizar estado de la llave a "inyectada" exitosamente
                        val successKeys = _state.value.keysToInject.mapIndexed { idx, item ->
                            if (idx == index) {
                                item.copy(status = KeyInjectionItemStatus.INJECTED)
                            } else {
                                item
                            }
                        }
                        _state.value = _state.value.copy(keysToInject = successKeys)
                    } catch (e: Exception) {
                        // NUEVO: Actualizar estado de la llave a "error"
                        val errorKeys = _state.value.keysToInject.mapIndexed { idx, item ->
                            if (idx == index) {
                                item.copy(
                                    status = KeyInjectionItemStatus.ERROR,
                                    errorMessage = e.message
                                )
                            } else {
                                item
                            }
                        }
                        _state.value = _state.value.copy(keysToInject = errorKeys)
                        throw e // Re-lanzar para que se maneje en el catch principal
                    }
                    
                    // Pausa entre inyecciones para asegurar que el puerto esté listo
                    // Aumentado a 1000ms para dar tiempo al keyreceiver de procesar y responder
                    kotlinx.coroutines.delay(1000)
                }

                val injectionEndTime = System.currentTimeMillis()
                val totalDurationMs = injectionEndTime - injectionStartTime
                Log.i(TAG, "╔═══════════════════════════════════════════════════════════════")
                Log.i(TAG, "║ === INYECCIÓN FUTUREX COMPLETADA EXITOSAMENTE ===")
                Log.i(TAG, "║ Perfil inyectado: ${profile.name}")
                Log.i(TAG, "║ Usuario: $currentUsername")
                Log.i(TAG, "║ Total de llaves inyectadas: ${profile.keyConfigurations.size}")
                Log.i(TAG, "║ Tiempo total: ${totalDurationMs}ms (${totalDurationMs / 1000}s)")
                Log.i(TAG, "║ ✓ Todos los logs de inyección han sido registrados en la base de datos")
                Log.i(TAG, "║ El Dashboard debería actualizar el contador automáticamente")
                Log.i(TAG, "╚═══════════════════════════════════════════════════════════════")

                _state.value = _state.value.copy(
                    status = InjectionStatus.SUCCESS,
                    log = _state.value.log + "¡Inyección completada exitosamente!\n"
                )

                _snackbarEvent.emit("Inyección de llaves completada")

                // NOTA: El diálogo de desinstalación ya no se muestra automáticamente
                // porque el keyreceiver se auto-elimina después de inyectar la última llave.
                // El comando 07 (UninstallApp) sigue disponible como funcionalidad opcional
                // pero debe ser invocado manualmente si es necesario.
                Log.i(TAG, "✓ Inyección completada. El keyreceiver se auto-eliminará después de la última llave.")

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
                // NO cerrar comunicación aquí - se cerrará después de resolver el diálogo de desinstalación
                Log.i(TAG, "Inyección completada, esperando respuesta del usuario para diálogo de desinstalación...")
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

    private suspend fun injectKey(keyConfig: KeyConfiguration, totalKeys: Int, currentKeyIndex: Int) {
        Log.i(TAG, "=== INICIANDO INYECCIÓN DE LLAVE FUTUREX ===")
        Log.i(TAG, "Configuración de llave:")
        Log.i(TAG, "  - Uso: ${keyConfig.usage}")
        Log.i(TAG, "  - Slot: ${keyConfig.slot}")
        Log.i(TAG, "  - Tipo: ${keyConfig.keyType}")
        Log.i(TAG, "  - Llave seleccionada: ${keyConfig.selectedKey}")
        Log.i(TAG, "  - Progreso: Llave $currentKeyIndex de $totalKeys")

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Timestamp: $startTime (inicio de inyección)")

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
            val injectionCommand = buildInjectionCommand(keyConfig, selectedKey, totalKeys, currentKeyIndex)

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

            val endTime = System.currentTimeMillis()
            val durationMs = endTime - startTime
            Log.i(TAG, "=== INYECCIÓN DE LLAVE FUTUREX COMPLETADA ===")
            Log.i(TAG, "Tiempo total de inyección: ${durationMs}ms (${durationMs / 1000}s)")

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

    private fun buildInjectionCommand(keyConfig: KeyConfiguration, selectedKey: InjectedKeyEntity, totalKeys: Int = 0, currentKeyIndex: Int = 0): ByteArray {
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
        
        // NUEVO: Agregar información de inyección (totalKeys y currentKeyIndex) al final del payload
        // Formato: 3 dígitos para totalKeys + 3 dígitos para currentKeyIndex (ej: "003001" = 3 llaves totales, llave 1)
        val totalKeysStr = if (totalKeys > 0) totalKeys.toString().padStart(3, '0') else ""
        val currentKeyIndexStr = if (currentKeyIndex > 0) currentKeyIndex.toString().padStart(3, '0') else ""
        
        // Para el protocolo Futurex, concatenamos todo en un solo string
        val payload = command + version + keySlot + ktkSlotStr + keyType + encryptionType + keyAlgorithm + keySubType +
                     keyChecksum + ktkChecksum + ksn + keyLength + keyHex + totalKeysStr + currentKeyIndexStr

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
        if (totalKeys > 0) {
            Log.i(TAG, "  - TotalKeys: $totalKeys")
            Log.i(TAG, "  - CurrentKeyIndex: $currentKeyIndex (${if (currentKeyIndex == totalKeys) "ÚLTIMA LLAVE" else "Llave $currentKeyIndex de $totalKeys"})")
        }
        Log.i(TAG, "================================================")

        // Construir el payload manualmente para Futurex
        val payloadString = command + version + keySlot + ktkSlotStr + keyType + encryptionType + keyAlgorithm + keySubType +
                           keyChecksum + ktkChecksum + ksn + keyLength + keyHex + totalKeysStr + currentKeyIndexStr

        Log.i(TAG, "=== PAYLOAD MANUAL FUTUREX ===")
        Log.i(TAG, "Payload string: $payloadString")
        Log.i(TAG, "Longitud payload: ${payloadString.length} caracteres")
        Log.i(TAG, "================================================")

        // DEBUG: Verificar cálculo del LRC
        // IMPORTANTE: Para encryptionType 02, la KTK NO va en el payload de este comando
        // La KTK se envía en un comando separado ANTES de este
        val fields = listOf(version, keySlot, ktkSlotStr, keyType, encryptionType, keyAlgorithm, keySubType,
                           keyChecksum, ktkChecksum, ksn, keyLength, keyHex, totalKeysStr, currentKeyIndexStr)
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

    private suspend fun sendData(data: ByteArray) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (comController == null) {
                throw Exception("Controlador de comunicación no inicializado")
            }

            Log.d(TAG, "📤 Iniciando envío de datos...")
            Log.d(TAG, "  Tamaño del buffer a enviar: ${data.size} bytes")
            Log.d(TAG, "  Primeros 40 caracteres (hex): ${data.toHexString().take(40)}...")
            Log.d(TAG, "  Todos los bytes (hex): ${data.toHexString()}")

            // OPTIMIZACIÓN: Limpiar buffer de entrada antes de enviar comandos críticos
            try {
                val flushBuffer = ByteArray(1024)
                var flushedBytes = 0
                while (true) {
                    val read = comController!!.readData(flushBuffer.size, flushBuffer, 50) // Timeout corto de 50ms
                    if (read <= 0) break
                    flushedBytes += read
                }
                if (flushedBytes > 0) {
                    Log.d(TAG, "  🧹 Buffer limpiado: $flushedBytes bytes descartados")
                }
            } catch (e: Exception) {
                // Si falla la limpieza, no es crítico, continuar
                Log.d(TAG, "  Buffer ya limpio o error al limpiar: ${e.message}")
            }

            val sendStartTime = System.currentTimeMillis()

            // Intentar enviar con reintentos
            var result = comController!!.write(data, 1000)
            val sendEndTime = System.currentTimeMillis()
            val sendDurationMs = sendEndTime - sendStartTime

            if (result < 0) {
                Log.w(TAG, "⚠️ Primer intento de escritura falló: $result, reintentando...")

                // Si el error es por pérdida de interfaz USB, intentar reabrir el puerto
                if (result == -1) {
                    Log.w(TAG, "⚠️ Posible pérdida de interfaz USB, intentando reabrir puerto...")
                    try {
                        // Cerrar y reabrir el puerto
                        comController!!.close()
                        kotlinx.coroutines.delay(200) // Pequeño delay antes de reabrir

                        comController!!.init(
                            EnumCommConfBaudRate.BPS_115200,
                            EnumCommConfParity.NOPAR,
                            EnumCommConfDataBits.DB_8
                        )
                        val openResult = comController!!.open()
                        if (openResult != 0) {
                            Log.e(TAG, "❌ Error al reabrir puerto: $openResult")
                            throw Exception("Error al reabrir puerto USB: $openResult")
                        }
                        Log.i(TAG, "✓ Puerto reabierto exitosamente")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error al reabrir puerto: ${e.message}")
                        throw Exception("Error al reabrir puerto USB: ${e.message}")
                    }
                }

                // Reintentar escritura después de un pequeño delay
                kotlinx.coroutines.delay(200)

                result = comController!!.write(data, 1000)
                if (result < 0) {
                    Log.e(TAG, "❌ Error al enviar datos después de reintento: $result")
                    throw Exception("Error al enviar datos: $result")
                } else {
                    Log.i(TAG, "✓ Enviados ${result} bytes en segundo intento (duración: ${sendDurationMs}ms): ${data.toHexString().take(40)}...")
                }
            } else {
                Log.i(TAG, "✓ Enviados ${result} bytes (duración: ${sendDurationMs}ms): ${data.toHexString().take(40)}...")
            }
        }
    }

    private suspend fun waitForResponse(): ByteArray {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Log.i(TAG, "Esperando respuesta (timeout: 3s)...")
            val readStartTime = System.currentTimeMillis()

            val buffer = ByteArray(1024)
            // OPTIMIZACIÓN: Reducir timeout de 10s a 3s para respuestas más rápidas
            val bytesRead = comController!!.readData(buffer.size, buffer, 3000)
            val readEndTime = System.currentTimeMillis()
            val readDurationMs = readEndTime - readStartTime

            if (bytesRead <= 0) {
                Log.e(TAG, "❌ Timeout o error al leer respuesta: $bytesRead bytes leídos")
                throw Exception("Timeout o error al leer respuesta")
            }

            val response = buffer.copyOf(bytesRead)
            Log.i(TAG, "✓ Recibidos $bytesRead bytes en ${readDurationMs}ms")
            Log.i(TAG, "  Primeros 40 caracteres hex: ${response.toHexString().take(40)}...")
            Log.d(TAG, "  Respuesta completa (hex): ${response.toHexString()}")

            response
        }
    }

    private fun processInjectionResponse(response: ByteArray, keyConfig: KeyConfiguration, @Suppress("UNUSED_PARAMETER") commandSent: ByteArray) {
        Log.i(TAG, "=== PROCESANDO RESPUESTA FUTUREX ===")
        Log.i(TAG, "Configuración de llave: ${keyConfig.usage} (Slot: ${keyConfig.slot})")
        Log.i(TAG, "Respuesta recibida: ${response.size} bytes")
        Log.d(TAG, "  Primeros 40 caracteres (hex): ${response.toHexString().take(40)}...")
        Log.d(TAG, "  Respuesta completa (hex): ${response.toHexString()}")

        val profile = _state.value.currentProfile
        val commandHex = commandSent.toHexString()
        val responseHex = response.toHexString()

        // Agregar datos al parser
        Log.i(TAG, "Agregando datos al parser Futurex...")
        messageParser!!.appendData(response)

        // Intentar parsear la respuesta
        Log.i(TAG, "Parseando respuesta del dispositivo...")
        val parseStartTime = System.currentTimeMillis()
        val parsedMessage = messageParser!!.nextMessage()
        val parseEndTime = System.currentTimeMillis()
        Log.d(TAG, "Respuesta parseada en ${parseEndTime - parseStartTime}ms")

        when (parsedMessage) {
            is InjectSymmetricKeyResponse -> {
                Log.i(TAG, "Respuesta parseada como InjectSymmetricKeyResponse:")
                Log.i(TAG, "  - Código de respuesta: ${parsedMessage.responseCode}")
                Log.i(TAG, "  - Checksum de llave: ${parsedMessage.keyChecksum}")
                Log.i(TAG, "  - Payload completo: ${parsedMessage.rawPayload}")
                Log.d(TAG, "  - Serial del dispositivo: ${parsedMessage.deviceSerial}")
                Log.d(TAG, "  - Modelo del dispositivo: ${parsedMessage.deviceModel}")
                
                if (parsedMessage.responseCode == "00") {
                    Log.i(TAG, "✓ Inyección exitosa para ${keyConfig.usage}")
                    _state.value = _state.value.copy(
                        log = _state.value.log + "✓ ${keyConfig.usage}: Inyectada exitosamente\n",
                        deviceSerial = parsedMessage.deviceSerial,
                        deviceModel = parsedMessage.deviceModel
                    )

                    // NUEVO: Extraer información del dispositivo receptor de la respuesta
                    val deviceInfo = if (parsedMessage.deviceSerial.isNotEmpty()) {
                        "Serial: ${parsedMessage.deviceSerial}, Modelo: ${parsedMessage.deviceModel}"
                    } else {
                        ""
                    }
                    if (deviceInfo.isNotEmpty()) {
                        Log.d(TAG, "Información del dispositivo receptor: $deviceInfo")
                    }

                    // Registrar log de inyección exitosa
                    injectionLogger.logSuccess(
                        commandSent = commandHex,
                        responseReceived = responseHex,
                        username = currentUsername,
                        profileName = profile?.name ?: "Desconocido",
                        keyType = keyConfig.keyType,
                        keySlot = keyConfig.slot.toIntOrNull() ?: -1,
                        deviceInfo = deviceInfo,  // NUEVO: Información del dispositivo receptor
                        notes = "Uso: ${keyConfig.usage}, KCV: ${parsedMessage.keyChecksum}"
                    )
                } else {
                    val errorCode = FuturexErrorCode.fromCode(parsedMessage.responseCode)
                    val errorMsg = errorCode?.description ?: "Error desconocido"
                    Log.e(TAG, "✗ Error en inyección de ${keyConfig.usage}: $errorMsg (Código: ${parsedMessage.responseCode})")

                    // NUEVO: Extraer información del dispositivo receptor de la respuesta
                    val deviceInfo = if (parsedMessage.deviceSerial.isNotEmpty()) {
                        "Serial: ${parsedMessage.deviceSerial}, Modelo: ${parsedMessage.deviceModel}"
                    } else {
                        ""
                    }
                    if (deviceInfo.isNotEmpty()) {
                        Log.d(TAG, "Información del dispositivo receptor en fallo: $deviceInfo")
                    }

                    // Registrar log de inyección fallida
                    injectionLogger.logFailure(
                        commandSent = commandHex,
                        responseReceived = responseHex,
                        username = currentUsername,
                        profileName = profile?.name ?: "Desconocido",
                        keyType = keyConfig.keyType,
                        keySlot = keyConfig.slot.toIntOrNull() ?: -1,
                        deviceInfo = deviceInfo,  // NUEVO: Información del dispositivo receptor
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
    suspend fun readDeviceSerial(): String? {
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
    suspend fun writeDeviceSerial(serialNumber: String): Boolean {
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
    suspend fun deleteAllKeys(): Boolean {
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
    suspend fun deleteSingleKey(keySlot: Int, keyType: String): Boolean {
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
    private suspend fun exportKEKToDevice(kek: InjectedKeyEntity) {
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
     * Usa la misma lógica confiable del keyreceiver
     */
    private fun startCableDetection() {
        // Cancelar job anterior si existe
        cableDetectionJob?.cancel()

        Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ INICIANDO DETECCIÓN AUTOMÁTICA DE CABLE USB")
        Log.i(TAG, "║ Estrategia: Hysteresis para evitar falsos positivos")
        Log.i(TAG, "╠══════════════════════════════════════════════════════════════")

        cableDetectionJob = viewModelScope.launch(Dispatchers.IO) {
            var consecutiveDetectionsToChange = 0
            val HYSTERESIS_THRESHOLD = 2 // Requiere 2 detecciones consistentes para cambiar estado
            val DETECTION_INTERVAL_CONNECTED = 3000L // 3s cuando hay cable
            val DETECTION_INTERVAL_DISCONNECTED = 5000L // 5s cuando no hay cable (menos sensible)

            while (isActive) {
                try {
                    // ⚠️ OPTIMIZACIÓN: No detectar durante inyección activa
                    if (_state.value.status == InjectionStatus.INJECTING) {
                        Log.d(TAG, "║ ⏸️ Detección pausada: inyección en progreso")
                        kotlinx.coroutines.delay(1000) // Esperar poco y volver a chequear
                        continue
                    }

                    val detected = detectCableConnection()
                    val currentState = _state.value.cableConnected

                    // Si la detección coincide con el estado actual, resetear contador
                    if (detected == currentState) {
                        consecutiveDetectionsToChange = 0
                    } else {
                        // Si cambia, incrementar contador
                        consecutiveDetectionsToChange++

                        // Solo cambiar estado si alcanza hysteresis threshold
                        if (consecutiveDetectionsToChange >= HYSTERESIS_THRESHOLD) {
                            _state.value = _state.value.copy(cableConnected = detected)
                            consecutiveDetectionsToChange = 0

                            if (detected) {
                                Log.i(TAG, "║ ✅ CABLE USB DETECTADO (confirmado $HYSTERESIS_THRESHOLD veces)!")
                                Log.i(TAG, "║    Listo para inyección")
                            } else {
                                Log.w(TAG, "║ ⚠️ CABLE USB DESCONECTADO (confirmado $HYSTERESIS_THRESHOLD veces)")
                            }
                        } else {
                            // Registro de transición pendiente (no se hizo el cambio aún)
                            Log.d(TAG, "║ 🔄 Detección transitoria (${consecutiveDetectionsToChange}/$HYSTERESIS_THRESHOLD): $detected != $currentState")
                        }
                    }

                    // Adaptable interval: más frecuente si no hay cable, menos frecuente si hay cable
                    val delay = if (currentState) DETECTION_INTERVAL_CONNECTED else DETECTION_INTERVAL_DISCONNECTED
                    kotlinx.coroutines.delay(delay)

                } catch (e: Exception) {
                    Log.e(TAG, "║ ❌ Error en detección de cable", e)
                    consecutiveDetectionsToChange = 0 // Resetear contador en error
                    kotlinx.coroutines.delay(5000) // Esperar más tiempo si hay error
                }
            }
        }

        Log.i(TAG, "║ ✓ Job de detección de cable iniciado (hysteresis mode)")
        Log.i(TAG, "╚══════════════════════════════════════════════════════════════")
    }

    /**
     * Detecta si hay un cable USB conectado usando la misma lógica confiable del keyreceiver
     * Usa múltiples métodos para mayor confiabilidad:
     * 1. UsbManager API (más confiable)
     * 2. Device Nodes (/dev/) con permisos de acceso
     * 3. System Files (/sys/bus/usb) con interfaz serial
     * 4. TTY Class (/sys/class/tty)
     * 5. CH340 Cable Detection
     * 
     * Lógica: Cable presente si AL MENOS 2 de 5 métodos lo detectan
     * O si el método 1 (UsbManager - más confiable) lo detecta
     */
    private suspend fun detectCableConnection(): Boolean {
        return try {
//            Log.d(TAG, "🔍 Iniciando detección de cable USB (5 métodos)...")
            
            // Usar los mismos métodos que keyreceiver
            val method1Result = usbCableDetector.detectUsingUsbManager()
            val method2Result = usbCableDetector.detectUsingDeviceNodes()
            val method3Result = usbCableDetector.detectUsingSystemFiles()
            val method4Result = usbCableDetector.detectUsingTtyClass()
            val method5Result = usbCableDetector.detectUsingCH340Cable()
            
            // Contar cuántos métodos detectaron
            val methodsCount = listOf(method1Result, method2Result, method3Result, method4Result, method5Result).count { it }
            
            // LÓGICA DEL KEYRECEIVER: Cable presente si AL MENOS 2 de 5 métodos lo detectan
            // O si el método 1 (UsbManager - más confiable) lo detecta
            val detected = methodsCount >= 2 || method1Result
            
            // Mostrar qué métodos específicos detectaron
            val detectingMethods = mutableListOf<String>()
            if (method1Result) detectingMethods.add("UsbManager")
            if (method2Result) detectingMethods.add("/dev/")
            if (method3Result) detectingMethods.add("/sys/bus/usb")
            if (method4Result) detectingMethods.add("/sys/class/tty")
            if (method5Result) detectingMethods.add("CH340")
            
            if (detected) {
                Log.i(TAG, "✓ Cable USB detectado: ${methodsCount}/5 métodos (${detectingMethods.joinToString(", ")})")
            } else {
                Log.w(TAG, "✗ Cable USB no detectado: 0/5 métodos")
            }
            
            detected
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error detectando cable: ${e.message}", e)
            false
        }
    }

    /**
     * Construye un comando Futurex "07" para desinstalar la aplicación KeyReceiver del dispositivo.
     * Solo funciona si la app keyreceiver es de sistema o tiene permisos DELETE_PACKAGES.
     */
    private fun buildUninstallAppCommand(): ByteArray {
        Log.i(TAG, "=== CONSTRUYENDO COMANDO DE DESINSTALACIÓN DE APP (07) ===")

        val command = "07" // Comando de desinstalación de app
        val version = "99" // Versión del comando (99 = no es un código de error válido, para diferenciar de respuesta)
        // Token de confirmación: timestamp en hex para validar origen del comando
        val confirmationToken = System.currentTimeMillis().toString(16).toUpperCase().padStart(16, '0')

        Log.i(TAG, "  - Comando: $command")
        Log.i(TAG, "  - Versión: $version")
        Log.i(TAG, "  - Token de confirmación: $confirmationToken")

        // Formatear comando Futurex
        val fields = listOf(version, confirmationToken)
        val formattedCommand = messageFormatter!!.format(command, fields)

        Log.i(TAG, "  - Tamaño total: ${formattedCommand.size} bytes")
        Log.i(TAG, "  - Comando formateado exitosamente")
        Log.i(TAG, "================================================")

        return formattedCommand
    }

    /**
     * Envía el comando de desinstalación de KeyReceiver al dispositivo.
     * Se llama después de que el usuario confirma desde el UI.
     */
    fun sendUninstallCommand() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "=== ENVIANDO COMANDO DE DESINSTALACIÓN ===")

                if (comController == null || messageParser == null || messageFormatter == null) {
                    Log.e(TAG, "Puerto no disponible para desinstalación")
                    _snackbarEvent.emit("Puerto no disponible para desinstalación")
                    return@launch
                }

                // Construir comando de desinstalación
                val uninstallCommand = buildUninstallAppCommand()

                // Enviar comando
                Log.i(TAG, "Enviando comando de desinstalación al keyreceiver...")
                sendData(uninstallCommand)

                // Esperar respuesta (que incluye ACK + Futurex response)
                Log.i(TAG, "Esperando respuesta del dispositivo (incluyendo ACK)...")
                val response = waitForResponse()

                // PASO 1: Procesar respuesta que puede incluir ACK (0x06)
                var processBuffer = response
                var ackReceived = false

                if (response.isNotEmpty() && response[0].toInt() == 0x06) {
                    Log.i(TAG, "✓ ACK (0x06) recibido - Dispositivo confirmó recepción del comando")
                    ackReceived = true
                    // Saltar el ACK byte y procesar el resto como Futurex response
                    processBuffer = response.drop(1).toByteArray()
                }

                if (!ackReceived) {
                    Log.w(TAG, "⚠️ ACK no recibido en el primer byte")
                }

                // PASO 2: Parsear respuesta Futurex
                Log.i(TAG, "Parseando respuesta Futurex del dispositivo...")
                messageParser!!.appendData(processBuffer)
                val parsedMessage = messageParser!!.nextMessage()

                // PASO 3: Procesar resultado
                when (parsedMessage) {
                    is UninstallAppResponse -> {
                        if (parsedMessage.responseCode == "00") {
                            Log.i(TAG, "✓ Comando de desinstalación confirmado por el dispositivo")
                            Log.i(TAG, "  - ACK recibido: $ackReceived")
                            Log.i(TAG, "  - Serial: ${parsedMessage.deviceSerial}")
                            Log.i(TAG, "  - Modelo: ${parsedMessage.deviceModel}")
                            _snackbarEvent.emit("✓ Desinstalación confirmada en el dispositivo")
                        } else {
                            val errorCode = FuturexErrorCode.fromCode(parsedMessage.responseCode)
                            val errorMsg = errorCode?.description ?: "Error desconocido"
                            Log.e(TAG, "✗ Error en desinstalación: $errorMsg")
                            _snackbarEvent.emit("Error en desinstalación: $errorMsg")
                        }
                    }
                    else -> {
                        Log.w(TAG, "Respuesta inesperada: ${parsedMessage?.javaClass?.simpleName}")
                        _snackbarEvent.emit("Respuesta inesperada del dispositivo")
                    }
                }

                Log.i(TAG, "================================================")

            } catch (e: Exception) {
                Log.e(TAG, "Error enviando comando de desinstalación: ${e.message}", e)
                _snackbarEvent.emit("Error: ${e.message}")
            } finally {
                // Cerrar comunicación después de intentar enviar el comando de desinstalación
                Log.i(TAG, "Cerrando comunicación después de desinstalación...")
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

    /**
     * Cancela el diálogo de desinstalación sin enviar el comando.
     * Se llama cuando el usuario hace click en "No, mantener" o cierra el diálogo.
     */
    fun cancelUninstallDialog() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "=== CANCELANDO DIÁLOGO DE DESINSTALACIÓN ===")
            Log.i(TAG, "Usuario optó por no desinstalar KeyReceiver")

            try {
                // Cerrar comunicación sin enviar comando de desinstalación
                Log.i(TAG, "Cerrando comunicación (sin enviar comando de desinstalación)...")
                closeCommunication()
            } catch (e: Exception) {
                Log.e(TAG, "Error cerrando comunicación: ${e.message}", e)
            }

            // Reiniciar polling
            viewModelScope.launch {
                Log.i(TAG, "Reiniciando polling después de cancelar desinstalación...")
                _state.value = _state.value.copy(
                    log = _state.value.log + "Desinstalación cancelada. Reiniciando polling...\n"
                )
                kotlinx.coroutines.delay(1000)
                restartPolling()
            }

            Log.i(TAG, "================================================")
        }
    }

    /**
     * Generate voucher data from the current injection state
     * @return VoucherData if injection was successful, null otherwise
     */
    fun generateVoucherData(): com.vigatec.injector.model.VoucherData? {
        return try {
            val state = _state.value
            val profile = state.currentProfile ?: return null

            // Collect injected keys information
            val injectedKeys = state.keysToInject
                .filter { it.status == KeyInjectionItemStatus.INJECTED }
                .map { item ->
                    com.vigatec.injector.model.VoucherData.InjectedKeyInfo(
                        keyUsage = item.keyConfig.usage,
                        keySlot = item.keyConfig.slot,
                        keyType = item.keyConfig.keyType,
                        kcv = item.keyConfig.selectedKey,
                        status = "INYECTADA"
                    )
                }

            // Get current date and time
            val calendar = java.util.Calendar.getInstance()
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val injectionDate = dateFormat.format(calendar.time)
            val injectionTime = timeFormat.format(calendar.time)

            // Create VoucherData
            com.vigatec.injector.model.VoucherData(
                deviceSerial = state.deviceSerial ?: "DESCONOCIDO",
                deviceModel = state.deviceModel ?: "DESCONOCIDO",
                profileName = profile.name,
                username = currentUsername,
                injectionDate = injectionDate,
                injectionTime = injectionTime,
                injectionStatus = "EXITOSA",
                keysInjected = injectedKeys,
                totalKeys = profile.keyConfigurations.size,
                successfulKeys = injectedKeys.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating voucher data", e)
            null
        }
    }

    /**
     * Print injection voucher with current state information
     * @param onSuccess Callback when printing succeeds
     * @param onError Callback when printing fails with error message
     */
    fun printVoucher(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Evitar múltiples clics si ya está imprimiendo
        if (_state.value.isPrintingVoucher) {
            Log.w(TAG, "Ignorando solicitud de impresión: ya hay una impresión en curso")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting voucher print process")
            
            // Actualizar estado a cargando
            Log.d(TAG, "Setting isPrintingVoucher = true")
            _state.value = _state.value.copy(isPrintingVoucher = true)

            val voucherData = generateVoucherData()
            if (voucherData == null) {
                Log.e(TAG, "Failed to generate voucher data")
                Log.d(TAG, "Setting isPrintingVoucher = false (error generating data)")
                _state.value = _state.value.copy(isPrintingVoucher = false)
                onError("Error al generar datos del voucher")
                return@launch
            }

            try {
                // Imprimir voucher
                Log.d(TAG, "Calling VoucherPrinter...")

                com.vigatec.injector.util.VoucherPrinter.printInjectionVoucher(
                    voucherData = voucherData,
                    onSuccess = {
                        Log.i(TAG, "Voucher printed successfully")
                        viewModelScope.launch {
                            Log.d(TAG, "Setting isPrintingVoucher = false (success)")
                            _state.value = _state.value.copy(isPrintingVoucher = false)
                            _snackbarEvent.emit("Voucher impreso exitosamente")
                            onSuccess()
                        }
                    },
                    onError = { errorMessage ->
                        Log.e(TAG, "Voucher printing failed: $errorMessage")
                        viewModelScope.launch {
                            Log.d(TAG, "Setting isPrintingVoucher = false (error callback)")
                            _state.value = _state.value.copy(isPrintingVoucher = false)
                            _snackbarEvent.emit("Error al imprimir voucher: $errorMessage")
                            onError(errorMessage)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during voucher printing", e)
                Log.d(TAG, "Setting isPrintingVoucher = false (exception)")
                _state.value = _state.value.copy(isPrintingVoucher = false)
                onError("Error: ${e.localizedMessage}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Detener monitoreo de cable al limpiar el ViewModel
        cableDetectionJob?.cancel()
        Log.d(TAG, "KeyInjectionViewModel cleared")
    }
}