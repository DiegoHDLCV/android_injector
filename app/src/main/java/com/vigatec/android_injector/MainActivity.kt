package com.vigatec.android_injector

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.vigatec.android_injector.ui.Navigator
import com.vigatec.android_injector.ui.navigation.AppNavHost
import com.vigatec.android_injector.ui.theme.MultimarcaTheme
import com.vigatec.android_injector.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MultimarcaTheme {
                MyApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }
}

@Composable
fun MyApp(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val TAG_APP_UI_EVENT_OBSERVER = "AppUIEventObserver"

    // Cambiar key1 a Unit y añadir log al inicio del LaunchedEffect
    LaunchedEffect(key1 = Unit) { // MODIFICADO: key1 = Unit
        // NUEVO LOG para verificar que el LaunchedEffect se activa
        Log.i(TAG_APP_UI_EVENT_OBSERVER, "LaunchedEffect en MyApp está ACTIVO y listo para observar uiEvents.")
        viewModel.uiEvents.collectLatest { event ->
            Log.i(TAG_APP_UI_EVENT_OBSERVER, "Evento de UI recolectado en MyApp desde MainViewModel: $event. Pasando al Navigator.")
            Navigator.navigate(navController, event)
        }
    }

    Scaffold { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AppNavHost(navController = navController)
        }
    }
}
