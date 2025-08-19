package com.vigatec.injector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vigatec.injector.ui.screens.LoginScreen
import com.vigatec.injector.ui.screens.SplashScreen
import com.vigatec.injector.viewmodel.SplashViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.injector.ui.events.UiEvent
import com.vigatec.injector.ui.screens.MainScaffold

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            val vm: SplashViewModel = hiltViewModel()
            SplashScreen(vm)

            LaunchedEffect(Unit) {
                vm.uiEvent.collect { event ->
                    when (event) {
                        is UiEvent.NavigateToRoute -> {
                            navController.navigate(event.route) {
                                event.popUpTo?.let { pop ->
                                    popUpTo(pop) { inclusive = event.inclusive }
                                }
                            }
                        }
                    }
                }
            }
        }
        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = { username ->
                navController.navigate(Screen.Main.route) {
                    popUpTo(Screen.Login.route) {
                        inclusive = true
                    }
                }
            })
        }
        composable(Screen.Main.route) {
            // Aquí recuperamos el username, aunque en este nuevo diseño
            // podríamos optar por obtenerlo de un ViewModel compartido o de una sesión.
            // Por ahora, lo pasamos como argumento, pero no lo usamos en MainScaffold.
            // Lo ideal sería obtenerlo de una fuente de datos única (ej. SessionViewModel).
            MainScaffold(username = "admin") // Pasamos un valor por ahora.
        }
    }
} 