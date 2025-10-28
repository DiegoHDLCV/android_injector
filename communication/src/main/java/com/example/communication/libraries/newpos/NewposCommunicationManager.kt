package com.example.communication.libraries.newpos

import android.app.Application
import android.content.Context
import com.example.communication.base.IComController
import com.example.communication.base.controllers.manager.ICommunicationManager
import com.example.communication.libraries.newpos.wrapper.NewposComController

object NewposCommunicationManager: ICommunicationManager {

    private var appContext: Context? = null

    override suspend fun initialize(context: Application) {
        appContext = context
    }

    override fun getComController(): IComController {
        return NewposComController(appContext)
    }

    override fun release() {
        appContext = null
    }

}