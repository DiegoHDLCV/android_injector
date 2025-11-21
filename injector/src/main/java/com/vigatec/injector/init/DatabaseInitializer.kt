package com.vigatec.injector.init

import android.util.Log
import com.vigatec.injector.BuildConfig
import com.vigatec.injector.util.PermissionManager
import com.vigatec.injector.util.PermissionsCatalog
import com.vigatec.persistence.dao.PermissionDao
import com.vigatec.persistence.dao.UserDao
import com.vigatec.persistence.entities.User
import com.vigatec.persistence.entities.UserPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseInitializer @Inject constructor(
    private val userDao: UserDao,
    private val permissionDao: PermissionDao
) {

    companion object {
        private const val TAG = "DatabaseInitializer"
        private const val DEFAULT_ADMIN_PASSWORD = "Vigatec2025@@@@@@"
        private const val DEFAULT_DEV_PASSWORD = "Vigatec2025@@@@@@"
        private const val DEFAULT_OPERATOR_PASSWORD = "Operador2025@"
        private const val OPERATOR_ONE_USERNAME = "operador1"
        private const val OPERATOR_TWO_USERNAME = "operador2"
        private const val LEGACY_RAW_DATA_PERMISSION_ID = "raw_data_listener"
    }

    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
                Log.i(TAG, "Sincronizando permisos y usuarios predeterminados...")
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
                synchronizeDefaultSetup()
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
                Log.i(TAG, "✓ Sincronización inicial completada correctamente")
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
            } catch (e: Exception) {
                Log.e(TAG, "✗✗✗ ERROR al sincronizar datos predeterminados", e)
                Log.e(TAG, "═══════════════════════════════════════════════════════════")
            }
        }
    }

    private suspend fun synchronizeDefaultSetup() {
        ensureSystemPermissions()

        val adminResult = ensureUserAccount(
            username = "admin",
            defaultPassword = DEFAULT_ADMIN_PASSWORD,
            role = PermissionManager.ROLE_SUPERVISOR,
            fullName = "Administrador"
        )
        logUserSyncResult("ADMIN", adminResult)

        if (BuildConfig.FLAVOR == "dev") {
            val devResult = ensureUserAccount(
                username = "dev",
                defaultPassword = DEFAULT_DEV_PASSWORD,
                role = PermissionManager.ROLE_SUPERVISOR,
                fullName = "Desarrollador"
            )
            logUserSyncResult("DEV", devResult)
        }

        val operator1Result = ensureUserAccount(
            username = OPERATOR_ONE_USERNAME,
            defaultPassword = DEFAULT_OPERATOR_PASSWORD,
            role = PermissionManager.ROLE_OPERATOR,
            fullName = "Operador 1"
        )
        logUserSyncResult("OPERADOR 1", operator1Result)

        val operator2Result = ensureUserAccount(
            username = OPERATOR_TWO_USERNAME,
            defaultPassword = DEFAULT_OPERATOR_PASSWORD,
            role = PermissionManager.ROLE_OPERATOR,
            fullName = "Operador 2"
        )
        logUserSyncResult("OPERADOR 2", operator2Result)

        assignOperatorPermissions(operator1Result.userId, OPERATOR_ONE_USERNAME)
        assignOperatorPermissions(operator2Result.userId, OPERATOR_TWO_USERNAME)
    }

    private suspend fun ensureSystemPermissions() {
        Log.d(TAG, "Actualizando catálogo de permisos del sistema…")
        permissionDao.insertPermissions(PermissionsCatalog.SYSTEM_PERMISSIONS)
        permissionDao.deleteUserPermissionsByPermissionId(LEGACY_RAW_DATA_PERMISSION_ID)
        permissionDao.deletePermissionById(LEGACY_RAW_DATA_PERMISSION_ID)
    }

    private suspend fun assignOperatorPermissions(
        userId: Int?,
        username: String
    ) {
        if (userId == null) {
            Log.w(TAG, "No se pudieron asignar permisos al usuario '$username' (ID nulo).")
            return
        }

        val permissions = PermissionsCatalog.OPERATOR_DEFAULT_PERMISSION_IDS.toList()
        permissionDao.updateUserPermissions(userId, permissions)
        Log.i(TAG, "✓ Permisos asignados a '$username': ${permissions.joinToString(", ")}")
    }

    private suspend fun ensureUserAccount(
        username: String,
        defaultPassword: String,
        role: String,
        fullName: String,
        ensureActive: Boolean = true
    ): EnsureUserResult {
        val existingUser = userDao.findByUsername(username)
        if (existingUser == null) {
            val newUser = User(
                username = username,
                pass = defaultPassword,
                role = role,
                fullName = fullName,
                isActive = ensureActive
            )
            val insertedId = userDao.insertUser(newUser)
            return if (insertedId > 0) {
                EnsureUserResult(
                    userId = insertedId.toInt(),
                    created = true,
                    passwordUpdated = false,
                    profileUpdated = false
                )
            } else {
                Log.e(TAG, "✗ No se pudo insertar el usuario '$username' (resultado $insertedId)")
                EnsureUserResult(null, created = false, passwordUpdated = false, profileUpdated = false)
            }
        }

        var updatedUser = existingUser
        var profileUpdated = false

        if (existingUser.role != role || existingUser.fullName != fullName || (ensureActive && !existingUser.isActive)) {
            updatedUser = existingUser.copy(
                role = role,
                fullName = fullName,
                isActive = if (ensureActive) true else existingUser.isActive
            )
            userDao.updateUser(updatedUser)
            profileUpdated = true
        }

        val passwordUpdated = if (existingUser.pass != defaultPassword) {
            userDao.updateUserPassword(existingUser.id, defaultPassword)
            true
        } else {
            false
        }

        return EnsureUserResult(
            userId = updatedUser.id,
            created = false,
            passwordUpdated = passwordUpdated,
            profileUpdated = profileUpdated
        )
    }

    private fun logUserSyncResult(label: String, result: EnsureUserResult) {
        if (result.userId == null) {
            Log.e(TAG, "✗ No se pudo asegurar el usuario $label.")
            return
        }

        val messages = mutableListOf<String>()
        if (result.created) messages.add("creado")
        if (result.profileUpdated) messages.add("perfil actualizado")
        if (result.passwordUpdated) messages.add("contraseña sincronizada")

        if (messages.isEmpty()) {
            Log.i(TAG, "✓ Usuario $label verificado (sin cambios).")
        } else {
            Log.i(TAG, "✓ Usuario $label (${messages.joinToString(", ")})")
        }
    }

    private data class EnsureUserResult(
        val userId: Int?,
        val created: Boolean,
        val passwordUpdated: Boolean,
        val profileUpdated: Boolean
    )
}
