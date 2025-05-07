package com.example.manufacturer.libraries.newpos // Adjust package if needed

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.manufacturer.base.controllers.manager.IKeyManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.base.controllers.ped.PedException // Import base exception
import com.example.manufacturer.libraries.newpos.wrapper.NewposPedController

object NewposKeyManager : IKeyManager {

    private const val TAG = "NewposKeyManager"
    private var pedControllerInstance: IPedController? = null
    private var isSdkInitialized = false
    private lateinit var applicationContext: Context

    override suspend fun initialize(application: Application) {
        if (isSdkInitialized) {
            Log.d(TAG, "NewposKeyManager ya se encuentra inicializado.")
            return
        }
        applicationContext = application.applicationContext
        Log.d(TAG, "Inicializando NewposKeyManager...")

        try {
            // El NewposPedController se encargará de obtener la instancia de Ped.
            pedControllerInstance = NewposPedController(applicationContext)

            // Llamar a la inicialización interna del controlador si es necesario
            val initialized = pedControllerInstance?.initializePed() ?: false
            if (!initialized) {
                throw PedException("Falló la inicialización interna de NewposPedController.")
            }

            isSdkInitialized = true
            Log.d(TAG, "NewposKeyManager inicializado con éxito.")
        } catch (e: PedException) { // Catch specific PED exceptions first
            Log.e(TAG, "Fallo crítico al inicializar NewposPedController: ${e.message}", e)
            pedControllerInstance = null
            isSdkInitialized = false
            throw e // Relanzar para que el llamador se entere
        } catch (e: Exception) { // Catch other potential exceptions during init
            Log.e(TAG, "Error inesperado durante la inicialización de NewposKeyManager", e)
            pedControllerInstance = null
            isSdkInitialized = false
            throw PedException("Error inesperado al inicializar NewposKeyManager: ${e.message}", e) // Wrap as PedException
        }
    }

    override fun getPedController(): IPedController? {
        if (!isSdkInitialized) {
            Log.w(TAG, "NewposKeyManager no está inicializado. Se llamó a getPedController.")
            // Considerar lanzar IllegalStateException para forzar la inicialización previa
            // throw IllegalStateException("NewposKeyManager no está inicializado. Llama a initialize() primero.")
            return null
        }
        return pedControllerInstance
    }

    override fun release() {
        Log.d(TAG, "Liberando recursos de NewposKeyManager...")
        try {
            // --- START CORRECTION ---
            // Call the standard release method from the interface
            pedControllerInstance?.releasePed()
            // Remove call to non-existent releasePedInternal()
            // (pedControllerInstance as? NewposPedController)?.releasePedInternal() // REMOVED
            // --- END CORRECTION ---
        } catch (e: Exception) {
            Log.e(TAG, "Error durante releasePed() en NewposPedController", e)
        } finally {
            pedControllerInstance = null
            isSdkInitialized = false
            Log.d(TAG, "NewposKeyManager liberado.")
        }
    }
}