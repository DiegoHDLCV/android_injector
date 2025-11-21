package com.vigatec.injector.data.model

import com.vigatec.persistence.entities.Permission
import com.vigatec.persistence.entities.User

data class UserWithPermissions(
    val user: User,
    val permissions: List<Permission>
) {
    fun hasPermission(permissionId: String): Boolean {
        // ADMIN siempre tiene todos los permisos
        if (user.role == "ADMIN") return true
        return permissions.any { it.id == permissionId }
    }
}


