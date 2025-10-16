package com.example.manufacturer.libraries.aisino

import android.app.Application
import android.util.Log
import com.example.manufacturer.base.controllers.manager.ITmsManager
import com.example.manufacturer.base.controllers.tms.ITmsController
import com.example.manufacturer.base.controllers.tms.TmsException
import com.example.manufacturer.libraries.aisino.wrapper.AisinoTmsController
import com.example.manufacturer.libraries.aisino.vtms.VTMSClientConnectionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manager específico para TMS en dispositivos Aisino/Vanstone.
 * Gestiona la inicialización y acceso al controlador TMS.
 */
object AisinoTmsManager : ITmsManager {

    private const val TAG = "AisinoTmsManager"
    private var tmsControllerInstance: ITmsController? = null
    private var isInitialized = false
    private val initializationMutex = Mutex()

    override suspend fun initialize(application: Application) {
        initializationMutex.withLock {
            if (isInitialized) {
                Log.d(TAG, "AisinoTmsManager ya se encuentra inicializado.")
                return
            }

            Log.d(TAG, "Inicializando AisinoTmsManager...")
            try {
                // Inicializar VTMSClientConnectionManager
                Log.d(TAG, "Inicializando VTMSClientConnectionManager...")
                VTMSClientConnectionManager.init(application)
                Log.d(TAG, "VTMSClientConnectionManager inicializado")

                // Crear la instancia del controlador específico de Aisino con contexto
                val controller = AisinoTmsController(application.applicationContext)

                tmsControllerInstance = controller
                isInitialized = true
                Log.i(TAG, "AisinoTmsManager inicializado con éxito.")

            } catch (e: TmsException) {
                Log.e(TAG, "Fallo al inicializar AisinoTmsManager: ${e.message}", e)
                tmsControllerInstance = null
                isInitialized = false
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado durante la inicialización de AisinoTmsManager", e)
                tmsControllerInstance = null
                isInitialized = false
                throw TmsException("Error inesperado al inicializar AisinoTmsManager: ${e.message}", e)
            }
        }
    }

    override fun getTmsController(): ITmsController? {
        if (!isInitialized || tmsControllerInstance == null) {
            Log.w(TAG, "AisinoTmsManager no está inicializado. Llama a initialize() primero.")
            return null
        }
        return tmsControllerInstance
    }

    override fun release() {
        Log.d(TAG, "Liberando recursos de AisinoTmsManager...")
        try {
            // El SDK de Vanstone TMS no requiere liberación explícita de recursos
            tmsControllerInstance = null
            isInitialized = false
            Log.d(TAG, "AisinoTmsManager liberado.")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la liberación de AisinoTmsManager", e)
        }
    }
}
