// Archivo: com/vigatec/android_injector/ui/screens/InjectedKeysScreen.kt

package com.vigatec.android_injector.ui.screens

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.persistence.entities.InjectedKeyEntity
import com.vigatec.android_injector.viewmodel.InjectedKeysViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InjectedKeysScreen(
    navController: NavController,
    viewModel: InjectedKeysViewModel = hiltViewModel()
) {
    val keys by viewModel.injectedKeys.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<InjectedKeyEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        viewModel.snackbarMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Llaves en Dispositivo") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Borrar Todas las Llaves", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showDeleteAllDialog = true
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "Borrar Todo",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (keys.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(keys, key = { it.id }) { key ->
                    KeyInfoCard(
                        key = key,
                        onDeleteClick = { keyToDelete = key }
                    )
                }
            }
        }
    }

    if (keyToDelete != null) {
        ConfirmationDialog(
            icon = Icons.Default.Warning,
            title = "Confirmar Borrado",
            text = "¿Estás seguro de que quieres borrar la llave en el slot ${keyToDelete!!.keySlot}?",
            onConfirm = {
                viewModel.deleteKey(keyToDelete!!)
                keyToDelete = null
            },
            onDismiss = { keyToDelete = null }
        )
    }

    if (showDeleteAllDialog) {
        ConfirmationDialog(
            icon = Icons.Default.Warning,
            title = "Borrar Todas las Llaves",
            text = "Esta acción es irreversible y eliminará todas las llaves del PED. ¿Estás seguro?",
            onConfirm = {
                Log.i("InjectedKeysScreen", "El usuario confirmó el borrado de todas las llaves.")
                viewModel.deleteAllKeys()
                showDeleteAllDialog = false
            },
            onDismiss = { showDeleteAllDialog = false }
        )
    }
}

@Composable
fun KeyInfoCard(modifier: Modifier = Modifier, key: InjectedKeyEntity, onDeleteClick: () -> Unit) {
    // --- INICIO DE CAMBIOS: Lógica de estado visual ---
    val statusColor = when (key.status) {
        "SUCCESSFUL" -> Color(0xFF388E3C)
        "FAILED" -> MaterialTheme.colorScheme.error
        "DELETING" -> Color(0xFF757575) // Gris para estado transitorio
        else -> Color.DarkGray
    }
    val cardBorder = when(key.status) {
        "FAILED" -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        "DELETING" -> BorderStroke(1.dp, Color.Gray)
        else -> null
    }
    // Deshabilitar la tarjeta si se está borrando para evitar interacciones múltiples
    val isEnabled = key.status != "DELETING"
    // --- FIN DE CAMBIOS ---

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = cardBorder,
        // --- CAMBIO: Aplicar estado deshabilitado visualmente ---
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = getKeyTypeIcon(key.keyType),
                    contentDescription = "Tipo de llave",
                    // --- CAMBIO ---
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(40.dp).padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatKeyType(key.keyType),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        // --- CAMBIO ---
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                    Text(
                        text = "Slot: ${key.keySlot}",
                        style = MaterialTheme.typography.bodySmall,
                        // --- CAMBIO ---
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                    )
                }
                // --- CAMBIO: Pasar el estado de borrado al chip ---
                StatusChip(text = key.status, color = statusColor, isDeleting = !isEnabled)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // --- CAMBIO: Pasar el estado de habilitado a las filas de detalle ---
            KeyDetailRow(icon = Icons.Default.Lock, label = "Algoritmo", value = key.keyAlgorithm, isEnabled = isEnabled)
            KeyDetailRow(icon = Icons.Default.Fingerprint, label = "KCV", value = key.kcv, isEnabled = isEnabled)
            KeyDetailRow(
                icon = Icons.Default.CalendarToday,
                label = "Fecha",
                value = remember {
                    SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).format(Date(key.injectionTimestamp))
                },
                isEnabled = isEnabled
            )

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                // --- CAMBIO: Deshabilitar el botón de borrado ---
                IconButton(onClick = onDeleteClick, enabled = isEnabled) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        "Borrar Llave",
                        tint = if (isEnabled) MaterialTheme.colorScheme.error else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyOff,
                contentDescription = "No hay llaves",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = "Sin Llaves Registradas",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Text(
                text = "Las llaves inyectadas en el dispositivo aparecerán aquí una vez que se registren en la base de datos local.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
// --- CAMBIO: Se añade el parámetro 'isEnabled' ---
private fun KeyDetailRow(icon: ImageVector, label: String, value: String, isEnabled: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentColor = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.Gray
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(100.dp),
            color = contentColor.copy(alpha = if (isEnabled) 1.0f else 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}

@Composable
// --- CAMBIO: Se añade el parámetro 'isDeleting' ---
fun StatusChip(text: String, color: Color, isDeleting: Boolean = false) {
    Box(
        modifier = Modifier
            .background(color, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mostrar un indicador de progreso si se está borrando
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = Color.White,
                    strokeWidth = 1.5.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                // Cambiar el texto para que sea más claro
                text = if (isDeleting) "BORRANDO" else text,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun ConfirmationDialog(
    icon: ImageVector,
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        icon = { Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.error) },
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun formatKeyType(type: String): String {
    return type.replace("_", " ").split(" ")
        .joinToString(" ") { it.lowercase(Locale.getDefault()).replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() } }
        .replace("Key", "Key")
}

private fun getKeyTypeIcon(type: String): ImageVector {
    return when {
        type.contains("PIN") -> Icons.Default.Pin
        type.contains("MAC") -> Icons.Default.Tag
        type.contains("TRANSPORT") || type.contains("MASTER") -> Icons.Default.VpnKey
        type.contains("DATA") -> Icons.Default.EnhancedEncryption
        else -> Icons.Default.Key
    }
}