// Archivo: com/vigatec/android_injector/ui/screens/MainScreen.kt

package com.vigatec.keyreceiver.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.config.CommProtocol
import com.example.config.SystemConfig
import com.vigatec.keyreceiver.ui.Navigator
import com.vigatec.keyreceiver.ui.components.InjectionFeedCard
import com.vigatec.keyreceiver.ui.events.UiEvent
import com.vigatec.keyreceiver.ui.navigation.Routes
import com.vigatec.keyreceiver.viewmodel.ConnectionStatus
import com.vigatec.keyreceiver.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreen(navController: NavHostController) {
    val viewModel: MainViewModel = hiltViewModel()
    val status by viewModel.connectionStatus.collectAsState()
    val cableDetected by viewModel.cableConnected.collectAsState()
    val recentInjections by viewModel.recentInjections.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedProtocol by remember { mutableStateOf(SystemConfig.commProtocolSelected) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.snackbarEvent.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collectLatest { event ->
            Navigator.navigate(navController, event)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== 1. INDICADOR DE CABLE USB ==========
            UsbCableStatusCard(cableDetected = cableDetected)

            // ========== 2. ESTADO DE CONEXIÓN ==========
            ConnectionStatusCard(status = status)

            // ========== 3. BOTONES DE CONTROL ==========
            ControlButtons(
                status = status,
                onStartListening = { viewModel.startListening() },
                onStopListening = { viewModel.stopListening() }
            )

            // ========== 4. FEED DE LLAVES INYECTADAS (NUEVO!) ==========
            InjectionFeedCard(injections = recentInjections)

            // ========== 5. BOTÓN VER TODAS LAS LLAVES ==========
            Button(
                onClick = { navController.navigate(Routes.InjectedKeysScreen.route) },
                enabled = status == ConnectionStatus.DISCONNECTED ||
                         status == ConnectionStatus.LISTENING ||
                         status == ConnectionStatus.ERROR,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Todas las Llaves Inyectadas")
            }

            // ========== 6. CONFIGURACIÓN AVANZADA (COLAPSABLE) ==========
            AdvancedSettingsCard(
                isExpanded = showAdvancedSettings,
                onToggle = { showAdvancedSettings = !showAdvancedSettings },
                selectedProtocol = selectedProtocol,
                onProtocolSelected = { protocol ->
                    selectedProtocol = protocol
                    viewModel.setProtocol(protocol)
                },
                isConnectionActive = status != ConnectionStatus.DISCONNECTED &&
                                   status != ConnectionStatus.ERROR
            )
        }
    }
}

/**
 * Card de estado del cable USB
 */
@Composable
private fun UsbCableStatusCard(cableDetected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (cableDetected)
                Color(0xFF4CAF50).copy(alpha = 0.15f)
            else
                Color(0xFFFF5722).copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (cableDetected) Icons.Default.Usb else Icons.Default.UsbOff,
                contentDescription = null,
                tint = if (cableDetected) Color(0xFF4CAF50) else Color(0xFFFF5722),
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = if (cableDetected) "Cable USB Conectado" else "Cable USB No Detectado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (cableDetected) Color(0xFF4CAF50) else Color(0xFFFF5722)
                )
                Text(
                    text = if (cableDetected)
                        "Puerto disponible. Pulse 'Iniciar Escucha' para comenzar."
                    else
                        "Conecte el cable USB y espere unos segundos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Card de estado de la conexión
 */
@Composable
private fun ConnectionStatusCard(status: ConnectionStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Estado de Conexión",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status == ConnectionStatus.LISTENING ||
                    status == ConnectionStatus.OPENING ||
                    status == ConnectionStatus.INITIALIZING ||
                    status == ConnectionStatus.CLOSING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp
                    )
                }

                Text(
                    text = when(status) {
                        ConnectionStatus.DISCONNECTED -> "DESCONECTADO"
                        ConnectionStatus.INITIALIZING -> "INICIALIZANDO..."
                        ConnectionStatus.OPENING -> "ABRIENDO PUERTO..."
                        ConnectionStatus.LISTENING -> "ESCUCHANDO"
                        ConnectionStatus.CLOSING -> "CERRANDO..."
                        ConnectionStatus.ERROR -> "ERROR"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = when(status) {
                        ConnectionStatus.LISTENING -> Color(0xFF4CAF50)
                        ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                        ConnectionStatus.INITIALIZING, ConnectionStatus.OPENING, ConnectionStatus.CLOSING ->
                            MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/**
 * Botones de control de conexión
 */
@Composable
private fun ControlButtons(
    status: ConnectionStatus,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onStartListening,
            enabled = status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.ERROR,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Iniciar Escucha")
        }

        Button(
            onClick = onStopListening,
            enabled = status != ConnectionStatus.DISCONNECTED && status != ConnectionStatus.CLOSING,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Detener")
        }
    }
}

/**
 * Sección colapsable de configuración avanzada
 */
@Composable
private fun AdvancedSettingsCard(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    selectedProtocol: CommProtocol,
    onProtocolSelected: (CommProtocol) -> Unit,
    isConnectionActive: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header clickeable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Configuración Avanzada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Contraer" else "Expandir"
                    )
                }
            }

            // Contenido colapsable
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Divider()

                    Text(
                        text = "Protocolo de Comunicación",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(modifier = Modifier.selectableGroup()) {
                        CommProtocol.values().forEach { protocol ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (selectedProtocol == protocol),
                                        onClick = { onProtocolSelected(protocol) },
                                        role = Role.RadioButton,
                                        enabled = !isConnectionActive
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedProtocol == protocol),
                                    onClick = null,
                                    enabled = !isConnectionActive
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = protocol.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    if (isConnectionActive) {
                        Text(
                            text = "⚠ Detén la conexión para cambiar de protocolo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
