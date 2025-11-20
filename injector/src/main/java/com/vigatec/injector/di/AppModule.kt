package com.vigatec.injector.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vigatec.persistence.dao.InjectionLogDao
import com.vigatec.injector.data.local.dao.PermissionDao
import com.vigatec.injector.data.local.dao.UserDao
import com.vigatec.injector.data.local.database.AppDatabase
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.data.local.preferences.SessionManager
import com.vigatec.injector.data.local.preferences.UserPreferencesManager
import com.vigatec.injector.data.local.preferences.CustodianTimeoutPreferencesManager
import com.vigatec.injector.util.PermissionManager
import com.vigatec.injector.util.PermissionsCatalog
import com.vigatec.injector.BuildConfig
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

    private const val DEFAULT_ADMIN_PASSWORD = "Vigatec2025@@@@@@"
    private const val DEFAULT_DEV_PASSWORD = "Vigatec2025@@@@@@"
    private const val DEFAULT_OPERATOR_PASSWORD = "Operador2025@"
    private const val OPERATOR_ONE_USERNAME = "operador1"
    private const val OPERATOR_TWO_USERNAME = "operador2"
    private const val LEGACY_RAW_DATA_PERMISSION_ID = "raw_data_listener"

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i(TAG, "Ejecutando migración 3→4: actualizando permisos y eliminando legado RawDataListener.")
            db.execSQL(
                "DELETE FROM user_permissions WHERE permissionId = ?",
                arrayOf(LEGACY_RAW_DATA_PERMISSION_ID)
            )
            db.execSQL(
                "DELETE FROM permissions WHERE id = ?",
                arrayOf(LEGACY_RAW_DATA_PERMISSION_ID)
            )

            PermissionsCatalog.SYSTEM_PERMISSIONS.forEach { permission ->
                db.execSQL(
                    "INSERT OR REPLACE INTO permissions (id, name, description) VALUES (?, ?, ?)",
                    arrayOf(permission.id, permission.name, permission.description)
                )
            }
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        userDaoProvider: Provider<UserDao>,
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
        .fallbackToDestructiveMigration()
        .addMigrations(MIGRATION_3_4)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
                Log.i(TAG, "⚠ BASE DE DATOS CREADA POR PRIMERA VEZ ⚠")
                Log.i(TAG, "Sincronizando permisos y usuarios predeterminados...")
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val userDao = userDaoProvider.get()
                        val permissionDao = permissionDaoProvider.get()
                        synchronizeDefaultSetup(userDao, permissionDao)
                        Log.i(TAG, "═══════════════════════════════════════════════════════════")
                        Log.i(TAG, "✓ Sincronización inicial completada correctamente")
                        Log.i(TAG, "═══════════════════════════════════════════════════════════")
                    } catch (e: Exception) {
                        Log.e(TAG, "✗✗✗ ERROR al sincronizar datos predeterminados", e)
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

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val userDao = userDaoProvider.get()
                        val permissionDao = permissionDaoProvider.get()
                        synchronizeDefaultSetup(userDao, permissionDao)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al sincronizar datos predeterminados al abrir la BD", e)
                    }
                }
            }
        }).build()
    }

    @Provides
    @Singleton
    fun provideUserDao(appDatabase: AppDatabase): UserDao {
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

    private suspend fun synchronizeDefaultSetup(
        userDao: UserDao,
        permissionDao: PermissionDao
    ) {
        ensureSystemPermissions(permissionDao)

        val adminResult = ensureUserAccount(
            userDao = userDao,
            username = "admin",
            defaultPassword = DEFAULT_ADMIN_PASSWORD,
            role = PermissionManager.ROLE_SUPERVISOR,
            fullName = "Administrador"
        )
        logUserSyncResult("ADMIN", adminResult)

        if (BuildConfig.FLAVOR == "dev") {
            val devResult = ensureUserAccount(
                userDao = userDao,
                username = "dev",
                defaultPassword = DEFAULT_DEV_PASSWORD,
                role = PermissionManager.ROLE_SUPERVISOR,
                fullName = "Desarrollador"
            )
            logUserSyncResult("DEV", devResult)
        }

        val operator1Result = ensureUserAccount(
            userDao = userDao,
            username = OPERATOR_ONE_USERNAME,
            defaultPassword = DEFAULT_OPERATOR_PASSWORD,
            role = PermissionManager.ROLE_OPERATOR,
            fullName = "Operador 1"
        )
        logUserSyncResult("OPERADOR 1", operator1Result)

        val operator2Result = ensureUserAccount(
            userDao = userDao,
            username = OPERATOR_TWO_USERNAME,
            defaultPassword = DEFAULT_OPERATOR_PASSWORD,
            role = PermissionManager.ROLE_OPERATOR,
            fullName = "Operador 2"
        )
        logUserSyncResult("OPERADOR 2", operator2Result)

        assignOperatorPermissions(operator1Result.userId, permissionDao, OPERATOR_ONE_USERNAME)
        assignOperatorPermissions(operator2Result.userId, permissionDao, OPERATOR_TWO_USERNAME)
    }

    private suspend fun ensureSystemPermissions(permissionDao: PermissionDao) {
        Log.d(TAG, "Actualizando catálogo de permisos del sistema…")
        permissionDao.insertPermissions(PermissionsCatalog.SYSTEM_PERMISSIONS)
        permissionDao.deleteUserPermissionsByPermissionId(LEGACY_RAW_DATA_PERMISSION_ID)
        permissionDao.deletePermissionById(LEGACY_RAW_DATA_PERMISSION_ID)
    }

    private suspend fun assignOperatorPermissions(
        userId: Int?,
        permissionDao: PermissionDao,
        username: String
    ) {
        if (userId == null) {
            Log.w(TAG, "No se pudieron asignar permisos al usuario '$username' (ID nulo).")
            return
        }

        val permissions = PermissionsCatalog.OPERATOR_DEFAULT_PERMISSION_IDS.toList()
        permissionDao.updateUserPermissions(userId, permissions)
        Log.i(TAG, "✓ Permisos asignados a '$username': ${permissions.joinToString(", ")}")
    }

    private suspend fun ensureUserAccount(
        userDao: UserDao,
        username: String,
        defaultPassword: String,
        role: String,
        fullName: String,
        ensureActive: Boolean = true
    ): EnsureUserResult {
        val existingUser = userDao.findByUsername(username)
        if (existingUser == null) {
            val newUser = User(
                username = username,
                pass = defaultPassword,
                role = role,
                fullName = fullName,
                isActive = ensureActive
            )
            val insertedId = userDao.insertUser(newUser)
            return if (insertedId > 0) {
                EnsureUserResult(
                    userId = insertedId.toInt(),
                    created = true,
                    passwordUpdated = false,
                    profileUpdated = false
                )
            } else {
                Log.e(TAG, "✗ No se pudo insertar el usuario '$username' (resultado $insertedId)")
                EnsureUserResult(null, created = false, passwordUpdated = false, profileUpdated = false)
            }
        }

        var updatedUser = existingUser
        var profileUpdated = false

        if (existingUser.role != role || existingUser.fullName != fullName || (ensureActive && !existingUser.isActive)) {
            updatedUser = existingUser.copy(
                role = role,
                fullName = fullName,
                isActive = if (ensureActive) true else existingUser.isActive
            )
            userDao.updateUser(updatedUser)
            profileUpdated = true
        }

        val passwordUpdated = if (existingUser.pass != defaultPassword) {
            userDao.updateUserPassword(existingUser.id, defaultPassword)
            true
        } else {
            false
        }

        return EnsureUserResult(
            userId = updatedUser.id,
            created = false,
            passwordUpdated = passwordUpdated,
            profileUpdated = profileUpdated
        )
    }

    private fun logUserSyncResult(label: String, result: EnsureUserResult) {
        if (result.userId == null) {
            Log.e(TAG, "✗ No se pudo asegurar el usuario $label.")
            return
        }

        val messages = mutableListOf<String>()
        if (result.created) messages.add("creado")
        if (result.profileUpdated) messages.add("perfil actualizado")
        if (result.passwordUpdated) messages.add("contraseña sincronizada")

        if (messages.isEmpty()) {
            Log.i(TAG, "✓ Usuario $label verificado (sin cambios).")
        } else {
            Log.i(TAG, "✓ Usuario $label (${messages.joinToString(", ")})")
        }
    }

    private data class EnsureUserResult(
        val userId: Int?,
        val created: Boolean,
        val passwordUpdated: Boolean,
        val profileUpdated: Boolean
    )
}