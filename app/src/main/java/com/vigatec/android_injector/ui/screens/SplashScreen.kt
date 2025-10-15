package com.vigatec.android_injector.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vigatec.android_injector.ui.Navigator
import com.vigatec.android_injector.ui.events.UiEvent
import com.vigatec.android_injector.viewmodel.SplashViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavHostController,
    splashViewModel: SplashViewModel = hiltViewModel() // Inyecta el ViewModel modificado
) {
    val initState by splashViewModel.initState.collectAsState()

    // Inicia la lógica de inicialización del ViewModel cuando el Composable se lanza por primera vez.
    LaunchedEffect(key1 = Unit) {
        splashViewModel.initializeApplication()
    }

    // Escucha los UiEvents emitidos por el ViewModel.
    LaunchedEffect(key1 = splashViewModel.uiEvent) { // Puede ser key1 = Unit si el canal no se recrea
        splashViewModel.uiEvent.collect { event ->
            // Podrías tener un when(event) aquí si el ViewModel emite diferentes tipos de UiEvents
            // que el SplashScreen necesite manejar de forma diferente.
            // Para este caso, asumimos que el evento es para navegación.
            if (event is UiEvent.NavigateToRoute) { // Seguridad extra
                Navigator.navigate(navController, event)
            }
            // Si tuvieras un UiEvent.ShowError que quisieras manejar aquí (ej. con un Snackbar):
            // if (event is UiEvent.ShowError) { /* Mostrar Snackbar */ }
        }
    }

    // UI del splash que reacciona al initState
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val currentInitState = initState) { // Usar 'currentInitState' para smart casting
            is SplashViewModel.InitState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Cargando...", fontSize = 20.sp)
                }
            }
            is SplashViewModel.InitState.Success -> {
                // La navegación se disparará a través del UiEvent.
                // Puedes mostrar un mensaje breve aquí si lo deseas.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Inicialización completada.", fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Redirigiendo...", fontSize = 16.sp)
                }
            }
            is SplashViewModel.InitState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Error de Inicialización",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentInitState.message,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        splashViewModel.initializeApplication() // Reintentar
                    }) {
                        Text("Reintentar")
                    }
                }
            }
        }
    }
}
