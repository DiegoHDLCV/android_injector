package com.vigatec.injector.util

import androidx.compose.runtime.*

/**
 * Debouncer para navegación que previene múltiples clics rápidos.
 * 
 * @param delayMillis Tiempo en milisegundos que debe pasar entre clics (default 500ms)
 */
class NavigationDebouncer(private val delayMillis: Long = 500L) {
    private var lastClickTime = 0L

    /**
     * Ejecuta la acción solo si ha pasado suficiente tiempo desde el último clic.
     * 
     * @param action La acción a ejecutar (ej: navegación)
     * @return true si la acción se ejecutó, false si fue bloqueada
     */
    fun onClick(action: () -> Unit): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastClick = currentTime - lastClickTime
        
        return if (timeSinceLastClick >= delayMillis) {
            lastClickTime = currentTime
            action()
            true
        } else {
            // Clic bloqueado, muy rápido
            false
        }
    }
}

/**
 * Composable que recuerda una instancia de NavigationDebouncer.
 * Uso: val debouncer = rememberNavigationDebouncer()
 */
@Composable
fun rememberNavigationDebouncer(delayMillis: Long = 500L): NavigationDebouncer {
    return remember { NavigationDebouncer(delayMillis) }
}
