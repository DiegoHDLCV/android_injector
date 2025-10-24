package com.vigatec.keyreceiver

import android.app.Application
import com.example.config.SystemConfig
import com.example.config.DeviceRole
import com.example.communication.usb.UsbModeManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Establecer rol SUBPOS explícitamente (por seguridad el default ya es SUBPOS)
        SystemConfig.deviceRole = DeviceRole.SUBPOS

        // Configurar USB en modo PERIPHERAL (SUBPOS es detectado por MASTER)
        UsbModeManager.configureUsbMode()
    }
}
