package com.example.manufacturer.base.controllers.manager

import android.app.Application
import com.example.manufacturer.base.controllers.tms.ITmsController

/**
 * Interfaz para el Terminal Management System (TMS).
 * Define las operaciones necesarias para conectar y obtener configuraciones
 * del sistema de gestión de terminales específico del fabricante.
 */
interface ITmsManager {
    /**
     * Inicializa el SDK de TMS específico del fabricante si es necesario.
     */
    suspend fun initialize(application: Application)

    /**
     * Obtiene el controlador TMS para operaciones específicas.
     * @return El controlador TMS o null si no está disponible.
     */
    fun getTmsController(): ITmsController?

    /**
     * Libera recursos del TMS.
     */
    fun release()
}
