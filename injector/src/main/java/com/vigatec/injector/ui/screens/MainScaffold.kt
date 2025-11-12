package com.vigatec.injector.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vigatec.injector.ui.navigation.MainScreen
import com.vigatec.injector.ui.navigation.MainNavGraph
import com.vigatec.injector.util.PermissionManager
import com.vigatec.injector.util.PermissionProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BottomBarDestination(
    val route: String,
    val icon: ImageVector
)

val bottomBarDestinations = listOf(
    BottomBarDestination(MainScreen.Dashboard.route, Icons.Default.Dashboard),
    BottomBarDestination(MainScreen.KeyVault.route, Icons.Default.VpnKey),
    BottomBarDestination(MainScreen.Ceremony.route, Icons.Default.Security),
    BottomBarDestination(MainScreen.Profiles.route, Icons.Default.AccountBox)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    username: String,
    userRole: String?,
    permissionProvider: PermissionProvider,
    onNavigateToConfig: () -> Unit = {},
    onNavigateToExportImport: () -> Unit = {}
) {
    val navController = rememberNavController()
    var isCeremonyInProgress by remember { mutableStateOf(false) }
    val userPermissions by permissionProvider.userPermissions.collectAsState()

    LaunchedEffect(username) {
        if (username.isNotBlank()) {
            try {
                permissionProvider.loadPermissions(username)
            } catch (e: Exception) {
                android.util.Log.e("MainScaffold", "Error al cargar permisos para $username", e)
            }
        }
    }

    val hasCeremonyPermission = remember(userPermissions) {
        userPermissions.contains(PermissionProvider.CEREMONY_KEK) ||
            userPermissions.contains(PermissionProvider.CEREMONY_OPERATIONAL)
    }
    val canShowCeremony = remember(userRole, hasCeremonyPermission) {
        userRole != PermissionManager.ROLE_OPERATOR && hasCeremonyPermission
    }
    val currentDestinations = remember(canShowCeremony) {
        if (canShowCeremony) {
            bottomBarDestinations
        } else {
            bottomBarDestinations.filterNot { it.route == MainScreen.Ceremony.route }
        }
    }

    Scaffold(
        topBar = { MainTopAppBar(onNavigateToConfig = onNavigateToConfig, isCeremonyInProgress = isCeremonyInProgress) },
        bottomBar = {
            AppBottomBar(
                navController = navController,
                destinations = currentDestinations,
                isCeremonyInProgress = isCeremonyInProgress
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            MainNavGraph(
                navController = navController,
                username = username,
                onNavigateToExportImport = onNavigateToExportImport,
                onCeremonyStateChanged = { inProgress ->
                    isCeremonyInProgress = inProgress
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(onNavigateToConfig: () -> Unit = {}, isCeremonyInProgress: Boolean = false) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    TopAppBar(
        title = { Text("Injector") },
        actions = {
            // Botón de configuración (deshabilitado durante ceremonia)
            IconButton(
                onClick = onNavigateToConfig,
                enabled = !isCeremonyInProgress
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configuración"
                )
            }

            IconButton(
                onClick = { showMenu = !showMenu },
                enabled = !isCeremonyInProgress
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More"
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Exportar logs") },
                    onClick = {
                        showMenu = false
                        exportLogs(context)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Exportar Logs"
                        )
                    })
            }
        }
    )
}

private fun exportLogs(context: Context) {
    val fileName = "comm-logs-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.txt"
    val logFile = File(context.getExternalFilesDir("Download"), fileName)

    if (logFile.exists()) {
        val uri = FileProvider.getUriForFile(context, "com.vigatec.android_injector.provider", logFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Exportar logs")
        context.startActivity(chooser)
    } else {
        // Handle file not found, maybe show a Toast
    }
}


@Composable
fun AppBottomBar(
    navController: NavHostController,
    destinations: List<BottomBarDestination>,
    isCeremonyInProgress: Boolean = false
) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        destinations.forEach { screen ->
            AddItem(
                screen = screen,
                currentDestination = currentDestination,
                navController = navController,
                enabled = !isCeremonyInProgress
            )
        }
    }
}

@Composable
fun RowScope.AddItem(
    screen: BottomBarDestination,
    currentDestination: NavDestination?,
    navController: NavHostController,
    enabled: Boolean = true
) {
    NavigationBarItem(
        icon = { Icon(imageVector = screen.icon, contentDescription = "Navigation Icon") },
        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
        enabled = enabled,
        onClick = {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    )
}