package com.vigatec.injector.util

import com.vigatec.injector.data.local.entity.User

/**
 * Gestor de permisos basado en roles de usuario.
 * Define qué acciones puede realizar cada rol en el sistema.
 */
object PermissionManager {

    // Roles del sistema
    const val ROLE_ADMIN = "ADMIN"
    const val ROLE_USER = "USER"

    /**
     * Verifica si un usuario tiene permiso para gestionar usuarios
     * (crear, editar, eliminar usuarios)
     */
    fun canManageUsers(user: User?): Boolean {
        return user?.role == ROLE_ADMIN
    }

    /**
     * Verifica si un usuario tiene permiso para eliminar llaves del almacén
     */
    fun canDeleteKeys(user: User?): Boolean {
        return user?.role == ROLE_ADMIN
    }

    /**
     * Verifica si un usuario tiene permiso para limpiar todo el almacén
     */
    fun canClearAllKeys(user: User?): Boolean {
        return user?.role == ROLE_ADMIN
    }

    /**
     * Verifica si un usuario tiene permiso para configurar llaves como KEK
     */
    fun canConfigureKEK(user: User?): Boolean {
        return user?.role == ROLE_ADMIN
    }

    /**
     * Verifica si un usuario tiene permiso para crear/editar/eliminar perfiles
     */
    fun canManageProfiles(user: User?): Boolean {
        return user?.role == ROLE_ADMIN
    }

    /**
     * Verifica si un usuario puede realizar ceremonias de llaves
     */
    fun canPerformCeremonies(user: User?): Boolean {
        return user != null && user.isActive // Cualquier usuario activo
    }

    /**
     * Verifica si un usuario puede ver el almacén de llaves
     */
    fun canViewKeyVault(user: User?): Boolean {
        return user != null && user.isActive // Cualquier usuario activo
    }

    /**
     * Verifica si un usuario puede ejecutar inyección de llaves
     */
    fun canInjectKeys(user: User?): Boolean {
        return user != null && user.isActive // Cualquier usuario activo
    }

    /**
     * Verifica si un usuario puede ver logs
     */
    fun canViewLogs(user: User?): Boolean {
        return user != null && user.isActive // Cualquier usuario activo
    }

    /**
     * Verifica si un usuario puede ver perfiles
     */
    fun canViewProfiles(user: User?): Boolean {
        return user != null && user.isActive // Cualquier usuario activo
    }
}
