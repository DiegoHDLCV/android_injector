package com.example.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.persistence.dao.KeyDao
import com.example.persistence.dao.UserDao
import com.example.persistence.entities.UsersEntity
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
        // Usamos Provider<UserDao> para romper una posible dependencia cíclica
        // si UserDao se usara directamente en el callback para crear el admin.
        // En este caso, es más seguro usar SQL directo en el callback.
        userDaoProvider: Provider<UserDao>
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
                    // Se ejecuta solo cuando la base de datos se crea por primera vez.
                    // Aquí podemos insertar el primer usuario administrador.
                    // Es mejor usar un Executor para operaciones de BD fuera del hilo principal.
                    Executors.newSingleThreadExecutor().execute {
                        // No podemos usar el DAO directamente aquí de forma sencilla antes de que la BD esté completamente construida.
                        // Por eso, usamos SQL crudo.
                        // O, si se usa Provider<UserDao> y se obtiene dentro del execute, podría funcionar,
                        // pero SQL crudo es más directo para el callback onCreate.
                        val adminEmail = "admin@example.com" // Cambia esto
                        val adminPasswordHash = "hashed_admin_password" // ¡DEBES HASHEAR ESTA CONTRASEÑA CORRECTAMENTE!
                        val adminName = "Administrador Principal"

                        // Usamos SQL crudo para insertar el primer administrador
                        // Esto es más seguro dentro del callback onCreate.
                        db.execSQL(
                            "INSERT INTO user (name, email, passwordHash, role) VALUES (?, ?, ?, ?)",
                            arrayOf(adminName, adminEmail, adminPasswordHash, Role.ADMIN.name)
                        )
                        println("DatabaseModule: Primer administrador insertado en onCreate.")

                        // Si tienes la entidad `counters` y su callback de DatabaseProvider,
                        // esa lógica también podría ir aquí o mantenerse separada si DatabaseProvider
                        // tiene un propósito diferente. Por simplicidad, la omito aquí.
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Se ejecuta cada vez que la base de datos se abre.
                    // Podrías verificar si el admin existe y crearlo si no, como una salvaguarda,
                    // pero onCreate es el lugar más apropiado para la creación inicial.
                    CoroutineScope(Dispatchers.IO).launch {
                        val userDao = userDaoProvider.get() // Obtener el DAO de forma segura
                        if (userDao.getFirstAdmin() == null) {
                            // Esto es una doble verificación, onCreate debería haberlo manejado.
                            // Considera si esta lógica es realmente necesaria en onOpen.
                            val adminEmail = "admin_on_open@example.com"
                            val adminPasswordHash = "hashed_admin_password_on_open"
                            val adminName = "Admin (onOpen Check)"
                            userDao.insertUser(
                                UsersEntity(
                                    name = adminName,
                                    email = adminEmail,
                                    passwordHash = adminPasswordHash, // ¡USA UN HASH REAL!
                                    role = Role.ADMIN.name
                                )
                            )
                            println("DatabaseModule: Admin creado en onOpen porque no se encontró.")
                        }
                    }
                }
            })
            .build()
    }

    @Provides
    @Singleton // Asegúrate que los DAOs también sean Singleton si la BD lo es.
    fun provideUserDao(appDatabase: AppDatabase): UserDao = appDatabase.userDao()

    @Provides
    @Singleton // Asegúrate que los DAOs también sean Singleton si la BD lo es.
    fun provideKeyDao(appDatabase: AppDatabase): KeyDao = appDatabase.keyDao()
}