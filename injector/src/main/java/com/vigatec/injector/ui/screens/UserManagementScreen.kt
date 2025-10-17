package com.vigatec.injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.viewmodel.UserManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateUserDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<User?>(null) }
    var userToEdit by remember { mutableStateOf<User?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Usuarios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateUserDialog = true }) {
                        Icon(Icons.Default.PersonAdd, "Crear usuario")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.users) { user ->
                    UserListItem(
                        user = user,
                        onToggleActive = { viewModel.toggleUserActiveStatus(user) },
                        onEdit = { userToEdit = user },
                        onDelete = { userToDelete = user }
                    )
                }
            }
        }
    }

    // Diálogo para crear usuario
    if (showCreateUserDialog) {
        CreateUserDialog(
            allPermissions = uiState.allPermissions,
            onDismiss = { showCreateUserDialog = false },
            onConfirm = { username, password, fullName, role, selectedPermissions ->
                viewModel.createUser(username, password, fullName, role, selectedPermissions)
                showCreateUserDialog = false
            }
        )
    }

    // Diálogo para editar usuario
    userToEdit?.let { user ->
        EditUserDialog(
            user = user,
            allPermissions = uiState.allPermissions,
            viewModel = viewModel,
            onDismiss = { userToEdit = null },
            onConfirm = { updatedUser, selectedPermissions ->
                viewModel.updateUserWithPermissions(updatedUser, selectedPermissions)
                userToEdit = null
            },
            onChangePassword = { newPassword ->
                viewModel.updateUserPassword(user.id, newPassword)
                userToEdit = null
            }
        )
    }

    // Diálogo de confirmación para eliminar
    userToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Eliminar Usuario") },
            text = { Text("¿Estás seguro de que deseas eliminar al usuario '${user.username}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteUser(user)
                        userToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Mostrar mensajes
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }
}

@Composable
fun UserListItem(
    user: User,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.fullName.ifEmpty { user.username },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${user.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (user.role == "ADMIN")
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (user.role == "ADMIN") "Admin" else "Usuario",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Switch activo/inactivo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Switch(
                        checked = user.isActive,
                        onCheckedChange = { onToggleActive() }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (user.isActive) "Activo" else "Inactivo",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Botones de acción
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Editar")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun CreateUserDialog(
    allPermissions: List<com.vigatec.injector.data.local.entity.Permission>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, List<String>) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("USER") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedPermissions by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nuevo Usuario") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                "Mostrar contraseña"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombre completo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de rol
                Column {
                    Text("Rol:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = role == "USER",
                                onClick = { role = "USER" }
                            )
                            Text("Usuario")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = role == "ADMIN",
                                onClick = { role = "ADMIN" }
                            )
                            Text("Administrador")
                        }
                    }
                }
                
                // Sección de permisos
                if (role == "ADMIN") {
                    // Para ADMIN, mostrar mensaje informativo
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "Los administradores tienen todos los permisos automáticamente",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    // Para USER, mostrar checkboxes de permisos
                    Column {
                        Text(
                            "Permisos:",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                items(allPermissions) { permission ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedPermissions.contains(permission.id),
                                            onCheckedChange = { checked ->
                                                selectedPermissions = if (checked) {
                                                    selectedPermissions + permission.id
                                                } else {
                                                    selectedPermissions - permission.id
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.padding(start = 8.dp)) {
                                            Text(
                                                text = permission.name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = permission.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username, password, fullName, role, selectedPermissions.toList()) },
                enabled = username.isNotBlank() && password.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun EditUserDialog(
    user: User,
    allPermissions: List<com.vigatec.injector.data.local.entity.Permission>,
    viewModel: UserManagementViewModel,
    onDismiss: () -> Unit,
    onConfirm: (User, List<String>) -> Unit,
    onChangePassword: (String) -> Unit
) {
    var fullName by remember { mutableStateOf(user.fullName) }
    var role by remember { mutableStateOf(user.role) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var selectedPermissions by remember { mutableStateOf(setOf<String>()) }
    var permissionsLoaded by remember { mutableStateOf(false) }
    
    // Cargar permisos del usuario al abrir el diálogo
    LaunchedEffect(user.id) {
        val userPerms = viewModel.getUserPermissions(user.id)
        selectedPermissions = userPerms.map { it.id }.toSet()
        permissionsLoaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Usuario") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = user.username,
                    onValueChange = {},
                    label = { Text("Nombre de usuario") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombre completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de rol
                Column {
                    Text("Rol:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = role == "USER",
                                onClick = { role = "USER" }
                            )
                            Text("Usuario")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = role == "ADMIN",
                                onClick = { role = "ADMIN" }
                            )
                            Text("Administrador")
                        }
                    }
                }
                
                // Sección de permisos
                if (permissionsLoaded) {
                    if (role == "ADMIN") {
                        // Para ADMIN, mostrar mensaje informativo
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = "Los administradores tienen todos los permisos automáticamente",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    } else {
                        // Para USER, mostrar checkboxes de permisos
                        Column {
                            Text(
                                "Permisos:",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    items(allPermissions) { permission ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = selectedPermissions.contains(permission.id),
                                                onCheckedChange = { checked ->
                                                    selectedPermissions = if (checked) {
                                                        selectedPermissions + permission.id
                                                    } else {
                                                        selectedPermissions - permission.id
                                                    }
                                                }
                                            )
                                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                                Text(
                                                    text = permission.name,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = permission.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { showPasswordDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cambiar Contraseña")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(user.copy(fullName = fullName, role = role), selectedPermissions.toList())
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { newPassword ->
                onChangePassword(newPassword)
                showPasswordDialog = false
            }
        )
    }
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar Contraseña") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nueva contraseña") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                "Mostrar contraseña"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmar contraseña") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                    Text(
                        text = "Las contraseñas no coinciden",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newPassword) },
                enabled = newPassword.isNotBlank() && newPassword == confirmPassword
            ) {
                Text("Cambiar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
