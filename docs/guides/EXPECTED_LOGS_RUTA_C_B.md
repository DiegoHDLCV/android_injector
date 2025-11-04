# 📊 Expected Logs - RUTA C+B Triple Strategy

**Document:** Guía de logs esperados para cada estrategia de conexión
**Última actualización:** 27 de octubre de 2025

---

## 📑 Tabla de Contenidos

1. [Scenario 1: Virtual Port Success (Linux)](#scenario-1-virtual-port-success)
2. [Scenario 2: USB Host API Success](#scenario-2-usb-host-api-success)
3. [Scenario 3: Rs232Api Fallback](#scenario-3-rs232api-fallback)
4. [Scenario 4: Async I/O with SerialInputOutputManager](#scenario-4-async-io-operations)
5. [Scenario 5: Port Probing Fallback](#scenario-5-port-probing-fallback)
6. [Scenario 6: Complete Error Chain](#scenario-6-complete-error-chain)
7. [How to View These Logs](#how-to-view-these-logs)

---

## Scenario 1: Virtual Port Success

**When:** Running on Linux kernel with virtual ports (ttyUSB0, ttyACM0, ttyGS0) available
**Status:** ✅ BEST CASE - Shared access enabled

```
I/AisinoComController: ╔══════════════════════════════════════════════════════════════
I/AisinoComController: ║ AISINO COM OPEN - Intentando puertos virtuales
I/AisinoComController: ╠══════════════════════════════════════════════════════════════
I/AisinoComController: ║ 🔍 Intentando ttyUSB0 (id=7)...
I/AisinoComController: ║ ✓ Puerto virtual encontrado: /dev/ttyUSB0
I/AisinoComController: ║ ✅ Puerto virtual abierto exitosamente
I/AisinoComController: ║ ✓ Usando puerto virtual: ttyUSB0 (id=7) (/dev/ttyUSB0)
I/AisinoComController: ║ ✅ VENTAJA: Acceso compartido permitido (múltiples procesos)
D/AisinoComController: ╚══════════════════════════════════════════════════════════════
```

### Subsecuent Write/Read Operations
```
I/AisinoComController: 📤 TX puerto virtual: 32 bytes
I/AisinoComController: 📥 RX puerto virtual: 16 bytes - 901A0103010100

I/AisinoComController: 📤 TX puerto virtual: 64 bytes
I/AisinoComController: 📥 RX puerto virtual: 32 bytes - 901D010202003030303030303030303030
```

### Key Indicators
- ✅ "Puerto virtual encontrado"
- ✅ "VENTAJA: Acceso compartido"
- 📤 TX messages with byte counts
- 📥 RX messages with hex data

---

## Scenario 2: USB Host API Success

**When:** Context provided AND Android USB Host API available AND device has permission
**Status:** ✅ GOOD CASE - Standard USB, non-proprietary

```
I/AisinoComController: ╔══════════════════════════════════════════════════════════════
I/AisinoComController: ║ AISINO COM OPEN - Intentando puertos virtuales
I/AisinoComController: ╠══════════════════════════════════════════════════════════════
I/AisinoComController: ║ 🔍 Intentando ttyUSB0 (id=7)...
D/AisinoComController: ║ ⚠️ ttyUSB0 no disponible: (No such file or directory)
I/AisinoComController: ║ 🔍 Intentando ttyACM0 (id=8)...
D/AisinoComController: ║ ⚠️ ttyACM0 no disponible: (No such file or directory)
I/AisinoComController: ║ 🔍 Intentando ttyGS0 (id=6)...
D/AisinoComController: ║ ⚠️ ttyGS0 no disponible: (No such file or directory)
I/AisinoComController: ║ [2/3] Intentando USB Host API...

I/AisinoUsbDeviceManager: Buscando dispositivos Aisino...
I/AisinoUsbDeviceManager: ✓ Encontrado: /dev/bus/usb/001/015 (0x05C6:0x901D)
I/AisinoUsbDeviceManager: Total: 1 dispositivo(s)

I/AisinoUsbComController: ╔═══════════════════════════════════════════════════════════════
I/AisinoUsbComController: ║ ABRIENDO PUERTO USB - /dev/bus/usb/001/015
I/AisinoUsbComController: ╠═══════════════════════════════════════════════════════════════
I/AisinoUsbComController: ║ ✓ Permiso USB verificado
I/AisinoUsbComController: ║ ✓ Conexión USB abierta
I/AisinoUsbComController: ║ ✅ Puerto USB abierto exitosamente
I/AisinoUsbComController: ║ Dispositivo: /dev/bus/usb/001/015
I/AisinoUsbComController: ║ Configuración: 115200bps, 8N1
D/AisinoUsbComController: ╚═══════════════════════════════════════════════════════════════

I/AisinoComController: ║ ✅ Usando USB Host API
D/AisinoComController: ╚══════════════════════════════════════════════════════════════
```

### Subsequent Write/Read Operations
```
I/AisinoUsbComController: 📤 TX: 32 bytes
I/AisinoUsbComController: 📥 RX: 16 bytes

I/AisinoUsbComController: 📤 TX: 64 bytes
I/AisinoUsbComController: 📥 RX: 32 bytes
```

### Key Indicators
- ✓ Virtual ports NOT available (⚠️ messages)
- ✓ USB device detected: "0x05C6:0x901D"
- ✓ Permission verified
- ✓ "✅ Usando USB Host API"

---

## Scenario 3: Rs232Api Fallback

**When:** Virtual ports unavailable AND (no context OR USB not available)
**Status:** ⚠️ ACCEPTABLE - Proprietary SDK, exclusive access

```
I/AisinoComController: ╔══════════════════════════════════════════════════════════════
I/AisinoComController: ║ AISINO COM OPEN - Intentando puertos virtuales
I/AisinoComController: ╠══════════════════════════════════════════════════════════════
I/AisinoComController: ║ 🔍 Intentando ttyUSB0 (id=7)...
D/AisinoComController: ║ ⚠️ ttyUSB0 no disponible: (No such file or directory)
I/AisinoComController: ║ 🔍 Intentando ttyACM0 (id=8)...
D/AisinoComController: ║ ⚠️ ttyACM0 no disponible: (No such file or directory)
I/AisinoComController: ║ 🔍 Intentando ttyGS0 (id=6)...
D/AisinoComController: ║ ⚠️ ttyGS0 no disponible: (No such file or directory)
I/AisinoComController: ║ [2/3] Omitiendo USB Host (sin contexto)
I/AisinoComController: ║ [3/3] Intentando fallback Rs232Api...
I/AisinoComController: ║ Intentando Puerto 0 (Rs232Api.PortOpen_Api)...
I/AisinoComController: ║ ✓ Puerto Rs232 0 abierto (115200bps)
I/AisinoComController: ║ ⚠️ Advertencia: Usando Puerto 0 (acceso exclusivo, sin compartir)
I/AisinoComController: ║ NOTA: Para Aisino-Aisino, considere usar puertos virtuales
D/AisinoComController: ╚══════════════════════════════════════════════════════════════
```

### Subsecuent Write/Read Operations
```
I/AisinoComController: 📤 TX puerto 0: 32 bytes
I/AisinoComController: 📥 RX puerto 0: 16 bytes - 901A0103010100

I/AisinoComController: 📤 TX puerto 0: 64 bytes
I/AisinoComController: 📥 RX puerto 0: 32 bytes
```

### Key Indicators
- ✓ Virtual ports unavailable
- ✓ "Omitiendo USB Host (sin contexto)" OR "Sin permiso USB"
- ✓ "✓ Puerto Rs232 0 abierto"
- ⚠️ "acceso exclusivo, sin compartir" (limitation notice)

---

## Scenario 4: Async I/O Operations

**When:** SerialInputOutputManager is active
**Status:** ✅ Non-blocking I/O with callbacks

```
D/SerialIoManager: Iniciando thread de I/O
I/SerialIoManager: 🔄 Thread I/O iniciado

D/SerialIoManager: 📥 Datos recibidos: 16 bytes
D/SerialIoManager: 📥 Datos recibidos: 32 bytes
D/SerialIoManager: 📥 Datos recibidos: 8 bytes

I/SerialIoManager: ⏹️ Thread I/O finalizado
```

### When Using Port Probing
```
I/AisinoPortProber: ╔════════════════════════════════════════════════════════
I/AisinoPortProber: ║ PROBANDO PUERTOS AISINO
I/AisinoPortProber: ╠════════════════════════════════════════════════════════
D/AisinoPortProber: ║ Probando puerto 0 @ 115200bps...
D/AisinoPortProber: ║   ✓ Puerto abierto
D/AisinoPortProber: ║   ⏱️ Esperando datos...
D/AisinoPortProber: ║   ⚠️ No hubo respuesta (timeout)
D/AisinoPortProber: ║ Probando puerto 0 @ 9600bps...
D/AisinoPortProber: ║   ✓ Puerto abierto
D/AisinoPortProber: ║   ⚠️ Error leyendo: -6
D/AisinoPortProber: ║ Probando puerto 1 @ 115200bps...
D/AisinoPortProber: ║   ⚠️ Error abriendo puerto: -3

... [continue probing] ...

I/AisinoPortProber: ║ ✅ Puerto 0 @ 115200bps RESPONDIÓ
I/AisinoPortProber: ║    Datos: <Device Response>
D/AisinoPortProber: ╚════════════════════════════════════════════════════════
```

### Key Indicators
- 🔄 "Thread I/O iniciado" (started)
- 📥 "Datos recibidos: X bytes" (data arriving)
- ⏹️ "Thread I/O finalizado" (stopped gracefully)

---

## Scenario 5: Port Probing Fallback

**When:** Normal detection fails, using AisinoPortProber.probePort()
**Status:** 🔍 Active detection as last resort

```
I/AisinoPortProber: ╔════════════════════════════════════════════════════════
I/AisinoPortProber: ║ PROBANDO PUERTOS AISINO
I/AisinoPortProber: ╠════════════════════════════════════════════════════════

D/AisinoPortProber: ║ Probando puerto 0 @ 115200bps...
D/AisinoPortProber: ║   ✓ Puerto abierto
D/AisinoPortProber: ║   ⚠️ No hubo respuesta

D/AisinoPortProber: ║ Probando puerto 0 @ 9600bps...
D/AisinoPortProber: ║   ✓ Puerto abierto
D/AisinoPortProber: ║   ⏱️ Esperando respuesta (500ms timeout)
I/AisinoPortProber: ║ ✅ Puerto 0 @ 9600bps RESPONDIÓ
I/AisinoPortProber: ║    Datos: AT
D/AisinoPortProber: ╚════════════════════════════════════════════════════════
```

### Result Object
```
ProbeResult(
    port = 0,
    baudRate = 9600,
    success = true
)
```

---

## Scenario 6: Complete Error Chain

**When:** All strategies fail (device not connected, no drivers, etc.)
**Status:** ❌ All fallbacks exhausted

```
I/AisinoComController: ╔══════════════════════════════════════════════════════════════
I/AisinoComController: ║ AISINO COM OPEN - Intentando puertos virtuales
I/AisinoComController: ╠══════════════════════════════════════════════════════════════
I/AisinoComController: ║ 🔍 Intentando ttyUSB0 (id=7)...
D/AisinoComController: ║ ⚠️ ttyUSB0 no disponible: (No such file or directory)
I/AisinoComController: ║ 🔍 Intentando ttyACM0 (id=8)...
D/AisinoComController: ║ ⚠️ ttyACM0 no disponible: (No such file or directory)
I/AisinoComController: ║ 🔍 Intentando ttyGS0 (id=6)...
D/AisinoComController: ║ ⚠️ ttyGS0 no disponible: (No such file or directory)
I/AisinoComController: ║ [2/3] Intentando USB Host API...
D/AisinoUsbDeviceManager: Buscando dispositivos Aisino...
W/AisinoUsbDeviceManager: No se encontraron dispositivos Aisino
D/AisinoComController: ║ ⚠️ No hay dispositivos Aisino USB
I/AisinoComController: ║ [3/3] Intentando fallback Rs232Api...
E/AisinoComController: ║ ❌ Error al abrir puerto Rs232 0: -1
D/AisinoComController: ╚══════════════════════════════════════════════════════════════
```

### Return Value
```kotlin
ERROR_OPEN_FAILED (-3)
```

---

## How to View These Logs

### Option 1: Android Studio Logcat
1. **Open Android Studio**
2. **View → Tool Windows → Logcat** (or `⌘6` on Mac)
3. **Filter by tag:**
   - `AisinoComController` (main strategy selection)
   - `AisinoUsbComController` (USB Host API)
   - `AisinoUsbDeviceManager` (USB device enumeration)
   - `AisinoPortProber` (port probing)
   - `SerialIoManager` (async I/O)

### Option 2: Command Line (ADB)
```bash
# All Aisino-related logs
adb logcat | grep -E "Aisino|USB|Serial"

# Specific component
adb logcat *:S AisinoComController:V
adb logcat *:S AisinoUsbComController:V
adb logcat *:S SerialIoManager:V

# Save to file
adb logcat > aisino_logs.txt
```

### Option 3: Real-time Monitoring
```bash
# Clear previous logs
adb logcat -c

# Start your test
# (e.g., open KeyReceiver, try to connect)

# View logs with timestamps
adb logcat -v threadtime | grep -E "Aisino|USB|Serial"
```

---

## Log Patterns to Look For

### Success Indicators ✅
- "✅" or "✓" emojis
- "exitosamente" (successfully)
- "abierto" (opened)
- "RESPONDIÓ" (responded)
- No error return codes

### Warning Indicators ⚠️
- "⚠️" emoji
- "advertencia" (warning)
- "omitiendo" (skipping)
- "acceso exclusivo" (exclusive access)

### Error Indicators ❌
- "❌" emoji
- "Error" or "error"
- Negative return codes (-1, -3, -4, -5, etc.)
- Exception stack traces

### Performance Indicators 📊
- "📤 TX" (transmit)
- "📥 RX" (receive)
- Byte counts in messages
- Hex data dumps

---

## Example Complete Test Sequence

### Scenario: Aisino-Aisino with Virtual Ports Success

**Device A (KeyReceiver):**
```
I/AisinoComController: ║ AISINO COM OPEN - Intentando puertos virtuales
I/AisinoComController: ║ ✓ Puerto virtual encontrado: /dev/ttyUSB0
I/AisinoComController: ║ ✅ Puerto virtual abierto exitosamente

I/SerialIoManager: 🔄 Thread I/O iniciado
D/SerialIoManager: 📥 Datos recibidos: 32 bytes  [KEY_INJECTION_REQUEST]
```

**Device B (Injector):**
```
I/AisinoComController: ║ AISINO COM OPEN - Intentando puertos virtuales
I/AisinoComController: ║ ✓ Puerto virtual encontrado: /dev/ttyUSB0
I/AisinoComController: ║ ✅ Puerto virtual abierto exitosamente

I/AisinoComController: 📤 TX puerto virtual: 32 bytes
I/AisinoComController: 📤 TX puerto virtual: 64 bytes
```

**Result:** ✅ Both devices communicate successfully with shared access enabled

---

## Troubleshooting Based on Logs

| Log Pattern | Problem | Solution |
|------------|---------|----------|
| "ttyUSB0 no disponible" | Virtual ports not available | Normal on some Android versions |
| "No se encontraron dispositivos Aisino" | USB not detected | Check cable, USB permissions |
| "acceso exclusivo, sin compartir" | Using Rs232Api fallback | Consider enabling virtual ports |
| "Thread I/O iniciado" + no "Datos recibidos" | I/O thread running but no data | Check if device is sending data |
| "❌ Error al abrir puerto" | All strategies failed | Device not connected or drivers missing |

---

**Generated:** 27 de octubre de 2025
**Version:** RUTA C+B Implementation
**Status:** Ready for testing
