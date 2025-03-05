package com.vigatec.android_injector.ui.navigation

sealed class Routes(val route: String) {
    object SplashScreen : Routes("splash_screen")
    object LoginScreen : Routes("login_screen")
}