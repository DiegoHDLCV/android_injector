package com.vigatec.manufacturer.libraries.newpos.controller.hardware

import android.util.Log
import com.vigatec.manufacturer.base.controllers.hardware.IBeeperController

class NewposBeeperController : IBeeperController {
    private val TAG = "NewposBeeperController"

    override fun beep(duration: Int, frequency: Int) {
        Log.d(TAG, "beep: duration=$duration frequency=$frequency")
    }

    override fun beepMultiple(count: Int, duration: Int, interval: Int) {
        Log.d(TAG, "beepMultiple: count=$count duration=$duration interval=$interval")
    }
}
