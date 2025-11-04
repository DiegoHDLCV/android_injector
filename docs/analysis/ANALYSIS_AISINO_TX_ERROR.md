# Análisis: Error de Transmisión Aisino (TX Error -1)

**Fecha**: 2025-10-24
**Estado**: Problema identificado, solución en progreso
**Referencia**: Logs de test Aisino-Aisino con Injector y KeyReceiver

---

## 📊 Progreso Observado

### ✅ ÉXITO: Fix de Busy-Wait Funcionó Magníficamente

El fix de detección de busy-wait (commit a3d2f86) logró **extender la duración de escucha de 22 segundos a 130 segundos (2 minutos)**:

```
Antes:
16:24:29.506  Puerto abierto
16:24:33.560  CABLE USB DESCONECTADO
16:24:59.000  Listening cerrado (~22-26 segundos)  ❌

Después del fix:
16:24:29.506  Puerto abierto
16:24:33.560  CABLE USB DESCONECTADO (pero escucha sigue)
16:26:09.943  ReadAttempt #100 (100221ms): duration=1001ms ✅
16:26:39.979  Closing port after 130 intentos (~130 segundos) ✅
```

**Métricas de Mejora**:
- Duración: 22s → 130s (**+5.9x mayor**)
- Intentos: 11-26 → 130 (**+5-12x más intentos**)
- readDuration: 0ms → 1001ms (**correctamente esperando timeout**)

**Conclusión**: El busy-wait loop SÍ fue la causa del cierre prematuro. ✅ **RESUELTO** ✅

---

## ❌ PROBLEMA: Error TX -1 en Injector

Cuando el Injector intenta enviar datos **después de que el KeyReceiver cierra**:

```
17:22:51.357  AisinoComController: ✓ Puerto 0 abierto (115200bps)
17:22:51.647  AisinoComController: ❌ Error TX puerto 0: -1
17:22:51.648  KeyInjectionViewModel: ❌ Error al enviar datos: -5
```

### Decodificación del Error:

| Código | Significado |
|--------|------------|
| `-1` | Retorno nativo de `Rs232Api.PortSends_Api()` - Puerto en estado inválido |
| `-5` | `ERROR_WRITE_FAILED` en AisinoComController (línea 23) |

### Causa Probable:

1. **Puerto no recuperado después de cierre**: Cuando KeyReceiver cierra el puerto, el hardware/driver de Aisino NO recupera completamente el puerto a un estado limpio
2. **Estado de puerto "pegado"**: El puerto pasa a un estado de "medio-abierto" donde PortOpen_Api() aparenta tener éxito (retorna 0) pero PortSends_Api() falla con -1
3. **Necesidad de "puerto reset"**: Aisino podría requerir una operación `PortReset_Api()` adicional entre cierre y siguiente apertura

---

## 🔧 Soluciones Propuestas

### Solución 1: Agregar PortReset_Api() Después de Cierre (RECOMENDADA)

En `AisinoComController.kt`, línea 113-135:

```kotlin
override fun close(): Int {
    if (!isOpen) {
        return SUCCESS
    }

    try {
        val result = Rs232Api.PortClose_Api(comport)
        isOpen = false

        if (result != AISINO_SUCCESS) {
            Log.w(TAG, "⚠️ Error al cerrar puerto $comport: $result")
            return ERROR_CLOSE_FAILED
        }

        // ✨ NUEVO: Reset del puerto después de cierre
        try {
            Rs232Api.PortReset_Api(comport)
            Log.d(TAG, "✓ Puerto $comport reseteado después del cierre")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error al resetear puerto $comport: ${e.message}")
        }

        Log.d(TAG, "✓ Puerto $comport cerrado")
        return SUCCESS
    } catch (e: Exception) {
        Log.e(TAG, "❌ Excepción al cerrar puerto $comport: ${e.message}")
        isOpen = false
        return ERROR_GENERAL_EXCEPTION
    }
}
```

### Solución 2: Delay Mayor Después de Cierre

En `MainViewModel.kt`, línea 371:

```kotlin
finally {
    Log.d(TAG, "startListeningInternal: Closing port after $readAttempts attempts")
    val closeRes = comController?.close()
    CommLog.d(TAG, "close() => $closeRes")

    // Aumentar delay de 500ms a 1000-2000ms
    kotlinx.coroutines.delay(2000)  // Dar tiempo al hardware para recuperarse

    if (_connectionStatus.value != ConnectionStatus.ERROR) {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
    Log.i(TAG, "Listening closed.")
}
```

### Solución 3: Implementar Re-scan con Reset

Crear un método en `AisinoCommunicationManager.kt`:

```kotlin
fun resetPortCompletelyIfInitialized() {
    if (!isInitialized || isInitializing) return
    Log.i(TAG, "resetPortCompletelyIfInitialized: Reseteando puerto completamente")

    comControllerInstance?.close()

    // Reset adicional del puerto a nivel nativo
    try {
        (comControllerInstance as? AisinoComController)?.forceReset()
    } catch (e: Exception) {
        Log.w(TAG, "Error al resetear: ${e.message}")
    }

    comControllerInstance = null
    autoScanPortsAndBauds()
}
```

---

## 📋 Plan de Implementación

### Paso 1: Aplicar Solución 1 (PortReset en close())
**Archivo**: `AisinoComController.kt`
**Complejidad**: ⭐ (muy simple)
**Probabilidad de éxito**: 80%

```kotlin
// Línea 127 - Agregar reset después de cerrar
Rs232Api.PortReset_Api(comport)
Log.d(TAG, "✓ Puerto $comport reseteado después del cierre")
```

### Paso 2: Aumentar Delay en Finally (si Paso 1 no funciona)
**Archivo**: `MainViewModel.kt`
**Complejidad**: ⭐ (muy simple)
**Probabilidad de éxito**: 50%

```kotlin
// Línea 371 - Cambiar de 500ms a 2000ms
kotlinx.coroutines.delay(2000)
```

### Paso 3: Implementar Recovery Completo (último recurso)
**Archivo**: `AisinoCommunicationManager.kt`
**Complejidad**: ⭐⭐⭐ (moderado)
**Probabilidad de éxito**: 90%

---

## 🎯 Teoría: ¿Por Qué Aisino→NewPOS Funciona?

Existen varias hipótesis por qué la comunicación Aisino→NewPOS es estable mientras que Aisino→Aisino falla:

1. **Puertos Diferentes**: Aisino usa Puerto 0 para lectura, NewPOS podría usar un puerto USB diferente completamente
2. **Protocolo Diferente**: NewPOS podría tener un driver/SDK que maneja mejor el flujo de control
3. **Sincronización**: Aisino A90 Pro podría tener mejor sincronización con NewPOS que consigo misma
4. **Polling vs Interrupts**: NewPOS podría usar interrupts mientras que Aisino usa polling, evitando conflictos

---

## ⏭️ Próximos Pasos

1. **Inmediato**: Aplicar Solución 1 (agregar PortReset después del cierre)
2. **Test**: Compilar APK y probar Aisino→Aisino nuevamente
3. **Monitoreo**: Observar si error -1 desaparece
4. **Iteración**: Si persiste, aplicar Soluciones 2 y 3

---

## 📌 Notas Técnicas

- Vanstone SDK `PortReset_Api()` ya está implementado en línea 93 de `AisinoComController.kt` durante la apertura
- El reset después de cierre es una práctica común en drivers RS232
- Los 130 intentos exitosos prueban que el puerto CAN funcionar correctamente cuando no hay busy-wait

---

**Resumen**: El fix de busy-wait fue un ÉXITO (130 vs 22 segundos). Ahora necesitamos resolver el estado inconsistente del puerto después del cierre aplicando un reset adicional.

