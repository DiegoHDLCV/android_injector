package com.vigatec.injector.ui.events

sealed class UiEvent {
    data class NavigateToRoute(
        val route: String,
        val popUpTo: String? = null,
        val inclusive: Boolean = false
    ) : UiEvent()
}


