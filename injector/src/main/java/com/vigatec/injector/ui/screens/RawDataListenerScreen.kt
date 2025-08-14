package com.vigatec.injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vigatec.injector.viewmodel.ListenerStatus
import com.vigatec.injector.viewmodel.RawDataListenerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawDataListenerScreen(
    viewModel: RawDataListenerViewModel = hiltViewModel()
) {
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val rawReceivedData by viewModel.rawReceivedData.collectAsStateWithLifecycle()
    val sentData by viewModel.sentData.collectAsStateWithLifecycle()
    val snackbarEvent by viewModel.snackbarEvent.collectAsStateWithLifecycle(initialValue = "")

    var customDataText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar snackbar cuando hay eventos
    LaunchedEffect(snackbarEvent) {
        if (snackbarEvent.isNotEmpty()) {
            snackbarHostState.showSnackbar(snackbarEvent)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Raw Data Listener") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Estado de conexión
            ConnectionStatusCard(
                status = connectionStatus,
                onStartListening = { viewModel.startListening() },
                onStopListening = { viewModel.stopListening() }
            )

            // Controles de envío
            SendControlsCard(
                customDataText = customDataText,
                onCustomDataTextChange = { customDataText = it },
                onSendAck = { viewModel.sendAck() },
                onSendCustomData = {
                    if (customDataText.isNotEmpty()) {
                        viewModel.sendCustomData(customDataText)
                        customDataText = ""
                    }
                },
                isConnected = connectionStatus == ListenerStatus.LISTENING
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Datos recibidos
                DataDisplayCard(
                    title = "Datos Recibidos",
                    data = rawReceivedData,
                    onClear = { viewModel.clearReceivedData() },
                    backgroundColor = Color(0xFFF3E5F5),
                    modifier = Modifier.weight(1f)
                )

                // Datos enviados
                DataDisplayCard(
                    title = "Datos Enviados",
                    data = sentData,
                    onClear = { viewModel.clearSentData() },
                    backgroundColor = Color(0xFFE8F5E8),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    status: ListenerStatus,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                ListenerStatus.LISTENING -> Color(0xFFE8F5E8)
                ListenerStatus.ERROR -> Color(0xFFFFEBEE)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Estado de Conexión",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (status) {
                        ListenerStatus.DISCONNECTED -> "Desconectado"
                        ListenerStatus.INITIALIZING -> "Inicializando..."
                        ListenerStatus.OPENING -> "Abriendo puerto..."
                        ListenerStatus.LISTENING -> "Escuchando"
                        ListenerStatus.CLOSING -> "Cerrando..."
                        ListenerStatus.ERROR -> "Error"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (status) {
                        ListenerStatus.LISTENING -> Color(0xFF2E7D32)
                        ListenerStatus.ERROR -> Color(0xFFD32F2F)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                when (status) {
                    ListenerStatus.DISCONNECTED, ListenerStatus.ERROR -> {
                        Button(
                            onClick = onStartListening
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Iniciar Escucha")
                        }
                    }
                    ListenerStatus.LISTENING -> {
                        Button(
                            onClick = onStopListening,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Detener")
                        }
                    }
                    else -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SendControlsCard(
    customDataText: String,
    onCustomDataTextChange: (String) -> Unit,
    onSendAck: () -> Unit,
    onSendCustomData: () -> Unit,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Controles de Envío",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón ACK
                Button(
                    onClick = onSendAck,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviar ACK")
                }
            }

            // Campo de texto para datos personalizados
            OutlinedTextField(
                value = customDataText,
                onValueChange = onCustomDataTextChange,
                label = { Text("Datos personalizados (ASCII)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected,
                singleLine = true
            )

            Button(
                onClick = onSendCustomData,
                enabled = isConnected && customDataText.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enviar Datos Personalizados")
            }
        }
    }
}

@Composable
private fun DataDisplayCard(
    title: String,
    data: String,
    onClear: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )

                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Limpiar",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = if (data.isEmpty()) "Sin datos..." else data,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .verticalScroll(rememberScrollState()),
                    color = if (data.isEmpty())
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
