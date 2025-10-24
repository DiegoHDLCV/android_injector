# Análisis: Problema de Cierre Prematuro de Escucha (Aisino-to-Aisino)

**Problema Principal**: La escucha (listening) se cierra automáticamente después de ~26 segundos cuando ambos dispositivos son Aisino, impidiendo que el receptor reciba datos del injector.

## Síntomas Observados

```
15:11:42.040  ¡Conexión establecida! Escuchando en protocolo FUTUREX
15:11:54.671  ⚠️ CABLE USB DESCONECTADO (sin cancelar listening)
15:12:08.200  startListeningInternal: Bloque finally - Cerrando comController
             [Tiempo transcurrido: ~26 segundos]

16:03:53-16:04:03  Injector intenta enviar datos → TIMEOUT (0 bytes recibidos)
```

## Causas Identificadas

### 1. **Código Removido Pero Efecto Persiste**
- Commits anteriores tenían `rescanIfSupported()` cada 5 lecturas silenciosas
- Este código cierra/reabre el puerto, causando pérdida de datos
- **Fue removido en commit 426f36f**, pero la escucha sigue cerrando

### 2. **Cancelación Automática por Detección de Cable**
- La detección de cable USB triggers un `stopListening()` automático
- **Fue deshabilitado en commit ca049a6**, pero la escucha sigue cerrando

### 3. **Causa Raíz Probable: Acumulación de Timeouts**
- La escucha llama `readData(1000ms)` en un loop
- Después de ~26 ciclos de 1000ms timeouts = ~26 segundos
- El driver nativo Vanstone (`Rs232Api.PortRecv_Api`) podría estar:
  - Degradando el estado del puerto con cada timeout
  - Alcanzando un límite interno de reintentos
  - Retornando códigos de error que no están siendo manejados correctamente

### 4. **Puerto Inestable Aisino**
- La detección de cable USB Aisino es notoriamente inconsistente
- El puerto RS232 físico podría estar perdiendo estabilidad
- Los timeouts repetidos podrían estar causando un círculo vicioso

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

## Diagnóstico Esperado en Próximas Ejecuciones

Con los logs detallados ahora en lugar:

1. **Si el loop cierra normalmente**:
   - Veremos exactamente cuántos `readAttempts` completó
   - Podremos identificar si es ~26 (coincidiendo con ~26 segundos)
   - O si completa muchos más, indicando otro problema

2. **Si hay excepciones**:
   - `readData() EXCEPCIÓN` será capturado y logueado
   - Veremos el mensaje de excepción exacto
   - Podremos identificar si es del driver nativo o del código Kotlin

3. **Si hay códigos de error**:
   - Veremos log: `readData retornó código de error: -X`
   - Códigos conocidos:
     * `-4` = ERROR_NOT_OPEN (puerto cerrado)
     * `-6` = ERROR_READ_TIMEOUT_OR_FAILURE (timeout)
     * `-99` = ERROR_GENERAL_EXCEPTION

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
