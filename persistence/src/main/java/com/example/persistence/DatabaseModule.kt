package com.example.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.persistence.dao.KeyDao
import com.vigatec.utils.enums.Role
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.Provider
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "pos_database"
        )
            // Es crucial manejar las migraciones correctamente en producción.
            // fallbackToDestructiveMigration eliminará los datos si la versión aumenta y no hay migración.
            // Para la versión 2, necesitarás una migración o usar fallbackToDestructiveMigration.
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)

                }
            })
            .build()
    }


    @Provides
    @Singleton // Asegúrate que los DAOs también sean Singleton si la BD lo es.
    fun provideKeyDao(appDatabase: AppDatabase): KeyDao = appDatabase.keyDao()
}