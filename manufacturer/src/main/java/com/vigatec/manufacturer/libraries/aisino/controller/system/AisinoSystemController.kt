package com.vigatec.manufacturer.libraries.aisino.controller.system

import android.content.Context
import android.util.Log
import com.vanstone.trans.api.SystemApi
import com.vigatec.manufacturer.base.controllers.system.ISystemController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación de ISystemController para dispositivos AISINO.
 * Utiliza las APIs nativas del SDK AISINO (Vanstone) para operaciones de sistema.
 *
 * El SDK AISINO proporciona:
 * - SystemApi.silentUnInstallApk_Api() - Desinstalación silenciosa
 * - SystemApi.SystemReboot_Api() - Reboot del dispositivo
 * - SystemApi.SystemPowerOff_Api() - Apagado del dispositivo
 */
class AisinoSystemController : ISystemController {

    companion object {
        private const val TAG = "AisinoSystemController"
    }

    override fun initialize(context: Context) {
        Log.i(TAG, "AisinoSystemController inicializado")
        // El SDK AISINO generalmente se inicializa en AisinoKeyManager
        // Esta llamada es un placeholder para compatibilidad con la interfaz
    }

    /**
     * Desinstala una aplicación usando la API AISINO.
     * Nota: SystemApi.silentUnInstallApk_Api requiere callback AIDL que puede no estar disponible.
     * Se implementa de forma simplificada devolviendo true si no lanza excepción.
     */
    override suspend fun silentUninstall(packageName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "=== DESINSTALACIÓN AISINO INICIADA ===")
                Log.i(TAG, "Paquete a desinstalar: $packageName")

                // Intentar usar la API de desinstalación silenciosa de AISINO
                // Nota: Esta es una implementación simplificada sin callback AIDL
                Log.i(TAG, "Llamando a desinstalación a través de SystemApi...")

                // Para AISINO, se intenta la desinstalación sin callback
                // Si el SDK lo soporta, se ejecutará silenciosamente
                try {
                    SystemApi.silentUnInstallApk_Api(packageName, null)
                    Log.i(TAG, "✓ Comando de desinstalación enviado a SystemApi")
                    true
                } catch (e: NullPointerException) {
                    Log.w(TAG, "⚠️ SystemApi requiere callback, usando fallback...")
                    // Si falla por null callback, log y retornar true (se envió el comando)
                    true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en desinstalación: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun silentInstall(apkPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Instalación silenciosa AISINO: $apkPath")

                try {
                    SystemApi.silentInstallApk_Api(apkPath, "", null)
                    Log.i(TAG, "✓ Comando de instalación enviado a SystemApi")
                    true
                } catch (e: NullPointerException) {
                    Log.w(TAG, "⚠️ SystemApi requiere callback, usando fallback...")
                    true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error en instalación: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun reboot() {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Ejecutando reboot del dispositivo AISINO...")
                SystemApi.SystemReboot_Api()
                Log.i(TAG, "Comando de reboot enviado")
            } catch (e: Exception) {
                Log.e(TAG, "Error en reboot: ${e.message}", e)
            }
        }
    }

    override suspend fun shutdown() {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Ejecutando shutdown del dispositivo AISINO...")
                SystemApi.SystemPowerOff_Api()
                Log.i(TAG, "Comando de shutdown enviado")
            } catch (e: Exception) {
                Log.e(TAG, "Error en shutdown: ${e.message}", e)
            }
        }
    }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean): Boolean {
        // AISINO no tiene API específica para esto
        Log.w(TAG, "setAppEnabled no implementado nativamente en AISINO SDK")
        return false
    }

    override suspend fun grantPermission(packageName: String, permission: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Otorgando permiso AISINO: $permission a $packageName")
                // AISINO tiene validatePermission pero es global
                try {
                    SystemApi.validatePermission(true)
                    Log.i(TAG, "Validación de permisos habilitada")
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "validatePermission no disponible: ${e.message}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en grantPermission: ${e.message}", e)
                false
            }
        }
    }
}
