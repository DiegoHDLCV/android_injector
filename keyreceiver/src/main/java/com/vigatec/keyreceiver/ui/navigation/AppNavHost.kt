package com.vigatec.keyreceiver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vigatec.keyreceiver.ui.screens.CryptoTestScreen
import com.vigatec.keyreceiver.ui.screens.InjectedKeysScreen
import com.vigatec.keyreceiver.ui.screens.KeyVerificationScreen
import com.vigatec.keyreceiver.ui.screens.LoginScreen
import com.vigatec.keyreceiver.ui.screens.MainScreen
import com.vigatec.keyreceiver.ui.screens.MasterKeyEntryScreen
import com.vigatec.keyreceiver.ui.screens.SplashScreen
import com.vigatec.keyreceiver.viewmodel.MainViewModel
import com.vigatec.keyreceiver.viewmodel.MasterKeyEntryViewModel

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
            MainScreen(navController = navController)
        }
        composable(Routes.InjectedKeysScreen.route) {
            // InjectedKeysScreen obtiene su propio ViewModel con hiltViewModel()
            InjectedKeysScreen(navController = navController)
        }
        composable(Routes.KeyVerificationScreen.route) {
            // KeyVerificationScreen obtiene su propio ViewModel con hiltViewModel()
            KeyVerificationScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = hiltViewModel()
            )
        }
        composable(Routes.CryptoTestScreen.route) {
            CryptoTestScreen(navController = navController)
        }

        // Add other composables (like PrintingScreen) if they exist
        // composable(Routes.PrintingScreen.route) { ... }
    }
}
