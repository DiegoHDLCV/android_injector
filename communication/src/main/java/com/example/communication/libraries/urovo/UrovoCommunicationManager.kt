package com.example.communication.libraries.urovo

import android.app.Application
import android.util.Log
import com.example.communication.base.IComController
import com.example.communication.base.controllers.manager.ICommunicationManager
import com.example.communication.libraries.urovo.wrapper.UrovoComController

object UrovoCommunicationManager : ICommunicationManager {

    private const val TAG = "UrovoCommManager"
    private var comControllerInstance: UrovoComController? = null
    @Volatile private var isInitialized = false
    private var applicationContext: Application? = null

    // Puedes definir aquí un nombre de puerto serie preferido si lo conoces,
    // o dejarlo null para que UrovoComController intente la auto-detección.
    private val PREFERRED_PORT_PATHS: List<String> = listOf(
        "/dev/ttyS1", // Común
        "/dev/ttyS0",
        "/dev/ttyS3",
        "/dev/ttyMT1", // Común en dispositivos MediaTek
        "/dev/ttyMT2",
        "/dev/ttyHSL0", // Común en dispositivos Qualcomm
        "/dev/ttyGS0"  // Otro común
    )

    override suspend fun initialize(application: Application) {
        if (isInitialized) {
            Log.i(TAG, "UrovoCommunicationManager ya está inicializado.")
            return
        }
        Log.i(TAG, "Inicializando UrovoCommunicationManager...")
        this.applicationContext = application
        // Para Urovo (usando SerialPortTool), la inicialización principal
        // es la creación de la instancia del controlador y su 'init'.
        // No parece haber una inicialización global del SDK de Urovo requerida aquí.
        // La creación real se hará en getComController para ser 'lazy'.
        isInitialized = true
        Log.i(TAG, "UrovoCommunicationManager inicializado.")
    }

    @Synchronized // Asegura que solo se cree una instancia del controlador
    override fun getComController(): IComController? {
        if (!isInitialized) {
            Log.e(TAG, "UrovoCommunicationManager no ha sido inicializado. Llama a initialize() primero.")
            return null
        }

        if (comControllerInstance == null) {
            Log.d(TAG, "Creando nueva instancia de UrovoComController...")
            try {
                // Creamos la instancia. Pasamos el nombre del puerto preferido (o null).
                comControllerInstance = UrovoComController(PREFERRED_PORT_PATHS)
                Log.i(TAG, "Instancia de UrovoComController creada.")
                // NOTA: El método `init()` de UrovoComController debe ser llamado
                // por la lógica de negocio *antes* de intentar abrir o usar el puerto.
                // El manager solo se encarga de *proveer* el controlador.
            } catch (e: Exception) {
                Log.e(TAG, "Error al crear UrovoComController", e)
                comControllerInstance = null // Asegurar que sea nulo si falla
            }
        }
        return comControllerInstance
    }

    override fun release() {
        Log.i(TAG, "Liberando UrovoCommunicationManager...")
        try {
            // Intentamos cerrar el puerto si el controlador existe y está abierto
            comControllerInstance?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar el puerto durante la liberación.", e)
        } finally {
            // Liberamos las referencias
            comControllerInstance = null
            applicationContext = null
            isInitialized = false
            Log.i(TAG, "UrovoCommunicationManager liberado.")
        }
    }
}