package com.example.manufacturer.libraries.aisino

import android.app.Application
import android.util.Log
import com.example.manufacturer.base.controllers.manager.ITmsManager
import com.example.manufacturer.base.controllers.tms.ITmsController
import com.example.manufacturer.base.controllers.tms.TmsException
import com.example.manufacturer.libraries.aisino.wrapper.AisinoTmsController
import com.example.manufacturer.libraries.aisino.wrapper.AisinoTmsParameterHelper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manager específico para TMS en dispositivos Aisino/Vanstone.
 * Gestiona la inicialización y acceso al controlador TMS.
 */
object AisinoTmsManager : ITmsManager {

    private const val TAG = "AisinoTmsManager"
    private var tmsControllerInstance: ITmsController? = null
    private var isInitialized = false
    private val initializationMutex = Mutex()

    override suspend fun initialize(application: Application) {
        initializationMutex.withLock {
            if (isInitialized) {
                Log.d(TAG, "AisinoTmsManager ya se encuentra inicializado.")
                return
            }

            Log.d(TAG, "Inicializando AisinoTmsManager...")
            try {
                // Verificar si el archivo param.env existe
                val paramFileExists = AisinoTmsParameterHelper.paramEnvFileExists(application)
                Log.d(TAG, "Estado del archivo param.env: ${if (paramFileExists) "existe" else "no existe"}")

                if (!paramFileExists) {
                    Log.w(TAG, "El archivo param.env no existe. Creando archivo vacío...")
                    // Crear archivo vacío para que el SDK pueda trabajar con él
                    val created = AisinoTmsParameterHelper.createEmptyParamEnvFile(application)
                    if (created) {
                        Log.i(TAG, "Archivo param.env vacío creado en: ${AisinoTmsParameterHelper.getParamEnvPath(application)}")
                        Log.i(TAG, "El archivo está listo para recibir parámetros desde:")
                        Log.i(TAG, "  1. El servidor TMS (sincronización), O")
                        Log.i(TAG, "  2. La pantalla de configuración TMS (botón 'Crear Parámetros de Prueba')")
                    } else {
                        Log.e(TAG, "No se pudo crear el archivo param.env vacío")
                    }
                } else {
                    Log.i(TAG, "Archivo param.env encontrado en: ${AisinoTmsParameterHelper.getParamEnvPath(application)}")
                    // Mostrar contenido para debug
                    val content = AisinoTmsParameterHelper.readParamEnvFile(application)
                    if (content.isNullOrBlank()) {
                        Log.d(TAG, "El archivo param.env existe pero está vacío")
                    } else {
                        Log.d(TAG, "Contenido del archivo param.env:\n$content")
                    }
                }

                // Crear la instancia del controlador específico de Aisino
                val controller = AisinoTmsController()

                tmsControllerInstance = controller
                isInitialized = true
                Log.i(TAG, "AisinoTmsManager inicializado con éxito.")

            } catch (e: TmsException) {
                Log.e(TAG, "Fallo al inicializar AisinoTmsManager: ${e.message}", e)
                tmsControllerInstance = null
                isInitialized = false
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error inesperado durante la inicialización de AisinoTmsManager", e)
                tmsControllerInstance = null
                isInitialized = false
                throw TmsException("Error inesperado al inicializar AisinoTmsManager: ${e.message}", e)
            }
        }
    }

    override fun getTmsController(): ITmsController? {
        if (!isInitialized || tmsControllerInstance == null) {
            Log.w(TAG, "AisinoTmsManager no está inicializado. Llama a initialize() primero.")
            return null
        }
        return tmsControllerInstance
    }

    override fun release() {
        Log.d(TAG, "Liberando recursos de AisinoTmsManager...")
        try {
            // El SDK de Vanstone TMS no requiere liberación explícita de recursos
            tmsControllerInstance = null
            isInitialized = false
            Log.d(TAG, "AisinoTmsManager liberado.")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la liberación de AisinoTmsManager", e)
        }
    }
}
