package com.vigatec.manufacturer.libraries.aisino.workflow.hardware

import android.util.Log
import com.vanstone.trans.api.SystemApi
import com.vigatec.manufacturer.base.controllers.hardware.IDeviceController

/**
 * Implementación de IDeviceController para dispositivos Aisino (Vanstone).
 * Obtiene información del dispositivo usando la API de SystemApi de Vanstone.
 */
class AisinoDeviceController : IDeviceController {
    private val TAG = "AisinoDeviceController"

    override fun getSerialNumber(): String {
        return try {
            SystemApi.ReadPosSn_Api()?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo número de serie Aisino: ${e.message}")
            ""
        }
    }

    override fun getModelName(): String {
        return try {
            SystemApi.SYS_MODEL?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo modelo Aisino: ${e.message}")
            ""
        }
    }

    override fun getFirmwareVersion(): String {
        return try {
            SystemApi.getFirmwareVersion_Api()?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo versión de firmware Aisino: ${e.message}")
            ""
        }
    }

    override fun getEmvKernelVersions(): String {
        return try {
            SystemApi.getAndroidKernelVersion_Api()?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo versiones EMV Aisino: ${e.message}")
            ""
        }
    }

    override fun getHardwareVersion(): String {
        return try {
            SystemApi.getHardwareVersion_Api()?.takeIf { it.isNotEmpty() } ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error obteniendo versión de hardware Aisino: ${e.message}")
            ""
        }
    }

    override fun getBatteryPercentage(): Int {
        // TODO: Implementar cuando sea disponible en SystemApi
        return -1
    }

    override fun getBatteryStatus(): Int {
        // TODO: Implementar cuando sea disponible en SystemApi
        return 0
    }

    override fun getBatteryWearPercentage(): Int {
        // TODO: Implementar cuando sea disponible en SystemApi
        return -1
    }

    override fun getNFCReaderStatus(): Int {
        // TODO: Implementar cuando sea disponible en SystemApi
        return 0
    }

    override fun getNFCReaderWearPercentage(): Int {
        // TODO: Implementar cuando sea disponible en SystemApi
        return -1
    }

    override fun getChipReaderStatus(): Int {
        // TODO: Implementar cuando sea disponible en SystemApi
        return 0
    }

    override fun getChipReaderWearPercentage(): Int {
        // TODO: Implementar cuando sea disponible en SystemApi
        return -1
    }

    override fun getMagstripeStatus(): Int {
        // TODO: Implementar cuando sea disponible en SystemApi
        return 0
    }

    override fun getMagstripeWearPercentage(): Int {
        // TODO: Implementar cuando sea disponible en SystemApi
        return -1
    }
}
