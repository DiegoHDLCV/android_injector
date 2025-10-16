package com.example.manufacturer.libraries.aisino.wrapper

import android.content.Context
import android.util.Log
import com.example.manufacturer.base.controllers.tms.ITmsController
import com.example.manufacturer.libraries.aisino.vtms.VTMSClientConnectionManager

/**
 * Implementación del controlador TMS para dispositivos Aisino/Vanstone.
 * Utiliza el servicio VTMS (Vanstone Terminal Management System) mediante AIDL.
 */
class AisinoTmsController(private val context: Context) : ITmsController {

    companion object {
        private const val TAG = "AisinoTmsController"
    }

    /**
     * Descarga parámetros desde el servidor TMS usando AIDL.
     * 
     * @param packageName Nombre del paquete de la aplicación.
     * @param onSuccess Callback con el JSON de parámetros descargados.
     * @param onError Callback con mensaje de error si falla.
     */
    override fun downloadParametersFromTms(
        packageName: String,
        onSuccess: (parametersJson: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        Log.i(TAG, "═══════════════════════════════════════════════════════════════")
        Log.i(TAG, "Iniciando descarga de parámetros desde TMS via AIDL")
        Log.i(TAG, "  - Package: $packageName")

        try {
            VTMSClientConnectionManager.requestApplicationParameter(
                packageName = packageName,
                onSucceed = { parametersJson ->
                    if (parametersJson.isNullOrEmpty()) {
                        val errorMsg = "TMS retornó parámetros vacíos o null"
                        Log.w(TAG, "⚠ $errorMsg")
                        Log.i(TAG, "═══════════════════════════════════════════════════════════════")
                        onError(errorMsg)
                    } else {
                        Log.i(TAG, "✓ Parámetros descargados exitosamente desde TMS")
                        Log.d(TAG, "  - Tamaño: ${parametersJson.length} caracteres")
                        Log.d(TAG, "  - Preview: ${parametersJson.take(100)}${if (parametersJson.length > 100) "..." else ""}")
                        Log.i(TAG, "═══════════════════════════════════════════════════════════════")
                        onSuccess(parametersJson)
                    }
                },
                onFailed = { errorMessage ->
                    val finalError = errorMessage ?: "Error desconocido al descargar desde TMS"
                    Log.e(TAG, "✗ Error al descargar parámetros desde TMS: $finalError")
                    Log.i(TAG, "═══════════════════════════════════════════════════════════════")
                    onError(finalError)
                }
            )
        } catch (e: Exception) {
            val errorMsg = "Excepción al intentar descargar desde TMS: ${e.message}"
            Log.e(TAG, "✗ $errorMsg", e)
            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            onError(errorMsg)
        }
    }

    /**
     * Verifica si el servicio TMS está disponible en el dispositivo.
     * 
     * @return true si TMS está disponible, false en caso contrario.
     */
    override fun isTmsServiceAvailable(): Boolean {
        return try {
            val isAvailable = VTMSClientConnectionManager.isVtmsServiceAvailable(context)
            Log.d(TAG, "Servicio TMS disponible: $isAvailable")
            isAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar disponibilidad de TMS", e)
            false
        }
    }
}
