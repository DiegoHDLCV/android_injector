// Archivo: com/vigatec/android_injector/ui/screens/InjectedKeysScreen.kt

package com.vigatec.android_injector.ui.screens

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    val keys by viewModel.keys.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val showDeleteModal by viewModel.showDeleteModal.collectAsState()
    val selectedKeyForDeletion by viewModel.selectedKeyForDeletion.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { message ->
            // Aquí podrías mostrar un Snackbar si tienes un estado para ello
            Log.d("InjectedKeysScreen", "Snackbar message: $message")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Llaves Inyectadas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshKeys() },
                        enabled = !loading
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refrescar"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearAllKeys() },
                        enabled = !loading && keys.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Limpiar Todo"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (loading) {
                InjectedKeysSkeletonScreen()
            } else if (keys.isEmpty()) {
                EmptyKeysScreen()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = keys,
                        key = { it.id }
                    ) { key ->
                        KeyInfoCard(
                            key = key,
                            onDeleteClick = { viewModel.onDeleteKey(key) }
                        )
                    }
                }
            }
        }
    }

    // Modal de confirmación de eliminación
    if (showDeleteModal && selectedKeyForDeletion != null) {
        ConfirmationDialog(
            icon = Icons.Default.Delete,
            title = "Eliminar Llave",
            text = "¿Estás seguro de que quieres eliminar la llave con KCV ${selectedKeyForDeletion!!.kcv}? Esta acción no se puede deshacer.",
            onConfirm = {
                viewModel.confirmDeleteKey()
            },
            onDismiss = {
                viewModel.dismissDeleteModal()
            }
        )
    }
}

@Composable
fun InjectedKeysSkeletonScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(6) {
            InjectedKeyCardSkeleton()
        }
    }
}

/**
 * Componente de esqueleto con efecto shimmer para mostrar mientras se cargan datos
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(shape)
            .background(brush)
    )
}

/**
 * Esqueleto para las tarjetas de llaves inyectadas
 */
@Composable
fun InjectedKeyCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // Tipo de llave esqueleto
                    SkeletonBox(
                        modifier = Modifier.width(100.dp),
                        height = 14.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Algoritmo esqueleto
                    SkeletonBox(
                        modifier = Modifier.width(80.dp),
                        height = 12.dp
                    )
                }

                // Estado esqueleto
                SkeletonBox(
                    modifier = Modifier.width(60.dp),
                    height = 24.dp,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Detalles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SkeletonBox(
                            modifier = Modifier.width(60.dp),
                            height = 12.dp
                        )
                        SkeletonBox(
                            modifier = Modifier.width(100.dp),
                            height = 12.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyKeysScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.VpnKey,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No hay llaves inyectadas",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Las llaves que inyectes en dispositivos POS aparecerán aquí para su gestión y auditoría.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header con tipo de llave y estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = key.keyType,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = key.keyAlgorithm,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                StatusChip(
                    text = key.status,
                    color = statusColor,
                    isDeleting = key.status == "DELETING"
                )
            }
            
            // Detalles de la llave
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyDetailRow(
                    icon = Icons.Default.Numbers,
                    label = "Slot",
                    value = "#${key.keySlot}",
                    isEnabled = isEnabled
                )
                
                KeyDetailRow(
                    icon = Icons.Default.Fingerprint,
                    label = "KCV",
                    value = key.kcv.uppercase(),
                    isEnabled = isEnabled
                )
                
                KeyDetailRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Fecha",
                    value = remember {
                        SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).format(Date(key.injectionTimestamp))
                    },
                    isEnabled = isEnabled
                )
            }

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
fun KeyDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    isEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color.Gray
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
    }
}

@Composable
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