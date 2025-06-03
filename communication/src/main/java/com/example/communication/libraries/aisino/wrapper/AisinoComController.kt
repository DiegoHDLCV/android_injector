package com.example.communication.libraries.aisino.wrapper

import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.vanstone.trans.api.Rs232Api // Importa la Rs232Api de Vanstone

class AisinoComController(private val comport: Int = 0) : IComController { // `comport` puede ser 0 o 1

    companion object {
        private const val TAG = "AisinoComController"
        private const val AISINO_SUCCESS = 0
        private const val AISINO_ERROR = -1

        // Errores de IComController
        private const val SUCCESS = 0
        private const val ERROR_ALREADY_OPEN = -1
        // const val ERROR_INIT_PARAMS_UNSUPPORTED = -2 // (Se manejará con advertencia)
        private const val ERROR_OPEN_FAILED = -3
        private const val ERROR_NOT_OPEN = -4
        private const val ERROR_WRITE_FAILED = -5
        private const val ERROR_READ_TIMEOUT_OR_FAILURE = -6 // Rs232Api.PortRecv_Api devuelve -1 para error o timeout
        // private const val ERROR_READ_LISTENER_FAILED = -7 // No aplica, la API es bloqueante
        private const val ERROR_CLOSE_FAILED = -8
        private const val ERROR_GENERAL_EXCEPTION = -99
        private const val ERROR_SET_BAUD_FAILED = -10 // Nuevo error específico
    }

    private var isOpen: Boolean = false
    private var storedBaudRate: Int = 9600
    private var storedDataBits: Int = 8
    private var storedParity: Int = 0 // 0 para NOPAR es común
    private var storedStopBits: Int = 1 // 1 es común

    private fun mapBaudRate(baudRate: EnumCommConfBaudRate): Int {
        return when (baudRate) {
            EnumCommConfBaudRate.BPS_1200 -> 1200
            EnumCommConfBaudRate.BPS_2400 -> 2400
            EnumCommConfBaudRate.BPS_4800 -> 4800
            EnumCommConfBaudRate.BPS_9600 -> 9600
            EnumCommConfBaudRate.BPS_19200 -> 19200
            EnumCommConfBaudRate.BPS_38400 -> 38400
            EnumCommConfBaudRate.BPS_57600 -> 57600
            EnumCommConfBaudRate.BPS_115200 -> 115200
        }
    }

    private fun mapDataBits(dataBits: EnumCommConfDataBits): Int {
        return when (dataBits) {
            EnumCommConfDataBits.DB_7 -> 7
            EnumCommConfDataBits.DB_8 -> 8
        }
    }

    private fun mapParity(parity: EnumCommConfParity): Int {
        return when (parity) {
            EnumCommConfParity.NOPAR -> 0 // Asumiendo 0 para sin paridad
            EnumCommConfParity.EVEN -> 2 // Suposición común, verificar con doc de Vanstone si es diferente
            EnumCommConfParity.ODD -> 1 // Suposición común, verificar con doc de Vanstone si es diferente
        }
    }

    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        if (isOpen) {
            Log.w(TAG, "Port $comport is already open. Close before re-initializing.")
            // Considerar si devolver error o simplemente permitir reconfiguración de parámetros almacenados
        }
        this.storedBaudRate = mapBaudRate(baudRate)
        this.storedDataBits = mapDataBits(dataBits)
        this.storedParity = mapParity(parity)
        // stopBits se asume 1 por defecto para Rs232Api, si es configurable se agregaría

        Log.i(TAG, "AisinoComController for port $comport initialized with Baud: $storedBaudRate, DataBits: $storedDataBits, Parity: $storedParity. Configuration will be applied on open().")
        // La Rs232Api no tiene un método init separado de open/setBaud.
        // La inicialización global del SDK de Aisino (SystemApi.SystemInit_Api) se asume hecha.
        return SUCCESS
    }

    override fun open(): Int {
        if (isOpen) {
            Log.w(TAG, "Port $comport is already open.")
            return SUCCESS // O ERROR_ALREADY_OPEN si se prefiere ser estricto
        }
        try {
            Log.d(TAG, "Attempting to open port $comport...")
            var result = Rs232Api.PortOpen_Api(comport) //
            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "Failed to open port $comport. Aisino Error Code: $result")
                return ERROR_OPEN_FAILED
            }
            Log.i(TAG, "Port $comport opened successfully.")

            // Resetear el puerto después de abrirlo puede ser una buena práctica
            Rs232Api.PortReset_Api(comport)
            Log.d(TAG, "Port $comport reset.")

            Log.d(TAG, "Setting baud rate for port $comport: Baud=$storedBaudRate, DataBits=$storedDataBits, Parity=$storedParity, StopBits=$storedStopBits")
            result = Rs232Api.PortSetBaud_Api(comport, storedBaudRate, storedDataBits, storedParity, storedStopBits) //
            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "Failed to set baud rate for port $comport. Aisino Error Code: $result")
                Rs232Api.PortClose_Api(comport) // Intentar cerrar si falló la configuración
                return ERROR_SET_BAUD_FAILED
            }
            Log.i(TAG, "Baud rate set successfully for port $comport.")

            isOpen = true
            return SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Exception during port $comport open or configuration.", e)
            isOpen = false
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun close(): Int {
        if (!isOpen) {
            Log.w(TAG, "Port $comport is not open or already closed.")
            return SUCCESS
        }
        try {
            Log.d(TAG, "Closing port $comport...")
            val result = Rs232Api.PortClose_Api(comport) //
            isOpen = false // Marcar como cerrado independientemente del resultado de la API
            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "Failed to close port $comport. Aisino Error Code: $result")
                return ERROR_CLOSE_FAILED
            }
            Log.i(TAG, "Port $comport closed successfully.")
            return SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Exception during port $comport close.", e)
            isOpen = false
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun write(data: ByteArray, timeout: Int): Int { // El timeout no es usado por Rs232Api.PortSends_Api
        if (!isOpen) {
            Log.e(TAG, "Port $comport not open for writing.")
            return ERROR_NOT_OPEN
        }
        if (data.isEmpty()) {
            Log.w(TAG, "Write data for port $comport is empty.")
            return 0
        }

        try {
            Log.d(TAG, "Writing ${data.size} bytes to port $comport...")
            // Rs232Api.PortSends_Api devuelve 0 para éxito, -1 para error.
            // IComController espera el número de bytes escritos o un error negativo.
            val result = Rs232Api.PortSends_Api(comport, data, data.size) //
            if (result == AISINO_SUCCESS) {
                Log.d(TAG, "Successfully wrote ${data.size} bytes to port $comport.")
                return data.size // Devolver número de bytes escritos en éxito
            } else {
                Log.e(TAG, "Write failed to port $comport. Aisino Error Code: $result")
                return ERROR_WRITE_FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during write to port $comport.", e)
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        if (!isOpen) {
            Log.e(TAG, "Port $comport not open for reading.")
            return ERROR_NOT_OPEN
        }
        if (buffer.isEmpty() || expectedLen <= 0) {
            Log.w(TAG, "Read buffer for port $comport is empty or expectedLen is invalid.")
            return 0
        }

        try {
            Log.d(TAG, "Attempting to read $expectedLen bytes from port $comport with timeout $timeout ms.")
            // Rs232Api.PortRecv_Api devuelve -1 para error/timeout, o la longitud de los datos recibidos.
            val bytesRead = Rs232Api.PortRecv_Api(comport, buffer, expectedLen, timeout) //
            if (bytesRead < 0) { // -1 indica error o timeout
                Log.w(TAG, "Read from port $comport failed or timed out. Aisino Code: $bytesRead")
                return ERROR_READ_TIMEOUT_OR_FAILURE
            }
            Log.d(TAG, "Read $bytesRead bytes from port $comport.")
            return bytesRead
        } catch (e: Exception) {
            Log.e(TAG, "Exception during read from port $comport.", e)
            return ERROR_GENERAL_EXCEPTION
        }
    }
}