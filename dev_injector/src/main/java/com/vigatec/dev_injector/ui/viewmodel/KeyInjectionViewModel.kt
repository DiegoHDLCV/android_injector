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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

data class KeyInjectionUiState(
    val keySlot: String = "",
    val keyType: String = KeyType.MASTER_KEY.name,
    val keyAlgorithm: String = KeyAlgorithm.DES_TRIPLE.name,
    val keyValue: String = "",
    val kcv: String = "",
    val keySlotError: String? = null,
    val keyValueError: String? = null,
    val kcvError: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class KeyInjectionViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

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

    fun injectKey() {
        val state = _uiState.value
        
        // Validar todos los campos
        val slotError = validateSlot(state.keySlot)
        val keyValueError = validateKeyValue(state.keyValue, state.keyAlgorithm)
        val kcvError = validateKcv(state.kcv)
        
        if (slotError != null || keyValueError != null || kcvError != null) {
            _uiState.value = state.copy(
                keySlotError = slotError,
                keyValueError = keyValueError,
                kcvError = kcvError
            )
            return
        }

        val slot = state.keySlot.toInt()
        val keyType = KeyType.valueOf(state.keyType)
        val keyAlgorithm = KeyAlgorithm.valueOf(state.keyAlgorithm)
        val keyValueHex = state.keyValue
        val kcvHex = state.kcv.takeIf { it.isNotEmpty() }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = null
                )

                // Convertir hex string a ByteArray
                val keyBytes = hexStringToByteArray(keyValueHex)
                val kcvBytes = kcvHex?.let { hexStringToByteArray(it) }

                // Crear PedKeyData
                val keyData = PedKeyData(keyBytes, kcvBytes)

                // Obtener el controlador PED
                val pedController = KeySDKManager.getPedController()
                    ?: throw IllegalStateException("PED Controller no está disponible")

                // Inyectar la llave
                val success = pedController.writeKey(
                    keyIndex = slot,
                    keyType = keyType,
                    keyAlgorithm = keyAlgorithm,
                    keyData = keyData
                )

                if (success) {
                    // Calcular KCV si no se proporcionó
                    val calculatedKcv = kcvHex ?: calculateKcv(keyBytes, keyAlgorithm)
                    
                    // Registrar la inyección en la base de datos
                    injectedKeyRepository.recordKeyInjectionWithData(
                        keySlot = slot,
                        keyType = keyType.name,
                        keyAlgorithm = keyAlgorithm.name,
                        kcv = calculatedKcv,
                        keyData = keyValueHex,
                        status = "SUCCESSFUL"
                    )

                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Llave inyectada exitosamente en slot $slot"
                    )
                    
                    // Limpiar el formulario después del éxito
                    clearForm()
                } else {
                    throw PedException("La inyección de la llave falló")
                }

            } catch (e: PedException) {
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
                } catch (dbError: Exception) {
                    // Log del error de base de datos pero no interrumpir el flujo
                }
                
            } catch (e: Exception) {
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

    fun injectPredefinedKey(predefinedKey: PredefinedKey) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = "Inyectando ${predefinedKey.name}..."
                )

                // Crear PedKeyData con la llave predefinida
                val keyData = PedKeyData(predefinedKey.keyBytes, null)

                // Obtener el controlador PED
                val pedController = KeySDKManager.getPedController()
                    ?: throw IllegalStateException("PED Controller no está disponible")

                // Inyectar la llave predefinida
                val success = pedController.writeKey(
                    keyIndex = predefinedKey.keyIndex,
                    keyType = predefinedKey.keyType,
                    keyAlgorithm = predefinedKey.keyAlgorithm,
                    keyData = keyData
                )

                if (success) {
                    // Calcular KCV para la llave predefinida
                    val calculatedKcv = calculateKcv(predefinedKey.keyBytes, predefinedKey.keyAlgorithm)
                    
                    // Registrar la inyección en la base de datos
                    injectedKeyRepository.recordKeyInjectionWithData(
                        keySlot = predefinedKey.keyIndex,
                        keyType = predefinedKey.keyType.name,
                        keyAlgorithm = predefinedKey.keyAlgorithm.name,
                        kcv = calculatedKcv,
                        keyData = predefinedKey.keyBytes.joinToString("") { "%02X".format(it) },
                        status = "SUCCESSFUL"
                    )

                    _uiState.value = _uiState.value.copy(
                        statusMessage = "${predefinedKey.name} inyectada exitosamente en slot ${predefinedKey.keyIndex}"
                    )
                    
                } else {
                    throw PedException("La inyección de ${predefinedKey.name} falló")
                }

            } catch (e: PedException) {
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
                } catch (dbError: Exception) {
                    // Log del error de base de datos pero no interrumpir el flujo
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Error inesperado al inyectar ${predefinedKey.name}: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

}
