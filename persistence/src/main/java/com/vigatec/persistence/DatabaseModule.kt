// file: com/example/persistence/DatabaseModule.kt

package com.vigatec.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vigatec.persistence.dao.InjectedKeyDao // Importa el nuevo DAO
import com.vigatec.persistence.dao.KeyDao
import com.vigatec.persistence.dao.ProfileDao
import com.vigatec.persistence.dao.InjectionLogDao
import com.vigatec.persistence.dao.PermissionDao
import com.vigatec.persistence.dao.UserDao
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

    /**
     * Migración de versión 12 a 13: Cambia índice único de KCV a KCV + kekType
     * - Elimina el índice único en KCV
     * - Agrega índice único en KCV + kekType
     * - Permite que la misma llave física (mismo KCV) se use para diferentes propósitos
     */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Eliminar el índice único en KCV
            database.execSQL("DROP INDEX IF EXISTS index_injected_keys_kcv")

            // Crear el nuevo índice único en KCV + kekType
            database.execSQL("CREATE UNIQUE INDEX index_injected_keys_kcv_kekType ON injected_keys (kcv, kekType)")
        }
    }

    /**
     * Migración de versión 13 a 14: Agrega campo deviceType a tabla profiles
     * - deviceType: Tipo de dispositivo (AISINO, NEWPOS)
     * - Valor por defecto: AISINO
     * - Permite validaciones específicas por dispositivo (ej: slots DUKPT 1-10 en Aisino)
     */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE profiles ADD COLUMN deviceType TEXT NOT NULL DEFAULT 'AISINO'")
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
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .fallbackToDestructiveMigration()
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

    @Provides
    @Singleton
    fun provideInjectionLogDao(appDatabase: AppDatabase): InjectionLogDao = appDatabase.injectionLogDao()

    @Provides
    @Singleton
    fun provideUserDao(appDatabase: AppDatabase): UserDao = appDatabase.userDao()

    @Provides
    @Singleton
    fun providePermissionDao(appDatabase: AppDatabase): PermissionDao = appDatabase.permissionDao()

    // El repositorio se inyecta automáticamente gracias a @Inject constructor,
    // por lo que no necesitas un @Provides para él a menos que sea una interfaz.
}