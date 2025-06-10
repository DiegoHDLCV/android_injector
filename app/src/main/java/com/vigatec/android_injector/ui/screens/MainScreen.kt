package com.vigatec.android_injector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.vigatec.android_injector.ui.Navigator // Asegúrate que esta importación sea correcta
import com.vigatec.android_injector.ui.events.UiEvent // Asegúrate que esta importación sea correcta
import com.vigatec.android_injector.viewmodel.ConnectionStatus
import com.vigatec.android_injector.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreen(navController: NavHostController) {
    val viewModel: MainViewModel = viewModel()
    val status by viewModel.connectionStatus.collectAsState()
    // --- MODIFICADO --- Usamos rawReceivedData
    val rawDataReceived by viewModel.rawReceivedData.collectAsState()
    // --- FIN MODIFICADO ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState() // Para hacer el área de texto scrollable

    // Escucha eventos de Snackbar
    LaunchedEffect(key1 = true) { // Se puede usar true si no cambia
        viewModel.snackbarEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Escucha eventos de Navegación
    LaunchedEffect(key1 = true) { // Se puede usar true si no cambia
        viewModel.uiEvent.collectLatest { event ->
            // Usa tu objeto Navigator para manejar la navegación
            Navigator.navigate(navController, event)
        }
    }

    // --- NUEVO --- Efecto para auto-scroll del área de texto
    LaunchedEffect(rawDataReceived) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    // --- FIN NUEVO ---


    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // Cambiado a Top para mejor layout
        ) {
            Text("Estado Conexión Serial:", style = MaterialTheme.typography.titleLarge)
            Text(status.name, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            if (status == ConnectionStatus.LISTENING || status == ConnectionStatus.OPENING || status == ConnectionStatus.INITIALIZING) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.startListening() },
                    enabled = status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.ERROR
                ) {
                    Text("Iniciar Escucha")
                }
                Button(
                    onClick = { viewModel.stopListening() },
                    enabled = status != ConnectionStatus.DISCONNECTED && status != ConnectionStatus.CLOSING
                ) {
                    Text("Detener Escucha")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón de prueba (opcional, podrías quitarlo)
            Button(
                onClick = {
                    // Envía un comando PING formateado (Ejemplo)
                    val pingCommand = com.example.format.SerialMessageFormatter.format("PING", "TEST")
                    viewModel.sendData(pingCommand)
                },
                enabled = status == ConnectionStatus.LISTENING
            ) {
                Text("Enviar 'PING'")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Log de Datos Recibidos:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // --- MODIFICADO --- Usamos rawDataReceived y añadimos scroll y borde
            Text(
                text = rawDataReceived, // <-- Cambio aquí
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // <-- Para que ocupe el espacio restante
                    .background(Color.LightGray.copy(alpha = 0.2f))
                    .border(1.dp, Color.Gray)
                    .padding(8.dp)
                    .verticalScroll(scrollState), // <-- Añadido scroll
                style = MaterialTheme.typography.bodySmall
            )
            // --- FIN MODIFICADO ---

            Spacer(modifier = Modifier.height(16.dp))

            // Ejemplo de botón de navegación
//            Button(onClick = { viewModel.navigate(UiEvent.NavigateBack) }) {
//                Text("Ir Atrás (Ejemplo Nav)")
//            }
        }
    }
}