package com.vigatec.injector.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.manufacturer.ManufacturerHardwareManager
import com.vigatec.utils.SharedPrefUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KioskModeSettings(
    val isKioskModeEnabled: Boolean = false,
    val disableStatusBar: Boolean = false,
    val hideNavigationBar: Boolean = false,
    val disableHomeKey: Boolean = false,
    val disableRecentKey: Boolean = false,
    val interceptPowerKey: Boolean = false,
    val disableSafeMode: Boolean = false,
    val preventUninstall: Boolean = false,
    val setAsDefaultLauncher: Boolean = false,
    val launchOnBoot: Boolean = false
)

sealed class KioskSettingsEvent {
    object SettingsApplied : KioskSettingsEvent()
    object SettingsReset : KioskSettingsEvent()
    data class ShowError(val message: String) : KioskSettingsEvent()
    data class ShowMessage(val message: String) : KioskSettingsEvent()
}

@HiltViewModel
class KioskModeSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefUtils: SharedPrefUtils
) : ViewModel() {

    private val TAG = "KioskModeViewModel"

    private val _settings = MutableStateFlow(KioskModeSettings())
    val settings: StateFlow<KioskModeSettings> = _settings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableSharedFlow<KioskSettingsEvent>()
    val events: SharedFlow<KioskSettingsEvent> = _events.asSharedFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _settings.value = KioskModeSettings(
            isKioskModeEnabled = sharedPrefUtils.isKioskModeEnabled(),
            disableStatusBar = sharedPrefUtils.isKioskDisableStatusBar(),
            hideNavigationBar = sharedPrefUtils.isKioskHideNavigationBar(),
            disableHomeKey = sharedPrefUtils.isKioskDisableHomeKey(),
            disableRecentKey = sharedPrefUtils.isKioskDisableRecentKey(),
            interceptPowerKey = sharedPrefUtils.isKioskInterceptPowerKey(),
            disableSafeMode = sharedPrefUtils.isKioskDisableSafeMode(),
            preventUninstall = sharedPrefUtils.isKioskPreventUninstall(),
            setAsDefaultLauncher = sharedPrefUtils.isKioskSetDefaultLauncher(),
            launchOnBoot = sharedPrefUtils.isKioskLaunchOnBoot()
        )
    }

    fun updateKioskModeEnabled(enabled: Boolean) {
        sharedPrefUtils.setKioskModeEnabled(enabled)
        _settings.value = _settings.value.copy(isKioskModeEnabled = enabled)
        
        // Si se activa el modo kiosk, activar todas las opciones recomendadas
        if (enabled) {
            updateDisableStatusBar(true)
            updateHideNavigationBar(true)
            updateDisableHomeKey(true)
            updateDisableRecentKey(true)
            updateInterceptPowerKey(true)
            updatePreventUninstall(true)
        }
    }

    fun updateDisableStatusBar(enabled: Boolean) {
        sharedPrefUtils.setKioskDisableStatusBar(enabled)
        _settings.value = _settings.value.copy(disableStatusBar = enabled)
    }

    fun updateHideNavigationBar(enabled: Boolean) {
        sharedPrefUtils.setKioskHideNavigationBar(enabled)
        _settings.value = _settings.value.copy(hideNavigationBar = enabled)
    }

    fun updateDisableHomeKey(enabled: Boolean) {
        sharedPrefUtils.setKioskDisableHomeKey(enabled)
        _settings.value = _settings.value.copy(disableHomeKey = enabled)
    }

    fun updateDisableRecentKey(enabled: Boolean) {
        sharedPrefUtils.setKioskDisableRecentKey(enabled)
        _settings.value = _settings.value.copy(disableRecentKey = enabled)
    }

    fun updateInterceptPowerKey(enabled: Boolean) {
        sharedPrefUtils.setKioskInterceptPowerKey(enabled)
        _settings.value = _settings.value.copy(interceptPowerKey = enabled)
    }

    fun updateDisableSafeMode(enabled: Boolean) {
        sharedPrefUtils.setKioskDisableSafeMode(enabled)
        _settings.value = _settings.value.copy(disableSafeMode = enabled)
    }

    fun updatePreventUninstall(enabled: Boolean) {
        sharedPrefUtils.setKioskPreventUninstall(enabled)
        _settings.value = _settings.value.copy(preventUninstall = enabled)
    }

    fun updateSetAsDefaultLauncher(enabled: Boolean) {
        sharedPrefUtils.setKioskSetDefaultLauncher(enabled)
        _settings.value = _settings.value.copy(setAsDefaultLauncher = enabled)
    }

    fun updateLaunchOnBoot(enabled: Boolean) {
        sharedPrefUtils.setKioskLaunchOnBoot(enabled)
        _settings.value = _settings.value.copy(launchOnBoot = enabled)
    }

    fun applySettings() {
        Log.i(TAG, "========== INICIO: Aplicando configuración Modo Kiosk ==========")
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            try {
                val systemController = ManufacturerHardwareManager.systemController()
                if (systemController == null) {
                    Log.e(TAG, "SystemController no disponible")
                    _events.emit(KioskSettingsEvent.ShowError("Error: Controlador de sistema no disponible"))
                    _isLoading.value = false
                    return@launch
                }

                val currentSettings = _settings.value
                val packageName = context.packageName
                
                var allSuccess = true
                val failedOperations = mutableListOf<String>()

                // 1. Barra de estado
                Log.d(TAG, "1. Configurando: Barra de estado -> ${currentSettings.disableStatusBar}")
                val statusBarResult = systemController.setStatusBarDisabled(currentSettings.disableStatusBar)
                if (!statusBarResult) failedOperations.add("Barra de estado")
                allSuccess = allSuccess && statusBarResult

                // 2. Barra de navegación
                Log.d(TAG, "2. Configurando: Barra de navegación -> ${!currentSettings.hideNavigationBar}")
                val navBarResult = systemController.setNavigationBarVisible(!currentSettings.hideNavigationBar)
                if (!navBarResult) failedOperations.add("Barra de navegación")
                allSuccess = allSuccess && navBarResult

                // 3. Teclas Home y Recent
                Log.d(TAG, "3. Configurando: Teclas Home/Recent -> ${!currentSettings.disableHomeKey}, ${!currentSettings.disableRecentKey}")
                val keysResult = systemController.setHomeRecentKeysEnabled(!currentSettings.disableHomeKey, !currentSettings.disableRecentKey)
                if (!keysResult) failedOperations.add("Teclas Home/Recent")
                allSuccess = allSuccess && keysResult

                // 4. Interceptación de Power key
                Log.d(TAG, "4. Configurando: Interceptación Power key -> ${currentSettings.interceptPowerKey}")
                val powerKeyResult = systemController.setPowerKeyLongPressIntercept(currentSettings.interceptPowerKey)
                if (!powerKeyResult) failedOperations.add("Interceptación Power key")
                allSuccess = allSuccess && powerKeyResult

                // 5. Modo seguro (si está implementado)
                // val safeModeResult = systemController.setSafeModeDisabled(currentSettings.disableSafeMode)
                
                // 6. Prevenir desinstalación
                Log.d(TAG, "6. Configurando: Prevenir desinstalación -> ${currentSettings.preventUninstall}")
                val uninstallResult = systemController.setAppUninstallDisabled(packageName, currentSettings.preventUninstall)
                if (!uninstallResult) failedOperations.add("Prevenir desinstalación")
                allSuccess = allSuccess && uninstallResult

                if (allSuccess) {
                    _events.emit(KioskSettingsEvent.SettingsApplied)
                    _events.emit(KioskSettingsEvent.ShowMessage("Configuración aplicada exitosamente"))
                } else {
                    val errorMessage = "Fallaron: ${failedOperations.joinToString(", ")}"
                    _events.emit(KioskSettingsEvent.ShowError(errorMessage))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al aplicar configuración", e)
                _events.emit(KioskSettingsEvent.ShowError("Error: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetAllSettings() {
        Log.i(TAG, "========== INICIO: Restaurando configuración Modo Kiosk ==========")
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            try {
                val systemController = ManufacturerHardwareManager.systemController()
                if (systemController == null) {
                    _events.emit(KioskSettingsEvent.ShowError("Error: Controlador de sistema no disponible"))
                    _isLoading.value = false
                    return@launch
                }

                val packageName = context.packageName
                
                // Restaurar valores por defecto
                systemController.setStatusBarDisabled(false)
                systemController.setNavigationBarVisible(true)
                systemController.setHomeRecentKeysEnabled(true, true)
                systemController.setPowerKeyLongPressIntercept(false)
                systemController.setAppUninstallDisabled(packageName, false)

                // Resetear preferencias
                sharedPrefUtils.setKioskModeEnabled(false)
                sharedPrefUtils.setKioskDisableStatusBar(false)
                sharedPrefUtils.setKioskHideNavigationBar(false)
                sharedPrefUtils.setKioskDisableHomeKey(false)
                sharedPrefUtils.setKioskDisableRecentKey(false)
                sharedPrefUtils.setKioskInterceptPowerKey(false)
                sharedPrefUtils.setKioskPreventUninstall(false)
                
                loadSettings() // Recargar estado

                _events.emit(KioskSettingsEvent.SettingsReset)
                _events.emit(KioskSettingsEvent.ShowMessage("Configuración restaurada exitosamente"))

            } catch (e: Exception) {
                Log.e(TAG, "Error al restaurar configuración", e)
                _events.emit(KioskSettingsEvent.ShowError("Error: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }
}
