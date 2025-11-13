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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InjectedKeysUiState(
    val injectedKeys: List<InjectedKeyEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class InjectedKeysViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    private val TAG = "InjectedKeysViewModel"

    private val _uiState = MutableStateFlow(InjectedKeysUiState())
    val uiState: StateFlow<InjectedKeysUiState> = _uiState.asStateFlow()

    private val _showDeleteModal = MutableStateFlow(false)
    val showDeleteModal: StateFlow<Boolean> = _showDeleteModal.asStateFlow()

    private val _selectedKeyForDeletion = MutableStateFlow<InjectedKeyEntity?>(null)
    val selectedKeyForDeletion: StateFlow<InjectedKeyEntity?> = _selectedKeyForDeletion.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        loadInjectedKeys()
    }

    fun refreshKeys() {
        loadInjectedKeys()
    }

    private fun loadInjectedKeys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            injectedKeyRepository.getAllInjectedKeys()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Error al cargar llaves: ${e.message}"
                    )
                    _snackbarMessage.emit("Error al cargar las llaves: ${e.message}")
                }
                .collect { keys ->
                    _uiState.value = _uiState.value.copy(
                        injectedKeys = keys,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }

    fun onDeleteKey(key: InjectedKeyEntity) {
        _selectedKeyForDeletion.value = key
        _showDeleteModal.value = true
    }

    fun confirmDeleteKey() {
        viewModelScope.launch {
            try {
                val key = _selectedKeyForDeletion.value
                if (key != null) {
                    // Marcar como eliminando
                    val updatedKey = key.copy(status = "DELETING")
                    injectedKeyRepository.insertOrUpdate(updatedKey)
                    
                    // Simular el proceso de eliminación
                    kotlinx.coroutines.delay(1000)
                    
                    // Eliminar de la base de datos
                    injectedKeyRepository.deleteKey(key.id)
                    _snackbarMessage.emit("Llave eliminada exitosamente")
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Error al eliminar la llave: ${e.message}")
            } finally {
                dismissDeleteModal()
            }
        }
    }

    fun dismissDeleteModal() {
        _showDeleteModal.value = false
        _selectedKeyForDeletion.value = null
    }

    fun clearAllKeys() {
        viewModelScope.launch {
            try {
                val currentKeys = _uiState.value.injectedKeys
                currentKeys.forEach { key ->
                    val updatedKey = key.copy(status = "DELETING")
                    injectedKeyRepository.insertOrUpdate(updatedKey)
                }
                
                // Simular el proceso de eliminación
                kotlinx.coroutines.delay(1500)
                
                // Eliminar todas las llaves
                injectedKeyRepository.deleteAllKeys()
                _snackbarMessage.emit("Todas las llaves han sido eliminadas")
            } catch (e: Exception) {
                _snackbarMessage.emit("Error al eliminar las llaves: ${e.message}")
            }
        }
    }

    fun deleteKey(key: InjectedKeyEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "deleteKey: Proceso de borrado por estados iniciado para la llave con id ${key.id} en slot ${key.keySlot}.")

            // PASO 1: MARCAR LA LLAVE ESPECÍFICA COMO "BORRANDO"
            Log.d(TAG, "deleteKey: 1. Actualizando estado a 'DELETING' en la BD para la llave id ${key.id}.")
            injectedKeyRepository.updateKeyStatus(key, "DELETING")
            _snackbarMessage.emit("Borrando llave en slot ${key.keySlot}...")

            // PASO 2: INTENTAR BORRAR DEL HARDWARE
            var hardwareSuccess = false
            try {
                val pedController = KeySDKManager.getPedController()
                if (pedController != null) {
                    Log.d(TAG, "deleteKey: 2. PED Controller obtenido: ${pedController::class.java.simpleName}")
                    
                    val genericKeyType = mapStringToGenericKeyType(key.keyType)
                    Log.d(TAG, "deleteKey: 2. Parámetros de eliminación:")
                    Log.d(TAG, "  - Slot: ${key.keySlot}")
                    Log.d(TAG, "  - Tipo original: ${key.keyType}")
                    Log.d(TAG, "  - Tipo mapeado: $genericKeyType")
                    Log.d(TAG, "  - Algoritmo: ${key.keyAlgorithm}")
                    Log.d(TAG, "  - KCV: ${key.kcv}")
                    
                    Log.i(TAG, "deleteKey: 2. Llamando a pedController.deleteKey(${key.keySlot}, $genericKeyType)...")
                    hardwareSuccess = pedController.deleteKey(key.keySlot, genericKeyType)
                    
                    if (hardwareSuccess) {
                        Log.i(TAG, "deleteKey: 2. ✅ ÉXITO - NewPOS eliminó la llave del slot ${key.keySlot}")
                    } else {
                        Log.w(TAG, "deleteKey: 2. ❌ FALLÓ - NewPOS retornó false para el slot ${key.keySlot}")
                    }
                } else {
                    Log.e(TAG, "deleteKey: 2. ❌ CRÍTICO - Controlador PED no disponible.")
                    _snackbarMessage.emit("Error: Controlador no disponible.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteKey: 2. ❌ EXCEPCIÓN durante el borrado de hardware en slot ${key.keySlot}", e)
                Log.e(TAG, "deleteKey: 2. Detalles de la excepción: ${e.message}")
                _snackbarMessage.emit("El proceso de borrado fue interrumpido.")
            }

            // PASO 3: FINALIZAR LA OPERACIÓN BASADO EN EL RESULTADO
            if (hardwareSuccess) {
                // SI EL HARDWARE BORRÓ, QUITAR EL REGISTRO DE LA BD
                Log.d(TAG, "deleteKey: 3. Éxito en hardware. Borrando llave id ${key.id} de la base de datos local.")
                injectedKeyRepository.deleteKey(key)
                _snackbarMessage.emit("Llave en slot ${key.keySlot} borrada con éxito.")
            } else {
                // SI EL HARDWARE FALLÓ, REVERTIR EL ESTADO A "SUCCESSFUL"
                Log.w(TAG, "deleteKey: 3. Fallo/Cancelación. Reviertiendo estado a 'SUCCESSFUL' para la llave id ${key.id}.")
                injectedKeyRepository.updateKeyStatus(key, "SUCCESSFUL")
                _snackbarMessage.emit("Fallo al borrar llave en slot ${key.keySlot}. Se revirtieron los cambios.")
            }
        }
    }

    fun deleteAllKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "deleteAllKeys: Proceso de borrado por estados iniciado.")

            if (_uiState.value.injectedKeys.isEmpty()) {
                Log.i(TAG, "deleteAllKeys: No hay llaves para borrar.")
                _snackbarMessage.emit("No hay llaves para borrar.")
                return@launch
            }

            // PASO 1: MARCAR TODAS LAS LLAVES COMO "BORRANDO"
            Log.d(TAG, "deleteAllKeys: 1. Actualizando estado a 'DELETING' en la BD.")
            injectedKeyRepository.updateStatusForAllKeys("DELETING")
            _snackbarMessage.emit("Iniciando borrado en el dispositivo...")

            // PASO 2: INTENTAR BORRAR DEL HARDWARE
            var hardwareSuccess = false
            try {
                val pedController = KeySDKManager.getPedController()
                if (pedController != null) {
                    Log.d(TAG, "deleteAllKeys: 2. PED Controller obtenido: ${pedController::class.java.simpleName}")
                    
                    val keyCount = _uiState.value.injectedKeys.size
                    Log.d(TAG, "deleteAllKeys: 2. Información del borrado:")
                    Log.d(TAG, "  - Número de llaves a eliminar: $keyCount")
                    Log.d(TAG, "  - Llaves en la base de datos:")
                    _uiState.value.injectedKeys.forEach { key ->
                        Log.d(TAG, "    * Slot ${key.keySlot}: ${key.keyType} (KCV: ${key.kcv})")
                    }
                    
                    Log.i(TAG, "deleteAllKeys: 2. Llamando a pedController.deleteAllKeys() -> NewPOS.clearUserKeys()...")
                    hardwareSuccess = pedController.deleteAllKeys()
                    
                    if (hardwareSuccess) {
                        Log.i(TAG, "deleteAllKeys: 2. ✅ ÉXITO - NewPOS eliminó TODAS las llaves del dispositivo")
                        Log.i(TAG, "deleteAllKeys: 2. Se eliminaron $keyCount llaves del hardware")
                    } else {
                        Log.w(TAG, "deleteAllKeys: 2. ❌ FALLÓ - NewPOS retornó false al intentar eliminar todas las llaves")
                    }
                } else {
                    Log.e(TAG, "deleteAllKeys: 2. ❌ CRÍTICO - Controlador PED no disponible.")
                    _snackbarMessage.emit("Error: Controlador no disponible.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteAllKeys: 2. ❌ EXCEPCIÓN durante el borrado de hardware", e)
                Log.e(TAG, "deleteAllKeys: 2. Detalles de la excepción: ${e.message}")
                Log.e(TAG, "deleteAllKeys: 2. Stacktrace: ${e.stackTraceToString()}")
                _snackbarMessage.emit("El proceso de borrado fue interrumpido.")
            }

            // PASO 3: FINALIZAR LA OPERACIÓN BASADO EN EL RESULTADO
            if (hardwareSuccess) {
                // SI EL HARDWARE SE LIMPIÓ, LIMPIAR LA BASE DE DATOS LOCAL
                Log.d(TAG, "deleteAllKeys: 3. Éxito en hardware. Limpiando la base de datos local.")
                injectedKeyRepository.deleteAllKeys()
                _snackbarMessage.emit("Dispositivo y base de datos limpiados con éxito.")
            } else {
                // SI EL HARDWARE FALLÓ O LA TAREA FUE CANCELADA, REVERTIR EL ESTADO
                Log.w(TAG, "deleteAllKeys: 3. Fallo/Cancelación en hardware. Reviertiendo estado a 'SUCCESSFUL'.")
                injectedKeyRepository.updateStatusForAllKeys("SUCCESSFUL")
                _snackbarMessage.emit("Error: No se pudo borrar del dispositivo. Se revirtieron los cambios.")
            }
        }
    }

    private fun mapStringToGenericKeyType(keyTypeString: String): KeyType {
        return try {
            KeyType.valueOf(keyTypeString)
        } catch (e: IllegalArgumentException) {
            if (keyTypeString.contains("MASTER") || keyTypeString.contains("TRANSPORT")) {
                KeyType.MASTER_KEY
            } else {
                KeyType.WORKING_PIN_KEY
            }
        }
    }
}
