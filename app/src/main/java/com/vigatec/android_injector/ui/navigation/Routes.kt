package com.vigatec.android_injector.ui.navigation

sealed class Routes(val route: String) {
    object SplashScreen : Routes("splash_screen")
    object LoginScreen : Routes("login_screen")
    object PrintingScreen : Routes("printing_screen")
    object MasterKeyEntryScreen : Routes("master_key_entry_screen")
    object MainScreen : Routes("main_screen")


}