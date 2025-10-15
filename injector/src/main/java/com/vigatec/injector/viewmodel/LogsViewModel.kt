package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.entities.InjectionLogEntity
import com.example.persistence.repository.InjectionLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class LogsUiState(
    val logs: List<InjectionLogEntity> = emptyList(),
    val filteredLogs: List<InjectionLogEntity> = emptyList(),
    val availableUsernames: List<String> = emptyList(),
    val availableProfiles: List<String> = emptyList(),
    val selectedUsername: String? = null,
    val selectedProfile: String? = null,
    val selectedDateStart: Long? = null,
    val selectedDateEnd: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val injectionLogRepository: InjectionLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        loadLogs()
        loadFilterOptions()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                injectionLogRepository.getAllLogs().collect { logs ->
                    _uiState.value = _uiState.value.copy(
                        logs = logs,
                        filteredLogs = applyFilters(logs),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar los logs: ${e.message}"
                )
            }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            try {
                val usernames = injectionLogRepository.getDistinctUsernames()
                val profiles = injectionLogRepository.getDistinctProfiles()
                _uiState.value = _uiState.value.copy(
                    availableUsernames = usernames,
                    availableProfiles = profiles
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al cargar opciones de filtro: ${e.message}"
                )
            }
        }
    }

    fun applyFilters(
        username: String? = _uiState.value.selectedUsername,
        profile: String? = _uiState.value.selectedProfile,
        dateStart: Long? = _uiState.value.selectedDateStart,
        dateEnd: Long? = _uiState.value.selectedDateEnd
    ) {
        _uiState.value = _uiState.value.copy(
            selectedUsername = username,
            selectedProfile = profile,
            selectedDateStart = dateStart,
            selectedDateEnd = dateEnd,
            filteredLogs = applyFilters(_uiState.value.logs)
        )
    }

    private fun applyFilters(logs: List<InjectionLogEntity>): List<InjectionLogEntity> {
        var filtered = logs

        _uiState.value.selectedUsername?.let { username ->
            if (username.isNotEmpty()) {
                filtered = filtered.filter { it.username == username }
            }
        }

        _uiState.value.selectedProfile?.let { profile ->
            if (profile.isNotEmpty()) {
                filtered = filtered.filter { it.profileName == profile }
            }
        }

        _uiState.value.selectedDateStart?.let { start ->
            filtered = filtered.filter { it.timestamp >= start }
        }

        _uiState.value.selectedDateEnd?.let { end ->
            filtered = filtered.filter { it.timestamp <= end }
        }

        return filtered
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedUsername = null,
            selectedProfile = null,
            selectedDateStart = null,
            selectedDateEnd = null,
            filteredLogs = _uiState.value.logs
        )
    }

    fun deleteLog(log: InjectionLogEntity) {
        viewModelScope.launch {
            try {
                injectionLogRepository.deleteLog(log)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al eliminar log: ${e.message}"
                )
            }
        }
    }

    fun deleteAllLogs() {
        viewModelScope.launch {
            try {
                injectionLogRepository.deleteAllLogs()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al eliminar todos los logs: ${e.message}"
                )
            }
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
