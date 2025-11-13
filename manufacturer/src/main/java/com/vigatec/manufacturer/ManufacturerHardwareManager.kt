package com.vigatec.manufacturer

import android.util.Log
import com.vigatec.config.EnumManufacturer
import com.vigatec.config.SystemConfig
import com.vigatec.manufacturer.base.controllers.hardware.IDeviceController
import com.vigatec.manufacturer.libraries.aisino.workflow.hardware.AisinoDeviceController
import com.vigatec.manufacturer.libraries.newpos.controller.hardware.NewposDeviceController

/**
 * Manager centralizado para acceso a información del dispositivo.
 * Delega a las implementaciones específicas de cada fabricante de forma automática.
 *
 * Ejemplo de uso:
 * ```kotlin
 * val serial = ManufacturerHardwareManager.getSerialNumber()
 * val model = ManufacturerHardwareManager.getModelName()
 * ```
 */
object ManufacturerHardwareManager : IDeviceController {
    private val TAG = "ManufacturerHardwareManager"

    private val manager: IDeviceController by lazy {
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

    override fun getSerialNumber(): String = manager.getSerialNumber()

    override fun getModelName(): String = manager.getModelName()

    override fun getFirmwareVersion(): String = manager.getFirmwareVersion()

    override fun getEmvKernelVersions(): String = manager.getEmvKernelVersions()

    override fun getHardwareVersion(): String = manager.getHardwareVersion()

    override fun getBatteryPercentage(): Int = manager.getBatteryPercentage()

    override fun getBatteryStatus(): Int = manager.getBatteryStatus()

    override fun getBatteryWearPercentage(): Int = manager.getBatteryWearPercentage()

    override fun getNFCReaderStatus(): Int = manager.getNFCReaderStatus()

    override fun getNFCReaderWearPercentage(): Int = manager.getNFCReaderWearPercentage()

    override fun getChipReaderStatus(): Int = manager.getChipReaderStatus()

    override fun getChipReaderWearPercentage(): Int = manager.getChipReaderWearPercentage()

    override fun getMagstripeStatus(): Int = manager.getMagstripeStatus()

    override fun getMagstripeWearPercentage(): Int = manager.getMagstripeWearPercentage()
}
