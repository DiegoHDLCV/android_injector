package com.vigatec.injector.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.persistence.entities.InjectedKeyEntity
import com.vigatec.persistence.repository.InjectedKeyRepository
import com.vigatec.persistence.repository.ProfileRepository
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.data.local.preferences.SessionManager
import com.vigatec.utils.security.StorageKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeyWithProfiles(
    val key: InjectedKeyEntity,
    val assignedProfiles: List<String> = emptyList()
)

data class KeyVaultState(
    val keysWithProfiles: List<KeyWithProfiles> = emptyList(),
    val loading: Boolean = true,
    val selectedKey: InjectedKeyEntity? = null,
    val showDeleteModal: Boolean = false,
    val showViewModal: Boolean = false,
    val showClearAllConfirmation: Boolean = false,  // Diálogo de confirmación para eliminar todas
    val showImportJsonDialog: Boolean = false,  // Diálogo para importar desde JSON
    val currentUser: User? = null,  // Usuario actual para permisos
    val isAdmin: Boolean = false,     // Flag rápido para verificar si es admin
    val showKEKStoragePasswordDialog: Boolean = false,  // Diálogo para pedir contraseña antes de mostrar KEK Storage
    val showKEKStorage: Boolean = false,  // Flag para mostrar/ocultar KEK Storage
    val kekStoragePasswordError: String? = null  // Error de contraseña al mostrar KEK Storage
)

@HiltViewModel
class KeyVaultViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository,
    private val profileRepository: ProfileRepository,
    private val sessionManager: SessionManager,
    private val userRepository: com.vigatec.injector.repository.UserRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "KeyVaultViewModel"
    }

    private val _uiState = MutableStateFlow(KeyVaultState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            Log.d(TAG, "KeyVault - Iniciando ViewModel")
            loadCurrentUser() // Primero cargar usuario
            loadKeys() // Luego cargar llaves
        }
    }

    /**
     * Carga el usuario actual para determinar permisos
     */
    private suspend fun loadCurrentUser() {
        try {
            Log.d(TAG, "KeyVault - Cargando usuario de sesión...")

            // CORRECCIÓN: Usar SessionManager en lugar de buscar usuarios activos
            val session = sessionManager.getCurrentSession()

            if (session != null) {
                val (userId, username, role) = session
                val isAdmin = role == "ADMIN"

                Log.d(TAG, "KeyVault - Usuario de sesión: username=$username, role=$role")
                Log.d(TAG, "KeyVault - isAdmin determinado: $isAdmin")

                _uiState.value = _uiState.value.copy(
                    currentUser = null, // Ya no necesitamos el objeto User completo aquí
                    isAdmin = isAdmin
                )

                Log.d(TAG, "KeyVault - Estado actualizado: isAdmin=${_uiState.value.isAdmin}")
            } else {
                Log.w(TAG, "KeyVault - ⚠️ No hay sesión activa")
                _uiState.value = _uiState.value.copy(
                    currentUser = null,
                    isAdmin = false
                )
            }
        } catch (e: Exception) {
            // Si hay error, mantener valores por defecto
            Log.e(TAG, "KeyVault - Error cargando usuario actual", e)
            e.printStackTrace()
        }
    }

    fun loadKeys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            Log.d(TAG, "KeyVault - loadKeys() - isAdmin actual: ${_uiState.value.isAdmin}")
            
            injectedKeyRepository.getAllInjectedKeys().collect { keys ->
                Log.d(TAG, "KeyVault - loadKeys() - Total llaves recibidas: ${keys.size}")
                
                // Filtrar KEK Storage - siempre oculto a menos que se haya autenticado con contraseña
                val filteredKeys = if (_uiState.value.showKEKStorage) {
                    Log.d(TAG, "KeyVault - KEK Storage visible (autenticado con contraseña)")
                    keys // Mostrar todas las llaves incluyendo KEK Storage
                } else {
                    val kekStorageCount = keys.count { it.isKEKStorage() }
                    Log.d(TAG, "KeyVault - Ocultando $kekStorageCount KEK Storage (requiere autenticación)")
                    keys.filter { !it.isKEKStorage() } // Ocultar KEK Storage hasta autenticación
                }
                
                Log.d(TAG, "KeyVault - loadKeys() - Llaves después del filtro: ${filteredKeys.size}")
                
                val keysWithProfiles = filteredKeys.map { key ->
                    val profiles = profileRepository.getProfileNamesByKeyKcv(key.kcv)
                    KeyWithProfiles(key = key, assignedProfiles = profiles)
                }
                _uiState.value = _uiState.value.copy(keysWithProfiles = keysWithProfiles, loading = false)
            }
        }
    }

    fun onDeleteKey(key: InjectedKeyEntity) {
        // Solo administradores pueden eliminar llaves
        if (!_uiState.value.isAdmin) {
            Log.w(TAG, "Usuario no autorizado intentó eliminar una llave")
            return
        }

        viewModelScope.launch {
            try {
                // Si es KEK Storage, también eliminar del Android Keystore
                if (key.isKEKStorage()) {
                    Log.w(TAG, "Eliminando KEK Storage del Android Keystore...")
                    StorageKeyManager.deleteStorageKEK()
                    Log.d(TAG, "✓ KEK Storage eliminada del Keystore")
                }

                // Eliminar de la base de datos
                injectedKeyRepository.deleteKey(key)
                Log.d(TAG, "✓ Llave eliminada de la base de datos")

                loadKeys() // Recargar
            } catch (e: Exception) {
                Log.e(TAG, "Error al eliminar llave", e)
                e.printStackTrace()
            }
        }
    }

    fun onShowClearAllConfirmation() {
        _uiState.value = _uiState.value.copy(showClearAllConfirmation = true)
    }

    fun onDismissClearAllConfirmation() {
        _uiState.value = _uiState.value.copy(showClearAllConfirmation = false)
    }

    fun onConfirmClearAllKeys() {
        // Solo administradores pueden eliminar todas las llaves
        if (!_uiState.value.isAdmin) {
            Log.w(TAG, "Usuario no autorizado intentó eliminar todas las llaves")
            _uiState.value = _uiState.value.copy(showClearAllConfirmation = false)
            return
        }

        viewModelScope.launch {
            try {
                // Verificar si existe KEK Storage antes de eliminar
                if (StorageKeyManager.hasStorageKEK()) {
                    Log.w(TAG, "Eliminando KEK Storage del Android Keystore (Clear All)...")
                    StorageKeyManager.deleteStorageKEK()
                    Log.d(TAG, "✓ KEK Storage eliminada del Keystore")
                }

                // Eliminar todas las llaves de la base de datos
                injectedKeyRepository.deleteAllKeys()
                Log.d(TAG, "✓ Todas las llaves eliminadas de la base de datos")

                _uiState.value = _uiState.value.copy(showClearAllConfirmation = false)
                loadKeys() // Recargar
            } catch (e: Exception) {
                Log.e(TAG, "Error al eliminar todas las llaves", e)
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(showClearAllConfirmation = false)
            }
        }
    }

    fun onShowDeleteModal(key: InjectedKeyEntity) {
        _uiState.value = _uiState.value.copy(showDeleteModal = true, selectedKey = key)
    }

    fun onDismissDeleteModal() {
        _uiState.value = _uiState.value.copy(showDeleteModal = false, selectedKey = null)
    }

    /**
     * Marca o desmarca una llave como KTK (Key Transport Key)
     * La KTK se usa para cifrar llaves antes de transmitirlas al SubPOS
     */
    fun toggleKeyAsKTK(key: InjectedKeyEntity) {
        viewModelScope.launch {
            try {
                if (key.isKEK && key.kekType == "KEK_TRANSPORT") {
                    // Quitar flag KTK
                    injectedKeyRepository.removeKeyAsKTK(key.kcv)
                } else {
                    // Establecer como KTK (automáticamente limpia KTKs anteriores)
                    injectedKeyRepository.setKeyAsKTK(key.kcv)
                }
                loadKeys() // Recargar para reflejar cambios
            } catch (e: Exception) {
                // Log error (podría agregarse un estado de error en el futuro)
                e.printStackTrace()
            }
        }
    }

    /**
     * Genera 5 llaves de prueba automáticamente para desarrollo.
     * Solo disponible para usuarios administradores.
     */
    fun generateTestKeys() {
        viewModelScope.launch {
            try {
                android.util.Log.i("KeyVaultViewModel", "=== GENERANDO LLAVES DE PRUEBA ===")

                // Llave 1: AES-256 para KEK
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = -1, // Sin slot específico (es llave de ceremonia)
                    keyType = "CEREMONY_KEY",
                    keyAlgorithm = "AES_256",
                    kcv = "A1B2C3",
                    keyData = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF", // 32 bytes (AES-256)
                    status = "ACTIVE",
                    isKEK = false,
                    customName = "KEK Test AES-256"
                )
                android.util.Log.d("KeyVaultViewModel", "✓ Llave 1 generada: AES-256 KEK")

                // Llave 2: 3DES PIN Encryption
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = -1,
                    keyType = "CEREMONY_KEY",
                    keyAlgorithm = "DES_TRIPLE",
                    kcv = "D4E5F6",
                    keyData = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF", // 24 bytes (3DES)
                    status = "ACTIVE",
                    isKEK = false,
                    customName = "PIN Key 3DES"
                )
                android.util.Log.d("KeyVaultViewModel", "✓ Llave 2 generada: 3DES PIN")

                // Llave 3: AES-128 MAC
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = -1,
                    keyType = "CEREMONY_KEY",
                    keyAlgorithm = "AES_128",
                    kcv = "789ABC",
                    keyData = "FEDCBA9876543210FEDCBA9876543210", // 16 bytes (AES-128)
                    status = "ACTIVE",
                    isKEK = false,
                    customName = "MAC Key AES-128"
                )
                android.util.Log.d("KeyVaultViewModel", "✓ Llave 3 generada: AES-128 MAC")

                // Llave 4: AES-192 Data Encryption
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = -1,
                    keyType = "CEREMONY_KEY",
                    keyAlgorithm = "AES_192",
                    kcv = "DEF012",
                    keyData = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF", // 24 bytes (AES-192)
                    status = "ACTIVE",
                    isKEK = false,
                    customName = "Data Encryption AES-192"
                )
                android.util.Log.d("KeyVaultViewModel", "✓ Llave 4 generada: AES-192 Data")

                // Llave 5: 3DES DUKPT BDK
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = -1,
                    keyType = "CEREMONY_KEY",
                    keyAlgorithm = "DES_TRIPLE",
                    kcv = "345678",
                    keyData = "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789", // 24 bytes (3DES)
                    status = "ACTIVE",
                    isKEK = false,
                    customName = "DUKPT BDK 3DES"
                )
                android.util.Log.d("KeyVaultViewModel", "✓ Llave 5 generada: 3DES DUKPT BDK")

                android.util.Log.i("KeyVaultViewModel", "✅ 5 llaves de prueba generadas exitosamente")
                android.util.Log.i("KeyVaultViewModel", "================================================")

                // Recargar las llaves para mostrar las nuevas
                loadKeys()

            } catch (e: Exception) {
                android.util.Log.e("KeyVaultViewModel", "❌ Error al generar llaves de prueba", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Importa llaves de prueba desde un archivo JSON generado por el script
     * generar_llaves_prueba.sh
     */
    fun onImportTestKeys() {
        _uiState.value = _uiState.value.copy(showImportJsonDialog = true)
    }
    
    /**
     * Cierra el diálogo de importación JSON
     */
    fun onDismissImportJsonDialog() {
        _uiState.value = _uiState.value.copy(showImportJsonDialog = false)
    }
    
    /**
     * Importa llaves desde contenido JSON (para pegar desde Vysor o archivos)
     */
    fun importFromJsonContent(jsonContent: String) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "=== IMPORTANDO DESDE JSON ===")
                
                val result = TestKeysImporter.importFromJson(jsonContent, injectedKeyRepository)
                result.fold(
                    onSuccess = { imported ->
                        Log.i(TAG, "✅ Importadas $imported llaves desde JSON")
                        loadKeys() // Recargar lista
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ Error importando desde JSON: ${error.message}")
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al importar desde JSON", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Genera llaves de prueba directamente (simulando importación del script)
     */
    private suspend fun generateTestKeysFromScript() {
        try {
            Log.i(TAG, "Generando llaves de prueba con KCVs correctos...")
            
            // Generar llaves para diferentes algoritmos
            val algorithms = listOf(
                "3DES-16" to "DES_DOUBLE",
                "3DES-24" to "DES_TRIPLE", 
                "AES-128" to "AES_128",
                "AES-192" to "AES_192",
                "AES-256" to "AES_256"
            )
            
            var imported = 0
            
            algorithms.forEach { (scriptAlgo, dbAlgo) ->
                // Generar llave aleatoria
                val keyBytes = when (scriptAlgo) {
                    "3DES-16" -> 16
                    "3DES-24" -> 24
                    "AES-128" -> 16
                    "AES-192" -> 24
                    "AES-256" -> 32
                    else -> 16
                }
                
                val keyHex = generateRandomKeyHex(keyBytes)
                val kcv = calculateKCV(keyHex, scriptAlgo)
                
                Log.d(TAG, "Generando llave: $scriptAlgo - KCV: $kcv")
                
                // Guardar en BD
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = -1,
                    keyType = "CEREMONY_KEY",
                    keyAlgorithm = dbAlgo,
                    kcv = kcv,
                    keyData = keyHex,
                    status = "GENERATED",
                    isKEK = false,
                    kekType = "",
                    customName = "Test Key $scriptAlgo"
                )
                
                imported++
            }
            
            Log.i(TAG, "✅ Importadas $imported llaves de prueba con KCVs correctos")
            
            // Recargar lista de llaves
            loadKeys()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generando llaves de prueba", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Genera una llave aleatoria en formato hexadecimal
     */
    private fun generateRandomKeyHex(bytes: Int): String {
        val chars = "0123456789ABCDEF"
        return (1..bytes * 2).map { chars.random() }.joinToString("")
    }
    
    /**
     * Calcula KCV usando el mismo algoritmo que KcvCalculator.kt
     * (simulación básica - en producción usar KcvCalculator)
     */
    private fun calculateKCV(keyHex: String, algorithm: String): String {
        // Simulación de KCV - en producción usar KcvCalculator.calculateKcv()
        // Por ahora generar un KCV simulado basado en la llave
        val hash = keyHex.hashCode().toString(16).uppercase()
        return hash.take(6).padEnd(6, '0')
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GESTIÓN DE KEK STORAGE OCULTA
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Muestra el diálogo para pedir contraseña antes de mostrar KEK Storage
     */
    fun onShowKEKStoragePasswordDialog() {
        _uiState.value = _uiState.value.copy(
            showKEKStoragePasswordDialog = true,
            kekStoragePasswordError = null
        )
    }

    /**
     * Cierra el diálogo de contraseña de KEK Storage
     */
    fun onDismissKEKStoragePasswordDialog() {
        _uiState.value = _uiState.value.copy(
            showKEKStoragePasswordDialog = false,
            kekStoragePasswordError = null
        )
    }

    /**
     * Verifica la contraseña del administrador y muestra KEK Storage si es correcta
     */
    fun verifyAdminPasswordAndShowKEKStorage(password: String) {
        viewModelScope.launch {
            try {
                val session = sessionManager.getCurrentSession()
                if (session == null) {
                    _uiState.value = _uiState.value.copy(
                        kekStoragePasswordError = "No hay sesión activa"
                    )
                    return@launch
                }

                val (userId, username, _) = session
                val user = userRepository.findById(userId.toInt())

                if (user == null) {
                    _uiState.value = _uiState.value.copy(
                        kekStoragePasswordError = "Usuario no encontrado"
                    )
                    return@launch
                }

                // Verificar contraseña
                if (user.pass != password) {
                    _uiState.value = _uiState.value.copy(
                        kekStoragePasswordError = "Contraseña incorrecta"
                    )
                    return@launch
                }

                // Contraseña correcta - mostrar KEK Storage
                _uiState.value = _uiState.value.copy(
                    showKEKStoragePasswordDialog = false,
                    showKEKStorage = true,
                    kekStoragePasswordError = null
                )
                loadKeys() // Recargar para mostrar KEK Storage
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando contraseña de admin", e)
                _uiState.value = _uiState.value.copy(
                    kekStoragePasswordError = "Error al verificar contraseña: ${e.message}"
                )
            }
        }
    }

    /**
     * Oculta KEK Storage nuevamente
     */
    fun hideKEKStorage() {
        _uiState.value = _uiState.value.copy(
            showKEKStorage = false
        )
        loadKeys() // Recargar para ocultar KEK Storage
    }
} 