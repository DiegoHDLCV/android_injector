package com.vigatec.dev_injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.manufacturer.base.models.KeyAlgorithm
import com.vigatec.manufacturer.base.models.KeyType
import com.vigatec.dev_injector.ui.viewmodel.KeyInjectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyInjectionScreen(
    onNavigateToInjectedKeys: () -> Unit,
    viewModel: KeyInjectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Título
        Text(
            text = "Inyección de Llaves de Prueba",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Card de configuración de llave
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Configuración de Llave",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Slot de la llave
                OutlinedTextField(
                    value = uiState.keySlot,
                    onValueChange = viewModel::updateKeySlot,
                    label = { Text("Slot de la llave") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.keySlotError != null,
                    supportingText = uiState.keySlotError?.let { { Text(it) } }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Tipo de llave
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = uiState.keyType,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Tipo de llave") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        KeyType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    viewModel.updateKeyType(type.name)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Algoritmo de llave
                var algorithmExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = algorithmExpanded,
                    onExpandedChange = { algorithmExpanded = !algorithmExpanded }
                ) {
                    OutlinedTextField(
                        value = uiState.keyAlgorithm,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Algoritmo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algorithmExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = algorithmExpanded,
                        onDismissRequest = { algorithmExpanded = false }
                    ) {
                        KeyAlgorithm.values().forEach { algorithm ->
                            DropdownMenuItem(
                                text = { Text(algorithm.name) },
                                onClick = {
                                    viewModel.updateKeyAlgorithm(algorithm.name)
                                    algorithmExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card de datos de la llave
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Datos de la Llave",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Valor de la llave
                OutlinedTextField(
                    value = uiState.keyValue,
                    onValueChange = viewModel::updateKeyValue,
                    label = { Text("Valor de la llave (hex)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.keyValueError != null,
                    supportingText = uiState.keyValueError?.let { { Text(it) } }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // KCV (opcional)
                OutlinedTextField(
                    value = uiState.kcv,
                    onValueChange = viewModel::updateKcv,
                    label = { Text("KCV (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.kcvError != null,
                    supportingText = uiState.kcvError?.let { { Text(it) } }
                )
                
                // Master Key Slot - Solo mostrar para Working Keys
                val isWorkingKey = uiState.keyType.contains("WORKING")
                if (isWorkingKey) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = uiState.masterKeyIndex,
                        onValueChange = viewModel::updateMasterKeyIndex,
                        label = { Text("Master Key Slot") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.masterKeyIndexError != null,
                        supportingText = uiState.masterKeyIndexError?.let { { Text(it) } }
                            ?: { Text("Slot donde está la Master Key para cifrar esta Working Key") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.injectKey() },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isLoading
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inyectar Llave")
            }

            OutlinedButton(
                onClick = onNavigateToInjectedKeys,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Llaves")
            }
        }

        // Progress bar
        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Mensaje de estado
        uiState.statusMessage?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (message.contains("éxito", ignoreCase = true)) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    color = if (message.contains("éxito", ignoreCase = true)) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
        }
    }
}
