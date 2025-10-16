package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfigUiState(
    val currentUser: User? = null,
    val isAdmin: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    fun loadCurrentUser(username: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.findByUsername(username)
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    isAdmin = user?.role == "ADMIN"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al cargar usuario: ${e.message}"
                )
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * Cierra la sesión del usuario actual.
     */
    fun logout() {
        viewModelScope.launch {
            try {
                // Desactivar todos los usuarios (cerrar sesión)
                userRepository.deactivateAllUsers()
            } catch (e: Exception) {
                // Silenciosamente fallar - el usuario aún navegará al login
            }
        }
    }
}
