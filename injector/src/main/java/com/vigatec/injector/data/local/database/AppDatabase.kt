package com.vigatec.injector.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vigatec.persistence.dao.InjectionLogDao
import com.vigatec.persistence.entities.InjectionLogEntity
import com.vigatec.injector.data.local.dao.PermissionDao
import com.vigatec.injector.data.local.dao.UserDao
import com.vigatec.injector.data.local.entity.Permission
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.data.local.entity.UserPermission

@Database(
    entities = [User::class, InjectionLogEntity::class, Permission::class, UserPermission::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun injectionLogDao(): InjectionLogDao
    abstract fun permissionDao(): PermissionDao
} 