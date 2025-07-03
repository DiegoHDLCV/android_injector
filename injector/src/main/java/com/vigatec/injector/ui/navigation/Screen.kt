package com.vigatec.injector.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Main : Screen("main")
}

sealed class MainScreen(val route: String) {
    object Dashboard : MainScreen("dashboard")
    object KeyVault : MainScreen("key_vault")
    object Ceremony : MainScreen("ceremony")
    object Profiles : MainScreen("profiles")
} 