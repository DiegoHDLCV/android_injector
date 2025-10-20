package com.vigatec.injector.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.persistence.dao.InjectionLogDao
import com.vigatec.injector.data.local.dao.PermissionDao
import com.vigatec.injector.data.local.database.AppDatabase
import com.vigatec.injector.data.local.entity.Permission
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.data.local.preferences.UserPreferencesManager
import com.vigatec.injector.data.local.preferences.SessionManager
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

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            Log.d(TAG, "Ejecutando MIGRACIÓN 2 → 3: Sistema de Permisos")
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            
            // Crear tabla permissions
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS permissions (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL
                )
            """)
            Log.d(TAG, "✓ Tabla 'permissions' creada")
            
            // Crear tabla user_permissions (muchos a muchos)
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS user_permissions (
                    userId INTEGER NOT NULL,
                    permissionId TEXT NOT NULL,
                    PRIMARY KEY(userId, permissionId),
                    FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY(permissionId) REFERENCES permissions(id) ON DELETE CASCADE
                )
            """)
            Log.d(TAG, "✓ Tabla 'user_permissions' creada")
            
            // Crear índices para optimizar las consultas
            database.execSQL("CREATE INDEX IF NOT EXISTS index_user_permissions_userId ON user_permissions(userId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_user_permissions_permissionId ON user_permissions(permissionId)")
            Log.d(TAG, "✓ Índices creados")
            
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            Log.d(TAG, "✓ Migración 2 → 3 completada exitosamente")
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        userDaoProvider: Provider<com.vigatec.injector.data.local.dao.UserDao>,
        permissionDaoProvider: Provider<PermissionDao>
    ): AppDatabase {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Creando instancia de AppDatabase")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "injector_database"
        )
        .addMigrations(MIGRATION_2_3)
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
                        
                        Log.i(TAG, "")
                        Log.i(TAG, "Insertando permisos predefinidos...")
                        
                        // Definir todos los permisos del sistema
                        val permissions = listOf(
                            Permission("key_vault", "Gestión de Llaves", "Acceso completo a la bóveda de llaves (KeyVault)"),
                            Permission("ceremony_kek", "Ceremonia KEK", "Iniciar ceremonia para llaves KEK"),
                            Permission("ceremony_operational", "Ceremonia Operacional", "Iniciar ceremonia para llaves operacionales"),
                            Permission("select_ktk", "Seleccionar KTK", "Capacidad de seleccionar llaves KTK"),
                            Permission("manage_profiles", "Gestionar Perfiles", "Crear y administrar perfiles de inyección"),
                            Permission("view_logs", "Ver Logs", "Acceso a logs de inyección"),
                            Permission("manage_users", "Gestionar Usuarios", "Crear, editar y eliminar usuarios del sistema"),
                            Permission("tms_config", "Configuración TMS", "Acceso a configuración del TMS"),
                            Permission("raw_data_listener", "Escuchar Datos Raw", "Acceso a la herramienta de escucha de datos en crudo")
                        )
                        
                        permissionDaoProvider.get().insertPermissions(permissions)
                        Log.i(TAG, "✓ ${permissions.size} permisos insertados")
                        
                        // Asignar TODOS los permisos a usuarios ADMIN (admin y dev)
                        Log.i(TAG, "Asignando permisos a usuarios ADMIN...")
                        
                        permissions.forEach { permission ->
                            permissionDaoProvider.get().updateUserPermissions(
                                adminId.toInt(),
                                permissions.map { it.id }
                            )
                        }
                        Log.i(TAG, "✓ Todos los permisos asignados a usuario 'admin'")
                        
                        permissionDaoProvider.get().updateUserPermissions(
                            devId.toInt(),
                            permissions.map { it.id }
                        )
                        Log.i(TAG, "✓ Todos los permisos asignados a usuario 'dev'")
                        
                        Log.i(TAG, "═══════════════════════════════════════════════════════════")
                        Log.i(TAG, "✓✓✓ Usuarios y permisos predeterminados creados exitosamente")
                        Log.i(TAG, "═══════════════════════════════════════════════════════════")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "✗✗✗ ERROR al crear usuarios y permisos predeterminados", e)
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
    fun providePermissionDao(appDatabase: AppDatabase): PermissionDao {
        return appDatabase.permissionDao()
    }

    @Provides
    @Singleton
    fun provideUserPreferencesManager(@ApplicationContext context: Context): UserPreferencesManager {
        return UserPreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager {
        return SessionManager(context)
    }

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }
} 