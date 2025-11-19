package com.vigatec.manufacturer.libraries.newpos

import android.app.Application
import com.vigatec.manufacturer.base.controllers.hardware.IBeeperController
import com.vigatec.manufacturer.base.controllers.hardware.IDeviceController
import com.vigatec.manufacturer.base.controllers.hardware.ILedController
import com.vigatec.manufacturer.base.controllers.hardware.IPrinterController
import com.vigatec.manufacturer.base.controllers.manager.IHardwareController
import com.vigatec.manufacturer.base.controllers.system.ISystemController
import com.vigatec.manufacturer.libraries.newpos.controller.hardware.NewposBeeperController
import com.vigatec.manufacturer.libraries.newpos.controller.hardware.NewposDeviceController
import com.vigatec.manufacturer.libraries.newpos.controller.hardware.NewposLedController
import com.vigatec.manufacturer.libraries.newpos.controller.hardware.NewposPrinterController
import com.vigatec.manufacturer.libraries.newpos.controller.hardware.NewposSystemController


object NewposHardwareManager : IHardwareController {

    private lateinit var context : Application

    /**
     * Inicialización principal de parametros
     */
    override fun initializeController(application: Application) {
        this.context = application
    }

    override fun ledController(): ILedController {
        return NewposLedController()
    }

    override fun beeperController(): IBeeperController {
        return NewposBeeperController()
    }

    override fun printerController(): IPrinterController {
        return NewposPrinterController()
    }

    override fun deviceController(): IDeviceController {
        return NewposDeviceController()
    }

    override fun systemController(): ISystemController {
        return NewposSystemController()
    }
}
