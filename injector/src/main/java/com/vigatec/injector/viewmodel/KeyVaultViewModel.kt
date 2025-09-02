package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.repository.InjectedKeyRepository
import com.example.persistence.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeyWithProfiles(
    val key: InjectedKeyEntity,
    val assignedProfiles: List<String> = emptyList()
)

data class KeyVaultState(
    val keysWithProfiles: List<KeyWithProfiles> = emptyList(),
    val loading: Boolean = true,
    val selectedKey: InjectedKeyEntity? = null,
    val showDeleteModal: Boolean = false,
    val showViewModal: Boolean = false
)

@HiltViewModel
class KeyVaultViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyVaultState())
    val uiState = _uiState.asStateFlow()

    init {
        loadKeys()
    }

    fun loadKeys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            injectedKeyRepository.getAllInjectedKeys().collect { keys ->
                val keysWithProfiles = keys.map { key ->
                    val profiles = profileRepository.getProfileNamesByKeyKcv(key.kcv)
                    KeyWithProfiles(key = key, assignedProfiles = profiles)
                }
                _uiState.value = _uiState.value.copy(keysWithProfiles = keysWithProfiles, loading = false)
            }
        }
    }

    fun onDeleteKey(key: InjectedKeyEntity) {
        viewModelScope.launch {
            injectedKeyRepository.deleteKey(key)
            loadKeys() // Recargar
        }
    }

    fun onClearAllKeys() {
        viewModelScope.launch {
            injectedKeyRepository.deleteAllKeys()
            loadKeys() // Recargar
        }
    }

    fun onShowDeleteModal(key: InjectedKeyEntity) {
        _uiState.value = _uiState.value.copy(showDeleteModal = true, selectedKey = key)
    }

    fun onDismissDeleteModal() {
        _uiState.value = _uiState.value.copy(showDeleteModal = false, selectedKey = null)
    }
} 