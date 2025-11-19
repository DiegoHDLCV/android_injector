package com.vigatec.manufacturer.base.controllers.manager

import android.content.Context
import com.vigatec.manufacturer.base.controllers.hardware.IBeeperController
import com.vigatec.manufacturer.base.controllers.hardware.IDeviceController
import com.vigatec.manufacturer.base.controllers.hardware.ILedController
import com.vigatec.manufacturer.base.controllers.hardware.IPrinterController
import com.vigatec.manufacturer.base.controllers.system.ISystemController

/**
 * Interfaz principal que proporciona acceso a todos los controladores de hardware y sistema
 * del dispositivo. Actúa como un facade para abstraer la complejidad de múltiples fabricantes.
 *
 * Implementaciones específicas para cada fabricante:
 * - NewposHardwareManager
 * - AisinoHardwareManager
 * - UrovoHardwareManager
 *
 * Ejemplo de uso:
 * ```kotlin
 * val hwManager = ManufacturerHardwareManager
 * val serial = hwManager.deviceController().getSerialNumber()
 * val success = hwManager.systemController().silentUninstall("com.example.app")
 * ```
 */
interface IHardwareController {

    /**
     * Inicializa el controlador de hardware.
     * Algunos fabricantes pueden necesitar Context para inicializar sus SDKs.
     *
     * @param application Contexto de la aplicación
     */
    fun initializeController(application: android.app.Application)

    /**
     * Retorna el controlador de dispositivo para obtener información del hardware.
     *
     * @return Implementación de IDeviceController específica del fabricante
     */
    fun deviceController(): IDeviceController

    /**
     * Retorna el controlador de sistema para operaciones del SO.
     *
     * @return Implementación de ISystemController específica del fabricante
     */
    fun systemController(): ISystemController

    /**
     * Retorna el controlador de impresora térmica.
     *
     * @return Implementación de IPrinterController específica del fabricante
     */
    fun printerController(): IPrinterController

    /**
     * Retorna el controlador de LED.
     *
     * @return Implementación de ILedController específica del fabricante
     */
    fun ledController(): ILedController

    /**
     * Retorna el controlador de beeper/buzzer.
     *
     * @return Implementación de IBeeperController específica del fabricante
     */
    fun beeperController(): IBeeperController
}
