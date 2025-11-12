package com.vigatec.injector.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Main : Screen("main")
    object Config : Screen("config")
    object Logs : Screen("logs")
    object LogDetail : Screen("log_detail/{logId}") {
        fun createRoute(logId: Long) = "log_detail/$logId"
    }
    object UserManagement : Screen("user_management")
    object ExportImport : Screen("export_import")
}

sealed class MainScreen(val route: String) {
    object Dashboard : MainScreen("dashboard")
    object KeyVault : MainScreen("key_vault")
    object Ceremony : MainScreen("ceremony")
    object Profiles : MainScreen("profiles")
}
