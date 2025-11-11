package com.vigatec.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vigatec.persistence.entities.KeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: KeyEntity): Long

    @Query("SELECT * FROM key WHERE id = :keyId")
    suspend fun getKeyById(keyId: Long): KeyEntity?

    @Query("SELECT * FROM key WHERE createdByAdminId = :adminId")
    fun getKeysByAdmin(adminId: Long): Flow<List<KeyEntity>>

    @Query("SELECT * FROM key WHERE isActive = 1")
    fun getAllActiveKeys(): Flow<List<KeyEntity>>

    @Query("SELECT * FROM key")
    fun getAllKeys(): Flow<List<KeyEntity>>

    @Update
    suspend fun updateKey(key: KeyEntity)

    @Query("DELETE FROM key WHERE id = :keyId")
    suspend fun deleteKeyById(keyId: Long)

    @Query("DELETE FROM key")
    suspend fun deleteAllKeys() // Para uso del admin o testing
}