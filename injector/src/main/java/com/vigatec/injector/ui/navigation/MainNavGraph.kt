package com.vigatec.injector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vigatec.injector.ui.screens.DashboardScreen
import com.vigatec.injector.ui.screens.KeyVaultScreen
import com.vigatec.injector.ui.screens.CeremonyScreen
import com.vigatec.injector.ui.screens.ProfilesScreen
import com.vigatec.injector.ui.screens.LogsScreen
import com.vigatec.injector.ui.screens.UserManagementScreen
import com.vigatec.injector.ui.screens.KioskModeSettingsScreen

@Composable
fun MainNavGraph(
    navController: NavHostController,
    username: String,
    permissionProvider: com.vigatec.injector.util.PermissionProvider,
    onNavigateToExportImport: () -> Unit = {},
    onLogout: () -> Unit = {},
    onCeremonyStateChanged: (Boolean) -> Unit = {}
) {
    // Estado compartido de ceremonia
    val isCeremonyInProgress = remember { mutableStateOf(false) }
    NavHost(
        navController = navController,
        startDestination = MainScreen.Dashboard.route,
        route = Screen.Main.route,
        enterTransition = { androidx.compose.animation.EnterTransition.None },
        exitTransition = { androidx.compose.animation.ExitTransition.None },
        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
        popExitTransition = { androidx.compose.animation.ExitTransition.None }
    ) {
        composable(MainScreen.Dashboard.route) {
            // Resetear estado de ceremonia al salir
            onCeremonyStateChanged(false)
            DashboardScreen(username, navController)
        }
        composable(MainScreen.KeyVault.route) {
            // Resetear estado de ceremonia al salir
            onCeremonyStateChanged(false)
            KeyVaultScreen(
                onNavigateToExportImport = onNavigateToExportImport
            )
        }
        composable(MainScreen.Ceremony.route) {
            CeremonyScreen(
                navController = navController,
                onCeremonyStateChanged = { inProgress ->
                    isCeremonyInProgress.value = inProgress
                    onCeremonyStateChanged(inProgress)
                }
            )
        }
        composable(MainScreen.Profiles.route) {
            // Resetear estado de ceremonia al salir
            onCeremonyStateChanged(false)
            ProfilesScreen(username = username)
        }
        composable(MainScreen.Config.route) {
            // Resetear estado de ceremonia al salir
            onCeremonyStateChanged(false)
            com.vigatec.injector.ui.screens.ConfigScreen(
                currentUsername = username,
                permissionProvider = permissionProvider,
                onNavigateToLogs = {
                    navController.navigate(MainScreen.Logs.route)
                },
                onNavigateToUserManagement = {
                    navController.navigate(MainScreen.UserManagement.route)
                },
                onNavigateToKioskConfig = {
                    navController.navigate(MainScreen.KioskConfig.route)
                },
                onBack = { navController.popBackStack() },
                onLogout = onLogout
            )
        }
        
        composable(MainScreen.Logs.route) {
            // Resetear estado de ceremonia al salir
            onCeremonyStateChanged(false)
            LogsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onLogClick = { logId ->
                    // TODO: Navigate to log detail if needed within MainNavGraph
                }
            )
        }
        
        composable(MainScreen.UserManagement.route) {
            // Resetear estado de ceremonia al salir
            onCeremonyStateChanged(false)
            UserManagementScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(MainScreen.KioskConfig.route) {
            // Resetear estado de ceremonia al salir
            onCeremonyStateChanged(false)
            KioskModeSettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

    }
}
