package com.vigatec.injector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vigatec.injector.ui.screens.LoginScreen
import com.vigatec.injector.ui.screens.SplashScreen
import com.vigatec.injector.util.PermissionProvider
import com.vigatec.injector.viewmodel.SplashViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.injector.ui.events.UiEvent
import com.vigatec.injector.ui.screens.MainScaffold
import com.vigatec.injector.ui.screens.ConfigScreen
import com.vigatec.injector.ui.screens.LogsScreen
import com.vigatec.injector.ui.screens.LogDetailScreen
import com.vigatec.injector.ui.screens.UserManagementScreen
import com.vigatec.injector.ui.screens.ExportImportScreen
import com.vigatec.injector.viewmodel.LoginViewModel
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import com.vigatec.injector.data.local.preferences.SessionManager

@EntryPoint
@InstallIn(ActivityComponent::class)
interface PermissionProviderEntryPoint {
    fun permissionProvider(): PermissionProvider
}

@EntryPoint
@InstallIn(ActivityComponent::class)
interface SessionManagerEntryPoint {
    fun sessionManager(): SessionManager
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = remember {
        EntryPointAccessors.fromActivity(
            context as android.app.Activity,
            SessionManagerEntryPoint::class.java
        ).sessionManager()
    }
    val permissionProvider = remember {
        EntryPointAccessors.fromActivity(
            context as android.app.Activity,
            PermissionProviderEntryPoint::class.java
        ).permissionProvider()
    }
    var currentUsername by remember { mutableStateOf("") }
    val roleFlow = remember { sessionManager.getLoggedUserRole() }
    val currentRole by roleFlow.collectAsState(initial = null)

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
                userRole = currentRole,
                permissionProvider = permissionProvider,
                onNavigateToConfig = {
                    navController.navigate(Screen.Config.route)
                },
                onNavigateToExportImport = {
                    navController.navigate(Screen.ExportImport.route)
                }
            )
        }

        composable(Screen.Config.route) {
            val context = LocalContext.current
            val permissionProvider = remember {
                EntryPointAccessors.fromActivity(
                    context as android.app.Activity,
                    PermissionProviderEntryPoint::class.java
                ).permissionProvider()
            }

            var usernameForConfig by remember { mutableStateOf(currentUsername) }

            // Obtener el username de SessionManager si currentUsername está vacío
            LaunchedEffect(currentUsername) {
                if (currentUsername.isEmpty()) {
                    sessionManager.getLoggedUsername().collect { username ->
                        usernameForConfig = username ?: ""
                    }
                } else {
                    usernameForConfig = currentUsername
                }
            }

            ConfigScreen(
                currentUsername = usernameForConfig,
                permissionProvider = permissionProvider,
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
                },
                onLogClick = { logId ->
                    navController.navigate(Screen.LogDetail.createRoute(logId))
                }
            )
        }

        composable(Screen.LogDetail.route) { backStackEntry ->
            val logId = backStackEntry.arguments?.getString("logId")?.toLongOrNull() ?: 0L
            LogDetailScreen(
                logId = logId,
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

        composable(Screen.ExportImport.route) {
            ExportImportScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
} 