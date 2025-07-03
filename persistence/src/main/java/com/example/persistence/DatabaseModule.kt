// file: com/example/persistence/DatabaseModule.kt

package com.example.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.persistence.dao.InjectedKeyDao // Importa el nuevo DAO
import com.example.persistence.dao.KeyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "pos_database"
        )
            .fallbackToDestructiveMigration() // Recuerda esto para producción
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

    // El repositorio se inyecta automáticamente gracias a @Inject constructor,
    // por lo que no necesitas un @Provides para él a menos que sea una interfaz.
}