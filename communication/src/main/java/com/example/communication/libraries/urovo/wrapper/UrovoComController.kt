package com.example.communication.libraries.urovo.wrapper

import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.urovo.serial.utils.SerialPortListener
import com.urovo.serial.utils.SerialPortTool
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min

class UrovoComController(
    private val initialPortPaths: List<String>?
) : IComController {

    // Usamos un enum para un estado más claro que un simple booleano
    private enum class PortState {
        CLOSED,
        OPEN,
        ERROR
    }

    @Volatile
    private var portState: PortState = PortState.CLOSED

    private var serialPortTool: SerialPortTool? = null
    private var storedBaudRate: EnumCommConfBaudRate = EnumCommConfBaudRate.BPS_9600
    private val receivedDataQueue = LinkedBlockingQueue<Result<ByteArray>>()

    companion object {
        private const val TAG = "UrovoComControllerW"
        private const val SUCCESS = 0
        private const val ERROR_ALREADY_OPEN = -1
        private const val ERROR_OPEN_FAILED = -3
        private const val ERROR_NOT_OPEN = -4
        private const val ERROR_WRITE_FAILED = -5
        private const val ERROR_READ_TIMEOUT = -6
        private const val ERROR_READ_LISTENER_FAILED = -7
        private const val ERROR_CLOSE_FAILED = -8
        private const val ERROR_GENERAL_EXCEPTION = -99
    }

    private val internalSerialListener = object : SerialPortListener {
        override fun onReceive(data: ByteArray?) {
            if (data != null) {
                Log.d(TAG, "Listener onReceive: ${data.size} bytes from ${serialPortTool?.pathName}")
                receivedDataQueue.offer(Result.success(data))
            } else {
                Log.w(TAG, "Listener onReceive: received null data from ${serialPortTool?.pathName}")
                receivedDataQueue.offer(Result.failure(IllegalStateException("Listener received null data.")))
            }
        }

        override fun onFail(code: String?, msg: String?) {
            portState = PortState.ERROR // Marcar el puerto como en estado de error
            Log.e(TAG, "Listener onFail from ${serialPortTool?.pathName}: Code=$code, Msg=$msg")
            receivedDataQueue.offer(Result.failure(SerialPortToolException(code, msg)))
        }
    }

    class SerialPortToolException(val errorCode: String?, val errorMessage: String?) :
        Exception("SerialPortTool failed: Code=$errorCode, Message=$errorMessage")

    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        if (portState == PortState.OPEN) {
            Log.w(TAG, "Port is already open. Close before re-initializing.")
            return ERROR_ALREADY_OPEN
        }

        this.storedBaudRate = baudRate
        this.serialPortTool = SerialPortTool() // Crear nueva instancia en cada inicialización

        Log.i(TAG, "UrovoComController initialized. Target baud rate: $baudRate.")
        return SUCCESS
    }

    override fun open(): Int {
        if (portState == PortState.OPEN) {
            Log.w(TAG, "Port is already open.")
            return SUCCESS
        }

        val currentSerialTool = serialPortTool
            ?: return ERROR_GENERAL_EXCEPTION.also { Log.e(TAG, "SerialPortTool is null. Call init() first.") }

        return try {
            currentSerialTool.setOnListener(internalSerialListener)
            val speedToUse = mapBaudRateToInt(this.storedBaudRate)
            val pathsToTry = this.initialPortPaths ?: emptyList()

            Log.d(TAG, "Attempting to open port. Paths to try: $pathsToTry, Speed: $speedToUse")
            val status = currentSerialTool.openSerialPort(pathsToTry, speedToUse)

            if (status == 0) {
                portState = PortState.OPEN
                Log.i(TAG, "Serial port opened successfully. Path: '${currentSerialTool.pathName}', Speed: ${currentSerialTool.currentSpeed}")
                SUCCESS
            } else {
                portState = PortState.ERROR
                Log.e(TAG, "Failed to open serial port. Status: $status")
                ERROR_OPEN_FAILED
            }
        } catch (e: Exception) {
            portState = PortState.ERROR
            Log.e(TAG, "Exception during serial port open.", e)
            ERROR_GENERAL_EXCEPTION
        }
    }

    override fun close(): Int {
        if (portState == PortState.CLOSED) {
            return SUCCESS // Ya está cerrado
        }

        return try {
            serialPortTool?.close() // Llama a close sin importar el estado (OPEN o ERROR)
            Log.i(TAG, "Serial port closed successfully.")
            SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Exception during serial port close.", e)
            ERROR_CLOSE_FAILED
        } finally {
            // Asegurarse de que el estado y los recursos se limpien siempre
            portState = PortState.CLOSED
            receivedDataQueue.clear()
            serialPortTool = null
        }
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        if (portState != PortState.OPEN) {
            Log.e(TAG, "Port not open. Current state: $portState")
            return ERROR_NOT_OPEN
        }

        return try {
            val status = serialPortTool!!.sendBuffData(data, data.size)
            if (status == 0) data.size else ERROR_WRITE_FAILED
        } catch (e: Exception) {
            Log.e(TAG, "Exception during write.", e)
            portState = PortState.ERROR
            ERROR_GENERAL_EXCEPTION
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        if (portState != PortState.OPEN) {
            Log.e(TAG, "Port not open. Current state: $portState")
            return ERROR_NOT_OPEN
        }

        try {
            val result = receivedDataQueue.poll(timeout.toLong(), TimeUnit.MILLISECONDS)
                ?: return ERROR_READ_TIMEOUT.also { Log.w(TAG, "Read timed out after $timeout ms.") }

            return result.fold(
                onSuccess = { receivedBytes ->
                    val bytesToCopy = min(receivedBytes.size, min(expectedLen, buffer.size))
                    System.arraycopy(receivedBytes, 0, buffer, 0, bytesToCopy)
                    bytesToCopy
                },
                onFailure = { error ->
                    portState = PortState.ERROR
                    Log.e(TAG, "Read failed due to listener error.", error)
                    ERROR_READ_LISTENER_FAILED
                }
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            portState = PortState.ERROR
            Log.w(TAG, "Read operation interrupted.", e)
            return ERROR_GENERAL_EXCEPTION
        }
    }

    private fun mapBaudRateToInt(baudRate: EnumCommConfBaudRate): Int {
        return when (baudRate) {
            EnumCommConfBaudRate.BPS_9600 -> 9600
            // ... otros baud rates
            else -> 9600
        }
    }
}
