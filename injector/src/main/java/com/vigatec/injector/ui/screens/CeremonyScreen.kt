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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.injector.viewmodel.CeremonyViewModel

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
                Spacer(modifier = Modifier.height(16.dp))

                when (state.currentStep) {
                    1 -> ConfigurationStep(viewModel)
                    2 -> CustodianStep(viewModel)
                    3 -> FinalizationStep(viewModel)
                }

                Text(
                    text = "Log de Operaciones:",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                Text(
                    text = state.log,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ConfigurationStep(viewModel: CeremonyViewModel) {
    val state by viewModel.uiState.collectAsState()
    Column {
        Text("Paso 1: Configuración Inicial", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Número de Custodios:")
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
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.startCeremony() }) {
            Text("Iniciar Ceremonia")
        }
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
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = state.component,
            onValueChange = { viewModel.onComponentChange(it) },
            label = { Text("Componente de Llave (Hex)") },
            visualTransformation = if (state.showComponent) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { viewModel.onToggleShowComponent() }) {
                    Icon(
                        if (state.showComponent) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle visibility"
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { viewModel.addComponent() }, enabled = state.component.isNotBlank()) {
            Text("Verificar KCV")
        }

        if (state.partialKCV.isNotBlank()) {
            Text("KCV: ${state.partialKCV}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            if (state.currentCustodian < state.numCustodians) {
                Button(onClick = { viewModel.nextCustodian() }) {
                    Text("Siguiente Custodio")
                }
            } else {
                Button(onClick = { viewModel.finalizeCeremony() }) {
                    Text("Finalizar y Guardar Llave")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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
        Text("KCV Final: ${state.finalKCV}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.cancelCeremony() }) {
            Text("Nueva Ceremonia")
        }
    }
} 