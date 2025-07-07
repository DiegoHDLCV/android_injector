package com.example.communication.libraries.aisino

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.communication.base.IComController
import com.example.communication.base.controllers.manager.ICommunicationManager
import com.example.communication.libraries.aisino.wrapper.AisinoComController
import com.vanstone.appsdk.client.ISdkStatue
import com.vanstone.appsdk.client.SdkApi
import com.vanstone.trans.api.SystemApi
import com.vanstone.utils.CommonConvert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AisinoCommunicationManager : ICommunicationManager {

    private const val TAG = "AisinoCommManager"
    private var comControllerInstance: AisinoComController? = null
    @Volatile private var isInitialized = false // Para la inicialización de este manager
    private var applicationContext: Application? = null

    // Define el puerto serial a usar. Puede ser 0, 1 u otro según el dispositivo Aisino.
    // Este valor podría necesitar ser configurable o detectado. Por ahora, usamos 0 como default.
    // El manual de Vanstone, en SystemApi.DownLoadSn_Api, menciona "port - [in]Serial Port" y luego
    // en la descripción de ese método indica que "port != 0 && port != 1" es un error, sugiriendo que 0 y 1 son puertos válidos. [cite: 551]
    private const val DEFAULT_AISINO_COMPORT = 0

    override suspend fun initialize(application: Application) {
        if (isInitialized) {
            Log.i(TAG, "AisinoCommunicationManager ya está inicializado.")
            return
        }
        Log.i(TAG, "Inicializando AisinoCommunicationManager...")
        this.applicationContext = application

        // Inicializar el SDK de Vanstone si no está ya inicializado
        try {
            Log.d(TAG, "Inicializando SDK de Vanstone para comunicación...")
            initializeVanstoneSDK(application)
            Log.i(TAG, "SDK de Vanstone inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar SDK de Vanstone", e)
            throw e
        }

        isInitialized = true
        Log.i(TAG, "AisinoCommunicationManager inicializado correctamente.")
    }

    @Synchronized
    override fun getComController(): IComController? {
        if (!isInitialized) {
            Log.e(TAG, "AisinoCommunicationManager no ha sido inicializado. Llama a initialize() primero.")
            // Considerar lanzar una excepción si se requiere inicialización estricta
            // throw IllegalStateException("AisinoCommunicationManager no ha sido inicializado.")
            return null
        }

        if (comControllerInstance == null) {
            Log.d(TAG, "Creando nueva instancia de AisinoComController para el puerto $DEFAULT_AISINO_COMPORT...")
            try {
                // Aquí puedes pasar el número de puerto específico si es diferente
                comControllerInstance = AisinoComController(comport = DEFAULT_AISINO_COMPORT)
                Log.i(TAG, "Instancia de AisinoComController creada.")
            } catch (e: Exception) {
                Log.e(TAG, "Error al crear AisinoComController", e)
                comControllerInstance = null
            }
        }
        return comControllerInstance
    }

    override fun release() {
        Log.i(TAG, "Liberando AisinoCommunicationManager...")
        try {
            comControllerInstance?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar el puerto durante la liberación.", e)
        } finally {
            comControllerInstance = null
            applicationContext = null
            isInitialized = false
            Log.i(TAG, "AisinoCommunicationManager liberado.")
        }
    }
    
    // Variable para evitar inicializar el SDK múltiples veces
    @Volatile
    private var isVanstoneSDKInitialized = false
    
    private suspend fun initializeVanstoneSDK(application: Application) = withContext(Dispatchers.IO) {
        if (isVanstoneSDKInitialized) {
            Log.d(TAG, "SDK de Vanstone ya está inicializado, omitiendo.")
            return@withContext
        }
        
        val context: Context = application.applicationContext
        
        suspendCancellableCoroutine { continuation ->
            try {
                val curAppDir = context.filesDir.absolutePath
                val pathBytes = CommonConvert.StringToBytes("$curAppDir/\u0000")
                if (pathBytes == null) {
                    val ex = IllegalStateException("Error convirtiendo directorio a bytes para SystemInit_Api.")
                    Log.e(TAG, "Error: pathBytes es null", ex)
                    if (continuation.isActive) continuation.resumeWithException(ex)
                    return@suspendCancellableCoroutine
                }

                Log.d(TAG, "Llamando SystemApi.SystemInit_Api...")
                SystemApi.SystemInit_Api(0, pathBytes, context, object : ISdkStatue {
                    override fun sdkInitSuccessed() {
                        Log.i(TAG, "SystemApi.SystemInit_Api: Éxito. Inicializando SdkApi...")
                        if (!continuation.isActive) return

                        // Paso 2: Inicializar SdkApi
                        SdkApi.getInstance().init(context, object : ISdkStatue {
                            override fun sdkInitSuccessed() {
                                Log.i(TAG, "SdkApi.getInstance().init(): Éxito. Inicialización del SDK completa.")
                                isVanstoneSDKInitialized = true
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }

                            override fun sdkInitFailed() {
                                val ex = IllegalStateException("Falló la inicialización del SDK (SdkApi).")
                                Log.e(TAG, "Error: Falló la inicialización de SdkApi.", ex)
                                if (continuation.isActive) {
                                    continuation.resumeWithException(ex)
                                }
                            }
                        })
                    }

                    override fun sdkInitFailed() {
                        val ex = IllegalStateException("Falló la inicialización del SDK (SystemApi).")
                        Log.e(TAG, "Error: Falló la inicialización de SystemApi.", ex)
                        if (continuation.isActive) {
                            continuation.resumeWithException(ex)
                        }
                    }
                })
            } catch (e: Throwable) {
                Log.e(TAG, "Excepción durante la configuración de initializeVanstoneSDK", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    // Objeto Dummy interno para manejar casos no soportados (ya existe en el archivo original)
    private object DummyCommunicationManager : ICommunicationManager {
        override suspend fun initialize(application: Application) { 
            Log.w("DummyCommManager", "Initialize llamado, pero no hace nada.") 
        }

        override fun getComController(): IComController? {
            Log.w("DummyCommManager", "getComController llamado, devolviendo null.")
            return null
        }

        override fun release() { 
            Log.w("DummyCommManager", "Release llamado, pero no hace nada.") 
        }
    }
}