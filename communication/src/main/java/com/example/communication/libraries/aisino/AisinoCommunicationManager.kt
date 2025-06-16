package com.example.communication.libraries.aisino

import android.app.Application
import android.util.Log
import com.example.communication.base.IComController
import com.example.communication.base.controllers.manager.ICommunicationManager
import com.example.communication.libraries.aisino.wrapper.AisinoComController

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

        // La inicialización del SDK global de Vanstone (SystemApi.SystemInit_Api)
        // se asume que ya fue manejada externamente (ej. en AisinoKeyManager o Application.onCreate)
        // ya que es un prerrequisito para que SdkApi.getInstance() funcione.
        // Si SdkApi.getInstance().getRs232Handler() es null, las llamadas fallarán.
        // Aquí solo marcamos este manager como inicializado.
        isInitialized = true
        Log.i(TAG, "AisinoCommunicationManager inicializado. Se asume que el SDK de Vanstone está listo.")
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
}