package com.vigatec.injector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.persistence.entities.ProfileEntity
import com.vigatec.injector.viewmodel.InjectionStatus
import com.vigatec.injector.viewmodel.KeyInjectionState
import com.vigatec.injector.viewmodel.KeyInjectionViewModel
import com.vigatec.injector.viewmodel.KeyInjectionItem
import com.vigatec.injector.viewmodel.KeyInjectionItemStatus
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyInjectionModal(
    viewModel: KeyInjectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var showUninstallDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            Log.i("KeyInjectionModal", "=== EVENTO SNACKBAR FUTUREX ===")
            Log.i("KeyInjectionModal", "Mensaje: $message")
            Log.i("KeyInjectionModal", "================================================")
            // Aquí podrías mostrar un Snackbar si es necesario
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uninstallDialogEvent.collect { shouldShow ->
            Log.i("KeyInjectionModal", "=== EVENTO DIALOG DESINSTALACIÓN ===")
            Log.i("KeyInjectionModal", "Mostrar diálogo: $shouldShow")
            Log.i("KeyInjectionModal", "================================================")
            showUninstallDialog = shouldShow
        }
    }

    // AlertDialog para confirmar desinstalación
    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = {
                Log.i("KeyInjectionModal", "Diálogo desinstalación cerrado sin respuesta")
                showUninstallDialog = false
                viewModel.cancelUninstallDialog()
            },
            title = {
                Text(
                    text = "Desinstalación de KeyReceiver",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "¿Deseas eliminar la aplicación KeyReceiver del dispositivo receptor?\n\nEsta acción eliminará completamente la aplicación. Para volver a usarla, será necesario reinstalarla.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        Log.i("KeyInjectionModal", "Usuario confirmó desinstalación")
                        showUninstallDialog = false
                        viewModel.sendUninstallCommand()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sí, eliminar")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        Log.i("KeyInjectionModal", "Usuario canceló desinstalación")
                        showUninstallDialog = false
                        viewModel.cancelUninstallDialog()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text("No, mantener")
                }
            }
        )
    }

    if (state.showInjectionModal) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.95f)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header - Optimizado
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "🚀 Inyección de Llaves",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "Protocolo FUTUREX",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                )
                            }

                            if (state.status == InjectionStatus.IDLE || state.status == InjectionStatus.SUCCESS || state.status == InjectionStatus.ERROR) {
                                IconButton(
                                    onClick = {
                                        Log.i("KeyInjectionModal", "=== CERRANDO MODAL FUTUREX DESDE UI ===")
                                        Log.i("KeyInjectionModal", "Usuario presionó botón 'Cerrar'")
                                        viewModel.hideInjectionModal()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cerrar",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Contenido - Sección unificada
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Sección unificada: Perfil + Llaves + Indicador de cable
                        UnifiedProfileAndKeysCard(
                            profile = state.currentProfile,
                            keysToInject = state.keysToInject,
                            cableConnected = state.cableConnected,
                            status = state.status
                        )
                    }

                    // Footer con botones - Optimizado
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (state.status) {
                                InjectionStatus.IDLE -> {
                                    Button(
                                        onClick = { 
                                            Log.i("KeyInjectionModal", "=== INICIANDO INYECCIÓN FUTUREX DESDE UI ===")
                                            Log.i("KeyInjectionModal", "Usuario presionó botón 'Iniciar Inyección'")
                                            viewModel.startKeyInjection() 
                                        },
                                        enabled = state.cableConnected,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Iniciar Inyección")
                                    }
                                }
                                InjectionStatus.SUCCESS -> {
                                    TextButton(
                                        onClick = { 
                                            Log.i("KeyInjectionModal", "=== CERRANDO MODAL FUTUREX DESDE UI (ÉXITO) ===")
                                            Log.i("KeyInjectionModal", "Usuario presionó botón 'Cerrar' en estado de éxito")
                                            viewModel.hideInjectionModal() 
                                        },
                                        modifier = Modifier.padding(end = 12.dp)
                                    ) {
                                        Text("Cerrar")
                                    }
                                    Button(
                                        onClick = { 
                                            Log.i("KeyInjectionModal", "=== CERRANDO MODAL FUTUREX DESDE UI (ÉXITO) ===")
                                            Log.i("KeyInjectionModal", "Usuario presionó botón 'Completado'")
                                            viewModel.hideInjectionModal() 
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Completado")
                                    }
                                }
                                InjectionStatus.ERROR -> {
                                    TextButton(
                                        onClick = { 
                                            Log.i("KeyInjectionModal", "=== CERRANDO MODAL FUTUREX DESDE UI (ERROR) ===")
                                            Log.i("KeyInjectionModal", "Usuario presionó botón 'Cerrar' en estado de error")
                                            viewModel.hideInjectionModal() 
                                        },
                                        modifier = Modifier.padding(end = 12.dp)
                                    ) {
                                        Text("Cerrar")
                                    }
                                    Button(
                                        onClick = { 
                                            Log.i("KeyInjectionModal", "=== REINTENTANDO INYECCIÓN FUTUREX DESDE UI ===")
                                            Log.i("KeyInjectionModal", "Usuario presionó botón 'Reintentar'")
                                            viewModel.startKeyInjection() 
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Reintentar")
                                    }
                                }
                                else -> {
                                    // Estados de carga - no mostrar botones
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedProfileAndKeysCard(
    profile: ProfileEntity?,
    keysToInject: List<KeyInjectionItem>,
    cableConnected: Boolean,
    status: InjectionStatus
) {
    if (profile == null) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Perfil + Indicador de cable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Información del perfil
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = profile.applicationType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Indicador de cable integrado
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (cableConnected) Icons.Rounded.Usb else Icons.Default.UsbOff,
                        contentDescription = null,
                        tint = if (cableConnected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (cableConnected) "Cable conectado" else "Sin cable",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cableConnected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Mensaje de estado
            if (status == InjectionStatus.IDLE) {
                Text(
                    text = if (cableConnected) 
                        "Preparado para inyección" 
                    else 
                        "Verificar cable USB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (cableConnected)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
            
            // Lista de llaves
            if (keysToInject.isNotEmpty()) {
                Divider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Llaves a inyectar (${keysToInject.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    keysToInject.forEachIndexed { index, item ->
                        KeyItemRow(
                            item = item,
                            index = index + 1,
                            total = keysToInject.size
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun InjectionLogsCard(state: KeyInjectionState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Logs de Inyección",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = state.log.ifEmpty { "Esperando inicio de inyección..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}


@Composable
private fun KeyItemRow(item: KeyInjectionItem, index: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icono de estado
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            when (item.status) {
                KeyInjectionItemStatus.PENDING -> {
                    Icon(
                        Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = "Pendiente",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                KeyInjectionItemStatus.INJECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
                KeyInjectionItemStatus.INJECTED -> {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Inyectada",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                KeyInjectionItemStatus.ERROR -> {
                    Icon(
                        Icons.Rounded.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Información de la llave
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${item.keyConfig.usage}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Slot: ${item.keyConfig.slot}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = item.keyConfig.keyType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (item.status == KeyInjectionItemStatus.ERROR && item.errorMessage != null) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
            }
        }
        
        // Número de índice
        Text(
            text = "$index/$total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}


