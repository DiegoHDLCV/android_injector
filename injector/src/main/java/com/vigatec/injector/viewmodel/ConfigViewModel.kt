package com.vigatec.injector.viewmodel

import android.util.Log
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

    companion object {
        private const val TAG = "ConfigViewModel"
    }

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
     * NOTA: Ya NO desactivamos usuarios aquí. El campo isActive es SOLO para control de acceso.
     */
    fun logout() {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Cerrando sesión del usuario actual")
        Log.d(TAG, "NOTA: isActive ya NO se modifica en logout.")
        Log.d(TAG, "      Ese campo es SOLO para que admins habiliten/deshabiliten acceso")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        // Ya NO llamamos a deactivateAllUsers() porque isActive es para control de acceso
        // El usuario simplemente vuelve al login
    }
}
