package com.example.manufacturer.libraries.aisino // Adjust package if needed

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.manufacturer.base.controllers.manager.IKeyManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.base.controllers.ped.PedException // Import base exception
import com.example.manufacturer.libraries.aisino.wrapper.AisinoPedController // Import your Aisino wrapper

object AisinoKeyManager : IKeyManager {

    private const val TAG = "AisinoKeyManager"
    private var pedControllerInstance: IPedController? = null
    private var isSdkInitialized = false // Tracks if *this manager* is initialized
    private lateinit var applicationContext: Context

    override suspend fun initialize(application: Application) {
        if (isSdkInitialized) {
            Log.d(TAG, "AisinoKeyManager ya se encuentra inicializado.")
            return
        }
        applicationContext = application.applicationContext
        Log.d(TAG, "Inicializando AisinoKeyManager...")
        // IMPORTANTE: Aisino/Vanstone SDK requiere SystemApi.SystemInit_Api()
        // Se asume que esta llamada ya se realizó en el Application.onCreate() o similar.
        // Este 'initialize' crea la instancia del controlador específico.
        Log.i(TAG, "Asegúrate que SystemApi.SystemInit_Api() haya sido llamado previamente.")

        try {
            // Crear la instancia del controlador específico de Aisino
            pedControllerInstance = AisinoPedController(applicationContext)
            // Llamar a la inicialización interna del controlador si es necesario
            val initialized = pedControllerInstance?.initializePed() ?: false
            if (!initialized) {
                throw PedException("Falló la inicialización interna de AisinoPedController.")
            }

            isSdkInitialized = true
            Log.d(TAG, "AisinoKeyManager inicializado con éxito.")
        } catch (e: PedException) {
            // Capturar excepciones específicas de PED lanzadas por el constructor/init del Controller
            Log.e(TAG, "Fallo crítico al inicializar AisinoPedController: ${e.message}", e)
            pedControllerInstance = null
            isSdkInitialized = false
            throw e // Relanzar para que el llamador (KeySDKManager o la app) se entere
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado durante la inicialización de AisinoKeyManager", e)
            pedControllerInstance = null
            isSdkInitialized = false
            // Relanzar como PedException genérica
            throw PedException("Error inesperado al inicializar AisinoKeyManager: ${e.message}", e)
        }
    }

    override fun getPedController(): IPedController? {
        if (!isSdkInitialized) {
            Log.w(TAG, "AisinoKeyManager no está inicializado. Se llamó a getPedController.")
            // Considerar lanzar IllegalStateException para forzar la inicialización previa
            // throw IllegalStateException("AisinoKeyManager no está inicializado. Llama a initialize() primero.")
            return null
        }
        return pedControllerInstance
    }

    override fun release() {
        Log.d(TAG, "Liberando recursos de AisinoKeyManager...")
        try {
            // Llamar a la liberación del controlador específico si es necesario
            pedControllerInstance?.releasePed()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante releasePed() en AisinoPedController", e)
        } finally {
            pedControllerInstance = null
            isSdkInitialized = false
            Log.d(TAG, "AisinoKeyManager liberado.")
        }
    }
}