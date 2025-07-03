package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SystemStats(
    val profilesCount: Int = 0,
    val keysCount: Int = 0,
    val injectionsToday: Int = 0,
    val usersCount: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor() : ViewModel() {

    private val _systemStats = MutableStateFlow(SystemStats())
    val systemStats: StateFlow<SystemStats> = _systemStats

    init {
        loadStats()
    }

    private fun loadStats() {
        // Simular la carga de estadísticas
        _systemStats.value = SystemStats(
            profilesCount = 8,
            keysCount = 42,
            injectionsToday = 15,
            usersCount = 3
        )
    }
} 