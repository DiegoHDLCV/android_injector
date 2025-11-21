package com.vigatec.injector

import android.app.Application
import com.vigatec.config.SystemConfig
import com.vigatec.config.DeviceRole
import com.vigatec.communication.usb.UsbModeManager
import com.vigatec.injector.init.DatabaseInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class InjectorApplication : Application() {

    @Inject
    lateinit var databaseInitializer: DatabaseInitializer

    override fun onCreate() {
        super.onCreate()
        // Establecer rol MASTER explícitamente para evitar polling en SubPOS
        SystemConfig.deviceRole = DeviceRole.MASTER

        // Configurar USB en modo HOST (MASTER detecta dispositivos)
        UsbModeManager.configureUsbMode()

        // Inicializar Hardware Manager (necesario para PrinterApi)
        com.vigatec.manufacturer.ManufacturerHardwareManager.initializeController(this)

        // Inicializar base de datos (usuarios/permisos por defecto)
        databaseInitializer.initialize()
    }
}
