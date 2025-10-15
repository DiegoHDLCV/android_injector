package com.example.manufacturer.base.controllers.tms

/**
 * Interfaz para operaciones del Terminal Management System (TMS).
 * Define métodos para leer parámetros de configuración desde el TMS.
 */
interface ITmsController {

    /**
     * Lee un parámetro de configuración del TMS.
     *
     * @param paramName El nombre del parámetro a leer (ej: "url_api", "timeout_ms").
     * @return El valor del parámetro como String, o null si no se encuentra.
     */
    fun getTmsParameter(paramName: String): String?

    /**
     * Lee un parámetro de configuración del TMS con un valor por defecto.
     *
     * @param paramName El nombre del parámetro a leer.
     * @param defaultValue Valor a retornar si el parámetro no existe.
     * @return El valor del parámetro o el valor por defecto.
     */
    fun getTmsParameter(paramName: String, defaultValue: String): String {
        return getTmsParameter(paramName) ?: defaultValue
    }

    /**
     * Lee múltiples parámetros de configuración del TMS.
     *
     * @param paramNames Lista de nombres de parámetros a leer.
     * @return Mapa con los parámetros encontrados (key = nombre, value = valor).
     */
    fun getTmsParameters(paramNames: List<String>): Map<String, String> {
        return paramNames.mapNotNull { paramName ->
            getTmsParameter(paramName)?.let { value ->
                paramName to value
            }
        }.toMap()
    }

    /**
     * Verifica si un parámetro existe en el TMS.
     *
     * @param paramName El nombre del parámetro a verificar.
     * @return true si el parámetro existe, false en caso contrario.
     */
    fun hasTmsParameter(paramName: String): Boolean {
        return getTmsParameter(paramName) != null
    }

    /**
     * Obtiene todos los parámetros configurados en el TMS.
     * Nota: Esta funcionalidad puede no estar disponible en todos los fabricantes.
     *
     * @return Mapa con todos los parámetros disponibles, o un mapa vacío.
     */
    fun getAllTmsParameters(): Map<String, String> {
        return emptyMap()
    }
}
