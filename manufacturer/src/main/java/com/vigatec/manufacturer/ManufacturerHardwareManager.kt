package com.vigatec.manufacturer

import android.app.Application
import android.util.Log
import com.vigatec.config.EnumManufacturer
import com.vigatec.config.SystemConfig
import com.vigatec.manufacturer.base.controllers.hardware.IBeeperController
import com.vigatec.manufacturer.base.controllers.hardware.IDeviceController
import com.vigatec.manufacturer.base.controllers.hardware.ILedController
import com.vigatec.manufacturer.base.controllers.hardware.IPrinterController
import com.vigatec.manufacturer.base.controllers.manager.IHardwareController
import com.vigatec.manufacturer.base.controllers.system.ISystemController
import com.vigatec.manufacturer.libraries.aisino.AisinoHardwareManager
import com.vigatec.manufacturer.libraries.aisino.controller.system.AisinoSystemController
import com.vigatec.manufacturer.libraries.aisino.workflow.hardware.AisinoDeviceController
import com.vigatec.manufacturer.libraries.newpos.NewposHardwareManager
import com.vigatec.manufacturer.libraries.newpos.controller.hardware.NewposDeviceController
import com.vigatec.manufacturer.libraries.newpos.controller.system.NewposSystemController
import com.vigatec.manufacturer.libraries.urovo.controller.system.UrovoSystemController

/**
 * Manager centralizado para acceso a todos los controladores de hardware y sistema.
 * Implementa IHardwareController y delega a las implementaciones específicas de cada fabricante.
 *
 * Proporciona acceso tanto a:
 * - IDeviceController: Información del dispositivo
 * - ISystemController: Operaciones del sistema
 *
 * Ejemplo de uso:
 * ```kotlin
 * val serial = ManufacturerHardwareManager.deviceController().getSerialNumber()
 * val success = ManufacturerHardwareManager.systemController().silentUninstall("com.example.app")
 * ```
 */
object ManufacturerHardwareManager : IHardwareController {
    private val TAG = "ManufacturerHardwareManager"

    private val deviceControllerInstance: IDeviceController by lazy {
        Log.d(TAG, "Seleccionando DeviceController para fabricante: ${SystemConfig.managerSelected}")
        when (SystemConfig.managerSelected) {
            EnumManufacturer.NEWPOS -> NewposDeviceController()
            EnumManufacturer.AISINO -> AisinoDeviceController()
            // TODO: Implementar UrovoDeviceController
            EnumManufacturer.UROVO -> {
                Log.w(TAG, "UrovoDeviceController no implementado aún, usando AisinoDeviceController como fallback")
                AisinoDeviceController()  // Fallback
            }
            else -> {
                Log.w(TAG, "Fabricante desconocido: ${SystemConfig.managerSelected}, usando AisinoDeviceController como fallback")
                AisinoDeviceController()  // Fallback por defecto
            }
        }
    }

    private val systemControllerInstance: ISystemController by lazy {
        Log.d(TAG, "Seleccionando SystemController para fabricante: ${SystemConfig.managerSelected}")
        when (SystemConfig.managerSelected) {
            EnumManufacturer.NEWPOS -> NewposSystemController()
            EnumManufacturer.AISINO -> AisinoSystemController()
            EnumManufacturer.UROVO -> {
                Log.d(TAG, "Inicializando UrovoSystemController")
                UrovoSystemController(null) // Context será seteado en initialize()
            }
            else -> {
                Log.w(TAG, "Fabricante desconocido: ${SystemConfig.managerSelected}, usando AisinoSystemController como fallback")
                AisinoSystemController()  // Fallback por defecto
            }
        }
    }

    private val printerControllerInstance: IPrinterController by lazy {
        Log.d(TAG, "Seleccionando PrinterController para fabricante: ${SystemConfig.managerSelected}")
        when (SystemConfig.managerSelected) {
            EnumManufacturer.NEWPOS -> NewposHardwareManager.printerController()
            EnumManufacturer.AISINO -> AisinoHardwareManager.printerController()
            EnumManufacturer.UROVO -> {
                Log.w(TAG, "UrovoPrinterController no implementado aún, usando AisinoHardwareManager como fallback")
                AisinoHardwareManager.printerController()  // Fallback
            }
            else -> {
                Log.w(TAG, "Fabricante desconocido: ${SystemConfig.managerSelected}, usando AisinoHardwareManager como fallback")
                AisinoHardwareManager.printerController()  // Fallback por defecto
            }
        }
    }

    private val ledControllerInstance: ILedController by lazy {
        Log.d(TAG, "Seleccionando LedController para fabricante: ${SystemConfig.managerSelected}")
        when (SystemConfig.managerSelected) {
            EnumManufacturer.NEWPOS -> NewposHardwareManager.ledController()
            EnumManufacturer.AISINO -> AisinoHardwareManager.ledController()
            EnumManufacturer.UROVO -> {
                Log.w(TAG, "UrovoLedController no implementado aún, usando AisinoHardwareManager como fallback")
                AisinoHardwareManager.ledController()  // Fallback
            }
            else -> {
                Log.w(TAG, "Fabricante desconocido: ${SystemConfig.managerSelected}, usando AisinoHardwareManager como fallback")
                AisinoHardwareManager.ledController()  // Fallback por defecto
            }
        }
    }

    private val beeperControllerInstance: IBeeperController by lazy {
        Log.d(TAG, "Seleccionando BeeperController para fabricante: ${SystemConfig.managerSelected}")
        when (SystemConfig.managerSelected) {
            EnumManufacturer.NEWPOS -> NewposHardwareManager.beeperController()
            EnumManufacturer.AISINO -> AisinoHardwareManager.beeperController()
            EnumManufacturer.UROVO -> {
                Log.w(TAG, "UrovoBeeperController no implementado aún, usando AisinoHardwareManager como fallback")
                AisinoHardwareManager.beeperController()  // Fallback
            }
            else -> {
                Log.w(TAG, "Fabricante desconocido: ${SystemConfig.managerSelected}, usando AisinoHardwareManager como fallback")
                AisinoHardwareManager.beeperController()  // Fallback por defecto
            }
        }
    }

    override fun initializeController(application: Application) {
        Log.i(TAG, "Inicializando ManufacturerHardwareManager para: ${SystemConfig.managerSelected}")

        // Inicializar SystemController si es UROVO
        if (SystemConfig.managerSelected == EnumManufacturer.UROVO) {
            if (systemControllerInstance is UrovoSystemController) {
                systemControllerInstance.initialize(application)
            }
        } else if (SystemConfig.managerSelected == EnumManufacturer.AISINO) {
             AisinoHardwareManager.initializeController(application)
        }

        Log.i(TAG, "✓ ManufacturerHardwareManager inicializado")
    }

    override fun deviceController(): IDeviceController {
        return deviceControllerInstance
    }

    override fun systemController(): ISystemController {
        return systemControllerInstance
    }

    override fun printerController(): IPrinterController {
        return printerControllerInstance
    }

    override fun ledController(): ILedController {
        return ledControllerInstance
    }

    override fun beeperController(): IBeeperController {
        return beeperControllerInstance
    }

    /**
     * Métodos de conveniencia para acceso directo a IDeviceController
     * (compatibilidad hacia atrás)
     */
    fun getSerialNumber(): String = deviceControllerInstance.getSerialNumber()

    fun getModelName(): String = deviceControllerInstance.getModelName()

    fun getFirmwareVersion(): String = deviceControllerInstance.getFirmwareVersion()

    fun getEmvKernelVersions(): String = deviceControllerInstance.getEmvKernelVersions()

    fun getHardwareVersion(): String = deviceControllerInstance.getHardwareVersion()

    fun getBatteryPercentage(): Int = deviceControllerInstance.getBatteryPercentage()

    fun getBatteryStatus(): Int = deviceControllerInstance.getBatteryStatus()

    fun getBatteryWearPercentage(): Int = deviceControllerInstance.getBatteryWearPercentage()

    fun getNFCReaderStatus(): Int = deviceControllerInstance.getNFCReaderStatus()

    fun getNFCReaderWearPercentage(): Int = deviceControllerInstance.getNFCReaderWearPercentage()

    fun getChipReaderStatus(): Int = deviceControllerInstance.getChipReaderStatus()

    fun getChipReaderWearPercentage(): Int = deviceControllerInstance.getChipReaderWearPercentage()

    fun getMagstripeStatus(): Int = deviceControllerInstance.getMagstripeStatus()

    fun getMagstripeWearPercentage(): Int = deviceControllerInstance.getMagstripeWearPercentage()
}
