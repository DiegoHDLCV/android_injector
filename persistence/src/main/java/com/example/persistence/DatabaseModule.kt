// file: com/example/persistence/DatabaseModule.kt

package com.example.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.persistence.dao.InjectedKeyDao // Importa el nuevo DAO
import com.example.persistence.dao.KeyDao
import com.example.persistence.dao.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Migración de versión 10 a 11: Agrega campos de cifrado a injected_keys
     * - encryptedKeyData: Datos cifrados con KEK Storage (AES-256-GCM)
     * - encryptionIV: Vector de inicialización (12 bytes en hex)
     * - encryptionAuthTag: Tag de autenticación GCM (16 bytes en hex)
     */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE injected_keys ADD COLUMN encryptedKeyData TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE injected_keys ADD COLUMN encryptionIV TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE injected_keys ADD COLUMN encryptionAuthTag TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * Migración de versión 11 a 12: Agrega campo kekType para diferenciar tipos de KEK
     * - kekType: Tipo de KEK (NONE, KEK_STORAGE, KEK_TRANSPORT)
     * - Permite distinguir entre KEK Storage (cifra en local) y KTK (cifra para transmisión)
     */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE injected_keys ADD COLUMN kekType TEXT NOT NULL DEFAULT 'NONE'")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "pos_database"
        )
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
            .build()
    }

    // Proveedor para el DAO original
    @Provides
    @Singleton
    fun provideKeyDao(appDatabase: AppDatabase): KeyDao = appDatabase.keyDao()

    // --- AÑADIDO: Proveedor para el nuevo DAO ---
    @Provides
    @Singleton
    fun provideInjectedKeyDao(appDatabase: AppDatabase): InjectedKeyDao = appDatabase.injectedKeyDao()

    @Provides
    @Singleton
    fun provideProfileDao(appDatabase: AppDatabase): ProfileDao = appDatabase.profileDao()

    // El repositorio se inyecta automáticamente gracias a @Inject constructor,
    // por lo que no necesitas un @Provides para él a menos que sea una interfaz.
}