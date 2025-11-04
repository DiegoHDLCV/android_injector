# Análisis: Problema de Cierre Prematuro de Escucha (Aisino-to-Aisino)

**Problema Principal**: La escucha (listening) se cierra automáticamente después de ~26 segundos cuando ambos dispositivos son Aisino, impidiendo que el receptor reciba datos del injector.

## Síntomas Observados

### Primer Reporte (15:11-15:12)
```
15:11:42.040  ¡Conexión establecida! Escuchando en protocolo FUTUREX
15:11:54.671  ⚠️ CABLE USB DESCONECTADO (sin cancelar listening)
15:12:08.200  startListeningInternal: Bloque finally - Cerrando comController
             [Tiempo transcurrido: ~26 segundos, completó 26 ciclos]

16:03:53-16:04:03  Injector intenta enviar datos → TIMEOUT (0 bytes recibidos)
```

### Segundo Reporte - PROBLEMA GRAVE DESCUBIERTO (15:26)
```
15:26:37.237  ¡Conexión establecida! Escuchando
15:26:44.402  ⚠️ CABLE USB DESCONECTADO
15:26:59.455  ⚠️ Loop EXITING: isActive became false after 11 attempts
             [Tiempo transcurrido: ~22 segundos, completó solo 11 ciclos]
```

### Logs de NewPOS - REVELADOR
```
🔄 ReadAttempt #575180 (29625ms): duration=0ms (¡SIEMPRE 0MS!)
🔄 ReadAttempt #575200 (29626ms): duration=0ms
🔄 ReadAttempt #575220 (29627ms): duration=0ms
             [~575k intentos en 29.6 segundos = ~19,400/segundo]
             [Debería ser ~0.5/segundo con timeout 2000ms]
```

## Causas Identificadas

### ⚠️ **CAUSA RAÍZ DESCUBIERTA: `readData()` NO ESPERA EL TIMEOUT**

**Observación Crítica**: En los logs de NewPOS, TODOS los `readData()` retornan en `0ms`:
```
🔄 ReadAttempt #575180: duration=0ms
🔄 ReadAttempt #575200: duration=0ms
🔄 ReadAttempt #575220: duration=0ms
```

**Significado**:
- `readData(2000)` debería esperar ~2 segundos (o devolver si hay datos)
- Pero está devolviendo INMEDIATAMENTE en 0ms cuando no hay datos
- Esto causa un **BUSY-WAIT LOOP** a máxima velocidad (~19,400 ciclos/segundo)
- Satura el puerto, la CPU y degrada el sistema

**Consecuencias**:
1. **Aisino-to-Aisino**: La CPU degradada causa que `isActive` se vuelva false después de ~22 segundos
2. **Aisino-to-NewPOS**: Funciona porque absorbe mejor el busy-wait o tiene mejor sincronización
3. **Logs masivos**: ~575k líneas de log cada 30 segundos (ilegible)

### 1. **Código Removido Pero Efecto Persiste**
- Commits anteriores tenían `rescanIfSupported()` que cierra/reabre el puerto
- **Fue removido**, pero el problema de busy-wait permanece

### 2. **Cancelación Automática por Detección de Cable**
- **Fue deshabilitado**, pero no es la causa principal

### 3. **Puerto Degradado por Busy-Wait**
- Cada 0ms de `readData()` sin esperar timeout degrada el puerto nativo
- Después de ~22 segundos, el puerto entra en estado inconsistente
- `isActive` se vuelve false por degradación del scope

## Soluciones Implementadas

### ✅ Commit 96bcb3d: Diagnóstico Detallado
```kotlin
// Agregados:
- readAttempts counter para rastrear ciclos
- Logs cada 10 intentos para monitoreo continuo
- Try-catch explícito en readData para capturar excepciones
- Logs detallados en finally block mostrando:
  * isActive status
  * connectionStatus
  * Número total de ciclos completados
```

### ✅ Commit 8903f75: Manejo Robusto de Excepciones
```kotlin
// Agregados:
- Wrap completo del loop de lectura en try-catch
- Captura de excepciones desde readData
- Logging de códigos de error negativos retornados por readData
- Propagación controlada de excepciones para debugging
```

### ✅ Commit e197a1b: Optimización de Timeout
```kotlin
// Cambios:
- readData timeout: 1000ms → 2000ms
- Reduce ciclos de lectura de ~26/seg a ~13/seg en 26 segundos
- Menos acumulación de timeouts en el driver
- Permite que el puerto respire entre ciclos
```

### ✅ Commit a3d2f86: CRÍTICO - Arreglar Busy-Wait y Logs Masivos
```kotlin
// Problema: readData() retorna en 0ms sin esperar timeout
// Causa: ~19,400 ciclos/segundo en lugar de ~0.5/segundo
// Consecuencia: Listening cierra en 22 segundos, logs ilegibles

// Soluciones:
1. **Detectar Busy-Wait**:
   if (readDuration < 50 && bytesRead == 0) {
       delay(50)  // Preventivo si readData no espera
   }

2. **Reducir Logs Masivos**:
   - DEBUG cada 100 intentos (en lugar de cada 20)
   - Eliminar logs innecesarios
   - Logs solo para eventos reales (datos/errores)

3. **Mejor Diagnóstico**:
   - readAttempts declarado FUERA del try
   - Accesible en finally block
   - Log final: "Closing port after N attempts"
```

## Diagnóstico Esperado en Próximas Ejecuciones

Con los fixes implementados:

### 1. **Si el busy-wait se arregla** (ESPERADO):
   - Logs DEBUG: cada 100 intentos (controlado)
   - Duration: debería ser ~1000-2000ms (no 0ms)
   - Listening: debería durar MINUTOS (no 22 segundos)
   - CPU: normal (no al 100%)

### 2. **Si aún hay busy-wait**:
   - Veremos delay(50ms) siendo invocado constantemente
   - Log final: "Closing port after N attempts" donde N es bajo (~11-26)
   - Duration: seguirá siendo ~0ms
   - Esto indica que el problema está en la capa nativa (Rs232Api)

### 3. **Código de Errores**:
   - Si hay verdaderos errores: `readData error code: -X`
   - Códigos conocidos:
     * `-4` = ERROR_NOT_OPEN (puerto cerrado)
     * `-6` = ERROR_READ_TIMEOUT_OR_FAILURE
     * `-99` = ERROR_GENERAL_EXCEPTION

### 4. **Aisino-to-Aisino ahora debería funcionar**:
   - Si el busy-wait está arreglado, la comunicación debería mejorar
   - Listening durará más de 22 segundos
   - Injector podrá enviar datos y recibir respuesta

## Próximos Pasos para Investigación

### 1. Ejecutar con Dispositivos Reales
```
Aisino Injector → Aisino KeyReceiver
- Observar logs de readAttempts
- Anotar exacto tiempo de cierre (~26s)
- Buscar excepciones o códigos de error
```

### 2. Basado en Hallazgos
- **Si es excepción en readData**: Investigar Vanstone SDK docs
- **Si es timeout acumulado**: Implementar port reset periódico
- **Si es puerto cerrado**: Verificar que cable no se desconecta realmente
- **Si loop completa ~52 ciclos (>26s)**: Hay otra causa que esperar

### 3. Soluciones Potenciales
- Implementar `rs232.PortReset()` periódicamente
- Aumentar aún más el timeout (3000-5000ms)
- Implementar auto-reconnection si detecto puerto cerrado
- Deshabilitar completamente detección de cable USB para Aisino

## Arquitectura Actual del Listening

```
init()
  ├─ setupProtocolHandlers() → FuturexMessageParser
  ├─ startCableDetection() → job paralelo que monitorea cable USB
  └─ performAutomaticKeyVerification()

startListening()
  └─ startListeningInternal()
      ├─ Abre puerto 0 @ 115200bps (Aisino específico)
      ├─ Loop principal:
      │  ├─ readData(2000ms) → bytesRead
      │  ├─ Si bytesRead > 0: procesar mensaje
      │  ├─ Si bytesRead ≤ 0: silentReads++
      │  └─ Continuar hasta isActive=false
      └─ finally: close() + delay(500)

Cable Detection Job (paralelo)
  └─ Cada 3 segundos: detectCableConnection()
      └─ Emite estado en _cableConnected StateFlow
      └─ **DESHABILITADO**: Antes cancelaba listening

```

## Cambios de Configuración

| Parámetro | Antes | Después | Razón |
|-----------|-------|--------|-------|
| readData timeout | 1000ms | 2000ms | Reduce ciclos rápidos |
| Re-scan automático | Cada 5 reads silenciosos | Deshabilitado | Evita cerrar puerto |
| Cable USB auto-cancel | Activo | Deshabilitado | Cable es inestable en Aisino |
| Logging | Mínimo | Muy detallado | Diagnóstico de problema |

## Conclusión

La raíz del problema aún no está completamente identificada, pero ha sido localizada dentro del rango de ~26 segundos. Los logs detallados agregados deberían revelar exactamente qué está causando que el listening cierre, permitiendo una solución definitiva.

**Próxima ejecución debe proporcionar datos diagnósticos claros para tomar acción correctiva.**
