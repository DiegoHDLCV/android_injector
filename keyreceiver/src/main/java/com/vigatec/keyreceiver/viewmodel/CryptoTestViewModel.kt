package com.vigatec.keyreceiver.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manufacturer.KeySDKManager
import com.example.manufacturer.base.models.BlockCipherMode
import com.example.manufacturer.base.models.KeyAlgorithm
import com.example.manufacturer.base.models.KeyType
import com.example.manufacturer.base.models.PedCipherRequest
import com.example.persistence.repository.InjectedKeyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CryptoTestState(
    val isLoading: Boolean = false,
    val inputData: String = "1234567890ABCDEF",
    val encryptedData: String = "",
    val decryptedData: String = "",
    val errorMessage: String? = null,
    val selectedKeySlot: Int = 1,
    val testResult: String? = null,
    val availableKeys: List<KeyInfo> = emptyList(),
    val kcvValidation: String? = null
)

data class KeyInfo(
    val slot: Int,
    val keyType: String,
    val algorithm: String,
    val kcv: String,
    val isKEK: Boolean = false
)

@HiltViewModel
class CryptoTestViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    private val TAG = "CryptoTestViewModel"
    private val keySDKManager = KeySDKManager

    private val _state = MutableStateFlow(CryptoTestState())
    val state: StateFlow<CryptoTestState> = _state.asStateFlow()

    init {
        loadAvailableKeys()
    }

    private fun loadAvailableKeys() {
        viewModelScope.launch {
            try {
                val allKeys = injectedKeyRepository.getAllInjectedKeysSync()
                val keys = allKeys
                    // Incluir todas las llaves para permitir pruebas
                    .map { key ->
                        KeyInfo(
                            slot = key.keySlot,
                            keyType = key.keyType,
                            algorithm = key.keyAlgorithm,
                            kcv = key.kcv,
                            isKEK = key.isKEK
                        )
                    }

                _state.value = _state.value.copy(
                    availableKeys = keys,
                    selectedKeySlot = keys.firstOrNull()?.slot ?: 1
                )

                Log.d(TAG, "Llaves disponibles para pruebas: ${keys.size}")
                keys.forEach {
                    val keyLabel = if (it.isKEK) "KEK/KTK" else it.keyType
                    Log.d(TAG, "  - Slot ${it.slot}: $keyLabel (${it.algorithm}) KCV=${it.kcv}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando llaves disponibles", e)
                _state.value = _state.value.copy(
                    errorMessage = "Error cargando llaves: ${e.message}"
                )
            }
        }
    }

    fun updateInputData(data: String) {
        _state.value = _state.value.copy(inputData = data)
    }

    fun updateSelectedKeySlot(slot: Int) {
        _state.value = _state.value.copy(selectedKeySlot = slot)
    }

    fun verifyKcv() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                kcvValidation = null
            )

            try {
                val pedController = keySDKManager.getPedController()
                    ?: throw Exception("PED Controller no disponible")

                val selectedKey = _state.value.availableKeys.find {
                    it.slot == _state.value.selectedKeySlot
                }
                    ?: throw Exception("Llave en slot ${_state.value.selectedKeySlot} no encontrada")

                Log.d(TAG, "=== VERIFICACIÓN DE KCV ===" )
                Log.d(TAG, "Llave seleccionada:")
                Log.d(TAG, "  - Slot: ${selectedKey.slot}")
                Log.d(TAG, "  - Tipo: ${selectedKey.keyType}")
                Log.d(TAG, "  - Algoritmo: ${selectedKey.algorithm}")
                Log.d(TAG, "  - KCV almacenado: ${selectedKey.kcv}")

                // Verificar si es MASTER_KEY
                if (selectedKey.keyType == "MASTER_KEY") {
                    throw Exception("Las Master Keys no permiten cifrado directo en NewPOS.\n" +
                            "Solo las Working Keys pueden cifrar datos.\n\n" +
                            "El KCV almacenado (${selectedKey.kcv}) fue calculado durante la inyección.")
                }

                // Determinar algoritmo
                val algorithm = when (selectedKey.algorithm) {
                    "DES_SINGLE" -> KeyAlgorithm.DES_SINGLE
                    "DES_DOUBLE" -> KeyAlgorithm.DES_DOUBLE
                    "DES_TRIPLE" -> KeyAlgorithm.DES_TRIPLE
                    "AES_128" -> KeyAlgorithm.AES_128
                    "AES_192" -> KeyAlgorithm.AES_192
                    "AES_256" -> KeyAlgorithm.AES_256
                    else -> throw Exception("Algoritmo no soportado: ${selectedKey.algorithm}")
                }

                // Determinar tamaño del bloque
                val blockSize = when (algorithm) {
                    KeyAlgorithm.AES_128, KeyAlgorithm.AES_192, KeyAlgorithm.AES_256 -> 16
                    else -> 8 // DES
                }

                // Crear bloque de ceros
                val zeroBlock = ByteArray(blockSize) { 0 }
                Log.d(TAG, "Bloque de ceros (${zeroBlock.size} bytes): ${zeroBlock.toHexString()}")

                // Cifrar bloque de ceros
                Log.d(TAG, ">>> Cifrando bloque de ceros...")
                val encryptRequest = PedCipherRequest(
                    encrypt = true,
                    keyType = KeyType.MASTER_KEY,  // Las working keys también usan este tipo en el request
                    keyIndex = selectedKey.slot,
                    algorithm = algorithm,
                    mode = BlockCipherMode.ECB,
                    data = zeroBlock,
                    iv = null
                )

                val encryptResult = pedController.encrypt(encryptRequest)
                val encryptedZeros = encryptResult.resultData
                Log.d(TAG, "✓ Cifrado exitoso (${encryptedZeros.size} bytes)")
                Log.d(TAG, "Resultado: ${encryptedZeros.toHexString()}")

                // Tomar primeros 3 bytes como KCV
                val calculatedKcv = encryptedZeros.take(3).toByteArray().toHexString()
                Log.d(TAG, "KCV calculado: $calculatedKcv")
                Log.d(TAG, "KCV almacenado: ${selectedKey.kcv}")

                val matches = calculatedKcv.equals(selectedKey.kcv, ignoreCase = true)

                val validationMessage = if (matches) {
                    "✅ KCV VÁLIDO\n\n" +
                    "El KCV calculado coincide con el almacenado.\n\n" +
                    "Proceso:\n" +
                    "1. Se cifró un bloque de ceros: ${zeroBlock.toHexString()}\n" +
                    "2. Resultado del cifrado: ${encryptedZeros.toHexString()}\n" +
                    "3. KCV (primeros 3 bytes): $calculatedKcv\n\n" +
                    "KCV almacenado: ${selectedKey.kcv}\n" +
                    "✓ Coinciden perfectamente"
                } else {
                    "❌ KCV NO COINCIDE\n\n" +
                    "El KCV calculado NO coincide con el almacenado.\n\n" +
                    "KCV calculado: $calculatedKcv\n" +
                    "KCV almacenado: ${selectedKey.kcv}\n\n" +
                    "Esto puede indicar:\n" +
                    "- La llave en el PED no es la correcta\n" +
                    "- Error en el proceso de inyección"
                }

                Log.d(TAG, "=== RESULTADO ===" )
                Log.d(TAG, if (matches) "✅ KCV VÁLIDO" else "❌ KCV INVÁLIDO")
                Log.d(TAG, "==================")

                _state.value = _state.value.copy(
                    isLoading = false,
                    kcvValidation = validationMessage
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error verificando KCV", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.message}",
                    kcvValidation = "❌ ERROR\n\n${e.message}"
                )
            }
        }
    }

    fun testEncryptDecrypt() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                testResult = null,
                encryptedData = "",
                decryptedData = "",
                kcvValidation = null
            )

            try {
                val pedController = keySDKManager.getPedController()
                    ?: throw Exception("PED Controller no disponible")

                val selectedKey = _state.value.availableKeys.find {
                    it.slot == _state.value.selectedKeySlot
                }
                    ?: throw Exception("Llave en slot ${_state.value.selectedKeySlot} no encontrada")

                // Verificar si es MASTER_KEY
                if (selectedKey.keyType == "MASTER_KEY") {
                    throw Exception("Las Master Keys no permiten cifrado directo en NewPOS.\n\n" +
                            "Limitación del hardware:\n" +
                            "- Las Master Keys solo pueden derivar Working Keys\n" +
                            "- No se pueden usar para cifrar datos directamente\n\n" +
                            "Para probar cifrado/descifrado, necesitas una Working Key " +
                            "(PIN_KEY, MAC_KEY, o DATA_ENCRYPTION_KEY).")
                }

                Log.d(TAG, "=== INICIANDO PRUEBA DE CIFRADO/DESCIFRADO ===")
                Log.d(TAG, "Llave seleccionada:")
                Log.d(TAG, "  - Slot: ${selectedKey.slot}")
                Log.d(TAG, "  - Tipo: ${selectedKey.keyType}")
                Log.d(TAG, "  - Algoritmo: ${selectedKey.algorithm}")
                Log.d(TAG, "  - KCV: ${selectedKey.kcv}")

                // Convertir input a bytes
                val inputBytes = _state.value.inputData.toByteArray(Charsets.UTF_8)
                Log.d(TAG, "Datos de entrada: '${_state.value.inputData}'")
                Log.d(TAG, "Bytes de entrada (${inputBytes.size}): ${inputBytes.toHexString()}")

                // Determinar algoritmo basado en la llave
                val algorithm = when (selectedKey.algorithm) {
                    "DES_SINGLE" -> KeyAlgorithm.DES_SINGLE
                    "DES_DOUBLE" -> KeyAlgorithm.DES_DOUBLE
                    "DES_TRIPLE" -> KeyAlgorithm.DES_TRIPLE
                    "AES_128" -> KeyAlgorithm.AES_128
                    "AES_192" -> KeyAlgorithm.AES_192
                    "AES_256" -> KeyAlgorithm.AES_256
                    else -> throw Exception("Algoritmo no soportado: ${selectedKey.algorithm}")
                }

                // Ajustar datos al tamaño del bloque
                val blockSize = when (algorithm) {
                    KeyAlgorithm.AES_128, KeyAlgorithm.AES_192, KeyAlgorithm.AES_256 -> 16
                    else -> 8 // DES
                }

                val paddedInput = padData(inputBytes, blockSize)
                Log.d(TAG, "Datos con padding (${paddedInput.size} bytes): ${paddedInput.toHexString()}")

                // 1. CIFRAR
                Log.d(TAG, ">>> Paso 1: CIFRANDO...")
                val encryptRequest = PedCipherRequest(
                    encrypt = true,
                    keyType = KeyType.MASTER_KEY,
                    keyIndex = selectedKey.slot,
                    algorithm = algorithm,
                    mode = BlockCipherMode.ECB,
                    data = paddedInput,
                    iv = null
                )

                val encryptResult = pedController.encrypt(encryptRequest)
                val encryptedHex = encryptResult.resultData.toHexString()
                Log.d(TAG, "✓ Cifrado exitoso (${encryptResult.resultData.size} bytes)")
                Log.d(TAG, "Datos cifrados: $encryptedHex")

                // 2. DESCIFRAR
                Log.d(TAG, ">>> Paso 2: DESCIFRANDO...")
                val decryptRequest = PedCipherRequest(
                    encrypt = false,
                    keyType = KeyType.MASTER_KEY,
                    keyIndex = selectedKey.slot,
                    algorithm = algorithm,
                    mode = BlockCipherMode.ECB,
                    data = encryptResult.resultData,
                    iv = null
                )

                val decryptResult = pedController.decrypt(decryptRequest)
                val decryptedHex = decryptResult.resultData.toHexString()
                Log.d(TAG, "✓ Descifrado exitoso (${decryptResult.resultData.size} bytes)")
                Log.d(TAG, "Datos descifrados: $decryptedHex")

                // Quitar padding
                val unpaddedData = unpadData(decryptResult.resultData)
                val decryptedString = String(unpaddedData, Charsets.UTF_8)
                Log.d(TAG, "Datos descifrados (sin padding): '$decryptedString'")

                // Verificar que coincidan
                val isSuccess = _state.value.inputData == decryptedString

                val resultMessage = if (isSuccess) {
                    "✅ PRUEBA EXITOSA\n\n" +
                    "La llave en el slot ${selectedKey.slot} funciona correctamente.\n" +
                    "Los datos cifrados y descifrados coinciden.\n\n" +
                    "Original: '${_state.value.inputData}'\n" +
                    "Descifrado: '$decryptedString'\n\n" +
                    "KCV de la llave: ${selectedKey.kcv}"
                } else {
                    "❌ ERROR EN PRUEBA\n\n" +
                    "Los datos NO coinciden:\n" +
                    "Original: '${_state.value.inputData}'\n" +
                    "Descifrado: '$decryptedString'"
                }

                Log.d(TAG, "=== RESULTADO ===")
                Log.d(TAG, if (isSuccess) "✅ ÉXITO" else "❌ FALLO")
                Log.d(TAG, "==================")

                _state.value = _state.value.copy(
                    isLoading = false,
                    encryptedData = encryptedHex,
                    decryptedData = decryptedHex,
                    testResult = resultMessage
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error en prueba de cifrado/descifrado", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.message}",
                    testResult = "❌ ERROR\n\n${e.message}"
                )
            }
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }

    private fun padData(data: ByteArray, blockSize: Int): ByteArray {
        val paddingSize = blockSize - (data.size % blockSize)
        val paddedData = ByteArray(data.size + paddingSize)

        // Copiar datos originales
        data.copyInto(paddedData)

        // Agregar padding PKCS#7
        for (i in data.size until paddedData.size) {
            paddedData[i] = paddingSize.toByte()
        }

        return paddedData
    }

    private fun unpadData(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data

        val paddingSize = data.last().toInt() and 0xFF
        if (paddingSize > data.size || paddingSize == 0) {
            return data
        }

        // Verificar que todos los bytes de padding sean iguales
        for (i in data.size - paddingSize until data.size) {
            if (data[i].toInt() and 0xFF != paddingSize) {
                return data
            }
        }

        return data.copyOfRange(0, data.size - paddingSize)
    }
}
