package com.vigatec.android_injector.ui.events

sealed class UiEvent {
    object NavigateToLogin : UiEvent()
    object NavigateToSplashScreen : UiEvent()
    class ShowError(val message: String) : UiEvent()
}