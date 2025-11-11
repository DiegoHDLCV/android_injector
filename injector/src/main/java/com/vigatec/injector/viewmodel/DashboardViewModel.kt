package com.vigatec.injector.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.communication.libraries.CommunicationSDKManager
import com.vigatec.communication.polling.PollingService
import com.vigatec.communication.polling.CommLog
import com.vigatec.persistence.repository.InjectedKeyRepository
import com.vigatec.persistence.repository.ProfileRepository
import com.vigatec.injector.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class SystemStats(
    val profilesCount: Int = 0,
    val keysCount: Int = 0,
    val injectionsToday: Int = 0,
    val usersCount: Int = 0
)

data class DashboardState(
    val stats: SystemStats = SystemStats(),
    val isLoading: Boolean = true,
    val isSubPosConnected: Boolean = false,
    val isPollingActive: Boolean = false,
    val commLogs: List<com.vigatec.communication.polling.CommLogEntry> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val injectedKeyRepository: InjectedKeyRepository,
    private val userRepository: UserRepository,
    application: Application
) : AndroidViewModel(application) {

    private val TAG = "DashboardViewModel"
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state
    
    // Servicio de polling
    private val pollingService = PollingService()
    
    // Estado de conexión del SubPOS
    private val _isSubPosConnected = MutableStateFlow(false)
    val isSubPosConnected: StateFlow<Boolean> = _isSubPosConnected

    init {
        loadStats()
        // La inicialización de SDKs se centraliza en SplashViewModel mediante SDKInitManager
        initializePolling()
        observePollingStatus()
    }

    private fun loadStats() {
        viewModelScope.launch {
            // Obtener el inicio del día actual
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis

            // Combinar todos los flujos de datos
            combine(
                profileRepository.getAllProfiles(),
                injectedKeyRepository.getAllInjectedKeys()
            ) { profiles, injectedKeys ->
                // Calcular estadísticas en tiempo real
                val profilesCount = profiles.size
                val keysCount = injectedKeys.count { it.status == "SUCCESSFUL" }
                val injectionsToday = injectedKeys.count { it.injectionTimestamp >= startOfDay }
                val usersCount = userRepository.getUserCount()

                val stats = SystemStats(
                    profilesCount = profilesCount,
                    keysCount = keysCount,
                    injectionsToday = injectionsToday,
                    usersCount = usersCount
                )

                DashboardState(
                    stats = stats,
                    isLoading = false
                )
            }.collect { dashboardState ->
                _state.value = dashboardState.copy(
                    isSubPosConnected = _isSubPosConnected.value,
                    isPollingActive = pollingService.isPollingActive.value
                )
            }
        }
    }
    
    private fun initializePolling() {
        viewModelScope.launch {
            try {
                // Inicializar el servicio de polling
                pollingService.initialize()
                
                Log.d(TAG, "Polling inicializado correctamente")
            } catch (e: Exception) {
                Log.e(TAG, "Error al inicializar polling", e)
            }
        }
    }
    
    private fun observePollingStatus() {
        viewModelScope.launch {
            // Observar el estado de conexión del polling
            pollingService.isConnected.collect { isConnected ->
                Log.d(TAG, "Estado de conexión SubPOS: $isConnected")
                _isSubPosConnected.value = isConnected
                
                // Actualizar el estado del dashboard
                _state.value = _state.value.copy(
                    isSubPosConnected = isConnected
                )
            }
        }
        
        viewModelScope.launch {
            // Observar el estado del polling
            pollingService.isPollingActive.collect { isActive ->
                Log.d(TAG, "Estado del polling: $isActive")
                _state.value = _state.value.copy(
                    isPollingActive = isActive
                )
            }
        }

        viewModelScope.launch {
            CommLog.entries.collect { logs ->
                _state.value = _state.value.copy(commLogs = logs)
            }
        }
    }
    
    fun startPolling() {
        Log.d(TAG, "Iniciando polling desde MasterPOS...")
        pollingService.startMasterPolling { isConnected ->
            Log.d(TAG, "Callback de conexión: $isConnected")
            _isSubPosConnected.value = isConnected
        }
    }
    
    fun stopPolling() {
        Log.d(TAG, "Deteniendo polling...")
        pollingService.stopPolling()
    }
    
    fun isPollingReady(): Boolean {
        return pollingService.isReadyForMessaging()
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel limpiado, deteniendo polling...")
        pollingService.stopPolling()
    }
} 