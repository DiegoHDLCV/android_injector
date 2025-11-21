package com.vigatec.manufacturer.libraries.newpos.controller.system

import android.content.Context
import android.util.Log
import com.pos.device.sys.SystemManager
import com.vigatec.manufacturer.base.controllers.system.ISystemController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación de ISystemController para dispositivos NEWPOS.
 * Utiliza las APIs nativas del SDK NEWPOS para operaciones de sistema.
 *
 * El SDK NEWPOS proporciona:
 * - SystemManager.uninstallApp(packageName, MODE_SILENT_UNINSTALL) - Desinstalación silenciosa
 * - SystemManager.reboot() - Reboot del dispositivo
 * - SystemManager.shutdown() - Apagado del dispositivo
 * - SystemManager.grantRuntimePermission() - Otorgar permisos
 * - SystemManager.setApplicationEnabledSetting() - Habilitar/deshabilitar apps
 */
class NewposSystemController : ISystemController {

    companion object {
        private const val TAG = "NewposSystemController"
    }

    override fun initialize(context: Context) {
        Log.i(TAG, "NewposSystemController inicializado")
        // El SDK NEWPOS generalmente se inicializa en NewposKeyManager
        // Esta llamada es un placeholder para compatibilidad con la interfaz
    }

    /**
     * Desinstala una aplicación usando la API NEWPOS.
     * Utiliza SystemManager.uninstallApp() con modo silencioso.
     */
    override suspend fun silentUninstall(packageName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "=== DESINSTALACIÓN NEWPOS INICIADA ===")
                Log.i(TAG, "Paquete a desinstalar: $packageName")

                // Llamar a la API NEWPOS con modo silencioso
                Log.i(TAG, "Llamando a SystemManager.uninstallApp(MODE_SILENT_UNINSTALL)...")
                val result = SystemManager.uninstallApp(
                    packageName,
                    SystemManager.MODE_SILENT_UNINSTALL
                )

                Log.i(TAG, "Resultado de desinstalación: $result")
                Log.i(TAG, "================================================")

                if (result == 0) {
                    Log.i(TAG, "✓ Desinstalación completada exitosamente")
                    true
                } else {
                    Log.e(TAG, "✗ Desinstalación falló con código: $result")
                    false
                }

            } catch (e: Exception) {
                Log.e(TAG, "Excepción en silentUninstall: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun silentInstall(apkPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Instalación silenciosa NEWPOS: $apkPath")

                val result = SystemManager.installApp(
                    apkPath,
                    SystemManager.MODE_SILENT_INSTALL
                )

                Log.i(TAG, "Resultado de instalación: $result")

                if (result == 0) {
                    Log.i(TAG, "✓ Instalación completada exitosamente")
                    true
                } else {
                    Log.e(TAG, "✗ Instalación falló con código: $result")
                    false
                }

            } catch (e: Exception) {
                Log.e(TAG, "Excepción en silentInstall: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun reboot() {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Ejecutando reboot del dispositivo NEWPOS...")
                SystemManager.reboot()
                Log.i(TAG, "Comando de reboot enviado")
            } catch (e: Exception) {
                Log.e(TAG, "Error en reboot: ${e.message}", e)
            }
        }
    }

    override suspend fun shutdown() {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Ejecutando shutdown del dispositivo NEWPOS...")
                SystemManager.shutdown()
                Log.i(TAG, "Comando de shutdown enviado")
            } catch (e: Exception) {
                Log.e(TAG, "Error en shutdown: ${e.message}", e)
            }
        }
    }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Configurando app enabled NEWPOS: $packageName = $enabled")

                val newState = if (enabled) {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                SystemManager.setApplicationEnabledSetting(
                    packageName,
                    newState,
                    0
                )

                Log.i(TAG, "✓ Estado de app configurado exitosamente")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Error en setAppEnabled: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun grantPermission(packageName: String, permission: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Otorgando permiso NEWPOS: $permission a $packageName")

                // NEWPOS proporciona grantRuntimePermission que toma un array
                SystemManager.grantRuntimePermission(arrayOf(permission))

                Log.i(TAG, "✓ Permiso otorgado exitosamente")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Error en grantPermission: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun setStatusBarDisabled(disabled: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Configurando StatusBar disabled: $disabled")
                SystemManager.setDisableStatusBar(disabled)
                Log.i(TAG, "SystemManager.setDisableStatusBar called.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error en setStatusBarDisabled", e)
                false
            }
        }
    }

    override suspend fun setNavigationBarVisible(visible: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Configurando NavigationBar visible: $visible")
                val result = SystemManager.controlNavigationBar(visible)
                Log.i(TAG, "SystemManager.controlNavigationBar result: $result")
                result == 0
            } catch (e: Exception) {
                Log.e(TAG, "Error en setNavigationBarVisible", e)
                false
            }
        }
    }

    override suspend fun setHomeRecentKeysEnabled(homeEnabled: Boolean, recentEnabled: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Configurando Home/Recent keys: home=$homeEnabled, recent=$recentEnabled")
                SystemManager.setHomeRecentAppKeyEnable(homeEnabled, recentEnabled)
                Log.i(TAG, "SystemManager.setHomeRecentAppKeyEnable called.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error en setHomeRecentKeysEnabled", e)
                false
            }
        }
    }

    override suspend fun setPowerKeyLongPressIntercept(intercept: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Configurando PowerKey intercept: $intercept")
                val result = SystemManager.powerKeyLongPressIntercept(intercept)
                Log.i(TAG, "SystemManager.powerKeyLongPressIntercept result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error en setPowerKeyLongPressIntercept", e)
                false
            }
        }
    }


    override suspend fun setAppUninstallDisabled(packageName: String, disabled: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Configurando Uninstall disabled para $packageName: $disabled")
                val result = SystemManager.disableAppUninstalledIncludeFR(packageName, disabled)
                Log.i(TAG, "SystemManager.disableAppUninstalledIncludeFR result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error en setAppUninstallDisabled", e)
                false
            }
        }
    }

    /**
     * Método específico de NEWPOS: Establecer launcher por defecto.
     */
    suspend fun setDefaultLauncher(packageName: String, activityName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Configurando launcher por defecto: $packageName/$activityName")
                val result = SystemManager.setDefaultLauncher(packageName, activityName)
                Log.i(TAG, "Resultado: $result")
                result == 0
            } catch (e: Exception) {
                Log.e(TAG, "Error en setDefaultLauncher: ${e.message}", e)
                false
            }
        }
    }
}
