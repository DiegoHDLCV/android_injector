package com.example.communication.libraries.aisino.manager

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.communication.libraries.aisino.wrapper.AisinoUsbComController

/**
 * Gestor de dispositivos USB Aisino
 *
 * PROPÓSITO:
 * - Encontrar dispositivos Aisino conectados
 * - Verificar permisos USB
 * - Crear controladores para comunicación
 *
 * VENTAJAS:
 * - Interfaz unificada para detección USB
 * - Manejo de permisos automático
 * - Soporte para múltiples dispositivos
 */
class AisinoUsbDeviceManager(private val context: Context) {

    companion object {
        // Vendor ID de Aisino (fabricante chino)
        private const val AISINO_VENDOR_ID = 0x05C6

        // Product IDs para diferentes modelos y configuraciones de A90
        private val SUPPORTED_PRODUCT_IDS = listOf(
            0x901D,  // A90 configurado como USB serial (CDC-ACM)
            0x9020,  // A90 configuración alternativa
        )

        private const val TAG = "AisinoUsbDeviceManager"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * Información sobre un dispositivo Aisino
     */
    data class AisinoDevice(
        val device: UsbDevice,
        val name: String,
        val vendorId: Int,
        val productId: Int,
        val isConnected: Boolean = true
    )

    /**
     * Buscar todos los dispositivos Aisino conectados
     *
     * Escanea los dispositivos USB disponibles y retorna los que
     * coinciden con Vendor/Product ID de Aisino
     *
     * @return Lista de dispositivos Aisino encontrados
     */
    fun findAisinoDevices(): List<AisinoDevice> {
        val devices = mutableListOf<AisinoDevice>()

        Log.d(TAG, "Buscando dispositivos Aisino...")

        for (device in usbManager.deviceList.values) {
            if (device.vendorId == AISINO_VENDOR_ID &&
                SUPPORTED_PRODUCT_IDS.contains(device.productId)) {

                devices.add(AisinoDevice(
                    device = device,
                    name = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    isConnected = true
                ))

                Log.i(TAG, "✓ Encontrado: ${device.deviceName} " +
                    "(${String.format("0x%04X:0x%04X", device.vendorId, device.productId)})")
            }
        }

        if (devices.isEmpty()) {
            Log.w(TAG, "No se encontraron dispositivos Aisino")
        } else {
            Log.i(TAG, "Total: ${devices.size} dispositivo(s)")
        }

        return devices
    }

    /**
     * Verificar si hay permiso para un dispositivo
     *
     * Android requiere permisos explícitos para acceder a dispositivos USB
     *
     * @param device Dispositivo a verificar
     * @return true si tenemos permiso, false caso contrario
     */
    fun hasPermission(device: UsbDevice): Boolean {
        val result = usbManager.hasPermission(device)
        Log.d(TAG, "Permiso para ${device.deviceName}: $result")
        return result
    }

    /**
     * Solicitar permiso para un dispositivo
     *
     * Muestra un diálogo al usuario pidiendo permiso para acceder
     * al dispositivo USB
     *
     * @param device Dispositivo para el que solicitar permiso
     * @param pendingIntent Intent que se ejecutará cuando el usuario responda
     */
    fun requestPermission(
        device: UsbDevice,
        pendingIntent: android.app.PendingIntent
    ) {
        Log.i(TAG, "Solicitando permiso para: ${device.deviceName}")
        usbManager.requestPermission(device, pendingIntent)
    }

    /**
     * Crear un controlador para un dispositivo específico
     *
     * @param device Dispositivo USB Aisino
     * @return AisinoUsbComController listo para usar
     */
    fun createController(device: UsbDevice): AisinoUsbComController {
        Log.i(TAG, "Creando controlador para: ${device.deviceName}")
        return AisinoUsbComController(context, usbManager, device)
    }

    /**
     * Obtener información detallada de un dispositivo
     *
     * Útil para logging y debugging
     *
     * @param device Dispositivo a describir
     * @return String con información formateada
     */
    fun getDeviceInfo(device: UsbDevice): String {
        return """
            ═══════════════════════════════════════════════════════
            Nombre: ${device.deviceName}
            Vendor ID: 0x${device.vendorId.toString(16).uppercase()}
            Product ID: 0x${device.productId.toString(16).uppercase()}
            Interfaces: ${device.interfaceCount}
            Número de serie: ${device.serialNumber ?: "N/A"}
            ═══════════════════════════════════════════════════════
        """.trimIndent()
    }

    /**
     * Obtener el nombre de un dispositivo basado en sus IDs
     *
     * @param vendorId Vendor ID
     * @param productId Product ID
     * @return Nombre descriptivo del dispositivo
     */
    fun getDeviceNameForIds(vendorId: Int, productId: Int): String {
        return when {
            vendorId == AISINO_VENDOR_ID && productId == 0x901D -> "Aisino A90 (CDC-ACM)"
            vendorId == AISINO_VENDOR_ID && productId == 0x9020 -> "Aisino A90 (Alt Config)"
            else -> "Dispositivo desconocido (0x${vendorId.toString(16)}:0x${productId.toString(16)})"
        }
    }
}
