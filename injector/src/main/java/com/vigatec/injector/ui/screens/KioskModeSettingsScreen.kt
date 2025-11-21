package com.vigatec.injector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.injector.viewmodel.KioskModeSettingsViewModel
import com.vigatec.injector.viewmodel.KioskSettingsEvent
import com.vigatec.injector.util.rememberNavigationDebouncer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskModeSettingsScreen(
    onBack: () -> Unit,
    viewModel: KioskModeSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val navigationDebouncer = rememberNavigationDebouncer()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is KioskSettingsEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        withDismissAction = true
                    )
                }
                is KioskSettingsEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        withDismissAction = true
                    )
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modo Kiosk") },
                navigationIcon = {
                    IconButton(onClick = { navigationDebouncer.onClick(onBack) }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Switch principal
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Habilitar Modo Kiosk",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Switch(
                            checked = settings.isKioskModeEnabled,
                            onCheckedChange = { viewModel.updateKioskModeEnabled(it) },
                            enabled = !isLoading
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Configuraciones Individuales",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                KioskSettingItem(
                    label = "Deshabilitar Barra de Estado",
                    checked = settings.disableStatusBar,
                    onCheckedChange = { viewModel.updateDisableStatusBar(it) },
                    enabled = !isLoading
                )

                KioskSettingItem(
                    label = "Ocultar Barra de Navegación",
                    checked = settings.hideNavigationBar,
                    onCheckedChange = { viewModel.updateHideNavigationBar(it) },
                    enabled = !isLoading
                )

                KioskSettingItem(
                    label = "Deshabilitar Tecla Home",
                    checked = settings.disableHomeKey,
                    onCheckedChange = { viewModel.updateDisableHomeKey(it) },
                    enabled = !isLoading
                )

                KioskSettingItem(
                    label = "Deshabilitar Tecla Recientes",
                    checked = settings.disableRecentKey,
                    onCheckedChange = { viewModel.updateDisableRecentKey(it) },
                    enabled = !isLoading
                )

                KioskSettingItem(
                    label = "Interceptar Tecla Power",
                    checked = settings.interceptPowerKey,
                    onCheckedChange = { viewModel.updateInterceptPowerKey(it) },
                    enabled = !isLoading
                )

                KioskSettingItem(
                    label = "Prevenir Desinstalación",
                    checked = settings.preventUninstall,
                    onCheckedChange = { viewModel.updatePreventUninstall(it) },
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.applySettings() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Aplicar Configuración")
                }

                OutlinedButton(
                    onClick = { viewModel.resetAllSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Restaurar Todo")
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun KioskSettingItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
