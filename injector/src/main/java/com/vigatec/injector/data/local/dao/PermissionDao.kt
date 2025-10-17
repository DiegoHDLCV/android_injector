package com.vigatec.injector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vigatec.injector.data.local.entity.Permission
import com.vigatec.injector.data.local.entity.UserPermission
import kotlinx.coroutines.flow.Flow

@Dao
interface PermissionDao {
    @Query("SELECT * FROM permissions")
    fun getAllPermissions(): Flow<List<Permission>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissions(permissions: List<Permission>)
    
    @Query("SELECT p.* FROM permissions p INNER JOIN user_permissions up ON p.id = up.permissionId WHERE up.userId = :userId")
    fun getUserPermissions(userId: Int): Flow<List<Permission>>
    
    @Query("SELECT p.* FROM permissions p INNER JOIN user_permissions up ON p.id = up.permissionId WHERE up.userId = :userId")
    suspend fun getUserPermissionsSync(userId: Int): List<Permission>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPermission(userPermission: UserPermission)
    
    @Query("DELETE FROM user_permissions WHERE userId = :userId")
    suspend fun deleteUserPermissions(userId: Int)
    
    @Transaction
    suspend fun updateUserPermissions(userId: Int, permissionIds: List<String>) {
        deleteUserPermissions(userId)
        permissionIds.forEach { permissionId ->
            insertUserPermission(UserPermission(userId, permissionId))
        }
    }
}

