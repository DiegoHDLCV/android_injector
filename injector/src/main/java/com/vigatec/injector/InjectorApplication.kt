package com.vigatec.injector

import android.app.Application
import com.vigatec.config.SystemConfig
import com.vigatec.config.DeviceRole
import com.vigatec.communication.usb.UsbModeManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class InjectorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Establecer rol MASTER explícitamente para evitar polling en SubPOS
        SystemConfig.deviceRole = DeviceRole.MASTER

        // Configurar USB en modo HOST (MASTER detecta dispositivos)
        UsbModeManager.configureUsbMode()

        // Inicializar Hardware Manager (necesario para PrinterApi)
        com.vigatec.manufacturer.ManufacturerHardwareManager.initializeController(this)
    }
}
