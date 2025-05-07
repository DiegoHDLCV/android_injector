package com.example.persistence

import android.content.ContentValues
import android.content.Context
import androidx.room.OnConflictStrategy
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    // --- INICIO: Definición del Callback ---
    private val roomCallback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Inserta la fila inicial de contadores cuando la base de datos se crea por primera vez.
            // Usar SQL directo aquí es más sencillo que usar el DAO dentro del callback.
            println("DatabaseProvider: RoomDatabase.Callback.onCreate executed - Initializing counters.") // Log para verificar
            val initialCounters = ContentValues().apply {
                put("id", 1L) // Asegúrate que el ID sea 1, como en tu DAO query
                put("stan", 0) // Valor inicial por defecto para stan
                put("invoice", 0) // Valor inicial por defecto para invoice
            }
            try {
                // Intenta insertar. IGNORE previene errores si por alguna razón extrema ya existe.
                val result = db.insert("counters", OnConflictStrategy.IGNORE, initialCounters)
                if (result == -1L) {
                    println("DatabaseProvider: Initial counters row with id=1 might already exist.")
                } else {
                    println("DatabaseProvider: Initial counters row inserted successfully with id=1.")
                }
            } catch (e: Exception) {
                // Captura cualquier excepción potencial durante la inserción inicial
                println("DatabaseProvider: Error inserting initial counters: ${e.message}")
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Puedes añadir código aquí si necesitas hacer algo cada vez que la base de datos se abre
            // println("DatabaseProvider: Database opened.")
        }
    }
    // --- FIN: Definición del Callback ---


    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            // Revisa si ya existe la instancia dentro del bloque sincronizado también
            INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
        }
    }

    // Método helper para construir la base de datos (más limpio)
    private fun buildDatabase(context: Context): AppDatabase {
        println("DatabaseProvider: Building new database instance.") // Log para verificar
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "pos_database" // Nombre de tu base de datos
        )
            // Destruye y recrea la base de datos si las migraciones no se proporcionan.
            // ¡CUIDADO! Esto elimina todos los datos existentes en una actualización de versión sin migración.
            .fallbackToDestructiveMigration()
            // --- AÑADIDO: Añade el callback al constructor de la base de datos ---
            .addCallback(roomCallback)
            // Puedes añadir otras opciones como .allowMainThreadQueries() (NO RECOMENDADO) o .setQueryExecutor(), etc.
            .build()

    }
}