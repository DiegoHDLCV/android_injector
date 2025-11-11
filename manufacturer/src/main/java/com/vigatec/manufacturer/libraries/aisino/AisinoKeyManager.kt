package com.vigatec.manufacturer.libraries.aisino

import android.app.Application
import android.util.Log
import com.vigatec.manufacturer.base.controllers.manager.IKeyManager
import com.vigatec.manufacturer.base.controllers.ped.IPedController
import com.vigatec.manufacturer.base.controllers.ped.PedException
import com.vigatec.manufacturer.libraries.aisino.wrapper.AisinoPedController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AisinoKeyManager : IKeyManager {

    private const val TAG = "AisinoKeyManager"
    private var pedControllerInstance: IPedController? = null
    private var isInitialized = false
    private val initializationMutex = Mutex()

    // Flujo de estado para notificar cuando la inicialización está completa
    private val _initializationState = MutableStateFlow(false)
    val initializationState: StateFlow<Boolean> = _initializationState.asStateFlow()

    // El método connect no es estándar en la interfaz, pero se puede implementar si es necesario.
    override suspend fun connect() {
        Log.d(TAG, "Connect method called, but initialization is handled in 'initialize'.")
        // Si se necesita lógica de conexión separada, iría aquí.
    }

    override suspend fun initialize(application: Application) {
        // Usa un Mutex para evitar condiciones de carrera si se llama desde múltiples corrutinas
        initializationMutex.withLock {
            if (isInitialized) {
                Log.i(TAG, ">>> AisinoKeyManager ya se encuentra inicializado. Omitiendo re-inicialización.")
                return
            }

            Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ INICIANDO INICIALIZACIÓN DE AisinoKeyManager")
            Log.i(TAG, "╚══════════════════════════════════════════════════════════════")

            // CORRECCIÓN CRÍTICA: Ejecutar en Main Thread
            // Los SDKs de hardware (Aisino, NewPOS) requieren Main Thread para:
            // 1. Acceso a servicios del sistema Android
            // 2. Registro de BroadcastReceivers para detección de dispositivos USB
            // 3. Recepción de intents del sistema para cable keyreceiver
            withContext(Dispatchers.Main) {
                try {
                    Log.d(TAG, "1/3 - Creando AisinoPedController...")
                    // 1. Crear la instancia del controlador específico de Aisino
                    // Se pasa el objeto 'application' directamente, que es del tipo correcto.
                    val controller = AisinoPedController(application)
                    Log.d(TAG, "    ✓ AisinoPedController instanciado")

                    Log.d(TAG, "2/3 - Inicializando SDK de Aisino (SystemApi + SdkApi)...")
                    // 2. Llamar a la inicialización interna del controlador
                    val sdkInitialized = controller.initializePed(application)
                    if (!sdkInitialized) {
                        // Si initializePed falla, lanzará una excepción que será capturada abajo.
                        // Este check es una salvaguarda adicional.
                        throw PedException("Falló la inicialización interna de AisinoPedController.")
                    }
                    Log.d(TAG, "    ✓ SDK de Aisino inicializado correctamente")

                    Log.d(TAG, "3/3 - Finalizando inicialización de AisinoKeyManager...")
                    // 3. Asignar la instancia solo después de una inicialización exitosa
                    pedControllerInstance = controller
                    isInitialized = true
                    _initializationState.value = true // Emitir que la inicialización está completa
                    Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
                    Log.i(TAG, "║ ✅ AisinoKeyManager COMPLETAMENTE INICIALIZADO Y LISTO")
                    Log.i(TAG, "╚══════════════════════════════════════════════════════════════")

                } catch (e: PedException) {
                    Log.e(TAG, "❌ Fallo crítico al inicializar AisinoPedController: ${e.message}", e)
                    pedControllerInstance = null
                    isInitialized = false
                    throw e // Relanzar para que el llamador se entere del fallo
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error inesperado durante la inicialización de AisinoKeyManager", e)
                    pedControllerInstance = null
                    isInitialized = false
                    // Envuelve la excepción inesperada en una PedException para mantener la consistencia
                    throw PedException("Error inesperado al inicializar AisinoKeyManager: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Verifica si el AisinoKeyManager está completamente inicializado y listo para usar.
     * Útil para consultas no-blocking sobre el estado de inicialización.
     */
    fun isReady(): Boolean {
        Log.d(TAG, "isReady() called - Estado actual: isInitialized=$isInitialized, pedControllerInstance=${pedControllerInstance != null}")
        return isInitialized && pedControllerInstance != null
    }

    override fun getPedController(): IPedController {
        if (!isInitialized || pedControllerInstance == null) {
            // Es preferible lanzar una excepción para forzar un flujo de código correcto.
            // Devolver null puede llevar a NullPointerExceptions en el código del llamador.
            val errorMsg = "AisinoKeyManager no está inicializado (isInitialized=$isInitialized, instance=${pedControllerInstance != null}). Llama a initialize() primero."
            Log.e(TAG, errorMsg)
            throw IllegalStateException(errorMsg)
        }
        Log.d(TAG, "getPedController() retornando instancia exitosamente")
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
            _initializationState.value = false // Resetear el estado
            Log.d(TAG, "AisinoKeyManager liberado.")
        }
    }
}
