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

private val DarkColorScheme = darkColorScheme(
    primary = VertexGreen,
    onPrimary = VertexWhite,
    secondary = VertexGray,
    onSecondary = VertexWhite,
    tertiary = VertexLightGray,
    background = VertexBlack,
    onBackground = VertexWhite,
    surface = VertexBlack,
    onSurface = VertexWhite,
    surfaceVariant = VertexGray,
    onSurfaceVariant = VertexLightGray,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = VertexGreen,
    onPrimary = VertexWhite,
    secondary = VertexGray,
    onSecondary = VertexWhite,
    tertiary = VertexBlack,
    background = VertexWhite,
    onBackground = VertexBlack,
    surface = VertexWhite,
    onSurface = VertexBlack,
    surfaceVariant = VertexLightGray,
    onSurfaceVariant = VertexBlack,
    error = Color(0xFFB00020),
    onError = Color.White
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