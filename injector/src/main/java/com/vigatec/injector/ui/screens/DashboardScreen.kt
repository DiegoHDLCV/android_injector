package com.vigatec.injector.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vigatec.injector.ui.components.StatCardSkeleton
import com.vigatec.injector.viewmodel.DashboardState
import com.vigatec.injector.viewmodel.DashboardViewModel
import com.vigatec.injector.viewmodel.SystemStats
import com.vigatec.communication.polling.CommLogEntry

private data class QuickActionMeta(
    val title: String,
    val icon: ImageVector,
    val isAdmin: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    @Suppress("UNUSED_PARAMETER") username: String,
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val dashboardStateState = viewModel.state.collectAsState()
    val dashboardState = dashboardStateState.value
    
    // Observar cambios en injectionsToday para depuración
    LaunchedEffect(dashboardState.stats.injectionsToday) {
        Log.d("DashboardScreen", "🔄 UI: injectionsToday cambió a ${dashboardState.stats.injectionsToday}")
        Log.d("DashboardScreen", "  - Stats completo: profiles=${dashboardState.stats.profilesCount}, keys=${dashboardState.stats.keysCount}, injections=${dashboardState.stats.injectionsToday}, users=${dashboardState.stats.usersCount}")
    }
    
    // Observar cambios en el estado completo para depuración
    LaunchedEffect(dashboardState) {
        Log.d("DashboardScreen", "🔄 UI: DashboardState cambió")
        Log.d("DashboardScreen", "  - isLoading: ${dashboardState.isLoading}")
        Log.d("DashboardScreen", "  - stats.injectionsToday: ${dashboardState.stats.injectionsToday}")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        //item(key = "connection") { ConnectionStatusCard(dashboardState, viewModel) }
        item(key = "stats") { 
            if (dashboardState.isLoading) {
                DashboardStatsSkeleton()
            } else {
                DashboardStats(stats = dashboardState.stats)
            }
        }
    }
}

@Composable
fun DashboardStatsSkeleton() {
    Column {
        // Título esqueleto
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(20.dp)
                .background(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(vertical = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Primera fila de estadísticas esqueleto
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCardSkeleton(modifier = Modifier.weight(1f))
            StatCardSkeleton(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Segunda fila de estadísticas esqueleto
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCardSkeleton(modifier = Modifier.weight(1f))
            StatCardSkeleton(modifier = Modifier.weight(1f))
        }
    }
}

@Suppress("UNUSED")
@Composable
fun WelcomeCard(username: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        val primaryTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        val secondaryTint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        val gradient = Brush.linearGradient(listOf(primaryTint, secondaryTint))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradient, shape = RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "¡Bienvenido, $username!",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sistema de inyección de llaves criptográficas para POS.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }
}

@Composable
fun DashboardStats(stats: SystemStats) {
    // Log cuando el composable se recomponiendo
    LaunchedEffect(stats.injectionsToday) {
        Log.d("DashboardStats", "🎨 Composable DashboardStats recomponiendo")
        Log.d("DashboardStats", "  - injectionsToday: ${stats.injectionsToday}")
    }
    
    Column {
        Text(
            text = "Estadísticas Rápidas",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(Modifier.weight(1f), "Perfiles Activos", stats.profilesCount.toString(), Icons.AutoMirrored.Filled.Label)
            StatCard(Modifier.weight(1f), "Llaves Almacenadas", stats.keysCount.toString(), Icons.Default.VpnKey)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(Modifier.weight(1f), "Inyecciones Hoy", stats.injectionsToday.toString(), Icons.Default.BarChart)
            StatCard(Modifier.weight(1f), "Usuarios", stats.usersCount.toString(), Icons.Default.People)
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
                Text(text = value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Suppress("UNUSED")
@Composable
fun QuickActionsCard(navController: NavController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Acciones Rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            val actions = remember(navController) {
                listOf(
                    QuickActionMeta("Gestionar Perfiles", Icons.AutoMirrored.Filled.Label, false) { /* navController.navigate(...) */ },
                    QuickActionMeta("Ver Llaves", Icons.Default.VpnKey, false) { /* navController.navigate(...) */ },
                    QuickActionMeta("Conexión POS", Icons.Default.Usb, false) { /* navController.navigate(...) */ },
                    QuickActionMeta("Ceremonia Crypto", Icons.Default.Security, true) { navController.navigate("ceremony") },
                    QuickActionMeta("Usuarios", Icons.Default.People, true) { /* navController.navigate(...) */ }
                )
            }

            for (i in actions.indices step 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val a0 = actions[i]
                    QuickActionItem(
                        title = a0.title,
                        icon = a0.icon,
                        onClick = a0.onClick,
                        modifier = Modifier.weight(1f),
                        isAdmin = a0.isAdmin
                    )
                    if (i + 1 < actions.size) {
                        val a1 = actions[i + 1]
                        QuickActionItem(
                            title = a1.title,
                            icon = a1.icon,
                            onClick = a1.onClick,
                            modifier = Modifier.weight(1f),
                            isAdmin = a1.isAdmin
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun QuickActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if(isAdmin) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if(isAdmin) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, fontWeight = FontWeight.Medium)
        }
    }
}

@Suppress("UNUSED")
@Composable
fun ConnectionStatusCard(dashboardState: DashboardState, viewModel: DashboardViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = run {
                val target = if (dashboardState.isSubPosConnected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else
                    MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                animateColorAsState(targetValue = target, label = "connectionContainer").value
            }
        )
    ) {
        val isTogglingState = remember { mutableStateOf(false) }
        LaunchedEffect(dashboardState.isPollingActive, dashboardState.isSubPosConnected) {
            isTogglingState.value = false
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = animateColorAsState(
                                targetValue = if (dashboardState.isSubPosConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                label = "connectionDot"
                            ).value,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Estado de Conexión SubPOS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (dashboardState.isSubPosConnected)
                            "SubPOS conectado y respondiendo"
                        else
                            "SubPOS no conectado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Button(
                onClick = {
                    isTogglingState.value = true
                    if (dashboardState.isPollingActive) {
                        viewModel.stopPolling()
                    } else {
                        viewModel.startPolling()
                    }
                },
                enabled = !isTogglingState.value,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isTogglingState.value -> MaterialTheme.colorScheme.surfaceVariant
                        dashboardState.isPollingActive -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                if (isTogglingState.value) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Procesando…")
                } else {
                    Icon(
                        imageVector = if (dashboardState.isPollingActive) Icons.Default.LinkOff else Icons.Default.Link,
                        contentDescription = if (dashboardState.isPollingActive) "Desconectar" else "Conectar"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (dashboardState.isPollingActive) "Desconectar" else "Conectar")
                }
            }
        }
    }
}

@Suppress("UNUSED")
@Composable
fun SystemHealthCard(dashboardState: DashboardState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Salud del Sistema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            HealthItem(
                "Conexión SubPOS", 
                if (dashboardState.isSubPosConnected) "Conectado" else "Desconectado", 
                if (dashboardState.isPollingActive) "Polling activo" else "Polling inactivo", 
                dashboardState.isSubPosConnected
            )
            HealthItem("Autenticación", "Activa", "Sesión válida", true)
            HealthItem("Comunicación API", "Operativa", "Backend disponible", true)
        }
    }
}

@Suppress("UNUSED")
@Composable
fun CommLogsCard(entries: List<CommLogEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Logs de Comunicación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text("Sin registros aún…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            } else {
                // Mostrar últimos 50 para rendimiento
                val last = entries.takeLast(50).asReversed()
                last.forEach { e ->
                    val color = when (e.level) {
                        "E" -> MaterialTheme.colorScheme.error
                        "W" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = "[${e.level}] ${e.tag}: ${e.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
fun HealthItem(name: String, status: String, details: String, isHealthy: Boolean) {
    val dotColorState = animateColorAsState(
        targetValue = if (isHealthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        label = "healthDot"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(dotColorState.value, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(text = details, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            text = status,
            color = dotColorState.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    // Nota: El preview no funcionará con NavController real.
    // Para un preview completo, se necesitaría un NavController de prueba.
    MaterialTheme {
       // DashboardScreen("Admin", rememberNavController())
    }
}