package com.vigatec.manufacturer.libraries.aisino

import android.app.Application
import android.util.Log
import com.vigatec.manufacturer.base.controllers.hardware.IBeeperController
import com.vigatec.manufacturer.base.controllers.hardware.IDeviceController
import com.vigatec.manufacturer.base.controllers.hardware.ILedController
import com.vigatec.manufacturer.base.controllers.hardware.IPrinterController
import com.vigatec.manufacturer.base.controllers.manager.IHardwareController
import com.vigatec.manufacturer.base.controllers.system.ISystemController
import com.vigatec.manufacturer.libraries.aisino.workflow.hardware.AisinoBeeperController
import com.vigatec.manufacturer.libraries.aisino.workflow.hardware.AisinoDeviceController
import com.vigatec.manufacturer.libraries.aisino.workflow.hardware.AisinoLedController
import com.vigatec.manufacturer.libraries.aisino.workflow.hardware.AisinoPrinterController
import com.vigatec.manufacturer.libraries.aisino.workflow.hardware.AisinoSystemController

object AisinoHardwareManager : IHardwareController {
    private val TAG = "AisinoSDKManager"
    private var application: Application? = null

    override fun initializeController(application: Application) {
        Log.d(TAG, "initializeController called - Aisino SDK initialization")
        this.application = application
    }

    override fun ledController(): ILedController {
        return AisinoLedController()
    }

    override fun beeperController(): IBeeperController {
        return AisinoBeeperController()
    }

    override fun printerController(): IPrinterController {
        return AisinoPrinterController(application)
    }

    override fun deviceController(): IDeviceController {
        return AisinoDeviceController()
    }

    override fun systemController(): ISystemController {
        return AisinoSystemController()
    }
}
