package com.vigatec.android_injector.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.android_injector.ui.events.UiEvent
import com.vigatec.android_injector.ui.navigation.Routes
// SOLO necesitas importar SDKInitManager (y PedException para el catch)
import com.example.manufacturer.base.controllers.ped.PedException // Ajusta la ruta
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
            Log.d(TAG_VM, "Iniciando initializeApplication (dependiendo de SDKInitManager)...")
            _initState.value = InitState.Loading
            try {
                // --- ÚNICO PUNTO DE INICIALIZACIÓN DE SDKs ---
                Log.i(TAG_VM, "Llamando a SDKInitManager.initializeOnce()...")
                SDKInitManager.initializeOnce(getApplication()) // Llamada única aquí
                Log.i(TAG_VM, "SDKInitManager.initializeOnce() retornó.")
                // Ya no se llaman a los otros managers directamente desde aquí


                // --- Marcar como Exitoso ---
                // Si SDKInitManager.initializeOnce termina sin lanzar excepción, asumimos éxito.
                Log.i(TAG_VM, "Inicialización GENERAL (via SDKInitManager) completada exitosamente. Estado -> Success.")
                _initState.value = InitState.Success

                // --- Navegar ---
                val nextRoute = Routes.MasterKeyEntryScreen.route
                Log.d(TAG_VM, "Enviando UiEvent para navegar a: $nextRoute")
                _uiEvent.send(UiEvent.NavigateToRoute(
                    route = nextRoute,
                    popUpTo = Routes.SplashScreen.route,
                    inclusive = true
                ))

            } catch (e: PedException) { // Captura específica de PED si SDKInitManager la relanza
                Log.e(TAG_VM, "Error de PED CRÍTICO durante la inicialización (capturado de SDKInitManager)", e)
                val errorMessage = "Error PED: ${e.localizedMessage ?: "Fallo al inicializar PED"}"
                _initState.value = InitState.Error(errorMessage)
            } catch (e: Exception) { // Captura genérica de cualquier error relanzado por SDKInitManager
                Log.e(TAG_VM, "Error CRÍTICO durante la inicialización general (capturado de SDKInitManager)", e)
                val errorMessage = "Error inicializando: ${e.javaClass.simpleName} - ${e.localizedMessage ?: "Detalle no disponible"}"
                _initState.value = InitState.Error(errorMessage)
            }
        }
    }
}