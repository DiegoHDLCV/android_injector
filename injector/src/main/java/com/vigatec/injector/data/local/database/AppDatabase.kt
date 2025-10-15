package com.vigatec.injector.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.persistence.dao.InjectionLogDao
import com.example.persistence.entities.InjectionLogEntity
import com.vigatec.injector.data.local.dao.UserDao
import com.vigatec.injector.data.local.entity.User

@Database(
    entities = [User::class, InjectionLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun injectionLogDao(): InjectionLogDao
} 