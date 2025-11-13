package com.vigatec.dev_injector.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.manufacturer.KeySDKManager
import com.vigatec.manufacturer.base.models.KeyType
import com.vigatec.persistence.entities.InjectedKeyEntity
import com.vigatec.persistence.repository.InjectedKeyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SimpleInjectedKeysUiState(
    val keys: List<InjectedKeyEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hardwareKeysInfo: String? = null
)

@HiltViewModel
class SimpleInjectedKeysViewModel @Inject constructor(
    private val repository: InjectedKeyRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SimpleInjectedKeysViewModel"
    }

    private val _uiState = MutableStateFlow(SimpleInjectedKeysUiState())
    val uiState: StateFlow<SimpleInjectedKeysUiState> = _uiState.asStateFlow()

    init {
        loadKeys()
    }

    fun refreshKeys() {
        loadKeys()
        scanHardwareKeys()
    }

    fun scanHardwareKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "scanHardwareKeys: Iniciando escaneo de llaves en hardware...")
            
            try {
                val pedController = KeySDKManager.getPedController()
                if (pedController != null) {
                    Log.d(TAG, "scanHardwareKeys: PED Controller obtenido: ${pedController::class.java.simpleName}")
                    
                    val hardwareKeys = mutableListOf<String>()
                    
                    // Escanear los slots más comunes (0-15)
                    val keyTypes = listOf(
                        KeyType.MASTER_KEY,
                        KeyType.TRANSPORT_KEY,
                        KeyType.WORKING_PIN_KEY,
                        KeyType.WORKING_MAC_KEY,
                        KeyType.WORKING_DATA_KEY
                    )
                    
                    for (slot in 0..15) {
                        for (keyType in keyTypes) {
                            try {
                                if (pedController.isKeyPresent(slot, keyType)) {
                                    Log.d(TAG, "scanHardwareKeys: Encontrada llave en slot $slot tipo $keyType")
                                    val keyInfo = pedController.getKeyInfo(slot, keyType)
                                    val algorithm = keyInfo?.algorithm?.name ?: "Unknown"
                                    hardwareKeys.add("Slot $slot: ${keyType.name} ($algorithm)")
                                }
                            } catch (e: Exception) {
                                // Ignorar errores individuales y continuar escaneando
                                Log.d(TAG, "scanHardwareKeys: No se puede acceder a slot $slot tipo $keyType: ${e.message}")
                            }
                        }
                    }
                    
                    val infoText = if (hardwareKeys.isEmpty()) {
                        "No se encontraron llaves en el hardware (slots 0-15)"
                    } else {
                        "Llaves encontradas en hardware:\n" + hardwareKeys.joinToString("\n")
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        hardwareKeysInfo = infoText
                    )
                    
                    Log.i(TAG, "scanHardwareKeys: Escaneo completado. Encontradas ${hardwareKeys.size} llaves.")
                } else {
                    Log.e(TAG, "scanHardwareKeys: Controlador PED no disponible.")
                    _uiState.value = _uiState.value.copy(
                        hardwareKeysInfo = "Error: Controlador PED no disponible"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "scanHardwareKeys: Error durante el escaneo", e)
                _uiState.value = _uiState.value.copy(
                    hardwareKeysInfo = "Error escaneando hardware: ${e.message}"
                )
            }
        }
    }

    fun clearAllKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "clearAllKeys: Proceso de borrado de hardware y BD iniciado.")
            
            if (_uiState.value.keys.isEmpty()) {
                Log.i(TAG, "clearAllKeys: No hay llaves para borrar.")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            var hardwareSuccess = false
            try {
                val pedController = KeySDKManager.getPedController()
                if (pedController != null) {
                    Log.d(TAG, "clearAllKeys: PED Controller obtenido: ${pedController::class.java.simpleName}")
                    
                    val keyCount = _uiState.value.keys.size
                    Log.d(TAG, "clearAllKeys: Número de llaves a eliminar: $keyCount")
                    _uiState.value.keys.forEach { key ->
                        Log.d(TAG, "    * Slot ${key.keySlot}: ${key.keyType} (KCV: ${key.kcv})")
                    }
                    
                    Log.i(TAG, "clearAllKeys: Llamando a pedController.deleteAllKeys()...")
                    hardwareSuccess = pedController.deleteAllKeys()
                    
                    if (hardwareSuccess) {
                        Log.i(TAG, "clearAllKeys: ✅ ÉXITO - NewPOS eliminó TODAS las llaves del dispositivo")
                    } else {
                        Log.w(TAG, "clearAllKeys: ❌ FALLÓ - NewPOS retornó false al intentar eliminar todas las llaves")
                    }
                } else {
                    Log.e(TAG, "clearAllKeys: ❌ CRÍTICO - Controlador PED no disponible.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "clearAllKeys: ❌ EXCEPCIÓN durante el borrado de hardware", e)
                Log.e(TAG, "clearAllKeys: Detalles de la excepción: ${e.message}")
            }

            // Finalizar la operación basado en el resultado
            if (hardwareSuccess) {
                // Si el hardware se limpió, limpiar la base de datos local
                Log.d(TAG, "clearAllKeys: Éxito en hardware. Limpiando la base de datos local.")
                try {
                    repository.deleteAllKeys()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = null
                    )
                    loadKeys() // Recargar para mostrar que está vacío
                    Log.i(TAG, "clearAllKeys: ✅ Dispositivo y base de datos limpiados con éxito.")
                } catch (dbError: Exception) {
                    Log.e(TAG, "clearAllKeys: Error limpiando base de datos después de éxito en hardware", dbError)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Hardware limpiado pero error en BD: ${dbError.message}"
                    )
                }
            } else {
                // Si el hardware falló, mantener la base de datos intacta
                Log.w(TAG, "clearAllKeys: Fallo en hardware. Manteniendo base de datos intacta.")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error: No se pudo borrar del dispositivo. Las llaves permanecen."
                )
            }
        }
    }

    private fun loadKeys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            repository.getAllInjectedKeys()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Error al cargar llaves: ${e.message}"
                    )
                }
                .collect { keys ->
                    _uiState.value = _uiState.value.copy(
                        keys = keys,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }
}