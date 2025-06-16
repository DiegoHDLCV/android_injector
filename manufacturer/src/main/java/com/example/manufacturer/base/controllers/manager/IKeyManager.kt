package com.example.manufacturer.base.controllers.manager

import android.app.Application
import com.example.manufacturer.base.controllers.ped.IPedController

/**
 * Interfaz principal para la gestión de llaves.
 * Proporciona acceso a los controladores de PED específicos del fabricante.
 */
interface IKeyManager {
    suspend fun connect()

    /**
     * Inicializa el gestor de llaves para el fabricante seleccionado.
     * @param application El contexto de la aplicación.
     */
    suspend fun initialize(application: Application)

    /**
     * Obtiene el controlador del PED.
     * @return Una instancia de [IPedController] o null si no está disponible o no inicializado.
     */
    fun getPedController(): IPedController?

    /**
     * Libera los recursos utilizados por el gestor de llaves.
     */
    fun release()
}