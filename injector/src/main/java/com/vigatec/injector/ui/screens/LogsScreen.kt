package com.vigatec.injector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.persistence.entities.InjectionLogEntity
import com.vigatec.injector.viewmodel.LogsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var selectedLog by remember { mutableStateOf<InjectionLogEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs de Inyección") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            if (showFilters) Icons.Default.FilterAltOff else Icons.Default.FilterAlt,
                            "Filtros"
                        )
                    }
                    IconButton(
                        onClick = { showDeleteAllDialog = true },
                        enabled = uiState.filteredLogs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteForever, "Eliminar todos")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filtros
            if (showFilters) {
                FilterSection(
                    uiState = uiState,
                    onUsernameSelected = { viewModel.applyFilters(username = it) },
                    onProfileSelected = { viewModel.applyFilters(profile = it) },
                    onClearFilters = { viewModel.clearFilters() }
                )
            }

            // Lista de logs
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay logs disponibles",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredLogs) { log ->
                        LogItem(
                            log = log,
                            onDelete = { selectedLog = it },
                            formatTimestamp = { viewModel.formatTimestamp(it) }
                        )
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para eliminar log
    selectedLog?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLog = null },
            title = { Text("Eliminar Log") },
            text = { Text("¿Estás seguro de que deseas eliminar este log?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLog(log)
                        selectedLog = null
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLog = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de confirmación para eliminar todos los logs
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Eliminar Todos los Logs") },
            text = { Text("¿Estás seguro de que deseas eliminar TODOS los logs? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllLogs()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Eliminar Todos", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Mostrar mensaje de error si existe
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Aquí podrías mostrar un Snackbar con el error
            viewModel.clearErrorMessage()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSection(
    uiState: com.vigatec.injector.viewmodel.LogsUiState,
    onUsernameSelected: (String?) -> Unit,
    onProfileSelected: (String?) -> Unit,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Filtros",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Filtro por usuario
            var expandedUsername by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedUsername,
                onExpandedChange = { expandedUsername = it }
            ) {
                OutlinedTextField(
                    value = uiState.selectedUsername ?: "Todos",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Usuario") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUsername) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedUsername,
                    onDismissRequest = { expandedUsername = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Todos") },
                        onClick = {
                            onUsernameSelected(null)
                            expandedUsername = false
                        }
                    )
                    uiState.availableUsernames.forEach { username ->
                        DropdownMenuItem(
                            text = { Text(username) },
                            onClick = {
                                onUsernameSelected(username)
                                expandedUsername = false
                            }
                        )
                    }
                }
            }

            // Filtro por perfil
            var expandedProfile by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedProfile,
                onExpandedChange = { expandedProfile = it }
            ) {
                OutlinedTextField(
                    value = uiState.selectedProfile ?: "Todos",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Perfil") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfile) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedProfile,
                    onDismissRequest = { expandedProfile = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Todos") },
                        onClick = {
                            onProfileSelected(null)
                            expandedProfile = false
                        }
                    )
                    uiState.availableProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile) },
                            onClick = {
                                onProfileSelected(profile)
                                expandedProfile = false
                            }
                        )
                    }
                }
            }

            // Botón para limpiar filtros
            Button(
                onClick = onClearFilters,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.selectedUsername != null || uiState.selectedProfile != null
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Limpiar Filtros")
            }
        }
    }
}

@Composable
fun LogItem(
    log: InjectionLogEntity,
    onDelete: (InjectionLogEntity) -> Unit,
    formatTimestamp: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header con estado y acciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(status = log.operationStatus)
                IconButton(onClick = { onDelete(log) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Información del log
            LogInfoRow(label = "Usuario", value = log.username)
            LogInfoRow(label = "Perfil", value = log.profileName)
            LogInfoRow(label = "Fecha", value = formatTimestamp(log.timestamp))

            if (log.keyType.isNotEmpty()) {
                LogInfoRow(label = "Tipo de Llave", value = log.keyType)
            }

            if (log.keySlot >= 0) {
                LogInfoRow(label = "Slot", value = log.keySlot.toString())
            }

            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            // Comando enviado
            Text(
                text = "Comando Enviado:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = log.commandSent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            )

            Spacer(Modifier.height(8.dp))

            // Respuesta recibida
            Text(
                text = "Respuesta Recibida:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = log.responseReceived,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            )

            if (log.notes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Notas: ${log.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (color, text) = when (status) {
        "SUCCESS" -> Color(0xFF4CAF50) to "Exitoso"
        "FAILED" -> Color(0xFFF44336) to "Fallido"
        "ERROR" -> Color(0xFFFF9800) to "Error"
        else -> Color.Gray to status
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LogInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
