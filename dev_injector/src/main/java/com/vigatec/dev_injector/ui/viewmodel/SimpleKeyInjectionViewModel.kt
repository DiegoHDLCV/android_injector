package com.vigatec.dev_injector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manufacturer.KeySDKManager
import com.example.manufacturer.base.controllers.ped.PedException
import com.example.manufacturer.base.models.KeyAlgorithm
import com.example.manufacturer.base.models.KeyType
import com.example.manufacturer.base.models.PedKeyData
import com.example.persistence.repository.InjectedKeyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import com.vigatec.utils.KcvCalculator.calculateKcv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

data class SimpleKeyInjectionUiState(
    val keySlot: String = "10",
    val keyType: String = KeyType.MASTER_KEY.name,
    val keyValue: String = "CB79E0898F2907C24A13516BEAE904A2", // Valor por defecto
    // Para working keys: index de la master key que cifra
    val masterKeyIndex: String = "0",
    // Para master keys: slot de la KTK (Key Transport Key) 
    val ktkSlot: String = "15",
    // Para DUKPT: KSN (Key Serial Number)
    val ksnValue: String = "F8765432100000000000", // KSN por defecto
    val keySlotError: String? = null,
    val keyValueError: String? = null,
    val masterKeyIndexError: String? = null,
    val ktkSlotError: String? = null,
    val ksnValueError: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class SimpleKeyInjectionViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SimpleKeyInjectionViewModel"
    }

    private val _uiState = MutableStateFlow(SimpleKeyInjectionUiState())
    val uiState: StateFlow<SimpleKeyInjectionUiState> = _uiState.asStateFlow()

    fun updateKeySlot(slot: String) {
        _uiState.value = _uiState.value.copy(
            keySlot = slot,
            keySlotError = validateSlot(slot)
        )
    }

    fun updateKeyType(type: String) {
        _uiState.value = _uiState.value.copy(keyType = type)
    }

    fun updateKeyValue(value: String) {
        val cleanValue = value.uppercase().replace(" ", "")
        _uiState.value = _uiState.value.copy(
            keyValue = cleanValue,
            keyValueError = validateKeyValue(cleanValue)
        )
    }

    fun updateMasterKeyIndex(index: String) {
        _uiState.value = _uiState.value.copy(
            masterKeyIndex = index,
            masterKeyIndexError = validateSlot(index)
        )
    }

    fun updateKtkSlot(slot: String) {
        _uiState.value = _uiState.value.copy(
            ktkSlot = slot,
            ktkSlotError = validateSlot(slot)
        )
    }

    fun updateKsnValue(ksn: String) {
        val cleanKsn = ksn.uppercase().replace(" ", "")
        _uiState.value = _uiState.value.copy(
            ksnValue = cleanKsn,
            ksnValueError = validateKsnValue(cleanKsn)
        )
    }

    fun injectKey() {
        val state = _uiState.value
        
        // Validación
        val slotError = validateSlot(state.keySlot)
        val keyValueError = validateKeyValue(state.keyValue)
        
        // Validar campos específicos según el tipo de llave
        val keyType = KeyType.valueOf(state.keyType)
        val masterKeyIndexError = if (isWorkingKey(keyType)) {
            validateSlot(state.masterKeyIndex)
        } else null
        
        val ktkSlotError = if (keyType == KeyType.MASTER_KEY) {
            validateSlot(state.ktkSlot)
        } else null
        
        val ksnValueError = if (keyType == KeyType.DUKPT_INITIAL_KEY) {
            validateKsnValue(state.ksnValue)
        } else null
        
        if (slotError != null || keyValueError != null || 
            masterKeyIndexError != null || ktkSlotError != null || ksnValueError != null) {
            _uiState.value = state.copy(
                keySlotError = slotError,
                keyValueError = keyValueError,
                masterKeyIndexError = masterKeyIndexError,
                ktkSlotError = ktkSlotError,
                ksnValueError = ksnValueError
            )
            return
        }

        val slot = state.keySlot.toInt()
        val keyAlgorithm = KeyAlgorithm.DES_TRIPLE

        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting simple key injection for slot $slot, type: $keyType, algorithm: $keyAlgorithm")
                
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = null
                )

                val pedController = KeySDKManager.getPedController()
                    ?: throw IllegalStateException("PED no disponible")
                
                Log.d(TAG, "PED Controller obtained: ${pedController::class.java.simpleName}")

                val keyBytes = hexToBytes(state.keyValue)
                
                Log.d(TAG, "Key data prepared - Key length: ${keyBytes.size} bytes")
                
                // PASO 1: Verificar si ya existe una llave en este slot
                Log.d(TAG, "Checking if key already exists in slot $slot")
                val existingKey = injectedKeyRepository.getKeyBySlotAndType(slot, keyType.name)
                if (existingKey != null) {
                    Log.w(TAG, "Key already exists in slot $slot: ${existingKey.keyType} (KCV: ${existingKey.kcv})")
                    Log.i(TAG, "Deleting existing key from slot $slot before injecting new key")
                    
                    // Eliminar la llave existente del hardware
                    try {
                        val deleteSuccess = pedController.deleteKey(slot, keyType)
                        if (deleteSuccess) {
                            Log.i(TAG, "✅ Successfully deleted existing key from slot $slot")
                            // Eliminar de la base de datos
                            injectedKeyRepository.deleteKey(existingKey)
                            Log.i(TAG, "✅ Successfully deleted existing key from database")
                        } else {
                            Log.w(TAG, "⚠️ Hardware deletion returned false for slot $slot, continuing anyway...")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to delete existing key from slot $slot", e)
                        // Continuar con la inyección ya que writeKey podría sobrescribir
                    }
                } else {
                    Log.d(TAG, "No existing key found in slot $slot, proceeding with injection")
                }
                
                // PASO 2: Inyectar la nueva llave usando el patrón del test
                val success = when (keyType) {
                    KeyType.TRANSPORT_KEY -> {
                        // Transport Keys se inyectan en texto plano (como injectTransportKey)
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Inyectando Transport Key en texto plano..."
                        )
                        Log.d(TAG, "Injecting TRANSPORT_KEY as plain text (like injectTransportKey)")
                        val result = pedController.writeKeyPlain(slot, keyType, keyAlgorithm, keyBytes, null)
                        Log.d(TAG, "writeKeyPlain returned: $result for TRANSPORT_KEY in slot $slot")
                        result
                    }
                    KeyType.MASTER_KEY -> {
                        // Master Key: inyección en texto plano (como injectTransportKey para masters en el test)
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Inyectando Master Key en texto plano..."
                        )
                        Log.d(TAG, "Injecting MASTER_KEY as plain text (like injectTransportKey for masters)")
                        val result = pedController.writeKeyPlain(slot, keyType, keyAlgorithm, keyBytes, null)
                        Log.d(TAG, "writeKeyPlain returned: $result for MASTER_KEY in slot $slot")
                        result
                    }
                    KeyType.DUKPT_INITIAL_KEY -> {
                        // DUKPT Initial Key: inyección en texto plano con KSN
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Inyectando DUKPT Initial Key en texto plano..."
                        )
                        Log.d(TAG, "Injecting DUKPT_INITIAL_KEY as plain text")

                        Log.d(TAG, "Using KSN: ${state.ksnValue}")
                        // Convertir KSN de hex a bytes
                        val ksnBytes = if (state.ksnValue.isNotEmpty()) {
                            hexToBytes(state.ksnValue)
                        } else {
                            // KSN por defecto si no se especifica
                            hexToBytes("F8765432100000000000")
                        }
                        
                        val result = pedController.writeDukptInitialKey(
                            groupIndex = slot, 
                            keyAlgorithm = keyAlgorithm, 
                            keyBytes = keyBytes, 
                            initialKsn = ksnBytes, 
                            keyChecksum = null
                        )
                        Log.d(TAG, "writeDukptInitialKey returned: $result for DUKPT_INITIAL_KEY in slot $slot")
                        result
                    }
                    else -> {
                        // Working Keys: cifrado en software con Master Key (como en el test)
                        val masterKeySlot = state.masterKeyIndex.toInt()
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Inyectando Working Key cifrada con Master Key..."
                        )
                        
                        Log.d(TAG, "Injecting WORKING_KEY using software encryption with Master Key from slot $masterKeySlot")
                        
                        // Obtener la Master Key de la base de datos para el cifrado en software
                        val masterKeyEntity = injectedKeyRepository.getKeyBySlotAndType(masterKeySlot, KeyType.MASTER_KEY.name)
                        if (masterKeyEntity == null) {
                            throw IllegalStateException("Master Key no encontrada en slot $masterKeySlot. Debe inyectarse primero.")
                        }
                        
                        // Cifrar la Working Key usando la Master Key (como softwareEncrypt en el test)
                        val masterKeyBytes = hexToBytes(masterKeyEntity.keyData)
                        val encryptedWorkingKey = softwareEncrypt(masterKeyBytes, keyBytes)
                        
                        Log.d(TAG, "Working key encrypted in software, calling pedController.writeKey")
                        val result = pedController.writeKey(
                            keyIndex = slot,
                            keyType = keyType,
                            keyAlgorithm = keyAlgorithm,
                            keyData = PedKeyData(encryptedWorkingKey),
                            transportKeyIndex = masterKeySlot,
                            transportKeyType = KeyType.MASTER_KEY
                        )
                        Log.d(TAG, "writeKey returned: $result for WORKING_KEY using Master Key")
                        result
                    }
                }

                if (success) {
                    Log.i(TAG, "Key injection SUCCESSFUL for slot $slot")
                    
                    val kcv = calculateKcv(state.keyValue)
                    
                    Log.d(TAG, "Calculated KCV: $kcv")
                    
                    // Registrar en BD
                    injectedKeyRepository.recordKeyInjectionWithData(
                        keySlot = slot,
                        keyType = keyType.name,
                        keyAlgorithm = keyAlgorithm.name,
                        kcv = kcv,
                        keyData = state.keyValue,
                        status = "SUCCESSFUL"
                    )
                    
                    Log.d(TAG, "Key injection recorded in database as SUCCESSFUL")

                    _uiState.value = _uiState.value.copy(
                        statusMessage = "✓ ${keyType.name} inyectada en slot $slot"
                    )
                } else {
                    Log.e(TAG, "Key injection FAILED - PED Controller returned false for slot $slot")
                    throw PedException("Fallo en la inyección - PED Controller retornó false")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception during key injection for slot $slot", e)
                _uiState.value = _uiState.value.copy(
                    statusMessage = "✗ Error: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    private fun softwareEncrypt(masterKeyBytes: ByteArray, workingKeyBytes: ByteArray): ByteArray {
        return try {
            val cipher = javax.crypto.Cipher.getInstance("DESede/ECB/NoPadding")
            val keySpec = javax.crypto.spec.SecretKeySpec(masterKeyBytes, "DESede")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
            cipher.doFinal(workingKeyBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error during software encryption", e)
            throw RuntimeException("Error cifrando Working Key con Master Key: ${e.message}", e)
        }
    }

    private fun validateSlot(slot: String): String? {
        if (slot.isEmpty()) return "Requerido"
        val slotNum = slot.toIntOrNull()
        return when {
            slotNum == null -> "Solo números"
            slotNum < 0 || slotNum > 255 -> "0-255"
            else -> null
        }
    }

    private fun isWorkingKey(keyType: KeyType): Boolean {
        return when (keyType) {
            KeyType.WORKING_PIN_KEY,
            KeyType.WORKING_MAC_KEY,
            KeyType.WORKING_DATA_ENCRYPTION_KEY -> true
            else -> false
        }
    }

    private fun validateKeyValue(value: String): String? {
        if (value.isEmpty()) return "Requerido"
        if (!value.matches(Regex("^[0-9A-Fa-f]*$"))) {
            return "Solo hex (0-9, A-F)"
        }
        if (value.length != 32) {
            return "32 caracteres hex"
        }
        return null
    }

    private fun validateKsnValue(value: String): String? {
        if (value.isEmpty()) return "Requerido"
        if (!value.matches(Regex("^[0-9A-Fa-f]*$"))) {
            return "Solo hex (0-9, A-F)"
        }
        if (value.length != 20) {
            return "20 caracteres hex (10 bytes)"
        }
        return null
    }

    private fun hexToBytes(hexString: String): ByteArray {
        return hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun calculateSimpleKcv(keyBytes: ByteArray): String {
        return try {
            val hash = MessageDigest.getInstance("SHA-256").digest(keyBytes)
            hash.take(3).joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            "000000"
        }
    }
}