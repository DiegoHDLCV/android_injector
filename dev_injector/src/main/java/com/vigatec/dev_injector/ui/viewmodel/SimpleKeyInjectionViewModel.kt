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
    val keySlotError: String? = null,
    val keyValueError: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class SimpleKeyInjectionViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

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

    fun injectKey() {
        val state = _uiState.value
        
        // Validación
        val slotError = validateSlot(state.keySlot)
        val keyValueError = validateKeyValue(state.keyValue)
        
        if (slotError != null || keyValueError != null) {
            _uiState.value = state.copy(
                keySlotError = slotError,
                keyValueError = keyValueError
            )
            return
        }

        val slot = state.keySlot.toInt()
        val keyType = KeyType.valueOf(state.keyType)
        val keyAlgorithm = KeyAlgorithm.DES_TRIPLE

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    statusMessage = null
                )

                val pedController = KeySDKManager.getPedController()
                    ?: throw IllegalStateException("PED no disponible")

                val keyBytes = hexToBytes(state.keyValue)
                
                val success = when (keyType) {
                    KeyType.MASTER_KEY, KeyType.TRANSPORT_KEY -> {
                        // Inyectar Master Keys y Transport Keys en texto plano
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Inyectando ${keyType.name} en texto plano..."
                        )
                        pedController.writeKeyPlain(slot, keyType, keyAlgorithm, keyBytes, null)
                    }
                    else -> {
                        // Para working keys, necesitamos una transport key
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Verificando transport key para working key..."
                        )
                        
                        // Verificar si existe transport key
                        val transportKeyIndex = 15
                        val transportKeyType = KeyType.TRANSPORT_KEY
                        
                        if (!pedController.isKeyPresent(transportKeyIndex, transportKeyType)) {
                            // Inyectar transport key primero
                            val transportKeyBytes = hexToBytes("A1B2C3D4E5F60708090A0B0C0D0E0F10")
                            pedController.writeKeyPlain(transportKeyIndex, transportKeyType, keyAlgorithm, transportKeyBytes, null)
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "Inyectando working key cifrada..."
                        )
                        
                        // Cifrar la llave con la transport key y usar writeKey cifrado
                        val encryptedKeyData = softwareEncrypt(hexToBytes("A1B2C3D4E5F60708090A0B0C0D0E0F10"), keyBytes)
                        pedController.writeKey(slot, keyType, keyAlgorithm, PedKeyData(encryptedKeyData), transportKeyIndex, transportKeyType)
                    }
                }

                if (success) {
                    val kcv = calculateSimpleKcv(keyBytes)
                    
                    // Registrar en BD
                    injectedKeyRepository.recordKeyInjectionWithData(
                        keySlot = slot,
                        keyType = keyType.name,
                        keyAlgorithm = keyAlgorithm.name,
                        kcv = kcv,
                        keyData = state.keyValue,
                        status = "SUCCESSFUL"
                    )

                    _uiState.value = _uiState.value.copy(
                        statusMessage = "✓ ${keyType.name} inyectada en slot $slot"
                    )
                } else {
                    throw PedException("Fallo en la inyección")
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "✗ Error: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    private fun softwareEncrypt(keyBytes: ByteArray, data: ByteArray): ByteArray {
        return try {
            val cipher = javax.crypto.Cipher.getInstance("DESede/ECB/NoPadding")
            val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "DESede")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            throw RuntimeException("Error cifrando llave: ${e.message}", e)
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