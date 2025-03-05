package com.vigatec.android_injector

import android.util.Log
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.vigatec.android_injector.ui.navigation.AppNavHost
import com.vigatec.android_injector.ui.navigation.Routes
import com.vigatec.android_injector.ui.components.CustomDialog
import com.vigatec.android_injector.ui.events.UiEvent
import com.vigatec.android_injector.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MyApp(mainViewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()

    // Variables para controlar el diálogo de error
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Se recogen los eventos globales del MainViewModel
    LaunchedEffect(Unit) {
        mainViewModel.uiEvents.collectLatest { event ->
            Log.d("MyApp", "Evento recibido: $event")
            when (event) {
                is UiEvent.NavigateToSplashScreen -> {
                    navController.navigate(Routes.SplashScreen.route) {
                        popUpTo(Routes.SplashScreen.route) { inclusive = true }
                    }
                }
                is UiEvent.NavigateToLogin -> {
                    navController.navigate(Routes.LoginScreen.route) {
                        popUpTo(Routes.SplashScreen.route) { inclusive = true }
                    }
                }
                is UiEvent.ShowError -> {
                    showErrorDialog = true
                    errorMessage = event.message
                }
            }
        }
    }

    if (showErrorDialog) {
        CustomDialog(
            title = "Error",
            text = errorMessage,
            onDismissRequest = {
                showErrorDialog = false
                errorMessage = ""
            }
        )
    }

    Scaffold { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // Se pasa el MainViewModel compartido al NavHost
            AppNavHost(navController = navController, mainViewModel = mainViewModel)
        }
    }
}
