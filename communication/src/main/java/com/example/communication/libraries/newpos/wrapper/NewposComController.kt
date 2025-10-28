package com.example.communication.libraries.newpos.wrapper

import android.content.Context
import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.base.IComController
import com.pos.device.uart.SerialPort
import java.io.IOException
import kotlinx.coroutines.runBlocking

class NewposComController(private val context: Context? = null) : IComController {
    private val TAG = "NewposComController"

    private var serialPort: SerialPort? = null
    private var ch340Detector: com.example.communication.libraries.ch340.CH340CableDetector? = null
    private var usingCH340Cable: Boolean = false

    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.d(TAG, "║ NEWPOS COM INIT - Detección Dual Cable (USB OTG + CH340)")
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ Parámetros solicitados:")
        Log.i(TAG, "║   • Baud Rate: ${baudRate.name}")
        Log.i(TAG, "║   • Parity: ${parity.name}")
        Log.i(TAG, "║   • Data Bits: ${dataBits.name}")

        try {
            // PASO 1: Intentar cable CH340 si contexto disponible
            if (context != null) {
                Log.i(TAG, "║ 🔍 PASO 1/2: Intentando cable especial CH340...")
                if (tryCH340()) {
                    Log.i(TAG, "║ ✅ Cable CH340 detectado y configurado")
                    Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                    return 0
                }
                Log.d(TAG, "║ Cable CH340 no disponible, continuando...")
            } else {
                Log.d(TAG, "║ [PASO 1/2] Omitiendo CH340 (sin contexto)")
            }

            // PASO 2: Intentar puertos virtuales USB (fallback)
            Log.i(TAG, "║ 🔍 PASO 2/2: Buscando puertos virtuales USB...")
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
                Log.i(TAG, "║ ✓ Puerto virtual USB encontrado y configurado")
                Log.i(TAG, "║ ✅ Inicialización exitosa (USB OTG)")
                Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
                0
            } else {
                Log.e(TAG, "║ ✗ FALLO: Ningún puerto disponible (CH340 ni USB virtual)")
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

    /**
     * Intentar detectar y usar cable CH340
     */
    private fun tryCH340(): Boolean {
        return try {
            Log.d(TAG, "║ [CH340] Detectando...")
            val detector = com.example.communication.libraries.ch340.CH340CableDetector(context!!)

            // Detectar cable de forma síncrona
            val detected = runBlocking {
                detector.detectCable()
            }

            if (detected) {
                Log.i(TAG, "║ [CH340] ✅ Cable detectado")
                // Configurar UART: 115200 baud, 8 data bits, 1 stop bit, no parity
                detector.configure(115200, 8, 1, 0, 0)
                ch340Detector = detector
                usingCH340Cable = true
                Log.i(TAG, "║ [CH340] ✓ Configurado: 115200bps 8N1")
                true
            } else {
                Log.d(TAG, "║ [CH340] ✗ No detectado")
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "║ [CH340] Error: ${e.message}")
            false
        }
    }

    override fun open(): Int {
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════")
        Log.d(TAG, "║ NEWPOS COM OPEN")
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════")

        return if (usingCH340Cable && ch340Detector != null) {
            Log.i(TAG, "║ ✓ Cable CH340 inicializado y listo")
            Log.i(TAG, "║ ✅ Puerto abierto exitosamente (CH340)")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            0
        } else if (serialPort != null) {
            Log.i(TAG, "║ ✓ Puerto virtual USB inicializado y listo")
            Log.i(TAG, "║ ✅ Puerto abierto exitosamente (USB OTG)")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            0
        } else {
            Log.e(TAG, "║ ✗ FALLO: Ningún puerto inicializado")
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
            if (usingCH340Cable) {
                Log.i(TAG, "║ 🔒 Cerrando cable CH340...")
                try {
                    ch340Detector?.close()
                    Log.i(TAG, "║ ✓ Cable CH340 cerrado")
                } catch (e: Exception) {
                    Log.w(TAG, "║ ⚠️ Error cerrando CH340: ${e.message}")
                }
                ch340Detector = null
                usingCH340Cable = false
            }

            if (serialPort != null) {
                Log.i(TAG, "║ 🔒 Liberando puerto serial...")
                serialPort?.release()
                serialPort = null
                Log.i(TAG, "║ ✓ Puerto serial cerrado y liberado")
            }

            Log.i(TAG, "║ ✅ Puerto cerrado exitosamente")
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

            val bytesWritten = if (usingCH340Cable) {
                Log.i(TAG, "║ Usando cable CH340")
                val written = ch340Detector?.writeData(data) ?: -1
                if (written > 0) {
                    Log.i(TAG, "║ ✓ Enviados $written bytes por CH340")
                }
                written
            } else {
                serialPort?.outputStream?.apply {
                    write(data)
                    flush()
                }
                Log.i(TAG, "║ ✓ Enviados ${data.size} bytes por USB OTG")
                data.size
            }

            Log.i(TAG, "║ ✓ Datos enviados y flushed exitosamente")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            bytesWritten
        } catch (e: IOException) {
            Log.e(TAG, "║ ❌ ERROR al escribir al puerto", e)
            Log.e(TAG, "║    Mensaje: ${e.message}")
            Log.d(TAG, "╚══════════════════════════════════════════════════════════════")
            -1
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        return try {
            val readBytes = if (usingCH340Cable) {
                Log.i(TAG, "║ Leyendo desde cable CH340...")
                val data = ch340Detector?.readData(expectedLen)
                if (data != null && data.isNotEmpty()) {
                    val bytesRead = minOf(data.size, buffer.size)
                    data.copyInto(buffer, 0, 0, bytesRead)
                    bytesRead
                } else {
                    0
                }
            } else {
                val inputStream = serialPort?.inputStream ?: run {
                    Log.e(TAG, "║ ✗ ERROR: InputStream es NULL")
                    return -1
                }
                inputStream.read(buffer)
            }

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