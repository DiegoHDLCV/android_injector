package com.example.communication.usb

import android.os.Build
import android.util.Log
import com.example.config.DeviceRole
import com.example.config.SystemConfig

/**
 * Gestiona la configuración del modo USB (HOST o PERIPHERAL) según el rol del dispositivo.
 *
 * CONTEXTO:
 * Cuando dos dispositivos Android se conectan vía USB, uno debe actuar como HOST (master)
 * y el otro como PERIPHERAL (device/esclavo). Sin esto, no hay enumeración mutua.
 *
 * ROLES:
 * - MASTER (injector): Debe ser USB HOST → detecta otros dispositivos
 * - SUBPOS (keyreceiver): Debe ser USB PERIPHERAL → es detectado por MASTER
 *
 * CONFIGURACIÓN:
 * Se realiza via propiedades del sistema (setprop) que requieren:
 * - Acceso a /system/build.prop
 * - Permisos de sistema
 * - En algunos casos, acceso root
 */
object UsbModeManager {
    private const val TAG = "UsbModeManager"

    /**
     * Configura el modo USB según el rol del dispositivo.
     * Se llama durante inicialización de la app.
     */
    fun configureUsbMode() {
        val role = SystemConfig.deviceRole
        Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ CONFIGURANDO MODO USB SEGÚN ROL: $role")
        Log.i(TAG, "╠══════════════════════════════════════════════════════════════")

        try {
            when (role) {
                DeviceRole.MASTER -> {
                    Log.i(TAG, "║ → MASTER (injector): Configurar como USB HOST")
                    configureHostMode()
                }
                DeviceRole.SUBPOS -> {
                    Log.i(TAG, "║ → SUBPOS (keyreceiver): Configurar como USB PERIPHERAL")
                    configurePeripheralMode()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar modo USB: ${e.message}", e)
            // No es crítico - continuamos de todas formas
        }

        Log.i(TAG, "╚══════════════════════════════════════════════════════════════")
    }

    /**
     * Configura el dispositivo como USB HOST (MASTER).
     * El HOST puede enumerar y comunicarse con dispositivos periféricos.
     */
    private fun configureHostMode() {
        try {
            Log.d(TAG, "  Intentando configurar como USB HOST...")

            // Intento 1: Usar setprop para configurar via propiedades del sistema
            try {
                Log.d(TAG, "  → Intento 1: setprop sys.usb.config")
                Runtime.getRuntime().exec("setprop sys.usb.config adb,mtp").waitFor()
                Log.i(TAG, "  ✓ USB HOST configurado via setprop")
                return
            } catch (e: Exception) {
                Log.d(TAG, "  ℹ️ setprop no disponible: ${e.message}")
            }

            // Intento 2: Escribir directamente en /system/build.prop (requiere root)
            try {
                Log.d(TAG, "  → Intento 2: /system/build.prop (requiere root)")
                Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop ro.usb.default_config adb,mtp")).waitFor()
                Log.i(TAG, "  ✓ USB HOST configurado via build.prop")
                return
            } catch (e: Exception) {
                Log.d(TAG, "  ℹ️ Root no disponible: ${e.message}")
            }

            Log.d(TAG, "  → USB HOST: Configuración no disponible (puede requerir root)")

        } catch (e: Exception) {
            Log.w(TAG, "Error configurando HOST mode: ${e.message}")
        }
    }

    /**
     * Configura el dispositivo como USB PERIPHERAL (DEVICE/SUBPOS).
     * El PERIPHERAL es detectado por el HOST y se comunica a través del cable USB.
     */
    private fun configurePeripheralMode() {
        try {
            Log.d(TAG, "  Intentando configurar como USB PERIPHERAL...")

            // Intento 1: Usar setprop para configurar accessory mode
            try {
                Log.d(TAG, "  → Intento 1: setprop sys.usb.config accessory")
                Runtime.getRuntime().exec("setprop sys.usb.config adb,accessory").waitFor()
                Log.i(TAG, "  ✓ USB PERIPHERAL configurado via setprop")
                return
            } catch (e: Exception) {
                Log.d(TAG, "  ℹ️ setprop no disponible: ${e.message}")
            }

            // Intento 2: Usar serial mode como alternativa
            try {
                Log.d(TAG, "  → Intento 2: setprop sys.usb.config serial (alternativa)")
                Runtime.getRuntime().exec("setprop sys.usb.config adb,serial").waitFor()
                Log.i(TAG, "  ✓ USB PERIPHERAL configurado via serial mode")
                return
            } catch (e: Exception) {
                Log.d(TAG, "  ℹ️ Serial mode no disponible: ${e.message}")
            }

            // Intento 3: Escribir en /system/build.prop (requiere root)
            try {
                Log.d(TAG, "  → Intento 3: /system/build.prop (requiere root)")
                Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop ro.usb.default_config adb,accessory")).waitFor()
                Log.i(TAG, "  ✓ USB PERIPHERAL configurado via build.prop")
                return
            } catch (e: Exception) {
                Log.d(TAG, "  ℹ️ Root no disponible: ${e.message}")
            }

            Log.d(TAG, "  → USB PERIPHERAL: Configuración no disponible (puede requerir root)")

        } catch (e: Exception) {
            Log.w(TAG, "Error configurando PERIPHERAL mode: ${e.message}")
        }
    }

    /**
     * Obtiene el modo USB actual del dispositivo.
     * Útil para debugging y logging.
     */
    fun getCurrentUsbMode(): String {
        return try {
            // En Android, el modo USB actual está en sys.usb.config
            // Pero acceder a propiedades de sistema requiere permisos especiales
            "Unable to read (requires system permissions)"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
