package com.vigatec.injector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.persistence.entities.InjectedKeyEntity
import com.vigatec.injector.viewmodel.KeyVaultViewModel
import com.vigatec.injector.viewmodel.KeyWithProfiles
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun KeyVaultScreen(
    viewModel: KeyVaultViewModel = hiltViewModel(),
    onNavigateToExportImport: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            KeyVaultTopBar(
                onRefresh = { viewModel.loadKeys() },
                onClearAll = { viewModel.onShowClearAllConfirmation() },
                onGenerateTestKeys = { viewModel.generateTestKeys() },
                onImportTestKeys = { viewModel.onImportTestKeys() },
                onNavigateToExportImport = onNavigateToExportImport,
                loading = state.loading,
                isAdmin = state.isAdmin
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Indicador de rol del usuario
            Surface(
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                color = if (state.isAdmin) 
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else 
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.isAdmin) "👤 ADMINISTRADOR" else "👤 USUARIO",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isAdmin) 
                            MaterialTheme.colorScheme.tertiary
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.keysWithProfiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay llaves almacenadas.", color = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.keysWithProfiles) { keyWithProfiles ->
                        KeyCard(
                            key = keyWithProfiles.key,
                            assignedProfiles = keyWithProfiles.assignedProfiles,
                            onDelete = { viewModel.onShowDeleteModal(it) },
                            onToggleKEK = { viewModel.toggleKeyAsKEK(it) },
                            isAdmin = state.isAdmin
                        )
                    }
                }
            }
        }
    }

    if (state.showDeleteModal && state.selectedKey != null) {
        DeleteKeyDialog(
            key = state.selectedKey!!,
            onConfirm = {
                viewModel.onDeleteKey(it)
                viewModel.onDismissDeleteModal()
            },
            onDismiss = { viewModel.onDismissDeleteModal() }
        )
    }

    if (state.showClearAllConfirmation) {
        ClearAllKeysDialog(
            onConfirm = { viewModel.onConfirmClearAllKeys() },
            onDismiss = { viewModel.onDismissClearAllConfirmation() }
        )
    }

    if (state.showImportJsonDialog) {
        ImportJsonDialog(
            onImport = { jsonContent -> 
                viewModel.importFromJsonContent(jsonContent)
                viewModel.onDismissImportJsonDialog()
            },
            onDismiss = { viewModel.onDismissImportJsonDialog() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyVaultTopBar(
    onRefresh: () -> Unit,
    onClearAll: () -> Unit,
    onGenerateTestKeys: () -> Unit,
    onImportTestKeys: () -> Unit,
    onNavigateToExportImport: () -> Unit,
    loading: Boolean,
    isAdmin: Boolean
) {
    TopAppBar(
        title = { Text("Almacén de Llaves", fontWeight = FontWeight.Bold) },
        actions = {
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
            }
            // Solo admins pueden acceder a exportar/importar
            if (isAdmin) {
                IconButton(
                    onClick = onNavigateToExportImport,
                    enabled = !loading
                ) {
                    Icon(Icons.Default.ImportExport, contentDescription = "Exportar/Importar")
                }
                IconButton(
                    onClick = onImportTestKeys,
                    enabled = !loading
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "Importar Llaves de Prueba")
                }
                IconButton(
                    onClick = onGenerateTestKeys,
                    enabled = !loading
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Generar Llaves de Prueba")
                }
                IconButton(onClick = onClearAll, enabled = !loading) {
                    Icon(Icons.Default.Delete, contentDescription = "Limpiar Almacén")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun KeyCard(
    key: InjectedKeyEntity,
    assignedProfiles: List<String> = emptyList(),
    onDelete: (InjectedKeyEntity) -> Unit,
    onToggleKEK: (InjectedKeyEntity) -> Unit,
    isAdmin: Boolean = false
) {
    val isCeremonyKey = key.keyType == "CEREMONY_KEY"
    val isKEKStorage = key.isKEKStorage() // KEK creada en ceremonia
    val isKTK = key.isKEK && !isKEKStorage // KTK marcada desde almacén
    val detectedAlgorithm = detectKeyAlgorithm(key)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isKEKStorage -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                isKTK -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                isCeremonyKey -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = "Key",
                    tint = when {
                        isKEKStorage -> MaterialTheme.colorScheme.tertiary
                        isKTK -> MaterialTheme.colorScheme.secondary
                        isCeremonyKey -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = key.kcv,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Badge de tipo de algoritmo
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = detectedAlgorithm,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        // Badge KEK Storage
                        if (isKEKStorage) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    text = "KEK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        // Badge KTK
                        if (isKTK) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Text(
                                    text = "KTK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    // Mostrar nombre personalizado si existe
                    if (key.customName.isNotEmpty()) {
                        Text(
                            text = key.customName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            // Solo mostrar detalles técnicos para llaves que NO son de ceremonia
            if (!isCeremonyKey) {
                // Solo mostrar slot si es positivo (slots reales)
                if (key.keySlot >= 0) {
                    Text("Slot: ${key.keySlot}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Tipo: ${key.keyType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Algoritmo: ${key.keyAlgorithm}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // Para llaves de ceremonia, mostrar información relevante
                Text("Origen: Ceremonia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Longitud: ${key.keyData.length / 2} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Mostrar estado si es KEK Storage o KTK
                if (isKEKStorage || isKTK) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Estado: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (key.status) {
                                "ACTIVE" -> Color(0xFF4CAF50)
                                "EXPORTED" -> Color(0xFF2196F3)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = key.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Mostrar los perfiles asignados para todas las llaves
            Text(
                text = if (assignedProfiles.isNotEmpty())
                    "Perfil${if (assignedProfiles.size > 1) "es" else ""}: ${assignedProfiles.joinToString(", ")}"
                else
                    "Sin asignar",
                style = MaterialTheme.typography.bodySmall,
                color = if (assignedProfiles.isNotEmpty())
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Fecha: ${formatDate(key.injectionTimestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón "Marcar como KTK" / "Quitar KTK" - Disponible para todos si NO es KEK Storage
                if (!isKEKStorage) {
                    OutlinedButton(
                        onClick = { onToggleKEK(key) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isKTK) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (isKTK) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isKTK) "Quitar KTK" else "Marcar KTK", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Botón Eliminar - Solo para administradores
                if (isAdmin) {
                    Button(
                        onClick = { onDelete(key) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = if (isKEKStorage) Modifier.fillMaxWidth() else Modifier.weight(1f)
                    ) {
                        Text("Eliminar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ImportJsonDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var jsonContent by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📥 Importar Llaves desde JSON") },
        text = {
            Column {
                Text(
                    "Pega el contenido del archivo JSON generado por el script:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = jsonContent,
                    onValueChange = { 
                        jsonContent = it
                        showError = false
                    },
                    label = { Text("Contenido JSON") },
                    placeholder = { Text("Pega aquí el contenido del archivo...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    maxLines = 12,
                    isError = showError
                )
                
                if (showError) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (jsonContent.isBlank()) {
                        showError = true
                        errorMessage = "El contenido JSON no puede estar vacío"
                    } else {
                        try {
                            // Validar que sea JSON válido
                            val gson = com.google.gson.Gson()
                            gson.fromJson(jsonContent, com.google.gson.JsonObject::class.java)
                            onImport(jsonContent)
                        } catch (e: Exception) {
                            showError = true
                            errorMessage = "JSON inválido: ${e.message}"
                        }
                    }
                }
            ) {
                Text("Importar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun DeleteKeyDialog(key: InjectedKeyEntity, onConfirm: (InjectedKeyEntity) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar Llave") },
        text = { Text("¿Estás seguro de que quieres eliminar la llave con KCV ${key.kcv}?") },
        confirmButton = {
            Button(onClick = { onConfirm(key) }) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ClearAllKeysDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar Todas las Llaves") },
        text = { 
            Text(
                "⚠️ ADVERTENCIA: Esta acción eliminará TODAS las llaves del almacén.\n\n" +
                "Esto incluye:\n" +
                "• Llaves de ceremonia\n" +
                "• KEK Storage\n" +
                "• KTK (Key Transport Key)\n" +
                "• Llaves operacionales\n\n" +
                "Esta acción NO se puede deshacer.\n\n" +
                "¿Estás completamente seguro?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Sí, Eliminar Todo")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Detecta el tipo de algoritmo basado en la longitud de los datos de la llave
 * o en el algoritmo almacenado en la entidad
 */
fun detectKeyAlgorithm(key: InjectedKeyEntity): String {
    // Si ya tiene un algoritmo asignado (de ceremonia), usarlo
    if (key.keyAlgorithm != "UNASSIGNED" && key.keyAlgorithm.isNotEmpty() && key.keyType == "CEREMONY_KEY") {
        return when (key.keyAlgorithm) {
            "DES_TRIPLE" -> "3DES"
            "AES_128" -> "AES-128"
            "AES_192" -> "AES-192"
            "AES_256" -> "AES-256"
            else -> key.keyAlgorithm
        }
    }

    // Si no, detectar por longitud de datos
    val keyLengthBytes = key.keyData.length / 2 // Convertir hex a bytes
    return when (keyLengthBytes) {
        8 -> "DES"
        16 -> "3DES/AES-128" // Ambigüedad - pueden ser 3DES o AES-128
        24 -> "3DES/AES-192"
        32 -> "AES-256"
        else -> "Desconocido (${keyLengthBytes}B)"
    }
} 