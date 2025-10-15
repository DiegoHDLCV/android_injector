package com.vigatec.injector.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manufacturer.TmsSDKManager
import com.example.manufacturer.libraries.aisino.wrapper.AisinoTmsParameterHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Representa un parámetro de configuración del TMS.
 */
data class TmsParameter(
    val key: String,
    val value: String,
    val description: String
)

data class TmsConfigUiState(
    val isLoading: Boolean = false,
    val parameters: List<TmsParameter> = emptyList(),
    val customParamName: String = "",
    val customParamValue: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isTmsAvailable: Boolean = false
)

@HiltViewModel
class TmsConfigViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(TmsConfigUiState())
    val uiState: StateFlow<TmsConfigUiState> = _uiState.asStateFlow()

    // Parámetros comunes del TMS (se pueden configurar desde la plataforma TMS)
    private val commonParameters = listOf(
        "url_api" to "URL del API del servidor",
        "timeout_ms" to "Timeout de conexión (ms)",

    )

    init {
        checkTmsAvailability()
    }

    /**
     * Verifica si el TMS está disponible en el dispositivo.
     */
    private fun checkTmsAvailability() {
        viewModelScope.launch {
            try {
                val controller = withContext(Dispatchers.IO) {
                    TmsSDKManager.getTmsController()
                }
                _uiState.value = _uiState.value.copy(
                    isTmsAvailable = controller != null
                )
                if (controller != null) {
                    loadCommonParameters()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "TMS no disponible en este dispositivo o fabricante"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTmsAvailable = false,
                    errorMessage = "Error al verificar TMS: ${e.message}"
                )
            }
        }
    }

    /**
     * Carga los parámetros comunes del TMS.
     */
    fun loadCommonParameters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val controller = withContext(Dispatchers.IO) {
                    TmsSDKManager.getTmsController()
                }

                if (controller == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "TMS no está disponible"
                    )
                    return@launch
                }

                val parameters = withContext(Dispatchers.IO) {
                    commonParameters.mapNotNull { (key, description) ->
                        controller.getTmsParameter(key)?.let { value ->
                            TmsParameter(key, value, description)
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    parameters = parameters,
                    successMessage = if (parameters.isEmpty()) "No se encontraron parámetros configurados" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar parámetros: ${e.message}"
                )
            }
        }
    }

    /**
     * Lee un parámetro personalizado por su nombre.
     */
    fun readCustomParameter(paramName: String) {
        if (paramName.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Debe ingresar un nombre de parámetro"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val controller = withContext(Dispatchers.IO) {
                    TmsSDKManager.getTmsController()
                }

                if (controller == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "TMS no está disponible"
                    )
                    return@launch
                }

                val value = withContext(Dispatchers.IO) {
                    controller.getTmsParameter(paramName)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    customParamValue = value,
                    errorMessage = if (value == null) "Parámetro '$paramName' no encontrado" else null,
                    successMessage = if (value != null) "Parámetro encontrado" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al leer parámetro: ${e.message}"
                )
            }
        }
    }

    /**
     * Actualiza el nombre del parámetro personalizado.
     */
    fun updateCustomParamName(name: String) {
        _uiState.value = _uiState.value.copy(
            customParamName = name,
            customParamValue = null
        )
    }

    /**
     * Limpia los mensajes de error y éxito.
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    /**
     * Crea un archivo param.env con parámetros de prueba.
     * Útil para testing cuando no se tiene acceso al servidor TMS.
     */
    fun createTestParameters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val success = withContext(Dispatchers.IO) {
                    // Eliminar archivo existente si lo hay (para asegurar que se crea nuevo)
                    if (AisinoTmsParameterHelper.paramEnvFileExists(application)) {
                        Log.d("TmsConfigViewModel", "Eliminando archivo param.env existente antes de recrear")
                        AisinoTmsParameterHelper.deleteParamEnvFile(application)
                    }

                    // Crear nuevo archivo con el SDK
                    AisinoTmsParameterHelper.createSampleParamEnvFile(application)
                }

                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Parámetros creados usando SDK de Vanstone. Los parámetros están disponibles ahora."
                    )
                    // Recargar parámetros inmediatamente
                    loadCommonParameters()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Error al crear parámetros de prueba. Revisa los logs."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al crear parámetros de prueba: ${e.message}"
                )
            }
        }
    }

    /**
     * Verifica si el archivo param.env existe.
     */
    fun checkParamFileExists(): Boolean {
        return try {
            AisinoTmsParameterHelper.paramEnvFileExists(application)
        } catch (e: Exception) {
            false
        }
    }
}
