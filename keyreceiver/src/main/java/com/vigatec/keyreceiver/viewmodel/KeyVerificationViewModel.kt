package com.vigatec.keyreceiver.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.manufacturer.KeySDKManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.base.controllers.ped.PedKeyException
import com.example.manufacturer.base.models.KeyAlgorithm
import com.example.manufacturer.base.models.KeyType
import com.vigatec.keyreceiver.data.InstalledKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class KeyVerificationViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "KeyVerificationViewModel"
        private const val MAX_SLOTS = 16 // Número máximo de slots a verificar
    }

    private val _installedKeys = MutableStateFlow<List<InstalledKey>>(emptyList())
    val installedKeys: StateFlow<List<InstalledKey>> = _installedKeys.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    private val _verificationProgress = MutableStateFlow(0)
    val verificationProgress: StateFlow<Int> = _verificationProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Verifica todas las llaves instaladas en el dispositivo
     */
    fun verifyAllKeys() {
        viewModelScope.launch {
            try {
                _isVerifying.value = true
                _errorMessage.value = null
                _verificationProgress.value = 0
                
                Log.i(TAG, "=== INICIANDO VERIFICACIÓN DE LLAVES ===")
                
                val keys = mutableListOf<InstalledKey>()
                val pedController = KeySDKManager.getPedController()
                
                if (pedController == null) {
                    throw PedKeyException("PED Controller no disponible. ¿Está el dispositivo conectado?")
                }

                // Verificar cada slot
                for (slot in 0 until MAX_SLOTS) {
                    try {
                        Log.d(TAG, "Verificando slot $slot...")
                        
                        // Intentar obtener información de la llave en este slot
                        val keyInfo = getKeyInfoFromSlot(pedController, slot)
                        
                        if (keyInfo != null) {
                            Log.d(TAG, "Slot $slot ocupado: KCV=${keyInfo.kcv}")
                            keys.add(keyInfo)
                        } else {
                            Log.d(TAG, "Slot $slot vacío")
                        }
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "Error verificando slot $slot: ${e.message}")
                        // Continuar con el siguiente slot (omitir errores)
                    }
                    
                    // Actualizar progreso
                    _verificationProgress.value = ((slot + 1) * 100) / MAX_SLOTS
                }
                
                _installedKeys.value = keys
                Log.i(TAG, "Verificación completada. ${keys.size} llaves encontradas")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error durante verificación de llaves", e)
                _errorMessage.value = "Error verificando llaves: ${e.message}"
            } finally {
                _isVerifying.value = false
            }
        }
    }

    /**
     * Obtiene información de una llave en un slot específico
     */
    private suspend fun getKeyInfoFromSlot(
        pedController: IPedController,
        slot: Int
    ): InstalledKey? = withContext(Dispatchers.IO) {
        try {
            // Intentar diferentes tipos de llaves para este slot
            val keyTypes = listOf(
                KeyType.MASTER_KEY,
                KeyType.TRANSPORT_KEY,
                KeyType.WORKING_PIN_KEY,
                KeyType.WORKING_DATA_KEY,
                KeyType.WORKING_MAC_KEY
            )
            
            for (keyType in keyTypes) {
                try {
                    // Intentar calcular KCV para este tipo de llave
                    val kcv = calculateKCVForSlot(pedController, slot, keyType)
                    if (kcv != null) {
                        return@withContext InstalledKey(
                            slot = slot,
                            kcv = kcv,
                            keyType = keyType.name,
                            isActive = true
                        )
                    }
                } catch (e: Exception) {
                    // Continuar con el siguiente tipo
                    Log.d(TAG, "Slot $slot no tiene llave de tipo $keyType")
                }
            }
            
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo información del slot $slot", e)
            null
        }
    }

    /**
     * Verifica si existe una llave en un slot específico sin crearla
     * NOTA: Esta es una implementación simplificada que no puede verificar llaves reales
     * En un dispositivo real, necesitarías métodos específicos del PED para verificar llaves existentes
     */
    private suspend fun calculateKCVForSlot(
        pedController: IPedController,
        slot: Int,
        keyType: KeyType
    ): String? = withContext(Dispatchers.IO) {
        try {
            // IMPLEMENTACIÓN SIMPLIFICADA: 
            // En un dispositivo real, aquí deberías usar métodos como:
            // - pedController.getKeyInfo(slot, keyType)
            // - pedController.verifyKeyExists(slot, keyType)
            // - pedController.calculateKCV(slot, keyType)
            
            // Por ahora, simulamos que NO hay llaves instaladas
            // Esto evita que se muestren llaves falsas
            Log.d(TAG, "Verificación de llave en slot $slot, tipo $keyType: No implementado")
            return@withContext null
            
        } catch (e: Exception) {
            Log.d(TAG, "No se pudo verificar llave en slot $slot, tipo $keyType: ${e.message}")
            null
        }
    }

    /**
     * Limpia los mensajes de error
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Refresca la lista de llaves instaladas
     */
    fun refreshKeys() {
        verifyAllKeys()
    }
}
