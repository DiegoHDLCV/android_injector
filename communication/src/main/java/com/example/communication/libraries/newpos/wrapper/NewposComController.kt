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
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.d(TAG, "║ NEWPOS COM INIT")
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ Parámetros solicitados:")
        Log.i(TAG, "║   • Baud Rate: ${baudRate.name}")
        Log.i(TAG, "║   • Parity: ${parity.name}")
        Log.i(TAG, "║   • Data Bits: ${dataBits.name}")

        try {
            Log.i(TAG, "║ 🔍 PASO 1/3: Buscando puerto serial disponible...")
            Log.i(TAG, "║     Intentando ttyUSB0 (id=7)...")
            serialPort = SerialPort.getInstance(SerialPort.DEFAULT_CFG, 7)

            if (serialPort == null) {
                Log.i(TAG, "║     ttyUSB0 no disponible, intentando ttyACM0 (id=8)...")
                serialPort = SerialPort.getInstance(SerialPort.DEFAULT_CFG, 8)
            }

            if (serialPort == null) {
                Log.i(TAG, "║     ttyACM0 no disponible, intentando ttyGS0 (id=6)...")
                serialPort = SerialPort.getInstance(SerialPort.DEFAULT_CFG, 6)
            }

            return if (serialPort != null) {
                Log.i(TAG, "║ ✓ Puerto serial encontrado y configurado")
                Log.i(TAG, "║ ✅ PASO 2/3: Inicialización exitosa")
                Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                0
            } else {
                Log.e(TAG, "║ ✗ FALLO: Ningún puerto serial disponible")
                Log.e(TAG, "║   Posibles causas:")
                Log.e(TAG, "║   • Cable USB no conectado")
                Log.e(TAG, "║   • Dispositivo no reconocido por el sistema")
                Log.e(TAG, "║   • Driver USB no disponible")
                Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "║ ❌ EXCEPCIÓN durante inicialización", e)
            Log.e(TAG, "║    Mensaje: ${e.message}")
            Log.e(TAG, "║    Stack: ${e.stackTraceToString().take(200)}")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            return -1
        }
    }

    override fun open(): Int {
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.d(TAG, "║ NEWPOS COM OPEN")
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════")

        return if (serialPort != null) {
            Log.i(TAG, "║ ✓ Puerto serial ya inicializado y listo")
            Log.i(TAG, "║ ✅ Puerto abierto exitosamente")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            0
        } else {
            Log.e(TAG, "║ ✗ FALLO: Puerto serial es NULL")
            Log.e(TAG, "║   Debe llamar a init() primero")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            -1
        }
    }

    override fun close(): Int {
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.d(TAG, "║ NEWPOS COM CLOSE")
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════")

        return try {
            Log.i(TAG, "║ 🔒 Liberando puerto serial...")
            serialPort?.release()
            serialPort = null
            Log.i(TAG, "║ ✓ Puerto serial cerrado y liberado exitosamente")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            0
        } catch (e: Exception) {
            Log.e(TAG, "║ ❌ EXCEPCIÓN durante cierre del puerto", e)
            Log.e(TAG, "║    Mensaje: ${e.message}")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            -1
        }
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        return try {
            val hexData = data.joinToString("") { "%02X".format(it) }
            Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ 📤 ENVIANDO DATOS - NEWPOS")
            Log.i(TAG, "╠══════════════════════════════════════════════════════════════")
            Log.i(TAG, "║ Bytes a enviar: ${data.size}")
            Log.i(TAG, "║ Datos HEX: $hexData")

            serialPort?.outputStream?.apply {
                write(data)
                flush()
            }

            Log.i(TAG, "║ ✓ Datos enviados y flushed exitosamente")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            data.size
        } catch (e: IOException) {
            Log.e(TAG, "║ ❌ ERROR al escribir al puerto serial", e)
            Log.e(TAG, "║    Mensaje: ${e.message}")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            -1
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        return try {
            val inputStream = serialPort?.inputStream ?: run {
                Log.e(TAG, "║ ✗ ERROR: InputStream es NULL")
                return -1
            }

            val readBytes = inputStream.read(buffer)

            if (readBytes > 0) {
                val hexData = buffer.take(readBytes).joinToString("") { "%02X".format(it) }
                Log.i(TAG, "╔══════════════════════════════════════════════════════════════")
                Log.i(TAG, "║ 📥 DATOS RECIBIDOS - NEWPOS")
                Log.i(TAG, "╠══════════════════════════════════════════════════════════════")
                Log.i(TAG, "║ Bytes leídos: $readBytes")
                Log.i(TAG, "║ Datos HEX: $hexData")
                Log.i(TAG, "║ Datos ASCII: ${String(buffer, 0, readBytes, Charsets.ISO_8859_1).replace("[^\\x20-\\x7E]".toRegex(), ".")}")
                Log.i(TAG, "╚══════════════════════════════════════════════════════════════")
            }

            if (readBytes >= 0) readBytes else 0
        } catch (e: IOException) {
            Log.e(TAG, "║ ❌ EXCEPCIÓN durante lectura del puerto", e)
            Log.e(TAG, "║    Mensaje: ${e.message}")
            -1
        }
    }
}