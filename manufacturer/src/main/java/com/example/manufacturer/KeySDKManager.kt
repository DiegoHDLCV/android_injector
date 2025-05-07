package com.example.manufacturer // Adjust package if needed

import android.app.Application
import android.util.Log
import com.example.config.EnumManufacturer
import com.example.config.SystemConfig // Adjust package if needed
import com.example.manufacturer.base.controllers.manager.IKeyManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.libraries.newpos.NewposKeyManager
import com.example.manufacturer.libraries.aisino.AisinoKeyManager // <-- Importar AisinoKeyManager

object KeySDKManager : IKeyManager {

    private const val TAG = "KeySDKManager"

    // Determina el manager específico basado en la configuración
    private val manager: IKeyManager by lazy {
        Log.d(TAG, "Seleccionando KeyManager para el fabricante: ${SystemConfig.managerSelected}")
        when (SystemConfig.managerSelected) {
            EnumManufacturer.NEWPOS -> NewposKeyManager
            EnumManufacturer.AISINO -> AisinoKeyManager // <-- Añadir caso para Aisino
            // EnumManufacturer.INGENICO -> IngenicoKeyManager() // Descomentar e implementar
            // EnumManufacturer.PAX -> PaxKeyManager()       // Descomentar e implementar
            // ... otros fabricantes
            else -> {
                Log.w(TAG, "Fabricante ${SystemConfig.managerSelected} no tiene implementación de gestión de llaves dedicada. Usando DummyKeyManager.")
                DummyKeyManager
            }
        }
    }

    override suspend fun initialize(application: Application) {
        try {
            Log.d(TAG, "Inicializando SDK de gestión de llaves para: ${SystemConfig.managerSelected}")
            manager.initialize(application)
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la inicialización del SDK de gestión de llaves (${SystemConfig.managerSelected})", e)
            // Considera relanzar la excepción o manejar el error de forma más específica
            // dependiendo de los requisitos de la aplicación.
            // Podrías querer notificar al usuario que las funciones de PED no estarán disponibles.
        }
    }

    override fun getPedController(): IPedController? {
        Log.d(TAG, "Delegando getPedController a ${SystemConfig.managerSelected}")
        return try {
            manager.getPedController() // Puede devolver null si el manager no está inicializado o no tiene controlador
        } catch (e: Exception) {
            Log.e(TAG, "Error en getPedController para ${SystemConfig.managerSelected}", e)
            null
        }
    }

    override fun release() {
        Log.d(TAG, "Delegando release a ${SystemConfig.managerSelected}")
        try {
            manager.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la liberación de recursos (release) para ${SystemConfig.managerSelected}", e)
        }
    }

    /**
     * Implementación Dummy para casos donde no hay un gestor específico o para pruebas.
     */
    private object DummyKeyManager : IKeyManager {
        private const val DUMMY_TAG = "DummyKeyManager"
        override suspend fun initialize(application: Application) {
            Log.w(DUMMY_TAG, "Initialize llamado en DummyKeyManager, no realiza ninguna acción.")
        }

        override fun getPedController(): IPedController? {
            Log.w(DUMMY_TAG, "getPedController llamado en DummyKeyManager, devuelve null.")
            // Podría devolver una implementación dummy de IPedController que loguea las llamadas
            // o lanza NotImplementedError si se prefiere un comportamiento más estricto.
            return null
        }

        override fun release() {
            Log.w(DUMMY_TAG, "Release llamado en DummyKeyManager, no realiza ninguna acción.")
        }
    }
}