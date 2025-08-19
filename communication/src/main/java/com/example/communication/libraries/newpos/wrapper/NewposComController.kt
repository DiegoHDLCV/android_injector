package com.example.communication.libraries.newpos.wrapper

import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.pos.device.uart.SerialPort
import java.io.IOException

class NewposComController : IComController {
    private val TAG = "NewposComController"

    private var serialPort: SerialPort? = null

    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        try {
            serialPort = SerialPort.getInstance(SerialPort.DEFAULT_CFG, 7) // ttyUSB0
            if (serialPort == null) {
                serialPort = SerialPort.getInstance(SerialPort.DEFAULT_CFG, 8) // ttyACM0
            }
            if (serialPort == null) {
                serialPort = SerialPort.getInstance(SerialPort.DEFAULT_CFG, 6) // ttyGS0
            }

            return if (serialPort != null) {
                Log.d(TAG, "init: SerialPort instance created successfully")
                0
            } else {
                Log.e(TAG, "init: Failed to initialize SerialPort")
                -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "init: Exception while initializing SerialPort - ${e.message}")
            return -1
        }
    }

    override fun open(): Int {
        return if (serialPort != null) {
            Log.d(TAG, "open: SerialPort opened successfully")
            0
        } else {
            Log.e(TAG, "open: Failed to open SerialPort, call init() first")
            -1
        }
    }

    override fun close(): Int {
        return try {
            serialPort?.release()
            serialPort = null
            Log.d(TAG, "close: SerialPort closed successfully")
            0
        } catch (e: Exception) {
            Log.e(TAG, "close: Error closing SerialPort - ${e.message}")
            -1
        }
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        return try {
            serialPort?.outputStream?.apply {
                write(data)
                flush()
            }
            Log.d(TAG, "write: Data written successfully")
            data.size
        } catch (e: IOException) {
            Log.e(TAG, "write: Error writing to SerialPort - ${e.message}")
            -1
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        return try {
            val inputStream = serialPort?.inputStream ?: return -1
            val readBytes = inputStream.read(buffer)
            if (readBytes >= 0) readBytes else 0
        } catch (e: IOException) {
            Log.e(TAG, "readData: Error reading from SerialPort - ${e.message}")
            -1
        }
    }
}