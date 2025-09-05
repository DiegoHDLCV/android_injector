package com.vigatec.dev_injector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manufacturer.KeySDKManager
import com.example.manufacturer.base.controllers.ped.PedException
import com.example.manufacturer.base.models.KeyAlgorithm
import com.example.manufacturer.base.models.KeyType
import com.example.manufacturer.base.models.PedKeyData
import com.example.persistence.repository.InjectedKeyRepository
import com.vigatec.dev_injector.data.models.PredefinedKey
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class KeyInjectionUiState(
    val keySlot: String = "",
    val keyType: String = KeyType.MASTER_KEY.name,
    val keyAlgorithm: String = KeyAlgorithm.DES_TRIPLE.name,
    val keyValue: String = "",
    val kcv: String = "",
    // Para working keys: index de la master key que cifra
    val masterKeyIndex: String = "0",
    // Para master keys: slot de la KTK (Key Transport Key) 
    val ktkSlot: String = "15",
    val keySlotError: String? = null,
    val keyValueError: String? = null,
    val kcvError: String? = null,
    val masterKeyIndexError: String? = null,
    val ktkSlotError: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class KeyInjectionViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    companion object {
        private const val TAG = "KeyInjectionViewModel"
    }

    private val _uiState = MutableStateFlow(KeyInjectionUiState())
    val uiState: StateFlow<KeyInjectionUiState> = _uiState.asStateFlow()

    fun updateKeySlot(slot: String) {
        _uiState.value = _uiState.value.copy(
            keySlot = slot,
            keySlotError = validateSlot(slot)
        )
    }

    fun updateKeyType(type: String) {
        _uiState.value = _uiState.value.copy(keyType = type)
    }

    fun updateKeyAlgorithm(algorithm: String) {
        _uiState.value = _uiState.value.copy(
            keyAlgorithm = algorithm,
            keyValueError = validateKeyValue(_uiState.value.keyValue, algorithm)
        )
    }

    fun updateKeyValue(value: String) {
        _uiState.value = _uiState.value.copy(
            keyValue = value.uppercase(),
            keyValueError = validateKeyValue(value, _uiState.value.keyAlgorithm)
        )
    }

    fun updateKcv(kcv: String) {
        _uiState.value = _uiState.value.copy(
            kcv = kcv.uppercase(),
            kcvError = validateKcv(kcv)
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

    fun injectKey() {
        val state = _uiState.value
        
        // Validar todos los campos
        val slotError = validateSlot(state.keySlot)
        val keyValueError = validateKeyValue(state.keyValue, state.keyAlgorithm)
        val kcvError = validateKcv(state.kcv)
        
        // Validar campos específicos según el tipo de llave
        val keyType = KeyType.valueOf(state.keyType)
        val masterKeyIndexError = if (isWorkingKey(keyType)) {
            validateSlot(state.masterKeyIndex)
        } else null
        
        val ktkSlotError = if (keyType == KeyType.MASTER_KEY) {
            validateSlot(state.ktkSlot)
        } else null
        
        if (slotError != null || keyValueError != null || kcvError != null || 
            masterKeyIndexError != null || ktkSlotError != null) {
            _uiState.value = state.copy(
                keySlotError = slotError,
                keyValueError = keyValueError,
                kcvError = kcvError,
                masterKeyIndexError = masterKeyIndexError,
                ktkSlotError = ktkSlotError
            )
            return
        }

        val slot = state.keySlot.toInt()
        val keyAlgorithm = KeyAlgorithm.valueOf(state.keyAlgorithm)
        val keyValueHex = state.keyValue
        val kcvHex = state.kcv.takeIf { it.isNotEmpty() }

        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting key injection for slot $slot, type: $keyType, algorithm: $keyAlgorithm")
                
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = null
                )

                // Convertir hex string a ByteArray
                val keyBytes = hexStringToByteArray(keyValueHex)
                val kcvBytes = kcvHex?.let { hexStringToByteArray(it) }
                
                Log.d(TAG, "Key data prepared - Key length: ${keyBytes.size} bytes, KCV provided: ${kcvBytes != null}")

                // Crear PedKeyData
                val keyData = PedKeyData(keyBytes, kcvBytes)

                // Obtener el controlador PED
                val pedController = KeySDKManager.getPedController()
                    ?: throw IllegalStateException("PED Controller no está disponible")
                
                Log.d(TAG, "PED Controller obtained: ${pedController::class.java.simpleName}")

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
                val success = if (keyType == KeyType.TRANSPORT_KEY) {
                    // Transport Keys se inyectan en texto plano (como injectTransportKey en el test)
                    Log.d(TAG, "Injecting TRANSPORT_KEY in plaintext (like injectTransportKey)")
                    Log.d(TAG, "Calling pedController.writeKeyPlain")
                    pedController.writeKeyPlain(slot, keyType, keyAlgorithm, keyBytes, kcvBytes)
                } else if (isWorkingKey(keyType)) {
                    // Working Keys: cifrarlas en software con la Master Key y luego inyectarlas
                    val masterKeySlot = state.masterKeyIndex.toInt()
                    Log.d(TAG, "Injecting WORKING_KEY using software encryption with Master Key from slot $masterKeySlot")
                    
                    // Obtener la Master Key de la base de datos para el cifrado en software
                    val masterKeyEntity = injectedKeyRepository.getKeyBySlotAndType(masterKeySlot, KeyType.MASTER_KEY.name)
                    if (masterKeyEntity == null) {
                        throw IllegalStateException("Master Key no encontrada en slot $masterKeySlot. Debe inyectarse primero.")
                    }
                    
                    // Cifrar la Working Key usando la Master Key (como softwareEncrypt en el test)
                    val masterKeyBytes = hexStringToByteArray(masterKeyEntity.keyData)
                    val encryptedWorkingKey = softwareEncrypt(masterKeyBytes, keyBytes)
                    
                    Log.d(TAG, "Working key encrypted in software, calling pedController.writeKey")
                    // Usar writeKey con la Master Key como transport key para descifrar
                    pedController.writeKey(
                        keyIndex = slot,
                        keyType = keyType,
                        keyAlgorithm = keyAlgorithm,
                        keyData = PedKeyData(encryptedWorkingKey, kcvBytes),
                        transportKeyIndex = masterKeySlot,
                        transportKeyType = KeyType.MASTER_KEY
                    )
                } else {
                    // Master Key u otros: inyección en claro (equivale a injectTransportKey para master keys en el test)
                    Log.d(TAG, "Injecting MASTER_KEY in plaintext (like injectTransportKey for masters)")
                    Log.d(TAG, "Calling pedController.writeKeyPlain")
                    pedController.writeKeyPlain(slot, keyType, keyAlgorithm, keyBytes, kcvBytes)
                }
                
                Log.d(TAG, "PED Controller writeKey returned: $success")

                if (success) {
                    Log.i(TAG, "Key injection SUCCESSFUL for slot $slot")
                    
                    // Calcular KCV si no se proporcionó
                    val calculatedKcv = kcvHex ?: calculateKcv(keyBytes, keyAlgorithm)
                    
                    Log.d(TAG, "Calculated/provided KCV: $calculatedKcv")
                    
                    // Registrar la inyección en la base de datos
                    injectedKeyRepository.recordKeyInjectionWithData(
                        keySlot = slot,
                        keyType = keyType.name,
                        keyAlgorithm = keyAlgorithm.name,
                        kcv = calculatedKcv,
                        keyData = keyValueHex,
                        status = "SUCCESSFUL"
                    )
                    
                    Log.d(TAG, "Key injection recorded in database as SUCCESSFUL")

                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Llave inyectada exitosamente en slot $slot"
                    )
                    
                    // Limpiar el formulario después del éxito
                    clearForm()
                } else {
                    Log.e(TAG, "Key injection FAILED - PED Controller returned false for slot $slot")
                    throw PedException("La inyección de la llave falló - PED Controller retornó false")
                }

            } catch (e: PedException) {
                Log.e(TAG, "PED Exception during key injection for slot $slot", e)
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Error del PED: ${e.message}"
                )
                
                // Registrar el fallo en la base de datos
                try {
                    injectedKeyRepository.recordKeyInjection(
                        keySlot = slot,
                        keyType = keyType.name,
                        keyAlgorithm = keyAlgorithm.name,
                        kcv = kcvHex ?: "N/A",
                        status = "FAILED"
                    )
                    Log.d(TAG, "Key injection failure recorded in database")
                } catch (dbError: Exception) {
                    Log.e(TAG, "Failed to record key injection failure in database", dbError)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception during key injection for slot $slot", e)
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Error inesperado: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun validateSlot(slot: String): String? {
        if (slot.isEmpty()) return "El slot es requerido"
        val slotNum = slot.toIntOrNull()
        return when {
            slotNum == null -> "Solo se permiten números"
            slotNum < 0 || slotNum > 255 -> "El slot debe ser entre 0 y 255"
            else -> null
        }
    }

    private fun validateKeyValue(value: String, algorithm: String): String? {
        if (value.isEmpty()) return "El valor de la llave es requerido"
        if (!value.matches(Regex("^[0-9A-Fa-f]*$"))) {
            return "Solo se permiten caracteres hexadecimales (0-9, A-F)"
        }
        val expectedLength = getExpectedKeyLength(algorithm)
        if (value.length != expectedLength) {
            return "La longitud debe ser $expectedLength caracteres para $algorithm"
        }
        return null
    }

    private fun validateKcv(kcv: String): String? {
        if (kcv.isNotEmpty() && !kcv.matches(Regex("^[0-9A-Fa-f]*$"))) {
            return "Solo se permiten caracteres hexadecimales (0-9, A-F)"
        }
        return null
    }

    private fun getExpectedKeyLength(algorithm: String): Int {
        return when (algorithm) {
            KeyAlgorithm.DES_SINGLE.name -> 16 // 8 bytes = 16 hex chars
            KeyAlgorithm.DES_DOUBLE.name -> 32 // 16 bytes = 32 hex chars
            KeyAlgorithm.DES_TRIPLE.name -> 32 // 16 bytes = 32 hex chars
            KeyAlgorithm.AES_128.name -> 32 // 16 bytes = 32 hex chars
            KeyAlgorithm.AES_192.name -> 48 // 24 bytes = 48 hex chars
            KeyAlgorithm.AES_256.name -> 64 // 32 bytes = 64 hex chars
            KeyAlgorithm.SM4.name -> 32 // 16 bytes = 32 hex chars
            else -> 32 // Default
        }
    }

    private fun clearForm() {
        _uiState.value = KeyInjectionUiState()
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        val cleanHex = hexString.replace(" ", "").uppercase()
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
        }
        
        return data
    }

    private fun calculateKcv(keyBytes: ByteArray, algorithm: KeyAlgorithm): String {
        return try {
            when (algorithm) {
                KeyAlgorithm.DES_SINGLE, KeyAlgorithm.DES_DOUBLE, KeyAlgorithm.DES_TRIPLE -> {
                    // Para DES, usar los primeros 3 bytes encriptados con la llave
                    val first3Bytes = keyBytes.take(3).toByteArray()
                    val encrypted = encryptWithKey(first3Bytes, keyBytes)
                    encrypted.take(3).joinToString("") { "%02X".format(it) }
                }
                KeyAlgorithm.AES_128, KeyAlgorithm.AES_192, KeyAlgorithm.AES_256 -> {
                    // Para AES, usar los primeros 3 bytes encriptados con la llave
                    val first3Bytes = keyBytes.take(3).toByteArray()
                    val encrypted = encryptWithKey(first3Bytes, keyBytes)
                    encrypted.take(3).joinToString("") { "%02X".format(it) }
                }
                else -> {
                    // Para otros algoritmos, usar hash de los primeros bytes
                    val hash = MessageDigest.getInstance("SHA-256").digest(keyBytes)
                    hash.take(3).joinToString("") { "%02X".format(it) }
                }
            }
        } catch (e: Exception) {
            // Si falla el cálculo, usar un hash simple
            val hash = MessageDigest.getInstance("SHA-256").digest(keyBytes)
            hash.take(3).joinToString("") { "%02X".format(it) }
        }
    }

    private fun encryptWithKey(data: ByteArray, key: ByteArray): ByteArray {
        // Implementación simplificada para KCV
        // En un entorno real, usarías la implementación criptográfica apropiada
        val result = ByteArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return result
    }
    
    private fun softwareEncrypt(masterKeyBytes: ByteArray, workingKeyBytes: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
            val keySpec = SecretKeySpec(masterKeyBytes, "DESede")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            cipher.doFinal(workingKeyBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error during software encryption", e)
            throw PedException("Error cifrando Working Key con Master Key: ${e.message}", e)
        }
    }

    fun injectPredefinedKey(predefinedKey: PredefinedKey) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting predefined key injection: ${predefinedKey.name} into slot ${predefinedKey.keyIndex}")
                
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = "Inyectando ${predefinedKey.name}..."
                )

                // Crear PedKeyData con la llave predefinida
                val keyData = PedKeyData(predefinedKey.keyBytes, null)
                
                Log.d(TAG, "Predefined key data prepared - ${predefinedKey.name} length: ${predefinedKey.keyBytes.size} bytes")

                // Obtener el controlador PED
                val pedController = KeySDKManager.getPedController()
                    ?: throw IllegalStateException("PED Controller no está disponible")
                
                Log.d(TAG, "PED Controller obtained for predefined key: ${pedController::class.java.simpleName}")

                // PASO 1: Verificar si ya existe una llave en este slot
                Log.d(TAG, "Checking if key already exists in slot ${predefinedKey.keyIndex}")
                val existingKey = injectedKeyRepository.getKeyBySlotAndType(predefinedKey.keyIndex, predefinedKey.keyType.name)
                if (existingKey != null) {
                    Log.w(TAG, "Key already exists in slot ${predefinedKey.keyIndex}: ${existingKey.keyType} (KCV: ${existingKey.kcv})")
                    Log.i(TAG, "Deleting existing key from slot ${predefinedKey.keyIndex} before injecting ${predefinedKey.name}")
                    
                    // Eliminar la llave existente del hardware
                    try {
                        val deleteSuccess = pedController.deleteKey(predefinedKey.keyIndex, predefinedKey.keyType)
                        if (deleteSuccess) {
                            Log.i(TAG, "✅ Successfully deleted existing key from slot ${predefinedKey.keyIndex}")
                            // Eliminar de la base de datos
                            injectedKeyRepository.deleteKey(existingKey)
                            Log.i(TAG, "✅ Successfully deleted existing key from database")
                        } else {
                            Log.w(TAG, "⚠️ Hardware deletion returned false for slot ${predefinedKey.keyIndex}, continuing anyway...")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to delete existing key from slot ${predefinedKey.keyIndex}", e)
                        // Continuar con la inyección ya que writeKey podría sobrescribir
                    }
                } else {
                    Log.d(TAG, "No existing key found in slot ${predefinedKey.keyIndex}, proceeding with injection")
                }

                // PASO 2: Inyectar la llave predefinida
                Log.d(TAG, "Calling pedController.writeKey for predefined key ${predefinedKey.name}")
                val success = pedController.writeKey(
                    keyIndex = predefinedKey.keyIndex,
                    keyType = predefinedKey.keyType,
                    keyAlgorithm = predefinedKey.keyAlgorithm,
                    keyData = keyData
                )
                
                Log.d(TAG, "PED Controller writeKey returned: $success for predefined key ${predefinedKey.name}")

                if (success) {
                    Log.i(TAG, "Predefined key injection SUCCESSFUL: ${predefinedKey.name} in slot ${predefinedKey.keyIndex}")
                    
                    // Calcular KCV para la llave predefinida
                    val calculatedKcv = calculateKcv(predefinedKey.keyBytes, predefinedKey.keyAlgorithm)
                    
                    Log.d(TAG, "Calculated KCV for predefined key ${predefinedKey.name}: $calculatedKcv")
                    
                    // Registrar la inyección en la base de datos
                    injectedKeyRepository.recordKeyInjectionWithData(
                        keySlot = predefinedKey.keyIndex,
                        keyType = predefinedKey.keyType.name,
                        keyAlgorithm = predefinedKey.keyAlgorithm.name,
                        kcv = calculatedKcv,
                        keyData = predefinedKey.keyBytes.joinToString("") { "%02X".format(it) },
                        status = "SUCCESSFUL"
                    )
                    
                    Log.d(TAG, "Predefined key injection recorded in database as SUCCESSFUL: ${predefinedKey.name}")

                    _uiState.value = _uiState.value.copy(
                        statusMessage = "${predefinedKey.name} inyectada exitosamente en slot ${predefinedKey.keyIndex}"
                    )
                    
                } else {
                    Log.e(TAG, "Predefined key injection FAILED - PED Controller returned false for ${predefinedKey.name}")
                    throw PedException("La inyección de ${predefinedKey.name} falló - PED Controller retornó false")
                }

            } catch (e: PedException) {
                Log.e(TAG, "PED Exception during predefined key injection: ${predefinedKey.name}", e)
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Error del PED al inyectar ${predefinedKey.name}: ${e.message}"
                )
                
                // Registrar el fallo en la base de datos
                try {
                    injectedKeyRepository.recordKeyInjection(
                        keySlot = predefinedKey.keyIndex,
                        keyType = predefinedKey.keyType.name,
                        keyAlgorithm = predefinedKey.keyAlgorithm.name,
                        kcv = "N/A",
                        status = "FAILED"
                    )
                    Log.d(TAG, "Predefined key injection failure recorded in database: ${predefinedKey.name}")
                } catch (dbError: Exception) {
                    Log.e(TAG, "Failed to record predefined key injection failure in database: ${predefinedKey.name}", dbError)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception during predefined key injection: ${predefinedKey.name}", e)
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Error inesperado al inyectar ${predefinedKey.name}: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
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

}
