package com.example.communication.libraries.aisino.util

import android.util.Log
import com.example.communication.base.EnumCommConfBaudRate
import com.example.communication.base.EnumCommConfDataBits
import com.example.communication.base.EnumCommConfParity
import com.example.communication.libraries.aisino.wrapper.AisinoComController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detector de puertos Aisino mediante prueba activa
 *
 * PROPÓSITO:
 * - Encontrar qué puerto tiene el cable conectado
 * - Fallback si detección por USB no funciona
 * - Verificar que el dispositivo responde
 *
 * ESTRATEGIA:
 * - Intenta abrir puertos candidatos (0-3)
 * - Intenta baudrates comunes (115200, 9600, 19200)
 * - Verifica que el puerto responda
 * - Retorna el primer puerto que funciona
 */
object AisinoPortProber {

    private const val TAG = "AisinoPortProber"

    // Puertos candidatos (típicamente 0-3 en Aisino A90)
    private val CANDIDATE_PORTS = listOf(0, 1, 2, 3)

    // Baudrates en orden de probabilidad
    private val CANDIDATE_BAUDS = listOf(115200, 9600, 19200, 38400, 57600)

    /**
     * Resultado de un intento de detección
     */
    data class ProbeResult(
        val port: Int,
        val baudRate: Int,
        val success: Boolean
    )

    /**
     * Probar puertos hasta encontrar uno que responda
     *
     * Función suspendible (async) que intenta varios puertos y baudrates
     * hasta encontrar uno que funciona.
     *
     * @return ProbeResult si encontró un puerto, null si ninguno funciona
     */
    suspend fun probePort(): ProbeResult? = withContext(Dispatchers.Default) {
        Log.i(TAG, "╔════════════════════════════════════════════════════════")
        Log.i(TAG, "║ PROBANDO PUERTOS AISINO")
        Log.i(TAG, "╠════════════════════════════════════════════════════════")

        for (port in CANDIDATE_PORTS) {
            for (baud in CANDIDATE_BAUDS) {
                Log.d(TAG, "║ Probando puerto $port @ ${baud}bps...")

                try {
                    // Crear controlador para este puerto
                    val controller = AisinoComController(port)

                    // Inicializar con parámetros
                    controller.init(
                        mapBaudRate(baud),
                        EnumCommConfParity.NOPAR,
                        EnumCommConfDataBits.DB_8
                    )

                    // Intentar abrir
                    val openResult = controller.open()
                    if (openResult != 0) {
                        Log.d(TAG, "║   ⚠️ Error abriendo puerto: $openResult")
                        continue
                    }

                    Log.d(TAG, "║   ✓ Puerto abierto")

                    // Intentar lectura rápida para verificar que funciona
                    val buffer = ByteArray(128)
                    val bytesRead = controller.readData(
                        128,
                        buffer,
                        500  // timeout 500ms
                    )

                    // Cerrar puerto después de probar
                    try {
                        controller.close()
                    } catch (e: Exception) {
                        Log.d(TAG, "║   ⚠️ Error cerrando: ${e.message}")
                    }

                    // Verificar respuesta
                    if (bytesRead > 0) {
                        val data = String(buffer.take(bytesRead).toByteArray(), Charsets.UTF_8)
                        Log.i(TAG, "║ ✅ Puerto $port @ ${baud}bps RESPONDIÓ")
                        Log.i(TAG, "║    Datos: $data")
                        Log.d(TAG, "╚════════════════════════════════════════════════════════")

                        return@withContext ProbeResult(port, baud, true)
                    }

                } catch (e: Exception) {
                    Log.d(TAG, "║   ⚠️ Excepción: ${e.message}")
                    // Continuar con siguiente combinación
                }
            }
        }

        Log.w(TAG, "║ ❌ NO SE ENCONTRÓ PUERTO RESPONDIENDO")
        Log.d(TAG, "╚════════════════════════════════════════════════════════")
        null
    }

    /**
     * Probar un puerto específico
     *
     * Función de utilidad para probar un puerto y baudrate específicos
     *
     * @param port Puerto a probar
     * @param baudRate Baudrate a probar
     * @return true si el puerto responde
     */
    suspend fun probeSpecificPort(port: Int, baudRate: Int): Boolean =
        withContext(Dispatchers.Default) {
            try {
                val controller = AisinoComController(port)

                controller.init(
                    mapBaudRate(baudRate),
                    EnumCommConfParity.NOPAR,
                    EnumCommConfDataBits.DB_8
                )

                if (controller.open() != 0) {
                    return@withContext false
                }

                val buffer = ByteArray(128)
                val bytesRead = controller.readData(128, buffer, 500)

                try {
                    controller.close()
                } catch (e: Exception) {
                    // Ignorar error de cierre
                }

                bytesRead > 0
            } catch (e: Exception) {
                Log.w(TAG, "Error probando puerto $port: ${e.message}")
                false
            }
        }

    /**
     * Mapear baudrate int a enum
     */
    private fun mapBaudRate(baudRate: Int): EnumCommConfBaudRate {
        return when (baudRate) {
            1200 -> EnumCommConfBaudRate.BPS_1200
            2400 -> EnumCommConfBaudRate.BPS_2400
            4800 -> EnumCommConfBaudRate.BPS_4800
            9600 -> EnumCommConfBaudRate.BPS_9600
            19200 -> EnumCommConfBaudRate.BPS_19200
            38400 -> EnumCommConfBaudRate.BPS_38400
            57600 -> EnumCommConfBaudRate.BPS_57600
            else -> EnumCommConfBaudRate.BPS_115200  // Default
        }
    }
}
