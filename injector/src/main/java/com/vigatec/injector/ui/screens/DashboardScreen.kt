package com.vigatec.injector.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vigatec.injector.ui.components.StatCardSkeleton
import com.vigatec.injector.viewmodel.DashboardState
import com.vigatec.injector.viewmodel.DashboardViewModel
import com.vigatec.injector.viewmodel.SystemStats

@Composable
fun DashboardScreen(
    username: String,
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val dashboardState by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { WelcomeCard(username = username) }
        item { ConnectionStatusCard(dashboardState, viewModel) }
        item { 
            if (dashboardState.isLoading) {
                DashboardStatsSkeleton()
            } else {
                DashboardStats(stats = dashboardState.stats)
            }
        }
        item { QuickActionsCard(navController) }
        item { SystemHealthCard(dashboardState) }
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

@Composable
fun WelcomeCard(username: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "¡Bienvenido, $username!",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sistema de inyección de llaves criptográficas para POS.",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
fun DashboardStats(stats: SystemStats) {
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
            StatCard("Perfiles Activos", stats.profilesCount.toString(), Icons.AutoMirrored.Filled.Label, Modifier.weight(1f))
            StatCard("Llaves Almacenadas", stats.keysCount.toString(), Icons.Default.VpnKey, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard("Inyecciones Hoy", stats.injectionsToday.toString(), Icons.Default.BarChart, Modifier.weight(1f))
            StatCard("Usuarios", stats.usersCount.toString(), Icons.Default.People, Modifier.weight(1f))
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
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
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
                Text(text = value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun QuickActionsCard(navController: NavController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Acciones Rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            QuickActionItem("Gestionar Perfiles", Icons.AutoMirrored.Filled.Label, onClick = { /* navController.navigate(...) */ })
            QuickActionItem("Ver Llaves", Icons.Default.VpnKey, onClick = { /* navController.navigate(...) */ })
            QuickActionItem("Conexión POS", Icons.Default.Usb, onClick = { /* navController.navigate(...) */ })
            QuickActionItem("Ceremonia Crypto", Icons.Default.Security, isAdmin = true, onClick = { navController.navigate("ceremony") })
            QuickActionItem("Usuarios", Icons.Default.People, isAdmin = true, onClick = { /* navController.navigate(...) */ })
        }
    }
}

@Composable
fun QuickActionItem(title: String, icon: ImageVector, isAdmin: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
            Icon(imageVector = icon, contentDescription = title)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ConnectionStatusCard(dashboardState: DashboardState, viewModel: DashboardViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dashboardState.isSubPosConnected) 
                Color(0xFF4CAF50).copy(alpha = 0.1f) 
            else 
                Color(0xFFF44336).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Indicador de luz verde/roja
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = if (dashboardState.isSubPosConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
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
            
            // Botón de control de polling
            Button(
                onClick = {
                    if (dashboardState.isPollingActive) {
                        viewModel.stopPolling()
                    } else {
                        viewModel.startPolling()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (dashboardState.isPollingActive) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (dashboardState.isPollingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (dashboardState.isPollingActive) "Detener" else "Iniciar"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (dashboardState.isPollingActive) "Detener" else "Iniciar")
            }
        }
    }
}

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

@Composable
fun HealthItem(name: String, status: String, details: String, isHealthy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(if (isHealthy) Color(0xFF4CAF50) else Color(0xFFF44336), CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(text = details, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            text = status,
            color = if (isHealthy) Color(0xFF4CAF50) else Color(0xFFF44336),
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