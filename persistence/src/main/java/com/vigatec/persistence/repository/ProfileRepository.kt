package com.vigatec.persistence.repository

import com.vigatec.persistence.dao.ProfileDao
import com.vigatec.persistence.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {
    fun getAllProfiles(): Flow<List<ProfileEntity>> = profileDao.getAllProfiles()

    suspend fun insertProfile(profile: ProfileEntity) {
        profileDao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: ProfileEntity) {
        profileDao.updateProfile(profile)
    }

    suspend fun deleteProfile(profile: ProfileEntity) {
        profileDao.deleteProfile(profile)
    }

    suspend fun getProfileNamesByKeyKcv(kcv: String): List<String> {
        return profileDao.getProfileNamesByKeyKcv(kcv)
    }

    suspend fun getProfileByName(name: String): ProfileEntity? {
        return profileDao.getProfileByName(name)
    }
}
