package com.example.communication.libraries.newpos

import android.app.Application
import android.util.Log
import com.example.communication.base.IComController
import com.example.communication.base.controllers.manager.ICommunicationManager
import com.example.communication.libraries.newpos.wrapper.NewposComController
import com.pos.device.uart.SerialPort

object NewposCommunicationManager: ICommunicationManager {
    private const val TAG = "NewposUsbCommunication"
    private const val PORT_TTYUSB0 = 7
    private const val PORT_TTYACM0 = 8
    private const val PORT_TTYGS0 = 6
    private const val DEFAULT_BAUDRATE = "115200,8,n,1"

    var serialPort:  SerialPort? = null
    override suspend fun initialize(context: Application) {
        try {
            serialPort = SerialPort.getInstance(SerialPort.DEFAULT_CFG, PORT_TTYUSB0)
            if (serialPort == null) {
                serialPort = SerialPort.getInstance(SerialPort.DEFAULT_CFG, PORT_TTYACM0)
            }
            if (serialPort == null) {
                serialPort = SerialPort.getInstance(SerialPort.DEFAULT_CFG, PORT_TTYGS0)
            }

            if (serialPort != null) {
                Log.d(TAG, "initialize: SerialPort instance created successfully")
            } else {
                Log.e(TAG, "initialize: Failed to initialize SerialPort")
            }
        } catch (e: Exception) {
            Log.e(TAG, "initialize: Exception while initializing SerialPort - ${e.message}")
        }
    }

    override fun getComController(): IComController {
        return NewposComController(serialPort)
    }
}