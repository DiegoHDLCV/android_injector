# Debug del Parser en MainViewModel

## 🔍 **Problema Identificado**

El `MainViewModel` está recibiendo datos del puerto serial pero **NO los está procesando**. Los logs muestran:

1. ✅ **Datos se reciben**: `RAW_SERIAL_IN (HEX): ...`
2. ❌ **NO se procesan**: No aparecen logs de parseo
3. ❌ **NO se ejecutan comandos**: Las inyecciones no funcionan

## 📊 **Análisis del Código**

### **Estado Actual**
```kotlin
// En MainViewModel.kt - Función startListening()
val received = buffer.copyOf(bytesRead)
val receivedString = String(received, Charsets.US_ASCII)
val hexString = received.joinToString("") { "%02X".format(it) }

val newData = "RX [${System.currentTimeMillis()}]: HEX($hexString) ASCII('$receivedString')\n"
_rawReceivedData.value += newData

Log.v(TAG, "RAW_SERIAL_IN (HEX): $hexString (ASCII: '$receivedString')")
CommLog.i(TAG, "RX ${bytesRead}B: $hexString")

// ⚠️ CRÍTICO: Enviar datos al parser para procesamiento
Log.i(TAG, "=== PROCESANDO DATOS RECIBIDOS ===")
Log.i(TAG, "Bytes recibidos: ${received.size}")
Log.i(TAG, "Parser configurado: ${messageParser::class.simpleName}")
Log.i(TAG, "Protocolo actual: ${SystemConfig.commProtocolSelected}")

try {
    Log.i(TAG, "Enviando datos al parser Futurex...")
    messageParser.appendData(received)
    Log.i(TAG, "✓ Datos enviados al parser")
    
    // Procesar mensajes parseados
    Log.i(TAG, "Intentando parsear mensajes...")
    var parsedMessage = messageParser.nextMessage()
    var messageCount = 0
    
    while (parsedMessage != null) {
        messageCount++
        Log.i(TAG, "Mensaje parseado #$messageCount: $parsedMessage")
        processParsedCommand(parsedMessage)
        parsedMessage = messageParser.nextMessage()
    }
    
    if (messageCount == 0) {
        Log.i(TAG, "⚠️ No se pudo parsear ningún mensaje completo")
    } else {
        Log.i(TAG, "✓ Se procesaron $messageCount mensajes")
    }
} catch (e: Exception) {
    Log.e(TAG, "✗ Error procesando datos: ${e.message}", e)
}

Log.i(TAG, "================================================")

_snackbarEvent.emit("Datos recibidos: ${bytesRead} bytes")
```

## 🛠️ **Logs de Debug Agregados**

### **1. Inicialización del MainViewModel**
```kotlin
init {
    Log.i(TAG, "=== INICIALIZANDO MAINVIEWMODEL ===")
    Log.i(TAG, "Configuración inicial:")
    Log.i(TAG, "  - Manager seleccionado: ${SystemConfig.managerSelected}")
    Log.i(TAG, "  - Protocolo seleccionado: ${SystemConfig.commProtocolSelected}")
    Log.i(TAG, "  - Rol del dispositivo: ${SystemConfig.deviceRole}")
    
    setupProtocolHandlers()
    
    Log.i(TAG, "✓ MainViewModel inicializado completamente")
    Log.i(TAG, "================================================")
}
```

### **2. Setup de Protocol Handlers**
```kotlin
private fun setupProtocolHandlers() {
    Log.i(TAG, "=== SETUP PROTOCOL HANDLERS ===")
    Log.i(TAG, "Protocolo seleccionado: ${SystemConfig.commProtocolSelected}")
    
    messageParser = when (SystemConfig.commProtocolSelected) {
        CommProtocol.LEGACY -> {
            Log.i(TAG, "Creando LegacyMessageParser")
            LegacyMessageParser()
        }
        CommProtocol.FUTUREX -> {
            Log.i(TAG, "Creando FuturexMessageParser")
            FuturexMessageParser()
        }
    }
    
    messageFormatter = when (SystemConfig.commProtocolSelected) {
        CommProtocol.LEGACY -> {
            Log.i(TAG, "Usando LegacyMessageFormatter")
            LegacyMessageFormatter
        }
        CommProtocol.FUTUREX -> {
            Log.i(TAG, "Usando FuturexMessageFormatter")
            FuturexMessageFormatter
        }
    }
    
    Log.i(TAG, "✓ Parser configurado: ${messageParser::class.simpleName}")
    Log.i(TAG, "✓ Formatter configurado: ${messageFormatter::class.simpleName}")
    Log.i(TAG, "================================================")
}
```

### **3. Start Listening**
```kotlin
fun startListening(...) = viewModelScope.launch {
    Log.i(TAG, "=== START LISTENING SOLICITADO ===")
    Log.i(TAG, "Estado actual: ${_connectionStatus.value}")
    Log.i(TAG, "Parser configurado: ${if (::messageParser.isInitialized) messageParser::class.simpleName else "NO INICIALIZADO"}")
    Log.i(TAG, "Formatter configurado: ${if (::messageFormatter.isInitialized) messageFormatter::class.simpleName else "NO INICIALIZADO"}")
    
    // ... resto del código
}
```

## 🔍 **Posibles Causas del Problema**

### **1. MainViewModel No Se Inicializa**
- **Síntoma**: No aparecen logs de inicialización
- **Causa**: Error en inyección de dependencias de Hilt
- **Solución**: Verificar logs de Hilt y dependencias

### **2. setupProtocolHandlers No Se Ejecuta**
- **Síntoma**: No aparecen logs de setup
- **Causa**: Excepción en el init
- **Solución**: Agregar try-catch en el init

### **3. Parser No Se Crea Correctamente**
- **Síntoma**: Parser aparece como "NO INICIALIZADO"
- **Causa**: Error en la creación del parser
- **Solución**: Verificar imports y clases

### **4. startListening No Se Llama**
- **Síntoma**: No aparecen logs de start listening
- **Causa**: La función no se invoca
- **Solución**: Verificar llamadas desde la UI

## 📋 **Logs Esperados**

### **1. Inicialización**
```
=== INICIALIZANDO MAINVIEWMODEL ===
Configuración inicial:
  - Manager seleccionado: AISINO
  - Protocolo seleccionado: FUTUREX
  - Rol del dispositivo: SUBPOS
=== SETUP PROTOCOL HANDLERS ===
Protocolo seleccionado: FUTUREX
Creando FuturexMessageParser
Usando FuturexMessageFormatter
✓ Parser configurado: FuturexMessageParser
✓ Formatter configurado: FuturexMessageFormatter
================================================
✓ MainViewModel inicializado completamente
================================================
```

### **2. Start Listening**
```
=== START LISTENING SOLICITADO ===
Estado actual: DISCONNECTED
Parser configurado: FuturexMessageParser
Formatter configurado: FuturexMessageFormatter
```

### **3. Procesamiento de Datos**
```
=== PROCESANDO DATOS RECIBIDOS ===
Bytes recibidos: 78
Parser configurado: FuturexMessageParser
Protocolo actual: FUTUREX
Enviando datos al parser Futurex...
✓ Datos enviados al parser
Intentando parsear mensajes...
Mensaje parseado #1: InjectSymmetricKeyCommand(...)
✓ Se procesaron 1 mensajes
================================================
```

## 🚀 **Próximos Pasos de Debug**

### **1. Verificar Inicialización**
- Buscar logs de inicialización del MainViewModel
- Confirmar que setupProtocolHandlers se ejecute
- Verificar que el parser se cree correctamente

### **2. Verificar Start Listening**
- Confirmar que startListening se llame
- Verificar el estado del parser y formatter
- Confirmar que no haya errores de inicialización

### **3. Verificar Procesamiento**
- Confirmar que los datos lleguen al parser
- Verificar que el parseo funcione
- Confirmar que los comandos se procesen

### **4. Verificar Dependencias**
- Confirmar que Hilt funcione correctamente
- Verificar que las importaciones estén correctas
- Confirmar que las clases existan

## 📊 **Estado del Debug**

- ✅ **Código agregado**: Logs de debug implementados
- ✅ **Inicialización**: Logs de init agregados
- ✅ **Setup**: Logs de setupProtocolHandlers agregados
- ✅ **Start Listening**: Logs de startListening agregados
- ✅ **Procesamiento**: Logs de procesamiento agregados
- 🔄 **Pruebas**: Pendiente de ejecutar y verificar logs

## 🎯 **Resultado Esperado**

Con estos logs de debug, deberíamos poder identificar exactamente dónde está fallando el proceso:

1. **Si no aparecen logs de inicialización**: Problema con Hilt o dependencias
2. **Si no aparecen logs de setup**: Problema en setupProtocolHandlers
3. **Si no aparecen logs de start listening**: Problema en la llamada a la función
4. **Si no aparecen logs de procesamiento**: Problema en el parseo o ejecución

Una vez identificado el punto de falla, podremos implementar la solución específica.
