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
import com.vigatec.injector.util.PermissionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val userRole: String = PermissionManager.ROLE_OPERATOR,
    val showKEKStoragePasswordDialog: Boolean = false,  // Diálogo para pedir contraseña antes de mostrar KEK Storage
    val showKEKStorage: Boolean = false,  // Flag para mostrar/ocultar KEK Storage
    val kekStoragePasswordError: String? = null,  // Error de contraseña al mostrar KEK Storage
    val showKeyDeletionValidationDialog: Boolean = false,  // Diálogo de validación de eliminación
    val keyDeletionValidation: com.vigatec.persistence.model.KeyDeletionValidation? = null,  // Resultado de validación
    val deletionError: String? = null,  // Mensaje de error al validar
    val showMultipleKeysDeletionValidationDialog: Boolean = false,  // Diálogo de validación de eliminación de todas las llaves
    val multipleKeysDeletionValidation: com.vigatec.persistence.model.MultipleKeysDeletionValidation? = null  // Resultado de validación de todas las llaves
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
                val (_, username, role) = session
                val isAdmin = role == "ADMIN"

                Log.d(TAG, "KeyVault - Usuario de sesión: username=$username, role=$role")
                Log.d(TAG, "KeyVault - isAdmin determinado: $isAdmin")

                _uiState.value = _uiState.value.copy(
                    currentUser = null, // Ya no necesitamos el objeto User completo aquí
                    isAdmin = isAdmin,
                    userRole = role
                )

                Log.d(TAG, "KeyVault - Estado actualizado: isAdmin=${_uiState.value.isAdmin}")
            } else {
                Log.w(TAG, "KeyVault - ⚠️ No hay sesión activa")
                _uiState.value = _uiState.value.copy(
                    currentUser = null,
                    isAdmin = false,
                    userRole = PermissionManager.ROLE_OPERATOR
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
            Log.d("Performance", "KeyVaultViewModel loadKeys started at ${System.currentTimeMillis()}")
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
                
                val keysWithProfiles = withContext(Dispatchers.IO) {
                    filteredKeys.map { key ->
                        async {
                            val profiles = profileRepository.getProfileNamesByKeyKcv(key.kcv)
                            KeyWithProfiles(key = key, assignedProfiles = profiles)
                        }
                    }.awaitAll()
                }
                _uiState.value = _uiState.value.copy(keysWithProfiles = keysWithProfiles, loading = false)
                Log.d("Performance", "KeyVaultViewModel loadKeys finished at ${System.currentTimeMillis()}")
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
                Log.d(TAG, "Iniciando proceso de eliminación para llave KCV=${key.kcv}")

                // Validar antes de eliminar
                val validation = injectedKeyRepository.validateKeyDeletion(key)

                if (!validation.canDelete) {
                    Log.w(TAG, "Eliminación bloqueada: ${validation.reason}")
                    _uiState.value = _uiState.value.copy(
                        showKeyDeletionValidationDialog = true,
                        keyDeletionValidation = validation,
                        selectedKey = key
                    )
                    return@launch
                }

                // Si la validación fue exitosa, proceder a eliminar
                Log.d(TAG, "Validación exitosa, procediendo a eliminar llave")
                performKeyDeletion(key)

            } catch (e: Exception) {
                Log.e(TAG, "Error al validar eliminación de llave", e)
                _uiState.value = _uiState.value.copy(
                    deletionError = "Error al validar: ${e.message}"
                )
            }
        }
    }

    /**
     * Realiza la eliminación real de la llave después de validación exitosa
     */
    private suspend fun performKeyDeletion(key: InjectedKeyEntity) {
        try {
            // Si es KEK Storage, también eliminar del Android Keystore
            if (key.isKEKStorage()) {
                Log.w(TAG, "Eliminando KEK Storage del Android Keystore...")
                StorageKeyManager.deleteStorageKEK()
                Log.d(TAG, "✓ KEK Storage eliminada del Keystore")
            }

            // Eliminar de la base de datos
            injectedKeyRepository.deleteKey(key)
            Log.d(TAG, "✓ Llave eliminada de la base de datos (KCV=${key.kcv})")

            loadKeys() // Recargar
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar llave", e)
            e.printStackTrace()
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
                Log.d(TAG, "Iniciando proceso de eliminación de todas las llaves")

                // Validar antes de eliminar
                val validation = injectedKeyRepository.validateAllKeysDeletion()

                if (!validation.canDeleteAll) {
                    Log.w(TAG, "Eliminación bloqueada: ${validation.blockedKeys.size} llaves no se pueden eliminar")
                    _uiState.value = _uiState.value.copy(
                        showClearAllConfirmation = false,
                        showMultipleKeysDeletionValidationDialog = true,
                        multipleKeysDeletionValidation = validation
                    )
                    return@launch
                }

                // Si la validación fue exitosa, proceder a eliminar
                Log.d(TAG, "Validación exitosa, procediendo a eliminar todas las llaves")
                performAllKeysDeletion()

            } catch (e: Exception) {
                Log.e(TAG, "Error al validar eliminación de todas las llaves", e)
                _uiState.value = _uiState.value.copy(
                    showClearAllConfirmation = false,
                    deletionError = "Error al validar: ${e.message}"
                )
            }
        }
    }

    /**
     * Realiza la eliminación real de todas las llaves después de validación exitosa
     */
    private suspend fun performAllKeysDeletion() {
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
            _uiState.value = _uiState.value.copy(
                showClearAllConfirmation = false,
                deletionError = "Error al eliminar: ${e.message}"
            )
        }
    }

    fun onShowDeleteModal(key: InjectedKeyEntity) {
        _uiState.value = _uiState.value.copy(showDeleteModal = true, selectedKey = key)
    }

    fun onDismissDeleteModal() {
        _uiState.value = _uiState.value.copy(showDeleteModal = false, selectedKey = null)
    }

    /**
     * Cierra el diálogo de validación de eliminación
     */
    fun onDismissKeyDeletionValidationDialog() {
        _uiState.value = _uiState.value.copy(
            showKeyDeletionValidationDialog = false,
            keyDeletionValidation = null,
            deletionError = null,
            selectedKey = null
        )
    }

    /**
     * Cierra el diálogo de validación de eliminación de todas las llaves
     */
    fun onDismissMultipleKeysDeletionValidationDialog() {
        _uiState.value = _uiState.value.copy(
            showMultipleKeysDeletionValidationDialog = false,
            multipleKeysDeletionValidation = null,
            deletionError = null
        )
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

                val (userId, _, _) = session
                val user = userRepository.findById(userId)

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