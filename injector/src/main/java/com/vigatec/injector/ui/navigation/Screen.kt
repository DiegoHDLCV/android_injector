package com.vigatec.injector.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Main : Screen("main")
    object Config : Screen("config")
    object Logs : Screen("logs")
    object UserManagement : Screen("user_management")
    object TmsConfig : Screen("tms_config")
}

sealed class MainScreen(val route: String) {
    object Dashboard : MainScreen("dashboard")
    object KeyVault : MainScreen("key_vault")
    object Ceremony : MainScreen("ceremony")
    object Profiles : MainScreen("profiles")
    object RawDataListener : MainScreen("raw_data_listener")
}
