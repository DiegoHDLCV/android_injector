package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.repository.InjectedKeyRepository
import com.example.persistence.repository.ProfileRepository
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    val currentUser: User? = null,  // Usuario actual para permisos
    val isAdmin: Boolean = false     // Flag rápido para verificar si es admin
)

@HiltViewModel
class KeyVaultViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository,
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyVaultState())
    val uiState = _uiState.asStateFlow()

    init {
        loadCurrentUser()
        loadKeys()
    }

    /**
     * Carga el usuario actual para determinar permisos
     * Nota: Esta es una implementación simplificada.
     * En producción, deberías gestionar la sesión de usuario de forma más robusta.
     */
    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                // Por ahora, asumimos que hay un usuario logueado
                // En una implementación real, deberías obtener esto de una sesión global
                val users = userRepository.getAllUsers().first()
                val currentUser = users.firstOrNull { it.isActive }
                _uiState.value = _uiState.value.copy(
                    currentUser = currentUser,
                    isAdmin = currentUser?.role == "ADMIN"
                )
            } catch (e: Exception) {
                // Si no hay usuarios, mantener valores por defecto
                e.printStackTrace()
            }
        }
    }

    fun loadKeys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            injectedKeyRepository.getAllInjectedKeys().collect { keys ->
                val keysWithProfiles = keys.map { key ->
                    val profiles = profileRepository.getProfileNamesByKeyKcv(key.kcv)
                    KeyWithProfiles(key = key, assignedProfiles = profiles)
                }
                _uiState.value = _uiState.value.copy(keysWithProfiles = keysWithProfiles, loading = false)
            }
        }
    }

    fun onDeleteKey(key: InjectedKeyEntity) {
        viewModelScope.launch {
            injectedKeyRepository.deleteKey(key)
            loadKeys() // Recargar
        }
    }

    fun onShowClearAllConfirmation() {
        _uiState.value = _uiState.value.copy(showClearAllConfirmation = true)
    }

    fun onDismissClearAllConfirmation() {
        _uiState.value = _uiState.value.copy(showClearAllConfirmation = false)
    }

    fun onConfirmClearAllKeys() {
        viewModelScope.launch {
            injectedKeyRepository.deleteAllKeys()
            _uiState.value = _uiState.value.copy(showClearAllConfirmation = false)
            loadKeys() // Recargar
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
    fun toggleKeyAsKEK(key: InjectedKeyEntity) {
        viewModelScope.launch {
            try {
                if (key.isKEK) {
                    // Quitar flag KTK
                    injectedKeyRepository.removeKeyAsKEK(key.kcv)
                } else {
                    // Establecer como KTK (automáticamente limpia KTKs anteriores)
                    injectedKeyRepository.setKeyAsKEK(key.kcv)
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
} 