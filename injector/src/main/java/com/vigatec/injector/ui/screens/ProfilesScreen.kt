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
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.entities.KeyConfiguration
import com.example.persistence.entities.ProfileEntity
import com.vigatec.injector.ui.components.ProfileCardSkeleton
import com.vigatec.injector.viewmodel.ProfileFormData
import com.vigatec.injector.viewmodel.ProfileViewModel
import com.vigatec.injector.viewmodel.KeyInjectionViewModel
import com.vigatec.injector.viewmodel.InjectionStatus
import android.util.Log

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
                title = { Text("Perfiles de Inyección", fontWeight = FontWeight.Bold) },
                actions = {
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
                    onEdit = { viewModel.onShowCreateModal(it) },
                    onDelete = { viewModel.onDeleteProfile(it) },
                    onInject = {
                        Log.i("ProfilesScreen", "=== ABRIENDO MODAL DE INYECCIÓN FUTUREX ===")
                        Log.i("ProfilesScreen", "Usuario presionó botón de inyección en perfil: ${it.name}")
                        Log.i("ProfilesScreen", "Configuraciones de llave: ${it.keyConfigurations.size}")
                        it.keyConfigurations.forEachIndexed { index, config ->
                            Log.i("ProfilesScreen", "  ${index + 1}. ${config.usage} - Slot: ${config.slot} - Tipo: ${config.keyType}")
                        }
                        keyInjectionViewModel.showInjectionModal(it)
                    }
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
    onInject: () -> Unit,
    modifier: Modifier = Modifier
) {
    // --- Estado derivado y helpers de UI ---
    val appTypeConfig = getAppTypeConfig(profile.applicationType)
    val totalKeys = profile.keyConfigurations.size
    val readyKeys = profile.keyConfigurations.count { it.selectedKey.isNotBlank() }
    val hasAny = totalKeys > 0
    val isReady = hasAny && readyKeys == totalKeys
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ====== CABECERA ======
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar con gradiente + estado
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(appTypeConfig.gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = appTypeConfig.icon,
                        contentDescription = "Tipo de app ${profile.applicationType}",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
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

                // Título + descripción + meta
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.description.isNotBlank()) {
                        Text(
                            text = profile.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Fila de metadatos compactos
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Badge tipo de aplicación
                        Surface(
                            color = appTypeConfig.color.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = profile.applicationType,
                                style = MaterialTheme.typography.labelSmall,
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
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$readyKeys/$totalKeys",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Estado (chip)
                        Surface(
                            color = statusColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
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

            // ====== PROGRESO (si aplica) ======
            if (hasAny) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isReady) "Todo listo para inyectar" else "Configuración pendiente",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ====== LLAVES / CHIPS ======
            if (totalKeys > 0) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        "KEK" -> "🗝️"
        else -> "🔑"
    }
}

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

                    // --- Configuración de Cifrado KEK ---
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "🔐 Configuración de Cifrado",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Toggle para activar KEK
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Usar cifrado KEK",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "Cifra todas las llaves antes de enviarlas",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Switch(
                                        checked = formData.useKEK,
                                        onCheckedChange = { onFormDataChange(formData.copy(useKEK = it)) }
                                    )
                                }

                                // Selector de KEK (solo visible si useKEK está activado)
                                if (formData.useKEK) {
                                    // Filtrar solo las KEKs disponibles
                                    val availableKEKs = remember(availableKeys) {
                                        availableKeys.filter { it.isKEK && (it.status == "ACTIVE" || it.status == "EXPORTED") }
                                    }

                                    if (availableKEKs.isEmpty()) {
                                        // No hay KEKs disponibles
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
                                                Text(
                                                    text = "No hay KEKs disponibles. Genera una KEK en la ceremonia de llaves primero.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    } else {
                                        // Selector de KEK
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Seleccionar KEK a usar:",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )

                                            // Radio buttons para cada KEK
                                            availableKEKs.forEach { kek ->
                                                val isSelected = formData.selectedKEKKcv == kek.kcv

                                                Card(
                                                    onClick = { onFormDataChange(formData.copy(selectedKEKKcv = kek.kcv)) },
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isSelected) {
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                        } else {
                                                            MaterialTheme.colorScheme.surface
                                                        }
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = if (isSelected) {
                                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                                    } else {
                                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = isSelected,
                                                            onClick = { onFormDataChange(formData.copy(selectedKEKKcv = kek.kcv)) }
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.Lock,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = kek.customName.ifEmpty { "KEK ${kek.kcv.take(6)}" },
                                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                                            )
                                                            Text(
                                                                text = "KCV: ${kek.kcv}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                            )
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                // Badge de estado
                                                                Surface(
                                                                    color = when (kek.status) {
                                                                        "ACTIVE" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                                                        "EXPORTED" -> Color(0xFF2196F3).copy(alpha = 0.2f)
                                                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                                                    },
                                                                    shape = RoundedCornerShape(4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = kek.status,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = when (kek.status) {
                                                                            "ACTIVE" -> Color(0xFF4CAF50)
                                                                            "EXPORTED" -> Color(0xFF2196F3)
                                                                            else -> MaterialTheme.colorScheme.onSurface
                                                                        },
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                                // Fecha de creación
                                                                Text(
                                                                    text = "Creada: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(kek.injectionTimestamp))}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Warning sobre exportación automática
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
                                                    text = "La KEK se exportará automáticamente al SubPOS la primera vez que inyectes este perfil.",
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
    onUpdate: (Long, String, String) -> Unit,
    onRemove: () -> Unit
) {
    val usageOptions = remember { listOf("PIN", "MAC", "DATA", "KEK") }
    val keyTypeOptions = remember { listOf("TDES", "AES", "DUKPT_TDES", "DUKPT_AES", "PIN", "MAC", "DATA") }

    // Estados UI
    var usageExpanded by rememberSaveable { mutableStateOf(false) }
    var keyTypeExpanded by rememberSaveable { mutableStateOf(false) }
    var keyExpanded by rememberSaveable { mutableStateOf(false) }

    // Derivados
    val isKeySelected = remember(config.selectedKey) { config.selectedKey.isNotBlank() }
    val headline = remember(config) { "Config. ${config.id}" }

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ===== Header compacto con meta + eliminar =====
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(getUsageIcon(config.usage), fontSize = 18.sp)
                    }
                    Column {
                        Text(
                            text = "Configuración de llave",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Meta en una línea
                        Text(
                            text = buildString {
                                append(headline)
                                append(" • Uso: ${config.usage.ifBlank { "—" }}")
                                append(" • Tipo: ${config.keyType.ifBlank { "—" }}")
                                append(" • Slot: ${config.slot.ifBlank { "—" }}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                CompositionLocalProvider(LocalMinimumTouchTargetEnforcement provides false) {
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
                        // Uso (solo lectura + menú)
                        ExposedDropdownMenuBox(
                            expanded = usageExpanded,
                            onExpandedChange = { usageExpanded = !usageExpanded }
                        ) {
                            OutlinedTextField(
                                value = config.usage,
                                onValueChange = {}, // evita escritura
                                readOnly = true,
                                label = { Text("Uso") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = usageExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = usageExpanded,
                                onDismissRequest = { usageExpanded = false },
                                modifier = Modifier.heightIn(max = 320.dp) // menú más alto
                            ) {
                                usageOptions.forEach { usage ->
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
                    }

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
                                onValueChange = {}, // evita escritura
                                readOnly = true,
                                label = { Text("Tipo de llave") },
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
                                keyTypeOptions.forEach { type ->
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
                    }

                    // Slot (validación suave HEX 2 dígitos)
                    OutlinedTextField(
                        value = config.slot,
                        onValueChange = { raw ->
                            val filtered = raw.uppercase()
                                .filter { it in "0123456789ABCDEF" }
                                .take(2)
                            onUpdate(config.id, "slot", filtered)
                        },
                        label = { Text("Slot (HEX)") },
                        placeholder = { Text("01") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        supportingText = {
                            Text(
                                "Máx. 2 dígitos hexadecimales",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Campo KSN (solo visible para llaves DUKPT)
                    val isDukptKey = config.keyType.contains("DUKPT", ignoreCase = true)
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
                            value = config.selectedKey,
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
                                                        "ID: ${key.id}",
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

                    ExposedDropdownMenuBox(
                        expanded = usageExpanded,
                        onExpandedChange = { usageExpanded = !usageExpanded }
                    ) {
                        OutlinedTextField(
                            value = config.usage,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Uso") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = usageExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = usageExpanded,
                            onDismissRequest = { usageExpanded = false },
                            modifier = Modifier.heightIn(max = 320.dp)
                        ) {
                            usageOptions.forEach { usage ->
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

                    ExposedDropdownMenuBox(
                        expanded = keyTypeExpanded,
                        onExpandedChange = { keyTypeExpanded = !keyTypeExpanded }
                    ) {
                        OutlinedTextField(
                            value = config.keyType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de llave") },
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
                            keyTypeOptions.forEach { type ->
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

                    // Slot (validación suave HEX 2 dígitos)
                    OutlinedTextField(
                        value = config.slot,
                        onValueChange = { raw ->
                            val filtered = raw.uppercase()
                                .filter { it in "0123456789ABCDEF" }
                                .take(2)
                            onUpdate(config.id, "slot", filtered)
                        },
                        label = { Text("Slot (HEX)") },
                        placeholder = { Text("01") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(
                                "Máx. 2 dígitos hexadecimales",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Campo KSN (solo visible para llaves DUKPT)
                    val isDukptKey = config.keyType.contains("DUKPT", ignoreCase = true)
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
                            value = config.selectedKey,
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
                                                        "ID: ${key.id}",
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


