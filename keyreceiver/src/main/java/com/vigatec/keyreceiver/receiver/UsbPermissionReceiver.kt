package com.vigatec.keyreceiver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * BroadcastReceiver silencioso para otorgación automática de permisos USB
 *
 * Este receiver escucha cuando se conectan dispositivos USB CH340.
 * Automáticamente otorga permiso sin lanzar la aplicación.
 *
 * Esto permite que la app acceda al cable CH340 cuando lo necesite,
 * sin que Android lance automáticamente la aplicación.
 */
class UsbPermissionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UsbPermissionReceiver"
        private const val ACTION_USB_PERMISSION = "com.vigatec.keyreceiver.USB_PERMISSION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    Log.d(TAG, "🔌 Dispositivo USB detectado: ${device.deviceName}")
                    Log.d(TAG, "   VID: 0x${device.vendorId.toString(16)}, PID: 0x${device.productId.toString(16)}")

                    // Verificar si es CH340
                    if (isChipCH340(device)) {
                        Log.i(TAG, "✓ CH340 detectado - Otorgando permiso automáticamente")
                        requestUsbPermission(context, device)
                    }
                }
            }
            ACTION_USB_PERMISSION -> {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val permission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                if (device != null) {
                    if (permission) {
                        Log.i(TAG, "✅ Permiso USB otorgado para ${device.deviceName}")
                    } else {
                        Log.w(TAG, "⚠️ Permiso USB denegado para ${device.deviceName}")
                    }
                }
            }
        }
    }

    private fun isChipCH340(device: UsbDevice): Boolean {
        val vendorId = device.vendorId
        val productId = device.productId

        // CH340 Vendor ID: 0x1A86
        if (vendorId != 0x1A86) {
            return false
        }

        // Check known CH340 product IDs
        return productId == 0x7523 || productId == 0x5523 || productId == 0x5512
    }

    private fun requestUsbPermission(context: Context, device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val permissionIntent = android.app.PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PERMISSION),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, permissionIntent)
    }
}
