package com.vigatec.injector.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vigatec.injector.ui.navigation.MainScreen
import com.vigatec.injector.ui.navigation.MainNavGraph
import com.vigatec.injector.util.PermissionProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@EntryPoint
@InstallIn(ActivityComponent::class)
interface MainScaffoldPermissionProviderEntryPoint {
    fun permissionProvider(): PermissionProvider
}

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
    onNavigateToConfig: () -> Unit = {},
    onNavigateToExportImport: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val permissionProvider = remember {
        EntryPointAccessors.fromActivity(
            context as android.app.Activity,
            MainScaffoldPermissionProviderEntryPoint::class.java
        ).permissionProvider()
    }
    val userPermissions by permissionProvider.userPermissions.collectAsState()

    Scaffold(
        topBar = { MainTopAppBar(onNavigateToConfig = onNavigateToConfig) },
        bottomBar = { AppBottomBar(navController = navController, userPermissions = userPermissions) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            MainNavGraph(
                navController = navController,
                username = username,
                onNavigateToExportImport = onNavigateToExportImport
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(onNavigateToConfig: () -> Unit = {}) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    TopAppBar(
        title = { Text("Injector") },
        actions = {
            // Botón de configuración
            IconButton(onClick = onNavigateToConfig) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configuración"
                )
            }

            IconButton(onClick = { showMenu = !showMenu }) {
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
    val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val fileName = "comm-logs-${'$'}{dateFormat.format(Date())}.txt"
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
fun AppBottomBar(navController: NavHostController, userPermissions: Set<String>) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        // Filtrar destinos según permisos
        val filteredDestinations = bottomBarDestinations.filter { destination ->
            when (destination.route) {
                MainScreen.Dashboard.route -> true // Dashboard siempre visible
                MainScreen.KeyVault.route -> userPermissions.contains(PermissionProvider.KEY_VAULT)
                MainScreen.Ceremony.route -> {
                    // Ceremony visible si tiene permiso para KEK o Operacional
                    userPermissions.contains(PermissionProvider.CEREMONY_KEK) ||
                    userPermissions.contains(PermissionProvider.CEREMONY_OPERATIONAL)
                }
                MainScreen.Profiles.route -> userPermissions.contains(PermissionProvider.MANAGE_PROFILES)
                else -> false
            }
        }

        filteredDestinations.forEach { screen ->
            AddItem(
                screen = screen,
                currentDestination = currentDestination,
                navController = navController
            )
        }
    }
}

@Composable
fun RowScope.AddItem(
    screen: BottomBarDestination,
    currentDestination: NavDestination?,
    navController: NavHostController
) {
    NavigationBarItem(
        icon = { Icon(imageVector = screen.icon, contentDescription = "Navigation Icon") },
        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
        onClick = {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    )
}