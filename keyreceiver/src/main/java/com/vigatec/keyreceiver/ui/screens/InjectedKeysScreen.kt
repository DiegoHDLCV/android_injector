
package com.vigatec.keyreceiver.ui.screens

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.persistence.entities.InjectedKeyEntity
import com.vigatec.keyreceiver.viewmodel.InjectedKeysViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InjectedKeysScreen(
    navController: NavController,
    viewModel: InjectedKeysViewModel = hiltViewModel()
) {
    val keys by viewModel.filteredKeys.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val showDeleteModal by viewModel.showDeleteModal.collectAsState()
    val selectedKeyForDeletion by viewModel.selectedKeyForDeletion.collectAsState()
    
    // Estados de filtros
    val filterAlgorithm by viewModel.filterAlgorithm.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val filterKTKType by viewModel.filterKTKType.collectAsState()
    val searchText by viewModel.searchText.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { message ->
            // Aquí podrías mostrar un Snackbar si tienes un estado para ello
            Log.d("InjectedKeysScreen", "Snackbar message: $message")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Llaves Inyectadas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { navController.navigate("crypto_test_screen") },
                        enabled = !loading && keys.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Science,
                            contentDescription = "Pruebas de Cifrado"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshKeys() },
                        enabled = !loading
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refrescar"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onClearAllRequested() }, // MODIFICADO
                        enabled = !loading && keys.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Limpiar Todo"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Barra de filtros - SIEMPRE VISIBLE
            FiltersBar(
                filterAlgorithm = filterAlgorithm,
                filterStatus = filterStatus,
                filterKTKType = filterKTKType,
                searchText = searchText,
                onFilterAlgorithmChange = { viewModel.updateFilterAlgorithm(it) },
                onFilterStatusChange = { viewModel.updateFilterStatus(it) },
                onFilterKTKTypeChange = { viewModel.updateFilterKTKType(it) },
                onSearchTextChange = { viewModel.updateSearchText(it) }
            )
            
            // Contenido principal
            Box(modifier = Modifier.fillMaxSize()) {
                if (loading) {
                    InjectedKeysSkeletonScreen()
                } else if (keys.isEmpty()) {
                    EmptyKeysScreen()
                } else {
                    // Lista de llaves agrupadas por secciones
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Separar llaves por tipo
                        val ktks = keys.filter { it.isKEK && it.kekType == "KEK_TRANSPORT" }
                        val kekStorage = keys.filter { it.isKEK && it.kekType == "KEK_STORAGE" }
                        val operational = keys.filter { !it.isKEK }
                        
                        // Sección KTKs (Key Transfer Keys)
                        if (ktks.isNotEmpty()) {
                            item {
                                SectionHeader(title = "KTK (Key Transfer Key)")
                            }
                            items(
                                items = ktks,
                                key = { it.id }
                            ) { key ->
                                CompactKeyCard(
                                    key = key,
                                    onDeleteClick = { viewModel.onDeleteKey(key) },
                                    onSetAsKTKClick = { viewModel.setAsKTK(key) },
                                    onRemoveAsKTKClick = { viewModel.removeAsKTK(key) }
                                )
                            }
                        }
                        
                        // Sección KEK Storage (si existe)
                        if (kekStorage.isNotEmpty()) {
                            item {
                                SectionHeader(title = "KEK Storage")
                            }
                            items(
                                items = kekStorage,
                                key = { it.id }
                            ) { key ->
                                CompactKeyCard(
                                    key = key,
                                    onDeleteClick = { viewModel.onDeleteKey(key) },
                                    onSetAsKTKClick = { viewModel.setAsKTK(key) },
                                    onRemoveAsKTKClick = { viewModel.removeAsKTK(key) }
                                )
                            }
                        }
                        
                        // Sección Llaves Operacionales
                        if (operational.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Llaves Operacionales")
                            }
                            items(
                                items = operational,
                                key = { it.id }
                            ) { key ->
                                CompactKeyCard(
                                    key = key,
                                    onDeleteClick = { viewModel.onDeleteKey(key) },
                                    onSetAsKTKClick = { viewModel.setAsKTK(key) },
                                    onRemoveAsKTKClick = { viewModel.removeAsKTK(key) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de confirmación de eliminación individual
    if (showDeleteModal && selectedKeyForDeletion != null) {
        ConfirmationDialog(
            icon = Icons.Default.Delete,
            title = "Eliminar Llave",
            text = "¿Estás seguro de que quieres eliminar la llave con KCV ${selectedKeyForDeletion!!.kcv}? Esta acción no se puede deshacer.",
            onConfirm = {
                viewModel.confirmDeleteKey()
            },
            onDismiss = {
                viewModel.dismissDeleteModal()
            }
        )
    }

    // Modal de confirmación para borrar todo
    val showClearAllModal by viewModel.showClearAllModal.collectAsState()
    if (showClearAllModal) {
        ConfirmationDialog(
            icon = Icons.Default.DeleteSweep,
            title = "Eliminar Todas las Llaves",
            text = "¿Estás seguro de que quieres eliminar TODAS las llaves del dispositivo y del historial? Esta acción es irreversible.",
            onConfirm = { 
                viewModel.dismissClearAllModal()
                viewModel.deleteAllKeys() 
            },
            onDismiss = { viewModel.dismissClearAllModal() }
        )
    }
}

@Composable
fun InjectedKeysSkeletonScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(6) {
            InjectedKeyCardSkeleton()
        }
    }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun InjectedKeyCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SkeletonBox(
                        modifier = Modifier.width(100.dp),
                        height = 14.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonBox(
                        modifier = Modifier.width(80.dp),
                        height = 12.dp
                    )
                }
                SkeletonBox(
                    modifier = Modifier.width(60.dp),
                    height = 24.dp,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SkeletonBox(
                            modifier = Modifier.width(60.dp),
                            height = 12.dp
                        )
                        SkeletonBox(
                            modifier = Modifier.width(100.dp),
                            height = 12.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyKeysScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.VpnKey,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No hay llaves inyectadas",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Las llaves que inyectes en dispositivos POS aparecerán aquí para su gestión y auditoría.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CompactKeyCard(
    key: InjectedKeyEntity,
    onDeleteClick: () -> Unit,
    onSetAsKTKClick: () -> Unit,
    onRemoveAsKTKClick: () -> Unit
) {
    val isEnabled = key.status != "DELETING"

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (key.status == "FAILED") BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = key.keyType,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row {
                    if (key.isKEK) {
                        KEKBadge(type = when(key.kekType) {
                            "KEK_TRANSPORT" -> "KTK"
                            "KEK_STORAGE" -> "KEK"
                            else -> "KEK"
                        })
                        Spacer(Modifier.width(4.dp))
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Slot ${key.keySlot}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            // KCV Prominente
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "KCV",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "KCV: ${key.kcv.uppercase()}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Algoritmo y Fecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = key.keyAlgorithm,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = remember {
                        SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(key.injectionTimestamp))
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            // Acciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onDeleteClick,
                        enabled = isEnabled,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Borrar",
                            modifier = Modifier.size(16.dp),
                            tint = if (isEnabled) MaterialTheme.colorScheme.error else Color.Gray
                        )
                    }
                    if (!key.isKEK) {
                        IconButton(
                            onClick = onSetAsKTKClick,
                            enabled = isEnabled,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Usar como KTK",
                                modifier = Modifier.size(16.dp),
                                tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onRemoveAsKTKClick,
                            enabled = isEnabled,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = "Quitar como KTK",
                                modifier = Modifier.size(16.dp),
                                tint = if (isEnabled) MaterialTheme.colorScheme.secondary else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    isEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color.Gray
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
    }
}

@Composable
fun StatusChip(text: String, color: Color, isDeleting: Boolean = false) {
    Box(
        modifier = Modifier
            .background(color, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = Color.White,
                    strokeWidth = 1.5.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = if (isDeleting) "BORRANDO" else text,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun ConfirmationDialog(
    icon: ImageVector,
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        icon = { Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.error) },
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun KEKBadge(type: String = "KEK") {
    Box(
        modifier = Modifier
            .background(
                when(type) {
                    "KTK" -> Color(0xFF4CAF50)
                    "KEK" -> Color(0xFF2196F3)
                    else -> Color(0xFF9C27B0)
                },
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = type,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Divider(modifier = Modifier.weight(1f))
    }
}

@Composable
fun FiltersBar(
    filterAlgorithm: String,
    filterStatus: String,
    filterKTKType: String,
    searchText: String,
    onFilterAlgorithmChange: (String) -> Unit,
    onFilterStatusChange: (String) -> Unit,
    onFilterKTKTypeChange: (String) -> Unit,
    onSearchTextChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                label = { Text("Buscar por KCV o nombre...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Buscar"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterDropdown(
                    label = "Algoritmo",
                    options = listOf("Todos", "3DES", "AES-128", "AES-192", "AES-256"),
                    selectedOption = filterAlgorithm,
                    onOptionSelected = onFilterAlgorithmChange,
                    modifier = Modifier.weight(1f)
                )
                FilterDropdown(
                    label = "Estado",
                    options = listOf("Todos", "SUCCESSFUL", "GENERATED", "ACTIVE", "EXPORTED", "INACTIVE"),
                    selectedOption = filterStatus,
                    onOptionSelected = onFilterStatusChange,
                    modifier = Modifier.weight(1f)
                )
            }
            FilterDropdown(
                label = "Tipo",
                options = listOf("Todas", "Solo KTK", "Solo Operacionales"),
                selectedOption = filterKTKType,
                onOptionSelected = onFilterKTKTypeChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
