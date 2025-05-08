package com.example.manufacturer.di // O tu ruta

import android.app.Application
import android.util.Log
import com.example.manufacturer.KeySDKManager
// import com.vigatec.manufacturer.ManufacturerSDKManager // Descomenta si también lo necesitas inicializar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SDKInitManager {

    private const val TAG = "SDKInitManager"
    @Volatile
    private var isInitialized = false
    private val mutex = Mutex()

    suspend fun initializeOnce(app: Application) {
        if (isInitialized) {
            return
        }
        mutex.withLock {
            if (isInitialized) {
                return@withLock
            }
            Log.i(TAG, "Iniciando secuencia de inicialización genérica de SDKs...")
            try {
                // Llamada al Manager(s) genérico(s)
                Log.d(TAG, "-> Inicializando KeySDKManager...")
                KeySDKManager.initialize(app)
                // Log.d(TAG, "-> Inicializando ManufacturerSDKManager...") // Si necesitas ambos
                // ManufacturerSDKManager.initialize(app)

                isInitialized = true
                Log.i(TAG, ">>> Secuencia genérica de inicialización completada.")

            } catch (e: Exception) {
                isInitialized = false
                Log.e(TAG, "!!! ERROR FATAL durante la secuencia de inicialización genérica !!!", e)
                throw RuntimeException("Fallo en SDKInitManager.initializeOnce: ${e.message}", e)
            }
        }
    }

    fun isInitialized(): Boolean {
        return isInitialized
    }

    // Considera si release también debe delegar a los managers
    fun releaseSDKs() {
        synchronized(this) { // synchronized está bien para release si no es suspend
            if(isInitialized) {
                Log.i(TAG, "Liberando SDKs...")
                try { KeySDKManager.release() } catch(e: Exception){ Log.e(TAG, "Error releasing KeySDKManager", e)}
                // try { ManufacturerSDKManager.release() } catch(e: Exception){ Log.e(TAG, "Error releasing ManufacturerSDKManager", e)}
                isInitialized = false
                Log.i(TAG, "SDKs liberados.")
            }
        }
    }
}