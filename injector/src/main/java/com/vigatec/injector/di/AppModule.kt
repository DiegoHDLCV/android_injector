package com.vigatec.injector.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.persistence.dao.InjectionLogDao
import com.vigatec.injector.data.local.dao.PermissionDao
import com.vigatec.injector.data.local.database.AppDatabase
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.data.local.preferences.SessionManager
import com.vigatec.injector.data.local.preferences.UserPreferencesManager
import com.vigatec.injector.data.local.preferences.CustodianTimeoutPreferencesManager
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

    private const val TAG = "AppModule"

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        userDaoProvider: Provider<com.vigatec.injector.data.local.dao.UserDao>
    ): AppDatabase {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Creando instancia de AppDatabase")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "injector_database"
        )
        .fallbackToDestructiveMigration() // Para versión 2
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
                Log.i(TAG, "⚠ BASE DE DATOS CREADA POR PRIMERA VEZ ⚠")
                Log.i(TAG, "Insertando usuarios predeterminados...")
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Usuario admin predeterminado
                        val adminUser = User(
                            username = "admin",
                            pass = "admin",
                            role = "ADMIN",
                            fullName = "Administrador",
                            isActive = true  // IMPORTANTE: Usuario activo por defecto
                        )
                        Log.d(TAG, "Insertando usuario ADMIN:")
                        Log.d(TAG, "  - Username: '${adminUser.username}'")
                        Log.d(TAG, "  - Password: '${adminUser.pass}'")
                        Log.d(TAG, "  - Role: ${adminUser.role}")
                        Log.d(TAG, "  - FullName: ${adminUser.fullName}")
                        
                        val adminId = userDaoProvider.get().insertUser(adminUser)
                        Log.i(TAG, "✓ Usuario ADMIN insertado con ID: $adminId")

                        // Usuario dev para desarrollo y pruebas
                        val devUser = User(
                            username = "dev",
                            pass = "dev",
                            role = "ADMIN",
                            fullName = "Desarrollador",
                            isActive = true  // IMPORTANTE: Usuario activo por defecto
                        )
                        Log.d(TAG, "Insertando usuario DEV:")
                        Log.d(TAG, "  - Username: '${devUser.username}'")
                        Log.d(TAG, "  - Password: '${devUser.pass}'")
                        Log.d(TAG, "  - Role: ${devUser.role}")
                        Log.d(TAG, "  - FullName: ${devUser.fullName}")
                        
                        val devId = userDaoProvider.get().insertUser(devUser)
                        Log.i(TAG, "✓ Usuario DEV insertado con ID: $devId")
                        
                        Log.i(TAG, "═══════════════════════════════════════════════════════════")
                        Log.i(TAG, "✓✓✓ Usuarios predeterminados creados exitosamente")
                        Log.i(TAG, "═══════════════════════════════════════════════════════════")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "✗✗✗ ERROR al crear usuarios predeterminados", e)
                        Log.e(TAG, "═══════════════════════════════════════════════════════════")
                    }
                }
            }
            
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Log.d(TAG, "─────────────────────────────────────────────────────────────")
                Log.d(TAG, "Base de datos ABIERTA (ya existía)")
                Log.d(TAG, "")
                Log.d(TAG, "NOTA: isActive controla el acceso (habilitado/deshabilitado)")
                Log.d(TAG, "      Los admins pueden activar/desactivar usuarios desde Gestión")
                Log.d(TAG, "─────────────────────────────────────────────────────────────")
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

    @Provides
    @Singleton
    fun providePermissionDao(appDatabase: AppDatabase): PermissionDao {
        return appDatabase.permissionDao()
    }

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager {
        return SessionManager(context)
    }

    @Provides
    @Singleton
    fun provideCustodianTimeoutPreferencesManager(@ApplicationContext context: Context): CustodianTimeoutPreferencesManager {
        return CustodianTimeoutPreferencesManager(context)
    }
}