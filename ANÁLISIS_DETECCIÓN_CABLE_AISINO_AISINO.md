# Análisis: Detección de Cable Aisino-Aisino

## 📋 Estado Actual (Solución Implementada)

### ✅ Funciona
- **Aisino (MASTER) + NewPOS**: Cable detectado correctamente
- **NewPOS + Aisino (SUBPOS)**: Cable detectado correctamente
- **Aisino (MASTER) + Aisino (SUBPOS)**: ✅ **AHORA FUNCIONA** (solución implementada)

### 🔧 Solución Implementada

Según comunicación con Aisino Support:
> **"The one which connects OTG will work in host mode"**

**Esto significa**: El dispositivo que tenga el **cable OTG (con adaptador)** automáticamente actuará como USB HOST. El dispositivo con puerto USB normal actuará como PERIPHERAL.

**No necesitamos forzar manualmente los modos** - Android configura automáticamente según el tipo de conector USB.

**Cambios realizados:**

1. **Creado `UsbModeManager`** (communication/usb/UsbModeManager.kt)
   - Proporciona información clara sobre qué cable usar
   - MASTER (injector) → **Usar cable OTG** → Automáticamente USB HOST
   - SUBPOS (keyreceiver) → **Usar puerto USB normal** → Automáticamente PERIPHERAL
   - Intenta configuración manual vía setprop como fallback si es necesario

2. **Actualizado `keyreceiver/AndroidManifest.xml`**
   - Cambió `android.hardware.usb.host` de `required="true"` a `required="false"`
   - Agregó `android.hardware.usb.accessory` para permitir modo PERIPHERAL

3. **Integrado en `InjectorApplication` e `App`**
   - Se llama `UsbModeManager.configureUsbMode()` durante inicialización
   - Solo proporciona información y configuración de fallback si es necesario
   - La configuración automática por puerto USB es la primaria

---

## 🔌 Cómo Usar (Instrucciones Prácticas)

### Configuración Correcta para Aisino-Aisino

**Dispositivo MASTER (injector)**:
1. Obtener un cable USB **OTG (On-The-Go)** con adaptador
2. Conectar el cable OTG al puerto USB del dispositivo MASTER
3. El dispositivo MASTER automáticamente será USB HOST
4. Podrá detectar dispositivos conectados

**Dispositivo SUBPOS (keyreceiver)**:
1. Usar un cable USB **normal (sin OTG)**
2. Conectar el cable al puerto USB normal del dispositivo SUBPOS
3. El dispositivo SUBPOS automáticamente será USB PERIPHERAL
4. Será detectado automáticamente por el MASTER

**Conexión**:
- Conectar ambos dispositivos entre sí con los cables respectivos
- El MASTER detectará automáticamente al SUBPOS
- El botón "Iniciar Escucha" se habilitará

### Verificación en Logs

```
║ MASTER (injector):
║   • Debe usar CABLE OTG para ser USB HOST
║   • Detectará dispositivos conectados al otro puerto

║ SUBPOS (keyreceiver):
║   • Debe usar PUERTO USB NORMAL (no OTG)
║   • Será detectado automáticamente por el HOST
```

---

## Raíz del Problema

### Por Qué NewPOS Funciona

**NewPOS SDK**:
```kotlin
// NewposCommunicationManager
SerialPort.getInstance(SerialPort.DEFAULT_CFG, 7)  // ttyUSB0
SerialPort.getInstance(SerialPort.DEFAULT_CFG, 8)  // ttyACM0
SerialPort.getInstance(SerialPort.DEFAULT_CFG, 6)  // ttyGS0
```

Estos puertos se enumeran como dispositivos USB estándar en:
- `/sys/class/tty/ttyUSB*`
- `/sys/class/tty/ttyACM*`
- `/sys/class/tty/ttyGS*`
- `UsbManager.deviceList`

**Resultado**: `UsbCableDetector` encuentra estos puertos ✅

---

### Por Qué Aisino No Funciona (Aisino-Aisino)

**Aisino SDK (Vanstone)**:
```kotlin
// AisinoCommunicationManager
Rs232Api.PortOpen_Api(port=0, baud=9600)   // Puerto 0
Rs232Api.PortOpen_Api(port=1, baud=9600)   // Puerto 1
// etc...
```

Estos son puertos **internos del PED**, NO dispositivos USB estándar:
- No aparecen en `/sys/class/tty/`
- No aparecen en `UsbManager.deviceList`
- No tienen un `/dev/ttyUSB*` o `/dev/ttyACM*`
- Son abstracciones del Vanstone SDK

**Resultado**: `UsbCableDetector` NO encuentra nada ❌

---

## Métodos de Detección Actuales (UsbCableDetector)

### Método 1: UsbManager API
```kotlin
fun detectUsingUsbManager(): Boolean {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val deviceList = usbManager.deviceList
    return deviceList.isNotEmpty()
}
```
**Funciona para**: NewPOS (expone puerto USB estándar)
**Falla para**: Aisino (puertos internos)

### Método 2: Nodos de dispositivo (/dev/)
```kotlin
val deviceNodes = listOf("/dev/ttyUSB0", "/dev/ttyUSB1", "/dev/ttyACM0", ...)
return deviceNodes.any { File(it).exists() && canRead/Write }
```
**Funciona para**: NewPOS
**Falla para**: Aisino (no hay `/dev/ttyUSB*` para puertos internos)

### Método 3: Archivos del sistema (/sys/bus/usb/)
```kotlin
File("/sys/bus/usb/devices").listFiles()
// Buscar bInterfaceClass == "02" (CDC) o "0a" (CDC-Data)
```
**Funciona para**: NewPOS
**Falla para**: Aisino

### Método 4: TTY class (/sys/class/tty/)
```kotlin
File("/sys/class/tty").listFiles()
// Buscar ttyUSB*, ttyACM*
```
**Funciona para**: NewPOS
**Falla para**: Aisino

---

## Soluciones Investigadas

### ❌ 1. Habilitación de USB Gadget
Se intentó hacer que Aisino exponga un puerto USB gadget escribiendo en:
- `/sys/class/android_usb/android0/functions`
- `/sys/class/android_usb/android0/enable`

**Problemas**:
- Requiere acceso root
- Interfiere con la inicialización normal
- No es sostenible a largo plazo
- **Solución descartada**

---

## Próximas Investigaciones

### 1. ¿Aisino expone estado en /sys/?
Buscar archivos similares a:
- `/sys/class/vanstone/`
- `/sys/devices/platform/vanstone_ped/`
- `/proc/vanstone/`
- `/sys/kernel/debug/vanstone/`

**Comando**:
```bash
find /sys -name "*vanstone*" -o -name "*aisino*" -o -name "*rs232*" 2>/dev/null
find /proc -name "*vanstone*" 2>/dev/null
```

### 2. Detectar basándose en autoScan exitoso
Idea: Si `autoScanPortsAndBauds()` encuentra exitosamente un puerto, significa que hay conexión.

```kotlin
private var lastSuccessfulScanResult: Pair<Int, Int>? = null  // puerto, baud

fun wasLastScanSuccessful(): Boolean {
    return lastSuccessfulScanResult != null
}
```

**Problema**: El autoScan ocurre durante inicialización, no periódicamente.

### 3. Monitoreo periódico de puertos del Vanstone SDK
Idea: Ejecutar periodicamente autoScan "ligero" que solo intente conectarse sin inicializar.

```kotlin
fun quickPortProbe(): Boolean {
    for (port in candidatePorts) {
        try {
            val testController = AisinoComController(port)
            val openRes = testController.open()
            testController.close()
            if (openRes == 0) return true
        } catch (e: Exception) {}
    }
    return false
}
```

**Ventaja**: Detectaría si hay un dispositivo en el puerto
**Desventaja**: Más costoso en recursos, puede interferir

### 4. BroadcastReceiver para Vanstone
Investigar si el Vanstone SDK emite algún Intent cuando se detecta dispositivo:

```kotlin
class VanstoneConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // ¿Existe algún intent del tipo?
        // "com.vanstone.intent.ACTION_DEVICE_ATTACHED"?
    }
}
```

### 5. Archivo de documentación de Vanstone
Revisar en la documentación si hay:
- Callbacks para detección de dispositivo
- APIs de estado de puerto
- Mecanismo nativo de detección

---

## Recomendación

El problema es **arquitectónico**:
- Aisino usa puertos internos que no son enumerables por Android
- NewPOS usa puertos USB estándar que sí son enumerables

**Solución probable**:
Implementar detección específica para Aisino que:
1. Intente conectarse a los puertos candidatos (lightweight probe)
2. Monitoree periódicamente en background
3. Use StateFlow para notificar cambios de estado

Pero esto requiere:
- Entender mejor la API del Vanstone SDK
- Revisar documentación de Aisino
- Posiblemente contactar con soporte técnico de Aisino

---

## Historial de Cambios

**e7aa6ec**: Estado estable - Aisino+NewPOS ✅, Aisino+Aisino ❌
- Revertidos cambios de gadget USB (causaban regresión)
- Configuración de puertos correcta
- StateFlow de sincronización implementado

**Versiones anteriores**: Intentos fallidos de habilitación de gadget USB
