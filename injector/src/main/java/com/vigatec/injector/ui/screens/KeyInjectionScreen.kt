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
import com.example.persistence.entities.ProfileEntity
import com.vigatec.injector.viewmodel.InjectionStatus
import com.vigatec.injector.viewmodel.KeyInjectionState
import com.vigatec.injector.viewmodel.KeyInjectionViewModel
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyInjectionModal(
    viewModel: KeyInjectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            Log.i("KeyInjectionModal", "=== EVENTO SNACKBAR FUTUREX ===")
            Log.i("KeyInjectionModal", "Mensaje: $message")
            Log.i("KeyInjectionModal", "================================================")
            // Aquí podrías mostrar un Snackbar si es necesario
        }
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
                    
                    // Contenido - Optimizado con menos espaciado
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Información del perfil
                        ProfileInfoCard(profile = state.currentProfile)

                        // Indicador de cable USB
                        CableDetectionCard(cableConnected = state.cableConnected)

                        // Estado de conexión
                        ConnectionStatusCard(state = state)

                        // Progreso de inyección
                        InjectionProgressCard(state = state)

                        // Logs de inyección
                        //InjectionLogsCard(state = state)
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
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
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
private fun ProfileInfoCard(profile: ProfileEntity?) {
    if (profile == null) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            shape = RoundedCornerShape(10.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
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

            // Profile Name and Type
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = profile.applicationType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1
                )
            }

            // Key Counts (Compact)
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${profile.keyConfigurations.size}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = "Configuradas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: KeyInjectionState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (state.status) {
                InjectionStatus.SUCCESS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                InjectionStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                InjectionStatus.CONNECTING, InjectionStatus.INJECTING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = when (state.status) {
                InjectionStatus.SUCCESS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                InjectionStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                InjectionStatus.CONNECTING, InjectionStatus.INJECTING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            },
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (state.status) {
                            InjectionStatus.CONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
                            InjectionStatus.INJECTING -> MaterialTheme.colorScheme.primaryContainer
                            InjectionStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                            InjectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (state.status) {
                        InjectionStatus.CONNECTING -> Icons.Rounded.Wifi
                        InjectionStatus.INJECTING -> Icons.Rounded.Sync
                        InjectionStatus.SUCCESS -> Icons.Rounded.CheckCircle
                        InjectionStatus.ERROR -> Icons.Rounded.Error
                        else -> Icons.Rounded.WifiOff
                    },
                    contentDescription = null,
                    tint = when (state.status) {
                        InjectionStatus.CONNECTING -> MaterialTheme.colorScheme.onTertiaryContainer
                        InjectionStatus.INJECTING -> MaterialTheme.colorScheme.onPrimaryContainer
                        InjectionStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                        InjectionStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column {
                Text(
                    text = when (state.status) {
                        InjectionStatus.IDLE -> "Listo para conectar"
                        InjectionStatus.CONNECTING -> "Conectando al dispositivo..."
                        InjectionStatus.INJECTING -> "Inyectando llaves..."
                        InjectionStatus.SUCCESS -> "Conexión exitosa"
                        InjectionStatus.ERROR -> "Error de conexión"
                        else -> "Estado desconocido"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (state.status) {
                        InjectionStatus.IDLE -> "Presiona 'Iniciar Inyección' para comenzar"
                        InjectionStatus.CONNECTING -> "Estableciendo comunicación serial..."
                        InjectionStatus.INJECTING -> "Procesando protocolo FUTUREX..."
                        InjectionStatus.SUCCESS -> "Todas las llaves fueron inyectadas correctamente"
                        InjectionStatus.ERROR -> state.error ?: "Error desconocido durante la inyección"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun InjectionProgressCard(state: KeyInjectionState) {
    if (state.status == InjectionStatus.IDLE) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Progreso de Inyección",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${state.currentStep}/${state.totalSteps}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            LinearProgressIndicator(
                progress = state.progress,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (state.status == InjectionStatus.INJECTING) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Inyectando llave ${state.currentStep} de ${state.totalSteps}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
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
private fun CableDetectionCard(cableConnected: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (cableConnected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else
                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(
            width = 1.5.dp,
            color = if (cableConnected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (cableConnected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
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
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (cableConnected)
                        "🔌 Listo para conectar"
                    else
                        "⚠️ Cable no detectado",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (cableConnected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (cableConnected)
                        "Cable USB conectado y listo"
                    else
                        "Conecte el cable USB para continuar",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cableConnected)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}
