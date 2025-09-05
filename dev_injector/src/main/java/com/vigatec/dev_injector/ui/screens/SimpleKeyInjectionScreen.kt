package com.vigatec.dev_injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.manufacturer.base.models.KeyType
import com.vigatec.dev_injector.ui.viewmodel.SimpleKeyInjectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleKeyInjectionScreen(
    onNavigateToKeys: () -> Unit = {},
    viewModel: SimpleKeyInjectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Título
        Text(
            text = "Dev Key Injector",
            style = MaterialTheme.typography.headlineSmall
        )
        
        // Slot
        OutlinedTextField(
            value = uiState.keySlot,
            onValueChange = viewModel::updateKeySlot,
            label = { Text("Slot") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = uiState.keySlotError != null,
            supportingText = uiState.keySlotError?.let { { Text(it) } }
        )
        
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
                label = { Text("Tipo") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                KeyType.values().forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.name.take(20)) },
                        onClick = {
                            viewModel.updateKeyType(type.name)
                            expanded = false
                        }
                    )
                }
            }
        }
        
        // Master Key Slot - Solo mostrar para Working Keys
        val isWorkingKey = uiState.keyType.contains("WORKING")
        if (isWorkingKey) {
            OutlinedTextField(
                value = uiState.masterKeyIndex,
                onValueChange = viewModel::updateMasterKeyIndex,
                label = { Text("Master Key Slot") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.masterKeyIndexError != null,
                supportingText = uiState.masterKeyIndexError?.let { { Text(it) } }
                    ?: { Text("Slot donde está la Master Key para cifrar") }
            )
        }
        
        // Valor de llave (con valor por defecto)
        OutlinedTextField(
            value = uiState.keyValue,
            onValueChange = viewModel::updateKeyValue,
            label = { Text("Valor (Hex)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = uiState.keyValueError != null,
            supportingText = uiState.keyValueError?.let { { Text(it) } }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Botones de acción
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botón inyectar
            Button(
                onClick = { viewModel.injectKey() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isLoading) "Inyectando..." else "Inyectar Llave")
            }
            
            // Botón ver llaves
            OutlinedButton(
                onClick = onNavigateToKeys,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Llaves Inyectadas")
            }
        }
        
        // Estado
        uiState.statusMessage?.let { message ->
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
                    modifier = Modifier.padding(12.dp),
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