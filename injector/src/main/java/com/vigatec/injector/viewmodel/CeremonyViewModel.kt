package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.repository.InjectedKeyRepository
import com.vigatec.utils.KcvCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CeremonyState(
    val currentStep: Int = 1, // 1: Config, 2: Custodios, 3: Finalización
    val numCustodians: Int = 2,
    val currentCustodian: Int = 1,
    val component: String = "",
    val components: List<String> = emptyList(),
    val showComponent: Boolean = false,
    val partialKCV: String = "",
    val finalKCV: String = "",
    val log: String = "Esperando inicio de ceremonia...\n",
    val isLoading: Boolean = false,
    val isCeremonyInProgress: Boolean = false,
    val isCeremonyFinished: Boolean = false,
)

@HiltViewModel
class CeremonyViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CeremonyState(component = "E59D620E1A6D311F19342054AB01ABF7"))
    val uiState = _uiState.asStateFlow()

    private fun addToLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _uiState.value = _uiState.value.copy(log = _uiState.value.log + "[$timestamp] $message\n")
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

    fun startCeremony() {
        addToLog("Iniciando ceremonia con ${_uiState.value.numCustodians} custodios.")
        _uiState.value = _uiState.value.copy(
            currentStep = 2,
            isCeremonyInProgress = true,
            isLoading = false
        )
    }

    fun addComponent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, partialKCV = "")
            try {
                val component = _uiState.value.component
                val kcv = KcvCalculator.calculateKcv(component)
                _uiState.value = _uiState.value.copy(partialKCV = kcv, isLoading = false)
                addToLog("Custodio ${_uiState.value.currentCustodian}: Componente verificado. KCV: $kcv")
            } catch (e: Exception) {
                addToLog("Error al verificar componente: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun nextCustodian() {
        val next = _uiState.value.currentCustodian + 1
        addToLog("Avanzando al custodio $next.")

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

    @OptIn(ExperimentalStdlibApi::class)
    fun finalizeCeremony() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addToLog("Finalizando ceremonia...")
            try {
                val finalComponents = _uiState.value.components + _uiState.value.component
                if (finalComponents.size != _uiState.value.numCustodians) {
                    throw IllegalStateException("Número incorrecto de componentes.")
                }

                val finalKeyBytes = finalComponents
                    .map { KcvCalculator.hexStringToByteArray(it) }
                    .reduce { acc, bytes -> KcvCalculator.xorByteArrays(acc, bytes) }

                val finalKeyHex = finalKeyBytes.toHexString()
                val finalKcv = KcvCalculator.calculateKcv(finalKeyHex)

                injectedKeyRepository.recordKeyInjection(
                    keySlot = 0, // Slot no se usa en ceremonia, se asigna en perfil
                    keyType = "MASTER_KEY_FROM_CEREMONY",
                    keyAlgorithm = "3DES",
                    kcv = finalKcv,
                    status = "GENERATED"
                )

                _uiState.value = _uiState.value.copy(
                    currentStep = 3,
                    finalKCV = finalKcv,
                    isCeremonyFinished = true,
                    isLoading = false
                )
                addToLog("Ceremonia completada. Llave guardada. KCV Final: $finalKcv")

            } catch (e: Exception) {
                addToLog("Error al finalizar la ceremonia: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun cancelCeremony() {
        addToLog("Ceremonia cancelada.")
        _uiState.value = CeremonyState() // Resetea al estado inicial
    }
} 