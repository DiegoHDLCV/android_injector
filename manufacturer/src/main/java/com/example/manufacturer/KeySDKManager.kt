package com.example.manufacturer // Adjust package if needed

import android.app.Application
import android.util.Log
import com.example.config.EnumManufacturer
import com.example.config.SystemConfig // Adjust package if needed
import com.example.manufacturer.base.controllers.manager.IKeyManager
import com.example.manufacturer.base.controllers.ped.IPedController
import com.example.manufacturer.libraries.newpos.NewposKeyManager
import com.example.manufacturer.libraries.aisino.AisinoKeyManager
import com.example.manufacturer.libraries.urovo.UrovoKeyManager

object KeySDKManager : IKeyManager {

    private const val TAG = "KeySDKManager"

    // Determina el manager específico basado en la configuración
    private val manager: IKeyManager by lazy {
        Log.d(TAG, "Seleccionando KeyManager para el fabricante: ${SystemConfig.managerSelected}")
        when (SystemConfig.managerSelected) {
            EnumManufacturer.NEWPOS -> NewposKeyManager
            EnumManufacturer.AISINO -> AisinoKeyManager
            EnumManufacturer.UROVO -> UrovoKeyManager
            // ... otros fabricantes ...
            else -> throw IllegalStateException("Fabricante no soportado para KeySDKManager: ${SystemConfig.managerSelected}") // O un Dummy si prefieres
        }
    }

    // Delegar initialize al manager específico
    override suspend fun initialize(application: Application) {
        try {
            Log.d(TAG, "Delegando inicialización a ${SystemConfig.managerSelected} KeyManager...")
            manager.initialize(application)
            Log.i(TAG, "Inicialización delegada a ${SystemConfig.managerSelected} KeyManager completada.")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la inicialización delegada a ${SystemConfig.managerSelected} KeyManager", e)
            throw e // Relanzar para que SDKInitManager se entere
        }
    }

    // Delegar connect al manager específico
    override suspend fun connect() {
        Log.d(TAG, "Delegando connect a ${SystemConfig.managerSelected} KeyManager...")
        try {
            manager.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante connect delegado a ${SystemConfig.managerSelected} KeyManager", e)
            // Decide si relanzar o no
        }
    }

    // Delegar getPedController al manager específico
    override fun getPedController(): IPedController? {
        Log.d(TAG, "Delegando getPedController a ${SystemConfig.managerSelected} KeyManager...")
        return try {
            manager.getPedController()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante getPedController delegado a ${SystemConfig.managerSelected} KeyManager", e)
            null
        }
    }

    // Delegar release al manager específico
    override fun release() {
        Log.d(TAG, "Delegando release a ${SystemConfig.managerSelected} KeyManager...")
        try {
            manager.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante release delegado a ${SystemConfig.managerSelected} KeyManager", e)
        }
    }
}