package com.vigatec.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

object FilesUtils {

    private val TAG = "Utils"

    /**
     * Copies an asset file to the app's internal storage if it doesn't already exist.
     */
    fun copyAssetIfNeeded(context: Context, filename: String) {
        val emvDir = context.filesDir.apply { if (!exists()) mkdirs() }
        val outFile = File(emvDir, filename)
        if (outFile.exists()) {
            return
        }
        try {
            context.assets.open(filename).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "No se pudo copiar '$filename': no existe en assets", e)
        }
    }
}