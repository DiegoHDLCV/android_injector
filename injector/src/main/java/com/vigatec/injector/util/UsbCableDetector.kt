package com.vigatec.injector.util

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.vigatec.communication.libraries.ch340.CH340CableDetector
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Helper class para detectar cable USB conectado usando múltiples métodos
 * Copiado desde keyreceiver para usar la misma lógica confiable de detección
 *
 * MÉTODOS SOPORTADOS:
 * 1. UsbManager API (detección de dispositivos USB genéricos)
 * 2. Device Nodes (/dev/) - puertos seriales virtuales
 * 3. System Files (/sys/bus/usb) - información del kernel
 * 4. TTY Class (/sys/class/tty) - puertos TTY USB
 * 5. CH340 Cable Detection - cables especiales con chip CH340/CH341
 */
class UsbCableDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "UsbCableDetector"
    }
    
    /**
     * MÉTODO 1: Detectar usando Android UsbManager API
     * Este es el método más confiable - verifica dispositivos USB físicamente conectados
     */
    fun detectUsingUsbManager(): Boolean {
        return try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            
            if (usbManager == null) {
                Log.e(TAG, "UsbManager no disponible")
                return false
            }
            
            val deviceList = usbManager.deviceList
            val hasDevices = deviceList.isNotEmpty()
            
            if (hasDevices) {
                Log.i(TAG, "✓ UsbManager: ${deviceList.size} dispositivo(s) USB detectado(s)")
                // Mostrar información de los dispositivos
                deviceList.values.forEach { device ->
                    Log.d(TAG, "  → USB: ${device.deviceName} (VID:${device.vendorId}, PID:${device.productId})")
                }
            } else {
                Log.w(TAG, "✗ UsbManager: No hay dispositivos USB conectados")
            }
            
            hasDevices
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en UsbManager: ${e.message}")
            false
        }
    }
    
    /**
     * MÉTODO 2: Verificar nodos de dispositivo en /dev/ CON permisos de acceso
     * Verifica si existen archivos de dispositivo serial Y si son accesibles
     */
    fun detectUsingDeviceNodes(): Boolean {
        return try {
            val deviceNodes = listOf(
                "/dev/ttyUSB0",  // Puerto USB serial común
                "/dev/ttyUSB1",
                "/dev/ttyACM0",  // Puerto USB ACM (Abstract Control Model)
                "/dev/ttyACM1",
                "/dev/ttyS0",    // Puerto serial estándar
                "/dev/ttyS1",
                "/dev/ttyGS0"    // Puerto USB gadget serial
            )
            
            // Filtrar solo los que existen Y son accesibles (readable/writable)
            val accessibleNodes = deviceNodes.filter { path ->
                val file = File(path)
                val exists = file.exists()
                val canRead = file.canRead()
                val canWrite = file.canWrite()
                
                if (exists) {
                    Log.d(TAG, "  • $path: exists=$exists, read=$canRead, write=$canWrite")
                }
                
                // Solo contar como presente si existe Y (puede leer O escribir)
                exists && (canRead || canWrite)
            }
            
            if (accessibleNodes.isNotEmpty()) {
                Log.i(TAG, "✓ Método 2 (/dev/): ${accessibleNodes.size} puerto(s) accesible(s)")
                accessibleNodes.forEach { node ->
                    Log.d(TAG, "  → Accesible: $node")
                }
                true
            } else {
                //Log.w(TAG, "✗ Método 2 (/dev/): No hay puertos seriales accesibles")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Método 2 error: ${e.message}")
            false
        }
    }
    
    /**
     * MÉTODO 3: Verificar dispositivos USB con interfaz serial
     * Lee información del sistema buscando específicamente interfaces serial/CDC-ACM
     */
    fun detectUsingSystemFiles(): Boolean {
        return try {
            val usbDevicesFile = File("/sys/bus/usb/devices")
            
            if (!usbDevicesFile.exists() || !usbDevicesFile.isDirectory) {
//                Log.w(TAG, "✗ Método 3 (/sys/bus/usb): directorio no disponible")
                return false
            }
            
            val usbDevices = usbDevicesFile.listFiles()?.filter { file ->
                // Filtrar solo dispositivos USB reales (no hubs ni root)
                file.isDirectory && file.name.matches(Regex("\\d+-\\d+.*"))
            } ?: emptyList()
            
            // Verificar si tienen interfaces serial
            var hasSerialInterface = false
            usbDevices.forEach { device ->
                val interfaceFile = File(device, "bInterfaceClass")
                if (interfaceFile.exists()) {
                    try {
                        val interfaceClass = interfaceFile.readText().trim()
                        // 02 = CDC (Communication Device Class), 0a = CDC-Data
                        if (interfaceClass == "02" || interfaceClass == "0a") {
                            Log.d(TAG, "  → ${device.name}: Interface Class = $interfaceClass (Serial/CDC)")
                            hasSerialInterface = true
                        }
                    } catch (e: Exception) {
                        // Ignorar errores de lectura
                    }
                }
            }
            
            if (hasSerialInterface) {
                Log.i(TAG, "✓ Método 3 (/sys/bus/usb): Dispositivo(s) USB serial encontrado(s)")
                true
            } else if (usbDevices.isNotEmpty()) {
                Log.w(TAG, "✗ Método 3 (/sys/bus/usb): ${usbDevices.size} USB(s) pero sin interfaz serial")
                false
            } else {
                Log.w(TAG, "✗ Método 3 (/sys/bus/usb): No hay dispositivos USB")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Método 3 error: ${e.message}")
            false
        }
    }
    
    /**
     * MÉTODO 4: Verificar puertos TTY activos en /sys/class/tty/
     * Busca específicamente puertos ttyUSB* y ttyACM* que son USB-serial
     */
    fun detectUsingTtyClass(): Boolean {
        return try {
            val ttyClassDir = File("/sys/class/tty")
            
            if (!ttyClassDir.exists() || !ttyClassDir.isDirectory) {
//                Log.w(TAG, "✗ Método 4 (/sys/class/tty): directorio no disponible")
                return false
            }
            
            val usbTtyDevices = ttyClassDir.listFiles()?.filter { file ->
                // Buscar específicamente puertos USB (ttyUSB*, ttyACM*)
                file.name.startsWith("ttyUSB") || file.name.startsWith("ttyACM")
            } ?: emptyList()
            
            if (usbTtyDevices.isNotEmpty()) {
                Log.i(TAG, "✓ Método 4 (/sys/class/tty): ${usbTtyDevices.size} puerto(s) USB-TTY encontrado(s)")
                usbTtyDevices.forEach { device ->
                    Log.d(TAG, "  → TTY USB: ${device.name}")
                }
                true
            } else {
                Log.w(TAG, "✗ Método 4 (/sys/class/tty): No hay puertos USB-TTY")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Método 4 error: ${e.message}")
            false
        }
    }
    
    /**
     * MÉTODO 5: Detectar cable especial CH340 (chip USB-Serial embebido)
     * Soporta cables especiales para comunicación Aisino-Aisino, Aisino-NewPOS, etc.
     *
     * El cable CH340 contiene un chip que se identifica por VID/PID específicos:
     * - Vendor ID: 0x1A86 (WCH)
     * - Product ID: 0x7523, 0x5523, 0x5512 (diferentes variantes)
     *
     * NOTA: Implementado con timeout para evitar bloqueos prolongados
     * Si CH340 detection tarda más de 1 segundo, asume que no está disponible
     */
    suspend fun detectUsingCH340Cable(): Boolean {
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
//                Log.d(TAG, "🔌 Método 5 (CH340): Detectando cable especial con timeout (1s)...")

                // Usar el detector CH340 con timeout para evitar bloqueos
                val ch340Detector = CH340CableDetector(context)

                // Usar timeout de 1 segundo - si no completa en ese tiempo, asume no detectado
                try {
                    kotlinx.coroutines.withTimeoutOrNull(1000) {
                        ch340Detector.detectCable()
                    } ?: false
                } catch (e: Exception) {
//                    Log.w(TAG, "⚠️ Método 5 (CH340) timeout: ${e.message}")
                    false
                }
            }.also { detected ->
                if (detected) {
                    // No necesitamos reinstanciar para obtener info, usamos una nueva instancia si es necesario
                    // o asumimos que detectCable ya logueó lo necesario.
                    // Para simplificar y evitar llamadas bloqueantes extra, solo logueamos el éxito.
                    Log.i(TAG, "✓ Método 5 (CH340): Cable especial CH340 detectado")
                } else {
                    Log.d(TAG, "✗ Método 5 (CH340): Cable CH340 no detectado")
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Método 5 (CH340) error: ${e.message}")
            false
        }
    }

    /**
     * MÉTODO COMBINADO: Usa múltiples métodos para mayor confiabilidad
     * Lógica del keyreceiver: Cable presente si AL MENOS 2 de 4 métodos lo detectan
     * O si el método 1 (UsbManager - más confiable) lo detecta
     */
    suspend fun detectCombined(): DetectionResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Log.d(TAG, "═══ Iniciando detección combinada de cable USB ═══")

            val method1 = detectUsingUsbManager()
            val method2 = detectUsingDeviceNodes()
            val method3 = detectUsingSystemFiles()
            val method4 = detectUsingTtyClass()
            val method5 = detectUsingCH340Cable()

            // LÓGICA DEL KEYRECEIVER: Cable presente si AL MENOS 2 de 5 métodos lo detectan
            // O si el método 1 (UsbManager - más confiable) lo detecta
            val methodsCount = listOf(method1, method2, method3, method4, method5).count { it }
            val detected = methodsCount >= 2 || method1
            
            val result = DetectionResult(
                detected = detected,
                usbManagerDetected = method1,
                deviceNodesDetected = method2,
                systemFilesDetected = method3,
                ttyClassDetected = method4,
                ch340CableDetected = method5
            )

            if (detected) {
                Log.i(TAG, "✅ CABLE USB DETECTADO (${result.detectionCount()}/5 métodos)")
            } else {
                Log.w(TAG, "⚠️ CABLE USB NO DETECTADO (0/5 métodos)")
            }

            result
        }
    }

    /**
     * Clase de resultado con detalles de cada método
     */
    data class DetectionResult(
        val detected: Boolean,
        val usbManagerDetected: Boolean,
        val deviceNodesDetected: Boolean,
        val systemFilesDetected: Boolean,
        val ttyClassDetected: Boolean,
        val ch340CableDetected: Boolean = false
    ) {
        fun detectionCount(): Int {
            var count = 0
            if (usbManagerDetected) count++
            if (deviceNodesDetected) count++
            if (systemFilesDetected) count++
            if (ttyClassDetected) count++
            if (ch340CableDetected) count++
            return count
        }

        fun getDetectingMethods(): String {
            val methods = mutableListOf<String>()
            if (usbManagerDetected) methods.add("UsbManager")
            if (deviceNodesDetected) methods.add("/dev/")
            if (systemFilesDetected) methods.add("/sys/bus/usb")
            if (ttyClassDetected) methods.add("/sys/class/tty")
            if (ch340CableDetected) methods.add("CH340 Cable")
            return methods.joinToString(", ")
        }

        override fun toString(): String {
            return "DetectionResult(detected=$detected, methods=${detectionCount()}/5: ${getDetectingMethods()})"
        }
    }
}


