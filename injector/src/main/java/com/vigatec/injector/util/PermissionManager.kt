package com.vigatec.injector.util

import com.vigatec.persistence.entities.User

/**
 * Gestor de permisos basado en roles de usuario.
 * Define qué acciones puede realizar cada rol en el sistema.
 */
@Suppress("unused")
object PermissionManager {

    // Roles del sistema
    const val ROLE_SUPERVISOR = "SUPERVISOR"
    const val ROLE_OPERATOR = "OPERATOR"

    /**
     * Verifica si un usuario tiene permiso para gestionar usuarios
     * (crear, editar, eliminar usuarios)
     */
    fun canManageUsers(user: User?): Boolean {
        return user?.role == ROLE_SUPERVISOR
    }

    /**
     * Verifica si un usuario tiene permiso para eliminar llaves del almacén
     */
    fun canDeleteKeys(user: User?): Boolean {
        return user?.role == ROLE_SUPERVISOR
    }

    /**
     * Verifica si un usuario tiene permiso para limpiar todo el almacén
     */
    fun canClearAllKeys(user: User?): Boolean {
        return user?.role == ROLE_SUPERVISOR
    }

    /**
     * Verifica si un usuario tiene permiso para configurar llaves como KEK
     */
    fun canConfigureKEK(user: User?): Boolean {
        return user?.role == ROLE_SUPERVISOR
    }

    /**
     * Verifica si un usuario tiene permiso para crear/editar/eliminar perfiles
     */
    fun canManageProfiles(user: User?, permissions: Set<String> = emptySet()): Boolean {
        if (user?.role == ROLE_SUPERVISOR) return true
        if (user == null || !user.isActive) return false
        if (permissions.isEmpty()) return false
        return permissions.contains(PermissionsCatalog.MANAGE_PROFILES)
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
    fun canViewKeyVault(user: User?, permissions: Set<String> = emptySet()): Boolean {
        if (user?.role == ROLE_SUPERVISOR) return user.isActive
        if (user == null || !user.isActive) return false
        if (permissions.isEmpty()) return true
        return permissions.contains(PermissionsCatalog.KEY_VAULT)
    }

    /**
     * Verifica si un usuario puede ejecutar inyección de llaves
     */
    fun canInjectKeys(user: User?, permissions: Set<String> = emptySet()): Boolean {
        if (user?.role == ROLE_SUPERVISOR) return user.isActive
        if (user == null || !user.isActive) return false
        if (permissions.isEmpty()) return true
        return permissions.contains(PermissionsCatalog.EXECUTE_INJECTION)
    }

    /**
     * Verifica si un usuario puede ver logs
     */
    fun canViewLogs(user: User?, permissions: Set<String> = emptySet()): Boolean {
        if (user?.role == ROLE_SUPERVISOR) return user.isActive
        if (user == null || !user.isActive) return false
        if (permissions.isEmpty()) return true
        return permissions.contains(PermissionsCatalog.VIEW_LOGS)
    }

    /**
     * Verifica si un usuario puede ver perfiles
     */
    fun canViewProfiles(user: User?, permissions: Set<String> = emptySet()): Boolean {
        if (user?.role == ROLE_SUPERVISOR) return user.isActive
        if (user == null || !user.isActive) return false
        if (permissions.isEmpty()) return true
        return permissions.contains(PermissionsCatalog.KEY_VAULT) || permissions.contains(PermissionsCatalog.SELECT_KTK)
    }
}
