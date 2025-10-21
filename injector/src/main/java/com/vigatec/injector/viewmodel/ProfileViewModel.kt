package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.entities.KeyConfiguration
import com.example.persistence.entities.ProfileEntity
import com.example.persistence.repository.InjectedKeyRepository
import com.example.persistence.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilesScreenState(
    val profiles: List<ProfileEntity> = emptyList(),
    val filteredProfiles: List<ProfileEntity> = emptyList(),
    val availableKeys: List<InjectedKeyEntity> = emptyList(),
    val isLoading: Boolean = true,
    val selectedProfile: ProfileEntity? = null,
    val showCreateModal: Boolean = false,
    val showManageKeysModal: Boolean = false,
    val showInjectModal: Boolean = false,
    val formData: ProfileFormData = ProfileFormData(),
    val searchQuery: String = ""
)

data class ProfileFormData(
    val id: Long? = null,
    val name: String = "",
    val description: String = "",
    val appType: String = "",
    val keyConfigurations: List<KeyConfiguration> = emptyList(),
    val useKTK: Boolean = false,
    val selectedKTKKcv: String = "",
    val currentKTK: InjectedKeyEntity? = null // KTK activa del almacén
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfilesScreenState())
    val state = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            combine(
                profileRepository.getAllProfiles(),
                injectedKeyRepository.getAllInjectedKeys()
            ) { profiles, keys ->
                ProfilesScreenState(
                    profiles = profiles,
                    filteredProfiles = profiles, // Inicialmente sin filtro
                    availableKeys = keys,
                    isLoading = false
                )
            }.collect {
                _state.value = it
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        filterProfiles(query)
    }

    private fun filterProfiles(query: String) {
        val filtered = if (query.isBlank()) {
            _state.value.profiles
        } else {
            _state.value.profiles.filter { profile ->
                // Filtrar por nombre
                profile.name.contains(query, ignoreCase = true) ||
                // Filtrar por descripción
                profile.description.contains(query, ignoreCase = true) ||
                // Filtrar por tipo de aplicación
                profile.applicationType.contains(query, ignoreCase = true) ||
                // Filtrar por KCV de llaves configuradas
                profile.keyConfigurations.any { config ->
                    config.selectedKey.contains(query, ignoreCase = true) ||
                    config.usage.contains(query, ignoreCase = true) ||
                    config.keyType.contains(query, ignoreCase = true)
                }
            }
        }
        _state.value = _state.value.copy(filteredProfiles = filtered)
    }
    
    fun onShowCreateModal(profile: ProfileEntity? = null) {
        viewModelScope.launch {
            // Obtener la KEK activa del almacén
            val currentKTK = injectedKeyRepository.getCurrentKEK()
            
            val formData = if (profile != null) {
                ProfileFormData(
                    id = profile.id,
                    name = profile.name,
                    description = profile.description,
                    appType = profile.applicationType,
                    keyConfigurations = profile.keyConfigurations,
                    useKTK = profile.useKEK,
                    selectedKTKKcv = currentKTK?.kcv ?: profile.selectedKEKKcv, // Usar la KTK activa si existe
                    currentKTK = currentKTK
                )
            } else {
                ProfileFormData(
                    currentKTK = currentKTK,
                    selectedKTKKcv = currentKTK?.kcv ?: "" // Auto-seleccionar KTK activa si existe
                )
            }
            _state.value = _state.value.copy(showCreateModal = true, selectedProfile = profile, formData = formData)
        }
    }

    fun onDismissCreateModal() {
        _state.value = _state.value.copy(showCreateModal = false, selectedProfile = null)
    }

    fun onFormDataChange(formData: ProfileFormData) {
        _state.value = _state.value.copy(formData = formData)
    }

    fun onSaveProfile() {
        viewModelScope.launch {
            val formData = _state.value.formData
            
            // Validar que el nombre no esté vacío
            if (formData.name.trim().isEmpty()) {
                // Aquí podrías mostrar un error al usuario
                return@launch
            }
            
            val profile = ProfileEntity(
                id = formData.id ?: 0,
                name = formData.name.trim(),
                description = formData.description,
                applicationType = formData.appType,
                keyConfigurations = formData.keyConfigurations,
                useKEK = formData.useKTK,
                selectedKEKKcv = formData.selectedKTKKcv
            )
            
            try {
                // Guardar o actualizar el perfil
                if (formData.id == null) {
                    profileRepository.insertProfile(profile)
                } else {
                    profileRepository.updateProfile(profile)
                }
                
                onDismissCreateModal()
            } catch (e: Exception) {
                // Manejar el error - en una implementación real mostrarías esto al usuario
                android.util.Log.e("ProfileViewModel", "Error saving profile", e)
            }
        }
    }

    fun onDeleteProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profile)
        }
    }

    fun addKeyConfiguration() {
        val currentConfigs = _state.value.formData.keyConfigurations
        val newConfig = KeyConfiguration(
            id = System.currentTimeMillis(),
            usage = "",
            keyType = "",
            slot = "",
            selectedKey = "",
            injectionMethod = "auto",
            ksn = ""
        )
        onFormDataChange(_state.value.formData.copy(keyConfigurations = currentConfigs + newConfig))
    }

    fun removeKeyConfiguration(configId: Long) {
        val currentConfigs = _state.value.formData.keyConfigurations
        onFormDataChange(_state.value.formData.copy(keyConfigurations = currentConfigs.filterNot { it.id == configId }))
    }

    fun updateKeyConfiguration(configId: Long, field: String, value: String) {
        val currentConfigs = _state.value.formData.keyConfigurations
        val updatedConfigs = currentConfigs.map {
            if (it.id == configId) {
                when (field) {
                    "usage" -> it.copy(usage = value)
                    "keyType" -> it.copy(keyType = value)
                    "slot" -> it.copy(slot = value)
                    "selectedKey" -> it.copy(selectedKey = value)
                    "ksn" -> it.copy(ksn = value)
                    else -> it
                }
            } else {
                it
            }
        }
        onFormDataChange(_state.value.formData.copy(keyConfigurations = updatedConfigs))
    }
} 