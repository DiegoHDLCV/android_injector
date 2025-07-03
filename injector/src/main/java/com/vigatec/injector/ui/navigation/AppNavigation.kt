package com.vigatec.injector.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vigatec.injector.ui.screens.LoginScreen
import com.vigatec.injector.ui.screens.MainScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.LoginScreen.route
    ) {
        composable(Screen.LoginScreen.route) {
            LoginScreen(onLoginSuccess = { username ->
                navController.navigate(Screen.MainScreen.createRoute(username)) {
                    popUpTo(Screen.LoginScreen.route) {
                        inclusive = true
                    }
                }
            })
        }
        composable(
            route = Screen.MainScreen.route,
            arguments = listOf(navArgument("username") { type = NavType.StringType })
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: "User"
            MainScreen(username = username)
        }
    }
} 