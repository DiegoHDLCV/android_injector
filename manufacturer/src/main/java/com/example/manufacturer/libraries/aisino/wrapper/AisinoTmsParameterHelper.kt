package com.example.manufacturer.libraries.aisino.wrapper

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Helper class para gestionar el archivo param.env utilizado por el SDK de Vanstone TMS.
 * Este archivo almacena los parámetros de configuración que normalmente se descargan
 * desde la plataforma TMS.
 */
object AisinoTmsParameterHelper {

    private const val TAG = "AisinoTmsParameterHelper"
    private const val PARAM_ENV_FILE = "param.env"

    /**
     * Verifica si el archivo param.env existe.
     */
    fun paramEnvFileExists(context: Context): Boolean {
        val file = File(context.filesDir, PARAM_ENV_FILE)
        return file.exists()
    }

    /**
     * Crea un archivo param.env vacío con solo la sección [ENV].
     * Esto permite que el SDK de Vanstone trabaje con el archivo,
     * y los parámetros pueden agregarse después desde el TMS o la UI.
     */
    fun createEmptyParamEnvFile(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, PARAM_ENV_FILE)

            Log.d(TAG, "Creando archivo param.env vacío...")
            Log.d(TAG, "Ruta: ${file.absolutePath}")

            // Crear archivo con solo la sección [ENV]
            val content = "[ENV]\n"

            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }

            // Establecer permisos de lectura/escritura
            val permissionsSet = file.setReadable(true, false) && file.setWritable(true, false)
            if (permissionsSet) {
                Log.d(TAG, "Permisos establecidos correctamente (lectura/escritura)")
            } else {
                Log.w(TAG, "No se pudieron establecer permisos explícitos (puede no ser necesario)")
            }

            // Verificar que el archivo se creó correctamente
            if (file.exists()) {
                Log.i(TAG, "✓ Archivo param.env vacío creado exitosamente")
                Log.d(TAG, "  - Ruta: ${file.absolutePath}")
                Log.d(TAG, "  - Tamaño: ${file.length()} bytes")
                Log.d(TAG, "  - Puede leer: ${file.canRead()}")
                Log.d(TAG, "  - Puede escribir: ${file.canWrite()}")
            } else {
                Log.e(TAG, "✗ El archivo no existe después de intentar crearlo")
                return false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al crear archivo param.env vacío", e)
            Log.e(TAG, "Tipo de excepción: ${e.javaClass.simpleName}")
            Log.e(TAG, "Mensaje: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Obtiene la ruta completa del archivo param.env.
     */
    fun getParamEnvPath(context: Context): String {
        return File(context.filesDir, PARAM_ENV_FILE).absolutePath
    }

    /**
     * Crea un archivo param.env de ejemplo para testing usando el SDK de Vanstone.
     * NOTA: En producción, este archivo debería ser descargado/sincronizado desde el servidor TMS.
     *
     * Utiliza FileApi.WritePrivateProfileString_Api del SDK de Vanstone para garantizar
     * compatibilidad con GetEnv_Api.
     */
    fun createSampleParamEnvFile(context: Context, parameters: Map<String, String> = getDefaultTestParameters()): Boolean {
        return try {
            val filePath = getParamEnvPath(context)
            Log.d(TAG, "Creando param.env usando SDK de Vanstone en: $filePath")

            // Intentar usar la API del SDK de Vanstone
            try {
                val fileApiClass = Class.forName("com.vanstone.trans.api.FileApi")
                val writeMethod = fileApiClass.getDeclaredMethod(
                    "WritePrivateProfileString_Api",
                    String::class.java,  // section
                    String::class.java,  // key
                    String::class.java,  // value
                    String::class.java   // filename
                )

                // Escribir cada parámetro usando la API del SDK
                parameters.forEach { (key, value) ->
                    val result = writeMethod.invoke(null, "ENV", key, value, filePath) as Boolean
                    if (result) {
                        Log.d(TAG, "Parámetro '$key' escrito exitosamente")
                    } else {
                        Log.w(TAG, "Fallo al escribir parámetro '$key'")
                    }
                }

                Log.i(TAG, "Archivo param.env creado exitosamente usando SDK en: $filePath")

                // Verificar contenido
                val content = readParamEnvFile(context)
                Log.d(TAG, "Contenido del archivo:\n$content")

                true
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "SDK de Vanstone no disponible, usando método manual", e)
                // Fallback: crear archivo manualmente
                createParamEnvManually(context, parameters)
            } catch (e: NoSuchMethodException) {
                Log.w(TAG, "Método WritePrivateProfileString_Api no encontrado, usando método manual", e)
                createParamEnvManually(context, parameters)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear archivo param.env", e)
            false
        }
    }

    /**
     * Método de respaldo para crear el archivo manualmente si el SDK no está disponible.
     */
    private fun createParamEnvManually(context: Context, parameters: Map<String, String>): Boolean {
        return try {
            val file = File(context.filesDir, PARAM_ENV_FILE)

            Log.d(TAG, "Creando archivo param.env manualmente (método fallback)...")
            Log.d(TAG, "Ruta: ${file.absolutePath}")
            Log.d(TAG, "Parámetros a escribir: ${parameters.size}")

            // Crear contenido en formato INI
            val content = buildString {
                appendLine("[ENV]")
                parameters.forEach { (key, value) ->
                    appendLine("$key=$value")
                    Log.d(TAG, "  Agregando: $key=$value")
                }
            }

            // Escribir archivo
            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }

            // Establecer permisos
            file.setReadable(true, false)
            file.setWritable(true, false)

            Log.i(TAG, "✓ Archivo param.env creado manualmente")
            Log.d(TAG, "  - Tamaño: ${file.length()} bytes")
            Log.d(TAG, "  - Puede leer: ${file.canRead()}")
            Log.d(TAG, "  - Puede escribir: ${file.canWrite()}")
            Log.d(TAG, "Contenido del archivo:\n$content")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error al crear archivo param.env manualmente", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Escribe un parámetro en el archivo param.env.
     * Si el archivo no existe, lo crea.
     * Si el parámetro ya existe, lo actualiza.
     *
     * NOTA: Esta es una implementación simple. El SDK de Vanstone tiene su propio método
     * WritePrivateProfileString_Api que debería usarse idealmente.
     */
    fun writeParameter(context: Context, key: String, value: String): Boolean {
        return try {
            val file = File(context.filesDir, PARAM_ENV_FILE)

            // Leer contenido existente o crear nuevo
            val existingContent = if (file.exists()) {
                file.readLines()
            } else {
                listOf("[ENV]")
            }

            // Actualizar o agregar parámetro
            val updatedContent = mutableListOf<String>()
            var parameterUpdated = false
            var inEnvSection = false

            existingContent.forEach { line ->
                when {
                    line.trim() == "[ENV]" -> {
                        updatedContent.add(line)
                        inEnvSection = true
                    }
                    line.trim().startsWith("[") -> {
                        // Si no se actualizó el parámetro y estamos saliendo de [ENV], agregarlo
                        if (inEnvSection && !parameterUpdated) {
                            updatedContent.add("$key=$value")
                            parameterUpdated = true
                        }
                        updatedContent.add(line)
                        inEnvSection = false
                    }
                    inEnvSection && line.trim().startsWith("$key=") -> {
                        updatedContent.add("$key=$value")
                        parameterUpdated = true
                    }
                    else -> {
                        updatedContent.add(line)
                    }
                }
            }

            // Si el parámetro no se agregó, agregarlo al final de [ENV]
            if (!parameterUpdated) {
                if (!inEnvSection) {
                    // No había sección [ENV], agregarla
                    if (updatedContent.isEmpty() || updatedContent.last().isNotBlank()) {
                        updatedContent.add("[ENV]")
                    }
                }
                updatedContent.add("$key=$value")
            }

            // Escribir archivo actualizado
            file.writeText(updatedContent.joinToString("\n"))

            Log.i(TAG, "Parámetro '$key' escrito en param.env")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al escribir parámetro en param.env", e)
            false
        }
    }

    /**
     * Escribe múltiples parámetros en el archivo param.env.
     */
    fun writeParameters(context: Context, parameters: Map<String, String>): Boolean {
        return try {
            parameters.forEach { (key, value) ->
                if (!writeParameter(context, key, value)) {
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al escribir parámetros en param.env", e)
            false
        }
    }

    /**
     * Lee el contenido completo del archivo param.env.
     */
    fun readParamEnvFile(context: Context): String? {
        return try {
            val file = File(context.filesDir, PARAM_ENV_FILE)
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al leer archivo param.env", e)
            null
        }
    }

    /**
     * Elimina el archivo param.env.
     */
    fun deleteParamEnvFile(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, PARAM_ENV_FILE)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar archivo param.env", e)
            false
        }
    }

    /**
     * Parámetros de prueba por defecto.
     * Estos son solo ejemplos y deben reemplazarse con valores reales del TMS.
     */
    private fun getDefaultTestParameters(): Map<String, String> = mapOf(
        "url_api" to "https://api.example.com/v1",
        "timeout_ms" to "30000",
        "merchant_id" to "TEST_MERCHANT_001",
        "terminal_id" to "TEST_TERMINAL_001",
        "api_key" to "test_key_12345",
        "env" to "test",
        "log_level" to "debug",
        "max_retries" to "3"
    )

    /**
     * Crea un archivo param.env con parámetros personalizados.
     */
    fun createCustomParamEnvFile(
        context: Context,
        urlApi: String,
        timeoutMs: String = "30000",
        merchantId: String = "",
        terminalId: String = "",
        apiKey: String = "",
        env: String = "prod",
        additionalParams: Map<String, String> = emptyMap()
    ): Boolean {
        val params = mutableMapOf(
            "url_api" to urlApi,
            "timeout_ms" to timeoutMs,
            "env" to env
        )

        if (merchantId.isNotBlank()) params["merchant_id"] = merchantId
        if (terminalId.isNotBlank()) params["terminal_id"] = terminalId
        if (apiKey.isNotBlank()) params["api_key"] = apiKey

        params.putAll(additionalParams)

        return createSampleParamEnvFile(context, params)
    }
}
