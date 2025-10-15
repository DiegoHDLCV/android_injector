package com.example.manufacturer

import android.app.Application
import android.util.Log
import com.example.config.EnumManufacturer
import com.example.config.SystemConfig
import com.example.manufacturer.base.controllers.manager.ITmsManager
import com.example.manufacturer.base.controllers.tms.ITmsController
import com.example.manufacturer.libraries.aisino.AisinoTmsManager

/**
 * Manager principal de TMS que delega a la implementación específica del fabricante.
 * Sigue el patrón de KeySDKManager para mantener coherencia arquitectónica.
 */
object TmsSDKManager : ITmsManager {

    private const val TAG = "TmsSDKManager"

    // Determina el manager específico basado en la configuración
    private val manager: ITmsManager by lazy {
        Log.d(TAG, "Seleccionando TmsManager para el fabricante: ${SystemConfig.managerSelected}")
        when (SystemConfig.managerSelected) {
            EnumManufacturer.AISINO -> AisinoTmsManager
            EnumManufacturer.NEWPOS -> {
                Log.w(TAG, "TMS no implementado para NEWPOS, usando implementación dummy")
                DummyTmsManager
            }
            EnumManufacturer.UROVO -> {
                Log.w(TAG, "TMS no implementado para UROVO, usando implementación dummy")
                DummyTmsManager
            }
            else -> {
                Log.w(TAG, "Fabricante no soportado para TMS: ${SystemConfig.managerSelected}, usando implementación dummy")
                DummyTmsManager
            }
        }
    }

    override suspend fun initialize(application: Application) {
        try {
            Log.d(TAG, "Delegando inicialización a ${SystemConfig.managerSelected} TmsManager...")
            manager.initialize(application)
            Log.i(TAG, "Inicialización delegada a ${SystemConfig.managerSelected} TmsManager completada.")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la inicialización delegada a ${SystemConfig.managerSelected} TmsManager", e)
            throw e
        }
    }

    override fun getTmsController(): ITmsController? {
        Log.d(TAG, "Delegando getTmsController a ${SystemConfig.managerSelected} TmsManager...")
        return try {
            manager.getTmsController()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante getTmsController delegado a ${SystemConfig.managerSelected} TmsManager", e)
            null
        }
    }

    override fun release() {
        Log.d(TAG, "Delegando release a ${SystemConfig.managerSelected} TmsManager...")
        try {
            manager.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante release delegado a ${SystemConfig.managerSelected} TmsManager", e)
        }
    }

    /**
     * Manager dummy para fabricantes que no tienen implementación de TMS.
     */
    private object DummyTmsManager : ITmsManager {
        private const val TAG = "DummyTmsManager"

        override suspend fun initialize(application: Application) {
            Log.d(TAG, "DummyTmsManager inicializado (no hace nada)")
        }

        override fun getTmsController(): ITmsController? {
            Log.d(TAG, "DummyTmsManager no proporciona controlador TMS")
            return null
        }

        override fun release() {
            Log.d(TAG, "DummyTmsManager liberado (no hace nada)")
        }
    }
}
