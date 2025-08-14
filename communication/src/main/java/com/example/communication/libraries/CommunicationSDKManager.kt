package com.example.communication.libraries

import android.app.Application
import android.util.Log
import com.example.communication.base.IComController
import com.example.communication.base.controllers.manager.ICommunicationManager
import com.example.communication.libraries.aisino.AisinoCommunicationManager
import com.example.communication.libraries.newpos.NewposCommunicationManager
import com.example.communication.libraries.urovo.UrovoCommunicationManager
import com.example.config.EnumManufacturer
import com.example.config.SystemConfig

object CommunicationSDKManager : ICommunicationManager {

    // Determina el manager específico basado en la configuración
    private val manager: ICommunicationManager by lazy {
        Log.d("CommSDKManager", "Seleccionando manager para: ${SystemConfig.managerSelected}")
        when (SystemConfig.managerSelected) {
            EnumManufacturer.AISINO -> AisinoCommunicationManager // Implementar
            EnumManufacturer.UROVO -> UrovoCommunicationManager // Implementar
            EnumManufacturer.NEWPOS -> NewposCommunicationManager // Implementar
            // ... otros fabricantes
            else -> {
                Log.w(
                    "CommSDKManager",
                    "Fabricante ${SystemConfig.managerSelected} no tiene implementación de comunicación dedicada. Usando Dummy."
                )
                DummyCommunicationManager // Devuelve la implementación Dummy
            }
        }
    }

    override suspend fun initialize(application: Application) {
        try {
            Log.d(
                "CommSDKManager",
                "Inicializando SDK de comunicación para: ${SystemConfig.managerSelected}"
            )
            manager.initialize(application)
        } catch (e: Exception) {
            Log.e("CommSDKManager", "Error durante la inicialización del SDK de comunicación", e)
        }
    }

    override fun getComController(): IComController? {
        // --- CORREGIDO --- Se cambió "getApnController" a "getComController" en el log
        Log.d("CommSDKManager", "Delegando getComController a ${SystemConfig.managerSelected}")
        return try {
            manager.getComController()
        } catch (e: Exception) {
            // --- CORREGIDO --- Se cambió "getApnController" a "getComController" en el log
            Log.e("CommSDKManager", "Error en getComController", e); null
        }
    }

    fun rescanIfSupported() {
        when (manager) {
            is AisinoCommunicationManager -> (manager as AisinoCommunicationManager).safeRescanIfInitialized()
            // Otros managers podrían implementar lógica futura
            else -> Log.d("CommSDKManager", "rescanIfSupported: no soportado para ${SystemConfig.managerSelected}")
        }
    }

    override fun release() {
        Log.d("CommSDKManager", "Delegando release a ${SystemConfig.managerSelected}")
        try {
            manager.release()
        } catch (e: Exception) {
            Log.e("CommSDKManager", "Error durante release", e)
        }
    }

    // Objeto Dummy interno para manejar casos no soportados
    private object DummyCommunicationManager : ICommunicationManager {
        override suspend fun initialize(application: Application) { Log.w("DummyCommManager", "Initialize llamado, pero no hace nada.") }

        // --- CORREGIDO --- Se implementó devolviendo null en lugar de TODO
        override fun getComController(): IComController? {
            Log.w("DummyCommManager", "getComController llamado, devolviendo null.")
            return null // Devuelve null ya que es una implementación dummy
        }
        // --- FIN CORRECCIÓN ---

        override fun release() { Log.w("DummyCommManager", "Release llamado, pero no hace nada.") }
    }
}