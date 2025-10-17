package com.vigatec.injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.injector.util.PermissionProvider
import com.vigatec.injector.viewmodel.ConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    currentUsername: String,
    viewModel: ConfigViewModel = hiltViewModel(),
    permissionProvider: PermissionProvider,
    onNavigateToLogs: () -> Unit,
    onNavigateToUserManagement: () -> Unit,
    onNavigateToTmsConfig: () -> Unit = {},
    onBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val userPermissions by permissionProvider.userPermissions.collectAsState()

    LaunchedEffect(currentUsername) {
        viewModel.loadCurrentUser(currentUsername)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Información del usuario actual
            uiState.currentUser?.let { user ->
                UserInfoCard(
                    username = user.username,
                    fullName = user.fullName,
                    role = user.role
                )
            }

            // Sección de TMS
            if (userPermissions.contains(PermissionProvider.TMS_CONFIG)) {
                ConfigOptionCard(
                    title = "Terminal Management System (TMS)",
                    description = "Configurar conexión y parámetros del sistema de gestión de terminales",
                    icon = Icons.Default.Cloud,
                    onClick = onNavigateToTmsConfig
                )
            }

            // Sección de Logs
            if (userPermissions.contains(PermissionProvider.VIEW_LOGS)) {
                ConfigOptionCard(
                    title = "Logs de Inyección",
                    description = "Ver historial de operaciones de inyección",
                    icon = Icons.Default.History,
                    onClick = onNavigateToLogs
                )
            }

            // Sección de Gestión de Usuarios
            if (userPermissions.contains(PermissionProvider.MANAGE_USERS)) {
                ConfigOptionCard(
                    title = "Gestión de Usuarios",
                    description = "Crear, editar y administrar usuarios del sistema",
                    icon = Icons.Default.Group,
                    onClick = onNavigateToUserManagement
                )
            }

            // Información del sistema
            SystemInfoCard()

            Spacer(modifier = Modifier.height(16.dp))

            // Botón de cerrar sesión
            Button(
                onClick = {
                    viewModel.logout() // Desactivar usuario antes de navegar
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar Sesión")
            }
        }
    }

    // Mostrar mensaje de error si existe
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Aquí podrías mostrar un Snackbar con el error
            viewModel.clearErrorMessage()
        }
    }
}

@Composable
fun UserInfoCard(
    username: String,
    fullName: String,
    role: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = fullName.ifEmpty { username },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Rol:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = if (role == "ADMIN")
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (role == "ADMIN") "Administrador" else "Usuario",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (role == "ADMIN")
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SystemInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Información del Sistema",
                style = MaterialTheme.typography.titleMedium
            )
            Divider()
            SystemInfoRow(label = "Versión", value = "1.0.0")
            SystemInfoRow(label = "Base de datos", value = "v2")
        }
    }
}

@Composable
fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
