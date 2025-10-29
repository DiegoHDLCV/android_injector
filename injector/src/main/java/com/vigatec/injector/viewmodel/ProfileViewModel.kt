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
    val compatibleKeys: List<InjectedKeyEntity> = emptyList(), // Llaves filtradas por compatibilidad KTK
    val ktkCompatibilityWarning: String? = null, // Advertencia de compatibilidad KTK
    val isLoading: Boolean = true,
    val selectedProfile: ProfileEntity? = null,
    val showCreateModal: Boolean = false,
    val showManageKeysModal: Boolean = false,
    val showInjectModal: Boolean = false,
    val formData: ProfileFormData = ProfileFormData(),
    val searchQuery: String = "",
    // Campos para importación de perfiles
    val showImportModal: Boolean = false,
    val importJsonText: String = "",
    val importError: String? = null,
    val importWarnings: List<String> = emptyList()
)

data class ProfileFormData(
    val id: Long? = null,
    val name: String = "",
    val description: String = "",
    val appType: String = "",
    val keyConfigurations: List<KeyConfiguration> = emptyList(),
    val useKTK: Boolean = false,
    val selectedKTKKcv: String = "",
    val currentKTK: InjectedKeyEntity? = null, // KTK activa del almacén
    val deviceType: String = "AISINO" // AISINO o NEWPOS
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
            // Obtener la KTK activa del almacén (KEK_TRANSPORT, no KEK_STORAGE)
            val currentKTK = injectedKeyRepository.getCurrentKTK()

            val formData = if (profile != null) {
                ProfileFormData(
                    id = profile.id,
                    name = profile.name,
                    description = profile.description,
                    appType = profile.applicationType,
                    keyConfigurations = profile.keyConfigurations,
                    useKTK = profile.useKEK,
                    selectedKTKKcv = currentKTK?.kcv ?: profile.selectedKEKKcv, // Usar la KTK activa si existe
                    currentKTK = currentKTK,
                    deviceType = profile.deviceType
                )
            } else {
                ProfileFormData(
                    currentKTK = currentKTK,
                    selectedKTKKcv = currentKTK?.kcv ?: "", // Auto-seleccionar KTK activa si existe
                    deviceType = "AISINO" // Por defecto AISINO
                )
            }

            // Filtrar llaves compatibles y generar advertencia si aplica
            updateCompatibleKeys(currentKTK)

            _state.value = _state.value.copy(showCreateModal = true, selectedProfile = profile, formData = formData)
        }
    }

    /**
     * Filtra las llaves disponibles según compatibilidad con la KTK seleccionada.
     * Si KTK es 3DES → solo llaves 3DES (writeKey() solo soporta 3DES)
     * Si KTK es AES → advertir que writeKey() no soporta AES
     */
    private fun updateCompatibleKeys(currentKTK: InjectedKeyEntity?) {
        val allKeys = _state.value.availableKeys

        if (currentKTK == null || !_state.value.formData.useKTK) {
            // Sin KTK, mostrar todas las llaves
            _state.value = _state.value.copy(
                compatibleKeys = allKeys,
                ktkCompatibilityWarning = null
            )
            return
        }

        val ktkAlgorithm = currentKTK.keyAlgorithm

        when {
            // KTK es 3DES → solo mostrar llaves 3DES
            ktkAlgorithm.contains("DES", ignoreCase = true) -> {
                val compatibleKeys = allKeys.filter { key ->
                    key.keyAlgorithm.contains("DES", ignoreCase = true)
                }
                _state.value = _state.value.copy(
                    compatibleKeys = compatibleKeys,
                    ktkCompatibilityWarning = "⚠️ KTK 3DES seleccionada: Solo se mostrarán llaves 3DES compatibles con writeKey()"
                )
            }

            // KTK es AES → advertir que writeKey() no soporta AES
            ktkAlgorithm.contains("AES", ignoreCase = true) -> {
                _state.value = _state.value.copy(
                    compatibleKeys = emptyList(),
                    ktkCompatibilityWarning = "❌ KTK AES no soportada: El método writeKey() solo acepta 3DES. Para inyectar llaves AES, necesitas usar DUKPT (requiere implementación futura)"
                )
            }

            // Otros algoritmos → mostrar todas
            else -> {
                _state.value = _state.value.copy(
                    compatibleKeys = allKeys,
                    ktkCompatibilityWarning = "⚠️ Algoritmo KTK desconocido: ${ktkAlgorithm}. Verifica compatibilidad manualmente."
                )
            }
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
                selectedKEKKcv = formData.selectedKTKKcv,
                deviceType = formData.deviceType
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

    // Funciones para importación de perfiles
    fun onShowImportModal() {
        _state.value = _state.value.copy(
            showImportModal = true,
            importJsonText = "",
            importError = null,
            importWarnings = emptyList()
        )
    }

    fun onDismissImportModal() {
        _state.value = _state.value.copy(
            showImportModal = false,
            importJsonText = "",
            importError = null,
            importWarnings = emptyList()
        )
    }

    fun onImportJsonChange(text: String) {
        _state.value = _state.value.copy(
            importJsonText = text,
            importError = null
        )
    }

    fun onImportProfile() {
        viewModelScope.launch {
            try {
                val jsonText = _state.value.importJsonText.trim()
                
                if (jsonText.isEmpty()) {
                    _state.value = _state.value.copy(importError = "El JSON no puede estar vacío")
                    return@launch
                }

                // Parsear JSON
                val profileData = parseProfileJson(jsonText)
                
                // Validar campos requeridos
                if (profileData.name.isEmpty()) {
                    _state.value = _state.value.copy(importError = "El campo 'name' es requerido")
                    return@launch
                }
                
                if (profileData.applicationType.isEmpty()) {
                    _state.value = _state.value.copy(importError = "El campo 'applicationType' es requerido")
                    return@launch
                }

                // Resolver conflicto de nombre si existe
                var finalName = profileData.name
                var counter = 2
                while (profileRepository.getProfileByName(finalName) != null) {
                    finalName = "${profileData.name} ($counter)"
                    counter++
                }

                // Validar que las llaves existan en el almacén
                val warnings = mutableListOf<String>()
                val availableKeyKcvs = _state.value.availableKeys.map { it.kcv }
                
                profileData.keyConfigurations.forEach { config ->
                    if (config.selectedKey.isNotEmpty() && !availableKeyKcvs.contains(config.selectedKey)) {
                        warnings.add("Llave con KCV '${config.selectedKey}' no encontrada en el almacén")
                    }
                }

                // Generar IDs únicos para las configuraciones
                val keyConfigurations = profileData.keyConfigurations.mapIndexed { index, config ->
                    config.copy(id = System.currentTimeMillis() + index)
                }

                // Crear perfil
                val profile = ProfileEntity(
                    name = finalName,
                    description = profileData.description,
                    applicationType = profileData.applicationType,
                    keyConfigurations = keyConfigurations,
                    useKEK = profileData.useKEK,
                    selectedKEKKcv = profileData.selectedKEKKcv
                )

                // Guardar perfil
                profileRepository.insertProfile(profile)

                // Cerrar modal y limpiar estado
                onDismissImportModal()

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    importError = "Error al procesar JSON: ${e.message}"
                )
            }
        }
    }

    // Data class para parsear JSON del perfil
    private data class ImportedProfileData(
        val name: String = "",
        val description: String = "",
        val applicationType: String = "",
        val useKEK: Boolean = false,
        val selectedKEKKcv: String = "",
        val keyConfigurations: List<KeyConfiguration> = emptyList()
    )

    private fun parseProfileJson(jsonText: String): ImportedProfileData {
        // Parsear JSON usando org.json (disponible en Android)
        val jsonObject = org.json.JSONObject(jsonText)
        
        val keyConfigsJson = jsonObject.optJSONArray("keyConfigurations")
        val keyConfigurations = mutableListOf<KeyConfiguration>()
        
        if (keyConfigsJson != null) {
            for (i in 0 until keyConfigsJson.length()) {
                val configJson = keyConfigsJson.getJSONObject(i)
                val config = KeyConfiguration(
                    id = System.currentTimeMillis() + i, // ID temporal, se reasignará
                    usage = configJson.optString("usage", ""),
                    keyType = configJson.optString("keyType", ""),
                    slot = configJson.optString("slot", ""),
                    selectedKey = configJson.optString("selectedKey", ""),
                    injectionMethod = configJson.optString("injectionMethod", "auto"),
                    ksn = configJson.optString("ksn", "")
                )
                keyConfigurations.add(config)
            }
        }

        return ImportedProfileData(
            name = jsonObject.optString("name", ""),
            description = jsonObject.optString("description", ""),
            applicationType = jsonObject.optString("applicationType", ""),
            useKEK = jsonObject.optBoolean("useKEK", false),
            selectedKEKKcv = jsonObject.optString("selectedKEKKcv", ""),
            keyConfigurations = keyConfigurations
        )
    }
} 