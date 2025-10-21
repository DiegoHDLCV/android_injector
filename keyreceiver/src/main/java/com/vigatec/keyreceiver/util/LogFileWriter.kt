package com.vigatec.keyreceiver.util

import android.content.Context
import android.os.Environment
import com.example.communication.polling.CommLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Escribe los CommLogs en un archivo en almacenamiento externo privado de la app.
 * Ruta sugerida: Android/data/<package>/files/Download/comm-logs-YYYYMMDD.txt
 */
object LogFileWriter {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun start(context: Context) {
        if (job?.isActive == true) return
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        job = scope.launch {
            CommLog.events.collectLatest { e ->
                try {
                    val day = dateFmt.format(Date(e.timestampMs))
                    val file = File(baseDir, "comm-logs-$day.txt")
                    val time = timeFmt.format(Date(e.timestampMs))
                    file.appendText("$time [${e.level}] ${e.tag}: ${e.message}\n")
                } catch (_: Exception) { }
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}



