# 📱 Guía de Despliegue: Virtual Ports para Aisino

**Fecha**: 2025-10-24
**Estado**: Listo para desplegar
**APKs**: Compiladas y listas

---

## 🚀 Pasos de Despliegue Rápido

### **Paso 1: Conectar dispositivos**
```bash
# Verificar conexión con ambos Aisino
adb devices
```

**Esperado:**
```
List of attached devices
XXXXX01AISINO01 device      (Aisino con Injector)
XXXXX02AISINO02 device      (Aisino con KeyReceiver)
```

### **Paso 2: Instalar Injector en primer Aisino**
```bash
adb -s XXXXX01AISINO01 install -r /Users/diegoherreradelacalle/StudioProjects/android_injector/injector/build/outputs/apk/debug/injector-debug.apk
```

### **Paso 3: Instalar KeyReceiver en segundo Aisino**
```bash
adb -s XXXXX02AISINO02 install -r /Users/diegoherreradelacalle/StudioProjects/android_injector/keyreceiver/build/outputs/apk/debug/keyreceiver-debug.apk
```

### **Paso 4: Reiniciar ambos dispositivos**
```bash
adb -s XXXXX01AISINO01 reboot
adb -s XXXXX02AISINO02 reboot
```

### **Paso 5: Esperar a que arranquen (2-3 minutos)**
```bash
# Esperar a que logs aparezcan en LogCat
adb logcat | grep -i aisino
```

---

## 📊 Monitoreo de Logs

### **Terminal 1: Logs del Injector**
```bash
adb -s XXXXX01AISINO01 logcat | grep -E "AisinoComController|KeyInjectionViewModel" | tee injector_logs.txt
```

### **Terminal 2: Logs del KeyReceiver**
```bash
adb -s XXXXX02AISINO02 logcat | grep -E "AisinoComController|MainViewModel" | tee keyreceiver_logs.txt
```

---

## 🎯 Prueba Paso a Paso

### **Fase 1: Verificar Apertura de Puerto (5 min)**

En **KeyReceiver** (Terminal 2):
1. Abrir app
2. Ir a pantalla principal
3. Presionar "Conectar" o botón para empezar a escuchar
4. Observar logs:

**Esperado BUENO:**
```
I: ║ 🔍 Intentando ttyUSB0...
I: ║ ✓ Puerto virtual encontrado: /dev/ttyUSB0
I: ║ ✅ Puerto virtual abierto exitosamente
I: ║ ✓ Usando puerto virtual: ttyUSB0 (id=7)
I: ║ ✅ VENTAJA: Acceso compartido permitido
I: ¡Conexión establecida! Escuchando en puerto virtual
D: 🔄 ReadAttempt #100: bytesRead=0, duration=999ms
```

**Esperado OK (Fallback):**
```
I: ║ 🔍 Intentando ttyUSB0...
D: ║ ⚠️ ttyUSB0 no disponible
I: ║ 🔍 Intentando ttyACM0...
D: ║ ⚠️ ttyACM0 no disponible
I: ║ ℹ️ Ningún puerto virtual disponible, usando fallback Rs232Api
I: ║ ✓ Puerto Rs232 0 abierto (115200bps)
I: ¡Conexión establecida! Escuchando
```

**MALO - No debería verse:**
```
❌ Error al abrir puerto
```

### **Fase 2: Verificar Escucha Sostenida (2-5 minutos)**

Dejar corriendo el KeyReceiver escuchando.

**Criterios de éxito:**
- ✅ Logs continúan (cada 100 intentos ver `ReadAttempt`)
- ✅ NO cierra después de 22-31 segundos
- ✅ `duration=999ms` indicando que espera el timeout
- ✅ Sin spam masivo de logs (< 200 líneas por minuto)

**Métricas esperadas después de 2 minutos:**
```
15:45:15.200  D: 🔄 ReadAttempt #100 (5230ms): bytesRead=0, duration=999ms
15:45:20.100  D: 🔄 ReadAttempt #200 (10460ms): bytesRead=0, duration=998ms
15:45:25.000  D: 🔄 ReadAttempt #300 (15690ms): bytesRead=0, duration=999ms
[... sin cierre después de 130+ segundos ...]
```

### **Fase 3: Envío de Datos (1-2 minutos)**

En **Injector**:
1. Presionar botón "Inyectar" o "Enviar Prueba"
2. Observar en **KeyReceiver** logs:

**Esperado:**
```
15:45:45.500  D: 🔄 ReadAttempt #350: bytesRead=26, duration=5ms
15:45:45.510  I: 📥 RX puerto: 26 bytes - 48656C6C6F...
15:45:45.520  I: ✓ Mensaje parseado correctamente
15:45:45.530  I: 📤 TX puerto: 8 bytes - respuesta
```

**IMPORTANTE**: KeyReceiver debe **seguir escuchando** después de recibir el mensaje. No debe cerrar.

### **Fase 4: Envío Múltiple (2-3 minutos)**

En **Injector**:
1. Enviar 5 mensajes con 10 segundos de intervalo
2. Observar que KeyReceiver recibe todos

**Criterio de éxito**:
- 5/5 mensajes recibidos sin errores
- KeyReceiver escucha sigue activa durante todo

---

## 📈 Métricas Esperadas vs Anteriores

| Métrica | Antes ❌ | Después ✅ |
|---------|---------|-----------|
| Duración escucha Aisino-Aisino | 22-31s | **Minutos+** |
| Puerto utilizado | Rs232Api (exclusivo) | Virtual (compartido) |
| Aisino-Aisino paralelo | ❌ FALLA | ✅ FUNCIONA |
| Aisino-NewPOS | ✅ OK | ✅ OK |
| Logs por minuto | 100k+ | ~100-200 |

---

## ❌ Problemas y Soluciones

### **Problema 1: Escucha cierra después de ~22-31s**
**Causa probable**: Virtuales no disponibles o fallback activado sin virtuales
**Solución**:
1. Verificar logs: ¿Dice "virtual abierto" o "fallback Rs232Api"?
2. Si fallback: Verificar si dispositivo tiene `/dev/ttyUSB0` (comando `adb shell ls /dev/tty*`)
3. Si virtual: Verificar que no hay error de permisos (comando `adb shell ls -l /dev/ttyUSB0`)

### **Problema 2: TX Error -1 en Injector**
**Causa probable**: Puerto cerrado inesperadamente
**Solución**:
1. Verificar que KeyReceiver aún escucha
2. Esperar a que KeyReceiver establezca conexión antes de enviar
3. Revisar si hay conflicto de acceso

### **Problema 3: No recibe mensajes en KeyReceiver**
**Causa probable**: Injector no tiene puerto abierto
**Solución**:
1. Verificar en Injector que dice "Puerto abierto"
2. Verificar que baud rates coinciden (115200)
3. Revisar cables USB/RS232

### **Problema 4: Logs dicen "virtual abierto" pero sigue cerrando a los 130s**
**Causa probable**: Puerto virtual funciona pero hay otro problema
**Solución**:
1. Esto es OK - significa que el busy-wait fix sigue funcionando
2. La duración de 130s es la anterior mejorada por busy-wait fix
3. Verificar si hay error de escritura: buscar "Error TX" en logs

---

## 🔧 Comandos de Diagnóstico

### **Ver todos los puertos disponibles**
```bash
adb -s XXXXX02AISINO02 shell ls -la /dev/tty*
```

**Esperado con virtuales:**
```
/dev/ttyUSB0        ← Puerto virtual USB CDC
/dev/ttyACM0        ← Puerto virtual USB ACM
/dev/ttyGS0         ← Puerto virtual USB Gadget Serial
```

### **Ver permisos de puerto**
```bash
adb -s XXXXX02AISINO02 shell ls -la /dev/ttyUSB0
```

**Esperado:**
```
crw-rw-rw- root root /dev/ttyUSB0
```

### **Ver si el puerto está en uso**
```bash
adb -s XXXXX02AISINO02 shell fuser /dev/ttyUSB0
```

**Esperado**:
- Si vacío: Puerto disponible
- Si números: PIDs de procesos usando el puerto

### **Ver todos los logs de AisinoComController**
```bash
adb -s XXXXX02AISINO02 logcat | grep AisinoComController
```

---

## 📝 Checklist de Validación

- [ ] Ambas APKs instaladas correctamente
- [ ] Ambos dispositivos reiniciados
- [ ] KeyReceiver abre puerto (virtual o fallback)
- [ ] Logs muestran "Puerto abierto exitosamente"
- [ ] Escucha dura > 1 minuto sin cerrar
- [ ] Logs son legibles (< 200/min, no spam)
- [ ] Aisino Injector envía datos exitosamente
- [ ] Aisino KeyReceiver recibe todos los datos
- [ ] Comunicación bidireccional funciona (5/5 mensajes)
- [ ] CPU normal durante operación (< 50%)
- [ ] Sin errores TX (-1) durante envío

---

## 🎯 Resultado Esperado Final

### **Si Todo Funciona ✅**

```
KeyReceiver (Aisino B):
15:45:12.100  ✅ Puerto virtual abierto: ttyUSB0
15:45:12.150  ¡Conexión establecida! Escuchando
15:45:15.200  🔄 ReadAttempt #100 (5230ms): bytesRead=0, duration=999ms
[... sigue escuchando sin cierre ...]
15:48:00.000  🔄 ReadAttempt #30000 (180000ms): bytesRead=0, duration=999ms

Injector (Aisino A):
15:45:45.500  📤 TX puerto: 26 bytes - TEST MESSAGE
15:45:45.510  ✓ Mensaje enviado exitosamente

KeyReceiver (Aisino B):
15:45:45.520  📥 RX puerto: 26 bytes - 48656C6C6F...
15:45:45.530  ✓ Mensaje parseado y respondido
```

**Conclusión**: Virtual ports implementado correctamente ✅

### **Si Usa Fallback (OK pero sin mejora) ⚠️**

```
KeyReceiver (Aisino B):
15:45:12.100  ℹ️ Ningún puerto virtual disponible
15:45:12.150  ✓ Puerto Rs232 0 abierto (115200bps)
15:45:12.150  ¡Conexión establecida! Escuchando
[... dura ~130 segundos como antes ...]
```

**Conclusión**: Virtuales no disponibles pero fallback funciona ✓

### **Si Falla ❌**

```
15:45:12.100  ❌ Error al abrir puerto Rs232 0: -1
```

**Conclusión**: Hardware/driver problem - revisar conexión

---

**Ready to Deploy**: ✅ YES
**APKs Compiled**: ✅ YES
**Last Update**: 2025-10-24

