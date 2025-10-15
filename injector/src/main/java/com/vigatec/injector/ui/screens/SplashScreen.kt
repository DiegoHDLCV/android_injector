package com.vigatec.injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vigatec.injector.viewmodel.SplashViewModel

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel()
) {
    val initState by viewModel.initState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.initializeApplication()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (initState) {
            is SplashViewModel.InitState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Inicializando...")
                }
            }
            is SplashViewModel.InitState.Success -> {
                // Navegación se realiza vía UiEvent en el ViewModel
                CircularProgressIndicator()
            }
            is SplashViewModel.InitState.Error -> {
                val msg = (initState as SplashViewModel.InitState.Error).message
                Text(text = "Error: $msg", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}


