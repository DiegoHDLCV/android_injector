package com.vigatec.injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.persistence.entities.User
import com.vigatec.injector.viewmodel.UserManagementViewModel
import com.vigatec.injector.ui.components.PasswordTextField
import com.vigatec.injector.ui.components.PasswordConfirmationFields
import com.vigatec.injector.ui.components.RoleSelector
import com.vigatec.injector.util.PermissionsCatalog
import com.vigatec.injector.util.rememberNavigationDebouncer

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
    val navigationDebouncer = rememberNavigationDebouncer()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Usuarios") },
                navigationIcon = {
                    IconButton(onClick = { navigationDebouncer.onClick(onBack) }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Action buttons
            UserManagementActionButtons(
                onCreateUser = { showCreateUserDialog = true }
            )
            
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(5) {
                            com.vigatec.injector.ui.components.UserListItemSkeleton(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
fun UserManagementActionButtons(
    onCreateUser: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCreateUser) {
            Icon(Icons.Default.PersonAdd, "Crear usuario")
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

                val (roleLabel, roleColor, roleContentColor) = when (user.role) {
                    "SUPERVISOR" -> Triple(
                        "Supervisor",
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    "OPERATOR" -> Triple(
                        "Operador",
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    else -> Triple(
                        user.role,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Surface(
                    color = roleColor,
                    contentColor = roleContentColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = roleLabel,
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
    allPermissions: List<com.vigatec.persistence.entities.Permission>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, List<String>) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("OPERATOR") }
    var selectedPermissions by remember { mutableStateOf(PermissionsCatalog.OPERATOR_DEFAULT_PERMISSION_IDS) }

    LaunchedEffect(role) {
        when (role) {
            "OPERATOR" -> selectedPermissions = PermissionsCatalog.OPERATOR_DEFAULT_PERMISSION_IDS
            "SUPERVISOR" -> selectedPermissions = PermissionsCatalog.SYSTEM_PERMISSION_IDS
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nuevo Usuario") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Contraseña",
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Indicador de fortaleza de contraseña
                PasswordStrengthIndicator(passphrase = password)

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombre completo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de rol
                RoleSelector(
                    selectedRole = role,
                    onRoleChange = { role = it }
                )
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
    allPermissions: List<com.vigatec.persistence.entities.Permission>,
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

    LaunchedEffect(role) {
        when (role) {
            "OPERATOR" -> selectedPermissions = PermissionsCatalog.OPERATOR_DEFAULT_PERMISSION_IDS
            "SUPERVISOR" -> selectedPermissions = PermissionsCatalog.SYSTEM_PERMISSION_IDS
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Usuario") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
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
                RoleSelector(
                    selectedRole = role,
                    onRoleChange = { role = it }
                )

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar Contraseña") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                PasswordConfirmationFields(
                    password = newPassword,
                    onPasswordChange = { newPassword = it },
                    confirmPassword = confirmPassword,
                    onConfirmPasswordChange = { confirmPassword = it },
                    passwordLabel = "Nueva contraseña",
                    confirmLabel = "Confirmar contraseña",
                    modifier = Modifier.fillMaxWidth(),
                    showMismatchError = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Indicador de fortaleza de contraseña
                PasswordStrengthIndicator(passphrase = newPassword)
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

@Composable
private fun PasswordStrengthIndicator(passphrase: String) {
    val hasMinLength = passphrase.length >= 16
    val hasUpperCase = passphrase.any { it.isUpperCase() }
    val hasLowerCase = passphrase.any { it.isLowerCase() }
    val hasDigit = passphrase.any { it.isDigit() }
    val hasSpecial = passphrase.any { !it.isLetterOrDigit() }

    val strength = listOf(hasMinLength, hasUpperCase, hasLowerCase, hasDigit, hasSpecial).count { it }
    val strengthText = when {
        strength < 3 -> "Débil"
        strength < 4 -> "Media"
        strength < 5 -> "Fuerte"
        else -> "Muy fuerte"
    }
    val strengthColor = when {
        strength < 3 -> MaterialTheme.colorScheme.error
        strength < 4 -> Color(0xFFFFA726)
        strength < 5 -> Color(0xFF66BB6A)
        else -> Color(0xFF43A047)
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "Fortaleza: ",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = strengthText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = strengthColor
            )
        }

        LinearProgressIndicator(
            progress = { strength / 5f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = strengthColor,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.padding(start = 8.dp)) {
            CheckItem("Mínimo 16 caracteres", hasMinLength)
            CheckItem("Mayúsculas", hasUpperCase)
            CheckItem("Minúsculas", hasLowerCase)
            CheckItem("Números", hasDigit)
            CheckItem("Caracteres especiales", hasSpecial)
        }
    }
}

@Composable
private fun CheckItem(text: String, checked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) Color(0xFF43A047) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
