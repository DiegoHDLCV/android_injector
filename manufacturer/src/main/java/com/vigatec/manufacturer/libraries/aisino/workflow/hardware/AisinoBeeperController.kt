package com.vigatec.manufacturer.libraries.aisino.workflow.hardware

import android.util.Log
import com.vigatec.manufacturer.base.controllers.hardware.IBeeperController

class AisinoBeeperController : IBeeperController {
    private val TAG = "AisinoBeeperController"

    override fun beep(duration: Int, frequency: Int) {
        Log.d(TAG, "beep: duration=$duration frequency=$frequency")
    }

    override fun beepMultiple(count: Int, duration: Int, interval: Int) {
        Log.d(TAG, "beepMultiple: count=$count duration=$duration interval=$interval")
    }
}
