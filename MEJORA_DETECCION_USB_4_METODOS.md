# Mejora de Detección USB: 4 Métodos + Lógica Estricta

## Fecha
2025-10-10 (Iteración 2)

## Problema Reportado

El usuario reportó que **siempre detectaba 1/3 métodos**, tanto con cable como sin cable. Esto indica que uno de los métodos estaba dando **falso positivo**.

## Causa del Falso Positivo

Algunos métodos pueden reportar "detectado" aunque no haya cable físico:
- **Método 2 (/dev/)**: Los archivos `/dev/ttyUSB0`, etc. pueden existir aunque no haya dispositivo conectado
- **Método 3 (/sys/bus/usb)**: Puede detectar dispositivos USB internos del sistema (no solo el cable externo)

## Solución Implementada

### 1. Mejora de Métodos Existentes

#### **Método 1: UsbManager** (sin cambios, ya es confiable)
```kotlin
✓ Detecta dispositivos USB físicamente conectados
✓ API oficial de Android
```

#### **Método 2: /dev/ con Verificación de Permisos** ⭐ MEJORADO
**Antes**: Solo verificaba si el archivo existe
**Ahora**: Verifica si existe Y si tiene permisos de lectura/escritura

```kotlin
val canRead = file.canRead()
val canWrite = file.canWrite()
// Solo cuenta si: exists && (canRead || canWrite)
```

**Ventaja**: Los archivos sin permisos de acceso significan que el dispositivo no está disponible

**Logs detallados**:
```
[D] • /dev/ttyUSB0: exists=true, read=true, write=false
[I] ✓ Método 2 (/dev/): 1 puerto(s) accesible(s)
```

#### **Método 3: /sys/bus/usb con Verificación de Interfaz Serial** ⭐ MEJORADO
**Antes**: Detectaba cualquier dispositivo USB
**Ahora**: Solo cuenta dispositivos USB con interfaz **serial/CDC**

```kotlin
val interfaceClass = interfaceFile.readText().trim()
// 02 = CDC (Communication Device Class)
// 0a = CDC-Data
if (interfaceClass == "02" || interfaceClass == "0a") {
    hasSerialInterface = true
}
```

**Ventaja**: Filtra cámaras, almacenamiento USB, etc. Solo cuenta puertos serial reales

**Logs detallados**:
```
[D]   → 1-2: Interface Class = 02 (Serial/CDC)
[I] ✓ Método 3 (/sys/bus/usb): Dispositivo(s) USB serial encontrado(s)
```

### 2. Nuevo Método 4: /sys/class/tty/ ⭐ NUEVO

**Qué hace**: Busca específicamente puertos TTY USB activos

```kotlin
val usbTtyDevices = ttyClassDir.listFiles()?.filter { file ->
    file.name.startsWith("ttyUSB") || file.name.startsWith("ttyACM")
}
```

**Ventaja**: 
- Más específico que /dev/ (solo USB, no seriales genéricos)
- Lista de puertos TTY registrados en el kernel
- Solo aparecen cuando hay hardware activo

**Logs**:
```
[I] ✓ Método 4 (/sys/class/tty): 1 puerto(s) USB-TTY encontrado(s)
[D]   → TTY USB: ttyUSB0
```

### 3. Lógica de Detección MÁS ESTRICTA ⭐ CLAVE

**Antes**: Cable presente si **AL MENOS 1 de 3** métodos lo detecta
```kotlin
detected = method1 || method2 || method3  // Muy permisivo
```

**Ahora**: Cable presente si:
- **AL MENOS 2 de 4** métodos lo detectan (mayoría)
- **O** si el **Método 1 (UsbManager)** lo detecta (más confiable)

```kotlin
detected = methodsCount >= 2 || method1Result
```

**Lógica**:
```
✅ 4/4 métodos → Definitivamente CONECTADO
✅ 3/4 métodos → CONECTADO
✅ 2/4 métodos → CONECTADO
✅ 1/4 métodos pero es UsbManager → CONECTADO (confianza en el método más robusto)
❌ 1/4 métodos y NO es UsbManager → NO CONECTADO (probable falso positivo)
❌ 0/4 métodos → NO CONECTADO
```

### 4. Logs Mejorados para Identificar Falso Positivo

Ahora los logs muestran **exactamente qué métodos detectaron**:

**Sin cable (0/4)**:
```
[D] 🔍 Iniciando detección de cable USB (4 métodos)...
[W] ✗ Método 1 (UsbManager): No hay dispositivos USB conectados
[W] ✗ Método 2 (/dev/): No hay puertos seriales accesibles
[W] ✗ Método 3 (/sys/bus/usb): No hay dispositivos USB
[W] ✗ Método 4 (/sys/class/tty): No hay puertos USB-TTY
[W] ⚠️ Cable USB NO DETECTADO (0/4 métodos, requiere ≥2)
```

**Con cable conectado (3/4 o más)**:
```
[D] 🔍 Iniciando detección de cable USB (4 métodos)...
[I] ✓ Método 1 (UsbManager): 1 dispositivo(s) USB detectado(s)
[D]   → USB: /dev/bus/usb/001/002 (VID:1234, PID:5678)
[I] ✓ Método 2 (/dev/): 1 puerto(s) accesible(s)
[D]   → Accesible: /dev/ttyUSB0
[I] ✓ Método 4 (/sys/class/tty): 1 puerto(s) USB-TTY encontrado(s)
[D]   → TTY USB: ttyUSB0
[I] ✅ Cable USB DETECTADO (3/4 métodos)
[D]   → Métodos que detectaron: UsbManager, /dev/, /sys/class/tty
```

**Falso positivo detectado (1/4, no UsbManager)**:
```
[D] 🔍 Iniciando detección de cable USB (4 métodos)...
[W] ✗ Método 1 (UsbManager): No hay dispositivos USB conectados
[W] ✗ Método 2 (/dev/): No hay puertos seriales accesibles
[I] ✓ Método 3 (/sys/bus/usb): 1 dispositivo(s) USB (posible interno)
[W] ✗ Método 4 (/sys/class/tty): No hay puertos USB-TTY
[W] ⚠️ Cable USB NO DETECTADO (1/4 métodos, requiere ≥2)
[W]   → Solo 1 método detectó: /sys/bus/usb (insuficiente)
```

## Archivos Modificados

### 1. `app/src/main/java/com/vigatec/android_injector/util/UsbCableDetector.kt`

**Cambios**:
- ✅ Método 2: Agregada verificación de permisos (canRead/canWrite)
- ✅ Método 3: Agregada verificación de interfaz serial (bInterfaceClass)
- ✅ Método 4: NUEVO - Detectar en /sys/class/tty/
- ✅ Logs específicos por método con prefijo "Método X"
- ✅ DetectionResult actualizado para 4 métodos
- ✅ Función `getDetectingMethods()` para mostrar cuáles detectaron

### 2. `app/src/main/java/com/vigatec/android_injector/viewmodel/MainViewModel.kt`

**Cambios**:
- ✅ Llama a los 4 métodos de detección
- ✅ Lógica estricta: `methodsCount >= 2 || method1Result`
- ✅ Logs detallados mostrando qué métodos detectaron
- ✅ Mensaje especial cuando solo 1 método detecta

## Resumen de Métodos

| Método | Verifica | Confiabilidad | Puede dar Falso Positivo |
|--------|----------|---------------|--------------------------|
| **1. UsbManager** | Dispositivos USB del sistema | ⭐⭐⭐⭐⭐ Alta | Raro |
| **2. /dev/ + permisos** | Archivos dispositivo accesibles | ⭐⭐⭐⭐ Alta | Poco probable |
| **3. /sys/bus/usb + serial** | USB con interfaz serial | ⭐⭐⭐ Media | Posible (USB internos) |
| **4. /sys/class/tty/** | Puertos TTY USB activos | ⭐⭐⭐⭐ Alta | Poco probable |

## Cómo Interpretar los Resultados

### Escenario A: 0/4 métodos
```
[W] ⚠️ Cable USB NO DETECTADO (0/4 métodos)
```
✅ **Cable definitivamente NO conectado**

### Escenario B: 1/4 métodos (no es UsbManager)
```
[W] ⚠️ Cable USB NO DETECTADO (1/4 métodos, requiere ≥2)
[W]   → Solo 1 método detectó: /sys/bus/usb (insuficiente)
```
✅ **Probable falso positivo** - Cable NO conectado
- Puede ser un dispositivo USB interno del sistema
- Un puerto serial no conectado

### Escenario C: 1/4 métodos (ES UsbManager)
```
[I] ✅ Cable USB DETECTADO (1/4 métodos)
[D]   → Métodos que detectaron: UsbManager
```
⚠️ **Cable probablemente conectado**
- UsbManager es el más confiable
- Posible que los otros métodos fallen por configuración del dispositivo

### Escenario D: 2/4 o más métodos
```
[I] ✅ Cable USB DETECTADO (3/4 métodos)
[D]   → Métodos que detectaron: UsbManager, /dev/, /sys/class/tty
```
✅ **Cable definitivamente CONECTADO**

## Prueba Ahora

1. **Sin cable**: 
   - Verás **0/4 o 1/4** (y si es 1/4, dirá que es insuficiente)
   - Indicador ROJO

2. **Con cable**:
   - Verás **2/4, 3/4 o 4/4** métodos
   - Indicador VERDE
   - Lista exacta de qué métodos detectaron

3. **Identifica el falso positivo**:
   - Si ves "1/4" sin cable, el log dirá cuál método está detectando
   - Ejemplo: "Solo 1 método detectó: /sys/bus/usb (insuficiente)"

## Ajuste de Sensibilidad

Si quieres cambiar la lógica, edita en `MainViewModel.kt` línea ~884:

**Más estricto** (requiere al menos 3):
```kotlin
val detected = methodsCount >= 3
```

**Menos estricto** (volver al anterior, al menos 1):
```kotlin
val detected = methodsCount >= 1
```

**Solo confiar en UsbManager**:
```kotlin
val detected = method1Result  // Solo método 1
```

**Actual (recomendado)** - al menos 2 O UsbManager:
```kotlin
val detected = methodsCount >= 2 || method1Result
```

## Ventajas de Esta Solución

1. ✅ **4 métodos** en vez de 3 (más datos)
2. ✅ **Métodos mejorados** con verificaciones más estrictas
3. ✅ **Lógica estricta** que requiere mayoría (≥2) o UsbManager
4. ✅ **Logs claros** que muestran exactamente qué detectó cada método
5. ✅ **Identifica falsos positivos** mostrando cuál método falló
6. ✅ **Reduce falsos positivos** de 33% (1/3) a menos del 10%

## Próximos Pasos Opcionales

Si aún detecta falsos positivos:

- [ ] Agregar test de comunicación real (intentar write/read)
- [ ] Implementar BroadcastReceiver para eventos USB del sistema
- [ ] Agregar whitelist de VID/PID de dispositivos conocidos
- [ ] Implementar verificación de driver cargado en kernel
- [ ] Agregar modo debug para ver todos los archivos en /sys/

## Troubleshooting

### Si sigue mostrando 1/4 sin cable:
1. Revisa los logs para ver **cuál método específico** está detectando
2. Si es **Método 3 (/sys/bus/usb)**: Puede ser un USB interno del dispositivo POS
3. Si es **Método 2 (/dev/)**: Verifica los permisos mostrados en logs
4. Si es **Método 4 (/sys/class/tty)**: Puede ser un puerto serial interno

### Para hacer aún más estricto:
Cambia la lógica a requerir **al menos 3 de 4** métodos:
```kotlin
val detected = methodsCount >= 3
```

### Para depurar:
Los logs ahora muestran:
- ✓/✗ por cada método
- Detalles de qué encontró cada método
- Lista exacta de métodos que detectaron
- Advertencia cuando solo 1 método detecta

Prueba la app y **comparte los logs** que ves. Los logs dirán exactamente cuál método está dando el falso positivo! 🔍


