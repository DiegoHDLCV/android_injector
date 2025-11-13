package com.vigatec.manufacturer.libraries.newpos.controller.hardware

import android.util.Log
import com.pos.device.config.DevConfig
import com.vigatec.manufacturer.base.controllers.hardware.IDeviceController

/**
 * Implementación de IDeviceController para dispositivos NewPOS.
 * Obtiene información del dispositivo usando la API de DevConfig de NewPOS.
 */
class NewposDeviceController : IDeviceController {
    private val TAG = "NewposDeviceController"

    override fun getSerialNumber(): String {
        return try {
            DevConfig.getSN()?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo número de serie NewPOS: ${e.message}")
            ""
        }
    }

    override fun getModelName(): String {
        return try {
            DevConfig.getMachine()?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo modelo NewPOS: ${e.message}")
            ""
        }
    }

    override fun getFirmwareVersion(): String {
        return try {
            DevConfig.getFirmwareVersion()?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo versión de firmware NewPOS: ${e.message}")
            ""
        }
    }

    override fun getEmvKernelVersions(): String {
        return try {
            DevConfig.getEmvVersions()?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo versiones EMV NewPOS: ${e.message}")
            ""
        }
    }

    override fun getHardwareVersion(): String {
        return try {
            DevConfig.getHardwareVersion()?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo versión de hardware NewPOS: ${e.message}")
            ""
        }
    }

    override fun getBatteryPercentage(): Int {
        // TODO: Implementar cuando sea disponible en DevConfig
        return -1
    }

    override fun getBatteryStatus(): Int {
        // TODO: Implementar cuando sea disponible en DevConfig
        return 0
    }

    override fun getBatteryWearPercentage(): Int {
        // TODO: Implementar cuando sea disponible en DevConfig
        return -1
    }

    override fun getNFCReaderStatus(): Int {
        // TODO: Implementar cuando sea disponible en DevConfig
        return 0
    }

    override fun getNFCReaderWearPercentage(): Int {
        // TODO: Implementar cuando sea disponible en DevConfig
        return -1
    }

    override fun getChipReaderStatus(): Int {
        // TODO: Implementar cuando sea disponible en DevConfig
        return 0
    }

    override fun getChipReaderWearPercentage(): Int {
        // TODO: Implementar cuando sea disponible en DevConfig
        return -1
    }

    override fun getMagstripeStatus(): Int {
        // TODO: Implementar cuando sea disponible en DevConfig
        return 0
    }

    override fun getMagstripeWearPercentage(): Int {
        // TODO: Implementar cuando sea disponible en DevConfig
        return -1
    }
}
