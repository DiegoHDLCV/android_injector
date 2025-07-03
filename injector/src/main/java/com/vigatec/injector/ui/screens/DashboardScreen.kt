package com.vigatec.injector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.vigatec.injector.viewmodel.DashboardViewModel
import com.vigatec.injector.viewmodel.SystemStats

@Composable
fun DashboardScreen(
    username: String,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val systemStats by viewModel.systemStats.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { WelcomeCard(username = username) }
        item { DashboardStats(stats = systemStats) }
        item { SystemStatusCard() }
        item { QuickActionsCard() }
        item { SystemHealthCard() }
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
            StatCard("Perfiles Activos", stats.profilesCount.toString(), Icons.Default.Label, Modifier.weight(1f))
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
fun SystemStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Estado del Sistema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            StatusItem("Frontend", "Operativo", Color.Green.copy(alpha = 0.7f))
            StatusItem("Backend", "Conectado", Color.Green.copy(alpha = 0.7f))
            StatusItem("Base de Datos", "Sincronizada", Color.Green.copy(alpha = 0.7f))
            StatusItem("Seguridad", "Activa", Color.Blue.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun StatusItem(label: String, status: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween, // <<< CORREGIDO
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QuickActionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Acciones Rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            QuickActionItem("Gestionar Perfiles", Icons.Default.Label)
            QuickActionItem("Ver Llaves", Icons.Default.VpnKey)
            QuickActionItem("Conexión POS", Icons.Default.Usb)
            QuickActionItem("Ceremonia Crypto", Icons.Default.Security, isAdmin = true)
            QuickActionItem("Usuarios", Icons.Default.People, isAdmin = true)
        }
    }
}

@Composable
fun QuickActionItem(title: String, icon: ImageVector, isAdmin: Boolean = false) {
    Button(
        onClick = { /* TODO */ },
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
fun SystemHealthCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Salud del Sistema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            HealthItem("Conexión POS", "Conectado", "Puerto: COM3", true)
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
        horizontalArrangement = Arrangement.SpaceBetween // <<< CORREGIDO
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(if (isHealthy) Color.Green else Color.Red, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(text = details, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            text = status,
            color = if (isHealthy) Color.Green else Color.Red,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    MaterialTheme {
        DashboardScreen("Admin")
    }
}