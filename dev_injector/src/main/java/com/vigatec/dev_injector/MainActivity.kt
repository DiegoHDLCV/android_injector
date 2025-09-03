package com.vigatec.dev_injector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.vigatec.dev_injector.ui.screens.SimpleInjectedKeysScreen
import com.vigatec.dev_injector.ui.screens.SimpleKeyInjectionScreen
import com.vigatec.dev_injector.ui.screens.SimpleSplashScreen
import com.vigatec.dev_injector.ui.theme.DevInjectorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DevInjectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DevInjectorApp()
                }
            }
        }
    }
}

@Composable
fun DevInjectorApp() {
    var isInitialized by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("injection") }

    when {
        !isInitialized -> {
            SimpleSplashScreen(
                onNavigateToMain = { isInitialized = true }
            )
        }
        currentScreen == "injection" -> {
            SimpleKeyInjectionScreen(
                onNavigateToKeys = { currentScreen = "keys" }
            )
        }
        currentScreen == "keys" -> {
            SimpleInjectedKeysScreen(
                onNavigateBack = { currentScreen = "injection" }
            )
        }
    }
}
