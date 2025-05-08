package com.vigatec.android_injector.ui.events

sealed class UiEvent {

    // Evento para navegar a una ruta específica.
    data class NavigateToRoute(
        val route: String, // La ruta de destino (e.g., "technical_password_screen")
        val popUpTo: String? = null, // Opcional: Ruta hasta la cual eliminar pantallas del backstack al navegar.
        val inclusive: Boolean = false, // Opcional: Si es true, también elimina la ruta especificada en popUpTo.
        val launchSingleTop: Boolean = false // Opcional: Si es true, evita crear múltiples instancias de la misma pantalla en el top del stack.
    ) : UiEvent() // Hereda de UiEvent

    // Evento para indicar que se debe navegar hacia atrás en la pila de navegación.
    // Es un 'object' porque no necesita parámetros adicionales, la acción es siempre la misma.
    object NavigateBack : UiEvent() // Hereda de UiEvent

    // --- Eventos Añadidos ---

    // Evento para navegar a la pantalla principal (determinada por SystemConfig). No necesita datos adicionales.
    object NavigateToMainScreen : UiEvent()

    // Evento para navegar a la pantalla de impresión. No necesita datos adicionales.
    object NavigateToPrintingScreen : UiEvent()

    // Evento para mostrar un error. Podría llevar datos como el mensaje o la causa.
    // Haciéndolo data class para flexibilidad, aunque en tu Navigator actual no se usan parámetros.
    data class ShowError(val message: String? = null, val error: Throwable? = null) : UiEvent()
}
