package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.repository.InjectedKeyRepository
import com.vigatec.utils.KcvCalculator
import com.vigatec.utils.KeyStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class KeyAlgorithmType(val displayName: String, val bytesRequired: Int) {
    DES_TRIPLE("3DES (16 bytes)", 16),
    AES_128("AES-128 (16 bytes)", 16),
    AES_192("AES-192 (24 bytes)", 24),
    AES_256("AES-256 (32 bytes)", 32)
}

data class CeremonyState(
    val currentStep: Int = 1, // 1: Config, 2: Custodios, 3: Finalización
    val numCustodians: Int = 2,
    val currentCustodian: Int = 1,
    val component: String = "",
    val components: List<String> = emptyList(),
    val showComponent: Boolean = false,
    val partialKCV: String = "",
    val finalKCV: String = "",
    val isLoading: Boolean = false,
    val isCeremonyInProgress: Boolean = false,
    val isCeremonyFinished: Boolean = false,

    // Nuevos campos para configuración de llave
    val customName: String = "",              // Nombre personalizado
    val selectedKeyType: KeyAlgorithmType = KeyAlgorithmType.DES_TRIPLE,  // Tipo de algoritmo seleccionado
    val componentError: String? = null        // Error de validación de componente
)

@HiltViewModel
class CeremonyViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CeremonyState(component = "E59D620E1A6D311F19342054AB01ABF7"))
    val uiState = _uiState.asStateFlow()

    fun addToLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    }

    fun onNumCustodiansChange(num: Int) {
        _uiState.value = _uiState.value.copy(numCustodians = num)
    }

    fun onComponentChange(component: String) {
        _uiState.value = _uiState.value.copy(component = component)
    }

    fun onToggleShowComponent() {
        _uiState.value = _uiState.value.copy(showComponent = !_uiState.value.showComponent)
    }


    fun onCustomNameChange(name: String) {
        _uiState.value = _uiState.value.copy(customName = name)
    }

    fun onKeyTypeChange(keyType: KeyAlgorithmType) {
        _uiState.value = _uiState.value.copy(selectedKeyType = keyType)
    }

    /**
     * Valida que el componente tenga la longitud correcta según el tipo de llave seleccionado
     */
    private fun validateComponentLength(component: String): Boolean {
        val expectedBytes = _uiState.value.selectedKeyType.bytesRequired
        val expectedHexLength = expectedBytes * 2 // Cada byte = 2 caracteres hex

        return component.length == expectedHexLength
    }

    fun startCeremony() {
        addToLog("=== INICIANDO CEREMONIA DE LLAVES ===")
        addToLog("Configuración inicial:")
        addToLog("  - Número de custodios: ${_uiState.value.numCustodians}")
        addToLog("  - Tipo de llave: ${_uiState.value.selectedKeyType.displayName}")
        addToLog("  - Longitud esperada: ${_uiState.value.selectedKeyType.bytesRequired * 2} caracteres hex")
        addToLog("  - Componente inicial: ${_uiState.value.component}")
        addToLog("  - Estado del repositorio: Inicializado")
        addToLog("  - Base de datos: Conectada")
        addToLog("================================================")

        _uiState.value = _uiState.value.copy(
            currentStep = 2,
            isCeremonyInProgress = true,
            isLoading = false
        )
    }

    fun addComponent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, partialKCV = "", componentError = null)
            try {
                val component = _uiState.value.component

                // Validar longitud del componente
                if (!validateComponentLength(component)) {
                    val expectedBytes = _uiState.value.selectedKeyType.bytesRequired
                    val expectedHexLength = expectedBytes * 2
                    _uiState.value = _uiState.value.copy(
                        componentError = "Se esperan $expectedHexLength caracteres hex, recibidos: ${component.length}",
                        isLoading = false
                    )
                    return@launch
                }

                val kcv = KcvCalculator.calculateKcv(component)
                _uiState.value = _uiState.value.copy(partialKCV = kcv, isLoading = false, componentError = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, componentError = e.message)
            }
        }
    }

    fun nextCustodian() {
        val next = _uiState.value.currentCustodian + 1

        val nextComponent = if (next == 2) {
            "ED77D12E82AF6099968D6F5653741D09"
        } else {
            ""
        }

        _uiState.value = _uiState.value.copy(
            currentCustodian = next,
            components = _uiState.value.components + _uiState.value.component,
            component = nextComponent,
            partialKCV = "",
            showComponent = false
        )
    }

    fun finalizeCeremony() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val finalComponents = _uiState.value.components + _uiState.value.component

                if (finalComponents.size != _uiState.value.numCustodians) {
                    throw IllegalStateException("Número incorrecto de componentes: ${finalComponents.size} vs ${_uiState.value.numCustodians}")
                }


                
                val finalKeyBytes = finalComponents
                    .map { component ->
                        val bytes = KcvCalculator.hexStringToByteArray(component)
                        bytes
                    }
                    .reduce { acc, bytes -> 
                        val result = KcvCalculator.xorByteArrays(acc, bytes)
                        result
                    }

                val finalKeyHex = finalKeyBytes.joinToString("") { "%02X".format(it) }



                
                val finalKcv = KcvCalculator.calculateKcv(finalKeyHex)



                try {
                    KeyStoreManager.storeMasterKey("master_transport_key", finalKeyBytes)

                } catch (e: Exception) {

                }

                // Todas las llaves se crean como operacionales
                val keyType = "CEREMONY_KEY"
                val keyStatus = "GENERATED"

                // Detectar el algoritmo basado en el tipo seleccionado
                val detectedAlgorithm = when (_uiState.value.selectedKeyType) {
                    KeyAlgorithmType.DES_TRIPLE -> "DES_TRIPLE"
                    KeyAlgorithmType.AES_128 -> "AES_128"
                    KeyAlgorithmType.AES_192 -> "AES_192"
                    KeyAlgorithmType.AES_256 -> "AES_256"
                }


                // CRÍTICO: Guardar la llave con su algoritmo detectado
                // El slot se asignará cuando se use la llave en un perfil (por ahora -1)
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = -1, // -1 indica que no hay slot asignado (se asignará en el perfil)
                    keyType = keyType, // Siempre CEREMONY_KEY (operacional)
                    keyAlgorithm = detectedAlgorithm, // Guardar el algoritmo detectado
                    kcv = finalKcv,
                    keyData = finalKeyHex, // ¡GUARDANDO LA LLAVE COMPLETA!
                    status = keyStatus,
                    isKEK = false, // Siempre false - se puede cambiar desde el almacén
                    customName = _uiState.value.customName // Nombre personalizado
                )



                _uiState.value = _uiState.value.copy(
                    currentStep = 3,
                    finalKCV = finalKcv,
                    isCeremonyFinished = true,
                    isLoading = false
                )


            } catch (e: Exception) {
                addToLog("Error al finalizar la ceremonia: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun cancelCeremony() {
        _uiState.value = CeremonyState() // Resetea al estado inicial
    }


}