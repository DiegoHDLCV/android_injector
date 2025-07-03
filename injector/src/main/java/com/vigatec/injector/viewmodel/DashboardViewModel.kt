package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.repository.InjectedKeyRepository
import com.example.persistence.repository.ProfileRepository
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
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val injectedKeyRepository: InjectedKeyRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init {
        loadStats()
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
                _state.value = dashboardState
            }
        }
    }
} 