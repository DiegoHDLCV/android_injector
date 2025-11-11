package com.vigatec.communication.libraries.aisino.util

import android.util.Log
import com.vigatec.communication.base.IComController
import kotlin.math.min

/**
 * Gestor asíncrono de I/O para puertos seriales Aisino
 *
 * PROPÓSITO:
 * - Lectura automática en thread separado
 * - Callbacks para nuevos datos
 * - Mejor responsiveness de UI
 *
 * BENEFICIOS:
 * - No bloquea el thread principal
 * - Procesamiento automático de datos entrantes
 * - Manejo robusto de errores
 */
class SerialInputOutputManager(
    private val port: IComController,
    private val listener: Listener
) : Runnable {

    /**
     * Listener para eventos de I/O
     */
    interface Listener {
        /**
         * Llamado cuando hay nuevos datos disponibles
         *
         * @param data Datos recibidos del puerto
         */
        fun onNewData(data: ByteArray)

        /**
         * Llamado cuando ocurre un error durante I/O
         *
         * @param exception Excepción ocurrida
         */
        fun onRunError(exception: Exception)
    }

    /**
     * Estados posibles del gestor
     */
    enum class State {
        STOPPED,   // No está corriendo
        RUNNING,   // Leyendo datos
        STOPPING   // En proceso de parar
    }

    companion object {
        private const val TAG = "SerialIoManager"
        private const val READ_TIMEOUT = 100  // ms
        private const val BUFFER_SIZE = 4096
    }

    private val readBuffer = ByteArray(BUFFER_SIZE)
    private var state = State.STOPPED
    private var thread: Thread? = null

    /**
     * Iniciar el gestor I/O
     *
     * Crea un nuevo thread que lee datos continuamente del puerto
     * y notifica al listener mediante callbacks
     */
    fun start() {
        synchronized(this) {
            if (state != State.STOPPED) {
                Log.w(TAG, "⚠️ Ya está corriendo")
                return
            }

            state = State.RUNNING
            thread = Thread(this, "AisinoSerialIoManager").apply {
                Log.d(TAG, "Iniciando thread de I/O")
                start()
            }
        }
    }

    /**
     * Detener el gestor I/O
     *
     * Señala al thread que debe parar y espera a que finalice
     */
    fun stop() {
        synchronized(this) {
            if (state == State.RUNNING) {
                state = State.STOPPING
            }
        }

        try {
            thread?.join(5000)  // Esperar máximo 5 segundos
        } catch (e: Exception) {
            Log.w(TAG, "Error esperando thread: ${e.message}")
        }

        synchronized(this) {
            Log.d(TAG, "Thread detenido")
        }
    }

    /**
     * Obtener estado actual del gestor
     */
    fun getState(): State = state

    /**
     * Thread de I/O (ejecutado en thread separado)
     *
     * Lee continuamente del puerto y notifica al listener de nuevos datos
     */
    override fun run() {
        Log.i(TAG, "🔄 Thread I/O iniciado")

        try {
            while (state == State.RUNNING) {
                try {
                    // Leer datos del puerto
                    val bytesRead = port.readData(
                        BUFFER_SIZE,
                        readBuffer,
                        READ_TIMEOUT
                    )

                    when {
                        // Nuevos datos disponibles
                        bytesRead > 0 -> {
                            val data = readBuffer.copyOf(bytesRead)
                            Log.d(TAG, "📥 Datos recibidos: $bytesRead bytes")
                            listener.onNewData(data)
                        }

                        // Error (excepto timeout)
                        bytesRead < 0 && bytesRead != -6 -> {
                            Log.w(TAG, "⚠️ Error lectura: $bytesRead")
                            listener.onRunError(
                                Exception("Read error: $bytesRead")
                            )
                            break
                        }

                        // bytesRead == -6 es timeout normal
                        // No hacer nada, continuar leyendo
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Excepción leyendo: ${e.message}", e)
                    listener.onRunError(e)
                    break
                }
            }
        } finally {
            synchronized(this) {
                state = State.STOPPED
            }
            Log.i(TAG, "⏹️ Thread I/O finalizado")
        }
    }
}
