package com.vigatec.injector.ui.theme

import androidx.compose.ui.graphics.Color

// Legacy colors (deprecated)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ==================== PALETA CORPORATIVA VIGATEC ====================
// Identidad visual estricta - Diseño técnico, seguro y de alto rendimiento

// Fondos y bases
val CorporateDarkBase = Color(0xFF1A2449)      // Fondo principal - Azul oscuro técnico
val CorporateLightBase = Color(0xFFF5F7FB)     // Fondo claro alternativo

// Texto e información
val CorporateLightText = Color(0xFFD6E1FD)     // Texto/paneles sobre fondo oscuro
val CorporateDarkText = Color(0xFF1A2449)      // Texto sobre fondo claro

// Acciones y botones
val CorporatePrimary = Color(0xFF2662F6)       // Botones de acción primarias
val CorporatePrimaryVariant = Color(0xFF1E4ED8) // Variante más oscura (hover/pressed)

// Estados y confirmación
val CorporateSuccess = Color(0xFF00A884)       // Éxito, verificación OK (color del logo)
val CorporateWarning = Color(0xFFFFA500)       // Advertencia
val CorporateError = Color(0xFFE53935)         // Error crítico y fallos de seguridad (rojo intenso)

// Grises neutrales (segundario)
val CorporateGrayLight = Color(0xFFE8EBF0)     // Bordes y separadores claros
val CorporateGrayMedium = Color(0xFF8B92A0)    // Texto secundario
val CorporateGrayDark = Color(0xFF4A525F)      // Texto terciario

// Componentes (mantener compatibilidad)
val VertexBlack = CorporateDarkBase            // Alias para compatibilidad
val VertexGray = CorporateGrayDark
val VertexGreen = CorporateSuccess
val VertexLightGray = CorporateLightText
val VertexWhite = Color(0xFFF2F2F2)