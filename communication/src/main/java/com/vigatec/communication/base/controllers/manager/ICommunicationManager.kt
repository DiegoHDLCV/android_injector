package com.vigatec.communication.base.controllers.manager

import android.app.Application
import com.vigatec.communication.base.IComController

interface ICommunicationManager {
    /**
     * Inicializa el SDK de comunicación específico del fabricante si es necesario.
     */
    suspend fun initialize(application: Application)


    fun getComController(): IComController?

    // Podrías añadir un método release si necesita liberar recursos específicos
    fun release() {} // Default implementation
}