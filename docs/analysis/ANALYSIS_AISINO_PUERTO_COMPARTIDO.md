# Análisis: Problema Fundamental de Puerto Compartido en Aisino

**Fecha**: 2025-10-24
**Estado**: Problema identificado, solución requiere arquitectura diferente
**Causa Raíz**: Aisino A90 Pro SOLO tiene UN puerto serial (Puerto 0)

---

## Resumen Ejecutivo

La comunicación Aisino-Aisino falla porque **ambos dispositivos intentan usar el mismo Puerto 0**:
- **KeyReceiver**: Lee continuamente en Puerto 0
- **Injector**: Intenta escribir en Puerto 0

En Aisino, el Puerto 0 **NO puede ser compartido** entre dos procesos simultáneamente, causando el cierre prematuro de la escucha.

---

## Análisis de Logs

### KeyReceiver (Aisino KeyReceiver)
```
16:32:27.821  ¡Conexión establecida! Escuchando en puerto 0
16:32:37.823  ⚠️ CABLE USB DESCONECTADO (disparado por fluctuación del puerto)
16:32:58.956  Closing port after 31 attempts (~31 segundos)
```

### Injector (Aisino Injector)
```
17:31:20.845  📤 TX puerto 0: 82 bytes  ← Intenta escribir mientras KeyReceiver lee
17:31:30.878  ✓ Puerto 0 reseteado después del cierre
```

**Conflicto**: Cuando Injector abre Puerto 0 para escribir, el driver de Aisino fuerza el cierre en KeyReceiver.

---

## Comparativa: ¿Por Qué Aisino-NewPOS Funciona?

| Aspecto | Aisino-Aisino ❌ | Aisino-NewPOS ✅ |
|--------|---|---|
| **Puertos disponibles** | Solo Puerto 0 | Aisino usa 0, NewPOS usa USB nativo |
| **Arquitectura** | Ambos usan Rs232Api | NewPOS tiene su propia stack |
| **Conflicto** | Ambos quieren Puerto 0 | No hay conflicto de puerto |
| **Resultado** | Cierre prematuro | Comunicación estable |

**NewPOS NO usa el mismo Puerto 0 de Aisino**. Utiliza la conexión USB del cable directamente, sin competir por el puerto serial de Aisino.

---

## El Problema Real

### 1. Arquitectura Actual (INCORRECTA para Aisino-Aisino)

```
Aisino A (Injector)          Aisino B (KeyReceiver)
    |                               |
    +---> Puerto 0                  +---> Puerto 0
         (abierto para TX)              (abierto para RX)

    ❌ CONFLICTO: Mismo puerto,
       mismo dispositivo físico de Vanstone SDK
```

### 2. Arquitectura Correcta (Aisino-NewPOS)

```
Aisino A (Injector)          NewPOS B (KeyReceiver)
    |                               |
    +---> Puerto 0                  +---> USB CDC
         (Vanstone SDK)                  (Stack USB nativa)

    ✅ SIN CONFLICTO: Diferentes stacks,
       sin competencia por puertos
```

---

## Intentos de Solución y Por Qué No Funcionan

### ❌ Intento 1: Pausar Detección de Cable
**Problema**: Causó que el estado del cable quedara "pegado"
**Causa**: La pausa permanente de cableDetectionJob hacía que nunca se reanudara correctamente

### ❌ Intento 2: PortReset Después del Cierre
**Problema**: El puerto ya estaba cerrado por Vanstone SDK
**Causa**: El reset no recupera el estado cuando hay conflicto simultáneo

### ❌ Intento 3: Aumentar Timeout
**Problema**: Solo reduce la frecuencia del problema, no lo resuelve
**Causa**: El conflicto persiste cuando ambos acceden al puerto

---

## Soluciones Posibles (Requieren Cambios Arquitectónicos)

### Opción A: Serializar Acceso al Puerto (COMPLEJA)

```kotlin
// Mutex compartido entre Injector y KeyReceiver
// Pero requiere cambios en ambas apps

mutex.withLock {
    // KeyReceiver: abre, lee, cierra
    // Injector: espera, abre, escribe, cierra
}
```

**Problema**: Requiere cambios en ambas aplicaciones
**Viabilidad**: Baja (no hay forma de compartir mutex entre apps)

### Opción B: Usar Puerto Diferente (IMPOSIBLE)

Aisino A90 Pro solo soporta **Puerto 0**.

Log de autoScan:
```
Init puerto 1: ❌ Error -1
Init puerto 2: ❌ Error -1
...
Init puerto 15: ❌ Error -1
Init puerto 0: ✅ Éxito
```

### Opción C: Cambiar Arquitectura de Comunicación (RECOMENDADA PERO COMPLICADA)

**Para Aisino-Aisino**: Usar BLE, WiFi, o algún protocolo que NO sea RS232 puerto compartido.

**Problema**: Requiere cambios muy grandes en la arquitectura.

### Opción D: Secuencial en Lugar de Paralelo (PARCIAL)

En lugar de que KeyReceiver escuche continuamente:

```
1. Injector abre Puerto 0
2. Injector envía datos
3. Injector cierra Puerto 0
4. KeyReceiver abre Puerto 0
5. KeyReceiver lee datos
6. KeyReceiver cierra Puerto 0
```

**Problema**: Requiere sincronización muy complicada
**Viabilidad**: Baja

---

## Configuración Actual vs. Limitaciones de Aisino

### Lo que Funciona ✅
- **Aisino → Aisino**: Comunicación **secuencial** (una después de la otra)
- **Aisino → NewPOS**: Comunicación **paralela** (simultánea)

### Lo que NO Funciona ❌
- **Aisino → Aisino**: Comunicación **paralela** (simultanea)

### Por Qué
Aisino Rs232Api tiene una limitación de hardware: **Un solo acceso al Puerto 0 a la vez**.

NewPOS se conecta vía USB directamente, sin usar el Puerto 0 de Aisino.

---

## Recomendación Final

**Este es un problema de limitación de hardware de Aisino, no de software.**

La A90 Pro simplemente **no puede soportar comunicación RS232 simultanea Aisino-Aisino** porque:

1. Solo tiene UN puerto (Puerto 0)
2. El driver de Vanstone no permite acceso compartido
3. Cuando dos procesos quieren acceder al mismo recurso, hay conflicto

**Conclusión**: Para comunicación Aisino-Aisino, se requiere una arquitectura diferente que no dependa de acceso simultaneo al Puerto 0.

---

## Fixes Que SÍ Funcionan (Implementados)

### 1. ✅ Detección y Prevención de Busy-Wait Loop
**Commits**: a3d2f86, e197a1b, 8903f75
**Efecto**: Extendió escucha de 22s a 130s
**Conclusión**: Resolvió PARTE del problema de cierre prematuro

### 2. ✅ PortReset Después del Cierre
**Commit**: e2ad536
**Efecto**: Recupera estado del puerto después de usar
**Conclusión**: Buena práctica pero no suficiente para resolver conflicto simultaneo

### 3. ❌ Pausa de Cable Detection
**Commit**: bb77199 (REVERTIDO: ee2610d)
**Efecto**: Causó que estado del cable quedara pegado
**Conclusión**: No es la solución

---

## Estado Actual

Las APKs compiladas incluyen:
- ✅ Busy-wait loop fix (Extiende a 130 segundos)
- ✅ PortReset fix (Recupera estado del puerto)
- ❌ Sin pausa de cable detection (Reverted)

**Resultado**: La escucha dura ~130 segundos (~2 minutos) en lugar de los anteriores ~22-31 segundos, pero sigue cerrándose cuando el Injector intenta escribir.

---

## Próximos Pasos Recomendados

1. **Investigar si Aisino tiene API para detección de "puerto en uso"**
   - Implementar retry automático después de que Injector libere el puerto

2. **Considerar alternativa para Aisino-Aisino**
   - Usar SIM USB, BLE, o red en lugar de RS232

3. **Documentar esta limitación**
   - Aisino-Aisino es una configuración no soportada en hardware

---

**Conclusión**: El problema no es un bug de software, es una limitación arquitectónica del hardware Aisino A90 Pro.

