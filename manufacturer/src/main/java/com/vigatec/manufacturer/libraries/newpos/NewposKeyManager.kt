package com.vigatec.manufacturer.libraries.newpos

import android.app.Application
import android.content.Context
import android.util.Log
import com.vigatec.manufacturer.base.controllers.manager.IKeyManager
import com.vigatec.manufacturer.base.controllers.ped.IPedController
import com.vigatec.manufacturer.base.controllers.ped.PedException
import com.vigatec.manufacturer.libraries.newpos.wrapper.NewposPedController
import com.pos.device.SDKManager
import com.pos.device.SDKManagerCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object NewposKeyManager : IKeyManager {

    private const val TAG = "NewposKeyManager"
    private var pedControllerInstance: IPedController? = null
    @Volatile private var isSdkInitialized = false
    private var applicationContext: Context? = null

    override suspend fun connect() {
        // En esta implementación, la conexión se maneja dentro de initialize()
    }

    override suspend fun initialize(application: Application) {
        if (isSdkInitialized) {
            Log.i(TAG, "NewposKeyManager ya está inicializado.")
            return
        }

        applicationContext = application.applicationContext
        Log.i(TAG, "Iniciando NewposKeyManager...")

        // --- INICIO DE LA CORRECCIÓN ---
        // Forzar la ejecución en el hilo principal (Main Thread), que es un requisito común
        // para la inicialización de SDKs de hardware en Android.
        withContext(Dispatchers.Main) {
            try {
                // Usar suspendCancellableCoroutine para esperar el callback asíncrono de SDKManager.init
                suspendCancellableCoroutine<Unit> { continuation ->
                    val callback = object : SDKManagerCallback {
                        override fun onFinish() {
                            Log.i(TAG, ">>> SDKManagerCallback.onFinish() recibido.")
                            if (continuation.isActive) {
                                continuation.resume(Unit) // Señal de éxito a la corutina
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        Log.w(TAG, "La inicialización fue cancelada.")
                    }

                    try {
                        // Esta llamada ahora se ejecuta en el hilo principal
                        SDKManager.init(application.applicationContext, callback)
                        Log.d(TAG, "Llamada a SDKManager.init realizada, esperando onFinish...")
                    } catch (e: Exception) {
                        Log.e(TAG, "Excepción síncrona al llamar a SDKManager.init", e)
                        if (continuation.isActive) {
                            continuation.resumeWithException(e) // Falla la corutina
                        }
                    }
                } // La corutina se reanuda aquí después de onFinish

                // Si llegamos aquí, SDKManager.init terminó exitosamente
                Log.i(TAG, "SDKManager.init completado. Procediendo a inicializar NewposPedController...")

                // Ahora que SDKManager está listo, se puede instanciar el PedController.
                // El constructor de NewposPedController llamará a Ped.getInstance(), que ahora debería funcionar.
                val ctx = applicationContext ?: throw IllegalStateException("Contexto nulo después de inicialización.")
                pedControllerInstance = NewposPedController(ctx)
                isSdkInitialized = true // Marcar como listo solo si todo salió bien
                Log.i(TAG, ">>> NewposKeyManager inicializado exitosamente (incluyendo PedController).")

            } catch (e: Exception) { // Captura excepciones de la corutina o de la inicialización del PedController
                isSdkInitialized = false
                Log.e(TAG, "Error durante la inicialización de NewposKeyManager", e)
                // Envolver la excepción original para no perder la causa raíz
                throw PedException("Fallo al inicializar NewposKeyManager: ${e.message}", e)
            }
        }
        // --- FIN DE LA CORRECCIÓN ---
    }

    override fun getPedController(): IPedController? {
        if (!isSdkInitialized) {
            Log.e(TAG, "getPedController llamado pero NewposKeyManager no está inicializado.")
            return null
        }
        return pedControllerInstance
    }

    override fun release() {
        Log.d(TAG, "Liberando recursos para NewposKeyManager y SDKManager...")
        // La instancia del PedController no tiene un método de liberación explícito
        pedControllerInstance = null
        applicationContext = null
        isSdkInitialized = false
        try {
            SDKManager.release()
            Log.i(TAG, "SDKManager.release() llamado.")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante SDKManager.release()", e)
        }
        Log.d(TAG, "NewposKeyManager liberado.")
    }
}