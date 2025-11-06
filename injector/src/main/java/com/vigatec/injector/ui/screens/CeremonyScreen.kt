package com.vigatec.injector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vigatec.injector.viewmodel.CeremonyViewModel
import com.vigatec.injector.ui.components.HexadecimalTextField
import com.vigatec.injector.viewmodel.KeyAlgorithmType
import com.vigatec.injector.ui.navigation.MainScreen
import com.example.persistence.entities.KEKType

@Composable
fun CeremonyScreen(
    navController: NavHostController? = null,
    viewModel: CeremonyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val localNavController = navController // Capturar navController en una variable local

    // Efecto para asegurar navegación en caso de que se cierre el diálogo de otras formas
    LaunchedEffect(state.showTimeoutDialog) {
        if (!state.showTimeoutDialog && state.currentStep == 1 && !state.isCeremonyInProgress) {
            // Si llegamos aquí, significa que el timeout fue manejado pero no estamos en ceremonia
            // Esto es un fallback en caso de que el usuario cierre el diálogo de otra forma
            android.util.Log.d("CeremonyScreen", "LaunchedEffect: Timeout handled, estado reiniciado")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ceremonia de Inyección de Llaves",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Indicador de carga
                if (state.isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Procesando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (state.currentStep) {
                    1 -> ConfigurationStep(viewModel)
                    2 -> CustodianStep(viewModel)
                    3 -> FinalizationStep(viewModel)
                }
            }
        }
    }

    // Diálogo de timeout expirado
    if (state.showTimeoutDialog) {
        TimeoutExpiredDialog(
            navController = localNavController,
            viewModel = viewModel
        )
    }
}

@Composable
private fun ConfigurationStep(viewModel: CeremonyViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column {
        Text("Paso 1: Configuración Inicial", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Indicador de rol del usuario
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (state.isAdmin) 
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isAdmin) "👤 ADMINISTRADOR" else "👤 USUARIO",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isAdmin) 
                        MaterialTheme.colorScheme.tertiary
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Selector de tipo de llave
        Text("Tipo de Llave:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Column(Modifier.selectableGroup()) {
            KeyAlgorithmType.values().forEach { keyType ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = state.selectedKeyType == keyType,
                        onClick = { viewModel.onKeyTypeChange(keyType) }
                    )
                    Text(
                        text = keyType.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selector de tipo de llave: Operacional o KEK (según permisos de admin)
        Text("Tipo de Llave:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(Modifier.selectableGroup()) {
            // Opción: Llave Operacional (siempre visible)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.selectedKEKType == KEKType.NONE)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    RadioButton(
                        selected = state.selectedKEKType == KEKType.NONE,
                        onClick = { viewModel.onKEKTypeChange(KEKType.NONE) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Llave Operacional",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Para operaciones normales (PIN, MAC, etc.)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Opción: KEK Storage (solo visible si tiene permiso)
            if (state.canCreateKEK) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.selectedKEKType == KEKType.KEK_STORAGE)
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = state.selectedKEKType == KEKType.KEK_STORAGE,
                            onClick = { viewModel.onKEKTypeChange(KEKType.KEK_STORAGE) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "KEK (Key Encryption Key)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Protege llaves en el almacén local",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (state.selectedKEKType == KEKType.KEK_STORAGE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ Requisitos:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "• Debe ser AES-256 (32 bytes)\n• Se guardará en Android Keystore\n• Cifra automáticamente todas las llaves",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        // Nota informativa sobre KTK
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ℹ️",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Para marcar una llave como KTK (transporte a SubPOS), hazlo desde el Almacén de Llaves",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Número de Custodios:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.selectableGroup()) {
            (2..3).forEach { num ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.numCustodians == num,
                        onClick = { viewModel.onNumCustodiansChange(num) }
                    )
                    Text(text = num.toString())
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { viewModel.startCeremony() }, modifier = Modifier.fillMaxWidth()) {
            Text("Iniciar Ceremonia")
        }
    }

    // Diálogo de error de validación de KEK
    if (state.kekValidationError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearKekValidationError() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "No se puede iniciar la ceremonia",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = state.kekValidationError ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!state.isAdmin && !state.hasKEKStorage) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "ℹ️ Información adicional",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Las llaves operacionales se cifran con la KEK Storage para mayor seguridad. " +
                                           "Un administrador debe crear primero una KEK Storage antes de que puedas crear llaves operacionales.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearKekValidationError() }
                ) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
private fun CustodianStep(viewModel: CeremonyViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column {
        // Título principal con el número de custodio prominente
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Custodio ${state.currentCustodian}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text(
                        text = "de ${state.numCustodians} custodios",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    // Display del timeout
                    if (state.isTimeoutActive && state.timeoutRemainingSeconds > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (state.isTimeoutWarning)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (state.isTimeoutWarning) "⏰" else "⏱️",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = formatTimeoutDisplay(state.timeoutRemainingSeconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isTimeoutWarning)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = state.selectedKeyType.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        HexadecimalTextField(
            value = state.component,
            onValueChange = { viewModel.onComponentChange(it) },
            label = "Componente de Llave (Hex)",
            maxLength = state.selectedKeyType.bytesRequired * 2, // Cada byte = 2 caracteres hex
            isError = state.componentError != null,
            errorMessage = state.componentError,
            onCancel = { viewModel.cancelCeremony() },
            onVerifyKCV = { viewModel.addComponent() }
        )
    }

    // Modal para mostrar y verificar el KCV
    if (state.showKcvModal) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissKcvModal() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "KCV del Componente",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.partialKCV,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.currentCustodian < state.numCustodians) {
                            "Presiona Aceptar para pasar al siguiente custodio"
                        } else {
                            "Presiona Aceptar para finalizar la ceremonia"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            },
            dismissButton = {
                Button(
                    onClick = { viewModel.dismissKcvModal() },
                    modifier = Modifier.fillMaxWidth(0.45f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancelar")
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmKcvAndProceed() },
                    modifier = Modifier.fillMaxWidth(0.45f)
                ) {
                    Text("Aceptar")
                }
            }
        )
    }
}

@Composable
private fun FinalizationStep(viewModel: CeremonyViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        // Título
        Text(
            "Ceremonia Completada",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "La llave ha sido generada exitosamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Card con KCV Final
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "KCV Final",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.finalKCV,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Card para nombre personalizado
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Nombre de la Llave",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Campo de nombre personalizado
                OutlinedTextField(
                    value = state.customName,
                    onValueChange = { viewModel.onCustomNameChange(it) },
                    label = { Text("Nombre (opcional)") },
                    placeholder = { Text("Ej: PIN Key Principal") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "El nombre te ayudará a identificar la llave más fácilmente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botones de acción
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.cancelCeremony() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nueva")
            }
            Button(
                onClick = { viewModel.cancelCeremony() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Guardar")
            }
        }
    }
}

/**
 * Formatea el tiempo de timeout en el formato MM:SS
 */
private fun formatTimeoutDisplay(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, secs)
}

@Composable
private fun TimeoutExpiredDialog(
    navController: NavHostController?,
    viewModel: CeremonyViewModel
) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissTimeoutDialog() },
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Tiempo de Espera Agotado",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Se ha excedido el tiempo máximo de espera para custodios.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "⏰ Información",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "La ceremonia se ha cancelado automáticamente. Consulte los logs para más detalles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Navegar primero, luego actualizar el estado
                    if (navController != null) {
                        android.util.Log.d("CeremonyScreen", "🔴 User clicked Accept - Navigating to Dashboard")
                        navController.navigate(MainScreen.Dashboard.route) {
                            popUpTo(MainScreen.Ceremony.route) { inclusive = true }
                        }
                    } else {
                        android.util.Log.e("CeremonyScreen", "❌ navController is NULL in TimeoutExpiredDialog!")
                    }

                    // Actualizar el estado después de navegar
                    viewModel.dismissTimeoutDialog()
                }
            ) {
                Text("Aceptar")
            }
        }
    )
}