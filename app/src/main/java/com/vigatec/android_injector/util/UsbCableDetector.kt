package com.vigatec.android_injector.util

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.communication.polling.CommLog
import java.io.File

/**
 * Helper class para detectar cable USB conectado usando múltiples métodos
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
                CommLog.e(TAG, "UsbManager no disponible")
                return false
            }
            
            val deviceList = usbManager.deviceList
            val hasDevices = deviceList.isNotEmpty()
            
            if (hasDevices) {
                CommLog.i(TAG, "✓ UsbManager: ${deviceList.size} dispositivo(s) USB detectado(s)")
                // Mostrar información de los dispositivos
                deviceList.values.forEach { device ->
                    CommLog.d(TAG, "  → USB: ${device.deviceName} (VID:${device.vendorId}, PID:${device.productId})")
                }
            } else {
                CommLog.w(TAG, "✗ UsbManager: No hay dispositivos USB conectados")
            }
            
            hasDevices
            
        } catch (e: Exception) {
            CommLog.e(TAG, "❌ Error en UsbManager: ${e.message}")
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
                    CommLog.d(TAG, "  • $path: exists=$exists, read=$canRead, write=$canWrite")
                }
                
                // Solo contar como presente si existe Y (puede leer O escribir)
                exists && (canRead || canWrite)
            }
            
            if (accessibleNodes.isNotEmpty()) {
                CommLog.i(TAG, "✓ Método 2 (/dev/): ${accessibleNodes.size} puerto(s) accesible(s)")
                accessibleNodes.forEach { node ->
                    CommLog.d(TAG, "  → Accesible: $node")
                }
                true
            } else {
                CommLog.w(TAG, "✗ Método 2 (/dev/): No hay puertos seriales accesibles")
                false
            }
            
        } catch (e: Exception) {
            CommLog.e(TAG, "❌ Método 2 error: ${e.message}")
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
                CommLog.w(TAG, "✗ Método 3 (/sys/bus/usb): directorio no disponible")
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
                            CommLog.d(TAG, "  → ${device.name}: Interface Class = $interfaceClass (Serial/CDC)")
                            hasSerialInterface = true
                        }
                    } catch (e: Exception) {
                        // Ignorar errores de lectura
                    }
                }
            }
            
            if (hasSerialInterface) {
                CommLog.i(TAG, "✓ Método 3 (/sys/bus/usb): Dispositivo(s) USB serial encontrado(s)")
                true
            } else if (usbDevices.isNotEmpty()) {
                CommLog.w(TAG, "✗ Método 3 (/sys/bus/usb): ${usbDevices.size} USB(s) pero sin interfaz serial")
                false
            } else {
                CommLog.w(TAG, "✗ Método 3 (/sys/bus/usb): No hay dispositivos USB")
                false
            }
            
        } catch (e: Exception) {
            CommLog.e(TAG, "❌ Método 3 error: ${e.message}")
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
                CommLog.w(TAG, "✗ Método 4 (/sys/class/tty): directorio no disponible")
                return false
            }
            
            val usbTtyDevices = ttyClassDir.listFiles()?.filter { file ->
                // Buscar específicamente puertos USB (ttyUSB*, ttyACM*)
                file.name.startsWith("ttyUSB") || file.name.startsWith("ttyACM")
            } ?: emptyList()
            
            if (usbTtyDevices.isNotEmpty()) {
                CommLog.i(TAG, "✓ Método 4 (/sys/class/tty): ${usbTtyDevices.size} puerto(s) USB-TTY encontrado(s)")
                usbTtyDevices.forEach { device ->
                    CommLog.d(TAG, "  → TTY USB: ${device.name}")
                }
                true
            } else {
                CommLog.w(TAG, "✗ Método 4 (/sys/class/tty): No hay puertos USB-TTY")
                false
            }
            
        } catch (e: Exception) {
            CommLog.e(TAG, "❌ Método 4 error: ${e.message}")
            false
        }
    }
    
    /**
     * MÉTODO COMBINADO: Usa múltiples métodos para mayor confiabilidad
     * Retorna true si AL MENOS UNO de los métodos detecta cable
     */
    fun detectCombined(): DetectionResult {
        CommLog.d(TAG, "═══ Iniciando detección combinada de cable USB ═══")
        
        val method1 = detectUsingUsbManager()
        val method2 = detectUsingDeviceNodes()
        val method3 = detectUsingSystemFiles()
        val method4 = detectUsingTtyClass()
        
        val detected = method1 || method2 || method3 || method4
        
        val result = DetectionResult(
            detected = detected,
            usbManagerDetected = method1,
            deviceNodesDetected = method2,
            systemFilesDetected = method3,
            ttyClassDetected = method4
        )
        
        if (detected) {
            CommLog.i(TAG, "✅ CABLE USB DETECTADO (${result.detectionCount()}/4 métodos)")
        } else {
            CommLog.w(TAG, "⚠️ CABLE USB NO DETECTADO (0/4 métodos)")
        }
        
        return result
    }
    
    /**
     * Clase de resultado con detalles de cada método
     */
    data class DetectionResult(
        val detected: Boolean,
        val usbManagerDetected: Boolean,
        val deviceNodesDetected: Boolean,
        val systemFilesDetected: Boolean,
        val ttyClassDetected: Boolean
    ) {
        fun detectionCount(): Int {
            var count = 0
            if (usbManagerDetected) count++
            if (deviceNodesDetected) count++
            if (systemFilesDetected) count++
            if (ttyClassDetected) count++
            return count
        }
        
        fun getDetectingMethods(): String {
            val methods = mutableListOf<String>()
            if (usbManagerDetected) methods.add("UsbManager")
            if (deviceNodesDetected) methods.add("/dev/")
            if (systemFilesDetected) methods.add("/sys/bus/usb")
            if (ttyClassDetected) methods.add("/sys/class/tty")
            return methods.joinToString(", ")
        }
        
        override fun toString(): String {
            return "DetectionResult(detected=$detected, methods=${detectionCount()}/4: ${getDetectingMethods()})"
        }
    }
}

