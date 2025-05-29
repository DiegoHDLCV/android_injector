package com.example.communication.libraries.urovo.wrapper

import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.urovo.serial.utils.SerialPortListener
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min
import com.urovo.serial.utils.SerialPortTool     // La clase principal a usar


class UrovoComController (
    private val initialPortPaths: List<String>? // Lista de rutas a probar
    ) : IComController {

        private lateinit var serialPortTool: SerialPortTool
        private var isOpen: Boolean = false
        private var storedBaudRate: EnumCommConfBaudRate = EnumCommConfBaudRate.BPS_9600 // Default

        private val receivedDataQueue = LinkedBlockingQueue<Result<ByteArray>>()

        companion object {
            private const val TAG = "UrovoComControllerW"
            private const val SUCCESS = 0
            private const val ERROR_ALREADY_OPEN = -1
            // private const val ERROR_INIT_PARAMS_UNSUPPORTED = -2 // Eliminado, solo advertencia
            private const val ERROR_OPEN_FAILED = -3
            private const val ERROR_NOT_OPEN = -4
            private const val ERROR_WRITE_FAILED = -5
            private const val ERROR_READ_TIMEOUT = -6
            private const val ERROR_READ_LISTENER_FAILED = -7
            private const val ERROR_CLOSE_FAILED = -8
            private const val ERROR_GENERAL_EXCEPTION = -99
            private const val ERROR_NOT_INITIALIZED = -100 // Nuevo error
        }

        private fun mapBaudRateToInt(baudRate: EnumCommConfBaudRate): Int {
            return when (baudRate) {
                EnumCommConfBaudRate.BPS_1200 -> 1200
                EnumCommConfBaudRate.BPS_2400 -> 2400
                EnumCommConfBaudRate.BPS_4800 -> 4800
                EnumCommConfBaudRate.BPS_9600 -> 9600
                EnumCommConfBaudRate.BPS_19200 -> 19200
                EnumCommConfBaudRate.BPS_38400 -> 38400
                EnumCommConfBaudRate.BPS_57600 -> 57600
                EnumCommConfBaudRate.BPS_115200 -> 115200
                // else -> throw IllegalArgumentException("Unsupported baud rate: $baudRate") // Opcional
            }
        }

        private val internalSerialListener = object : SerialPortListener {
            override fun onReceive(data: ByteArray?) {
                if (data != null) {
                    Log.d(TAG, "Listener onReceive: ${data.size} bytes from ${serialPortTool.pathName}")
                    receivedDataQueue.offer(Result.success(data))
                } else {
                    Log.w(TAG, "Listener onReceive: received null data from ${serialPortTool.pathName}")
                    receivedDataQueue.offer(Result.failure(IllegalStateException("Listener received null data.")))
                }
            }

            override fun onFail(code: String?, msg: String?) {
                Log.e(TAG, "Listener onFail from ${serialPortTool.pathName}: Code=$code, Msg=$msg")
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
        if (isOpen) {
            Log.w(TAG, "Port is already open. Close before re-initializing.")
            return ERROR_ALREADY_OPEN
        }

        this.storedBaudRate = baudRate
        // val targetSpeed = mapBaudRateToInt(baudRate) // No se usa aquí

        serialPortTool = SerialPortTool() // Se instancia aquí
        Log.d(TAG, "SerialPortTool instance created. Stored baudRate: $baudRate. Initial port paths: $initialPortPaths")

        if (parity != EnumCommConfParity.NOPAR || dataBits != EnumCommConfDataBits.DB_8) {
            Log.w(TAG, "Warning: SerialPortTool typically defaults to Parity=NONE, DataBits=8.")
        }
        Log.i(TAG, "UrovoComController initialized. Target baud rate: $baudRate. Configuration will be fully applied on open().")
        return SUCCESS
    }

    override fun open(): Int {
        if (!::serialPortTool.isInitialized) {
            Log.e(TAG, "SerialPortTool not prepared in init(). Call init() first.")
            return ERROR_NOT_INITIALIZED
        }
        if (isOpen) {
            Log.w(TAG, "Port is already open.")
            return SUCCESS
        }

        try {
            serialPortTool.setOnListener(internalSerialListener)
            val speedToUse = mapBaudRateToInt(this.storedBaudRate)
            val status: Int

            // --- MODIFICACIÓN AQUÍ ---
            // Usa la lista de rutas proporcionada al constructor, o una lista vacía si es null.
            val pathsToTry = this.initialPortPaths ?: emptyList()
            // --- FIN MODIFICACIÓN ---

            Log.d(TAG, "Attempting to open port. Paths to try: $pathsToTry, Speed: $speedToUse")
            if (pathsToTry.isEmpty()) {
                Log.w(TAG, "No preferred port paths provided to SerialPortTool. Attempting open with empty list (SDK default behavior).")
            }
            status = serialPortTool.openSerialPort(pathsToTry, speedToUse)

            return if (status == 0) {
                isOpen = true
                Log.i(TAG, "Serial port opened successfully. Actual Path: '${serialPortTool.pathName}', Actual Speed: ${serialPortTool.currentSpeed}")
                SUCCESS
            } else {
                Log.e(TAG, "Failed to open serial port. SerialPortTool status: $status. Attempted paths: $pathsToTry, Speed: $speedToUse")
                ERROR_OPEN_FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during serial port open.", e)
            isOpen = false
            return ERROR_GENERAL_EXCEPTION
        }
    }

        override fun close(): Int {
            if (!isOpen) {
                Log.w(TAG, "Port is not open or already closed.")
                return SUCCESS // Idempotente
            }
            if (!::serialPortTool.isInitialized) {
                Log.e(TAG, "SerialPortTool not initialized, cannot close (though marked open). This indicates an inconsistent state.")
                isOpen = false // Corregir estado
                return ERROR_NOT_INITIALIZED
            }

            return try {
                val status = serialPortTool.close() // Devuelve 0 para éxito, -1 para error
                isOpen = false
                receivedDataQueue.clear() // Limpiar datos pendientes
                if (status == 0) {
                    Log.i(TAG, "Serial port closed successfully.")
                    SUCCESS
                } else {
                    Log.w(TAG, "SerialPortTool close reported status: $status (expected 0 for success, -1 for error).")
                    ERROR_CLOSE_FAILED
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during serial port close.", e)
                isOpen = false // Aún marcar como cerrado
                ERROR_GENERAL_EXCEPTION
            }
        }

        override fun write(data: ByteArray, timeout: Int): Int {
            if (!isOpen || !::serialPortTool.isInitialized) {
                Log.e(TAG, "Port not open or SerialPortTool not initialized.")
                return ERROR_NOT_OPEN
            }

            if (timeout > 0) {
                Log.d(TAG, "Write timeout ($timeout ms) requested, but SerialPortTool's sendBuffData does not use it.")
            }

            return try {
                // sendBuffData devuelve 0 (éxito), 5 (hilo de lectura no vivo), 6 (IOException)
                val status = serialPortTool.sendBuffData(data, data.size)
                when (status) {
                    0 -> {
                        Log.d(TAG, "Wrote ${data.size} bytes successfully to '${serialPortTool.pathName}'.")
                        data.size // ICommController espera bytes escritos o error negativo
                    }
                    5 -> {
                        Log.e(TAG, "Write failed to '${serialPortTool.pathName}': ReadThread not alive (SerialPortTool status 5).")
                        ERROR_WRITE_FAILED
                    }
                    6 -> {
                        Log.e(TAG, "Write failed to '${serialPortTool.pathName}': IOException (SerialPortTool status 6).")
                        ERROR_WRITE_FAILED
                    }
                    else -> {
                        Log.e(TAG, "Write failed to '${serialPortTool.pathName}': Unknown SerialPortTool status $status.")
                        ERROR_WRITE_FAILED
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during write to '${serialPortTool.pathName}'.", e)
                ERROR_GENERAL_EXCEPTION
            }
        }

        override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
            if (!isOpen || !::serialPortTool.isInitialized) {
                Log.e(TAG, "Port not open or SerialPortTool not initialized.")
                return ERROR_NOT_OPEN
            }
            if (buffer.isEmpty()) {
                Log.w(TAG, "Read buffer for '${serialPortTool.pathName}' is empty.")
                return 0
            }
            if (expectedLen <= 0) {
                Log.w(TAG, "Expected length for read from '${serialPortTool.pathName}' is non-positive: $expectedLen.")
                return 0
            }

            Log.d(TAG, "Attempting to read $expectedLen bytes from '${serialPortTool.pathName}' with timeout $timeout ms.")
            // Limpiar cualquier dato/error viejo de la cola antes de la nueva operación de lectura.
            // Esto asegura que solo obtenemos datos frescos para esta llamada.
            receivedDataQueue.clear()

            try {
                val result = receivedDataQueue.poll(timeout.toLong(), TimeUnit.MILLISECONDS)

                if (result == null) {
                    Log.w(TAG, "Read from '${serialPortTool.pathName}' timed out after $timeout ms.")
                    return ERROR_READ_TIMEOUT
                }

                return result.fold(
                    onSuccess = { receivedBytes ->
                        val bytesToCopy = min(receivedBytes.size, min(expectedLen, buffer.size))
                        System.arraycopy(receivedBytes, 0, buffer, 0, bytesToCopy)
                        Log.d(TAG, "Read $bytesToCopy bytes from queue (received: ${receivedBytes.size}, expected: $expectedLen, buffer: ${buffer.size}).")
                        if (bytesToCopy < receivedBytes.size) {
                            Log.w(TAG, "More bytes received (${receivedBytes.size}) than could be copied ($bytesToCopy) due to expectedLen/buffer constraints. Excess data from this packet is discarded for this readData() call.")
                        }
                        bytesToCopy
                    },
                    onFailure = { error ->
                        if (error is SerialPortToolException) {
                            Log.e(TAG, "Read from '${serialPortTool.pathName}' failed due to SerialPortTool listener error: Code=${error.errorCode}, Msg=${error.errorMessage}", error)
                        } else {
                            Log.e(TAG, "Read from '${serialPortTool.pathName}' failed due to an unexpected listener error.", error)
                        }
                        ERROR_READ_LISTENER_FAILED
                    }
                )
            } catch (e: InterruptedException) {
                Log.w(TAG, "Read operation from '${serialPortTool.pathName}' interrupted.", e)
                Thread.currentThread().interrupt() // Restablecer el estado interrumpido
                return ERROR_GENERAL_EXCEPTION
            }
        }
    }