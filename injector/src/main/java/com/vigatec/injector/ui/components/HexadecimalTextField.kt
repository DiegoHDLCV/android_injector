package com.vigatec.injector.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Campo de texto especializado para entrada hexadecimal con teclado personalizado
 * @param value Valor actual del campo
 * @param onValueChange Callback cuando cambia el valor
 * @param label Label del campo
 * @param maxLength Longitud máxima de caracteres (según el algoritmo de la llave)
 * @param isError Si hay error de validación
 * @param errorMessage Mensaje de error a mostrar
 * @param isPasswordVisible Si el contenido debe mostrarse o ocultarse
 * @param onPasswordVisibilityChange Callback cuando cambia la visibilidad
 */
@Composable
fun HexadecimalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int = Int.MAX_VALUE,
    isError: Boolean = false,
    errorMessage: String? = null,
    isPasswordVisible: Boolean = false,
    onPasswordVisibilityChange: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Campo de texto deshabilitado (solo lectura, la entrada viene del teclado)
        OutlinedTextField(
            value = value,
            onValueChange = {}, // No permitir edición manual
            label = { Text(label) },
            enabled = false, // Campo deshabilitado para forzar uso del teclado
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = isError,
            supportingText = {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (value.isNotEmpty()) {
                    Text(
                        text = "${value.length}/$maxLength caracteres",
                        color = if (value.length >= maxLength) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Teclado hexadecimal personalizado
        HexadecimalKeyboard(
            onKeyPressed = { char ->
                if (value.length < maxLength) {
                    onValueChange(value + char)
                }
            },
            onBackspace = {
                if (value.isNotEmpty()) {
                    onValueChange(value.dropLast(1))
                }
            },
            onClear = {
                onValueChange("")
            },
            onToggleVisibility = onPasswordVisibilityChange,
            isPasswordVisible = isPasswordVisible,
            maxLength = maxLength,
            currentLength = value.length
        )
    }
}
