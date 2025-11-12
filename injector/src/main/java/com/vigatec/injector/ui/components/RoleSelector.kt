package com.vigatec.injector.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Componente reutilizable para seleccionar rol (OPERATOR/USER/ADMIN)
 */
@Composable
fun RoleSelector(
    selectedRole: String,
    onRoleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Rol:"
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedRole == "OPERATOR",
                    onClick = { onRoleChange("OPERATOR") },
                    enabled = enabled
                )
                Text("Operador")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedRole == "USER",
                    onClick = { onRoleChange("USER") },
                    enabled = enabled
                )
                Text("Usuario")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedRole == "ADMIN",
                    onClick = { onRoleChange("ADMIN") },
                    enabled = enabled
                )
                Text("Administrador")
            }
        }
    }
}
