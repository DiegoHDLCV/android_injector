package com.vigatec.dev_injector.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.manufacturer.di.SDKInitManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SimpleSplashViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    sealed class InitState {
        object Loading : InitState()
        object Success : InitState()
        data class Error(val message: String) : InitState()
    }

    private val _initState = MutableStateFlow<InitState>(InitState.Loading)
    val initState = _initState.asStateFlow()

    fun initializeApplication() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("DevInjectorSplash", "Iniciando SDK...")
            _initState.value = InitState.Loading
            
            try {
                // Inicializar SDK usando el manager común
                SDKInitManager.initializeOnce(getApplication())
                
                Log.d("DevInjectorSplash", "SDK inicializado exitosamente")
                _initState.value = InitState.Success
                
            } catch (e: Exception) {
                Log.e("DevInjectorSplash", "Error al inicializar SDK", e)
                _initState.value = InitState.Error(e.localizedMessage ?: "Error desconocido")
            }
        }
    }
}