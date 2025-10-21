package com.vigatec.keyreceiver.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vigatec.keyreceiver.viewmodel.InjectionEvent
import java.text.SimpleDateFormat
import java.util.*

/**
 * Feed de inyecciones de llaves - Muestra las últimas llaves inyectadas con animaciones
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun InjectionFeedCard(
    injections: List<InjectionEvent>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Llaves Inyectadas Recientemente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (injections.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${injections.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Divider()

            // Lista de inyecciones con animaciones
            if (injections.isEmpty()) {
                EmptyFeedState()
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    injections.forEach { injection ->
                        key(injection.id) {
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically(
                                    initialOffsetY = { -it },
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing
                                    )
                                ) + fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing
                                    )
                                ),
                                exit = slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing
                                    )
                                ) + fadeOut(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            ) {
                                InjectionItem(injection = injection)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item individual de inyección
 */
@Composable
private fun InjectionItem(injection: InjectionEvent) {
    val (icon, iconColor, badgeColor) = getKeyTypeVisualsConfig(injection.keyType)
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeString = timeFormat.format(Date(injection.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (injection.success)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono y tipo de llave
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    // Row con badges de tipo y algoritmo
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge con tipo de llave
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor
                        ) {
                            Text(
                                text = formatKeyTypeName(injection.keyType),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Badge con algoritmo (AES-256, 3DES-168, etc.)
                        if (injection.algorithm.isNotEmpty()) {
                            val algorithmColor = if (injection.algorithm.startsWith("AES"))
                                Color(0xFF2196F3) // Azul para AES
                            else
                                Color(0xFFFF9800) // Naranja para 3DES

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = algorithmColor
                            ) {
                                Text(
                                    text = injection.algorithm,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Slot: ${injection.slot}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Estado y hora
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (injection.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (injection.success)
                            Color(0xFF4CAF50)
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (injection.success) "Éxito" else "Fallo",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (injection.success)
                            Color(0xFF4CAF50)
                        else
                            MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Estado vacío cuando no hay inyecciones
 */
@Composable
private fun EmptyFeedState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "No hay llaves inyectadas aún",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Las llaves aparecerán aquí cuando se inyecten",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Obtiene la configuración visual (icono, color) según el tipo de llave
 */
private fun getKeyTypeVisualsConfig(keyType: String): Triple<ImageVector, Color, Color> {
    return when {
        keyType.contains("MASTER", ignoreCase = true) || keyType.contains("KEK", ignoreCase = true) -> {
            Triple(Icons.Default.Lock, Color(0xFF9C27B0), Color(0xFF9C27B0))
        }
        keyType.contains("TRANSPORT", ignoreCase = true) || keyType.contains("KTK", ignoreCase = true) -> {
            Triple(Icons.Default.VpnKey, Color(0xFF2196F3), Color(0xFF2196F3))
        }
        keyType.contains("WORKING", ignoreCase = true) || keyType.contains("PIN", ignoreCase = true) ||
        keyType.contains("MAC", ignoreCase = true) || keyType.contains("DATA", ignoreCase = true) -> {
            Triple(Icons.Default.Key, Color(0xFF4CAF50), Color(0xFF4CAF50))
        }
        keyType.contains("DUKPT", ignoreCase = true) -> {
            Triple(Icons.Default.Security, Color(0xFFFF9800), Color(0xFFFF9800))
        }
        else -> {
            Triple(Icons.Default.Key, Color.Gray, Color.Gray)
        }
    }
}

/**
 * Formatea el nombre del tipo de llave para display
 */
private fun formatKeyTypeName(keyType: String): String {
    return when {
        keyType.contains("MASTER_KEY", ignoreCase = true) -> "KEK"
        keyType.contains("TRANSPORT_KEY", ignoreCase = true) -> "KTK"
        keyType.contains("WORKING_PIN_KEY", ignoreCase = true) -> "PIN"
        keyType.contains("WORKING_MAC_KEY", ignoreCase = true) -> "MAC"
        keyType.contains("WORKING_DATA_ENCRYPTION_KEY", ignoreCase = true) -> "DATA"
        keyType.contains("DUKPT", ignoreCase = true) -> "DUKPT"
        else -> keyType.take(10)
    }
}
