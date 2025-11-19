package com.vigatec.manufacturer.libraries.newpos.controller.hardware

import android.util.Log
import com.vigatec.manufacturer.base.controllers.hardware.ILedController

class NewposLedController : ILedController {
    private val TAG = "NewposLedController"

    override fun turnOn(ledType: String, color: Int) {
        Log.d(TAG, "turnOn: $ledType color=0x${Integer.toHexString(color)}")
    }

    override fun turnOff(ledType: String) {
        Log.d(TAG, "turnOff: $ledType")
    }

    override fun blink(ledType: String, durationMs: Int, intervalMs: Int) {
        Log.d(TAG, "blink: $ledType duration=$durationMs interval=$intervalMs")
    }
}
