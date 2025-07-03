package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class CeremonyState(
    val currentStep: Int = 1, // 1: Config, 2: Custodios, 3: Finalización
    val numCustodians: Int = 2,
    val currentCustodian: Int = 1,
    val component: String = "",
    val showComponent: Boolean = false,
    val partialKCV: String = "",
    val finalKCV: String = "",
    val log: String = "Esperando inicio de ceremonia...\n",
    val isLoading: Boolean = false,
    val isCeremonyInProgress: Boolean = false,
    val isCeremonyFinished: Boolean = false,
)

@HiltViewModel
class CeremonyViewModel @Inject constructor() : ViewModel() {

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
        addToLog("Custodio ${_uiState.value.currentCustodian}: Componente '${_uiState.value.component}' agregado.")
        _uiState.value = _uiState.value.copy(
            partialKCV = "KCV_${_uiState.value.currentCustodian}", // KCV simulado
            isLoading = false
        )
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
            component = nextComponent,
            partialKCV = "",
            showComponent = false
        )
    }

    fun finalizeCeremony() {
        addToLog("Finalizando ceremonia...")
        _uiState.value = _uiState.value.copy(
            currentStep = 3,
            finalKCV = "FINAL_KCV_123456", // KCV final simulado
            isCeremonyFinished = true,
            isLoading = false
        )
        addToLog("Ceremonia completada. KCV Final: ${_uiState.value.finalKCV}")
    }

    fun cancelCeremony() {
        addToLog("Ceremonia cancelada.")
        _uiState.value = CeremonyState() // Resetea al estado inicial
    }
} 