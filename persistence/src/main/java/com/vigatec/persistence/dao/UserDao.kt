package com.vigatec.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vigatec.persistence.entities.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun findById(userId: Int): User?

    /**
     * Obtiene todos los usuarios del sistema.
     */
    @Query("SELECT * FROM users ORDER BY username ASC")
    fun getAllUsers(): Flow<List<User>>

    /**
     * Obtiene todos los usuarios activos.
     */
    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY username ASC")
    fun getActiveUsers(): Flow<List<User>>

    /**
     * Obtiene usuarios por rol.
     */
    @Query("SELECT * FROM users WHERE role = :role ORDER BY username ASC")
    fun getUsersByRole(role: String): Flow<List<User>>

    /**
     * Cuenta el total de usuarios en el sistema.
     */
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    /**
     * Cuenta usuarios por rol.
     */
    @Query("SELECT COUNT(*) FROM users WHERE role = :role")
    suspend fun getUserCountByRole(role: String): Int

    /**
     * Verifica si existe al menos un usuario admin.
     */
    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")
    suspend fun getAdminCount(): Int

    /**
     * Actualiza el estado activo de un usuario.
     */
    @Query("UPDATE users SET isActive = :isActive WHERE id = :userId")
    suspend fun updateUserActiveStatus(userId: Int, isActive: Boolean)

    /**
     * Actualiza la contraseña de un usuario.
     */
    @Query("UPDATE users SET pass = :newPassword WHERE id = :userId")
    suspend fun updateUserPassword(userId: Int, newPassword: String)

    /**
     * Elimina un usuario por ID.
     */
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: Int)
    
    /**
     * Desactiva todos los usuarios (cierra todas las sesiones).
     */
    @Query("UPDATE users SET isActive = 0")
    suspend fun deactivateAllUsers()
    
    /**
     * Activa solo un usuario específico (establece sesión única).
     */
    @Query("UPDATE users SET isActive = CASE WHEN id = :userId THEN 1 ELSE 0 END")
    suspend fun setActiveUserById(userId: Int)
}
