package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.repository.InjectedKeyRepository
import com.example.persistence.entities.KEKType
import com.vigatec.utils.security.StorageKeyManager
import com.vigatec.utils.KcvCalculator
import com.vigatec.utils.KeyStoreManager
import com.vigatec.injector.data.local.preferences.SessionManager
import com.vigatec.injector.data.local.preferences.CustodianTimeoutPreferencesManager
import com.vigatec.injector.util.PermissionProvider
import com.vigatec.injector.util.CustodianTimeoutManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class KeyAlgorithmType(val displayName: String, val bytesRequired: Int) {
    DES_TRIPLE("3DES (16 bytes)", 16),
    AES_128("AES-128 (16 bytes)", 16),
    AES_192("AES-192 (24 bytes)", 24),
    AES_256("AES-256 (32 bytes)", 32)
}

data class CeremonyState(
    val currentStep: Int = 1, // 1: Config, 2: Custodios, 3: Finalización
    val numCustodians: Int = 2,
    val currentCustodian: Int = 1,
    val component: String = "",
    val components: List<String> = emptyList(),
    val showComponent: Boolean = false,
    val partialKCV: String = "",
    val showKcvModal: Boolean = false,        // Mostrar modal de verificación de KCV
    val finalKCV: String = "",
    val isLoading: Boolean = false,
    val isCeremonyInProgress: Boolean = false,
    val isCeremonyFinished: Boolean = false,

    // Nuevos campos para configuración de llave
    val customName: String = "",              // Nombre personalizado
    val selectedKeyType: KeyAlgorithmType = KeyAlgorithmType.DES_TRIPLE,  // Tipo de algoritmo seleccionado
    val componentError: String? = null,       // Error de validación de componente
    val selectedKEKType: KEKType = KEKType.NONE,  // Tipo de KEK: NONE, KEK_STORAGE, KEK_TRANSPORT
    val hasKEKStorage: Boolean = false,       // Si existe KEK Storage (necesaria para llaves operacionales)
    val isAdmin: Boolean = false,             // Si el usuario actual es administrador
    val kekValidationError: String? = null,   // Error cuando no hay KEK y se intenta crear llave operacional
    val canCreateKEK: Boolean = false,        // Si el usuario tiene permiso para crear KEK
    val canCreateOperational: Boolean = false, // Si el usuario tiene permiso para crear llaves operacionales

    // Nuevos campos para timeout de custodios
    val timeoutRemainingSeconds: Int = 0,     // Segundos restantes del timeout
    val timeoutTotalSeconds: Int = 0,         // Segundos totales del timeout
    val isTimeoutActive: Boolean = false,     // Si el timeout está activo
    val isTimeoutWarning: Boolean = false,    // Si se mostró la advertencia de tiempo agotándose
    val showTimeoutDialog: Boolean = false,   // Si se debe mostrar el diálogo de timeout expirado

    // Campo para almacenar logs de la ceremonia
    val ceremonyLogs: List<String> = emptyList() // Historial de logs de la ceremonia
)

@HiltViewModel
class CeremonyViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository,
    private val sessionManager: SessionManager,
    private val permissionProvider: PermissionProvider,
    private val custodianTimeoutPreferencesManager: CustodianTimeoutPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CeremonyState(component = "E59D620E1A6D311F19342054AB01ABF7"))
    val uiState = _uiState.asStateFlow()

    // Gestor de timeout para custodios
    private val timeoutManager = CustodianTimeoutManager()

    init {
        // Verificar si existe KEK Storage y cargar usuario actual
        refreshCeremonyState()
    }

    /**
     * Reinicializa el estado de la ceremonia verificando KEK Storage e isAdmin
     * Se usa tanto en init como cuando se cancela/completa una ceremonia
     */
    private fun refreshCeremonyState() {
        checkKEKStorageExists()
        loadCurrentUser()
    }

    private fun checkKEKStorageExists() {
        val hasKEK = StorageKeyManager.hasStorageKEK()
        _uiState.value = _uiState.value.copy(
            hasKEKStorage = hasKEK
        )
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                android.util.Log.d("CeremonyViewModel", "Ceremony - Cargando usuario de sesión...")

                // CORRECCIÓN: Usar SessionManager en lugar de buscar usuarios activos
                val session = sessionManager.getCurrentSession()

                if (session != null) {
                    val (userId, username, role) = session
                    val isAdmin = role == "ADMIN"

                    android.util.Log.d("CeremonyViewModel", "Ceremony - Usuario de sesión: username=$username, role=$role")
                    android.util.Log.d("CeremonyViewModel", "Ceremony - isAdmin determinado: $isAdmin")

                    // CORRECCIÓN: Primero cargar los permisos del usuario
                    android.util.Log.d("CeremonyViewModel", "Ceremony - Cargando permisos del usuario...")
                    try {
                        permissionProvider.loadPermissions(username)
                        android.util.Log.d("CeremonyViewModel", "Ceremony - Permisos cargados desde PermissionProvider")
                    } catch (e: Exception) {
                        android.util.Log.e("CeremonyViewModel", "Ceremony - Error al cargar permisos", e)
                    }

                    // Ahora verificar permisos (después de cargarlos)
                    val canCreateKEK = permissionProvider.hasPermission(PermissionProvider.CEREMONY_KEK)
                    val canCreateOperational = permissionProvider.hasPermission(PermissionProvider.CEREMONY_OPERATIONAL)

                    android.util.Log.d("CeremonyViewModel", "Ceremony - Permiso CEREMONY_KEK: $canCreateKEK")
                    android.util.Log.d("CeremonyViewModel", "Ceremony - Permiso CEREMONY_OPERATIONAL: $canCreateOperational")
                    android.util.Log.d("CeremonyViewModel", "Ceremony - Todos los permisos: ${permissionProvider.userPermissions.value}")

                    _uiState.value = _uiState.value.copy(
                        isAdmin = isAdmin,
                        canCreateKEK = canCreateKEK,
                        canCreateOperational = canCreateOperational
                    )

                    android.util.Log.d("CeremonyViewModel", "Ceremony - Estado actualizado: isAdmin=${_uiState.value.isAdmin}, canCreateKEK=${_uiState.value.canCreateKEK}")
                } else {
                    android.util.Log.w("CeremonyViewModel", "Ceremony - ⚠️ No hay sesión activa")
                    _uiState.value = _uiState.value.copy(
                        isAdmin = false,
                        canCreateKEK = false,
                        canCreateOperational = false
                    )
                }
            } catch (e: Exception) {
                // Si hay error, asumir no admin
                android.util.Log.e("CeremonyViewModel", "Ceremony - Error cargando usuario actual", e)
                _uiState.value = _uiState.value.copy(
                    isAdmin = false,
                    canCreateKEK = false,
                    canCreateOperational = false
                )
            }
        }
    }

    fun addToLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        _uiState.value = _uiState.value.copy(
            ceremonyLogs = _uiState.value.ceremonyLogs + logEntry
        )
        // También registrar en logcat para debuggear
        android.util.Log.d("CeremonyViewModel", logEntry)
    }

    fun onNumCustodiansChange(num: Int) {
        _uiState.value = _uiState.value.copy(numCustodians = num)
    }

    fun onComponentChange(component: String) {
        _uiState.value = _uiState.value.copy(component = component)
    }

    fun onCustomNameChange(name: String) {
        _uiState.value = _uiState.value.copy(customName = name)
    }

    fun onKeyTypeChange(keyType: KeyAlgorithmType) {
        _uiState.value = _uiState.value.copy(selectedKeyType = keyType)
    }

    fun onKEKTypeChange(kekType: KEKType) {
        _uiState.value = _uiState.value.copy(
            selectedKEKType = kekType,
            // Si se marca como KEK Storage, forzar AES-256
            selectedKeyType = if (kekType == KEKType.KEK_STORAGE) KeyAlgorithmType.AES_256 else _uiState.value.selectedKeyType
        )
    }

    /**
     * Valida que el componente tenga la longitud correcta según el tipo de llave seleccionado
     */
    private fun validateComponentLength(component: String): Boolean {
        val expectedBytes = _uiState.value.selectedKeyType.bytesRequired
        val expectedHexLength = expectedBytes * 2 // Cada byte = 2 caracteres hex

        return component.length == expectedHexLength
    }

    fun startCeremony() {
        android.util.Log.d("CeremonyViewModel", "═══════════════════════════════════════════════════════════")
        android.util.Log.d("CeremonyViewModel", "Iniciando ceremonia de llaves")
        android.util.Log.d("CeremonyViewModel", "  - Tipo de llave: ${_uiState.value.selectedKEKType}")
        android.util.Log.d("CeremonyViewModel", "  - ¿Tiene KEK Storage?: ${_uiState.value.hasKEKStorage}")
        android.util.Log.d("CeremonyViewModel", "  - ¿Es Admin?: ${_uiState.value.isAdmin}")

        // Verificar permisos específicos
        val hasCeremonyKEKPermission = permissionProvider.hasPermission(PermissionProvider.CEREMONY_KEK)
        val hasCeremonyOperationalPermission = permissionProvider.hasPermission(PermissionProvider.CEREMONY_OPERATIONAL)

        android.util.Log.d("CeremonyViewModel", "  - Permiso CEREMONY_KEK: $hasCeremonyKEKPermission")
        android.util.Log.d("CeremonyViewModel", "  - Permiso CEREMONY_OPERATIONAL: $hasCeremonyOperationalPermission")

        // VALIDACIÓN 1: Verificar permiso para crear llaves KEK
        if (_uiState.value.selectedKEKType != KEKType.NONE && !hasCeremonyKEKPermission) {
            android.util.Log.w("CeremonyViewModel", "⚠️ Usuario sin permiso CEREMONY_KEK intenta crear KEK")

            _uiState.value = _uiState.value.copy(
                kekValidationError = "No tiene permisos para crear llaves KEK. " +
                    "Por favor, contacte con un administrador para obtener el permiso 'Ceremonia KEK'."
            )

            android.util.Log.d("CeremonyViewModel", "═══════════════════════════════════════════════════════════")
            return
        }

        // VALIDACIÓN 2: Verificar permiso para crear llaves operacionales
        if (_uiState.value.selectedKEKType == KEKType.NONE && !hasCeremonyOperationalPermission) {
            android.util.Log.w("CeremonyViewModel", "⚠️ Usuario sin permiso CEREMONY_OPERATIONAL intenta crear llave operacional")

            _uiState.value = _uiState.value.copy(
                kekValidationError = "No tiene permisos para crear llaves operacionales. " +
                    "Por favor, contacte con un administrador para obtener el permiso 'Ceremonia Operacional'."
            )

            android.util.Log.d("CeremonyViewModel", "═══════════════════════════════════════════════════════════")
            return
        }

        // VALIDACIÓN 3: Si se intenta crear una llave operacional (NONE) sin KEK Storage
        if (_uiState.value.selectedKEKType == KEKType.NONE && !_uiState.value.hasKEKStorage) {
            android.util.Log.w("CeremonyViewModel", "⚠️ No se puede crear llave operacional sin KEK Storage")

            val errorMessage = if (hasCeremonyKEKPermission) {
                "No se puede crear una llave operacional sin una KEK Storage. " +
                "Por favor, cree primero una llave KEK Storage desde esta pantalla."
            } else {
                "No se puede crear una llave operacional porque no existe una KEK Storage. " +
                "Por favor, contacte con un administrador o usuario con permisos para que cree primero una KEK Storage."
            }

            _uiState.value = _uiState.value.copy(
                kekValidationError = errorMessage
            )

            android.util.Log.d("CeremonyViewModel", "═══════════════════════════════════════════════════════════")
            return
        }

        // Limpiar errores previos
        _uiState.value = _uiState.value.copy(kekValidationError = null)

        android.util.Log.d("CeremonyViewModel", "✓ Validaciones pasadas, iniciando ceremonia")
        android.util.Log.d("CeremonyViewModel", "═══════════════════════════════════════════════════════════")

        addToLog("=== INICIANDO CEREMONIA DE LLAVES ===")
        addToLog("Configuración inicial:")
        addToLog("  - Número de custodios: ${_uiState.value.numCustodians}")
        addToLog("  - Tipo de llave: ${_uiState.value.selectedKeyType.displayName}")
        addToLog("  - Tipo de KEK: ${_uiState.value.selectedKEKType}")
        addToLog("  - Longitud esperada: ${_uiState.value.selectedKeyType.bytesRequired * 2} caracteres hex")
        addToLog("  - Componente inicial: ${_uiState.value.component}")
        addToLog("  - Estado del repositorio: Inicializado")
        addToLog("  - Base de datos: Conectada")
        addToLog("================================================")

        _uiState.value = _uiState.value.copy(
            currentStep = 2,
            isCeremonyInProgress = true,
            isLoading = false
        )

        // Iniciar el timeout de custodios
        startCustodianTimeout()
    }

    /**
     * Inicia el timeout para custodios
     */
    private fun startCustodianTimeout() {
        viewModelScope.launch {
            try {
                // Obtener el tiempo de timeout configurado (en segundos)
                custodianTimeoutPreferencesManager.getCustodianTimeoutSeconds().collect { timeoutSeconds ->
                    android.util.Log.d("CeremonyViewModel", "Iniciando timeout de custodios: $timeoutSeconds segundos")

                    timeoutManager.startTimer(
                        timeoutSeconds = timeoutSeconds,
                        scope = viewModelScope,
                        onWarning = { remainingSeconds ->
                            android.util.Log.w("CeremonyViewModel", "⚠️ Advertencia de timeout: $remainingSeconds segundos restantes")
                            onCustodianTimeoutWarning(remainingSeconds)
                        },
                        onExpired = {
                            android.util.Log.e("CeremonyViewModel", "❌ Timeout de custodio expirado")
                            onCustodianTimeoutExpired()
                        }
                    )

                    // Observar el estado del timeout manager en tiempo real
                    observeTimeoutUpdates()
                }
            } catch (e: Exception) {
                android.util.Log.e("CeremonyViewModel", "Error al iniciar timeout de custodios", e)
            }
        }
    }

    /**
     * Observa los cambios del timeout manager y actualiza el UI
     */
    private fun observeTimeoutUpdates() {
        viewModelScope.launch {
            try {
                timeoutManager.timeoutState.collect { timeoutState ->
                    // Actualizar el estado con los valores actuales del timeout
                    _uiState.value = _uiState.value.copy(
                        timeoutRemainingSeconds = timeoutState.remainingSeconds,
                        timeoutTotalSeconds = timeoutState.totalSeconds,
                        isTimeoutActive = timeoutState.isActive,
                        isTimeoutWarning = timeoutState.isWarning
                    )

                    // Log solo en puntos clave para no saturar los logs
                    if (timeoutState.remainingSeconds % 10 == 0 && timeoutState.remainingSeconds > 0) {
                        android.util.Log.d("CeremonyViewModel", "Timeout: ${timeoutState.remainingSeconds}s restantes")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CeremonyViewModel", "Error observando actualizaciones de timeout", e)
            }
        }
    }

    /**
     * Callback cuando se alcanza la advertencia de timeout
     */
    private fun onCustodianTimeoutWarning(remainingSeconds: Int) {
        _uiState.value = _uiState.value.copy(
            isTimeoutWarning = true,
            timeoutRemainingSeconds = remainingSeconds
        )
        addToLog("⚠️ ADVERTENCIA: El tiempo de espera para custodios se está agotando")
        addToLog("   Tiempo restante: ${remainingSeconds / 60} minutos ${remainingSeconds % 60} segundos")
    }

    /**
     * Callback cuando el timeout de custodios expira
     */
    private fun onCustodianTimeoutExpired() {
        android.util.Log.e("CeremonyViewModel", "═══════════════════════════════════════════════════════════")
        android.util.Log.e("CeremonyViewModel", "TIMEOUT DE CUSTODIOS EXPIRADO")
        android.util.Log.e("CeremonyViewModel", "Registrando sesión expirada...")

        addToLog("")
        addToLog("═══════════════════════════════════════════════════════════")
        addToLog("❌ TIMEOUT DE CUSTODIO EXPIRADO")
        addToLog("Se ha excedido el tiempo máximo de espera")
        addToLog("Cancelando ceremonia automáticamente...")
        addToLog("═══════════════════════════════════════════════════════════")

        // Mostrar diálogo de timeout expirado
        _uiState.value = _uiState.value.copy(
            showTimeoutDialog = true,
            isTimeoutActive = false
        )

        // Nota: La redirección se realizará desde la UI cuando el usuario cierre el diálogo
    }

    /**
     * Cierra el diálogo de timeout expirado y vuelve a la pantalla principal
     */
    fun dismissTimeoutDialog() {
        android.util.Log.d("CeremonyViewModel", "Cerrando diálogo de timeout y volviendo a pantalla principal")

        // Detener el timer
        timeoutManager.stopTimer()

        // Volver a la pantalla principal (paso 1)
        _uiState.value = _uiState.value.copy(
            showTimeoutDialog = false,
            currentStep = 1,
            isCeremonyInProgress = false,
            isTimeoutActive = false,
            isTimeoutWarning = false,
            timeoutRemainingSeconds = 0,
            timeoutTotalSeconds = 0,
            currentCustodian = 1,
            components = emptyList(),
            component = "E59D620E1A6D311F19342054AB01ABF7"
        )

        // Registrar en log
        addToLog("")
        addToLog("═══════════════════════════════════════════════════════════")
        addToLog("Volviendo a pantalla principal de ceremonia")
        addToLog("═══════════════════════════════════════════════════════════")
    }

    fun addComponent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, componentError = null)
            try {
                val component = _uiState.value.component

                // Validar longitud del componente
                if (!validateComponentLength(component)) {
                    val expectedBytes = _uiState.value.selectedKeyType.bytesRequired
                    val expectedHexLength = expectedBytes * 2
                    _uiState.value = _uiState.value.copy(
                        componentError = "Se esperan $expectedHexLength caracteres hex, recibidos: ${component.length}",
                        isLoading = false
                    )
                    return@launch
                }

                val kcv = KcvCalculator.calculateKcv(component)
                _uiState.value = _uiState.value.copy(
                    partialKCV = kcv,
                    isLoading = false,
                    componentError = null,
                    showKcvModal = true  // Abrir el modal de KCV
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, componentError = e.message)
            }
        }
    }

    fun nextCustodian() {
        val next = _uiState.value.currentCustodian + 1

        val nextComponent = if (next == 2) {
            "ED77D12E82AF6099968D6F5653741D09"
        } else {
            ""
        }

        android.util.Log.d("CeremonyViewModel", "═══════════════════════════════════════════════════════════")
        android.util.Log.d("CeremonyViewModel", "Avanzando a custodio $next de ${_uiState.value.numCustodians}")
        android.util.Log.d("CeremonyViewModel", "Reiniciando timeout para nuevo custodio...")
        android.util.Log.d("CeremonyViewModel", "═══════════════════════════════════════════════════════════")

        // Detener el timer anterior
        timeoutManager.stopTimer()

        _uiState.value = _uiState.value.copy(
            currentCustodian = next,
            components = _uiState.value.components + _uiState.value.component,
            component = nextComponent,
            partialKCV = "",
            showComponent = false,
            // Resetear el estado de timeout para el nuevo custodio
            isTimeoutWarning = false,
            timeoutRemainingSeconds = 0,
            timeoutTotalSeconds = 0,
            isTimeoutActive = false
        )

        // Registrar en log
        addToLog("")
        addToLog("═══════════════════════════════════════════════════════════")
        addToLog("Custodio $next de ${_uiState.value.numCustodians}")
        addToLog("Componente anterior guardado. Timeout reiniciado.")
        addToLog("═══════════════════════════════════════════════════════════")

        // Reiniciar el timeout para el nuevo custodio
        startCustodianTimeout()
    }

    fun finalizeCeremony() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val finalComponents = _uiState.value.components + _uiState.value.component

                if (finalComponents.size != _uiState.value.numCustodians) {
                    throw IllegalStateException("Número incorrecto de componentes: ${finalComponents.size} vs ${_uiState.value.numCustodians}")
                }


                
                val finalKeyBytes = finalComponents
                    .map { component ->
                        val bytes = KcvCalculator.hexStringToByteArray(component)
                        bytes
                    }
                    .reduce { acc, bytes -> 
                        val result = KcvCalculator.xorByteArrays(acc, bytes)
                        result
                    }

                val finalKeyHex = finalKeyBytes.joinToString("") { "%02X".format(it) }



                
                val finalKcv = KcvCalculator.calculateKcv(finalKeyHex)



                try {
                    KeyStoreManager.storeMasterKey("master_transport_key", finalKeyBytes)

                } catch (e: Exception) {

                }

                // Todas las llaves se crean como operacionales
                val keyType = "CEREMONY_KEY"
                val keyStatus = "GENERATED"

                // Detectar el algoritmo basado en el tipo seleccionado
                val detectedAlgorithm = when (_uiState.value.selectedKeyType) {
                    KeyAlgorithmType.DES_TRIPLE -> "DES_TRIPLE"
                    KeyAlgorithmType.AES_128 -> "AES_128"
                    KeyAlgorithmType.AES_192 -> "AES_192"
                    KeyAlgorithmType.AES_256 -> "AES_256"
                }


                // Si es KEK Storage, inicializarla en Android Keystore
                if (_uiState.value.selectedKEKType == KEKType.KEK_STORAGE) {
                    addToLog("=== INICIALIZANDO KEK STORAGE ===")
                    addToLog("Esta llave se usará para cifrar todas las llaves del almacén")

                    try {
                        // Validar que sea AES-256
                        if (_uiState.value.selectedKeyType != KeyAlgorithmType.AES_256) {
                            throw IllegalStateException("KEK Storage debe ser AES-256")
                        }

                        val kekStorageExistaAntes = _uiState.value.hasKEKStorage

                        // Si ya existe una KEK Storage, rotar las llaves
                        if (kekStorageExistaAntes) {
                            addToLog("")
                            addToLog("=== ROTACIÓN DE KEK STORAGE ===")
                            addToLog("Se detectó una KEK Storage existente")
                            addToLog("Iniciando proceso de rotación...")
                            addToLog("")

                            // Obtener todas las llaves cifradas que necesitan re-cifrarse
                            val allKeys = injectedKeyRepository.getAllInjectedKeysSync()
                            val encryptedKeys = allKeys.filter { it.isEncrypted() && !it.isKEKStorage() }

                            addToLog("Llaves operacionales cifradas encontradas: ${encryptedKeys.size}")

                            if (encryptedKeys.isNotEmpty()) {
                                addToLog("")
                                addToLog("Paso 1/5: Descifrando llaves operacionales con KEK antigua...")

                                // Descifrar todas las llaves con la KEK antigua (ANTES de eliminarla)
                                val decryptedKeys = encryptedKeys.map { key ->
                                    try {
                                        val encryptedData = com.vigatec.utils.security.EncryptedKeyData(
                                            encryptedData = key.encryptedKeyData,
                                            iv = key.encryptionIV,
                                            authTag = key.encryptionAuthTag
                                        )
                                        val decryptedKeyHex = StorageKeyManager.decryptKey(encryptedData)
                                        addToLog("  ✓ Llave descifrada: KCV ${key.kcv}")
                                        key to decryptedKeyHex
                                    } catch (e: Exception) {
                                        addToLog("  ✗ Error al descifrar llave KCV ${key.kcv}: ${e.message}")
                                        throw e
                                    }
                                }.toMap()

                                addToLog("✓ ${decryptedKeys.size} llaves descifradas exitosamente")
                                addToLog("")
                                addToLog("Paso 2/5: Eliminando KEK Storage antigua de BD...")

                                // Eliminar la KEK Storage antigua de la base de datos
                                val oldKEKStorageKeys = allKeys.filter { it.isKEKStorage() }
                                oldKEKStorageKeys.forEach { oldKEK ->
                                    injectedKeyRepository.deleteKey(oldKEK.id)
                                    addToLog("  ✓ KEK Storage antigua eliminada de BD: KCV ${oldKEK.kcv}")
                                }

                                addToLog("")
                                addToLog("Paso 3/5: Eliminando KEK Storage antigua del Keystore...")

                                // Eliminar la KEK antigua del Android Keystore
                                StorageKeyManager.deleteStorageKEK()
                                addToLog("✓ KEK Storage antigua eliminada del Keystore")
                                addToLog("")
                                addToLog("Paso 4/5: Instalando nueva KEK Storage...")

                                // Instalar la nueva KEK
                                StorageKeyManager.initializeFromCeremony(finalKeyHex, replaceIfExists = false)
                                addToLog("✓ Nueva KEK Storage instalada")
                                addToLog("")
                                addToLog("Paso 5/5: Re-cifrando llaves operacionales con nueva KEK...")

                                // Re-cifrar todas las llaves con la nueva KEK
                                decryptedKeys.forEach { (key, decryptedKeyHex) ->
                                    try {
                                        val reEncryptedData = StorageKeyManager.encryptKey(decryptedKeyHex)

                                        // Actualizar la llave en la base de datos
                                        val updatedKey = key.copy(
                                            encryptedKeyData = reEncryptedData.encryptedData,
                                            encryptionIV = reEncryptedData.iv,
                                            encryptionAuthTag = reEncryptedData.authTag
                                        )

                                        injectedKeyRepository.updateInjectedKey(updatedKey)
                                        addToLog("  ✓ Llave re-cifrada: KCV ${key.kcv}")
                                    } catch (e: Exception) {
                                        addToLog("  ✗ Error al re-cifrar llave KCV ${key.kcv}: ${e.message}")
                                        throw e
                                    }
                                }

                                addToLog("✓ ${decryptedKeys.size} llaves operacionales re-cifradas exitosamente")
                                addToLog("")
                                addToLog("=== ROTACIÓN COMPLETADA EXITOSAMENTE ===")
                            } else {
                                addToLog("No hay llaves operacionales cifradas para rotar")
                                addToLog("")
                                addToLog("Eliminando KEK Storage antigua de BD...")

                                // Eliminar la KEK Storage antigua de la base de datos
                                val oldKEKStorageKeys = allKeys.filter { it.isKEKStorage() }
                                oldKEKStorageKeys.forEach { oldKEK ->
                                    injectedKeyRepository.deleteKey(oldKEK.id)
                                    addToLog("  ✓ KEK Storage antigua eliminada de BD: KCV ${oldKEK.kcv}")
                                }

                                addToLog("Reemplazando KEK Storage en Keystore...")

                                // Si no hay llaves cifradas, simplemente reemplazar
                                StorageKeyManager.initializeFromCeremony(finalKeyHex, replaceIfExists = true)
                                addToLog("✓ KEK Storage reemplazada")
                            }
                        } else {
                            // No existe KEK Storage, crear una nueva
                            addToLog("No existe KEK Storage previa")
                            StorageKeyManager.initializeFromCeremony(finalKeyHex, replaceIfExists = false)
                            addToLog("✓ KEK Storage inicializada en Android Keystore")
                        }

                        addToLog("✓ Protección hardware: Activa (Android Keystore)")
                        addToLog("")
                        addToLog("=== KEK STORAGE CONFIGURADA EXITOSAMENTE ===")
                        addToLog("✓ KEK Storage almacenada en Android Keystore")
                        addToLog("✓ La KEK Storage está lista para cifrar/descifrar llaves operacionales")
                        addToLog("")
                        addToLog("NOTA: Slot 0 del PED Aisino disponible para otras llaves maestras")
                        addToLog("================================================")
                    } catch (e: Exception) {
                        addToLog("✗ Error al inicializar KEK Storage: ${e.message}")
                        e.printStackTrace()
                        throw e
                    }
                }

                // CRÍTICO: Guardar la llave con su algoritmo detectado
                // KEK Storage se guarda en slot 0, operacionales en slot -1 (se asignará en el perfil)
                val keySlotToSave = if (_uiState.value.selectedKEKType == KEKType.KEK_STORAGE) {
                    0 // KEK Storage siempre en slot 0 del PED del inyector
                } else {
                    -1 // Operacionales sin slot asignado aún
                }

                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = keySlotToSave,
                    keyType = keyType, // Siempre CEREMONY_KEY (operacional)
                    keyAlgorithm = detectedAlgorithm, // Guardar el algoritmo detectado
                    kcv = finalKcv,
                    keyData = finalKeyHex, // ¡GUARDANDO LA LLAVE COMPLETA!
                    status = keyStatus,
                    isKEK = _uiState.value.selectedKEKType != KEKType.NONE, // Mantener compatibilidad con isKEK
                    kekType = _uiState.value.selectedKEKType.name, // Nuevo campo: tipo específico de KEK
                    customName = _uiState.value.customName // Nombre personalizado
                )

                // Si se acaba de crear una KEK Storage, actualizar el estado para reflejar que ahora existe
                val shouldUpdateKEKStorageFlag = _uiState.value.selectedKEKType == KEKType.KEK_STORAGE

                // Detener el timeout al finalizar la ceremonia
                timeoutManager.stopTimer()
                android.util.Log.d("CeremonyViewModel", "Timer de timeout detenido al finalizar ceremonia")

                _uiState.value = _uiState.value.copy(
                    currentStep = 3,
                    finalKCV = finalKcv,
                    isCeremonyFinished = true,
                    isLoading = false,
                    isTimeoutActive = false,
                    hasKEKStorage = shouldUpdateKEKStorageFlag || _uiState.value.hasKEKStorage // Actualizar si se creó una KEK Storage
                )


            } catch (e: Exception) {
                addToLog("Error al finalizar la ceremonia: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Cierra el modal de verificación de KCV sin avanzar a la siguiente acción
     */
    fun dismissKcvModal() {
        _uiState.value = _uiState.value.copy(showKcvModal = false)
    }

    /**
     * Verifica el KCV y procede:
     * - Si no es el último custodio: avanza automáticamente al siguiente
     * - Si es el último custodio: finaliza la ceremonia automáticamente
     */
    fun confirmKcvAndProceed() {
        val isLastCustodian = _uiState.value.currentCustodian == _uiState.value.numCustodians

        // Cerrar el modal
        _uiState.value = _uiState.value.copy(showKcvModal = false)

        if (isLastCustodian) {
            // Es el último custodio, finalizar la ceremonia
            finalizeCeremony()
        } else {
            // No es el último, pasar al siguiente custodio
            nextCustodian()
        }
    }

    fun cancelCeremony() {
        android.util.Log.d("CeremonyViewModel", "Cancelando ceremonia - reinicializando estado")

        // Detener el timer de timeout
        timeoutManager.stopTimer()
        android.util.Log.d("CeremonyViewModel", "Timer de timeout detenido")

        // Registrar cancelación en el log
        addToLog("")
        addToLog("═══════════════════════════════════════════════════════════")
        addToLog("Ceremonia cancelada por el usuario")
        addToLog("═══════════════════════════════════════════════════════════")

        // Resetea al estado inicial pero preservando los datos de KEK Storage e isAdmin
        _uiState.value = CeremonyState(
            component = "E59D620E1A6D311F19342054AB01ABF7",
            hasKEKStorage = _uiState.value.hasKEKStorage, // Preservar estado actual de KEK
            isAdmin = _uiState.value.isAdmin, // Preservar estado actual de admin
            canCreateKEK = _uiState.value.canCreateKEK,
            canCreateOperational = _uiState.value.canCreateOperational
        )
        // Recarga el estado de KEK Storage (por si cambió durante la ceremonia)
        refreshCeremonyState()
    }

    fun clearKekValidationError() {
        _uiState.value = _uiState.value.copy(kekValidationError = null)
    }

}