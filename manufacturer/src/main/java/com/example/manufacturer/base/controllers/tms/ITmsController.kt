package com.example.manufacturer.base.controllers.tms

/**
 * Interfaz para operaciones del Terminal Management System (TMS).
 * Define métodos para leer parámetros de configuración desde el TMS.
 */
interface ITmsController {

    /**
     * Descarga parámetros desde el servidor TMS.
     * Esta funcionalidad usa AIDL para conectarse al servicio TMS y descargar parámetros.
     *
     * @param packageName Nombre del paquete de la aplicación para descargar parámetros.
     * @param onSuccess Callback invocado cuando la descarga es exitosa, con el JSON de parámetros.
     * @param onError Callback invocado cuando hay un error, con el mensaje de error.
     */
    fun downloadParametersFromTms(
        packageName: String,
        onSuccess: (parametersJson: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        // Implementación por defecto: no soportado
        onError("Descarga desde TMS no soportada en este fabricante")
    }

    /**
     * Verifica si el servicio TMS está disponible en el dispositivo.
     * 
     * @return true si TMS está disponible, false en caso contrario.
     */
    fun isTmsServiceAvailable(): Boolean {
        return false // Por defecto no está disponible
    }
}
