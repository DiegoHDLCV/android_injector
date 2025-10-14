// Archivo: com/vigatec/android_injector/ui/screens/MainScreen.kt

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
import com.vigatec.android_injector.ui.navigation.Routes // Asegúrate de tener este import
import com.vigatec.android_injector.viewmodel.ConnectionStatus
import com.vigatec.android_injector.viewmodel.MainViewModel
import com.vigatec.android_injector.util.LogcatReader
import com.example.communication.polling.CommLog
import com.example.communication.polling.CommLogEntry
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreen(navController: NavHostController) {
    val viewModel: MainViewModel = hiltViewModel()
    val status by viewModel.connectionStatus.collectAsState()
    val cableDetected by viewModel.cableConnected.collectAsState()

    // ELIMINADO: Auto-start automático. Ahora el usuario controla cuándo iniciar la escucha
    // o se puede detectar el cable y auto-iniciar

    val commLogs by CommLog.entries.collectAsState()
    val logcatLines by LogcatReader.lines.collectAsState()
    val rawDataReceived by viewModel.rawReceivedData.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var selectedProtocol by remember { mutableStateOf(SystemConfig.commProtocolSelected) }

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Indicador de cable USB con más detalle
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (cableDetected) Color.Green.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (cableDetected) "🔌 Cable USB CONECTADO" else "⚠️ Cable USB NO DETECTADO",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (cableDetected) Color(0xFF006400) else Color.Red
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (cableDetected) 
                            "✓ Puerto físico disponible. Pulse 'Iniciar Escucha' para comenzar." 
                        else 
                            "✗ Puerto no disponible. Conecte el cable USB y espere unos segundos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cableDetected) Color(0xFF006400) else Color.Red,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                                enabled = !isConnectionActive
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedProtocol == protocol),
                            onClick = null,
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

            // --- CÓDIGO AÑADIDO ---
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate(Routes.InjectedKeysScreen.route) },
                // Habilita el botón siempre que la app no esté en un estado de transición
                enabled = status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.LISTENING || status == ConnectionStatus.ERROR
            ) {
                Text("Ver Llaves Inyectadas")
            }
            // --- FIN DEL CÓDIGO AÑADIDO ---

            // --- BOTONES DE ENVÍO AGREGADOS ---
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.sendAck() },
                    enabled = status == ConnectionStatus.LISTENING
                ) {
                    Text("Enviar ACK")
                }
            }

            // Campo para datos personalizados
            var customDataText by remember { mutableStateOf("") }

            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.material3.OutlinedTextField(
                value = customDataText,
                onValueChange = { customDataText = it },
                label = { Text("Datos personalizados (ASCII)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = status == ConnectionStatus.LISTENING,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (customDataText.isNotEmpty()) {
                        viewModel.sendCustomData(customDataText)
                        customDataText = ""
                    }
                },
                enabled = status == ConnectionStatus.LISTENING && customDataText.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar Datos Personalizados")
            }
            // --- FIN BOTONES DE ENVÍO ---

            Spacer(modifier = Modifier.height(16.dp))

            // --- BOTÓN DE EMERGENCIA ---
//            Button(
//                onClick = { viewModel.emergencyReset() },
//                enabled = true, // Siempre habilitado para emergencias
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .background(
//                        color = Color.Red.copy(alpha = 0.1f),
//                        shape = MaterialTheme.shapes.medium
//                    )
//            ) {
//                Text(
//                    text = "🚨 RESET DE EMERGENCIA",
//                    color = Color.Red,
//                    style = MaterialTheme.typography.bodyLarge
//                )
//            }
            Text(
                text = "Usar solo si la app se queda colgada",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Red.copy(alpha = 0.7f)
            )
            // --- FIN BOTÓN DE EMERGENCIA ---

            Spacer(modifier = Modifier.height(24.dp))

            // Panel de Logs de Comunicación (incluye detección de cable)
            CommLogsPanel(entries = commLogs)

            // Panel de Logcat (proceso actual)
            //LogcatPanel(lines = logcatLines)

            Text("Log de Datos Recibidos:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = rawDataReceived.ifEmpty { "Esperando datos..." },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommLogsPanel(entries: List<CommLogEntry>) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Logs de Comunicación (SubPOS)", style = MaterialTheme.typography.titleSmall)
            if (entries.isEmpty()) {
                Text("Sin registros aún…", style = MaterialTheme.typography.bodySmall)
            } else {
                val last = entries.takeLast(120).asReversed()
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 180.dp)) {
                    items(last.size) { idx ->
                        val e = last[idx]
                        val color = when (e.level) {
                            "E" -> MaterialTheme.colorScheme.error
                            "W" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text("[${e.level}] ${e.tag}: ${e.message}", color = color, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogcatPanel(lines: List<String>) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 240.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Logcat (proceso actual)", style = MaterialTheme.typography.titleSmall)
            val allowedTags = setOf(
                "AisinoComController",
                "AisinoCommManager",
                "CommSDKManager",
                "CommunicationSDKManager",
                "PollingService",
                "NewposComController",
                "UrovoComController",
                "IComController",
                "MainViewModel", // solo tomaremos líneas con palabras de puerto/serial abajo
                "SdkApi"
            )
            val keywords = setOf(
                "UART", "USB", "Serial", "Baud", "write", "read", "RX", "TX",
                "Port", "open", "close", "SystemInit_Api", "Attempting to read",
                "Attempting to open", "opened successfully", "reset", "baud rate"
            )
            val filtered = lines.filter { raw ->
                val line = raw.trim()
                val tagMatch = allowedTags.any { t -> line.contains(" $t ") || line.contains("$t ") }
                val keywordMatch = keywords.any { kw -> line.contains(kw, ignoreCase = true) }
                if (line.contains("MainViewModel")) {
                    // Solo logs de MV relacionados a comunicación
                    keywordMatch
                } else {
                    tagMatch || keywordMatch
                }
            }
            if (filtered.isEmpty()) {
                Text("Sin líneas aún…", style = MaterialTheme.typography.bodySmall)
            } else {
                val last = filtered.takeLast(120).asReversed()
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(last.size) { idx ->
                        Text(last[idx], style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
