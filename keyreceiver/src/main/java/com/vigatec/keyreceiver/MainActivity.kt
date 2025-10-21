package com.vigatec.keyreceiver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.vigatec.keyreceiver.ui.Navigator
import com.vigatec.keyreceiver.ui.navigation.AppNavHost
import com.vigatec.keyreceiver.ui.theme.MultimarcaTheme
import com.vigatec.keyreceiver.viewmodel.MainViewModel
import com.vigatec.keyreceiver.viewmodel.ConnectionStatus
import com.vigatec.keyreceiver.util.LogcatReader
import com.vigatec.keyreceiver.util.LogFileWriter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        // CAMBIO AQUÍ: Quitar 'private' para hacerlo accesible, o usar 'internal'
        const val TAG = "MainActivityPermissions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MultimarcaTheme {
                PermissionProtectedContent()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

}

@Composable
fun PermissionProtectedContent(appViewModel: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var hasStoragePermissions by remember { mutableStateOf(checkInitialPermissions(context)) }
    var initialCheckDone by remember { mutableStateOf(false) }

    val requestManageStoragePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Se verifica de nuevo después de que el usuario vuelve de la pantalla de configuración
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.i(MainActivity.TAG, "MANAGE_EXTERNAL_STORAGE Granted after settings.")
                    hasStoragePermissions = true
                } else {
                    Log.w(MainActivity.TAG, "MANAGE_EXTERNAL_STORAGE Denied after settings.")
                    Toast.makeText(context, "Permiso de acceso a todos los archivos denegado.", Toast.LENGTH_LONG).show()
                    // Aquí podrías decidir cerrar la app o mostrar un mensaje permanente
                }
            }
        }

    val requestLegacyStoragePermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
            val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false

            if (writeGranted && readGranted) {
                Log.i(MainActivity.TAG, "Legacy READ/WRITE_EXTERNAL_STORAGE Granted.")
                hasStoragePermissions = true
            } else {
                Log.w(MainActivity.TAG, "Legacy READ/WRITE_EXTERNAL_STORAGE Denied.")
                Toast.makeText(context, "Permisos de almacenamiento denegados.", Toast.LENGTH_LONG).show()
                // Aquí podrías decidir cerrar la app o mostrar un mensaje permanente
            }
        }

    // Ejecutar la solicitud de permisos al inicio
    LaunchedEffect(Unit) {
        if (!hasStoragePermissions) {
            requestPermissions(
                context = context,
                onManageStorage = { intent -> requestManageStoragePermission.launch(intent) },
                onLegacyStorage = { perms -> requestLegacyStoragePermissions.launch(perms) }
            )
        }
        initialCheckDone = true
    }

    if (!initialCheckDone || (!hasStoragePermissions && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)) {
        // Loader de permisos
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Text("Verificando permisos...", modifier = Modifier.padding(top = 8.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && initialCheckDone && !hasStoragePermissions) {
                    Button(onClick = {
                        requestPermissions(
                            context = context,
                            onManageStorage = { intent -> requestManageStoragePermission.launch(intent) },
                            onLegacyStorage = { perms -> requestLegacyStoragePermissions.launch(perms) }
                        )
                    }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Reintentar otorgar permiso")
                    }
                    Text(
                        "La aplicación requiere acceso a todos los archivos para funcionar correctamente. Por favor, otórguelo en la siguiente pantalla.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    } else if (hasStoragePermissions) {
        // Permisos listos => Montamos la app (SplashScreen realizará la init real)
        Log.d(MainActivity.TAG, "Permisos OK, mostrando MyApp (Splash gestionará inicialización de SDKs).")
        MyApp(viewModel = appViewModel)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Los permisos de almacenamiento son necesarios para usar la aplicación.")
                Button(onClick = {
                    requestPermissions(
                        context = context,
                        onManageStorage = { intent -> requestManageStoragePermission.launch(intent) },
                        onLegacyStorage = { perms -> requestLegacyStoragePermissions.launch(perms) }
                    )
                }) { Text("Otorgar Permisos") }
            }
        }
    }
}

// Función auxiliar para verificar permisos iniciales
private fun checkInitialPermissions(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

// Función auxiliar para solicitar permisos
private fun requestPermissions(
    context: android.content.Context,
    onManageStorage: (Intent) -> Unit,
    onLegacyStorage: (Array<String>) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Log.i(MainActivity.TAG, "Requesting MANAGE_EXTERNAL_STORAGE.")
        Toast.makeText(context, "Se requiere permiso para acceder a todos los archivos.", Toast.LENGTH_LONG).show()
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            onManageStorage(intent)
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Error launching MANAGE_APP_ALL_FILES_ACCESS_PERMISSION settings", e)
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) // Fallback
            onManageStorage(intent)
        }
    } else {
        Log.i(MainActivity.TAG, "Requesting legacy READ/WRITE_EXTERNAL_STORAGE.")
        val permissionsToRequest = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        onLegacyStorage(permissionsToRequest)
    }
}

@Composable
fun MyApp(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val TAG_APP_UI_EVENT_OBSERVER = "AppUIEventObserver"
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    // Cambiar key1 a Unit y añadir log al inicio del LaunchedEffect
    LaunchedEffect(key1 = Unit) { // MODIFICADO: key1 = Unit
        // NUEVO LOG para verificar que el LaunchedEffect se activa
        Log.i(TAG_APP_UI_EVENT_OBSERVER, "LaunchedEffect en MyApp está ACTIVO y listo para observar uiEvents.")
        viewModel.uiEvent.collectLatest { event ->
            Log.i(TAG_APP_UI_EVENT_OBSERVER, "Evento de UI recolectado en MyApp desde MainViewModel: $event. Pasando al Navigator.")
            Navigator.navigate(navController, event)
        }
    }

    Scaffold { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
        ) {
            AppNavHost(navController = navController)
        }
    }
}
// Los paneles de logs se renderizan dentro de MainScreen para que compartan su scroll

@Composable
private fun ConnectionStatusBanner(status: ConnectionStatus) {
    val (bg, fg, text) = when (status) {
        ConnectionStatus.LISTENING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "SubPOS escuchando"
        )
        ConnectionStatus.INITIALIZING, ConnectionStatus.OPENING -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Inicializando comunicación…"
        )
        ConnectionStatus.CLOSING -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Cerrando comunicación…"
        )
        ConnectionStatus.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Error de comunicación"
        )
        ConnectionStatus.DISCONNECTED -> Triple(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            "Desconectado"
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = bg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text = text, color = fg)
            }
        }
    }
}
