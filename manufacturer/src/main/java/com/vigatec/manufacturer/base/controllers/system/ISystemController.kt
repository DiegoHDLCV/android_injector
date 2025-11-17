package com.vigatec.manufacturer.base.controllers.system

import android.content.Context

/**
 * Interfaz que define operaciones de control del sistema del dispositivo.
 * Proporciona abstracción para operaciones que pueden variar según el fabricante.
 *
 * Las implementaciones deben manejar:
 * - Desinstalación silenciosa de aplicaciones
 * - Control de reboot/shutdown
 * - Gestión de permisos
 * - Otras operaciones de sistema específicas del fabricante
 */
interface ISystemController {

    /**
     * Inicializa el controlador de sistema con el contexto de la aplicación.
     * Algunas implementaciones pueden necesitar esto para acceder a APIs del fabricante.
     *
     * @param context Contexto de la aplicación
     */
    fun initialize(context: Context)

    /**
     * Desinstala una aplicación de forma silenciosa (sin dialogo de confirmación).
     * La app debe tener permisos de sistema o ser app de sistema para que funcione.
     *
     * @param packageName Nombre del paquete a desinstalar (ej: "com.example.app")
     * @return true si la desinstalación fue iniciada exitosamente, false en caso contrario
     */
    suspend fun silentUninstall(packageName: String): Boolean

    /**
     * Instala una aplicación de forma silenciosa (sin dialogo de confirmación).
     * La app debe tener permisos de sistema para que funcione.
     *
     * @param apkPath Ruta absoluta al archivo APK a instalar
     * @return true si la instalación fue iniciada exitosamente, false en caso contrario
     */
    suspend fun silentInstall(apkPath: String): Boolean

    /**
     * Reinicia el dispositivo.
     */
    suspend fun reboot()

    /**
     * Apaga el dispositivo.
     */
    suspend fun shutdown()

    /**
     * Habilita o deshabilita una aplicación en el dispositivo.
     *
     * @param packageName Nombre del paquete
     * @param enabled true para habilitar, false para deshabilitar
     * @return true si la operación fue exitosa
     */
    suspend fun setAppEnabled(packageName: String, enabled: Boolean): Boolean

    /**
     * Otorga un permiso de tiempo de ejecución a una aplicación.
     *
     * @param packageName Nombre del paquete
     * @param permission Nombre del permiso (ej: "android.permission.CAMERA")
     * @return true si se otorgó el permiso exitosamente
     */
    suspend fun grantPermission(packageName: String, permission: String): Boolean
}
