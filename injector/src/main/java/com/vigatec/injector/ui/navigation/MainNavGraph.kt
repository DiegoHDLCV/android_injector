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
import com.vigatec.injector.ui.screens.RawDataListenerScreen

@Composable
fun MainNavGraph(
    navController: NavHostController,
    username: String,
    onNavigateToExportImport: () -> Unit = {},
    onCeremonyStateChanged: (Boolean) -> Unit = {}
) {
    // Estado compartido de ceremonia
    val isCeremonyInProgress = remember { mutableStateOf(false) }
    NavHost(
        navController = navController,
        startDestination = MainScreen.Dashboard.route,
        route = Screen.Main.route
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
        composable(MainScreen.RawDataListener.route) {
            // Resetear estado de ceremonia al salir
            onCeremonyStateChanged(false)
            RawDataListenerScreen()
        }
    }
}
