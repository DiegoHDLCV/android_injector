package com.example.persistence

// ... otros imports ...
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.persistence.dao.KeyDao
import com.example.persistence.entities.KeyEntity
// ¿Falta el import para UsersEntity?

@Database(
    entities = [
        KeyEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun keyDao(): KeyDao
}