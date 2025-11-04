package com.vigatec.injector.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TimeoutState(
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val isActive: Boolean = false,
    val isWarning: Boolean = false,  // true cuando queda menos del 20% del tiempo
    val isExpired: Boolean = false
)

/**
 * Gestor de timeout para custodios en ceremonias de llaves.
 * Controla el contador regresivo y dispara acciones cuando el tiempo se agota.
 */
class CustodianTimeoutManager {

    companion object {
        private const val TAG = "CustodianTimeoutManager"

        // Porcentaje del tiempo total en el que se muestra la advertencia (20%)
        private const val WARNING_THRESHOLD_PERCENT = 0.2
    }

    private var timerJob: Job? = null

    private val _timeoutState = MutableStateFlow(TimeoutState())
    val timeoutState: StateFlow<TimeoutState> = _timeoutState.asStateFlow()

    /**
     * Inicia el timer de timeout para custodios
     * @param timeoutSeconds Tiempo total en segundos
     * @param scope Coroutine scope donde ejecutar el timer
     * @param onWarning Callback cuando se alcanza el umbral de advertencia
     * @param onExpired Callback cuando el tiempo se agota
     */
    fun startTimer(
        timeoutSeconds: Int,
        scope: CoroutineScope,
        onWarning: (remainingSeconds: Int) -> Unit = {},
        onExpired: () -> Unit = {}
    ) {
        // Cancelar timer anterior si existe
        stopTimer()

        val warningThreshold = (timeoutSeconds * WARNING_THRESHOLD_PERCENT).toInt()

        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Iniciando timeout de custodio")
        Log.d(TAG, "  - Tiempo total: $timeoutSeconds segundos (${timeoutSeconds / 60} minutos)")
        Log.d(TAG, "  - Umbral de advertencia: $warningThreshold segundos")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")

        _timeoutState.value = TimeoutState(
            totalSeconds = timeoutSeconds,
            remainingSeconds = timeoutSeconds,
            isActive = true,
            isWarning = false,
            isExpired = false
        )

        timerJob = scope.launch {
            for (remaining in timeoutSeconds downTo 0) {
                _timeoutState.value = TimeoutState(
                    totalSeconds = timeoutSeconds,
                    remainingSeconds = remaining,
                    isActive = true,
                    isWarning = remaining <= warningThreshold && remaining > 0,
                    isExpired = remaining == 0
                )

                // Registrar cuando se alcanza el umbral de advertencia
                if (remaining == warningThreshold) {
                    Log.w(TAG, "⚠️ Se alcanzó el umbral de advertencia: $warningThreshold segundos restantes")
                    logSessionTimeout("ADVERTENCIA_TIEMPO_AGOTANDOSE", remaining, timeoutSeconds)
                    onWarning(remaining)
                }

                // Registrar cuando el tiempo se agota
                if (remaining == 0) {
                    Log.e(TAG, "⏰ ¡El timeout de custodio ha expirado!")
                    logSessionTimeout("TIMEOUT_EXPIRADO", remaining, timeoutSeconds)
                    onExpired()
                    stopTimer()
                    return@launch
                }

                delay(1000) // Actualizar cada segundo
            }
        }
    }

    /**
     * Detiene el timer actual
     */
    fun stopTimer() {
        if (timerJob?.isActive == true) {
            timerJob?.cancel()
            Log.d(TAG, "Timer de timeout detenido")
        }

        _timeoutState.value = TimeoutState(
            totalSeconds = 0,
            remainingSeconds = 0,
            isActive = false,
            isWarning = false,
            isExpired = false
        )
    }

    /**
     * Pausa el timer (sin detenerlo completamente)
     */
    fun pauseTimer() {
        // Implementación futura si se necesita pausar
        Log.d(TAG, "Timer pausado")
    }

    /**
     * Reanuda el timer
     */
    fun resumeTimer() {
        // Implementación futura si se necesita reanudar
        Log.d(TAG, "Timer reanudado")
    }

    /**
     * Obtiene el tiempo restante formateado como MM:SS
     */
    fun getFormattedTime(): String {
        val remaining = _timeoutState.value.remainingSeconds
        val minutes = remaining / 60
        val seconds = remaining % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    /**
     * Registra la sesión de timeout en el log del sistema
     */
    private fun logSessionTimeout(
        event: String,
        remainingSeconds: Int,
        totalSeconds: Int
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val message = "[$timestamp] $event - Tiempo restante: $remainingSeconds seg / $totalSeconds seg totales"
        Log.d(TAG, message)
    }

    /**
     * Obtiene información del estado actual del timer
     */
    fun getStatusInfo(): String {
        val state = _timeoutState.value
        return buildString {
            append("Estado: ${if (state.isActive) "ACTIVO" else "INACTIVO"}\n")
            append("Tiempo total: ${state.totalSeconds} seg (${state.totalSeconds / 60} min)\n")
            append("Tiempo restante: ${state.remainingSeconds} seg\n")
            append("Advertencia: ${if (state.isWarning) "SÍ" else "NO"}\n")
            append("Expirado: ${if (state.isExpired) "SÍ" else "NO"}\n")
            append("Tiempo formateado: ${getFormattedTime()}")
        }
    }
}
