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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.injector.viewmodel.CeremonyViewModel
import com.vigatec.injector.ui.components.HexadecimalTextField
import com.vigatec.injector.viewmodel.KeyAlgorithmType
import com.example.persistence.entities.KEKType

@Composable
fun CeremonyScreen(viewModel: CeremonyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

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
                
                // Indicador de estado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Procesando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Estado: ${when(state.currentStep) {
                                1 -> "Configuración"
                                2 -> "Custodios"
                                3 -> "Completado"
                                else -> "Desconocido"
                            }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                        text = state.kekValidationError!!,
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
        Text(
            "Paso 2: Custodio ${state.currentCustodian} de ${state.numCustodians}",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Mostrar tipo de llave seleccionado
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ) {
            Text(
                text = "Tipo de llave: ${state.selectedKeyType.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HexadecimalTextField(
            value = state.component,
            onValueChange = { viewModel.onComponentChange(it) },
            label = "Componente de Llave (Hex)",
            maxLength = state.selectedKeyType.bytesRequired * 2, // Cada byte = 2 caracteres hex
            isError = state.componentError != null,
            errorMessage = state.componentError,
            isPasswordVisible = state.showComponent,
            onPasswordVisibilityChange = { viewModel.onToggleShowComponent() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.addComponent() }, enabled = state.component.isNotBlank()) {
            Text("Verificar KCV")
        }

        if (state.partialKCV.isNotBlank()) {
            Text("KCV: ${state.partialKCV}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Botones de navegación
        if (state.currentCustodian < state.numCustodians) {
            Button(
                onClick = { viewModel.nextCustodian() },
                enabled = state.partialKCV.isNotBlank() && !state.isLoading
            ) {
                Text("Siguiente Custodio")
            }
        } else {
            // Último custodio - solo habilitar el botón si ya se verificó el KCV
            Button(
                onClick = { viewModel.finalizeCeremony() },
                enabled = state.partialKCV.isNotBlank() && !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Procesando...")
                } else {
                    Text("Finalizar y Guardar Llave")
                }
            }

            if (state.partialKCV.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Verifica el componente del último custodio antes de finalizar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.cancelCeremony() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text("Cancelar Ceremonia")
        }
    }
}

@Composable
private fun FinalizationStep(viewModel: CeremonyViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column {
        Text("Paso 3: ¡Ceremonia Completada!", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text("La llave ha sido generada exitosamente.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Text("KCV Final: ${state.finalKCV}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Card para nombre personalizado
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Información de la Llave",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Campo de nombre personalizado
                OutlinedTextField(
                    value = state.customName,
                    onValueChange = { viewModel.onCustomNameChange(it) },
                    label = { Text("Nombre de la llave (opcional)") },
                    placeholder = { Text("Ej: PIN Key Principal, MAC Key Tienda") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "El nombre te ayudará a identificar la llave más fácilmente. Todas las llaves se crean como operacionales y pueden ser configuradas como KEK desde el almacén de llaves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botón para nueva ceremonia
        Button(
            onClick = { viewModel.cancelCeremony() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nueva Ceremonia")
        }
    }
} 