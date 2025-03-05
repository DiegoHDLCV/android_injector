package com.vigatec.android_injector.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.vigatec.android_injector.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavHostController,
    // Se recibe el MainViewModel compartido
    mainViewModel: MainViewModel
) {
    // Simula una carga de 5 segundos y luego navega a Login
    LaunchedEffect(key1 = Unit) {
        delay(5000L) // 5000 ms = 5 segundos
        mainViewModel.navigateToLogin()
    }

    // UI básica del splash
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Splash Screen",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
