// file: com/example/persistence/AppDatabase.kt

package com.example.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.persistence.dao.InjectedKeyDao
import com.example.persistence.dao.KeyDao
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.entities.KeyEntity

@Database(
    entities = [
        // Mantén la original si la usas para otra cosa
        KeyEntity::class,
        // AÑADE LA NUEVA ENTIDAD
        InjectedKeyEntity::class
    ],
    version = 4, // Incrementa la versión de la base de datos
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    // Mantén el DAO original si lo necesitas
    abstract fun keyDao(): KeyDao

    // AÑADE EL NUEVO DAO
    abstract fun injectedKeyDao(): InjectedKeyDao
}