package com.vigatec.injector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.persistence.entities.InjectedKeyEntity
import com.vigatec.injector.viewmodel.KeyVaultViewModel
import com.vigatec.injector.util.PermissionManager
import com.vigatec.injector.BuildConfig
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun KeyVaultScreen(
    viewModel: KeyVaultViewModel = hiltViewModel(),
    onNavigateToExportImport: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // Verificar si hay KEK Storage oculta
    val hasHiddenKEKStorage = !state.showKEKStorage && state.isAdmin

    Scaffold(
        topBar = {
            KeyVaultTopBar(
                onRefresh = { viewModel.loadKeys() },
                onClearAll = { viewModel.onShowClearAllConfirmation() },
                onImportTestKeys = { viewModel.onImportTestKeys() },
                onNavigateToExportImport = onNavigateToExportImport,
                loading = state.loading,
                isAdmin = state.isAdmin,
                hasHiddenKEKStorage = hasHiddenKEKStorage,
                onShowKEKStoragePasswordDialog = { viewModel.onShowKEKStoragePasswordDialog() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Indicador de rol del usuario
            val (roleBackground, roleTextColor, roleLabel) = when {
                state.isAdmin || state.userRole == PermissionManager.ROLE_SUPERVISOR -> Triple(
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    MaterialTheme.colorScheme.tertiary,
                    "👤 SUPERVISOR"
                )
                state.userRole == PermissionManager.ROLE_OPERATOR -> Triple(
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                    MaterialTheme.colorScheme.tertiary,
                    "👤 OPERADOR"
                )
                else -> Triple(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "👤 USUARIO"
                )
            }

            Surface(
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                color = roleBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = roleTextColor
                    )
                }
            }
            
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.keysWithProfiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay llaves almacenadas.", color = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 300.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.keysWithProfiles) { keyWithProfiles ->
                            KeyCard(
                                key = keyWithProfiles.key,
                                assignedProfiles = keyWithProfiles.assignedProfiles,
                                onDelete = { viewModel.onShowDeleteModal(it) },
                                onToggleKTK = { viewModel.toggleKeyAsKTK(it) },
                                isAdmin = state.isAdmin,
                                onHideKEKStorage = { viewModel.hideKEKStorage() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showDeleteModal && state.selectedKey != null) {
        DeleteKeyDialog(
            key = state.selectedKey!!,
            onConfirm = {
                viewModel.onDeleteKey(it)
                viewModel.onDismissDeleteModal()
            },
            onDismiss = { viewModel.onDismissDeleteModal() }
        )
    }

    if (state.showClearAllConfirmation) {
        ClearAllKeysDialog(
            onConfirm = { viewModel.onConfirmClearAllKeys() },
            onDismiss = { viewModel.onDismissClearAllConfirmation() }
        )
    }

    if (state.showImportJsonDialog) {
        ImportJsonDialog(
            onImport = { jsonContent -> 
                viewModel.importFromJsonContent(jsonContent)
                viewModel.onDismissImportJsonDialog()
            },
            onDismiss = { viewModel.onDismissImportJsonDialog() }
        )
    }

    // Diálogo para pedir contraseña antes de mostrar KEK Storage
    if (state.showKEKStoragePasswordDialog) {
        KEKStoragePasswordDialog(
            passwordError = state.kekStoragePasswordError,
            onPasswordEntered = { password ->
                viewModel.verifyAdminPasswordAndShowKEKStorage(password)
            },
            onDismiss = { viewModel.onDismissKEKStoragePasswordDialog() }
        )
    }

    // Diálogo de validación de eliminación
    if (state.showKeyDeletionValidationDialog && state.keyDeletionValidation != null) {
        KeyDeletionValidationDialog(
            validation = state.keyDeletionValidation!!,
            onDismiss = { viewModel.onDismissKeyDeletionValidationDialog() }
        )
    }

    // Diálogo de validación de eliminación de todas las llaves
    if (state.showMultipleKeysDeletionValidationDialog && state.multipleKeysDeletionValidation != null) {
        MultipleKeysDeletionValidationDialog(
            validation = state.multipleKeysDeletionValidation!!,
            onDismiss = { viewModel.onDismissMultipleKeysDeletionValidationDialog() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyVaultTopBar(
    onRefresh: () -> Unit,
    onClearAll: () -> Unit,
    onImportTestKeys: () -> Unit,
    onNavigateToExportImport: () -> Unit,
    loading: Boolean,
    isAdmin: Boolean,
    hasHiddenKEKStorage: Boolean = false,
    onShowKEKStoragePasswordDialog: () -> Unit = {}
) {
    TopAppBar(
        title = { Text("Almacén de Llaves", fontWeight = FontWeight.Bold) },
        actions = {
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
            }
            // Mostrar indicador de KEK Storage oculta si existe
            if (isAdmin && hasHiddenKEKStorage) {
                IconButton(
                    onClick = onShowKEKStoragePasswordDialog,
                    enabled = !loading
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = "KEK Storage oculta - Toca para mostrar",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            // Solo admins pueden acceder a exportar/importar
            if (isAdmin) {
                @Suppress("KotlinConstantConditions")
                val isDevFlavor = BuildConfig.FLAVOR == "dev"
                IconButton(
                    onClick = onNavigateToExportImport,
                    enabled = !loading
                ) {
                    Icon(Icons.Default.ImportExport, contentDescription = "Exportar/Importar")
                }
                @Suppress("KotlinConstantConditions")
                if (isDevFlavor) {
                    IconButton(
                        onClick = onImportTestKeys,
                        enabled = !loading
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Importar Llaves de Prueba")
                    }
                }
                IconButton(onClick = onClearAll, enabled = !loading) {
                    Icon(Icons.Default.Delete, contentDescription = "Limpiar Almacén")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@Composable
fun KeyCard(
    key: InjectedKeyEntity,
    assignedProfiles: List<String> = emptyList(),
    onDelete: (InjectedKeyEntity) -> Unit,
    onToggleKTK: (InjectedKeyEntity) -> Unit,
    isAdmin: Boolean = false,
    onHideKEKStorage: (() -> Unit)? = null
) {
    val isCeremonyKey = key.keyType == "CEREMONY_KEY"
    val isKEKStorage = key.isKEKStorage() // KEK creada en ceremonia
    val isKTK = key.isKEK && !isKEKStorage // KTK marcada desde almacén
    val detectedAlgorithm = detectKeyAlgorithm(key)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isKEKStorage -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                isKTK -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                isCeremonyKey -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = "Key",
                    tint = when {
                        isKEKStorage -> MaterialTheme.colorScheme.tertiary
                        isKTK -> MaterialTheme.colorScheme.secondary
                        isCeremonyKey -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = key.kcv,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Badge de tipo de algoritmo
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = detectedAlgorithm,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        // Badge KEK Storage
                        if (isKEKStorage) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    text = "KEK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        // Badge KTK
                        if (isKTK) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Text(
                                    text = "KTK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    // Mostrar nombre personalizado si existe
                    if (key.customName.isNotEmpty()) {
                        Text(
                            text = key.customName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            // Solo mostrar detalles técnicos para llaves que NO son de ceremonia
            if (!isCeremonyKey) {
                // Solo mostrar slot si es positivo (slots reales)
                if (key.keySlot >= 0) {
                    Text("Slot: ${key.keySlot}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Tipo: ${key.keyType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Algoritmo: ${key.keyAlgorithm}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // Para llaves de ceremonia, mostrar información relevante
                // Solo mostrar "Origen" para llaves operacionales (no para KEK Storage)
                if (!isKEKStorage) {
                    Text("Origen: Ceremonia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val keyLengthBytes = when {
                    key.encryptedKeyData.isNotEmpty() -> key.encryptedKeyData.length / 2
                    key.isLegacy() -> {
                        @Suppress("DEPRECATION")
                        key.keyData.length / 2
                    }
                    else -> 0
                }
                Text("Longitud: ${keyLengthBytes} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Mostrar estado SOLO si es KTK (no para KEK Storage)
                if (isKTK) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Estado: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (key.status) {
                                "ACTIVE" -> Color(0xFF4CAF50)
                                "EXPORTED" -> Color(0xFF2196F3)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = key.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Mostrar los perfiles asignados (solo para llaves operacionales, no para KEK Storage)
            if (!isKEKStorage) {
                Text(
                    text = if (assignedProfiles.isNotEmpty())
                        "Perfil${if (assignedProfiles.size > 1) "es" else ""}: ${assignedProfiles.joinToString(", ")}"
                    else
                        "Sin asignar",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (assignedProfiles.isNotEmpty())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Fecha: ${formatDate(key.injectionTimestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Botones de acciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón "Marcar como KTK" / "Quitar KTK" - Disponible para todos si NO es KEK Storage
                if (!isKEKStorage) {
                    OutlinedButton(
                        onClick = { onToggleKTK(key) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isKTK) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (isKTK) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isKTK) "Quitar KTK" else "Marcar KTK", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Botón Ocultar - Solo para KEK Storage
                if (isKEKStorage && onHideKEKStorage != null) {
                    OutlinedButton(
                        onClick = onHideKEKStorage,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Ocultar", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Botón Eliminar - Solo para administradores
                if (isAdmin) {
                    Button(
                        onClick = { onDelete(key) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Eliminar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ImportJsonDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var jsonContent by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📥 Importar Llaves desde JSON") },
        text = {
            Column {
                Text(
                    "Pega el contenido del archivo JSON generado por el script:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = jsonContent,
                    onValueChange = {
                        jsonContent = it
                        showError = false
                    },
                    label = { Text("Contenido JSON") },
                    placeholder = { Text("Pega aquí el contenido del archivo...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                    maxLines = 12,
                    isError = showError
                )

                if (showError) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (jsonContent.isBlank()) {
                        showError = true
                        errorMessage = "El contenido JSON no puede estar vacío"
                    } else {
                        try {
                            // Validar que sea JSON válido
                            val gson = com.google.gson.Gson()
                            gson.fromJson(jsonContent, com.google.gson.JsonObject::class.java)
                            onImport(jsonContent)
                        } catch (e: Exception) {
                            showError = true
                            errorMessage = "JSON inválido: ${e.message}"
                        }
                    }
                }
            ) {
                Text("Importar")
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
fun DeleteKeyDialog(key: InjectedKeyEntity, onConfirm: (InjectedKeyEntity) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar Llave") },
        text = { Text("¿Estás seguro de que quieres eliminar la llave con KCV ${key.kcv}?") },
        confirmButton = {
            Button(onClick = { onConfirm(key) }) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ClearAllKeysDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar Todas las Llaves") },
        text = { 
            Text(
                "⚠️ ADVERTENCIA: Esta acción eliminará TODAS las llaves del almacén.\n\n" +
                "Esto incluye:\n" +
                "• Llaves de ceremonia\n" +
                "• KEK Storage\n" +
                "• KTK (Key Transport Key)\n" +
                "• Llaves operacionales\n\n" +
                "Esta acción NO se puede deshacer.\n\n" +
                "¿Estás completamente seguro?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Sí, Eliminar Todo")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Detecta el tipo de algoritmo basado en la longitud de los datos de la llave
 * o en el algoritmo almacenado en la entidad
 */
fun detectKeyAlgorithm(key: InjectedKeyEntity): String {
    // Si ya tiene un algoritmo asignado (de ceremonia), usarlo
    if (key.keyAlgorithm != "UNASSIGNED" && key.keyAlgorithm.isNotEmpty() && key.keyType == "CEREMONY_KEY") {
        return when (key.keyAlgorithm) {
            "DES_TRIPLE" -> "3DES"
            "AES_128" -> "AES-128"
            "AES_192" -> "AES-192"
            "AES_256" -> "AES-256"
            else -> key.keyAlgorithm
        }
    }

    // Si no, detectar por longitud de datos
    val keyLengthBytes = when {
        key.encryptedKeyData.isNotEmpty() -> key.encryptedKeyData.length / 2
        key.isLegacy() -> {
            @Suppress("DEPRECATION")
            key.keyData.length / 2
        }
        else -> 0
    }
    return when (keyLengthBytes) {
        8 -> "DES"
        16 -> "3DES/AES-128" // Ambigüedad - pueden ser 3DES o AES-128
        24 -> "3DES/AES-192"
        32 -> "AES-256"
        else -> "Desconocido (${keyLengthBytes}B)"
    }
}

@Composable
fun KEKStoragePasswordDialog(
    passwordError: String?,
    onPasswordEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verificar Contraseña") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Ingrese su contraseña de administrador para ver KEK Storage:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    visualTransformation = if (showPassword)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Mostrar/Ocultar"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordError != null,
                    singleLine = true
                )
                if (passwordError != null) {
                    Text(
                        text = passwordError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPasswordEntered(password) },
                enabled = password.isNotEmpty()
            ) {
                Text("Verificar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Diálogo que muestra la validación fallida de eliminación de una llave.
 * Indica por qué no se puede eliminar y en qué perfiles está siendo usada.
 */
@Composable
fun KeyDeletionValidationDialog(
    validation: com.vigatec.persistence.model.KeyDeletionValidation,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "No se puede eliminar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Text("No se puede eliminar la llave")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Título del motivo
                Text(
                    text = getMotivoEliminacion(validation.reason),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

                // Detalles específicos según la razón
                when (validation.reason) {
                    com.vigatec.persistence.model.DeletionReason.IN_USE_BY_PROFILES -> {
                        Text(
                            "Esta llave está asignada a los siguientes perfil(es):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            validation.assignedProfiles.forEach { profileName ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("•", fontWeight = FontWeight.Bold)
                                    Text(
                                        profileName,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        Text(
                            "Elimine esta llave de los perfiles antes de continuar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    com.vigatec.persistence.model.DeletionReason.IS_ACTIVE_KEK_STORAGE -> {
                        Text(
                            "Esta llave es la KEK Storage activa.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Se usa para cifrar otras llaves. Primero debe establecer una nueva KEK Storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    com.vigatec.persistence.model.DeletionReason.IS_ACTIVE_KTK -> {
                        Text(
                            "Esta llave es la KTK (Key Transport Key) activa.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Se usa para transportar llaves. Primero debe establecer una nueva KTK.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    com.vigatec.persistence.model.DeletionReason.MULTIPLE_USES -> {
                        Text(
                            "Esta llave está siendo usada en múltiples contextos:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        if (validation.assignedProfiles.isNotEmpty()) {
                            Text("• Asignada a ${validation.assignedProfiles.size} perfil(es)", style = MaterialTheme.typography.bodySmall)
                        }
                        if (validation.isActiveKEKStorage) {
                            Text("• Es la KEK Storage activa", style = MaterialTheme.typography.bodySmall)
                        }
                        if (validation.isActiveKTK) {
                            Text("• Es la KTK activa", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Debe resolver todos estos usos antes de eliminar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        Text(
                            "No se puede eliminar esta llave debido a inconsistencias de datos.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Entendido")
            }
        }
    )
}

/**
 * Diálogo que muestra la validación fallida de eliminación de todas las llaves.
 * Indica qué llaves no se pueden eliminar y por qué.
 */
@Composable
fun MultipleKeysDeletionValidationDialog(
    validation: com.vigatec.persistence.model.MultipleKeysDeletionValidation,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "No se pueden eliminar todas las llaves",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Text("No se pueden eliminar todas las llaves")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 400.dp)
            ) {
                // Resumen
                Text(
                    text = "No se pueden eliminar ${validation.blockedKeys.size} de ${validation.totalKeys} llaves porque están en uso.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = "Las siguientes llaves están siendo utilizadas y deben ser eliminadas de los perfiles primero:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )

                // Lista de llaves bloqueadas
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    validation.blockedKeys.forEach { blockedKey ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Información de la llave
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("•", fontWeight = FontWeight.Bold)
                                Text(
                                    "KCV: ${blockedKey.kcv} (${blockedKey.keyType})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Razón
                            Text(
                                text = "  Motivo: ${getMotivoEliminacion(blockedKey.reason)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Perfiles asignados (si aplica)
                            if (blockedKey.assignedProfiles.isNotEmpty()) {
                                Text(
                                    text = "  Perfiles: ${blockedKey.assignedProfiles.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // KEK Storage o KTK activa
                            if (blockedKey.isActiveKEKStorage) {
                                Text(
                                    text = "  ⚠️ Es la KEK Storage activa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (blockedKey.isActiveKTK) {
                                Text(
                                    text = "  ⚠️ Es la KTK activa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Text(
                    "Elimine estas llaves de los perfiles antes de continuar con la eliminación masiva.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Entendido")
            }
        }
    )
}

/**
 * Convierte el código de razón a un mensaje legible para el usuario
 */
private fun getMotivoEliminacion(reason: com.vigatec.persistence.model.DeletionReason): String {
    return when (reason) {
        com.vigatec.persistence.model.DeletionReason.IN_USE_BY_PROFILES ->
            "Llave en uso en perfiles"
        com.vigatec.persistence.model.DeletionReason.IS_ACTIVE_KEK_STORAGE ->
            "KEK Storage activa"
        com.vigatec.persistence.model.DeletionReason.IS_ACTIVE_KTK ->
            "KTK activa"
        com.vigatec.persistence.model.DeletionReason.MULTIPLE_USES ->
            "Usos múltiples"
        else ->
            "Error de validación"
    }
} 