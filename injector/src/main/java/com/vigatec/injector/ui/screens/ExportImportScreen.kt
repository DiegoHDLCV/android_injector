package com.vigatec.injector.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vigatec.injector.viewmodel.ExportImportState
import com.vigatec.injector.viewmodel.ExportImportViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportImportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // File picker para importación
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Copiar archivo a cache para lectura
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(context.cacheDir, "temp_import.json")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.onSelectImportFile(tempFile.absolutePath)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exportar / Importar Llaves") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Información del sistema
            SystemInfoCard(
                hasKEKStorage = state.hasKEKStorage,
                totalKeys = state.totalKeysInVault,
                isAdmin = state.isAdmin
            )

            // Tabs
            TabRow(
                selectedTabIndex = state.currentTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = state.currentTab == 0,
                    onClick = { viewModel.onTabChange(0) },
                    text = { Text("Exportar") },
                    icon = { Icon(Icons.Default.Upload, "Exportar") }
                )
                Tab(
                    selected = state.currentTab == 1,
                    onClick = { viewModel.onTabChange(1) },
                    text = { Text("Importar") },
                    icon = { Icon(Icons.Default.Download, "Importar") }
                )
            }

            // Contenido de los tabs
            when (state.currentTab) {
                0 -> ExportTab(
                    state = state,
                    onPassphraseChange = viewModel::onExportPassphraseChange,
                    onPassphraseConfirmChange = viewModel::onExportPassphraseConfirmChange,
                    onToggleVisibility = viewModel::onToggleExportPassphraseVisibility,
                    onExport = viewModel::exportKeys
                )
                1 -> ImportTab(
                    state = state,
                    onPassphraseChange = viewModel::onImportPassphraseChange,
                    onToggleVisibility = viewModel::onToggleImportPassphraseVisibility,
                    onSelectFile = { importFileLauncher.launch(createFilePickerIntent()) },
                    onImport = viewModel::importKeys
                )
            }
        }

        // Diálogos de estado
        if (state.exportSuccess) {
            ExportSuccessDialog(
                filePath = state.exportedFilePath,
                keyCount = state.exportedKeyCount,
                onDismiss = viewModel::clearExportSuccess
            )
        }

        if (state.importSuccess) {
            ImportSuccessDialog(
                importedCount = state.importedKeyCount,
                skippedCount = state.skippedDuplicates,
                onDismiss = viewModel::clearImportSuccess
            )
        }

        state.errorMessage?.let { errorMessage ->
            ErrorDialog(
                message = errorMessage,
                onDismiss = viewModel::clearError
            )
        }

        // Loading overlay con mensaje
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxSize()
                ) {}
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = if (state.currentTab == 0) {
                                "Exportando llaves...\nCifrando con PBKDF2 (200k iteraciones)"
                            } else {
                                "Importando llaves...\nDescifrando y validando"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Por favor espere",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemInfoCard(
    hasKEKStorage: Boolean,
    totalKeys: Int,
    isAdmin: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasKEKStorage)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = if (hasKEKStorage) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasKEKStorage)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasKEKStorage) "KEK Storage: Disponible" else "KEK Storage: No disponible",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Llaves en almacén: $totalKeys",
                style = MaterialTheme.typography.bodyMedium
            )
            if (isAdmin) {
                Text(
                    text = "Permisos: Administrador",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExportTab(
    state: ExportImportState,
    onPassphraseChange: (String) -> Unit,
    onPassphraseConfirmChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Instrucciones
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Instrucciones de Exportación",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Las llaves se exportarán cifradas con su KEK actual\n" +
                                "• El archivo se protegerá con una passphrase adicional\n" +
                                "• Mínimo 16 caracteres, incluir mayúsculas, minúsculas y números\n" +
                                "• El archivo se guardará en la carpeta Descargas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Passphrase
        OutlinedTextField(
            value = state.exportPassphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Passphrase de exportación") },
            visualTransformation = if (state.showExportPassphrase)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (state.showExportPassphrase)
                            Icons.Default.VisibilityOff
                        else
                            Icons.Default.Visibility,
                        contentDescription = "Mostrar/Ocultar"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isError = state.exportPassphraseError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Confirmar Passphrase
        OutlinedTextField(
            value = state.exportPassphraseConfirm,
            onValueChange = onPassphraseConfirmChange,
            label = { Text("Confirmar passphrase") },
            visualTransformation = if (state.showExportPassphrase)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            isError = state.exportPassphraseError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (state.exportPassphraseError != null) {
            Text(
                text = state.exportPassphraseError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Indicador de fortaleza
        PassphraseStrengthIndicator(passphrase = state.exportPassphrase)

        Spacer(modifier = Modifier.height(24.dp))

        // Botón exportar
        Button(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading &&
                      state.exportPassphrase.isNotEmpty() &&
                      state.exportPassphraseConfirm.isNotEmpty() &&
                      state.hasKEKStorage &&
                      state.totalKeysInVault > 0
        ) {
            Icon(Icons.Default.Upload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Exportar ${state.totalKeysInVault} Llaves")
        }

        if (!state.hasKEKStorage) {
            Text(
                text = "⚠️ No se puede exportar sin KEK Storage",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (state.totalKeysInVault == 0) {
            Text(
                text = "⚠️ No hay llaves para exportar",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ImportTab(
    state: ExportImportState,
    onPassphraseChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSelectFile: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Instrucciones
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Instrucciones de Importación",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Seleccione el archivo JSON exportado\n" +
                                "• Ingrese la passphrase usada en la exportación\n" +
                                "• Las llaves duplicadas (mismo KCV) serán omitidas\n" +
                                "• Debe existir KEK Storage en este dispositivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Selector de archivo
        OutlinedButton(
            onClick = onSelectFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.selectedImportFile != null)
                    "Archivo: ${File(state.selectedImportFile).name}"
                else
                    "Seleccionar archivo JSON"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Passphrase
        OutlinedTextField(
            value = state.importPassphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Passphrase de exportación") },
            visualTransformation = if (state.showImportPassphrase)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (state.showImportPassphrase)
                            Icons.Default.VisibilityOff
                        else
                            Icons.Default.Visibility,
                        contentDescription = "Mostrar/Ocultar"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isError = state.importPassphraseError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (state.importPassphraseError != null) {
            Text(
                text = state.importPassphraseError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botón importar
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading &&
                      state.selectedImportFile != null &&
                      state.importPassphrase.isNotEmpty() &&
                      state.hasKEKStorage
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Importar Llaves")
        }

        if (!state.hasKEKStorage) {
            Text(
                text = "⚠️ No se puede importar sin KEK Storage. Debe crear una KEK Storage mediante ceremonia primero.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PassphraseStrengthIndicator(passphrase: String) {
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
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExportSuccessDialog(
    filePath: String,
    keyCount: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF43A047),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Exportación Exitosa",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                "$keyCount llaves exportadas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Cifradas con PBKDF2 + AES-256-GCM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "Ubicación del archivo:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            filePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Guarde este archivo en un lugar seguro. Necesitará la passphrase para importarlo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    shareExportFile(context, filePath)
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compartir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

private fun shareExportFile(context: Context, filePath: String) {
    val file = File(filePath)
    if (file.exists()) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartir archivo de exportación")
        context.startActivity(chooser)
    }
}

@Composable
private fun ImportSuccessDialog(
    importedCount: Int,
    skippedCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF43A047)
            )
        },
        title = { Text("Importación Exitosa") },
        text = {
            Column {
                Text("Se han importado $importedCount llaves exitosamente.")
                if (skippedCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Se omitieron $skippedCount llaves duplicadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Aceptar")
            }
        }
    )
}

@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Error") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Aceptar")
            }
        }
    )
}

/**
 * Componente DropZoneArea para arrastrar y soltar archivos JSON
 * Muestra un área visual para que el usuario arrastre archivos
 */
@Composable
private fun DropZoneArea(
    isDragging: Boolean,
    fileName: String?,
    onDragEnter: () -> Unit,
    onDragExit: () -> Unit,
    onFileDrop: (fileName: String, content: String) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .animateContentSize()
            .border(
                width = 2.dp,
                color = if (isDragging)
                    MaterialTheme.colorScheme.primary
                else if (fileName != null)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else if (fileName != null)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (fileName == null) {
                // Estado vacío - mostrar instrucciones de drag & drop
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (isDragging)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Arrastra un archivo JSON aquí",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDragging)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "O desde Vysor, Google Drive, etc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                // Estado con archivo cargado
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Archivo cargado",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onClear,
                    modifier = Modifier.size(width = 120.dp, height = 36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Limpiar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun createFilePickerIntent(): Intent {
    val intent = Intent(Intent.ACTION_GET_CONTENT)
    intent.type = "application/json"
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    return Intent.createChooser(intent, "Seleccionar archivo de exportación")
}
