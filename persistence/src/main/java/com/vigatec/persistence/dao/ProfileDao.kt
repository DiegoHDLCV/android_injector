package com.vigatec.persistence.dao

import androidx.room.*
import com.vigatec.persistence.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("SELECT name FROM profiles WHERE keyConfigurations LIKE '%' || :kcv || '%'")
    suspend fun getProfileNamesByKeyKcv(kcv: String): List<String>

    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun getProfileByName(name: String): ProfileEntity?
} 