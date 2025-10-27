# 🎯 Estrategia de Integración: Demo Aisino → Proyecto Actual

**Fecha**: 2025-10-24
**Estado**: Análisis completado, estrategia definida
**Objetivo**: Aplicar patrones del demo de Aisino para resolver Aisino-Aisino

---

## 📋 Problema Identificado

### Tu Implementación Actual
- ✅ Puertos virtuales (ttyUSB0/ttyACM0/ttyGS0)
- ✅ Fallback a Rs232Api
- ✅ Comunicación funcionando (parcialmente)
- ❌ **Detección de cable NO funciona** (UsbCableDetector falla)
- ❌ **Razón**: Rs232Api NO expone dispositivos en `UsbManager`

### Demo de Aisino
- ✅ Android USB Host API
- ✅ CDC-ACM driver
- ✅ Detección automática de dispositivos
- ✅ Comunicación bidireccional
- ✅ **Detección de cable funciona** (UsbManager nativo)

### Conclusión
**El demo usa estándares USB nativos de Android, tu código usa SDK propietario que no expone dispositivos.**

---

## 🛣️ Rutas de Acción

### Ruta A: RECOMENDADA - Migración a Android USB Host API
```
Ventajas:
  ✅ Detección de cable funciona
  ✅ Estándar USB (no propietario)
  ✅ Código más robusto
  ✅ Compatible con múltiples dispositivos

Desventajas:
  ❌ Requiere cambios significativos
  ❌ Requiere permisos USB (diálogo)
  ❌ No usa puertos virtuales
  ❌ ~3-5 días de desarrollo

Complejidad: ⭐⭐⭐⭐ (moderada-alta)
```

### Ruta B: INTERMEDIA - Híbrida (Puertos Virtuales + USB Host)
```
Ventajas:
  ✅ Mantiene acceso compartido (virtuales)
  ✅ Agrega detección de cable (USB Host)
  ✅ Fallback a ambas opciones

Desventajas:
  ❌ Más complejo de mantener
  ❌ Dos APIs simultáneamente

Complejidad: ⭐⭐⭐⭐⭐ (alta)
```

### Ruta C: LIGERA - Mejorar sin Migrar
```
Ventajas:
  ✅ Mínimos cambios
  ✅ Rápido de implementar

Desventajas:
  ❌ Detección de cable sigue sin funcionar
  ❌ Sin acceso a UsbManager
  ❌ Soluciones parciales

Complejidad: ⭐ (baja)
```

---

## 📊 Análisis Detallado de Rutas

### RUTA A: Migración a USB Host API (RECOMENDADA)

#### Paso 1: Copiar Drivers del Demo

**Archivos a copiar:**
```
Demo → Tu Proyecto
communication/src/main/java/com/example/communication/libraries/aisino/usb/
├── CdcAcmSerialDriver.java     ← Del demo
├── CommonUsbSerialPort.java    ← Del demo
├── UsbSerialDriver.java         ← Del demo
├── UsbSerialPort.java           ← Del demo
├── UsbSerialProber.java         ← Del demo
├── CustomProber.java            ← Del demo (adaptado para Aisino)
├── ProbeTable.java              ← Del demo
└── UsbId.java                   ← Del demo
```

#### Paso 2: Crear Wrapper IComController para USB

```kotlin
// communication/src/main/java/com/example/communication/libraries/aisino/wrapper/
// AisinoUsbComController.kt (NUEVO)

class AisinoUsbComController(
    private val context: Context,
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice
) : IComController {

    companion object {
        private const val TAG = "AisinoUsbComController"
        // Usar constantes del demo
    }

    private var usbSerialPort: UsbSerialPort? = null
    private var isOpen = false
    private var storedBaudRate = 115200

    override fun open(): Int {
        try {
            val driver = UsbSerialProber.getDefaultProber().probeDevice(usbDevice)
                ?: CustomProber.getCustomProber().probeDevice(usbDevice)
                ?: return ERROR_OPEN_FAILED

            usbSerialPort = driver.ports[0]  // Puerto 0
            val connection = usbManager.openDevice(usbDevice)
                ?: return ERROR_OPEN_FAILED

            usbSerialPort?.open(connection)
            usbSerialPort?.setParameters(storedBaudRate, 8, 1, UsbSerialPort.PARITY_NONE)

            isOpen = true
            Log.i(TAG, "✅ Puerto USB abierto: ${usbDevice.deviceName}")
            return SUCCESS

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al abrir puerto USB", e)
            return ERROR_OPEN_FAILED
        }
    }

    override fun close(): Int {
        try {
            usbSerialPort?.close()
            isOpen = false
            return SUCCESS
        } catch (e: Exception) {
            return ERROR_CLOSE_FAILED
        }
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        if (!isOpen) return ERROR_NOT_OPEN

        try {
            usbSerialPort?.write(data, timeout)
            return data.size
        } catch (e: Exception) {
            return ERROR_WRITE_FAILED
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        if (!isOpen) return ERROR_NOT_OPEN

        try {
            return usbSerialPort?.read(buffer, timeout) ?: ERROR_READ_TIMEOUT_OR_FAILURE
        } catch (e: Exception) {
            return ERROR_READ_TIMEOUT_OR_FAILURE
        }
    }
}
```

#### Paso 3: Crear Device Manager

```kotlin
// communication/src/main/java/com/example/communication/libraries/aisino/manager/
// AisinoUsbDeviceManager.kt (NUEVO)

class AisinoUsbDeviceManager(private val context: Context) {

    companion object {
        private const val AISINO_VENDOR_ID = 0x05C6
        private const val AISINO_PRODUCT_ID = 0x901D  // A90
        private const val TAG = "AisinoUsbDeviceManager"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    data class AisinoDevice(
        val device: UsbDevice,
        val name: String,
        val isConnected: Boolean
    )

    // Buscar dispositivos Aisino conectados
    fun findAisinoDevices(): List<AisinoDevice> {
        val devices = mutableListOf<AisinoDevice>()

        for (device in usbManager.deviceList.values) {
            if (device.vendorId == AISINO_VENDOR_ID &&
                device.productId == AISINO_PRODUCT_ID) {
                devices.add(AisinoDevice(
                    device = device,
                    name = device.deviceName,
                    isConnected = true
                ))
            }
        }

        Log.i(TAG, "Encontrados ${devices.size} dispositivos Aisino")
        return devices
    }

    // Solicitar permiso USB
    fun requestPermission(device: UsbDevice, intent: PendingIntent) {
        usbManager.requestPermission(device, intent)
    }

    // Verificar permiso
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    // Crear controlador para dispositivo
    fun createController(device: UsbDevice): AisinoUsbComController {
        return AisinoUsbComController(context, usbManager, device)
    }
}
```

#### Paso 4: Integrar con UsbCableDetector

```kotlin
// REEMPLAZAR UsbCableDetector.kt

class UsbCableDetector(
    private val context: Context,
    private val listener: CableDetectionListener
) {

    companion object {
        private const val TAG = "UsbCableDetector"
        private const val CHECK_INTERVAL = 1000L  // 1 segundo
    }

    private val usbDeviceManager = AisinoUsbDeviceManager(context)
    private var checkJob: Job? = null

    interface CableDetectionListener {
        fun onCableConnected(device: UsbDevice)
        fun onCableDisconnected()
    }

    fun start(scope: CoroutineScope) {
        checkJob = scope.launch {
            while (isActive) {
                val devices = usbDeviceManager.findAisinoDevices()

                if (devices.isNotEmpty()) {
                    // Cable conectado
                    val device = devices[0]  // Usar primer dispositivo
                    if (usbDeviceManager.hasPermission(device.device)) {
                        listener.onCableConnected(device.device)
                        Log.i(TAG, "✅ Cable detectado: ${device.name}")
                    } else {
                        Log.w(TAG, "⚠️ Permiso USB requerido para: ${device.name}")
                    }
                } else {
                    // Sin cable
                    listener.onCableDisconnected()
                }

                delay(CHECK_INTERVAL)
            }
        }
    }

    fun stop() {
        checkJob?.cancel()
    }
}
```

#### Paso 5: Configurar AndroidManifest.xml

```xml
<!-- Agregar -->
<uses-feature android:name="android.hardware.usb.host" />

<!-- En tu Activity -->
<activity ...>
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
    </intent-filter>
    <meta-data
        android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
        android:resource="@xml/device_filter" />
</activity>
```

#### Paso 6: Crear device_filter.xml

```xml
<!-- res/xml/device_filter.xml (NUEVO) -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Aisino A90 -->
    <usb-device vendor-id="1478" product-id="36893" />  <!-- 0x05C6:0x901D -->
</resources>
```

#### Paso 7: Broadcast Receiver para Permisos

```kotlin
// En tu Activity/Application

private val usbPermissionIntent = PendingIntent.getBroadcast(
    this, 0, Intent(ACTION_USB_PERMISSION),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

private val usbPermissionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_USB_PERMISSION) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            val permission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            if (permission && device != null) {
                Log.i(TAG, "✅ Permiso USB otorgado para ${device.deviceName}")
                onAisinoDevicePermissionGranted(device)
            } else {
                Log.w(TAG, "❌ Permiso USB rechazado")
            }
        }
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Registrar broadcast receiver
    val intentFilter = IntentFilter(ACTION_USB_PERMISSION)
    registerReceiver(usbPermissionReceiver, intentFilter, Context.RECEIVER_EXPORTED)
}

override fun onDestroy() {
    super.onDestroy()
    unregisterReceiver(usbPermissionReceiver)
}

private fun onAisinoDevicePermissionGranted(device: UsbDevice) {
    val controller = AisinoUsbDeviceManager(this).createController(device)
    controller.open()

    // Usar controller para comunicación
    // controller.write(...)
    // controller.readData(...)
}
```

---

### RUTA B: Híbrida (Recomendada si no puedes hacer Ruta A completa)

**Mantener puertos virtuales + Agregar USB Host API para detección**

```kotlin
// AisinoComController.kt (MEJORADO)

class AisinoComController(private val context: Context?) : IComController {

    companion object {
        private const val TAG = "AisinoComController"
        private val VIRTUAL_PORTS = listOf(
            Pair("/dev/ttyUSB0", "ttyUSB0"),
            Pair("/dev/ttyACM0", "ttyACM0"),
            Pair("/dev/ttyGS0", "ttyGS0")
        )
    }

    private var usingVirtualPort = false
    private var usingUsbHost = false
    private var usbController: AisinoUsbComController? = null

    override fun open(): Int {
        // ESTRATEGIA 1: Intentar puertos virtuales
        for ((portPath, portName) in VIRTUAL_PORTS) {
            try {
                val portFile = File(portPath)
                if (portFile.exists() && portFile.canRead() && portFile.canWrite()) {
                    // ... código de puertos virtuales ...
                    usingVirtualPort = true
                    return SUCCESS
                }
            } catch (e: Exception) { }
        }

        // ESTRATEGIA 2: Intentar USB Host API (si contexto disponible)
        if (context != null) {
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val usbDeviceManager = AisinoUsbDeviceManager(context)
                val devices = usbDeviceManager.findAisinoDevices()

                if (devices.isNotEmpty() && usbDeviceManager.hasPermission(devices[0].device)) {
                    usbController = usbDeviceManager.createController(devices[0].device)
                    if (usbController!!.open() == SUCCESS) {
                        usingUsbHost = true
                        return SUCCESS
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "USB Host API falló, continuando con fallback")
            }
        }

        // ESTRATEGIA 3: Fallback a Rs232Api
        return openRs232Api()
    }

    override fun write(data: ByteArray, timeout: Int): Int {
        return when {
            usingVirtualPort -> writeVirtualPort(data, timeout)
            usingUsbHost -> usbController?.write(data, timeout) ?: ERROR_NOT_OPEN
            else -> writeRs232Api(data, timeout)
        }
    }

    override fun readData(expectedLen: Int, buffer: ByteArray, timeout: Int): Int {
        return when {
            usingVirtualPort -> readVirtualPort(expectedLen, buffer, timeout)
            usingUsbHost -> usbController?.readData(expectedLen, buffer, timeout) ?: ERROR_NOT_OPEN
            else -> readRs232Api(expectedLen, buffer, timeout)
        }
    }

    override fun close(): Int {
        return when {
            usingUsbHost -> usbController?.close() ?: SUCCESS
            usingVirtualPort -> closeVirtualPort()
            else -> closeRs232Api()
        }
    }
}
```

---

### RUTA C: Mejorar sin Migrar (Quick Wins)

Si no tienes tiempo para migración, estos cambios mejoran significativamente:

#### 1. Implementar SerialInputOutputManager

```kotlin
// communication/src/main/java/com/example/communication/libraries/aisino/util/
// SerialInputOutputManager.kt (NUEVO)

class SerialInputOutputManager(
    private val port: IComController,
    private val listener: Listener
) : Runnable {

    interface Listener {
        fun onNewData(data: ByteArray)
        fun onRunError(exception: Exception)
    }

    private val readBuffer = ByteArray(4096)
    private var running = false

    fun start() {
        Thread(this, "AisinoSerialIoManager").start()
    }

    fun stop() {
        running = false
    }

    override fun run() {
        running = true
        try {
            while (running) {
                val bytesRead = port.readData(readBuffer.size, readBuffer, 100)
                if (bytesRead > 0) {
                    val data = readBuffer.copyOf(bytesRead)
                    listener.onNewData(data)
                }
            }
        } catch (e: Exception) {
            listener.onRunError(e)
        }
    }
}
```

**Uso:**

```kotlin
val ioManager = SerialInputOutputManager(aisinoComController, object : Listener {
    override fun onNewData(data: ByteArray) {
        // Procesar datos automáticamente
    }

    override fun onRunError(exception: Exception) {
        Log.e(TAG, "I/O Error", exception)
    }
})
ioManager.start()
```

#### 2. Mejorar Thread Safety

```kotlin
override fun write(data: ByteArray, timeout: Int): Int {
    if (!isOpen) return ERROR_NOT_OPEN

    synchronized(this) {  // Agregar sincronización
        try {
            if (usingVirtualPort) {
                virtualPortOutputStream?.write(data)
                virtualPortOutputStream?.flush()
                return data.size
            }

            val result = Rs232Api.PortSends_Api(comport, data, data.size)
            return if (result == AISINO_SUCCESS) data.size else ERROR_WRITE_FAILED
        } catch (e: Exception) {
            return ERROR_GENERAL_EXCEPTION
        }
    }
}
```

#### 3. Implementar AisinoPortProber

```kotlin
// communication/src/main/java/com/example/communication/libraries/aisino/util/
// AisinoPortProber.kt (NUEVO)

object AisinoPortProber {

    private val CANDIDATE_PORTS = listOf(0, 1, 2, 3, 4)
    private const val TAG = "AisinoPortProber"

    data class ProbeResult(
        val port: Int,
        val success: Boolean
    )

    suspend fun probePort(): ProbeResult? = withContext(Dispatchers.IO) {
        for (port in CANDIDATE_PORTS) {
            try {
                val controller = AisinoComController(port)

                controller.init(
                    EnumCommConfBaudRate.BPS_115200,
                    EnumCommConfParity.NOPAR,
                    EnumCommConfDataBits.DB_8
                )

                if (controller.open() == 0) {
                    // Intentar lectura rápida
                    val buffer = ByteArray(64)
                    val result = controller.readData(64, buffer, 500)
                    controller.close()

                    if (result >= 0) {
                        Log.i(TAG, "✅ Puerto $port respondió")
                        return@withContext ProbeResult(port, true)
                    }
                }
            } catch (e: Exception) {
                // Continuar
            }
        }

        Log.w(TAG, "❌ No se encontró puerto respondiendo")
        null
    }
}
```

**Usar en detección:**

```kotlin
val probeResult = AisinoPortProber.probePort()
if (probeResult?.success == true) {
    Log.i(TAG, "Cable detectado en puerto ${probeResult.port}")
} else {
    Log.i(TAG, "Sin cable detectado")
}
```

---

## 🏆 Recomendación Final

### Por Tiempo (Corto): RUTA C
- **Tiempo**: 1-2 días
- **Mejoras**: SerialInputOutputManager, Thread Safety, AisinoPortProber
- **Resultado**: Mejor responsiveness, más robusto

### Por Funcionalidad (Medio): RUTA B
- **Tiempo**: 2-3 días
- **Mejoras**: Todo Ruta C + USB Host API para detección
- **Resultado**: Detección de cable funciona, mantiene compatibilidad

### Por Robustez (Largo): RUTA A
- **Tiempo**: 3-5 días
- **Mejoras**: Migración completa a USB Host API
- **Resultado**: Código estándar, detección perfecta, mejor mantenibilidad

---

## 📂 Estructura de Archivos Después de Integración

### Ruta A (Migración Completa)

```
communication/src/main/java/com/example/communication/libraries/aisino/
├── wrapper/
│   ├── AisinoUsbComController.kt      (NUEVO)
│   └── (eliminar AisinoComController.kt después migración)
├── usb/
│   ├── CdcAcmSerialDriver.java        (COPIAR del demo)
│   ├── CommonUsbSerialPort.java       (COPIAR del demo)
│   ├── UsbSerialDriver.java           (COPIAR del demo)
│   ├── UsbSerialPort.java             (COPIAR del demo)
│   ├── UsbSerialProber.java           (COPIAR del demo)
│   ├── CustomProber.java              (COPIAR del demo + adaptar)
│   ├── ProbeTable.java                (COPIAR del demo)
│   └── UsbId.java                     (COPIAR del demo)
├── manager/
│   └── AisinoUsbDeviceManager.kt      (NUEVO)
└── util/
    ├── SerialInputOutputManager.kt    (NUEVO)
    └── AisinoPortProber.kt            (NUEVO)

AndroidManifest.xml                     (Modificar)
res/xml/device_filter.xml               (NUEVO)
```

### Ruta B (Híbrida)

```
communication/src/main/java/com/example/communication/libraries/aisino/
├── wrapper/
│   └── AisinoComController.kt         (MEJORAR con USB Host)
├── usb/
│   └── ... (minimal, solo lo necesario)
├── manager/
│   └── AisinoUsbDeviceManager.kt      (NUEVO)
└── util/
    ├── SerialInputOutputManager.kt    (NUEVO)
    └── AisinoPortProber.kt            (NUEVO)

AndroidManifest.xml                     (Modificar)
res/xml/device_filter.xml               (NUEVO)
```

### Ruta C (Quick Wins)

```
communication/src/main/java/com/example/communication/libraries/aisino/
├── wrapper/
│   └── AisinoComController.kt         (MEJORAR: Thread safety)
└── util/
    ├── SerialInputOutputManager.kt    (NUEVO)
    └── AisinoPortProber.kt            (NUEVO)

(Sin cambios en AndroidManifest.xml)
```

---

## ✅ Checklist de Implementación

### Ruta A (Migración Completa)

- [ ] Copiar archivos USB del demo
- [ ] Crear AisinoUsbComController
- [ ] Crear AisinoUsbDeviceManager
- [ ] Modificar UsbCableDetector
- [ ] Agregar permisos USB en AndroidManifest.xml
- [ ] Crear device_filter.xml
- [ ] Crear BroadcastReceiver para permisos
- [ ] Compilar y probar
- [ ] Migrar MainViewModel a usar nuevo controlador
- [ ] Eliminar código de puertos virtuales/Rs232Api
- [ ] Pruebas exhaustivas Aisino-Aisino

### Ruta B (Híbrida)

- [ ] Crear AisinoUsbDeviceManager
- [ ] Mejorar AisinoComController con multi-estrategia
- [ ] Copiar drivers USB mínimos del demo
- [ ] Modificar UsbCableDetector
- [ ] Agregar permisos USB
- [ ] Crear device_filter.xml
- [ ] Implementar SerialInputOutputManager
- [ ] Implementar AisinoPortProber
- [ ] Compilar y probar
- [ ] Pruebas Aisino-Aisino (con fallback a puertos virtuales)

### Ruta C (Quick Wins)

- [ ] Mejorar AisinoComController (thread safety)
- [ ] Implementar SerialInputOutputManager
- [ ] Implementar AisinoPortProber
- [ ] Integrar en MainViewModel
- [ ] Compilar y probar
- [ ] Pruebas responsiveness mejorado

---

## 📊 Matriz de Comparación

| Característica | Ruta A | Ruta B | Ruta C |
|---|---|---|---|
| Detección cable | ✅ Perfecta | ✅ Buena | ❌ Nula |
| Acceso compartido | ✅ Sí | ✅ Sí (si usa virtual) | ✅ Sí (si usa virtual) |
| I/O Asíncrono | ✅ Automático | ⚠️ Manual | ⚠️ Manual |
| Complejidad código | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐ |
| Tiempo desarrollo | 3-5 días | 2-3 días | 1-2 días |
| Estándar USB | ✅ Sí | ⚠️ Parcial | ❌ No |
| Productivo rápido | ❌ No | ⚠️ Tal vez | ✅ Sí |

---

## 🎯 Recomendación Ejecutiva

**SI quieres resolver Aisino-Aisino COMPLETAMENTE** → **RUTA A** (USB Host API)
- Tiempo: 3-5 días
- Resultado: Comunicación perfecta, detección funciona

**SI quieres mejora RÁPIDA** → **RUTA C** (Quick Wins)
- Tiempo: 1-2 días
- Resultado: Mejor responsiveness, aún con limitaciones

**SI quieres BALANCE** → **RUTA B** (Híbrida)
- Tiempo: 2-3 días
- Resultado: Detección + compatibilidad

---

**Próximo paso**: ¿Cuál ruta prefieres implementar?

