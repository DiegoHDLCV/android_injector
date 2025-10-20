package com.vigatec.injector.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.data.local.preferences.SessionManager
import com.vigatec.injector.repository.UserRepository
import com.vigatec.injector.util.PermissionProvider
import com.vigatec.injector.util.SystemInfoProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfigUiState(
    val currentUser: User? = null,
    val isAdmin: Boolean = false,
    val errorMessage: String? = null,
    val applicationVersion: String = "",
    val databaseVersion: String = ""
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val permissionProvider: PermissionProvider,
    private val systemInfoProvider: SystemInfoProvider
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
                    isAdmin = user?.role == "ADMIN",
                    applicationVersion = systemInfoProvider.getApplicationVersion(),
                    databaseVersion = systemInfoProvider.getDatabaseVersion()
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
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            Log.d(TAG, "Cerrando sesión del usuario actual")

            // Limpiar la sesión en SessionManager
            sessionManager.clearSession()
            Log.d(TAG, "✓ Sesión limpiada en SessionManager")

            // Limpiar permisos del usuario
            permissionProvider.clear()
            Log.d(TAG, "✓ Permisos limpiados")

            Log.d(TAG, "✓ Logout completado exitosamente")
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
        }
    }
}
