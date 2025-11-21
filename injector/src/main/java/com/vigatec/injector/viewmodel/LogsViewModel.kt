package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.persistence.entities.InjectionLogEntity
import com.vigatec.persistence.repository.InjectionLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class LogsUiState(
    val filteredLogs: List<InjectionLogEntity> = emptyList(),
    val availableUsernames: List<String> = emptyList(),
    val availableProfiles: List<String> = emptyList(),
    val selectedUsername: String? = null,
    val selectedProfile: String? = null,
    val selectedDateStart: Long? = null,
    val selectedDateEnd: Long? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val hasMorePages: Boolean = true,
    val currentPage: Int = 0,
    val totalCount: Int = 0
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val injectionLogRepository: InjectionLogRepository
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
    }

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        loadFilterOptions()
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                currentPage = 0,
                filteredLogs = emptyList()
            )
            loadLogsPage(pageNumber = 0, isInitialLoad = true)
        }
    }

    private suspend fun loadLogsPage(pageNumber: Int, isInitialLoad: Boolean = false) {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("LogsPerformance", "=== LOADING PAGE $pageNumber (initial=$isInitialLoad) ===")
        
        try {
            // Run both queries in parallel for better performance
            coroutineScope {
                val logsDeferred = async {
                    injectionLogRepository.getLogsWithFiltersPaged(
                        username = _uiState.value.selectedUsername,
                        profileName = _uiState.value.selectedProfile,
                        startTimestamp = _uiState.value.selectedDateStart,
                        endTimestamp = _uiState.value.selectedDateEnd,
                        pageSize = PAGE_SIZE,
                        pageNumber = pageNumber
                    )
                }

                val countDeferred = async {
                    injectionLogRepository.getLogsCountWithFilters(
                        username = _uiState.value.selectedUsername,
                        profileName = _uiState.value.selectedProfile,
                        startTimestamp = _uiState.value.selectedDateStart,
                        endTimestamp = _uiState.value.selectedDateEnd
                    )
                }

                // Await both results
                val logs = logsDeferred.await()
                val queryTime = System.currentTimeMillis()
                android.util.Log.d("LogsPerformance", "Data query completed in ${queryTime - startTime}ms, got ${logs.size} logs")

                val totalCount = countDeferred.await()
                val countTime = System.currentTimeMillis()
                android.util.Log.d("LogsPerformance", "Both queries completed in ${countTime - startTime}ms")

                val currentLogs = if (isInitialLoad) emptyList() else _uiState.value.filteredLogs
                val updatedLogs = currentLogs + logs

                _uiState.value = _uiState.value.copy(
                    filteredLogs = updatedLogs,
                    isLoading = false,
                    isLoadingMore = false,
                    hasMorePages = updatedLogs.size < totalCount,
                    currentPage = pageNumber,
                    totalCount = totalCount
                )
                
                val totalTime = System.currentTimeMillis() - startTime
                android.util.Log.d("LogsPerformance", "Page $pageNumber loaded in ${totalTime}ms. Total logs: ${updatedLogs.size}/$totalCount")
            }
        } catch (e: Exception) {
            android.util.Log.e("LogsPerformance", "Error loading page $pageNumber: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isLoadingMore = false,
                errorMessage = "Error al cargar los logs: ${e.message}"
            )
        }
    }

    fun loadMoreLogs() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMorePages) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            loadLogsPage(pageNumber = _uiState.value.currentPage + 1)
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
            selectedDateEnd = dateEnd
        )
        loadFirstPage()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedUsername = null,
            selectedProfile = null,
            selectedDateStart = null,
            selectedDateEnd = null
        )
        loadFirstPage()
    }

    fun deleteLog(log: InjectionLogEntity) {
        viewModelScope.launch {
            try {
                injectionLogRepository.deleteLog(log)
                // Reload current page to reflect deletion
                loadFirstPage()
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
                loadFirstPage()
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
