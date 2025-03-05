package com.vigatec.android_injector.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.android_injector.ui.events.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    fun navigateToSplashScreen() {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.NavigateToSplashScreen)
        }
    }

    fun navigateToLogin() {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.NavigateToLogin)
        }
    }

    fun showError(message: String) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowError(message))
        }
    }
}