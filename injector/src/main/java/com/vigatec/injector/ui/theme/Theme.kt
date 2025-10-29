package com.vigatec.injector.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ==================== TEMA OSCURO (CORPORATIVO) ====================
// Diseño técnico, profesional y de alta seguridad
private val DarkColorScheme = darkColorScheme(
    primary = CorporatePrimary,                 // Botones de acción (#2662F6)
    onPrimary = VertexWhite,                    // Texto sobre botones
    secondary = CorporateGrayMedium,            // Secundario
    onSecondary = VertexWhite,
    tertiary = CorporateLightText,              // Panel info (#D6E1FD)
    onTertiary = CorporateDarkText,             // Texto en paneles
    background = CorporateDarkBase,             // Fondo principal (#1A2449)
    onBackground = CorporateLightText,          // Texto principal (#D6E1FD)
    surface = CorporateDarkBase,                // Superficies
    onSurface = CorporateLightText,             // Texto sobre superficies
    surfaceVariant = CorporateGrayDark,         // Variante de superficie
    onSurfaceVariant = CorporateLightText,      // Texto sobre variante
    error = CorporateError,                     // Rojo intenso para alertas
    onError = VertexWhite,
    inverseOnSurface = CorporateDarkText
)

// ==================== TEMA CLARO (ALTERNATIVO) ====================
private val LightColorScheme = lightColorScheme(
    primary = CorporatePrimary,                 // Botones de acción (#2662F6)
    onPrimary = VertexWhite,
    secondary = CorporateGrayMedium,
    onSecondary = VertexWhite,
    tertiary = CorporateDarkBase,               // Acentos (#1A2449)
    onTertiary = VertexWhite,
    background = CorporateLightBase,            // Fondo claro (#F5F7FB)
    onBackground = CorporateDarkText,           // Texto oscuro
    surface = VertexWhite,                      // Blanco puro
    onSurface = CorporateDarkText,
    surfaceVariant = CorporateGrayLight,        // Bordes claros
    onSurfaceVariant = CorporateGrayMedium,
    error = CorporateError,                     // Rojo intenso
    onError = VertexWhite,
    inverseOnSurface = CorporateLightText
)

@Composable
fun Android_injectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}