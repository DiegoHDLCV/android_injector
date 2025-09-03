package com.vigatec.dev_injector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.manufacturer.di.SDKInitManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        initializeSDKs()
    }

    private fun initializeSDKs() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                SDKInitManager.initializeOnce(getApplication())
                _isInitialized.value = true
                
            } catch (e: Exception) {
                _errorMessage.value = "Error al inicializar SDKs: ${e.message}"
                _isInitialized.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retryInitialization() {
        initializeSDKs()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
