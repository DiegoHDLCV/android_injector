package com.vigatec.dev_injector.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.repository.InjectedKeyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SimpleInjectedKeysUiState(
    val keys: List<InjectedKeyEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SimpleInjectedKeysViewModel @Inject constructor(
    private val repository: InjectedKeyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SimpleInjectedKeysUiState())
    val uiState: StateFlow<SimpleInjectedKeysUiState> = _uiState.asStateFlow()

    init {
        loadKeys()
    }

    fun refreshKeys() {
        loadKeys()
    }

    fun clearAllKeys() {
        viewModelScope.launch {
            try {
                repository.deleteAllKeys()
                loadKeys() // Recargar después de limpiar
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al limpiar llaves: ${e.message}"
                )
            }
        }
    }

    private fun loadKeys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            repository.getAllInjectedKeys()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Error al cargar llaves: ${e.message}"
                    )
                }
                .collect { keys ->
                    _uiState.value = _uiState.value.copy(
                        keys = keys,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }
}