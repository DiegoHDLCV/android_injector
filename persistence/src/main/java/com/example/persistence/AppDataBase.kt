// file: com/example/persistence/AppDatabase.kt

package com.example.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.persistence.common.Converters
import com.example.persistence.dao.InjectedKeyDao
import com.example.persistence.dao.KeyDao
import com.example.persistence.dao.ProfileDao
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.entities.KeyEntity
import com.example.persistence.entities.ProfileEntity

/**
 * La clase principal de la base de datos para la app.
 * Debe ser abstracta y extender RoomDatabase.
 */
@Database(
    entities = [
        // Mantén la original si la usas para otra cosa
        KeyEntity::class,
        // AÑADE LA NUEVA ENTIDAD
        InjectedKeyEntity::class,
        ProfileEntity::class
    ],
    version = 3, // Se incrementa la versión por el nuevo esquema
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    // Mantén el DAO original si lo necesitas
    abstract fun keyDao(): KeyDao

    // AÑADE EL NUEVO DAO
    abstract fun injectedKeyDao(): InjectedKeyDao

    abstract fun profileDao(): ProfileDao

    companion object {
        // ... existing code ...
    }
}