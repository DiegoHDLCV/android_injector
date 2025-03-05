package com.vigatec.android_injector.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDialog(
    title: String = "Tarjeta Insertada",
    text: String = "La tarjeta ha sido insertada. Por favor, espera...",
    onDismissRequest: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = title)
        },
        text = {
            Text(text)
        },
        confirmButton = {
            // Botón OK para cerrar el diálogo
            TextButton(onClick = onDismissRequest) {
                Text("OK")
            }
        }
    )
}
