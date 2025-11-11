package com.example.communication.libraries.newpos

import android.app.Application
import android.content.Context
import com.example.communication.base.IComController
import com.example.communication.base.controllers.manager.ICommunicationManager
import com.example.communication.libraries.newpos.wrapper.NewposComController

object NewposCommunicationManager: ICommunicationManager {

    private var appContext: Context? = null
    private const val TAG = "NewposCommManager"

    override suspend fun initialize(context: Application) {
        appContext = context
        android.util.Log.d(TAG, "✓ NewposCommunicationManager inicializado con contexto: ${context.packageName}")
    }

    override fun getComController(): IComController {
        val context = appContext
        if (context == null) {
            android.util.Log.w(TAG, "⚠️ getComController() llamado pero contexto es null. ¿Se llamó initialize()?")
        } else {
            android.util.Log.d(TAG, "✓ Creando NewposComController con contexto disponible")
        }
        return NewposComController(context)
    }

    override fun release() {
        appContext = null
    }

}