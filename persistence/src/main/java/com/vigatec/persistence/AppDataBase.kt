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
import com.vigatec.persistence.dao.PermissionDao
import com.vigatec.persistence.dao.UserDao
import com.vigatec.persistence.entities.Permission
import com.vigatec.persistence.entities.User
import com.vigatec.persistence.entities.UserPermission

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
        ProfileEntity::class,
        com.vigatec.persistence.entities.InjectionLogEntity::class,
        User::class,
        Permission::class,
        UserPermission::class
    ],
    version = 20, //
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    // Mantén el DAO original si lo necesitas
    abstract fun keyDao(): KeyDao

    // AÑADE EL NUEVO DAO
    abstract fun injectedKeyDao(): InjectedKeyDao

    abstract fun profileDao(): ProfileDao

    abstract fun injectionLogDao(): com.vigatec.persistence.dao.InjectionLogDao
    abstract fun userDao(): UserDao
    abstract fun permissionDao(): PermissionDao

    companion object {
        // ... existing code ...
    }
}