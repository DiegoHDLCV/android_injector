package com.vigatec.injector.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.entities.KeyConfiguration
import com.example.persistence.entities.ProfileEntity
import com.vigatec.injector.viewmodel.ProfileFormData
import com.vigatec.injector.viewmodel.ProfileViewModel
import com.vigatec.injector.viewmodel.KeyInjectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    keyInjectionViewModel: KeyInjectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Gestión de Perfiles",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = "Administra perfiles de aplicaciones y sus configuraciones",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onShowCreateModal() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear Perfil")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                LoadingScreen()
            } else if (state.profiles.isEmpty()) {
                EmptyStateScreen(onCreateProfile = { viewModel.onShowCreateModal() })
            } else {
                ProfilesContent(
                    profiles = state.profiles,
                    onEdit = { viewModel.onShowCreateModal(it) },
                    onDelete = { viewModel.onDeleteProfile(it) },
                    onInject = { keyInjectionViewModel.showInjectionModal(it) }
                )
            }
        }
    }

    // Modales
    if (state.showCreateModal) {
        CreateProfileModal(
            formData = state.formData,
            availableKeys = state.availableKeys,
            onDismiss = { viewModel.onDismissCreateModal() },
            onSave = { viewModel.onSaveProfile() },
            onFormDataChange = { viewModel.onFormDataChange(it) },
            onAddKeyConfig = { viewModel.addKeyConfiguration() },
            onRemoveKeyConfig = { viewModel.removeKeyConfiguration(it) },
            onUpdateKeyConfig = { id, field, value -> viewModel.updateKeyConfiguration(id, field, value) }
        )
    }

    // Modal de inyección de llaves
    KeyInjectionModal(keyInjectionViewModel)
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Cargando perfiles...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EmptyStateScreen(onCreateProfile: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Icono animado
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No hay perfiles configurados",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Comienza creando un nuevo perfil de aplicación para gestionar tus configuraciones de llaves",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            
            Button(
                onClick = onCreateProfile,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear Primer Perfil")
            }
        }
    }
}

@Composable
private fun ProfilesContent(
    profiles: List<ProfileEntity>,
    onEdit: (ProfileEntity) -> Unit,
    onDelete: (ProfileEntity) -> Unit,
    onInject: (ProfileEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header con estadísticas
        item {
            StatisticsHeader(profiles = profiles)
        }
        
        // Lista de perfiles
        items(
            items = profiles,
            key = { it.id }
        ) { profile ->
            ProfileCard(
                profile = profile,
                onEdit = { onEdit(profile) },
                onDelete = { onDelete(profile) },
                onInject = { onInject(profile) }
            )
        }
    }
}

@Composable
private fun StatisticsHeader(profiles: List<ProfileEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatisticItem(
                icon = Icons.Rounded.Folder,
                value = profiles.size.toString(),
                label = "Total Perfiles",
                color = MaterialTheme.colorScheme.primary
            )
            
            StatisticItem(
                icon = Icons.Rounded.Key,
                value = profiles.count { it.keyConfigurations.isNotEmpty() }.toString(),
                label = "Configurados",
                color = MaterialTheme.colorScheme.secondary
            )
            
            StatisticItem(
                icon = Icons.Rounded.CheckCircle,
                value = profiles.count { 
                    it.keyConfigurations.all { config -> config.selectedKey.isNotEmpty() }
                }.toString(),
                label = "Listos",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun StatisticItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ProfileCard(
    profile: ProfileEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInject: () -> Unit
) {
    val appTypeConfig = getAppTypeConfig(profile.applicationType)
    val keyCount = profile.keyConfigurations.size
    val isConfigured = keyCount > 0
    val isReady = profile.keyConfigurations.all { it.selectedKey.isNotEmpty() }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header con avatar y info principal
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar con gradiente
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(appTypeConfig.gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = appTypeConfig.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    // Indicador de estado
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isReady -> MaterialTheme.colorScheme.primary
                                    isConfigured -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                    )
                }
                
                // Información del perfil
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = profile.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge del tipo de aplicación
                        Surface(
                            color = appTypeConfig.color.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = profile.applicationType,
                                style = MaterialTheme.typography.labelMedium,
                                color = appTypeConfig.color,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        
                        // Contador de llaves
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "$keyCount llaves",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            
            // Barra de estado
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        isReady -> MaterialTheme.colorScheme.primary
                                        isConfigured -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.tertiary
                                    }
                                )
                        )
                        Text(
                            text = when {
                                isReady -> "Listo para inyectar"
                                isConfigured -> "Pendiente configuración"
                                else -> "Sin configuraciones"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Configuraciones de llaves (vista compacta)
            if (keyCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Configuraciones",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = profile.keyConfigurations.take(6),
                            key = { it.id }
                        ) { config ->
                            KeyConfigChip(config = config)
                        }
                        
                        if (keyCount > 6) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "+${keyCount - 6}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón principal de inyección
                Button(
                    onClick = onInject,
                    modifier = Modifier.weight(1f),
                    enabled = isReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inyectar")
                }
                
                // Botones secundarios
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar Perfil",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar Perfil",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyConfigChip(config: KeyConfiguration) {
    val usageIcon = getUsageIcon(config.usage)
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = usageIcon,
                fontSize = 12.sp
            )
            Column {
                Text(
                    text = config.usage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "#${config.slot}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

// Funciones de utilidad
private fun getAppTypeConfig(appType: String): AppTypeConfig {
    return when (appType) {
        "Retail" -> AppTypeConfig(
            icon = Icons.Rounded.Store,
            color = Color(0xFF3B82F6),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
            )
        )
        "H2H" -> AppTypeConfig(
            icon = Icons.Rounded.Link,
            color = Color(0xFF8B5CF6),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))
            )
        )
        "Posint" -> AppTypeConfig(
            icon = Icons.Rounded.CreditCard,
            color = Color(0xFF10B981),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFF10B981), Color(0xFF059669))
            )
        )
        "ATM" -> AppTypeConfig(
            icon = Icons.Rounded.AccountBalance,
            color = Color(0xFFF59E0B),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
            )
        )
        else -> AppTypeConfig(
            icon = Icons.Rounded.Settings,
            color = Color(0xFF6B7280),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFF6B7280), Color(0xFF4B5563))
            )
        )
    }
}

private fun getUsageIcon(usage: String): String {
    return when (usage.lowercase()) {
        "pin" -> "🔐"
        "mac" -> "🔒"
        "data" -> "📊"
        "kek" -> "🗝️"
        else -> "🔑"
    }
}

private data class AppTypeConfig(
    val icon: ImageVector,
    val color: Color,
    val gradient: Brush
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileModal(
    formData: ProfileFormData,
    availableKeys: List<InjectedKeyEntity>,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onFormDataChange: (ProfileFormData) -> Unit,
    onAddKeyConfig: () -> Unit,
    onRemoveKeyConfig: (Long) -> Unit,
    onUpdateKeyConfig: (Long, String, String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .align(Alignment.Center),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (formData.id == null) "Crear Perfil" else "Editar Perfil",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Configura las propiedades del perfil y sus llaves",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // Contenido
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Información básica
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "📋 Información Básica",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = formData.name,
                                    onValueChange = { onFormDataChange(formData.copy(name = it)) },
                                    label = { Text("Nombre del Perfil") },
                                    placeholder = { Text("ej: Perfil Retail Principal") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                
                                OutlinedTextField(
                                    value = formData.description,
                                    onValueChange = { onFormDataChange(formData.copy(description = it)) },
                                    label = { Text("Descripción") },
                                    placeholder = { Text("Describe el propósito de este perfil...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 5
                                )
                                
                                // Selector de tipo de aplicación
                                var expanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = !expanded }
                                ) {
                                    OutlinedTextField(
                                        value = formData.appType,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Tipo de Aplicación") },
                                        placeholder = { Text("Seleccionar tipo...") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        listOf("Retail", "H2H", "Posint", "ATM", "Custom").forEach { type ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(getAppTypeIcon(type))
                                                        Text(type)
                                                    }
                                                },
                                                onClick = {
                                                    onFormDataChange(formData.copy(appType = type))
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Configuración de llaves
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🔑 Configuración de Llaves",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Button(
                                onClick = onAddKeyConfig,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Agregar Llave")
                            }
                        }
                        
                        if (formData.keyConfigurations.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Key,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = "No hay configuraciones de llaves",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "Agrega configuraciones para definir qué llaves se inyectarán",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                formData.keyConfigurations.forEach { config ->
                                    KeyConfigurationItem(
                                        config = config,
                                        availableKeys = availableKeys,
                                        onUpdate = onUpdateKeyConfig,
                                        onRemove = { onRemoveKeyConfig(config.id) }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Footer con botones
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = onSave,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (formData.id == null) "Crear Perfil" else "Actualizar Perfil")
                        }
                    }
                }
            }
        }
    }
}

private fun getAppTypeIcon(appType: String): String {
    return when (appType) {
        "Retail" -> "🏪"
        "H2H" -> "🔗"
        "Posint" -> "💳"
        "ATM" -> "🏧"
        else -> "⚙️"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyConfigurationItem(
    config: KeyConfiguration,
    availableKeys: List<InjectedKeyEntity>,
    onUpdate: (Long, String, String) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header con icono y botón de eliminar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getUsageIcon(config.usage),
                            fontSize = 20.sp
                        )
                    }
                    Column {
                        Text(
                            text = "Configuración de Llave",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ID: ${config.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar configuración",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Campos de configuración
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Uso
                var usageExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = usageExpanded,
                    onExpandedChange = { usageExpanded = !usageExpanded }
                ) {
                    OutlinedTextField(
                        value = config.usage,
                        onValueChange = { onUpdate(config.id, "usage", it) },
                        label = { Text("Uso") },
                        placeholder = { Text("Seleccionar uso...") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = usageExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = usageExpanded,
                        onDismissRequest = { usageExpanded = false }
                    ) {
                        listOf("PIN", "MAC", "DATA", "KEK").forEach { usage ->
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(getUsageIcon(usage))
                                        Text(usage)
                                    }
                                },
                                onClick = {
                                    onUpdate(config.id, "usage", usage)
                                    usageExpanded = false
                                }
                            )
                        }
                    }
                }
                
                // Tipo de llave
                var keyTypeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = keyTypeExpanded,
                    onExpandedChange = { keyTypeExpanded = !keyTypeExpanded }
                ) {
                    OutlinedTextField(
                        value = config.keyType,
                        onValueChange = { onUpdate(config.id, "keyType", it) },
                        label = { Text("Tipo de Llave") },
                        placeholder = { Text("Seleccionar tipo...") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyTypeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = keyTypeExpanded,
                        onDismissRequest = { keyTypeExpanded = false }
                    ) {
                        listOf("TDES", "AES").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    onUpdate(config.id, "keyType", type)
                                    keyTypeExpanded = false
                                }
                            )
                        }
                    }
                }
                
                // Slot
                OutlinedTextField(
                    value = config.slot,
                    onValueChange = { onUpdate(config.id, "slot", it) },
                    label = { Text("Slot") },
                    placeholder = { Text("ej: 01") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                // Llave seleccionada
                var keyExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = keyExpanded,
                    onExpandedChange = { keyExpanded = !keyExpanded }
                ) {
                    OutlinedTextField(
                        value = config.selectedKey,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Llave Seleccionada (KCV)") },
                        placeholder = { Text("Seleccionar llave...") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = keyExpanded,
                        onDismissRequest = { keyExpanded = false }
                    ) {
                        if (availableKeys.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No hay llaves disponibles") },
                                onClick = { keyExpanded = false }
                            )
                        } else {
                            availableKeys.forEach { key ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(key.kcv, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                            Text("(ID: ${key.id})", style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        onUpdate(config.id, "selectedKey", key.kcv)
                                        keyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}