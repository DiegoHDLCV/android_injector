package com.vigatec.keyreceiver

import android.app.Application
import com.vigatec.config.SystemConfig
import com.vigatec.config.DeviceRole
import com.vigatec.communication.usb.UsbModeManager
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
