# Plan de Pruebas: Aisino Injector ↔ Aisino KeyReceiver (Fix Busy-Wait)

**Estado**: Listo para testing con dispositivos reales
**Commits Aplicados**: a3d2f86, e197a1b, 8903f75, fb178c1, e2bed76
**Causa Raíz Identificada**: `readData()` retorna en 0ms sin esperar timeout → busy-wait loop
**Fix Principal**: Detección de busy-wait + reducción de logs masivos

---

## 📱 APKs Generados

Ambas APKs están compiladas y listas para desplegar:

```
✅ Aisino Injector:
   /Users/diegoherreradelacalle/StudioProjects/android_injector/injector/build/outputs/apk/debug/injector-debug.apk

✅ Aisino KeyReceiver:
   /Users/diegoherreradelacalle/StudioProjects/android_injector/keyreceiver/build/outputs/apk/debug/keyreceiver-debug.apk
```

**Pasos de Despliegue**:
1. Desinstalar versiones anteriores de ambas apps
2. Instalar `injector-debug.apk` en Aisino con Injector
3. Instalar `keyreceiver-debug.apk` en Aisino con KeyReceiver
4. Reiniciar ambos dispositivos

---

## 🎯 Métricas Críticas a Observar

### 1. **Duración de la Escucha (MAIN)**
| Escenario | Esperado Antes | Esperado Después | Lo que Indica |
|-----------|----------------|------------------|---------------|
| Aisino → Aisino | ~22-26 segundos ❌ | **Minutos** ✅ | Si < 30s: aún hay problema |
| Aisino → NewPOS | Funciona | Sigue funcional | Control de regresión |

**Dónde observar**:
- KeyReceiver logs: `"Listening closed."` o `"Closing port after N attempts"`
- Esperar varios minutos antes de ver cierre
- Si cierra antes de 1 minuto: el fix no funcionó

### 2. **Frecuencia de Logs DEBUG**
| Métrica | Antes | Después | Ruta en Logs |
|---------|-------|---------|--------------|
| DEBUG logs | Cada 20 intentos | Cada 100 intentos | `🔄 ReadAttempt #N` |
| Logs por 30 segundos | ~575k líneas | ~100-150 líneas | Ver en LogCat |

**Interpretación**:
- Si ves `🔄 ReadAttempt #100`, luego `#200`, etc. en intervalos sensatos → ✅ OK
- Si ves spam continuo de logs → ❌ Problema aún persiste

### 3. **Duración de Lectura (readDuration)**
| Parámetro | Antes (MALO) | Después (BUENO) |
|-----------|-------------|-----------------|
| `duration=0ms` | ~19,400 intentos/seg | < 5% del tiempo |
| `duration~1000ms` | Casi nunca | ~50% del tiempo |

**Ejemplo de log BUENO**:
```
🔄 ReadAttempt #100 (5230ms): bytesRead=0, duration=1001ms
🔄 ReadAttempt #200 (10460ms): bytesRead=0, duration=999ms
🔄 ReadAttempt #300 (15690ms): bytesRead=26, duration=5ms ← Datos llegaron rápido
```

**Ejemplo de log MALO**:
```
🔄 ReadAttempt #575180 (29625ms): bytesRead=0, duration=0ms
🔄 ReadAttempt #575200 (29626ms): bytesRead=0, duration=0ms  ← BUSY-WAIT
```

### 4. **Detección de Busy-Wait (delay(50) invocado)**
| Indicador | Significado |
|-----------|------------|
| Logs muestran delay(50) constantemente | readData() aún no espera el timeout |
| No hay delay(50) mencionado | ✅ readData() ahora respeta timeout |

**Dónde observar**:
- Búsqueda en logs por `"delay"` o `"50"`
- Si se invoca constantemente → problema en Rs232Api nativa

---

## 🧪 Protocolo de Prueba Paso a Paso

### **Fase 1: Preparación (5 min)**

```bash
# En KeyReceiver Aisino:
1. Abrir app de KeyReceiver
2. Ir a Home o pantalla de status
3. Tomar nota de timestamp inicial: ___:___:___

# En Injector Aisino:
1. Abrir app de Injector
2. Ir a pantalla de envío de datos o inyección
```

### **Fase 2: Iniciar Escucha (30 segundos)**

```bash
# En KeyReceiver:
1. Presionar "Conectar" o similar para iniciar listening
2. Observar logs de inicio:
   ✓ "¡Conexión establecida! Escuchando"
   ✓ "Puerto abierto exitosamente"
   ✓ "ReadAttempt #20" (dentro de 2-5 segundos)
```

**Log Esperado BUENO**:
```
15:45:12.100  I: ¡Conexión establecida! Escuchando en protocolo FUTUREX
15:45:12.150  D: Puerto abierto exitosamente en intento #1
15:45:15.200  D: 🔄 ReadAttempt #100: bytesRead=0, duration=999ms
```

### **Fase 3: Envío de Datos desde Injector (1 minuto)**

```bash
# En Injector:
1. Enviar un mensaje de prueba (ej: "TEST")
2. Esperar respuesta

# En KeyReceiver:
1. Observar si recibe datos:
   ✓ "RX Nº bytes: HEXDATA"
   ✓ Mensaje parseado correctamente
   ✓ Respuesta enviada al Injector
```

**Log Esperado BUENO**:
```
15:45:45.500  D: 🔄 ReadAttempt #350: bytesRead=26, duration=5ms
15:45:45.510  I: 📥 RX puerto 0: 26 bytes - 48656C6C6F...
15:45:45.520  I: ✓ Mensaje parseado: ProtocolMessage
```

### **Fase 4: Monitoreo Largo Plazo (5-10 minutos)**

```bash
# Dejar corriendo:
1. KeyReceiver escuchando
2. Injector en espera (sin enviar)
3. Observar en logs:
   - Si cierra escucha → PROBLEMA ❌
   - Si sigue escuchando después de 1 min → ÉXITO ✅
   - Logs cada 100 intentos, no spam → ÉXITO ✅
```

**Log Esperado BUENO después de 5 minutos**:
```
15:50:30.100  D: 🔄 ReadAttempt #30000: bytesRead=0, duration=1001ms
15:50:30.200  D: 🔄 ReadAttempt #30100: bytesRead=0, duration=999ms
[... sigue escuchando ...]
```

**Log MALO - Cierre Prematuro**:
```
15:45:15.500  D: ⚠️ Loop EXITING: isActive became false after 26 attempts
15:45:15.600  I: Listening closed.
```

### **Fase 5: Envío Múltiple (2 minutos)**

```bash
# En Injector:
1. Enviar 3-5 mensajes separados
2. Espaciarlos 10 segundos

# En KeyReceiver:
1. Debe recibir TODOS los mensajes
2. Debe responder a cada uno
3. Escucha debe permanecer activa durante todo
```

---

## 📊 Matriz de Resultados Esperados

| Métrica | FALLA ❌ | PARCIAL ⚠️ | ÉXITO ✅ |
|---------|---------|----------|---------|
| Duración escucha | < 30s | 30s-2min | > 5 min |
| Logs/30seg | > 100k líneas | 100k-1k líneas | < 200 líneas |
| readDuration | Siempre 0ms | Mezcla | ~1000ms cuando vacío |
| Recepción de datos | 0/5 mensajes | 1-3/5 mensajes | 5/5 mensajes ✓ |
| CPU durante escucha | 100% | 50-80% | < 30% |
| Error logs | Muchos | Algunos | Solo normales |

---

## 🔍 Diagnóstico por Síntoma

### Síntoma 1: Escucha cierra después de ~22 segundos
**Causa Probable**: Busy-wait loop no se arregló
**Acciones**:
1. Verificar logs por `duration=0ms`
2. Buscar `delay(50)` invocado constantemente
3. Revisar que `readData(1000)` realmente espera 1000ms
4. **Solución**: Aumentar timeout a 2000ms o 3000ms

### Síntoma 2: Logs aún muestran spam (> 100k líneas/min)
**Causa Probable**: Cambio de logging no aplicó correctamente
**Acciones**:
1. Verificar que logs cambiaron de `every 20` a `every 100`
2. Rebuild sin cache: `./gradlew clean keyreceiver:assembleDebug`
3. Verificar que cambios están en MainViewModel.kt línea 304

### Síntoma 3: No recibe datos del Injector
**Causa Probable**: Puerto no abierto o no configurado
**Acciones**:
1. Verificar log de apertura de puerto: `"Puerto abierto exitosamente"`
2. Verificar baudios coinciden: `115200 bps`
3. Verificar puerto: `comport=0`
4. Revisar que cables USB/RS232 están bien conectados

### Síntoma 4: Aisino→Aisino sigue sin funcionar pero Aisino→NewPOS sí
**Causa Probable**: Problema específico de puerto Aisino-Aisino
**Acciones**:
1. Buscar excepciones en logs: `"❌ EXCEPCIÓN en readData()"`
2. Buscar códigos de error negativos: `"readData error code: -X"`
3. Revisar `AisinoComController.readData()` en línea 171
4. Posible problema en Rs232Api nativa de Vanstone

---

## 📝 Checklist de Validación

- [ ] Ambas APKs compiladas exitosamente
- [ ] Instaladas en dispositivos (ambas apps sin versión anterior)
- [ ] KeyReceiver inicia y abre puerto 0 @ 115200bps
- [ ] Injector puede conectar y comunicar (prueba antes con NewPOS)
- [ ] Escucha dura > 1 minuto sin cerrar
- [ ] Logs son legibles (< 200 líneas por minuto)
- [ ] Aisino Injector envía datos → Aisino KeyReceiver recibe ✅
- [ ] Aisino KeyReceiver responde → Aisino Injector recibe ✅
- [ ] Comunicación bidireccional funciona correctamente
- [ ] CPU normal durante operación (< 50%)

---

## 📞 Próximos Pasos Basados en Resultados

### Si TODO funciona ✅
1. Hacer commit: `[DIEGOH][AI-75] ÉXITO: Aisino-Aisino comunicación restaurada`
2. Crear PR a main
3. Documentar cambios en issue AI-75

### Si hay parcial improvement ⚠️
1. Revisar qué métrica aún es baja
2. Aumentar timeout a 2000ms o 3000ms
3. Reducir frecuencia de logs aún más (cada 200 intentos)
4. Re-test

### Si sigue sin funcionar ❌
1. Revisar que código tiene exactamente los cambios del commit a3d2f86
2. Buscar en Rs232Api si ignora timeout
3. Posible issue en nivel nativo de Vanstone SDK
4. Considerar implementar poll-based reading en Kotlin en lugar de blocking

---

## 📌 Notas Importantes

- **NO** cambiar timeout nuevamente sin documentar el cambio
- **NO** desactivar el busy-wait detection (`readDuration < 50`)
- **NO** aumentar frecuencia de logs (ya está en 100 que es apropiado)
- **SÍ** mantener una copia de los logs de prueba para referencia
- **SÍ** probar tanto en `adb logcat` como en la app de LogCat si está disponible

---

## ✅ Resumen del Fix Implementado

```kotlin
// ANTES (MALO):
while (isActive) {
    readAttempts++
    val bytesRead = comController!!.readData(buffer.size, buffer, 1000)
    // Sin detección de busy-wait → readDuration=0ms → CPU al 100%
    if (bytesRead > 0) { /* procesar */ }
}

// DESPUÉS (BUENO):
while (isActive) {
    readAttempts++
    val readStartTime = System.currentTimeMillis()
    val bytesRead = comController!!.readData(buffer.size, buffer, 1000)
    val readDuration = System.currentTimeMillis() - readStartTime

    // ✨ CRÍTICA: Detect busy-wait
    if (readDuration < 50 && bytesRead == 0) {
        kotlinx.coroutines.delay(50)  // Prevent CPU saturation
    }

    if (bytesRead > 0) { /* procesar */ }
}
```

El fix es **mínimo pero efectivo**: si readData() retorna sin esperar, agregamos un delay preventivo.

---

**Última actualización**: 2025-10-24
**Autor**: AI Assistant
**Estado**: Listo para pruebas en dispositivos reales
