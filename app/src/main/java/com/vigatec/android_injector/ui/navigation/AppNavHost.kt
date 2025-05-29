package com.vigatec.android_injector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vigatec.android_injector.ui.screens.LoginScreen
import com.vigatec.android_injector.ui.screens.MainScreen
import com.vigatec.android_injector.ui.screens.MasterKeyEntryScreen
import com.vigatec.android_injector.ui.screens.SplashScreen
import com.vigatec.android_injector.viewmodel.MainViewModel
import com.vigatec.android_injector.viewmodel.MasterKeyEntryViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,

) {
    NavHost(navController = navController, startDestination = Routes.SplashScreen.route) {
        composable(Routes.SplashScreen.route) {
            SplashScreen(navController = navController)
        }
        composable(Routes.LoginScreen.route) {
            LoginScreen(navController = navController)
        }
        // --- Add Composable for MasterKeyEntryScreen ---
        composable(Routes.MasterKeyEntryScreen.route) {
            // Obtain instance of the specific ViewModel for this screen
            val masterKeyViewModel: MasterKeyEntryViewModel = viewModel()
            MasterKeyEntryScreen(
                navController = navController,
                viewModel = masterKeyViewModel
            )
        }
        // --- Add Composable for MainScreen ---
        composable(Routes.MainScreen.route) {
            val mainViewModel: MainViewModel = viewModel()
            MainScreen(navController = navController)
        }
        // Add other composables (like PrintingScreen) if they exist
        // composable(Routes.PrintingScreen.route) { ... }
    }
}
