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

    fun onClearAllKeys() {
        viewModelScope.launch {
            injectedKeyRepository.deleteAllKeys()
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
     * Marca o desmarca una llave como KEK
     */
    fun toggleKeyAsKEK(key: InjectedKeyEntity) {
        viewModelScope.launch {
            try {
                if (key.isKEK) {
                    // Quitar flag KEK
                    injectedKeyRepository.removeKeyAsKEK(key.kcv)
                } else {
                    // Establecer como KEK (automáticamente limpia KEKs anteriores)
                    injectedKeyRepository.setKeyAsKEK(key.kcv)
                }
                loadKeys() // Recargar para reflejar cambios
            } catch (e: Exception) {
                // Log error (podría agregarse un estado de error en el futuro)
                e.printStackTrace()
            }
        }
    }
} 