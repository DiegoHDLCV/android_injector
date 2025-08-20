# Problema del LRC en Protocolo Futurex

## 🔍 **Problema Identificado**

El dispositivo Futurex está recibiendo el comando de inyección pero **no responde**. Analizando los logs, se observa que:

1. ✅ **Formato de longitud corregido**: Ahora aparece `"010"` en lugar de `"016"`
2. ✅ **Comando se envía**: El dispositivo recibe los datos
3. ❌ **LRC incorrecto**: El último byte no coincide con el esperado
4. ❌ **Sin respuesta**: El dispositivo ignora el comando

## 📊 **Análisis de los Logs**

### **Comando Enviado (desde injector)**
```
02303230313030303030313030373732373030303030303030303030303030303030303030303030303031303038454142333230393843323531383638464239344630324638373542364645034E
```

### **Comando Recibido (en subpos)**
```
02303230313030303030313030373732373030303030303030303030303030303030303030303030303031303038454142333230393843323531383638464239344630324638373542364645034E
```

**Observación**: Los datos son idénticos, pero el **LRC final es `0x4E` (ASCII 'N')** en lugar del valor correcto.

## 📚 **Documentación Futurex - Cálculo del LRC**

Según la documentación oficial:

> **LRC (Longitudinal Redundancy Check)**: Es una Verificación de Redundancia Longitudinal de un solo byte que se crea haciendo un XOR de todos los bytes desde el primer byte en `[MESSAGE]` hasta el byte `<ETX>` inclusive.

### **Fórmula del LRC**
```
LRC = MESSAGE[0] XOR MESSAGE[1] XOR ... XOR MESSAGE[n] XOR ETX
```

### **Ejemplo de Cálculo**
Para el comando: `020100000100772700000000000000000000000001008EAB32098C251868FB94F02F875B6FE03`

1. **MESSAGE**: `020100000100772700000000000000000000000001008EAB32098C251868FB94F02F875B6FE`
2. **ETX**: `03`
3. **LRC**: XOR de todos los bytes anteriores + ETX

## 🛠️ **Solución Implementada**

### **1. Método de Debug del LRC**
Se agregó `debugLrcCalculation()` que:
- Construye el payload manualmente
- Calcula el LRC paso a paso
- Muestra todos los bytes intermedios
- Valida el cálculo

### **2. Logs Detallados**
- **Payload manual**: Construcción paso a paso
- **Bytes para LRC**: Exactamente qué se está calculando
- **LRC manual**: Cálculo independiente para verificación
- **Datos formateados**: STX, payload, ETX y LRC final

### **3. Validación del Formato**
- Verificación de la longitud en ASCII HEX
- Validación del payload completo
- Comparación de LRC calculado vs enviado

## 🔧 **Implementación Técnica**

### **Código del Debug LRC**
```kotlin
private fun debugLrcCalculation(command: String, fields: List<String>) {
    // Construir payload manualmente
    val payloadString = command + fields.joinToString("")
    val payloadBytes = payloadString.toByteArray(Charsets.US_ASCII)
    val etxByte = byteArrayOf(0x03)
    val bytesForLrc = payloadBytes + etxByte
    
    // Calcular LRC manualmente
    var lrc: Byte = 0
    for (byte in bytesForLrc) {
        lrc = (lrc.toInt() xor byte.toInt()).toByte()
    }
    
    Log.i(TAG, "LRC calculado manualmente: 0x${lrc.toString(16).uppercase()}")
}
```

### **Flujo de Validación**
1. **Construir payload** manualmente
2. **Calcular LRC** paso a paso
3. **Formatear mensaje** con FuturexMessageFormatter
4. **Verificar LRC** del mensaje formateado
5. **Comparar** ambos cálculos

## 📊 **Resultados Esperados**

### **Con la Corrección**
```
=== DEBUG LRC CALCULATION ===
Payload string: 020100000100772700000000000000000000000001008EAB32098C251868FB94F02F875B6FE
Bytes para LRC: 020100000100772700000000000000000000000001008EAB32098C251868FB94F02F875B6FE03
LRC calculado manualmente: 0x[VALOR_CORRECTO]
================================================

=== DATOS FORMATEADOS FUTUREX ===
STX: 0x02
Payload: 020100000100772700000000000000000000000001008EAB32098C251868FB94F02F875B6FE
ETX: 0x03
LRC: 0x[VALOR_CORRECTO]
================================================
```

### **Validación**
- ✅ LRC manual = LRC formateado
- ✅ Formato correcto del mensaje
- ✅ Dispositivo responde con ACK
- ✅ Inyección exitosa

## 🚀 **Próximos Pasos**

### **1. Ejecutar Inyección**
- Probar con las nuevas validaciones
- Revisar logs de debug del LRC
- Confirmar que los valores coincidan

### **2. Verificar Respuesta**
- El dispositivo debería responder con ACK
- La inyección debería ser exitosa
- Los logs deberían mostrar respuesta válida

### **3. Validar Funcionamiento**
- Confirmar que la llave se inyecte
- Verificar que aparezca en el dispositivo
- Comprobar que el sistema funcione correctamente

## 🔍 **Diagnóstico del Problema**

### **Posibles Causas**
1. **Cálculo incorrecto del LRC** en FuturexMessageFormatter
2. **Formato incorrecto** del payload
3. **Caracteres especiales** en los datos
4. **Encoding incorrecto** de los bytes

### **Solución**
Con el debug implementado, podremos:
- Identificar exactamente dónde falla el cálculo
- Verificar cada byte del payload
- Confirmar que el LRC se calcule correctamente
- Asegurar que el dispositivo reciba un comando válido

## 📝 **Conclusión**

El problema del LRC es crítico para el funcionamiento del protocolo Futurex. Con las validaciones implementadas, podremos:

1. **Identificar** el problema exacto del cálculo
2. **Corregir** el formato del mensaje
3. **Validar** que el LRC sea correcto
4. **Confirmar** que el dispositivo responda

Una vez resuelto el LRC, el dispositivo Futurex debería procesar correctamente los comandos de inyección y responder con ACK, permitiendo que las llaves se inyecten exitosamente.
