package com.vigatec.injector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.injector.viewmodel.LogsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailScreen(
    logId: Long,
    viewModel: LogsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val log = remember(uiState.filteredLogs, logId) {
        uiState.filteredLogs.find { it.id == logId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (log == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Log no encontrado",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(onClick = onBack) {
                        Text("Volver")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Estado y fecha
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip(status = log.operationStatus)
                        Text(
                            text = viewModel.formatTimestamp(log.timestamp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sección de error destacada (solo para errores)
                if (log.operationStatus == "ERROR" && log.notes.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Detalles del Error",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = log.notes,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Información general
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Información General",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        LogDetailRow(label = "Usuario", value = log.username, icon = Icons.Default.Person)
                        LogDetailRow(label = "Perfil", value = log.profileName, icon = Icons.Default.Folder)

                        if (log.keyType.isNotEmpty() && log.keyType != "N/A") {
                            LogDetailRow(label = "Tipo de Llave", value = log.keyType, icon = Icons.Default.Key)
                        }

                        if (log.keySlot >= 0) {
                            LogDetailRow(label = "Slot", value = log.keySlot.toString(), icon = Icons.Default.Storage)
                        }
                    }
                }

                // Comando enviado
                MessageDataCard(
                    title = "Comando Enviado",
                    hexData = log.commandSent,
                    icon = Icons.Default.Send
                )

                // Respuesta recibida
                MessageDataCard(
                    title = "Respuesta Recibida",
                    hexData = log.responseReceived,
                    icon = Icons.Default.Download
                )
            }
        }
    }
}

@Composable
fun LogDetailRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MessageDataCard(
    title: String,
    hexData: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    var selectedFormat by remember { mutableStateOf("ASCII") }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header con título e icono
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Selector de formato
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFormat == "ASCII",
                    onClick = { selectedFormat = "ASCII" },
                    label = { Text("ASCII") }
                )
                FilterChip(
                    selected = selectedFormat == "HEX",
                    onClick = { selectedFormat = "HEX" },
                    label = { Text("HEX") }
                )
            }

            // Contenido del mensaje
            val displayText = when (selectedFormat) {
                "ASCII" -> hexToAscii(hexData)
                "HEX" -> formatHexString(hexData)
                else -> hexData
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = displayText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Información de longitud
            Text(
                text = "Longitud: ${hexData.length / 2} bytes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Convierte una cadena hexadecimal a ASCII
 */
fun hexToAscii(hex: String): String {
    val cleanHex = hex.replace(" ", "").replace("0x", "")
    val result = StringBuilder()
    
    var i = 0
    while (i < cleanHex.length - 1) {
        try {
            val byte = cleanHex.substring(i, i + 2).toInt(16)
            // Mostrar caracteres imprimibles, sino mostrar punto
            result.append(if (byte in 32..126) byte.toChar() else '.')
        } catch (e: Exception) {
            result.append('?')
        }
        i += 2
    }
    
    return result.toString()
}

/**
 * Formatea una cadena hexadecimal para mostrarla sin 0x y con espacios
 */
fun formatHexString(hex: String): String {
    val cleanHex = hex.replace(" ", "").replace("0x", "").uppercase()
    val result = StringBuilder()
    
    for (i in cleanHex.indices step 2) {
        if (i + 2 <= cleanHex.length) {
            if (i > 0) result.append(" ")
            result.append(cleanHex.substring(i, i + 2))
        }
    }
    
    return result.toString()
}


