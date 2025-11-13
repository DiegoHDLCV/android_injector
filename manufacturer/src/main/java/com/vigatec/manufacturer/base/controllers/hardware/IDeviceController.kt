package com.vigatec.manufacturer.base.controllers.hardware

/**
 * Interfaz para obtener información del dispositivo de forma agnóstica al fabricante.
 * Proporciona métodos para acceder a datos del dispositivo físico.
 */
interface IDeviceController {
    /**
     * Obtiene el número de serie del dispositivo.
     * @return String con el número de serie, o cadena vacía si no está disponible
     */
    fun getSerialNumber(): String

    /**
     * Obtiene el nombre/modelo del dispositivo.
     * @return String con el modelo, o cadena vacía si no está disponible
     */
    fun getModelName(): String

    /**
     * Obtiene la versión de firmware del dispositivo.
     * @return String con la versión de firmware, o cadena vacía si no está disponible
     */
    fun getFirmwareVersion(): String

    /**
     * Obtiene las versiones del kernel EMV (si aplica).
     * @return String con las versiones EMV, o cadena vacía si no está disponible
     */
    fun getEmvKernelVersions(): String

    /**
     * Obtiene la versión de hardware del dispositivo.
     * @return String con la versión de hardware, o cadena vacía si no está disponible
     */
    fun getHardwareVersion(): String

    /**
     * Obtiene el porcentaje de batería del dispositivo.
     * @return Porcentaje entre 0-100, o -1 si no está disponible
     */
    fun getBatteryPercentage(): Int

    /**
     * Obtiene el estado de la batería.
     * @return Código de estado, 0 si desconocido
     */
    fun getBatteryStatus(): Int

    /**
     * Obtiene el porcentaje de desgaste de la batería.
     * @return Porcentaje entre 0-100, o -1 si no está disponible
     */
    fun getBatteryWearPercentage(): Int

    /**
     * Obtiene el estado del lector NFC.
     * @return Código de estado, 0 si desconocido
     */
    fun getNFCReaderStatus(): Int

    /**
     * Obtiene el porcentaje de desgaste del lector NFC.
     * @return Porcentaje entre 0-100, o -1 si no está disponible
     */
    fun getNFCReaderWearPercentage(): Int

    /**
     * Obtiene el estado del lector de chips.
     * @return Código de estado, 0 si desconocido
     */
    fun getChipReaderStatus(): Int

    /**
     * Obtiene el porcentaje de desgaste del lector de chips.
     * @return Porcentaje entre 0-100, o -1 si no está disponible
     */
    fun getChipReaderWearPercentage(): Int

    /**
     * Obtiene el estado del lector de banda magnética.
     * @return Código de estado, 0 si desconocido
     */
    fun getMagstripeStatus(): Int

    /**
     * Obtiene el porcentaje de desgaste del lector de banda magnética.
     * @return Porcentaje entre 0-100, o -1 si no está disponible
     */
    fun getMagstripeWearPercentage(): Int
}
