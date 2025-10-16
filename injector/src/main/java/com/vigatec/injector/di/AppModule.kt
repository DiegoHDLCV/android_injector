package com.vigatec.injector.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.persistence.dao.InjectionLogDao
import com.vigatec.injector.data.local.database.AppDatabase
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.data.local.preferences.UserPreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        userDaoProvider: Provider<com.vigatec.injector.data.local.dao.UserDao>
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "injector_database"
        )
        .fallbackToDestructiveMigration() // Para versión 2
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    // Usuario admin predeterminado
                    userDaoProvider.get().insertUser(
                        User(
                            username = "admin",
                            pass = "admin",
                            role = "ADMIN",
                            fullName = "Administrador"
                        )
                    )

                    // Usuario dev para desarrollo y pruebas
                    userDaoProvider.get().insertUser(
                        User(
                            username = "dev",
                            pass = "dev",
                            role = "ADMIN",
                            fullName = "Desarrollador"
                        )
                    )
                }
            }
        }).build()
    }

    @Provides
    @Singleton
    fun provideUserDao(appDatabase: AppDatabase): com.vigatec.injector.data.local.dao.UserDao {
        return appDatabase.userDao()
    }

    @Provides
    @Singleton
    fun provideInjectionLogDao(appDatabase: AppDatabase): InjectionLogDao {
        return appDatabase.injectionLogDao()
    }

    @Provides
    @Singleton
    fun provideUserPreferencesManager(@ApplicationContext context: Context): UserPreferencesManager {
        return UserPreferencesManager(context)
    }
} 