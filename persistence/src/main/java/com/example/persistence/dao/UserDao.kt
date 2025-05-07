package com.example.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.persistence.entities.UsersEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) // Evita emails duplicados si hay una constraint UNIQUE en email
    suspend fun insertUser(user: UsersEntity): Long

    @Query("SELECT * FROM user WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UsersEntity?

    @Query("SELECT * FROM user WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): UsersEntity?

    @Query("SELECT * FROM user")
    fun getAllUsers(): Flow<List<UsersEntity>> // Para que un admin vea usuarios, por ejemplo

    @Update
    suspend fun updateUser(user: UsersEntity)

    // Podrías añadir un método para cambiar el rol si es necesario
    @Query("UPDATE user SET role = :newRole WHERE id = :userId")
    suspend fun updateUserRole(userId: Long, newRole: String)

    // Query para el primer administrador. Podrías tener una lógica más robusta
    // para asegurar que solo haya un primer admin o para identificarlo.
    @Query("SELECT * FROM user WHERE role = 'ADMIN' ORDER BY id ASC LIMIT 1")
    suspend fun getFirstAdmin(): UsersEntity?
}