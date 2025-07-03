package com.vigatec.injector.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vigatec.injector.data.local.dao.UserDao
import com.vigatec.injector.data.local.entity.User

@Database(entities = [User::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
} 