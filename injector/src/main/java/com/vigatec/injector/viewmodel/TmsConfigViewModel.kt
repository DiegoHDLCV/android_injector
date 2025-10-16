package com.vigatec.injector.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manufacturer.TmsSDKManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TmsConfigUiState(
    val isLoading: Boolean = false,
    val parametersJson: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isTmsServiceAvailable: Boolean = false,
    val isDownloading: Boolean = false
)

@HiltViewModel
class TmsConfigViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(TmsConfigUiState())
    val uiState: StateFlow<TmsConfigUiState> = _uiState.asStateFlow()


    init {
        checkTmsServiceAvailability()
    }

    /**
     * Verifica si el servicio TMS está disponible en el dispositivo.
     */
    private fun checkTmsServiceAvailability() {
        viewModelScope.launch {
            try {
                val controller = withContext(Dispatchers.IO) {
                    TmsSDKManager.getTmsController()
                }
                
                val isAvailable = controller?.isTmsServiceAvailable() ?: false
                
                _uiState.value = _uiState.value.copy(
                    isTmsServiceAvailable = isAvailable
                )
                
                Log.d("TmsConfigViewModel", "Servicio TMS disponible: $isAvailable")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTmsServiceAvailable = false,
                    errorMessage = "Error al verificar servicio TMS: ${e.message}"
                )
            }
        }
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
     * Descarga parámetros desde el servidor TMS.
     */
    fun downloadParametersFromTms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                errorMessage = null
            )
            
            try {
                val controller = withContext(Dispatchers.IO) {
                    TmsSDKManager.getTmsController()
                }

                if (controller == null) {
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        errorMessage = "TMS no está disponible"
                    )
                    return@launch
                }

                if (!controller.isTmsServiceAvailable()) {
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        errorMessage = "Servicio TMS no está disponible en este dispositivo"
                    )
                    return@launch
                }

                // Usar el package name de la aplicación actual
                val packageName = application.packageName
                Log.d("TmsConfigViewModel", "Descargando parámetros desde TMS para: $packageName")

                withContext(Dispatchers.IO) {
                    controller.downloadParametersFromTms(
                        packageName = packageName,
                        onSuccess = { parametersJson ->
                            Log.i("TmsConfigViewModel", "✓ Parámetros descargados exitosamente desde TMS")
                            Log.i("TmsConfigViewModel", "═══════════════════════════════════════════════════")
                            Log.i("TmsConfigViewModel", "JSON COMPLETO recibido:")
                            Log.i("TmsConfigViewModel", parametersJson)
                            Log.i("TmsConfigViewModel", "═══════════════════════════════════════════════════")
                            Log.d("TmsConfigViewModel", "Tamaño total: ${parametersJson.length} caracteres")
                            
                            // Actualizar UI en el hilo principal
                            viewModelScope.launch(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(
                                    isDownloading = false,
                                    parametersJson = parametersJson,
                                    successMessage = "Parámetros descargados: ${parametersJson.length} caracteres"
                                )
                            }
                        },
                        onError = { errorMessage ->
                            Log.e("TmsConfigViewModel", "✗ Error al descargar desde TMS: $errorMessage")
                            
                            // Actualizar UI en el hilo principal
                            viewModelScope.launch(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(
                                    isDownloading = false,
                                    errorMessage = "Error al descargar desde TMS: $errorMessage"
                                )
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("TmsConfigViewModel", "Excepción al intentar descargar desde TMS", e)
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    errorMessage = "Error inesperado: ${e.message}"
                )
            }
        }
    }

}
