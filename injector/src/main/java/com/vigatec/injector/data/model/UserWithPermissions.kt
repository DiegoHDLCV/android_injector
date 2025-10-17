package com.vigatec.injector.data.model

import com.vigatec.injector.data.local.entity.Permission
import com.vigatec.injector.data.local.entity.User

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

