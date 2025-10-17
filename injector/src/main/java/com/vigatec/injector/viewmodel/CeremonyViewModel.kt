package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.repository.InjectedKeyRepository
import com.example.persistence.entities.KEKType
import com.vigatec.utils.security.StorageKeyManager
import com.vigatec.utils.KcvCalculator
import com.vigatec.utils.KeyStoreManager
import com.vigatec.injector.data.local.preferences.SessionManager
import com.vigatec.injector.util.PermissionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val canCreateOperational: Boolean = false // Si el usuario tiene permiso para crear llaves operacionales
)

@HiltViewModel
class CeremonyViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository,
    private val sessionManager: SessionManager,
    private val permissionProvider: PermissionProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(CeremonyState(component = "E59D620E1A6D311F19342054AB01ABF7"))
    val uiState = _uiState.asStateFlow()

    init {
        // Verificar si existe KEK Storage y cargar usuario actual
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

                    // Cargar permisos del usuario
                    val canCreateKEK = permissionProvider.hasPermission(PermissionProvider.CEREMONY_KEK)
                    val canCreateOperational = permissionProvider.hasPermission(PermissionProvider.CEREMONY_OPERATIONAL)

                    android.util.Log.d("CeremonyViewModel", "Ceremony - Usuario de sesión: username=$username, role=$role")
                    android.util.Log.d("CeremonyViewModel", "Ceremony - isAdmin determinado: $isAdmin")
                    android.util.Log.d("CeremonyViewModel", "Ceremony - Permiso CEREMONY_KEK: $canCreateKEK")
                    android.util.Log.d("CeremonyViewModel", "Ceremony - Permiso CEREMONY_OPERATIONAL: $canCreateOperational")

                    _uiState.value = _uiState.value.copy(
                        isAdmin = isAdmin,
                        canCreateKEK = canCreateKEK,
                        canCreateOperational = canCreateOperational
                    )

                    android.util.Log.d("CeremonyViewModel", "Ceremony - Estado actualizado: isAdmin=${_uiState.value.isAdmin}")
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
    }

    fun onNumCustodiansChange(num: Int) {
        _uiState.value = _uiState.value.copy(numCustodians = num)
    }

    fun onComponentChange(component: String) {
        _uiState.value = _uiState.value.copy(component = component)
    }

    fun onToggleShowComponent() {
        _uiState.value = _uiState.value.copy(showComponent = !_uiState.value.showComponent)
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
    }

    fun addComponent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, partialKCV = "", componentError = null)
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
                _uiState.value = _uiState.value.copy(partialKCV = kcv, isLoading = false, componentError = null)
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

        _uiState.value = _uiState.value.copy(
            currentCustodian = next,
            components = _uiState.value.components + _uiState.value.component,
            component = nextComponent,
            partialKCV = "",
            showComponent = false
        )
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

                        // Inicializar KEK Storage en Android Keystore
                        StorageKeyManager.initializeFromCeremony(finalKeyHex)
                        addToLog("✓ KEK Storage inicializada en Android Keystore")
                        addToLog("✓ Protección hardware: Activa")
                        addToLog("================================================")
                    } catch (e: Exception) {
                        addToLog("✗ Error al inicializar KEK Storage: ${e.message}")
                        throw e
                    }
                }

                // CRÍTICO: Guardar la llave con su algoritmo detectado
                // El slot se asignará cuando se use la llave en un perfil (por ahora -1)
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = -1, // -1 indica que no hay slot asignado (se asignará en el perfil)
                    keyType = keyType, // Siempre CEREMONY_KEY (operacional)
                    keyAlgorithm = detectedAlgorithm, // Guardar el algoritmo detectado
                    kcv = finalKcv,
                    keyData = finalKeyHex, // ¡GUARDANDO LA LLAVE COMPLETA!
                    status = keyStatus,
                    isKEK = _uiState.value.selectedKEKType != KEKType.NONE, // Mantener compatibilidad con isKEK
                    kekType = _uiState.value.selectedKEKType.name, // Nuevo campo: tipo específico de KEK
                    customName = _uiState.value.customName // Nombre personalizado
                )



                _uiState.value = _uiState.value.copy(
                    currentStep = 3,
                    finalKCV = finalKcv,
                    isCeremonyFinished = true,
                    isLoading = false
                )


            } catch (e: Exception) {
                addToLog("Error al finalizar la ceremonia: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun cancelCeremony() {
        _uiState.value = CeremonyState() // Resetea al estado inicial
    }

    fun clearKekValidationError() {
        _uiState.value = _uiState.value.copy(kekValidationError = null)
    }

}