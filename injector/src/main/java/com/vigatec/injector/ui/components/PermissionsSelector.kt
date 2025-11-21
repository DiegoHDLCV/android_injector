package com.vigatec.injector.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vigatec.persistence.entities.Permission

/**
 * Componente reutilizable para seleccionar permisos
 * Muestra un mensaje informativo si el usuario es ADMIN,
 * o una lista de checkboxes si es USER
 */
@Composable
fun PermissionsSelector(
    allPermissions: List<Permission>,
    selectedPermissions: Set<String>,
    onPermissionChange: (String, Boolean) -> Unit,
    isAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (isAdmin) {
            // Para ADMIN, mostrar mensaje informativo
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Los administradores tienen todos los permisos automáticamente",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            // Para USER, mostrar checkboxes de permisos
            Column {
                Text(
                    "Permisos:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        items(allPermissions) { permission ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedPermissions.contains(permission.id),
                                    onCheckedChange = { checked ->
                                        onPermissionChange(permission.id, checked)
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = permission.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = permission.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
