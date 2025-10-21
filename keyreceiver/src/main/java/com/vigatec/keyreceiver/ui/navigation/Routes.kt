package com.vigatec.keyreceiver.ui.navigation

sealed class Routes(val route: String) {
    object SplashScreen : Routes("splash_screen")
    object LoginScreen : Routes("login_screen")
    object PrintingScreen : Routes("printing_screen")
    object MasterKeyEntryScreen : Routes("master_key_entry_screen")
    object MainScreen : Routes("main_screen")
    object InjectedKeysScreen : Routes("injected_keys_screen")
    object ConfigScreen : Routes("config_screen")
    object LogsScreen : Routes("logs_screen")
    object UserManagementScreen : Routes("user_management_screen")
}