package com.vigatec.injector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vigatec.injector.ui.screens.LoginScreen
import com.vigatec.injector.ui.screens.SplashScreen
import com.vigatec.injector.viewmodel.SplashViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.injector.ui.events.UiEvent
import com.vigatec.injector.ui.screens.MainScaffold
import com.vigatec.injector.ui.screens.ConfigScreen
import com.vigatec.injector.ui.screens.LogsScreen
import com.vigatec.injector.ui.screens.UserManagementScreen
import com.vigatec.injector.viewmodel.LoginViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var currentUsername by remember { mutableStateOf("") }

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
            val loginViewModel: LoginViewModel = hiltViewModel()
            LoginScreen(
                loginViewModel = loginViewModel,
                onLoginSuccess = { username ->
                    currentUsername = username
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScaffold(
                username = currentUsername,
                onNavigateToConfig = {
                    navController.navigate(Screen.Config.route)
                }
            )
        }

        composable(Screen.Config.route) {
            ConfigScreen(
                currentUsername = currentUsername,
                onNavigateToLogs = {
                    navController.navigate(Screen.Logs.route)
                },
                onNavigateToUserManagement = {
                    navController.navigate(Screen.UserManagement.route)
                },
                onBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    // Limpiar el username y navegar al login
                    currentUsername = ""
                    navController.navigate(Screen.Login.route) {
                        // Limpiar toda la pila de navegación
                        popUpTo(Screen.Main.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(Screen.Logs.route) {
            LogsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.UserManagement.route) {
            UserManagementScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
} 