package com.vigatec.android_injector

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {
    // Puedes inicializar configuraciones globales aquí
}
