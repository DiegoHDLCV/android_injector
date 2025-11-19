package com.vigatec.injector.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Componente reutilizable para seleccionar rol (OPERATOR/SUPERVISOR)
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
                    selected = selectedRole == "SUPERVISOR",
                    onClick = { onRoleChange("SUPERVISOR") },
                    enabled = enabled
                )
                Text("Supervisor")
            }
        }
    }
}
