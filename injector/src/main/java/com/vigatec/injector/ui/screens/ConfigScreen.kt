package com.vigatec.injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    onBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val userPermissions by permissionProvider.userPermissions.collectAsState()

    LaunchedEffect(currentUsername) {
        viewModel.loadCurrentUser(currentUsername)
        // Cargar permisos del usuario para mostrar opciones de configuración
        permissionProvider.loadPermissions(currentUsername)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "Volver")
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

            // Sección de Configuración de Timeout de Custodios (solo para administradores)
            if (userPermissions.contains(PermissionProvider.MANAGE_USERS)) {
                CustodianTimeoutConfigDropdown(
                    currentTimeoutMinutes = uiState.custodianTimeoutMinutes,
                    onTimeoutChange = { viewModel.saveCustodianTimeout(it) },
                    isSaving = uiState.isSavingTimeout,
                    saveMessage = uiState.timeoutSaveMessage
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
            SystemInfoCard(
                applicationVersion = uiState.applicationVersion,
                databaseVersion = uiState.databaseVersion
            )

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
                    Icons.AutoMirrored.Default.Logout,
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

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Rol:",
                    style = MaterialTheme.typography.bodyMedium
                )
                val (roleLabel, roleColor, contentColor) = when (role) {
                    "ADMIN" -> Triple(
                        "Administrador",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    "OPERATOR" -> Triple(
                        "Operador",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    else -> Triple(
                        "Usuario",
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Surface(
                    color = roleColor,
                    contentColor = contentColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = roleLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
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
fun SystemInfoCard(
    applicationVersion: String = "",
    databaseVersion: String = ""
) {
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
            HorizontalDivider()
            SystemInfoRow(label = "Versión", value = applicationVersion.ifEmpty { "Cargando..." })
            SystemInfoRow(label = "Base de datos", value = databaseVersion.ifEmpty { "Cargando..." })
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

@Composable
fun CustodianTimeoutConfigDropdown(
    currentTimeoutMinutes: Int,
    onTimeoutChange: (Int) -> Unit,
    isSaving: Boolean = false,
    saveMessage: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedTimeout by remember { mutableStateOf(currentTimeoutMinutes) }

    val timeoutOptions = listOf(1, 5, 10, 15, 20, 30, 45, 60)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Título con icono
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Timeout de Custodios",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tiempo máximo de espera",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Dropdown
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$selectedTimeout minutos",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Dropdown menu
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    timeoutOptions.forEach { minutes ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$minutes min",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    if (selectedTimeout == minutes) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                selectedTimeout = minutes
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Botón de guardar
            Button(
                onClick = {
                    onTimeoutChange(selectedTimeout)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedTimeout != currentTimeoutMinutes && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isSaving) "Guardando..." else "Guardar")
            }

            // Mensaje de éxito/error
            saveMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (message.startsWith("Error"))
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (message.startsWith("Error")) Icons.Default.Error else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (message.startsWith("Error"))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (message.startsWith("Error"))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
