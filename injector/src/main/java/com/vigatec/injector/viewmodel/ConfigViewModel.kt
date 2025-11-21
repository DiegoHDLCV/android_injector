package com.vigatec.injector.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.persistence.entities.User
import com.vigatec.injector.data.local.preferences.SessionManager
import com.vigatec.injector.data.local.preferences.CustodianTimeoutPreferencesManager
import com.vigatec.injector.repository.UserRepository
import com.vigatec.injector.util.PermissionProvider
import com.vigatec.injector.util.SystemInfoProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    val databaseVersion: String = "",
    val custodianTimeoutMinutes: Int = 3,  // Timeout de custodios en minutos
    val isSavingTimeout: Boolean = false,   // Indicador de guardado
    val timeoutSaveMessage: String? = null  // Mensaje de éxito/error al guardar timeout
)

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val permissionProvider: PermissionProvider,
    private val systemInfoProvider: SystemInfoProvider,
    private val custodianTimeoutPreferencesManager: CustodianTimeoutPreferencesManager
) : ViewModel() {

    companion object {
        private const val TAG = "ConfigViewModel"
    }

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        // Cargar el timeout configurado
        loadCustodianTimeout()
    }

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
            } catch (e: CancellationException) {
                // Re-lanzar CancellationException - es una cancelación legítima de coroutine
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al cargar usuario: ${e.message}"
                )
            }
        }
    }

    /**
     * Carga el timeout configurado para custodios
     */
    private fun loadCustodianTimeout() {
        viewModelScope.launch {
            try {
                custodianTimeoutPreferencesManager.getCustodianTimeoutMinutes().collect { minutes ->
                    _uiState.value = _uiState.value.copy(
                        custodianTimeoutMinutes = minutes
                    )
                    Log.d(TAG, "Timeout de custodios cargado: $minutes minutos")
                }
            } catch (e: CancellationException) {
                // Re-lanzar CancellationException - es una cancelación legítima de coroutine
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar timeout de custodios", e)
            }
        }
    }

    /**
     * Guarda el nuevo timeout para custodios
     */
    fun saveCustodianTimeout(minutes: Int) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSavingTimeout = true)

                custodianTimeoutPreferencesManager.saveCustodianTimeoutMinutes(minutes)

                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                Log.d(TAG, "Timeout de custodios guardado")
                Log.d(TAG, "  - Nuevo valor: $minutes minutos")
                Log.d(TAG, "  - Equivalente: ${minutes * 60} segundos")
                Log.d(TAG, "═══════════════════════════════════════════════════════════")

                _uiState.value = _uiState.value.copy(
                    custodianTimeoutMinutes = minutes,
                    isSavingTimeout = false,
                    timeoutSaveMessage = "Timeout de custodios actualizado a $minutes minutos"
                )

                // Limpiar el mensaje después de 3 segundos
                clearTimeoutSaveMessage()
            } catch (e: CancellationException) {
                // Re-lanzar CancellationException - es una cancelación legítima de coroutine
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error al guardar timeout de custodios", e)
                _uiState.value = _uiState.value.copy(
                    isSavingTimeout = false,
                    timeoutSaveMessage = "Error: ${e.message}"
                )
            }
        }
    }

    /**
     * Limpia el mensaje de guardado después de un tiempo
     */
    private fun clearTimeoutSaveMessage() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(timeoutSaveMessage = null)
        }
    }

    /**
     * Restablece el timeout al valor por defecto
     */
    fun resetCustodianTimeoutToDefault() {
        saveCustodianTimeout(10) // 10 minutos es el valor por defecto
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
