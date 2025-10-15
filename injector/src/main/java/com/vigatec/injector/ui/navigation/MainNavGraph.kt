package com.vigatec.injector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vigatec.injector.ui.screens.DashboardScreen
import com.vigatec.injector.ui.screens.KeyVaultScreen
import com.vigatec.injector.ui.screens.CeremonyScreen
import com.vigatec.injector.ui.screens.ProfilesScreen
import com.vigatec.injector.ui.screens.RawDataListenerScreen

@Composable
fun MainNavGraph(navController: NavHostController, username: String) {
    NavHost(
        navController = navController,
        startDestination = MainScreen.Dashboard.route,
        route = Screen.Main.route
    ) {
        composable(MainScreen.Dashboard.route) {
            DashboardScreen(username, navController)
        }
        composable(MainScreen.KeyVault.route) {
            KeyVaultScreen()
        }
        composable(MainScreen.Ceremony.route) {
            CeremonyScreen()
        }
        composable(MainScreen.Profiles.route) {
            ProfilesScreen(username = username)
        }
        composable(MainScreen.RawDataListener.route) {
            RawDataListenerScreen()
        }
    }
}
