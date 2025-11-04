package com.vigatec.injector.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Componente de teclado personalizado para entrada hexadecimal (0-9, A-F)
 * @param onKeyPressed Callback cuando se presiona un botón de carácter
 * @param onBackspace Callback cuando se presiona borrar
 * @param onClear Callback cuando se presiona limpiar todo
 * @param onCancel Callback cuando se cancela la ceremonia (botón X)
 * @param onVerifyKCV Callback cuando se verifica el KCV (botón OK)
 * @param maxLength Longitud máxima de caracteres permitidos (para deshabilitar botones si se alcanza)
 * @param currentLength Longitud actual del texto
 */
@Composable
fun HexadecimalKeyboard(
    onKeyPressed: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    onVerifyKCV: () -> Unit,
    maxLength: Int = Int.MAX_VALUE,
    currentLength: Int = 0
) {
    val hexChars = listOf(
        '0', '1', '2', '3',
        '4', '5', '6', '7',
        '8', '9', 'A', 'B',
        'C', 'D', 'E', 'F'
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Grid de caracteres hexadecimales (4 columnas)
        repeat(4) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { col ->
                    val index = row * 4 + col
                    val char = hexChars[index]
                    val isEnabled = currentLength < maxLength

                    OutlinedButton(
                        onClick = { if (isEnabled) onKeyPressed(char) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        enabled = isEnabled,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = char.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fila de botones de control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Botón Backspace
            OutlinedButton(
                onClick = onBackspace,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = currentLength > 0,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Backspace,
                    contentDescription = "Borrar último carácter",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Botón Limpiar (Trash)
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = currentLength > 0,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Limpiar todo",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Botón Cancelar (X)
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancelar ceremonia",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Botón Verificar KCV (OK)
            OutlinedButton(
                onClick = onVerifyKCV,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = currentLength > 0,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Verificar KCV",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
