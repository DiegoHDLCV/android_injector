package com.example.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.persistence.dao.KeyDao
import com.example.persistence.dao.UserDao
import com.example.persistence.entities.KeyEntity

@Database(
    entities = [
        UsersEntity::class,
        KeyEntity::class  // Añadir la nueva entidad KeyEntity
    ],
    version = 2, // Incrementar la versión debido al cambio de esquema (nueva tabla)
    exportSchema = true // Mantener true para exportar el esquema
)
// @TypeConverters(DateConverter::class) // Descomentar si usas TypeConverters
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun keyDao(): KeyDao // Añadir el DAO para KeyEntity
}