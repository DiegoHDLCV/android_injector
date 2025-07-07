package com.example.communication.libraries.newpos.wrapper

import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.pos.device.uart.SerialPort
import java.io.IOException

class NewposComController(private var serialPort: SerialPort?) : IComController {
    private val TAG = "NewposComControllerWrapper"

    override fun open(): Int {
        return if (serialPort != null) {
            Log.d(TAG, "open: SerialPort opened successfully")
            0
        } else {
            Log.e(TAG, "open: Failed to open SerialPort")
            -1
        }
    }

    override fun close(): Int {
        serialPort = null
        Log.d(TAG, "close: SerialPort closed")
        return 0
    }

    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        Log.d(TAG, "init: SerialPort initialized with default config")
        return 0
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        return try {
            serialPort?.outputStream?.apply {
                write(data)
                flush()
            }
            Log.d(TAG, "write: Data written successfully")
            0
        } catch (e: IOException) {
            Log.e(TAG, "write: Error writing to SerialPort - ${e.message}")
            -1
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        val inputStream = serialPort?.inputStream ?: return -1
        val readBytes = inputStream.read(buffer)
        return if (readBytes >= 0) readBytes else 0
    }
}