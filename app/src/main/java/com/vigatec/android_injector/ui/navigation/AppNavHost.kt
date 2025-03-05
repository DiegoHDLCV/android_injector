package com.vigatec.android_injector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vigatec.android_injector.ui.screens.LoginScreen
import com.vigatec.android_injector.ui.screens.SplashScreen
import com.vigatec.android_injector.viewmodel.MainViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel  // Se recibe el MainViewModel compartido
) {
    NavHost(navController = navController, startDestination = Routes.SplashScreen.route) {
        composable(Routes.SplashScreen.route) {
            // Se pasa el mainViewModel a SplashScreen
            SplashScreen(navController = navController, mainViewModel = mainViewModel)
        }
        composable(Routes.LoginScreen.route) {
            LoginScreen(navController = navController)
        }
    }
}
