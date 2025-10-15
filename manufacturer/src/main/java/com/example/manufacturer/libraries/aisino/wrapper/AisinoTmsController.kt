package com.example.manufacturer.libraries.aisino.wrapper

import android.util.Log
import com.example.manufacturer.base.controllers.tms.ITmsController
import com.example.manufacturer.base.controllers.tms.TmsException

/**
 * Implementación del controlador TMS para dispositivos Aisino/Vanstone.
 * Utiliza el SDK de Vanstone (SystemApi.GetEnv_Api) para leer parámetros del TMS.
 */
class AisinoTmsController : ITmsController {

    companion object {
        private const val TAG = "AisinoTmsController"
        private const val DEFAULT_BUFFER_SIZE = 256
    }

    /**
     * Recupera un parámetro de configuración desde el entorno del sistema TMS.
     * Utiliza el método SystemApi.GetEnv_Api del SDK de Vanstone.
     *
     * @param paramName El nombre del parámetro a obtener (ej. "url_api", "timeout_ms").
     * @return El valor del parámetro como un String, o null si no se encuentra.
     */
    override fun getTmsParameter(paramName: String): String? {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "Intentando leer parámetro TMS: '$paramName'")

        try {
            // Buffer para almacenar el valor del parámetro que se leerá
            val paramValueBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
            Log.d(TAG, "Buffer creado con tamaño: $DEFAULT_BUFFER_SIZE bytes")

            // Intentar leer el archivo param.env para debug
            try {
                val paramEnvPath = "/data/user/0/com.vigatec.injector/files/param.env"
                val paramFile = java.io.File(paramEnvPath)
                if (paramFile.exists()) {
                    val fileContent = paramFile.readText()
                    Log.d(TAG, "📄 Archivo param.env encontrado:")
                    Log.d(TAG, "  - Ruta: $paramEnvPath")
                    Log.d(TAG, "  - Tamaño: ${paramFile.length()} bytes")
                    Log.d(TAG, "  - Contenido completo:")
                    Log.d(TAG, "────────────────────────────────")
                    Log.d(TAG, fileContent)
                    Log.d(TAG, "────────────────────────────────")
                    Log.d(TAG, "  - Buscar en archivo: '$paramName=' existe: ${fileContent.contains("$paramName=")}")
                } else {
                    Log.w(TAG, "📄 Archivo param.env NO existe en: $paramEnvPath")
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo leer param.env para debug: ${e.message}")
            }

            // Llamar a la API del SDK de Vanstone
            // Según el manual: devuelve 1 si se encuentra el parámetro
            val result = try {
                Log.d(TAG, "Buscando clase SystemApi del SDK de Vanstone...")
                val systemApiClass = Class.forName("com.vanstone.trans.api.SystemApi")
                Log.d(TAG, "✓ Clase SystemApi encontrada")

                Log.d(TAG, "Buscando método GetEnv_Api...")
                val getEnvMethod = systemApiClass.getDeclaredMethod(
                    "GetEnv_Api",
                    String::class.java,
                    ByteArray::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                Log.d(TAG, "✓ Método GetEnv_Api encontrado")

                Log.d(TAG, "Invocando GetEnv_Api con parámetros:")
                Log.d(TAG, "  - paramName: '$paramName'")
                Log.d(TAG, "  - offset: 0")
                Log.d(TAG, "  - bufferSize: ${paramValueBuffer.size}")
                Log.d(TAG, "  - flag: 1")
                Log.d(TAG, "  - maxSize: $DEFAULT_BUFFER_SIZE")

                val invokeResult = getEnvMethod.invoke(null, paramName, paramValueBuffer, 0, paramValueBuffer.size, 1, DEFAULT_BUFFER_SIZE) as Int
                Log.d(TAG, "GetEnv_Api retornó: $invokeResult (1=éxito, otro=no encontrado)")

                // SIEMPRE imprimir el contenido del buffer para debug
                val bufferContent = String(paramValueBuffer, Charsets.UTF_8).trim().trimEnd('\u0000')
                Log.d(TAG, "Contenido del buffer después de GetEnv_Api:")
                Log.d(TAG, "  - Buffer completo (primeros 100 bytes hex): ${paramValueBuffer.take(100).joinToString(" ") { "%02X".format(it) }}")
                Log.d(TAG, "  - Buffer como String: '$bufferContent'")
                Log.d(TAG, "  - Longitud del String: ${bufferContent.length} caracteres")
                Log.d(TAG, "  - ¿Buffer vacío?: ${bufferContent.isEmpty()}")
                Log.d(TAG, "  - ¿Todo ceros?: ${paramValueBuffer.take(20).all { it == 0.toByte() }}")

                invokeResult
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "✗ SDK de Vanstone no disponible. Clase SystemApi no encontrada.", e)
                throw TmsException("SDK de Vanstone no disponible en este dispositivo", e)
            } catch (e: NoSuchMethodException) {
                Log.e(TAG, "✗ Método GetEnv_Api no encontrado en el SDK de Vanstone.", e)
                throw TmsException("Método GetEnv_Api no disponible en el SDK", e)
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error al invocar GetEnv_Api del SDK de Vanstone.", e)
                Log.e(TAG, "Tipo de excepción: ${e.javaClass.simpleName}")
                Log.e(TAG, "Mensaje: ${e.message}")
                e.printStackTrace()
                throw TmsException("Error al acceder al SDK de Vanstone: ${e.message}", e)
            }

            if (result == 1) {
                // Éxito, el parámetro fue encontrado
                // Convertir el array de bytes a String y eliminar espacios en blanco
                val rawValue = String(paramValueBuffer, Charsets.UTF_8)
                val value = rawValue.trim().trimEnd('\u0000')

                Log.i(TAG, "✓ ÉXITO: Parámetro '$paramName' encontrado")
                Log.d(TAG, "  - Valor raw (hex): ${rawValue.take(50).toByteArray().joinToString(" ") { "%02X".format(it) }}")
                Log.d(TAG, "  - Valor limpio: '$value'")
                Log.d(TAG, "  - Longitud: ${value.length} caracteres")
                Log.d(TAG, "═══════════════════════════════════════════════════")

                return value
            } else {
                // El parámetro no se encontró
                Log.w(TAG, "✗ Parámetro '$paramName' NO encontrado en el TMS")
                Log.w(TAG, "  - Código de retorno: $result")
                Log.w(TAG, "  - Esto puede significar:")
                Log.w(TAG, "    1. El parámetro no está en el archivo param.env")
                Log.w(TAG, "    2. El formato del archivo es incorrecto")
                Log.w(TAG, "    3. El nombre del parámetro es diferente")
                Log.d(TAG, "═══════════════════════════════════════════════════")
                return null
            }

        } catch (e: TmsException) {
            Log.e(TAG, "═══════════════════════════════════════════════════")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error inesperado al leer el parámetro '$paramName' del TMS.", e)
            Log.e(TAG, "═══════════════════════════════════════════════════")
            throw TmsException("Error inesperado al leer parámetro '$paramName': ${e.message}", e)
        }
    }

    /**
     * Obtiene todos los parámetros del TMS (no soportado directamente por el SDK de Vanstone).
     * Esta implementación devuelve un mapa vacío ya que el SDK no proporciona un método
     * para listar todos los parámetros disponibles.
     *
     * @return Mapa vacío.
     */
    override fun getAllTmsParameters(): Map<String, String> {
        Log.d(TAG, "getAllTmsParameters no está soportado por el SDK de Vanstone.")
        return emptyMap()
    }
}
