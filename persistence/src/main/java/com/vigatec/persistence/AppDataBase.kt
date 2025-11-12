// file: com/example/persistence/AppDatabase.kt

package com.vigatec.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vigatec.persistence.common.Converters
import com.vigatec.persistence.dao.InjectedKeyDao
import com.vigatec.persistence.dao.KeyDao
import com.vigatec.persistence.dao.ProfileDao
import com.vigatec.persistence.entities.InjectedKeyEntity
import com.vigatec.persistence.entities.KeyEntity
import com.vigatec.persistence.entities.ProfileEntity

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
    version = 15, //
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