package com.vigatec.injector.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Componente reutilizable para campos de contraseña con toggle de visibilidad
 */
@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (passwordVisible)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    if (passwordVisible) Icons.Default.Visibility
                    else Icons.Default.VisibilityOff,
                    contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                )
            }
        },
        modifier = modifier,
        isError = isError,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

/**
 * Componente para mostrar dos campos de contraseña (entrada y confirmación)
 */
@Composable
fun PasswordConfirmationFields(
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    passwordLabel: String = "Contraseña",
    confirmLabel: String = "Confirmar contraseña",
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    showMismatchError: Boolean = true
) {
    var passwordVisible by remember { mutableStateOf(false) }

    PasswordTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = passwordLabel,
        modifier = modifier,
        isError = isError,
        enabled = enabled
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChange,
        label = { Text(confirmLabel) },
        singleLine = true,
        visualTransformation = if (passwordVisible)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        modifier = modifier,
        isError = isError,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )

    if (showMismatchError && password.isNotEmpty() && confirmPassword.isNotEmpty() && password != confirmPassword) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Las contraseñas no coinciden",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
