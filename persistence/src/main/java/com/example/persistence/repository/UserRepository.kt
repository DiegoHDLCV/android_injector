package com.example.persistence.repository

import com.example.persistence.dao.UserDao
import com.example.persistence.entities.UsersEntity
import com.vigatec.utils.enums.Role
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao // Cambiado de adtDao a userDao para consistencia
) {

    suspend fun registerUser(name: String, email: String, passwordHash: String, role: Role = Role.USER): UsersEntity? {
        if (userDao.getUserByEmail(email) != null) {
            // Usuario ya existe con ese email
            // Considera lanzar una excepción específica o devolver un resultado encapsulado
            println("Error: Usuario con email $email ya existe.")
            return null
        }
        val newUser = UsersEntity(
            name = name,
            email = email,
            passwordHash = passwordHash, // Asegúrate de que esto sea un hash seguro
            role = role.name
        )
        val id = userDao.insertUser(newUser)
        return newUser.copy(id = id)
    }

    suspend fun loginUser(email: String, passwordHash: String): UsersEntity? {
        val user = userDao.getUserByEmail(email)
        // Aquí deberías comparar el passwordHash proporcionado con el user.passwordHash almacenado.
        // Esta es una simplificación. Necesitas una librería de hashing y comparación segura.
        return if (user != null && user.passwordHash == passwordHash) {
            user
        } else {
            null
        }
    }

    suspend fun getUserById(userId: Long): UsersEntity? {
        return userDao.getUserById(userId)
    }

    fun getAllUsers(): Flow<List<UsersEntity>> {
        return userDao.getAllUsers()
    }

    suspend fun promoteUserToAdmin(userId: Long, currentAdmin: UsersEntity): Boolean {
        if (currentAdmin.role != Role.ADMIN.name) {
            println("Error: Solo un administrador puede promover usuarios.")
            return false // O lanzar una excepción de permisos
        }
        val userToPromote = userDao.getUserById(userId)
        return if (userToPromote != null) {
            userDao.updateUserRole(userId, Role.ADMIN.name)
            true
        } else {
            false // Usuario no encontrado
        }
    }

    // Método para crear el primer administrador si aún no existe (ej. desde una pantalla de setup)
    // El callback de la base de datos ya intenta crear un admin. Esto es una alternativa o complemento.
    suspend fun createFirstAdminAccount(name: String, email: String, passwordHash: String): UsersEntity? {
        val existingAdmin = userDao.getFirstAdmin()
        if (existingAdmin != null) {
            println("Un administrador ya existe.")
            return existingAdmin
        }
        return registerUser(name, email, passwordHash, Role.ADMIN)
    }
}