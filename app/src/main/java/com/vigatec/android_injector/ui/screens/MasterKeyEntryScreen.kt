package com.vigatec.android_injector.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vigatec.android_injector.viewmodel.MasterKeyEntryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterKeyEntryScreen(
    navController: NavHostController,
    viewModel: MasterKeyEntryViewModel // <-- Removed default value assignment
) {
    val component1 by viewModel.component1
    val component2 by viewModel.component2
    val errorMessage by viewModel.errorMessage
    val successMessage by viewModel.successMessage
    val isLoading by viewModel.isLoading

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Master Key A Entry") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ... (UI Layout remains the same as before) ...
            Text(
                text = "Enter the components for Master Key A. Ensure values are in Hex format and have the correct length.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Component 1 Input
            OutlinedTextField(
                value = component1,
                onValueChange = viewModel::onComponent1Change,
                label = { Text("Component 1 (Hex)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
                isError = errorMessage != null
            )

            // Component 2 Input
            OutlinedTextField(
                value = component2,
                onValueChange = viewModel::onComponent2Change,
                label = { Text("Component 2 (Hex)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
                isError = errorMessage != null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Button to Generate/Inject
            Button(
                onClick = viewModel::generateMasterKeyA,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Generate and Inject Master Key A")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display Error Message
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Display Success Message
            successMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary, // Use a success color
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}