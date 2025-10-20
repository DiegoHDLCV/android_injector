package com.vigatec.injector.util

import android.content.Context
import android.content.pm.PackageManager
import com.vigatec.injector.data.local.database.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proveedor de información del sistema.
 * Obtiene versiones reales de la aplicación y de la base de datos.
 */
@Singleton
class SystemInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase
) {

    /**
     * Obtiene la versión de la aplicación desde el PackageManager
     * @return Versión de la aplicación (e.g., "1.2")
     */
    fun getApplicationVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0
            )
            packageInfo.versionName ?: "Desconocida"
        } catch (e: Exception) {
            "Desconocida"
        }
    }

    /**
     * Obtiene la versión del código de la aplicación
     * @return Código de versión (e.g., 3)
     */
    fun getApplicationVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0
            )
            packageInfo.versionCode
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Obtiene la versión de la base de datos
     * @return Versión de la BD (e.g., "v3")
     */
    fun getDatabaseVersion(): String {
        return try {
            val version = appDatabase.openHelper.readableDatabase.version
            "v$version"
        } catch (e: Exception) {
            "Desconocida"
        }
    }

    /**
     * Obtiene información completa de versión
     * @return String con versión de app y BD (e.g., "1.2 (BD v3)")
     */
    fun getCompleteVersionInfo(): String {
        return try {
            val appVersion = getApplicationVersion()
            val dbVersion = getDatabaseVersion()
            "$appVersion (BD $dbVersion)"
        } catch (e: Exception) {
            "Desconocida"
        }
    }

    /**
     * Obtiene versión del Android
     * @return Versión de Android (e.g., "14")
     */
    fun getAndroidVersion(): String {
        return try {
            android.os.Build.VERSION.RELEASE
        } catch (e: Exception) {
            "Desconocida"
        }
    }

    /**
     * Obtiene el modelo del dispositivo
     * @return Modelo del dispositivo
     */
    fun getDeviceModel(): String {
        return try {
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        } catch (e: Exception) {
            "Desconocido"
        }
    }

    /**
     * Obtiene el nombre del paquete de la aplicación
     * @return Package name
     */
    fun getPackageName(): String {
        return context.packageName
    }
}
