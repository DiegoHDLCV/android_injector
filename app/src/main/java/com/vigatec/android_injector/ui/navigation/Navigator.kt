package com.vigatec.android_injector.ui

import android.util.Log
import androidx.navigation.NavController
import com.vigatec.android_injector.ui.events.UiEvent
import com.vigatec.android_injector.ui.navigation.Routes

object Navigator {
    private const val TAG = "Navigator" // Tag para logs

    fun navigate(navController: NavController, event: UiEvent) {
        Log.d(TAG, "Navegando con evento: $event") // Log general existente
        when (event) {
            is UiEvent.NavigateToRoute -> {
                Log.d(TAG, "Caso: NavigateToRoute. Ruta: '${event.route}', PopUpTo: '${event.popUpTo}', Inclusive: ${event.inclusive}, SingleTop: ${event.launchSingleTop}")
                try {
                    navController.navigate(event.route) {
                        event.popUpTo?.let { popUp ->
                            Log.d(TAG, "Aplicando popUpTo: '$popUp', inclusive=${event.inclusive}")
                            popUpTo(popUp) { inclusive = event.inclusive }
                        }
                        launchSingleTop = event.launchSingleTop
                    }
                    Log.d(TAG, "Navegación a NavigateToRoute '${event.route}' ejecutada.")
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Error al navegar (NavigateToRoute) a la ruta '${event.route}'. ¿Está definida en el NavHost? Verifique el NavGraph.", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción general al navegar (NavigateToRoute) a la ruta '${event.route}'.", e)
                }
            }

            UiEvent.NavigateBack -> {
                Log.d(TAG, "Caso: NavigateBack. Intentando popBackStack.")
                if (!navController.popBackStack()) {
                    Log.w(TAG, "popBackStack() falló. No hay pantalla anterior en el stack o NavController no está en un estado válido para pop.")
                } else {
                    Log.d(TAG, "popBackStack() ejecutado exitosamente.")
                }
            }


            UiEvent.NavigateToMainScreen -> {

                try {
                } catch (e: IllegalArgumentException) {

                } catch (e: Exception) {
                }
            }

            is UiEvent.NavigateToPrintingScreen -> { // 'is' porque es data class (o object)
                val route = Routes.PrintingScreen.route
                Log.d(TAG, "Caso: NavigateToPrintingScreen. Navegando a ruta: '$route'")
                try {
                    navController.navigate(route)
                    Log.d(TAG, "Navegación a PrintingScreen (ruta: '$route') ejecutada.")
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Error al navegar a PrintingScreen (ruta: '$route'). ¿Ruta definida en NavGraph?", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción general al navegar a PrintingScreen (ruta: '$route').", e)
                }
            }
            is UiEvent.NavigateToMainScreen -> {
                val route = Routes.MainScreen.route
                Log.d(TAG, "Caso: NavigateToMainScreen. Navegando a ruta: '$route'")
                try {
                    navController.navigate(route)
                    Log.d(TAG, "Navegación a MainScreen (ruta: '$route') ejecutada.")
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Error al navegar a MainScreen (ruta: '$route'). ¿Ruta definida en NavGraph?", e)
                }
                catch (e: Exception) {
                    Log.e(TAG, "Excepción general al navegar a MainScreen (ruta: '$route').", e)
                }
            }



            is UiEvent.ShowError -> {
                Log.e(TAG, "Caso: ShowError. Mensaje: ${event.message}, Error: ${event.error?.message ?: "N/A"}")
                // Aquí podrías, por ejemplo, mostrar un Snackbar o un diálogo de error global si no se maneja en la pantalla específica.
                // navController.navigate(Routes.GlobalErrorScreen.createRoute(event.message)) // Ejemplo
            }
            // Considera si necesitas un 'else' para eventos no manejados.
            // else -> {
            //     Log.w(TAG, "Evento de UI no reconocido o sin acción de navegación explícita: $event")
            // }
        }
    }
}