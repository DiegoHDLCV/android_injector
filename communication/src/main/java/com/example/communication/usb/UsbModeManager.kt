package com.example.communication.usb

import android.util.Log
import com.example.config.DeviceRole
import com.example.config.SystemConfig

/**
 * Gestiona la configuración del modo USB (HOST o PERIPHERAL).
 *
 * INFORMACIÓN CLAVE DE AISINO:
 * "The one which connects OTG will work in host mode"
 *
 * Significado:
 * El dispositivo que tiene el CABLE OTG (con adaptador/conector) automáticamente
 * actúa como USB HOST. El otro dispositivo actúa como PERIPHERAL.
 *
 * ARQUITECTURA:
 * - El rol del dispositivo (MASTER/SUBPOS) define la intención de la aplicación
 * - El puerto USB físico (OTG o normal) determina el rol USB automáticamente
 * - No necesitamos forzar manualmente los modos en la mayoría de casos
 *
 * DISPOSITIVOS:
 * - injector (MASTER): Debería conectarse con cable OTG → automáticamente HOST
 * - keyreceiver (SUBPOS): Debería conectarse con puerto USB normal → automáticamente PERIPHERAL
 *
 * IMPORTANTE:
 * El puerto USB físico es lo que determina el rol. La configuración vía setprop
 * es un fallback para casos donde es necesario.
 */
object UsbModeManager {
    private const val TAG = "UsbModeManager"

    /**
     * Realiza la configuración USB necesaria.
     * Se llama durante inicialización de la app.
     *
     * En la mayoría de casos, no necesita hacer nada porque:
     * - El puerto USB físico (OTG vs normal) determina automáticamente el rol
     * - Android configura host/peripheral según el tipo de conector
     */
    fun configureUsbMode() {
        val role = SystemConfig.deviceRole
        Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ USB MODE MANAGER INICIALIZADO")
        Log.i(TAG, "╠══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ Rol del dispositivo: $role")
        Log.i(TAG, "║")
        Log.i(TAG, "║ INFORMACIÓN DE AISINO:")
        Log.i(TAG, "║ 'The one which connects OTG will work in host mode'")
        Log.i(TAG, "║")
        Log.i(TAG, "║ → El puerto USB físico determina automáticamente el rol")
        Log.i(TAG, "║   • Cable OTG (adaptador) → USB HOST (detecta dispositivos)")
        Log.i(TAG, "║   • Puerto USB normal → USB PERIPHERAL (es detectado)")
        Log.i(TAG, "║")

        val usbStateBefore = readUsbState()
        if (usbStateBefore != null) {
            Log.i(TAG, "║ Estado USB actual: '$usbStateBefore'")
        } else {
            Log.w(TAG, "║ Estado USB actual no disponible")
        }

        when (role) {
            DeviceRole.MASTER -> {
                Log.i(TAG, "║ MASTER (injector):")
                Log.i(TAG, "║   • Debe usar CABLE OTG para ser USB HOST")
                Log.i(TAG, "║   • Detectará dispositivos conectados al otro puerto")
            }
            DeviceRole.SUBPOS -> {
                Log.i(TAG, "║ SUBPOS (keyreceiver):")
                Log.i(TAG, "║   • Debe usar PUERTO USB NORMAL (no OTG)")
                Log.i(TAG, "║   • Será detectado automáticamente por el HOST")
            }
        }

        Log.i(TAG, "╚══════════════════════════════════════════════════════════════")

        // Intento de configuración manual solo como fallback
        // En la mayoría de casos no será necesario porque el puerto USB lo hace automáticamente
        tryManualUsbConfiguration(role)
    }

    /**
     * Intento FALLBACK de configuración manual vía setprop.
     * Solo se intenta si el puerto USB no configura automáticamente.
     */
    private fun tryManualUsbConfiguration(role: DeviceRole) {
        try {
            Log.d(TAG, "Intentando configuración manual vía setprop (fallback)...")

            when (role) {
                DeviceRole.MASTER -> {
                    // Intenta configurar como HOST (en caso que no sea automático)
                    try {
                        Runtime.getRuntime().exec("setprop sys.usb.config adb,mtp").waitFor()
                        Log.d(TAG, "  Fallback HOST: configurado via setprop")
                    } catch (e: Exception) {
                        Log.d(TAG, "  Fallback HOST: no disponible - usando configuración automática")
                    }
                }
                DeviceRole.SUBPOS -> {
                    Log.d(TAG, "  Fallback PERIPHERAL deshabilitado para evitar forzar modo accessory")
                    return
                }
            }

            val usbStateAfter = readUsbState()
            if (usbStateAfter != null) {
                Log.d(TAG, "  Estado USB tras fallback: '$usbStateAfter'")
            }
        } catch (e: Exception) {
            Log.d(TAG, "  Fallback deshabilitado: ${e.message}")
        }
    }

    private fun readUsbState(): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "sys.usb.state"))
            process.inputStream.bufferedReader().use { reader ->
                val state = reader.readLine()?.trim()
                process.waitFor()
                if (state.isNullOrEmpty()) null else state
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer sys.usb.state: ${e.message}")
            null
        }
    }
}
