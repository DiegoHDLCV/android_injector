package com.vigatec.injector.util

import android.util.Log
import com.vigatec.injector.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proveedor singleton de permisos del usuario actual.
 * Gestiona el estado de los permisos globalmente en la aplicación.
 */
@Singleton
class PermissionProvider @Inject constructor(
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "PermissionProvider"
        
        // IDs de permisos del sistema
        const val KEY_VAULT = "key_vault"
        const val CEREMONY_KEK = "ceremony_kek"
        const val CEREMONY_OPERATIONAL = "ceremony_operational"
        const val SELECT_KTK = "select_ktk"
        const val MANAGE_PROFILES = "manage_profiles"
        const val VIEW_LOGS = "view_logs"
        const val MANAGE_USERS = "manage_users"
        const val RAW_DATA_LISTENER = "raw_data_listener"
        const val EXPORT_IMPORT_KEYS = "export_import_keys"
    }
    
    private val _userPermissions = MutableStateFlow<Set<String>>(emptySet())
    val userPermissions: StateFlow<Set<String>> = _userPermissions.asStateFlow()
    
    /**
     * Carga los permisos del usuario especificado.
     * Si el usuario es ADMIN, obtiene todos los permisos automáticamente.
     */
    suspend fun loadPermissions(username: String) {
        try {
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            Log.d(TAG, "Cargando permisos para usuario: '$username'")

            val user = userRepository.findByUsername(username)

            if (user == null) {
                Log.e(TAG, "✗ Usuario no encontrado: '$username', permisos vacíos")
                _userPermissions.value = emptySet()
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                return
            }

            Log.d(TAG, "Usuario encontrado: id=${user.id}, username=${user.username}, role=${user.role}")

            if (user.role == "ADMIN") {
                // Admin tiene TODOS los permisos
                Log.d(TAG, "Usuario es ADMIN → asignando TODOS los permisos")
                _userPermissions.value = setOf(
                    KEY_VAULT,
                    CEREMONY_KEK,
                    CEREMONY_OPERATIONAL,
                    SELECT_KTK,
                    MANAGE_PROFILES,
                    VIEW_LOGS,
                    MANAGE_USERS,
                    RAW_DATA_LISTENER,
                    EXPORT_IMPORT_KEYS
                )
                Log.d(TAG, "Permisos asignados a ADMIN: ${_userPermissions.value.joinToString(", ")}")
            } else {
                // Obtener permisos desde la BD
                Log.d(TAG, "Usuario es USER → cargando permisos desde BD")
                val permissions = userRepository.getUserPermissionsSync(user.id)
                _userPermissions.value = permissions.map { it.id }.toSet()

                Log.d(TAG, "Permisos cargados: ${_userPermissions.value.joinToString(", ")}")
            }

            Log.i(TAG, "✓ ${_userPermissions.value.size} permisos cargados para '$username'")
            Log.d(TAG, "═══════════════════════════════════════════════════════════")

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al cargar permisos para '$username'", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            _userPermissions.value = emptySet()
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
        }
    }
    
    /**
     * Verifica si el usuario actual tiene un permiso específico.
     */
    fun hasPermission(permissionId: String): Boolean {
        return userPermissions.value.contains(permissionId)
    }
    
    /**
     * Limpia todos los permisos (al hacer logout).
     */
    fun clear() {
        Log.d(TAG, "Limpiando permisos del usuario")
        _userPermissions.value = emptySet()
    }
}


