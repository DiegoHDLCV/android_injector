package com.vigatec.injector.viewmodel

import android.util.Log

/**
 * Extensión para ejecutar un bloque de código con logging automático y manejo de errores
 */
inline fun <T> executeWithLogging(
    tag: String,
    operation: String,
    block: () -> T
): Result<T> {
    return try {
        Log.d(tag, "$operation - Iniciando")
        val result = block()
        Log.d(tag, "$operation - Completado exitosamente")
        Result.success(result)
    } catch (e: Exception) {
        Log.e(tag, "❌ Error en $operation", e)
        e.printStackTrace()
        Result.failure(e)
    }
}

/**
 * Extensión para ejecutar un bloque de código suspendido con logging y manejo de errores
 */suspend inline fun <T> executeSuspendWithLogging(
    tag: String,
    operation: String,
    block: suspend () -> T
): Result<T> {
    return try {
        Log.d(tag, "$operation - Iniciando")
        val result = block()
        Log.d(tag, "$operation - Completado exitosamente")
        Result.success(result)
    } catch (e: Exception) {
        Log.e(tag, "❌ Error en $operation", e)
        e.printStackTrace()
        Result.failure(e)
    }
}

/**
 * Extensión para ejecutar con logging y actualizar estado en caso de error
 */inline fun <T> executeWithStateUpdate(
    tag: String,
    operation: String,
    onError: (String) -> Unit,
    block: () -> T
): Result<T> {
    return try {
        Log.d(tag, "$operation - Iniciando")
        val result = block()
        Log.d(tag, "$operation - Completado exitosamente")
        Result.success(result)
    } catch (e: Exception) {
        val errorMessage = "Error en $operation: ${e.message}"
        Log.e(tag, "❌ $errorMessage", e)
        onError(errorMessage)
        e.printStackTrace()
        Result.failure(e)
    }
}
