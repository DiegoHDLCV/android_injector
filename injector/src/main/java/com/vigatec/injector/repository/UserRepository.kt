package com.vigatec.injector.repository

import android.util.Log
import com.vigatec.persistence.dao.PermissionDao
import com.vigatec.persistence.dao.UserDao
import com.vigatec.persistence.entities.Permission
import com.vigatec.persistence.entities.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val permissionDao: PermissionDao
) {

    companion object {
        private const val TAG = "UserRepository"
    }

    suspend fun login(username: String, pass: String): User? {
        Log.d(TAG, "─────────────────────────────────────────────────────────────")
        Log.d(TAG, "UserRepository.login() iniciado")
        Log.d(TAG, "  - Username buscado: '$username'")
        Log.d(TAG, "  - Password recibida: '$pass'")
        Log.d(TAG, "")
        Log.d(TAG, "NOTA: isActive se usa SOLO para control de acceso (habilitado/deshabilitado)")
        Log.d(TAG, "      Si isActive = false, el usuario NO puede entrar (bloqueado por admin)")
        Log.d(TAG, "")
        
        Log.d(TAG, "Buscando usuario en BD...")
        val user = userDao.findByUsername(username)
        
        if (user == null) {
            Log.e(TAG, "✗ Usuario NO ENCONTRADO en la base de datos")
            Log.e(TAG, "  El username '$username' no existe en la tabla users")
            Log.d(TAG, "─────────────────────────────────────────────────────────────")
            return null
        }
        
        Log.d(TAG, "✓ Usuario encontrado en BD:")
        Log.d(TAG, "  - ID: ${user.id}")
        Log.d(TAG, "  - Username: '${user.username}'")
        Log.d(TAG, "  - Password en BD: '${user.pass}'")
        Log.d(TAG, "  - Role: ${user.role}")
        Log.d(TAG, "  - FullName: ${user.fullName}")
        Log.d(TAG, "  - IsActive: ${user.isActive}")
        Log.d(TAG, "  - CreatedAt: ${user.createdAt}")
        
        Log.d(TAG, "Validando credenciales...")
        
        // Validación 1: Contraseña
        val passwordMatch = user.pass == pass
        Log.d(TAG, "  ✓ Verificación de contraseña:")
        Log.d(TAG, "    - Password ingresada: '$pass'")
        Log.d(TAG, "    - Password en BD: '${user.pass}'")
        Log.d(TAG, "    - ¿Coinciden?: $passwordMatch")
        
        // Validación 2: Estado activo
        Log.d(TAG, "  ✓ Verificación de estado activo:")
        Log.d(TAG, "    - IsActive: ${user.isActive}")
        
        return if (user.pass == pass && user.isActive) {
            Log.i(TAG, "✓✓✓ VALIDACIÓN EXITOSA - Usuario autenticado")
            Log.d(TAG, "─────────────────────────────────────────────────────────────")
            user
        } else {
            Log.e(TAG, "✗✗✗ VALIDACIÓN FALLIDA")
            if (user.pass != pass) {
                Log.e(TAG, "  ✗ Contraseña incorrecta")
            }
            if (!user.isActive) {
                Log.e(TAG, "  ✗ Usuario DESHABILITADO por administrador (isActive = false)")
                Log.e(TAG, "     El usuario existe y la contraseña es correcta,")
                Log.e(TAG, "     pero el acceso fue bloqueado desde Gestión de Usuarios")
            }
            Log.d(TAG, "─────────────────────────────────────────────────────────────")
            null
        }
    }

    suspend fun insertUser(user: User): Long {
        return userDao.insertUser(user)
    }

    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
    }

    suspend fun deleteUser(user: User) {
        userDao.deleteUser(user)
    }

    suspend fun findByUsername(username: String): User? {
        return userDao.findByUsername(username)
    }

    suspend fun findById(userId: Int): User? {
        return userDao.findById(userId)
    }

    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers()
    }

    fun getActiveUsers(): Flow<List<User>> {
        return userDao.getActiveUsers()
    }

    fun getUsersByRole(role: String): Flow<List<User>> {
        return userDao.getUsersByRole(role)
    }

    /**
     * Obtiene el total de usuarios en el sistema.
     */
    suspend fun getUserCount(): Int {
        return userDao.getUserCount()
    }

    suspend fun getUserCountByRole(role: String): Int {
        return userDao.getUserCountByRole(role)
    }

    suspend fun getAdminCount(): Int {
        return userDao.getAdminCount()
    }

    suspend fun updateUserActiveStatus(userId: Int, isActive: Boolean) {
        userDao.updateUserActiveStatus(userId, isActive)
    }

    suspend fun updateUserPassword(userId: Int, newPassword: String) {
        userDao.updateUserPassword(userId, newPassword)
    }

    suspend fun deleteUserById(userId: Int) {
        userDao.deleteUserById(userId)
    }
    
    /**
     * Establece un usuario como el único activo (sesión única).
     * Desactiva a todos los demás usuarios.
     */
    suspend fun setActiveUser(userId: Int) {
        userDao.setActiveUserById(userId)
    }
    
    /**
     * Cierra todas las sesiones (desactiva todos los usuarios).
     */
    suspend fun deactivateAllUsers() {
        userDao.deactivateAllUsers()
    }

    suspend fun isUserAdmin(username: String): Boolean {
        val user = userDao.findByUsername(username)
        return user?.role == "ADMIN"
    }
    
    /**
     * Método de depuración: Lista todos los usuarios en la base de datos con detalles completos.
     * Útil para debugging del login.
     */
    suspend fun debugListAllUsers() {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "DEBUG: Listando TODOS los usuarios en la base de datos")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        try {
            val totalCount = userDao.getUserCount()
            Log.d(TAG, "Total de usuarios en BD: $totalCount")
            
            if (totalCount == 0) {
                Log.w(TAG, "⚠⚠⚠ LA BASE DE DATOS ESTÁ VACÍA ⚠⚠⚠")
                Log.w(TAG, "No hay usuarios. Los usuarios predeterminados no se crearon.")
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                return
            }
            
            val users = getAllUsers().first()
            
            Log.d(TAG, "")
            users.forEachIndexed { index, user ->
                Log.d(TAG, "Usuario #${index + 1}:")
                Log.d(TAG, "  - ID: ${user.id}")
                Log.d(TAG, "  - Username: '${user.username}'")
                Log.d(TAG, "  - Password: '${user.pass}'")
                Log.d(TAG, "  - Role: ${user.role}")
                Log.d(TAG, "  - FullName: ${user.fullName}")
                Log.d(TAG, "  - IsActive: ${user.isActive}")
                Log.d(TAG, "  - CreatedAt: ${user.createdAt}")
                Log.d(TAG, "")
            }
            
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al listar usuarios", e)
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS DE PERMISOS
    // ═══════════════════════════════════════════════════════════════════════
    
    fun getAllPermissions(): Flow<List<Permission>> {
        return permissionDao.getAllPermissions()
    }
    
    fun getUserPermissions(userId: Int): Flow<List<Permission>> {
        return permissionDao.getUserPermissions(userId)
    }
    
    suspend fun getUserPermissionsSync(userId: Int): List<Permission> {
        return permissionDao.getUserPermissionsSync(userId)
    }
    
    suspend fun updateUserPermissions(userId: Int, permissionIds: List<String>) {
        Log.d(TAG, "─────────────────────────────────────────────────────────────")
        Log.d(TAG, "Actualizando permisos para usuario ID: $userId")
        Log.d(TAG, "Permisos a asignar: ${permissionIds.joinToString(", ")}")
        
        try {
            permissionDao.updateUserPermissions(userId, permissionIds)
            Log.i(TAG, "✓ Permisos actualizados exitosamente")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al actualizar permisos", e)
            throw e
        } finally {
            Log.d(TAG, "─────────────────────────────────────────────────────────────")
        }
    }
} 