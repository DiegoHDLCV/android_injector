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
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.d(TAG, "║ AISINO COM INIT - Puerto $comport")
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════")
        if (isOpen) {
            Log.w(TAG, "║ ⚠️  ADVERTENCIA: Puerto $comport ya está abierto")
            Log.w(TAG, "║     Se debe cerrar antes de reinicializar")
        }
        this.storedBaudRate = mapBaudRate(baudRate)
        this.storedDataBits = mapDataBits(dataBits)
        this.storedParity = mapParity(parity)

        Log.i(TAG, "║ ✓ Parámetros configurados:")
        Log.i(TAG, "║   • Baud Rate: $storedBaudRate bps")
        Log.i(TAG, "║   • Data Bits: $storedDataBits")
        Log.i(TAG, "║   • Parity: $storedParity")
        Log.i(TAG, "║   • Stop Bits: $storedStopBits (fijo)")
        Log.i(TAG, "║ ℹ️  Configuración será aplicada en open()")
        Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
        return SUCCESS
    }

    override fun open(): Int {
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.d(TAG, "║ AISINO COM OPEN - Puerto $comport")
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════")

        if (isOpen) {
            Log.w(TAG, "║ ⚠️  Puerto $comport ya está abierto, retornando SUCCESS")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            return SUCCESS
        }

        try {
            Log.i(TAG, "║ 🔌 PASO 1/4: Intentando abrir puerto $comport...")
            Log.i(TAG, "║     Llamando a Rs232Api.PortOpen_Api($comport)")

            var result = Rs232Api.PortOpen_Api(comport)

            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "║ ✗ FALLO al abrir puerto $comport")
                Log.e(TAG, "║   Código de error Aisino: $result")
                Log.e(TAG, "║   Posibles causas:")
                Log.e(TAG, "║   • Cable USB no conectado")
                Log.e(TAG, "║   • Puerto en uso por otra aplicación")
                Log.e(TAG, "║   • Permisos insuficientes")
                Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                return ERROR_OPEN_FAILED
            }
            Log.i(TAG, "║ ✓ Puerto $comport abierto exitosamente")

            Log.i(TAG, "║ 🔄 PASO 2/4: Reseteando puerto $comport...")
            Log.i(TAG, "║     Llamando a Rs232Api.PortReset_Api($comport)")
            Rs232Api.PortReset_Api(comport)
            Log.i(TAG, "║ ✓ Puerto $comport reseteado")

            Log.i(TAG, "║ ⚙️  PASO 3/4: Configurando parámetros de comunicación...")
            Log.i(TAG, "║     Baud: $storedBaudRate, Data: $storedDataBits, Parity: $storedParity, Stop: $storedStopBits")
            Log.i(TAG, "║     Llamando a Rs232Api.PortSetBaud_Api(...)")

            result = Rs232Api.PortSetBaud_Api(comport, storedBaudRate, storedDataBits, storedParity, storedStopBits)

            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "║ ✗ FALLO al configurar baud rate del puerto $comport")
                Log.e(TAG, "║   Código de error Aisino: $result")
                Log.e(TAG, "║ 🔒 Cerrando puerto debido al error...")
                Rs232Api.PortClose_Api(comport)
                Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                return ERROR_SET_BAUD_FAILED
            }
            Log.i(TAG, "║ ✓ Parámetros configurados correctamente")

            Log.i(TAG, "║ ✅ PASO 4/4: Puerto $comport LISTO PARA COMUNICACIÓN")
            isOpen = true
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            return SUCCESS

        } catch (e: Exception) {
            Log.e(TAG, "║ ❌ EXCEPCIÓN durante apertura del puerto $comport", e)
            Log.e(TAG, "║    Mensaje: ${e.message}")
            Log.e(TAG, "║    Stack: ${e.stackTraceToString().take(200)}")
            isOpen = false
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            return ERROR_GENERAL_EXCEPTION
        }
    }

    override fun close(): Int {
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.d(TAG, "║ AISINO COM CLOSE - Puerto $comport")
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════")

        if (!isOpen) {
            Log.w(TAG, "║ ⚠️  Puerto $comport no está abierto o ya fue cerrado")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            return SUCCESS
        }

        try {
            Log.i(TAG, "║ 🔒 Cerrando puerto $comport...")
            Log.i(TAG, "║    Llamando a Rs232Api.PortClose_Api($comport)")

            val result = Rs232Api.PortClose_Api(comport)
            isOpen = false

            if (result != AISINO_SUCCESS) {
                Log.e(TAG, "║ ✗ ADVERTENCIA: Error al cerrar puerto $comport")
                Log.e(TAG, "║   Código de error Aisino: $result")
                Log.e(TAG, "║   Puerto marcado como cerrado de todas formas")
                Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                return ERROR_CLOSE_FAILED
            }

            Log.i(TAG, "║ ✓ Puerto $comport cerrado exitosamente")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            return SUCCESS

        } catch (e: Exception) {
            Log.e(TAG, "║ ❌ EXCEPCIÓN durante cierre del puerto $comport", e)
            Log.e(TAG, "║    Mensaje: ${e.message}")
            isOpen = false
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
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
            Log.e(TAG, "║ ✗ ERROR: Puerto $comport no está abierto para lectura")
            return ERROR_NOT_OPEN
        }
        if (buffer.isEmpty() || expectedLen <= 0) {
            Log.w(TAG, "║ ⚠️  Buffer vacío o expectedLen inválido para puerto $comport")
            return 0
        }

        try {
            // Log detallado solo cada cierto número de lecturas para evitar spam
            if (Math.random() < 0.05) { // 5% de las veces
                Log.d(TAG, "║ 📖 Intentando leer $expectedLen bytes del puerto $comport (timeout: $timeout ms)")
            }

            val bytesRead = Rs232Api.PortRecv_Api(comport, buffer, expectedLen, timeout)

            if (bytesRead < 0) {
                // Solo loguear timeouts ocasionalmente para evitar spam
                if (Math.random() < 0.01) { // 1% de las veces
                    Log.v(TAG, "║ ⏱️  Timeout en lectura del puerto $comport (normal si no hay datos)")
                }
                return ERROR_READ_TIMEOUT_OR_FAILURE
            }

            if (bytesRead > 0) {
                val hexData = buffer.take(bytesRead).joinToString("") { "%02X".format(it) }
                Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
                Log.i(TAG, "║ 📥 DATOS RECIBIDOS - Puerto $comport")
                Log.i(TAG, "╠══════════════════════════════════════════════════════════════")
                Log.i(TAG, "║ Bytes leídos: $bytesRead")
                Log.i(TAG, "║ Datos HEX: $hexData")
                Log.i(TAG, "║ Datos ASCII: ${String(buffer, 0, bytesRead, Charsets.ISO_8859_1).replace("[^\\x20-\\x7E]".toRegex(), ".")}")
                Log.i(TAG, "╚══════════════════════════════════════════════════════════════")
            }

            return bytesRead

        } catch (e: Exception) {
            Log.e(TAG, "║ ❌ EXCEPCIÓN durante lectura del puerto $comport", e)
            Log.e(TAG, "║    Mensaje: ${e.message}")
            return ERROR_GENERAL_EXCEPTION
        }
    }
}