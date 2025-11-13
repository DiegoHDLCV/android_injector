package com.vigatec.injector.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.communication.polling.PollingService
import com.vigatec.communication.polling.CommLog
import com.vigatec.persistence.repository.InjectedKeyRepository
import com.vigatec.persistence.repository.ProfileRepository
import com.vigatec.persistence.repository.InjectionLogRepository
import com.vigatec.injector.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
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
    private val injectionLogRepository: InjectionLogRepository,
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
    @Suppress("UNUSED")
    val isSubPosConnected: StateFlow<Boolean> = _isSubPosConnected

    init {
        loadStats()
        // La inicialización de SDKs se centraliza en SplashViewModel mediante SDKInitManager
        initializePolling()
        observePollingStatus()
    }

    private fun loadStats() {
        viewModelScope.launch {
            // Obtener el inicio del día actual con zona horaria explícita
            val timeZone = TimeZone.getDefault()
            val calendar = Calendar.getInstance(timeZone)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis

            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
            dateFormat.timeZone = timeZone

            Log.d(TAG, "=== CARGANDO ESTADÍSTICAS DEL DASHBOARD ===")
            Log.d(TAG, "Zona horaria: ${timeZone.id} (${timeZone.displayName})")
            Log.d(TAG, "Inicio del día: $startOfDay (${dateFormat.format(java.util.Date(startOfDay))})")
            Log.d(TAG, "Timestamp actual: ${System.currentTimeMillis()} (${dateFormat.format(java.util.Date(System.currentTimeMillis()))})")

            // Obtener Flow de logs exitosos de hoy y mapearlo para contar inyecciones individuales
            val successfulLogsToday = injectionLogRepository.getSuccessfulLogsSince(startOfDay)
                .map { logs ->
                    Log.d(TAG, "🔄 Flow de logs emitió: ${logs.size} logs")
                    logs.forEach { log ->
                        val logDate = dateFormat.format(java.util.Date(log.timestamp))
                        Log.d(TAG, "  - Log ID: ${log.id}, Perfil: ${log.profileName}, Timestamp: ${log.timestamp} ($logDate), Estado: ${log.operationStatus}")
                        // Verificar que el timestamp esté dentro del rango del día actual
                        if (log.timestamp < startOfDay) {
                            Log.w(TAG, "  ⚠️ Log con timestamp anterior al inicio del día: ${log.timestamp} < $startOfDay")
                        }
                    }
                    // Contar inyecciones individuales (no perfiles únicos)
                    val count = logs.size
                    val uniqueProfiles = logs.map { it.profileName }.distinct()
                    Log.d(TAG, "📊 Logs exitosos de hoy: ${logs.size}, Inyecciones individuales: $count")
                    Log.d(TAG, "📋 Perfiles únicos inyectados hoy: ${uniqueProfiles.size} (${uniqueProfiles.joinToString(", ")})")
                    count
                }
                .distinctUntilChanged() // Solo emitir cuando el conteo cambia realmente

            // Combinar todos los flujos de datos
            combine(
                profileRepository.getAllProfiles(),
                injectedKeyRepository.getAllInjectedKeys(),
                successfulLogsToday
            ) { profiles, injectedKeys, injectionsToday ->
                Log.d(TAG, "🔄 Combine ejecutado - Nuevos valores recibidos")
                
                // Calcular estadísticas en tiempo real
                val profilesCount = profiles.size
                val keysCount = injectedKeys.size // Contar todas las llaves almacenadas, sin filtrar por estado
                val usersCount = userRepository.getUserCount()

                Log.d(TAG, "=== ESTADÍSTICAS CALCULADAS ===")
                Log.d(TAG, "  - Perfiles: $profilesCount")
                Log.d(TAG, "  - Llaves: $keysCount")
                Log.d(TAG, "  - Inyecciones hoy: $injectionsToday")
                Log.d(TAG, "  - Usuarios: $usersCount")

                val stats = SystemStats(
                    profilesCount = profilesCount,
                    keysCount = keysCount,
                    injectionsToday = injectionsToday,
                    usersCount = usersCount
                )

                val newState = DashboardState(
                    stats = stats,
                    isLoading = false
                )
                
                Log.d(TAG, "📦 Nuevo DashboardState creado con stats.injectionsToday = ${newState.stats.injectionsToday}")
                
                newState
            }.collect { dashboardState ->
                val previousInjectionsToday = _state.value.stats.injectionsToday
                val newInjectionsToday = dashboardState.stats.injectionsToday
                
                Log.d(TAG, "📥 Collect ejecutado - Actualizando estado")
                Log.d(TAG, "  - Inyecciones anteriores: $previousInjectionsToday")
                Log.d(TAG, "  - Inyecciones nuevas: $newInjectionsToday")
                
                // Preservar el stats correctamente al actualizar el estado
                _state.value = dashboardState.copy(
                    isSubPosConnected = _isSubPosConnected.value,
                    isPollingActive = pollingService.isPollingActive.value
                )
                
                Log.d(TAG, "✅ Estado actualizado - stats.injectionsToday = ${_state.value.stats.injectionsToday}")
                
                // Verificar que el stats se preservó correctamente
                if (_state.value.stats.injectionsToday != newInjectionsToday) {
                    Log.e(TAG, "❌ ERROR: El stats no se preservó correctamente después del copy()")
                    Log.e(TAG, "  - Esperado: $newInjectionsToday")
                    Log.e(TAG, "  - Actual: ${_state.value.stats.injectionsToday}")
                }
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
    
    @Suppress("UNUSED")
    fun isPollingReady(): Boolean {
        return pollingService.isReadyForMessaging()
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel limpiado, deteniendo polling...")
        pollingService.stopPolling()
    }
} 