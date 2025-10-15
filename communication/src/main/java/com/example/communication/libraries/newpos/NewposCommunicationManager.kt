package com.example.communication.libraries.newpos

import android.app.Application
import com.example.communication.base.IComController
import com.example.communication.base.controllers.manager.ICommunicationManager
import com.example.communication.libraries.newpos.wrapper.NewposComController

object NewposCommunicationManager: ICommunicationManager {

    override suspend fun initialize(context: Application) {
        // No-op
    }

    override fun getComController(): IComController {
        return NewposComController()
    }

    override fun release() {
        // No-op
    }

}