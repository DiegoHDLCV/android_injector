package com.vigatec.android_injector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.config.CommProtocol
import com.example.config.SystemConfig
import com.vigatec.android_injector.ui.Navigator
import com.vigatec.android_injector.ui.events.UiEvent
import com.vigatec.android_injector.viewmodel.ConnectionStatus
import com.vigatec.android_injector.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreen(navController: NavHostController) {
    val viewModel: MainViewModel = hiltViewModel()
    val status by viewModel.connectionStatus.collectAsState()
    val rawDataReceived by viewModel.rawReceivedData.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    // Usamos 'remember' para que la UI reaccione a los cambios en la configuración.
    // Aunque SystemConfig es un objeto, necesitamos un State para que Compose se recomponga.
    var selectedProtocol by remember { mutableStateOf(SystemConfig.commProtocolSelected) }

    // Escucha eventos de Snackbar
    LaunchedEffect(key1 = true) {
        viewModel.snackbarEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Escucha eventos de Navegación
    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collectLatest { event ->
            Navigator.navigate(navController, event)
        }
    }

    // Efecto para auto-scroll del área de texto
    LaunchedEffect(rawDataReceived) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("Estado Conexión Serial:", style = MaterialTheme.typography.titleLarge)
            Text(status.name, style = MaterialTheme.typography.headlineMedium, color = when(status) {
                ConnectionStatus.LISTENING -> Color.Green
                ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            })
            Spacer(modifier = Modifier.height(16.dp))

            if (status == ConnectionStatus.LISTENING || status == ConnectionStatus.OPENING || status == ConnectionStatus.INITIALIZING || status == ConnectionStatus.CLOSING) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- SELECCIÓN DE PROTOCOLO ---
            Text("Protocolo de Comunicación:", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.selectableGroup()) {
                val isConnectionActive = status != ConnectionStatus.DISCONNECTED && status != ConnectionStatus.ERROR
                CommProtocol.values().forEach { protocol ->
                    Row(
                        Modifier
                            .height(56.dp)
                            .selectable(
                                selected = (selectedProtocol == protocol),
                                onClick = {
                                    selectedProtocol = protocol
                                    viewModel.setProtocol(protocol)
                                },
                                role = Role.RadioButton,
                                enabled = !isConnectionActive // Deshabilitar si la conexión está activa
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedProtocol == protocol),
                            onClick = null, // null recomendado para que el onClick del padre maneje todo
                            enabled = !isConnectionActive
                        )
                        Text(
                            text = protocol.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            if (status != ConnectionStatus.DISCONNECTED && status != ConnectionStatus.ERROR) {
                Text(
                    text = "Detén la conexión para cambiar de protocolo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }


            Spacer(modifier = Modifier.height(16.dp))


            // --- BOTONES DE CONTROL DE CONEXIÓN ---
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

            Spacer(modifier = Modifier.height(24.dp))

            Text("Log de Datos Recibidos:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = rawDataReceived.ifEmpty { "Esperando datos..." },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(8.dp)
                    .verticalScroll(scrollState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
