package com.vigatec.dev_injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.dev_injector.ui.viewmodel.SimpleSplashViewModel

@Composable
fun SimpleSplashScreen(
    onNavigateToMain: () -> Unit,
    viewModel: SimpleSplashViewModel = hiltViewModel()
) {
    val initState by viewModel.initState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initializeApplication()
    }

    LaunchedEffect(initState) {
        if (initState is SimpleSplashViewModel.InitState.Success) {
            onNavigateToMain()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (initState) {
            is SimpleSplashViewModel.InitState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Inicializando SDK...")
                }
            }
            is SimpleSplashViewModel.InitState.Success -> {
                Text("Listo")
            }
            is SimpleSplashViewModel.InitState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Error: ${(initState as SimpleSplashViewModel.InitState.Error).message}",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.initializeApplication() }) {
                        Text("Reintentar")
                    }
                }
            }
        }
    }
}