package com.vigatec.android_injector.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    application: Application
    // Puedes inyectar otras dependencias, por ejemplo SharedPrefUtils, si lo necesitas
) : AndroidViewModel(application) {
    // Aquí puedes incluir la lógica propia del splash (verificación, inicialización, etc.)
}