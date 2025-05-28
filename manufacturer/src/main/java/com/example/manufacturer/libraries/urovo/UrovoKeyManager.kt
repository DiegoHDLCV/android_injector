package com.example.manufacturer.libraries.urovo

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.manufacturer.base.controllers.manager.IKeyManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.base.controllers.ped.PedException
import com.example.manufacturer.libraries.urovo.wrapper.UrovoPedController
import com.urovo.sdk.pinpad.PinPadProviderImpl // Importamos la clase principal de la API

object UrovoKeyManager : IKeyManager {

    private const val TAG = "UrovoKeyManager"
    private var pedControllerInstance: UrovoPedController? = null
    @Volatile private var isSdkInitialized = false
    private var applicationContext: Context? = null

    override suspend fun connect() {
        Log.d(TAG, "Connect - Urovo no requiere una conexión explícita.")
        // La conexión/instanciación se maneja al obtener el controlador.
    }

    override suspend fun initialize(application: Application) {
        if (isSdkInitialized) {
            Log.i(TAG, "UrovoKeyManager ya se encuentra inicializado.")
            return
        }
        Log.i(TAG, "Inicializando UrovoKeyManager...")
        try {
            this.applicationContext = application.applicationContext
            // Forzamos la obtención (y por tanto, inicialización) del PinPadProviderImpl.
            // Esto verificará si el SDK está disponible.
            PinPadProviderImpl.getInstance() // Verifica si se puede obtener la instancia
            Log.i(TAG, "Instancia de PinPadProviderImpl obtenida (SDK parece presente).")
            // La inicialización real del PedController se hará en getPedController()
            isSdkInitialized = true
            Log.i(TAG, "UrovoKeyManager inicializado con éxito.")
        } catch (e: Throwable) { // Capturamos Throwable por si hay LinkageError, etc.
            isSdkInitialized = false
            Log.e(TAG, "Error durante la inicialización de UrovoKeyManager. ¿Está el .aar de Urovo en el proyecto?", e)
            throw PedException("Fallo en la inicialización de UrovoKeyManager: ${e.message}", e)
        }
    }

    // Método interno para asegurar la creación e inicialización
    @Synchronized
    private fun getPedControllerInternal(): UrovoPedController {
        if (pedControllerInstance == null) {
            val ctx = applicationContext ?: throw IllegalStateException("UrovoKeyManager no inicializado (contexto nulo).")
            Log.d(TAG, "Creando instancia de UrovoPedController...")
            pedControllerInstance = UrovoPedController(ctx)
        }
        return pedControllerInstance!!
    }

    override fun getPedController(): IPedController? {
        if (!isSdkInitialized) {
            Log.w(TAG, "getPedController llamado pero UrovoKeyManager no está inicializado.")
            return null
        }
        try {
            return getPedControllerInternal()
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener UrovoPedController", e)
            return null
        }
    }

    override fun release() {
        Log.d(TAG, "Liberando recursos de UrovoKeyManager...")
        try {
            pedControllerInstance?.releasePed()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante releasePed() en UrovoPedController", e)
        } finally {
            pedControllerInstance = null
            applicationContext = null
            isSdkInitialized = false
            // Urovo no tiene un 'release' global visible,
            // pero `Ped.java` tiene `close()`, aunque no es accesible desde aquí.
            Log.d(TAG, "UrovoKeyManager liberado.")
        }
    }
}