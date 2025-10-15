package com.example.manufacturer.di // O tu ruta

import android.app.Application
import android.util.Log
import com.example.communication.libraries.CommunicationSDKManager
import com.example.manufacturer.KeySDKManager
import com.example.manufacturer.TmsSDKManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SDKInitManager {

    private const val TAG = "SDKInitManager"
    @Volatile
    private var isInitialized = false
    private val mutex = Mutex()

    /**
     * Inicializa todos los SDKs necesarios de forma segura y una sola vez.
     * Esta función es 'suspend' para esperar a que las inicializaciones asíncronas terminen.
     */
    suspend fun initializeOnce(app: Application) {
        if (isInitialized) {
            Log.d(TAG, "Los SDKs ya fueron inicializados, omitiendo.")
            return
        }
        mutex.withLock {
            // Doble check por si otra corrutina estaba esperando el lock
            if (isInitialized) {
                return@withLock
            }
            Log.i(TAG, "Iniciando secuencia de inicialización genérica de SDKs...")
            try {
                // --- INICIALIZACIÓN SECUENCIAL DE MANAGERS ---
                // Se inicializan todos los managers, uno después del otro.

                Log.d(TAG, "-> Inicializando KeySDKManager...")
                KeySDKManager.initialize(app)
                Log.i(TAG, "-> KeySDKManager inicializado con éxito.")

                Log.d(TAG, "-> Inicializando CommunicationSDKManager...")
                CommunicationSDKManager.initialize(app)
                Log.i(TAG, "-> CommunicationSDKManager inicializado con éxito.")

                Log.d(TAG, "-> Inicializando TmsSDKManager...")
                try {
                    TmsSDKManager.initialize(app)
                    Log.i(TAG, "-> TmsSDKManager inicializado con éxito.")
                } catch (e: Exception) {
                    // TMS puede no estar disponible en todos los dispositivos, no es crítico
                    Log.w(TAG, "-> TmsSDKManager no pudo inicializarse (puede ser normal en dispositivos sin soporte TMS): ${e.message}")
                }

                isInitialized = true
                Log.i(TAG, ">>> Secuencia genérica de inicialización completada.")

            } catch (e: Exception) {
                isInitialized = false
                Log.e(TAG, "!!! ERROR FATAL durante la secuencia de inicialización genérica !!!", e)
                // Relanzar la excepción para que el llamador (ViewModel) sepa que algo falló
                throw RuntimeException("Fallo en SDKInitManager.initializeOnce: ${e.message}", e)
            }
        }
    }

    fun isInitialized(): Boolean {
        return isInitialized
    }

    fun releaseSDKs() {
        synchronized(this) {
            if (isInitialized) {
                Log.i(TAG, "Liberando SDKs...")
                try { KeySDKManager.release() } catch (e: Exception) { Log.e(TAG, "Error releasing KeySDKManager", e) }
                try { CommunicationSDKManager.release() } catch (e: Exception) { Log.e(TAG, "Error releasing CommunicationSDKManager", e) }
                try { TmsSDKManager.release() } catch (e: Exception) { Log.e(TAG, "Error releasing TmsSDKManager", e) }
                isInitialized = false
                Log.i(TAG, "SDKs liberados.")
            }
        }
    }
}
