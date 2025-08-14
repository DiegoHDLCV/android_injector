package com.vigatec.injector

import android.app.Application
import com.example.config.SystemConfig
import com.example.config.DeviceRole
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class InjectorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Establecer rol MASTER explícitamente para evitar polling en SubPOS
        SystemConfig.deviceRole = DeviceRole.MASTER
    }
}
