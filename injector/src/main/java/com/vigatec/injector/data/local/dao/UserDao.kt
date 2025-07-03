package com.vigatec.injector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vigatec.injector.data.local.entity.User

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): User?

    /**
     * Cuenta el total de usuarios en el sistema.
     */
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
} 