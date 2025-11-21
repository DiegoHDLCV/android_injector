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

    /**
     * Habilita o deshabilita la barra de estado (status bar).
     *
     * @param disabled true para deshabilitar (ocultar/bloquear), false para habilitar
     * @return true si la operación fue exitosa
     */
    suspend fun setStatusBarDisabled(disabled: Boolean): Boolean

    /**
     * Habilita o deshabilita la barra de navegación (botones virtuales).
     *
     * @param visible true para mostrar, false para ocultar
     * @return true si la operación fue exitosa
     */
    suspend fun setNavigationBarVisible(visible: Boolean): Boolean

    /**
     * Habilita o deshabilita las teclas Home y Recent apps.
     *
     * @param homeEnabled true para habilitar tecla Home
     * @param recentEnabled true para habilitar tecla Recent
     * @return true si la operación fue exitosa
     */
    suspend fun setHomeRecentKeysEnabled(homeEnabled: Boolean, recentEnabled: Boolean): Boolean

    /**
     * Intercepta la pulsación larga del botón de encendido (Power).
     * Útil para evitar que el usuario apague el dispositivo.
     *
     * @param intercept true para interceptar (bloquear menú de apagado), false para comportamiento normal
     * @return true si la operación fue exitosa
     */
    suspend fun setPowerKeyLongPressIntercept(intercept: Boolean): Boolean

    /**
     * Evita que una aplicación sea desinstalada por el usuario.
     *
     * @param packageName Nombre del paquete
     * @param disabled true para evitar desinstalación, false para permitirla
     * @return true si la operación fue exitosa
     */
    suspend fun setAppUninstallDisabled(packageName: String, disabled: Boolean): Boolean
}
