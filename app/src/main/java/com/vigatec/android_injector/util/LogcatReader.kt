package com.vigatec.android_injector.util

import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Lector simple de logcat para SOLO el proceso actual (no requiere READ_LOGS).
 * Usa: logcat -v time --pid <pid>
 */
object LogcatReader {
    private const val MAX_LINES = 400
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                // -v time para timestamp; --pid para filtrar por este proceso
                val pid = Process.myPid().toString()
                val process = ProcessBuilder("logcat", "-v", "time", "--pid", pid)
                    .redirectErrorStream(true)
                    .start()

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (isActive) {
                        val l = reader.readLine() ?: break
                        if (l.isEmpty()) continue
                        val next = (_lines.value + l.trim()).takeLast(MAX_LINES)
                        _lines.value = next
                    }
                }
            } catch (_: Exception) {
                // Silenciar: en algunos dispositivos logcat puede no estar disponible
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun clear() { _lines.value = emptyList() }
}


