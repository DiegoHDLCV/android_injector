package com.example.manufacturer.libraries.newpos // Adjust package if needed

import android.app.Application
import android.content.Context
import android.util.Log
import com.android.newpos.libemv.PBOCManager
import com.example.manufacturer.base.controllers.manager.IKeyManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.base.controllers.ped.PedException
import com.example.manufacturer.libraries.newpos.wrapper.NewposPedController
// Importar SDKManager y Callback de NewPOS
import com.pos.device.SDKManager
import com.pos.device.SDKManagerCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.vigatec.utils.FilesUtils

object NewposKeyManager : IKeyManager {

    private const val TAG = "NewposKeyManager"
    private var pedControllerInstance: NewposPedController? = null // Tipo concreto para llamar a init interno
    @Volatile private var isSdkInitialized = false
    private var applicationContext: Context? = null // Nullable
    private lateinit var modules: Array<String>
    private lateinit var libname: Array<String>
    private lateinit var pbocManager: PBOCManager
    private lateinit var context : Application
    private lateinit var path: String

    override suspend fun connect() {}


    override suspend fun initialize(application: Application) {
        if (isSdkInitialized) {
            Log.i(TAG, "NewposKeyManager ya se encuentra inicializado.")
            return
        }
        this.context = application // Esto ya está, pero es para la variable 'context', no 'applicationContext'
        this.path = application.getFilesDir().getPath() + "/"

        // ... (copia de assets) ...

        // Guardar contexto primero
        applicationContext = application.applicationContext // <--- DESCOMENTAR ESTA LÍNEA
        Log.i(TAG, "Inicializando NewposKeyManager - Llamando a SDKManager.init...")

        try {
            // Usar suspendCancellableCoroutine para esperar el callback de SDKManager.init
            suspendCancellableCoroutine<Unit> { continuation -> // <--- DESCOMENTAR ESTE BLOQUE
                val callback = object : SDKManagerCallback {
                    override fun onFinish() {
                        Log.i(TAG, ">>> SDKManagerCallback.onFinish() recibido para NewposKeyManager.")
                        if (continuation.isActive) {
                            continuation.resume(Unit) // Indicar éxito a la coroutine
                        }
                    }
                    // TODO: Implementar onError si existe y llamar a continuation.resumeWithException
                    // Por ejemplo, si SDKManagerCallback tuviera un método onError:
                    /*
                    override fun onError(errorCode: Int, errorMessage: String?) {
                        Log.e(TAG, "SDKManagerCallback.onError() recibido: $errorCode - $errorMessage")
                        if (continuation.isActive) {
                            continuation.resumeWithException(RuntimeException("SDKManager.init falló: $errorMessage"))
                        }
                    }
                    */
                }

                continuation.invokeOnCancellation {
                    Log.w(TAG, "Inicialización cancelada.")
                    // Aquí podrías considerar si necesitas hacer alguna limpieza específica de Newpos si la cancelación ocurre.
                }

                try {
                    SDKManager.init(application.applicationContext, callback) // Usar el applicationContext
                    Log.d(TAG, "Llamada a SDKManager.init realizada, esperando onFinish...")
                } catch (e: Exception) {
                    Log.e(TAG, "Excepción síncrona al llamar a SDKManager.init", e)
                    if (continuation.isActive) {
                        continuation.resumeWithException(e) // Falla la coroutine
                    }
                }
            } // La coroutine se reanuda aquí después de onFinish (o falla)

            // Si llegamos aquí, SDKManager.init terminó exitosamente (onFinish fue llamado)
            Log.i(TAG, "SDKManager.init completado. Procediendo a inicializar NewposPedController...")

            // Ahora que SDKManager.init ha terminado, intentamos crear e inicializar el PedController
            // Ya tenemos 'applicationContext' asignado y sabemos que SDKManager.init ha finalizado.
            val ctx = applicationContext ?: throw PedException("Contexto no disponible después de init (inesperado después de coroutine).") // El ?: ahora es una salvaguarda extra.
            val controller = NewposPedController(ctx) // Esto llama a Ped.getInstance() en el init de NewposPedController
            val pedInitialized = controller.initializePedInternal() // Llama a la inicialización interna que obtiene Ped.getInstance
            if (!pedInitialized) {
                throw PedException("Falló la inicialización interna de NewposPedController (Ped.getInstance probablemente).")
            }

            pedControllerInstance = controller
            isSdkInitialized = true // Marcar como listo solo si TODO fue bien
            Log.i(TAG, ">>> NewposKeyManager inicializado con éxito (incluyendo PedController).")

        } catch (e: Exception) { // Atrapar excepciones de la coroutine o de la inicialización del PedController
            isSdkInitialized = false
            Log.e(TAG, "Error durante la inicialización de NewposKeyManager", e)
            throw PedException("Fallo en la inicialización de NewposKeyManager: ${e.message}", e) // Relanzar como PedException o una más genérica
        }
    }

    override fun getPedController(): IPedController? {
        if (!isSdkInitialized || pedControllerInstance == null) {
            Log.w(TAG, "getPedController llamado pero NewposKeyManager no está inicializado correctamente (isSdkInitialized=$isSdkInitialized, instance=${pedControllerInstance != null}).")
            return null
        }
        return pedControllerInstance
    }

    override fun release() {
        Log.d(TAG, "Liberando recursos de NewposKeyManager y SDKManager...")
        try {
            pedControllerInstance?.releasePed() // Liberar el controlador si existe
        } catch (e: Exception) {
            Log.e(TAG, "Error durante releasePed() en NewposPedController", e)
        } finally {
            pedControllerInstance = null
            applicationContext = null
            isSdkInitialized = false
            // Intentar liberar el SDKManager general también
            try {
                SDKManager.release()
                Log.i(TAG, "SDKManager.release() llamado.")
            } catch (e: Exception) {
                Log.e(TAG, "Error durante SDKManager.release()", e)
            }
            Log.d(TAG, "NewposKeyManager liberado.")
        }
    }
}