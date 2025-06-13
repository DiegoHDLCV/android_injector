// Archivo: com/vigatec/android_injector/viewmodel/InjectedKeysViewModel.kt

package com.vigatec.android_injector.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manufacturer.KeySDKManager
import com.example.manufacturer.base.models.KeyType as GenericKeyType
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.repository.InjectedKeyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InjectedKeysViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    private val TAG = "InjectedKeysViewModel"

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    val injectedKeys: StateFlow<List<InjectedKeyEntity>> =
        injectedKeyRepository.getAllInjectedKeys()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun deleteKey(key: InjectedKeyEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "deleteKey: Proceso de borrado por estados iniciado para la llave con id ${key.id} en slot ${key.keySlot}.")

            // --- PASO 1: MARCAR LA LLAVE ESPECÍFICA COMO "BORRANDO" ---
            Log.d(TAG, "deleteKey: 1. Actualizando estado a 'DELETING' en la BD para la llave id ${key.id}.")
            injectedKeyRepository.updateKeyStatus(key, "DELETING")
            _snackbarMessage.emit("Borrando llave en slot ${key.keySlot}...")

            // --- PASO 2: INTENTAR BORRAR DEL HARDWARE ---
            var hardwareSuccess = false
            try {
                val pedController = KeySDKManager.getPedController()
                if (pedController != null) {
                    val genericKeyType = mapStringToGenericKeyType(key.keyType)
                    Log.d(TAG, "deleteKey: 2. Intentando borrar del hardware (Slot: ${key.keySlot}, Tipo: $genericKeyType)...")
                    hardwareSuccess = pedController.deleteKey(key.keySlot, genericKeyType)
                    Log.i(TAG, "deleteKey: 2. Respuesta del hardware: $hardwareSuccess")
                } else {
                    Log.e(TAG, "deleteKey: 2. Controlador PED no disponible.")
                    _snackbarMessage.emit("Error: Controlador no disponible.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteKey: 2. Excepción durante el borrado de hardware.", e)
                _snackbarMessage.emit("El proceso de borrado fue interrumpido.")
            }

            // --- PASO 3: FINALIZAR LA OPERACIÓN BASADO EN EL RESULTADO ---
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

    // --- FUNCIÓN DE BORRADO TOTAL REESCRITA CON MANEJO DE ESTADOS ---
    fun deleteAllKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "deleteAllKeys: Proceso de borrado por estados iniciado.")

            if (injectedKeys.value.isEmpty()) {
                Log.i(TAG, "deleteAllKeys: No hay llaves para borrar.")
                _snackbarMessage.emit("No hay llaves para borrar.")
                return@launch
            }

            // --- PASO 1: MARCAR TODAS LAS LLAVES COMO "BORRANDO" ---
            Log.d(TAG, "deleteAllKeys: 1. Actualizando estado a 'DELETING' en la BD.")
            injectedKeyRepository.updateStatusForAllKeys("DELETING")
            _snackbarMessage.emit("Iniciando borrado en el dispositivo...")

            // --- PASO 2: INTENTAR BORRAR DEL HARDWARE ---
            var hardwareSuccess = false
            try {
                val pedController = KeySDKManager.getPedController()
                if (pedController != null) {
                    Log.d(TAG, "deleteAllKeys: 2. Intentando borrar del hardware...")
                    hardwareSuccess = pedController.deleteAllKeys()
                    Log.i(TAG, "deleteAllKeys: 2. Respuesta del hardware: $hardwareSuccess")
                } else {
                    Log.e(TAG, "deleteAllKeys: 2. Controlador PED no disponible.")
                    _snackbarMessage.emit("Error: Controlador no disponible.")
                }
            } catch (e: Exception) {
                // Captura de JobCancellationException y otros errores.
                Log.e(TAG, "deleteAllKeys: 2. Excepción durante el borrado de hardware.", e)
                _snackbarMessage.emit("El proceso de borrado fue interrumpido.")
                // hardwareSuccess permanece 'false' para que se ejecute la lógica de reversión.
            }

            // --- PASO 3: FINALIZAR LA OPERACIÓN BASADO EN EL RESULTADO ---
            if (hardwareSuccess) {
                // SI EL HARDWARE SE LIMPIÓ, LIMPIAR LA BASE DE DATOS LOCAL
                Log.d(TAG, "deleteAllKeys: 3. Éxito en hardware. Limpiando la base de datos local.")
                injectedKeyRepository.deleteAllKeys()
                _snackbarMessage.emit("Dispositivo y base de datos limpiados con éxito.")
            } else {
                // SI EL HARDWARE FALLÓ O LA TAREA FUE CANCELADA, REVERTIR EL ESTADO
                Log.w(TAG, "deleteAllKeys: 3. Fallo/Cancelación en hardware. Reviertiendo estado a 'SUCCESSFUL'.")
                // Asumimos que las llaves estaban 'SUCCESSFUL' antes de esto.
                // Una lógica más compleja podría guardar y restaurar el estado previo de cada llave.
                injectedKeyRepository.updateStatusForAllKeys("SUCCESSFUL")
                _snackbarMessage.emit("Error: No se pudo borrar del dispositivo. Se revirtieron los cambios.")
            }
        }
    }

    private fun mapStringToGenericKeyType(keyTypeString: String): GenericKeyType {
        return try {
            GenericKeyType.valueOf(keyTypeString)
        } catch (e: IllegalArgumentException) {
            if (keyTypeString.contains("MASTER") || keyTypeString.contains("TRANSPORT")) {
                GenericKeyType.MASTER_KEY
            } else {
                GenericKeyType.WORKING_PIN_KEY
            }
        }
    }
}