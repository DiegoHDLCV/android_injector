package com.example.manufacturer.libraries.aisino

import android.app.Application
import android.util.Log
import com.example.manufacturer.base.controllers.manager.IKeyManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.base.controllers.ped.PedException
import com.example.manufacturer.libraries.aisino.wrapper.AisinoPedController
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AisinoKeyManager : IKeyManager {

    private const val TAG = "AisinoKeyManager"
    private var pedControllerInstance: IPedController? = null
    private var isInitialized = false
    private val initializationMutex = Mutex()

    // El método connect no es estándar en la interfaz, pero se puede implementar si es necesario.
    override suspend fun connect() {
        Log.d(TAG, "Connect method called, but initialization is handled in 'initialize'.")
        // Si se necesita lógica de conexión separada, iría aquí.
    }

    override suspend fun initialize(application: Application) {
        // Usa un Mutex para evitar condiciones de carrera si se llama desde múltiples corrutinas
        initializationMutex.withLock {
            if (isInitialized) {
                Log.d(TAG, "AisinoKeyManager ya se encuentra inicializado.")
                return
            }

            Log.d(TAG, "Inicializando AisinoKeyManager...")
            try {
                // 1. Crear la instancia del controlador específico de Aisino
                // Se pasa el objeto 'application' directamente, que es del tipo correcto.
                val controller = AisinoPedController(application)

                // 2. Llamar a la inicialización interna del controlador
                val sdkInitialized = controller.initializePed(application)
                if (!sdkInitialized) {
                    // Si initializePed falla, lanzará una excepción que será capturada abajo.
                    // Este check es una salvaguarda adicional.
                    throw PedException("Falló la inicialización interna de AisinoPedController.")
                }

                // 3. Asignar la instancia solo después de una inicialización exitosa
                pedControllerInstance = controller
                isInitialized = true
                Log.d(TAG, "AisinoKeyManager inicializado con éxito.")

            } catch (e: PedException) {
                Log.e(TAG, "Fallo crítico al inicializar AisinoPedController: ${e.message}", e)
                pedControllerInstance = null
                isInitialized = false
                throw e // Relanzar para que el llamador se entere del fallo
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado durante la inicialización de AisinoKeyManager", e)
                pedControllerInstance = null
                isInitialized = false
                // Envuelve la excepción inesperada en una PedException para mantener la consistencia
                throw PedException("Error inesperado al inicializar AisinoKeyManager: ${e.message}", e)
            }
        }
    }

    override fun getPedController(): IPedController {
        if (!isInitialized || pedControllerInstance == null) {
            // Es preferible lanzar una excepción para forzar un flujo de código correcto.
            // Devolver null puede llevar a NullPointerExceptions en el código del llamador.
            throw IllegalStateException("AisinoKeyManager no está inicializado. Llama a initialize() primero.")
        }
        return pedControllerInstance!!
    }

    override fun release() {
        // También se puede usar el mutex aquí si la liberación pudiera ser llamada concurrentemente
        Log.d(TAG, "Liberando recursos de AisinoKeyManager...")
        try {
            pedControllerInstance?.releasePed()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante releasePed() en AisinoPedController", e)
        } finally {
            pedControllerInstance = null
            isInitialized = false
            Log.d(TAG, "AisinoKeyManager liberado.")
        }
    }
}
