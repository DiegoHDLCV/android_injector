package com.vigatec.injector.ui.navigation

sealed class Screen(val route: String) {
    object LoginScreen : Screen("login_screen")
    object MainScreen : Screen("main_screen/{username}") {
        fun createRoute(username: String) = "main_screen/$username"
    }
} 