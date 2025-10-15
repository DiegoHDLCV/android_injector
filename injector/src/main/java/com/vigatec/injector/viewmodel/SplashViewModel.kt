package com.vigatec.injector.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.injector.ui.navigation.Screen
import com.vigatec.injector.ui.events.UiEvent
import com.example.manufacturer.di.SDKInitManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG_VM = "InjectorSplashVM"

@HiltViewModel
class SplashViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    sealed class InitState {
        object Loading : InitState()
        object Success : InitState()
        data class Error(val message: String) : InitState()
    }

    private val _initState = MutableStateFlow<InitState>(InitState.Loading)
    val initState = _initState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    fun initializeApplication() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG_VM, "Iniciando initializeApplication (SDKInitManager)...")
            _initState.value = InitState.Loading
            try {
                SDKInitManager.initializeOnce(getApplication())
                _initState.value = InitState.Success
                _uiEvent.send(
                    UiEvent.NavigateToRoute(
                        route = Screen.Login.route,
                        popUpTo = Screen.Splash.route,
                        inclusive = true
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG_VM, "Error durante la inicialización", e)
                _initState.value = InitState.Error(e.localizedMessage ?: "Fallo al inicializar")
            }
        }
    }
}


