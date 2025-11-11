package com.vigatec.manufacturer.base.controllers.tms

/**
 * Excepción personalizada para errores relacionados con el TMS.
 *
 * @param message Mensaje descriptivo del error.
 * @param cause Causa subyacente de la excepción (opcional).
 */
class TmsException(message: String, cause: Throwable? = null) : Exception(message, cause)
