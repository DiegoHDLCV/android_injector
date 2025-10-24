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
            Log.w(TAG, "⚠️ Puerto $comport ya abierto al inicializar")
        }
        this.storedBaudRate = mapBaudRate(baudRate)
        this.storedDataBits = mapDataBits(dataBits)
        this.storedParity = mapParity(parity)
        Log.d(TAG, "Init puerto $comport: ${storedBaudRate}bps ${storedDataBits}N${storedStopBits}")
        return SUCCESS
    }

    override fun open(): Int {
        if (isOpen) {
            Log.d(TAG, "Puerto $comport ya abierto")
            return SUCCESS
        }

        try {
            var result = Rs232Api.PortOpen_Api(comport)
            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "❌ Error al abrir puerto $comport: $result")
                return ERROR_OPEN_FAILED
            }

            Rs232Api.PortReset_Api(comport)
            result = Rs232Api.PortSetBaud_Api(comport, storedBaudRate, storedDataBits, storedParity, storedStopBits)

            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "❌ Error al configurar baud puerto $comport: $result")
                Rs232Api.PortClose_Api(comport)
                return ERROR_SET_BAUD_FAILED
            }

            isOpen = true
            Log.i(TAG, "✓ Puerto $comport abierto (${storedBaudRate}bps)")
            return SUCCESS

        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al abrir puerto $comport: ${e.message}")
            isOpen = false
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun close(): Int {
        if (!isOpen) {
            return SUCCESS
        }

        try {
            val result = Rs232Api.PortClose_Api(comport)
            isOpen = false

            if (result != AISINO_SUCCESS) {
                Log.w(TAG, "⚠️ Error al cerrar puerto $comport: $result")
                return ERROR_CLOSE_FAILED
            }

            Log.d(TAG, "✓ Puerto $comport cerrado")
            return SUCCESS

        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción al cerrar puerto $comport: ${e.message}")
            isOpen = false
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        if (!isOpen) {
            Log.e(TAG, "❌ Puerto $comport no abierto para escritura")
            return ERROR_NOT_OPEN
        }
        if (data.isEmpty()) {
            return 0
        }

        try {
            val result = Rs232Api.PortSends_Api(comport, data, data.size)
            if (result == AISINO_SUCCESS) {
                Log.i(TAG, "📤 TX puerto $comport: ${data.size} bytes")
                return data.size
            } else {
                Log.e(TAG, "❌ Error TX puerto $comport: $result")
                return ERROR_WRITE_FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción TX puerto $comport: ${e.message}")
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        if (!isOpen) {
            Log.e(TAG, "❌ Puerto $comport no abierto para lectura")
            return ERROR_NOT_OPEN
        }
        if (buffer.isEmpty() || expectedLen <= 0) {
            return 0
        }

        try {
            val bytesRead = Rs232Api.PortRecv_Api(comport, buffer, expectedLen, timeout)

            if (bytesRead < 0) {
                return ERROR_READ_TIMEOUT_OR_FAILURE
            }

            if (bytesRead > 0) {
                val hexData = buffer.take(bytesRead).joinToString("") { "%02X".format(it) }
                Log.i(TAG, "📥 RX puerto $comport: $bytesRead bytes - $hexData")
            }

            return bytesRead

        } catch (e: Exception) {
            Log.e(TAG, "❌ Excepción RX puerto $comport: ${e.message}")
            return ERROR_GENERAL_EXCEPTION
        }
    }
}