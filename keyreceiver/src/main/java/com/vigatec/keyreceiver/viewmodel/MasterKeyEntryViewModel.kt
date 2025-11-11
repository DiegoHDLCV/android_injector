package com.vigatec.keyreceiver.viewmodel

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.config.KeyCombinationMethod
import com.vigatec.config.SystemConfig
import com.vigatec.manufacturer.KeySDKManager
import com.vigatec.manufacturer.base.controllers.ped.PedException
import com.vigatec.manufacturer.base.controllers.ped.PedKeyException
import com.vigatec.manufacturer.base.models.KeyAlgorithm
import com.vigatec.manufacturer.base.models.KeyType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MasterKeyEntryViewModel @Inject constructor(
    //private val secureStorageManager: SecureStorageManager // Comentado según tu versión
) : ViewModel() {

    // --- VALORES POR DEFECTO ---
    // Asegúrate que estos tienen la longitud correcta (32 caracteres hex en este ejemplo)
    private val defaultComponent1 = "0123456789ABCDEF0123456789ABCDEF"
    private val defaultComponent2 = "FEDCBA9876543210FEDCBA9876543210"
    // --- FIN VALORES POR DEFECTO ---


    // Inicializar con los valores por defecto
    private val _component1 = mutableStateOf(defaultComponent1) // <-- CAMBIO AQUÍ
    val component1: State<String> = _component1

    private val _component2 = mutableStateOf(defaultComponent2) // <-- CAMBIO AQUÍ
    val component2: State<String> = _component2

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private val _successMessage = mutableStateOf<String?>(null)
    val successMessage: State<String?> = _successMessage

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val TAG = "MasterKeyEntryVM"


    fun onComponent1Change(newValue: String) {
        _component1.value = newValue.uppercase().filter { it.isDigit() || it in 'A'..'F' }
        clearMessages()
    }

    fun onComponent2Change(newValue: String) {
        _component2.value = newValue.uppercase().filter { it.isDigit() || it in 'A'..'F' }
        clearMessages()
    }

    private fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    // CAMBIADO: Función de combinación de componentes ahora es parametrizable
    // @OptIn(ExperimentalStdlibApi::class) // Ya no es necesario si usas ConversionUtils
    @OptIn(ExperimentalStdlibApi::class)
    private fun combineKeyComponents(comp1Hex: String, comp2Hex: String): Result<ByteArray> {
        return when (SystemConfig.keyCombinationMethod) {
            KeyCombinationMethod.XOR_PLACEHOLDER -> {
                // ¡¡¡ADVERTENCIA DE SEGURIDAD!!! ESTE MÉTODO ES INSEGURO.
                Log.w(TAG, "Usando método de combinación XOR (INSEGURO - SOLO PARA PRUEBAS).")
                try {
                    val comp1Bytes = comp1Hex.hexToByteArray() // Usando ConversionUtils
                    val comp2Bytes = comp2Hex.hexToByteArray() // Usando ConversionUtils
                    if (comp1Bytes.isEmpty() || comp2Bytes.isEmpty()) {
                        return Result.failure(IllegalArgumentException("Components cannot be empty for XOR."))
                    }
                    if (comp1Bytes.size != comp2Bytes.size) {
                        return Result.failure(IllegalArgumentException("Key components must have the same length for XOR combination."))
                    }
                    val resultBytes = ByteArray(comp1Bytes.size)
                    for (i in comp1Bytes.indices) {
                        resultBytes[i] = (comp1Bytes[i].toInt() xor comp2Bytes[i].toInt()).toByte()
                    }
                    Log.d(TAG, "Combined key (XOR - INSECURE): ${resultBytes.toHexString()}") // Usando ConversionUtils
                    Result.success(resultBytes)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en combinación XOR: ${e.message}", e)
                    Result.failure(IllegalArgumentException("Error en combinación XOR: ${e.message}", e))
                }
            }
            KeyCombinationMethod.STANDARD_ACQUIRER_METHOD_1 -> {
                Log.e(TAG, "Método de combinación 'STANDARD_ACQUIRER_METHOD_1' NO IMPLEMENTADO.")
                Result.failure(NotImplementedError("Método de combinación 'STANDARD_ACQUIRER_METHOD_1' no implementado."))
            }
            KeyCombinationMethod.SECURE_DERIVATION_ABC -> {
                Log.e(TAG, "Método de combinación 'SECURE_DERIVATION_ABC' NO IMPLEMENTADO.")
                Result.failure(NotImplementedError("Método de combinación 'SECURE_DERIVATION_ABC' no implementado."))
            }
        }
    }

    // @OptIn(ExperimentalStdlibApi::class) // Ya no es necesario si usas ConversionUtils
    @OptIn(ExperimentalStdlibApi::class)
    fun generateMasterKeyA() {
        clearMessages()
        val comp1 = _component1.value.trim()
        val comp2 = _component2.value.trim()

        // Validaciones ...
        if (comp1.isEmpty() || comp2.isEmpty()) {
            _errorMessage.value = "Please enter both key components."
            return
        }
        val expectedHexLength = 32 // AJUSTA ESTO: Ejemplo para clave TDES de 16 bytes
        if (comp1.length != expectedHexLength || comp2.length != expectedHexLength) {
            _errorMessage.value = "Components must be $expectedHexLength hex digits long (e.g., 16-byte key)."
            return
        }


        _isLoading.value = true
        viewModelScope.launch {
            try {
                val combinedKeyResult = combineKeyComponents(comp1, comp2)
                val combinedKeyBytes = combinedKeyResult.getOrElse {
                    throw PedKeyException("Failed to combine key components: ${it.message}", it)
                }

                val pedController = KeySDKManager.getPedController()
                    ?: throw PedException("PED Controller not available. Is SDK initialized and device connected?")

                val keyIndexToInject = 0
                val keyTypeToInject = KeyType.MASTER_KEY
                val keyAlgorithmToInject = KeyAlgorithm.DES_TRIPLE // Asegúrate que esto es correcto

                Log.d(TAG, "Attempting to inject key: Index=$keyIndexToInject, Type=$keyTypeToInject, Alg=$keyAlgorithmToInject, Key(Hex)=${combinedKeyBytes.toHexString()}") // Usando ConversionUtils

                val success = pedController.writeKeyPlain(
                    keyIndex = keyIndexToInject,
                    keyType = keyTypeToInject,
                    keyAlgorithm = keyAlgorithmToInject,
                    keyBytes = combinedKeyBytes,
                    kcvBytes = combinedKeyBytes
                )

                if (success) {
                    Log.i(TAG, "Master Key A injection successful for index $keyIndexToInject.")
                    _successMessage.value = "Master Key A (Index $keyIndexToInject) generated and injected successfully!"
                    _component1.value = "" // Limpiar campos después del éxito
                    _component2.value = "" // Limpiar campos después del éxito
                    // secureStorageManager.setMasterKeyALoaded(true) // Comentado

                } else {
                    Log.e(TAG, "PED controller reported failure for writeKeyPlain without throwing an exception.")
                    _errorMessage.value = "Key injection failed (PED reported failure)."
                    // secureStorageManager.setMasterKeyALoaded(false) // Comentado
                }

            } catch (e: NotImplementedError) {
                Log.e(TAG, "Error de implementación: ${e.message}", e)
                _errorMessage.value = "Error: ${e.message}"
            } catch (e: PedKeyException) {
                Log.e(TAG, "Key Error during Master Key A generation/injection: ${e.message}", e)
                _errorMessage.value = "Key Error: ${e.message}"
                // secureStorageManager.setMasterKeyALoaded(false) // Comentado
            } catch (e: PedException) {
                Log.e(TAG, "PED Error during Master Key A generation/injection: ${e.message}", e)
                _errorMessage.value = "PED Error: ${e.message}"
                // secureStorageManager.setMasterKeyALoaded(false) // Comentado
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Argument/Validation Error: ${e.message}", e)
                _errorMessage.value = "Input Error: ${e.message}"
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during Master Key A generation/injection: ${e.message}", e)
                _errorMessage.value = "Unexpected Error: Please check logs."
                // secureStorageManager.setMasterKeyALoaded(false) // Comentado
            } finally {
                _isLoading.value = false
            }
        }
    }
}