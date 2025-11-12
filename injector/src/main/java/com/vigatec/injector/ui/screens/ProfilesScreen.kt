package com.vigatec.injector.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigatec.persistence.entities.InjectedKeyEntity
import com.vigatec.persistence.entities.KeyConfiguration
import com.vigatec.persistence.entities.ProfileEntity
import com.vigatec.injector.ui.components.ProfileCardSkeleton
import com.vigatec.injector.viewmodel.ProfileFormData
import com.vigatec.injector.viewmodel.ProfileViewModel
import com.vigatec.injector.viewmodel.KeyInjectionViewModel
import com.vigatec.injector.viewmodel.InjectionStatus
import com.vigatec.config.SystemConfig
import com.vigatec.config.EnumManufacturer
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    username: String = "system",
    viewModel: ProfileViewModel = hiltViewModel(),
    keyInjectionViewModel: KeyInjectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfiles de Inyección", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.onShowImportModal() }) {
                        Icon(Icons.Default.Upload, contentDescription = "Importar Perfil")
                    }
                    IconButton(onClick = { viewModel.onShowCreateModal() }) {
                        Icon(Icons.Default.Add, contentDescription = "Crear Perfil")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                ProfilesSkeletonScreen()
            } else if (state.profiles.isEmpty()) {
                EmptyStateScreen(onCreateProfile = { viewModel.onShowCreateModal() })
            } else {
                ProfilesContent(
                    profiles = state.profiles,
                    filteredProfiles = state.filteredProfiles,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                    onEdit = { viewModel.onShowCreateModal(it) },
                    onDelete = { viewModel.onDeleteProfile(it) },
                    onInject = {
                        Log.i("ProfilesScreen", "=== ABRIENDO MODAL DE INYECCIÓN FUTUREX ===")
                        Log.i("ProfilesScreen", "Usuario: $username")
                        Log.i("ProfilesScreen", "Usuario presionó botón de inyección en perfil: ${it.name}")
                        Log.i("ProfilesScreen", "Configuraciones de llave: ${it.keyConfigurations.size}")
                        it.keyConfigurations.forEachIndexed { index, config ->
                            Log.i("ProfilesScreen", "  ${index + 1}. ${config.usage} - Slot: ${config.slot} - Tipo: ${config.keyType}")
                        }
                        keyInjectionViewModel.showInjectionModal(it, username)
                    }
                )
            }
        }
    }

    // Modales
    if (state.showCreateModal) {
        CreateProfileModal(
            formData = state.formData,
            availableKeys = state.compatibleKeys, // Usar llaves compatibles en lugar de todas
            ktkCompatibilityWarning = state.ktkCompatibilityWarning, // Pasar advertencia
            onDismiss = { viewModel.onDismissCreateModal() },
            onSave = { viewModel.onSaveProfile() },
            onFormDataChange = { viewModel.onFormDataChange(it) },
            onAddKeyConfig = { viewModel.addKeyConfiguration() },
            onRemoveKeyConfig = { viewModel.removeKeyConfiguration(it) },
            onUpdateKeyConfig = { id, field, value -> viewModel.updateKeyConfiguration(id, field, value) }
        )
    }

    // Modal de importación de perfiles
    if (state.showImportModal) {
        ImportProfileModal(
            importJsonText = state.importJsonText,
            importError = state.importError,
            importWarnings = state.importWarnings,
            onDismiss = { viewModel.onDismissImportModal() },
            onImportJsonChange = { viewModel.onImportJsonChange(it) },
            onImportProfile = { viewModel.onImportProfile() }
        )
    }

    // Modal de inyección de llaves
    KeyInjectionModal(keyInjectionViewModel)
}

@Composable
fun ProfilesSkeletonScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header con estadísticas esqueleto
        item {
            StatisticsHeaderSkeleton()
        }

        // Lista de perfiles esqueleto
        items(5) {
            ProfileCardSkeleton()
        }
    }
}

@Composable
fun StatisticsHeaderSkeleton() {
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
            repeat(3) {
                StatisticItemSkeleton()
            }
        }
    }
}

@Composable
fun StatisticItemSkeleton() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Icono esqueleto
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        // Valor esqueleto
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        // Label esqueleto
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
fun EmptyStateScreen(onCreateProfile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No hay perfiles configurados",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Crea tu primer perfil para comenzar a inyectar llaves criptográficas en dispositivos POS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

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

@Composable
private fun ProfilesContent(
    profiles: List<ProfileEntity>,
    filteredProfiles: List<ProfileEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onEdit: (ProfileEntity) -> Unit,
    onDelete: (ProfileEntity) -> Unit,
    onInject: (ProfileEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header con estadísticas
        item {
            StatisticsHeader(profiles = profiles)
        }

        // Barra de búsqueda
        item {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Buscar perfiles..."
            )
        }

        // Mensaje si no hay resultados
        if (filteredProfiles.isEmpty() && searchQuery.isNotEmpty()) {
            item {
                EmptySearchResults(query = searchQuery)
            }
        }

        // Lista de perfiles filtrados
        items(
            items = filteredProfiles,
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
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Buscar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Limpiar búsqueda",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun EmptySearchResults(query: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "No se encontraron resultados",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "No hay perfiles que coincidan con \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
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
                .padding(12.dp),
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
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ProfileCard(
    profile: ProfileEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInject: () -> Unit,
    modifier: Modifier = Modifier
) {
    // --- Estado derivado y helpers de UI ---
    val appTypeConfig = getAppTypeConfig(profile.applicationType)
    val totalKeys = profile.keyConfigurations.size
    val readyKeys = profile.keyConfigurations.count { it.selectedKey.isNotBlank() }
    val hasAny = totalKeys > 0
    val ktkConfigured = profile.selectedKTKKcv.isNotBlank()
    val isReady = hasAny && readyKeys == totalKeys && ktkConfigured
    val statusColor = when {
        isReady -> MaterialTheme.colorScheme.primary
        hasAny  -> MaterialTheme.colorScheme.secondary
        else    -> MaterialTheme.colorScheme.tertiary
    }
    val statusLabel = when {
        isReady -> "Listo"
        hasAny  -> "Pendiente"
        else    -> "Vacío"
    }
    val progress = if (totalKeys == 0) 0f else readyKeys.toFloat() / totalKeys.toFloat()

    var showOverflow by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Tarjeta de perfil ${profile.name}" }
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ====== CABECERA ======
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar con gradiente + estado
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(appTypeConfig.gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = appTypeConfig.icon,
                        contentDescription = "Tipo de app ${profile.applicationType}",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    // Punto de estado
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }

                // Título + metadatos
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Nombre
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Descripción (opcional)
                    if (profile.description.isNotBlank()) {
                        Text(
                            text = profile.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Fila de badges compactos
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Badge dispositivo (fabricante) - COMPACTO
                        val deviceEmoji = when (profile.deviceType) {
                            "NEWPOS" -> "💻"
                            else -> "🏭"
                        }
                        val deviceLabel = profile.deviceType.ifBlank { "AISINO" }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "$deviceEmoji $deviceLabel",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        // Badge tipo de aplicación
                        Surface(
                            color = appTypeConfig.color.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = profile.applicationType,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = appTypeConfig.color,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        // Contador de llaves
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$readyKeys/$totalKeys",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                    }
                }

                // Menú de desborde
                Box {
                    IconButton(
                        onClick = { showOverflow = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Más acciones"
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showOverflow = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { showOverflow = false; showDeleteConfirm = true }
                        )
                    }
                }
            }

            // ====== SECCIÓN DE KTK (OBLIGATORIO) ======
            Surface(
                color = if (profile.selectedKTKKcv.isNotBlank()) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (profile.selectedKTKKcv.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "KTK",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (profile.selectedKTKKcv.isNotBlank()) {
                                "Configurada: ${profile.selectedKTKKcv.take(8)}"
                            } else {
                                "Requiere configuración"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (profile.selectedKTKKcv.isNotBlank()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }

            // ====== PROGRESO DE LLAVES ======
            if (hasAny) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Llaves: $readyKeys/$totalKeys",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ====== CHIPS DE LLAVES ======
            if (totalKeys > 0) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        items = profile.keyConfigurations.take(10),
                        key = { it.id }
                    ) { config ->
                        KeyConfigChip(config = config)
                    }
                    if (totalKeys > 10) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                tonalElevation = 1.dp
                            ) {
                                Text(
                                    text = "+${totalKeys - 10}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Vacío: callout suave
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Aún no agregas configuraciones de llaves.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ====== ACCIONES ======
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Acción principal destacada
                Button(
                    onClick = { 
                        Log.i("ProfileCard", "=== PRESIONANDO BOTÓN DE INYECCIÓN FUTUREX ===")
                        Log.i("ProfileCard", "Perfil: ${profile.name}")
                        Log.i("ProfileCard", "Estado: $statusLabel (${readyKeys}/${totalKeys} llaves configuradas)")
                        Log.i("ProfileCard", "Configuraciones de llave:")
                        profile.keyConfigurations.forEachIndexed { index, config ->
                            Log.i("ProfileCard", "  ${index + 1}. ${config.usage} - Slot: ${config.slot} - Tipo: ${config.keyType} - Llave: ${if (config.selectedKey.isNotEmpty()) "Configurada" else "No configurada"}")
                        }
                        onInject() 
                    },
                    enabled = isReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("injectButton"),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Inyectar llaves")
                }
            }
        }
    }

    // ====== Confirmación de eliminación ======
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Eliminar perfil") },
            text = { Text("¿Seguro que deseas eliminar \"${profile.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun KeyConfigChip(config: KeyConfiguration) {
    val isConfigured = config.selectedKey.isNotEmpty()

    Surface(
        color = if (isConfigured) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Rounded.Key,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = config.keyType,
                style = MaterialTheme.typography.labelSmall,
                color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// Funciones auxiliares
data class AppTypeConfig(
    val icon: ImageVector,
    val color: Color,
    val gradient: Brush
)

fun getAppTypeConfig(appType: String): AppTypeConfig {
    return when (appType.uppercase()) {
        "RETAIL" -> AppTypeConfig(
            icon = Icons.Rounded.Store,
            color = Color(0xFF2196F3),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFF2196F3), Color(0xFF1976D2))
            )
        )
        "H2H" -> AppTypeConfig(
            icon = Icons.Rounded.Computer,
            color = Color(0xFF4CAF50),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFF4CAF50), Color(0xFF388E3C))
            )
        )
        "POSINT" -> AppTypeConfig(
            icon = Icons.Rounded.PointOfSale,
            color = Color(0xFFFF9800),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFFFF9800), Color(0xFFF57C00))
            )
        )
        "ATM" -> AppTypeConfig(
            icon = Icons.Rounded.AccountBalance,
            color = Color(0xFF9C27B0),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFF9C27B0), Color(0xFF7B1FA2))
            )
        )
        "CUSTOM" -> AppTypeConfig(
            icon = Icons.Rounded.Settings,
            color = Color(0xFF607D8B),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFF607D8B), Color(0xFF455A64))
            )
        )
        else -> AppTypeConfig(
            icon = Icons.Rounded.Apps,
            color = Color(0xFF757575),
            gradient = Brush.linearGradient(
                colors = listOf(Color(0xFF757575), Color(0xFF616161))
            )
        )
    }
}

fun getUsageIcon(usage: String): String {
    return when (usage.uppercase()) {
        "PIN" -> "🔐"
        "MAC" -> "🔒"
        "DATA" -> "📄"
        "KTK" -> "🗝️"
        else -> "🔑"
    }
}

fun getKeyTypeIcon(keyType: String): String {
    return when {
        keyType.contains("PIN", ignoreCase = true) -> "🔐"
        keyType.contains("MAC", ignoreCase = true) -> "🔒"
        keyType.contains("Data", ignoreCase = true) -> "📄"
        keyType.contains("Master", ignoreCase = true) -> "🗝️"
        keyType.contains("DUKPT", ignoreCase = true) -> "🔄"
        else -> "🔑"
    }
}

fun deriveUsageFromKeyType(keyType: String): String {
    return when {
        keyType.contains("PIN", ignoreCase = true) -> "PIN"
        keyType.contains("MAC", ignoreCase = true) -> "MAC"
        keyType.contains("Data", ignoreCase = true) -> "DATA"
        keyType.contains("DUKPT", ignoreCase = true) -> "DUKPT"
        else -> "GENERAL"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileModal(
    formData: ProfileFormData,
    availableKeys: List<InjectedKeyEntity>,
    ktkCompatibilityWarning: String? = null, // Advertencia de compatibilidad KTK
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
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.95f)
                .align(Alignment.Center),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ==== HEADER ====
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (formData.id == null) "Crear" else "Editar",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Configura el perfil y sus llaves",
                                style = MaterialTheme.typography.bodySmall,
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

                // ==== CONTENIDO SCROLLEABLE ====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // --- Información básica ---
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "📋 Información Básica",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = formData.name,
                                    onValueChange = { onFormDataChange(formData.copy(name = it)) },
                                    label = { Text("Nombre") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = formData.description,
                                    onValueChange = { onFormDataChange(formData.copy(description = it)) },
                                    label = { Text("Descripción") },
                                    minLines = 2,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // --- Selector de tipo de aplicación ---
                                var expanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = !expanded }
                                ) {
                                    OutlinedTextField(
                                        value = formData.appType,
                                        onValueChange = {}, // evita escribir
                                        readOnly = true,
                                        label = { Text("Tipo de Aplicación") },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                    )

                                    // Menú más alto
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.heightIn(max = 300.dp)
                                    ) {
                                        listOf("Retail", "H2H", "Posint", "ATM", "Custom").forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type) },
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

                    // --- Configuración del Dispositivo ---
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "📱 Dispositivo de Destino",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Selector de dispositivo
                                var deviceExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = deviceExpanded,
                                    onExpandedChange = { deviceExpanded = !deviceExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = formData.deviceType,
                                        onValueChange = {}, // evita escribir
                                        readOnly = true,
                                        label = { Text("Fabricante/Dispositivo") },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        supportingText = {
                                            Text(
                                                "Selecciona el dispositivo para ajustar las validaciones (ej: rango de slots DUKPT)",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )

                                    // Menú de dispositivos
                                    ExposedDropdownMenu(
                                        expanded = deviceExpanded,
                                        onDismissRequest = { deviceExpanded = false },
                                        modifier = Modifier.heightIn(max = 200.dp)
                                    ) {
                                        listOf("AISINO", "NEWPOS").forEach { device ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(
                                                            text = when (device) {
                                                                "AISINO" -> "🏭"
                                                                "NEWPOS" -> "💻"
                                                                else -> "📱"
                                                            }
                                                        )
                                                        Text(device)
                                                    }
                                                },
                                                onClick = {
                                                    onFormDataChange(formData.copy(deviceType = device))
                                                    deviceExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Información sobre el dispositivo seleccionado
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = when (formData.deviceType) {
                                                "AISINO" -> "Aisino/Vanstone: DUKPT slots 1-10, 3DES máximo"
                                                "NEWPOS" -> "NewPOS: Soporta rangos más amplios de slots"
                                                else -> "Dispositivo desconocido"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- Configuración de KTK (OBLIGATORIO) ---
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "🔐 Cifrado KTK (Obligatorio)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Información obligatoria
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "KTK Requerida",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "Debes seleccionar una llave como KTK para cifrar",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                // Mostrar KTK activa (solo lectura)
                                if (formData.useKTK) {
                                    if (formData.currentKTK == null) {
                                        // No hay KTK activa
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "No hay KTK activa en el almacén",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Ve al almacén de llaves y selecciona una llave AES-256 como KTK activa.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Mostrar KTK activa (solo lectura)
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "KTK activa seleccionada del almacén:",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )

                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = formData.currentKTK.customName.ifEmpty { "KTK ${formData.currentKTK.kcv.take(6)}" },
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                                    )
                                                        Text(
                                                            text = "KCV: ${formData.currentKTK.kcv}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                        )
                                                        Text(
                                                            text = "Creada: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(formData.currentKTK.injectionTimestamp))}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            // Info sobre el uso de KTK
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.tertiary
                                                    )
                                                    Text(
                                                        text = "Esta KTK se usará para cifrar las llaves antes de enviarlas al SubPOS.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- Configuración de llaves ---
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🔑 Llaves",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = onAddKeyConfig) {
                                Icon(Icons.Default.Add, contentDescription = "Agregar llave")
                            }
                        }

                        // Mostrar advertencia de compatibilidad KTK si existe
                        if (ktkCompatibilityWarning != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (ktkCompatibilityWarning.startsWith("❌")) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (ktkCompatibilityWarning.startsWith("❌")) {
                                            Icons.Default.Error
                                        } else {
                                            Icons.Default.Warning
                                        },
                                        contentDescription = null,
                                        tint = if (ktkCompatibilityWarning.startsWith("❌")) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                                    Text(
                                        text = ktkCompatibilityWarning,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (formData.keyConfigurations.isEmpty()) {
                            Text(
                                "No hay llaves configuradas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                formData.keyConfigurations.forEach { config ->
                                    KeyConfigurationItem(
                                        config = config,
                                        availableKeys = availableKeys,
                                        deviceType = formData.deviceType,
                                        onUpdate = { id, field, value -> onUpdateKeyConfig(id, field, value) },
                                        onRemove = { onRemoveKeyConfig(config.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==== FOOTER BOTONES ====
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onSave) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (formData.id == null) "Crear" else "Actualizar",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeyConfigurationItem(
    config: KeyConfiguration,
    availableKeys: List<InjectedKeyEntity>,
    deviceType: String = "AISINO",
    onUpdate: (Long, String, String) -> Unit,
    onRemove: () -> Unit
) {
    val keyTypeOptions = remember {
        listOf(
            "Master Session Key",
            "PIN Encryption Key",
            "MAC Key",
            "Data Encryption Key",
            "DUKPT Initial Key (IPEK)",
            "DUKPT BDK"
        )
    }

    // Estados UI
    var keyTypeExpanded by rememberSaveable { mutableStateOf(false) }
    var keyExpanded by rememberSaveable { mutableStateOf(false) }
    var isExpanded by rememberSaveable { mutableStateOf(false) } // Iniciar contraídas

    // Derivados
    val isKeySelected = remember(config.selectedKey) { config.selectedKey.isNotBlank() }
    val headline = remember(config) { "Config. ${config.id}" }
    
    // Encontrar la llave seleccionada para obtener su algoritmo
    val selectedKeyEntity = remember(config.selectedKey, availableKeys) {
        availableKeys.find { it.kcv == config.selectedKey }
    }
    
    // Resumen compacto para estado colapsado
    val compactSummary = remember(config, selectedKeyEntity) {
        buildString {
            if (config.keyType.isNotBlank()) {
                append("Tipo: ${config.keyType}")
            }
            if (config.slot.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("Slot: ${config.slot}")
            }
            if (config.selectedKey.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("KCV: ${config.selectedKey.take(6)}...")
                // Agregar algoritmo si está disponible
                selectedKeyEntity?.keyAlgorithm?.let { algorithm ->
                    append(" (${algorithm})")
                }
            }
            if (isEmpty()) {
                append("Configuración nueva")
            }
        }
    }
    
    // Indicador de estado de validación
    val hasRequiredData = config.keyType.isNotBlank() && config.slot.isNotBlank() && config.selectedKey.isNotBlank()
    val isDukptKey = config.keyType.contains("DUKPT", ignoreCase = true)
    val isDukptValid = !isDukptKey || config.ksn.length == 20
    val isFullyValid = hasRequiredData && isDukptValid
    
    // Valor a mostrar en el campo de texto (KCV + algoritmo)
    val displayValue = remember(config.selectedKey, selectedKeyEntity) {
        if (config.selectedKey.isBlank()) {
            ""
        } else {
            selectedKeyEntity?.let { key ->
                "${key.kcv} (${key.keyAlgorithm})"
            } ?: config.selectedKey
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ===== Header compacto con meta + controles =====
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isFullyValid) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(getKeyTypeIcon(config.keyType), fontSize = 18.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Configuración de llave",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Indicador de estado
                            if (!isFullyValid) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                        // Resumen compacto
                        Text(
                            text = if (isExpanded) headline else compactSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFullyValid) MaterialTheme.colorScheme.onSurfaceVariant 
                                   else MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Botones de control
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Botón expandir/colapsar
                    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Botón eliminar
                    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar configuración",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // ===== Contenido colapsable =====
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                // ===== Layout responsive (1 o 2 columnas según ancho) =====
                val configuration = LocalConfiguration.current
                val isWide = configuration.screenWidthDp.dp >= 600.dp
                val fieldSpacing = 10.dp

                if (isWide) {
                // 2 columnas: reduce scroll vertical y la percepción de "modal chico"
                Row(horizontalArrangement = Arrangement.spacedBy(fieldSpacing)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(fieldSpacing)
                    ) {
                        // Tipo de llave (solo lectura + menú)
                        ExposedDropdownMenuBox(
                            expanded = keyTypeExpanded,
                            onExpandedChange = { keyTypeExpanded = !keyTypeExpanded }
                        ) {
                            OutlinedTextField(
                                value = config.keyType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tipo de Llave") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyTypeExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = keyTypeExpanded,
                                onDismissRequest = { keyTypeExpanded = false },
                                modifier = Modifier.heightIn(max = 320.dp)
                            ) {
                                keyTypeOptions.forEach { keyType ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(getKeyTypeIcon(keyType))
                                                Text(keyType)
                                            }
                                        },
                                        onClick = {
                                            onUpdate(config.id, "keyType", keyType)
                                            // Derivar 'usage' automáticamente del tipo
                                            val derivedUsage = deriveUsageFromKeyType(keyType)
                                            onUpdate(config.id, "usage", derivedUsage)
                                            keyTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Advertencia para DUKPT Initial Key
                        if (config.keyType.contains("DUKPT Initial", ignoreCase = true)) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "⚠️ DUKPT Initial Key solo para pruebas. Usar DUKPT BDK en producción",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(fieldSpacing)
                    ) {
                        // Slot se mueve aquí
                    }

                    // Slot (validación con restricción DUKPT 1-10 para Aisino según deviceType del perfil)
                    val isDukptKey = config.keyType.contains("DUKPT", ignoreCase = true)
                    val isDukptAisino = isDukptKey && deviceType == "AISINO"

                    // Calcular si el slot está fuera de rango para DUKPT en Aisino
                    val slotDecimal = if (config.slot.isNotEmpty()) config.slot.toIntOrNull(16) else null
                    val isDukptSlotOutOfRange = isDukptAisino && slotDecimal != null && (slotDecimal < 1 || slotDecimal > 10)

                    OutlinedTextField(
                        value = config.slot,
                        onValueChange = { raw ->
                            val filtered = raw.uppercase()
                                .filter { it in "0123456789ABCDEF" }
                                .take(2)

                            // Validar rango para DUKPT en Aisino
                            if (isDukptAisino && filtered.isNotEmpty()) {
                                val decimal = filtered.toIntOrNull(16) ?: 0
                                if (decimal >= 1 && decimal <= 10) {
                                    onUpdate(config.id, "slot", filtered)
                                }
                                // Si está fuera de rango, no actualizar (rechazar silenciosamente)
                            } else {
                                onUpdate(config.id, "slot", filtered)
                            }
                        },
                        label = { Text("Slot (HEX)") },
                        placeholder = { Text(if (isDukptAisino) "01-0A" else "01") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        supportingText = {
                            Text(
                                if (isDukptAisino) {
                                    "DUKPT en Aisino: slots 01-0A (1-10 decimal)"
                                } else {
                                    "Máx. 2 dígitos hexadecimales"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDukptSlotOutOfRange) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        isError = isDukptSlotOutOfRange,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Advertencia si slot está fuera de rango para DUKPT en Aisino
                    if (isDukptSlotOutOfRange) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "❌ Slot ${slotDecimal} fuera de rango. DUKPT en Aisino solo soporta slots 1-10 (0x01-0x0A)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Campo KSN (solo visible para llaves DUKPT)
                    if (isDukptKey) {
                        OutlinedTextField(
                            value = config.ksn,
                            onValueChange = { raw ->
                                val filtered = raw.uppercase()
                                    .filter { it in "0123456789ABCDEF" }
                                    .take(20)
                                onUpdate(config.id, "ksn", filtered)
                            },
                            label = { Text("KSN (Key Serial Number)") },
                            placeholder = { Text("F876543210000000000A") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            supportingText = {
                                Text(
                                    "Exactamente 20 dígitos hexadecimales para DUKPT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (config.ksn.length == 20) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.error
                                )
                            },
                            isError = isDukptKey && config.ksn.length != 20,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(fieldSpacing)
                ) {
                    // Llave seleccionada (readOnly + lista larga scrolleable)
                    ExposedDropdownMenuBox(
                        expanded = keyExpanded,
                        onExpandedChange = { keyExpanded = !keyExpanded }
                    ) {
                        OutlinedTextField(
                            value = displayValue,
                            onValueChange = {}, // no editable
                            readOnly = true,
                            label = { Text("Llave seleccionada (KCV)") },
                            placeholder = { Text("Seleccionar llave...") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyExpanded)
                            },
                            supportingText = {
                                if (!isKeySelected) Text(
                                    "Requerida para inyectar",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        // Menú alto + contenido scrolleable
                        ExposedDropdownMenu(
                            expanded = keyExpanded,
                            onDismissRequest = { keyExpanded = false },
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .widthIn(min = 280.dp)
                        ) {
                            if (availableKeys.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No hay llaves disponibles") },
                                    onClick = { keyExpanded = false }
                                )
                            } else {
                                // Lista con Column scrolleable para evitar problemas de medicion
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    availableKeys.forEach { key ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        key.kcv,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        "${key.keyAlgorithm} • ID: ${key.id}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onUpdate(
                                                    config.id,
                                                    "selectedKey",
                                                    key.kcv
                                                )
                                                keyExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ===== Layout 1 columna (móvil/estrecho) =====
                Column(verticalArrangement = Arrangement.spacedBy(fieldSpacing)) {

                    // Tipo de llave (solo lectura + menú)
                    ExposedDropdownMenuBox(
                        expanded = keyTypeExpanded,
                        onExpandedChange = { keyTypeExpanded = !keyTypeExpanded }
                    ) {
                        OutlinedTextField(
                            value = config.keyType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de Llave") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyTypeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = keyTypeExpanded,
                            onDismissRequest = { keyTypeExpanded = false },
                            modifier = Modifier.heightIn(max = 320.dp)
                        ) {
                            keyTypeOptions.forEach { keyType ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(getKeyTypeIcon(keyType))
                                            Text(keyType)
                                        }
                                    },
                                    onClick = {
                                        onUpdate(config.id, "keyType", keyType)
                                        // Derivar 'usage' automáticamente del tipo
                                        val derivedUsage = deriveUsageFromKeyType(keyType)
                                        onUpdate(config.id, "usage", derivedUsage)
                                        keyTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Advertencia para DUKPT Initial Key
                    if (config.keyType.contains("DUKPT Initial", ignoreCase = true)) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "⚠️ DUKPT Initial Key solo para pruebas. Usar DUKPT BDK en producción",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Slot (validación con restricción DUKPT 1-10 para Aisino según deviceType del perfil)
                    val isDukptKeyMobile = config.keyType.contains("DUKPT", ignoreCase = true)
                    val isDukptAisinoMobile = isDukptKeyMobile && deviceType == "AISINO"

                    // Calcular si el slot está fuera de rango para DUKPT en Aisino
                    val slotDecimalMobile = if (config.slot.isNotEmpty()) config.slot.toIntOrNull(16) else null
                    val isDukptSlotOutOfRangeMobile = isDukptAisinoMobile && slotDecimalMobile != null && (slotDecimalMobile < 1 || slotDecimalMobile > 10)

                    OutlinedTextField(
                        value = config.slot,
                        onValueChange = { raw ->
                            val filtered = raw.uppercase()
                                .filter { it in "0123456789ABCDEF" }
                                .take(2)

                            // Validar rango para DUKPT en Aisino
                            if (isDukptAisinoMobile && filtered.isNotEmpty()) {
                                val decimal = filtered.toIntOrNull(16) ?: 0
                                if (decimal >= 1 && decimal <= 10) {
                                    onUpdate(config.id, "slot", filtered)
                                }
                                // Si está fuera de rango, no actualizar (rechazar silenciosamente)
                            } else {
                                onUpdate(config.id, "slot", filtered)
                            }
                        },
                        label = { Text("Slot (HEX)") },
                        placeholder = { Text(if (isDukptAisinoMobile) "01-0A" else "01") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(
                                if (isDukptAisinoMobile) {
                                    "DUKPT en Aisino: slots 01-0A (1-10 decimal)"
                                } else {
                                    "Máx. 2 dígitos hexadecimales"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDukptSlotOutOfRangeMobile) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        isError = isDukptSlotOutOfRangeMobile,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Advertencia si slot está fuera de rango para DUKPT en Aisino
                    if (isDukptSlotOutOfRangeMobile) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "❌ Slot ${slotDecimalMobile} fuera de rango. DUKPT en Aisino solo soporta slots 1-10 (0x01-0x0A)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Campo KSN (solo visible para llaves DUKPT)
                    if (isDukptKey) {
                        OutlinedTextField(
                            value = config.ksn,
                            onValueChange = { raw ->
                                val filtered = raw.uppercase()
                                    .filter { it in "0123456789ABCDEF" }
                                    .take(20)
                                onUpdate(config.id, "ksn", filtered)
                            },
                            label = { Text("KSN (Key Serial Number)") },
                            placeholder = { Text("F876543210000000000A") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            supportingText = {
                                Text(
                                    "Exactamente 20 dígitos hexadecimales para DUKPT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (config.ksn.length == 20) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.error
                                )
                            },
                            isError = isDukptKey && config.ksn.length != 20,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = keyExpanded,
                        onExpandedChange = { keyExpanded = !keyExpanded }
                    ) {
                        OutlinedTextField(
                            value = displayValue,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Llave seleccionada (KCV)") },
                            placeholder = { Text("Seleccionar llave...") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyExpanded) },
                            supportingText = {
                                if (!isKeySelected) Text(
                                    "Requerida para inyectar",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = keyExpanded,
                            onDismissRequest = { keyExpanded = false },
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            if (availableKeys.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No hay llaves disponibles") },
                                    onClick = { keyExpanded = false }
                                )
                            } else {
                                // Lista con Column scrolleable para evitar problemas de medicion
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    availableKeys.forEach { key ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        key.kcv,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        "${key.keyAlgorithm} • ID: ${key.id}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onUpdate(
                                                    config.id,
                                                    "selectedKey",
                                                    key.kcv
                                                )
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportProfileModal(
    importJsonText: String,
    importError: String?,
    importWarnings: List<String>,
    onDismiss: () -> Unit,
    onImportJsonChange: (String) -> Unit,
    onImportProfile: () -> Unit
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
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ==== HEADER ====
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Importar Perfil",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Pega el JSON del perfil a importar",
                                style = MaterialTheme.typography.bodySmall,
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

                // ==== CONTENIDO SCROLLEABLE ====
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    // Campo de texto para JSON
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "📋 JSON del Perfil",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        OutlinedTextField(
                            value = importJsonText,
                            onValueChange = onImportJsonChange,
                            label = { Text("Pega aquí el JSON del perfil") },
                            placeholder = { 
                                Text(
                                    """{
  "name": "Mi Perfil Test",
  "description": "Perfil de prueba",
  "applicationType": "Retail",
  "useKTK": true,
  "selectedKTKKcv": "ABC123",
  "keyConfigurations": [...]
}""",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            minLines = 15,
                            maxLines = 20,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    // Mostrar errores
                    if (importError != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = importError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Mostrar warnings
                    if (importWarnings.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Advertencias:",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                importWarnings.forEach { warning ->
                                    Text(
                                        text = "• $warning",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(start = 28.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Información sobre el formato esperado
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Formato esperado:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "• name: Nombre del perfil (requerido)\n• applicationType: Tipo de aplicación (requerido)\n• description: Descripción opcional\n• useKTK: true (siempre requerida)\n• selectedKTKKcv: KCV de la KTK seleccionada (requerida)\n• keyConfigurations: Array de configuraciones de llaves",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 24.dp)
                            )
                        }
                    }
                }

                // ==== FOOTER BOTONES ====
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onImportProfile,
                            enabled = importJsonText.trim().isNotEmpty()
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Importar", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}


