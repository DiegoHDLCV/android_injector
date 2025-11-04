# Documentación del Sistema de Inyección de Llaves Criptográficas

## Parte 5: Protocolos de Comunicación

### Versión: 1.0
### Fecha: Octubre 2025

---

## 1. INTRODUCCIÓN A LOS PROTOCOLOS

### 1.1 Protocolos Soportados

El sistema implementa **dos protocolos de comunicación serial**:

#### 1.1.1 Protocolo FUTUREX
- **Propósito**: Inyección de llaves criptográficas
- **Especificación**: Manual Futurex v3.8.3
- **Características**:
  - Comandos estructurados con campos definidos
  - Verificación de integridad con LRC
  - Soporte para múltiples tipos de llaves
  - Manejo de errores detallado

#### 1.1.2 Protocolo LEGACY
- **Propósito**: Detección de conexión (polling)
- **Características**:
  - Simple y rápido
  - Mensajes cortos con separador `|`
  - Usado solo para keep-alive
  - Compatibilidad con versiones anteriores

### 1.2 Configuración de Protocolo

**Selección Global**:
```kotlin
// En SystemConfig
SystemConfig.commProtocolSelected = CommProtocol.FUTUREX  // o LEGACY
```

**Uso Combinado**:
- **FUTUREX**: Para inyección de llaves
- **LEGACY**: Para polling/detección de conexión
- Ambos pueden coexistir en una sesión

---

## 2. PROTOCOLO FUTUREX

### 2.1 Estructura General

**Formato de Mensaje**:
```
<STX> [PAYLOAD] <ETX> <LRC>

Donde:
- STX  = 0x02 (Start of Text)
- PAYLOAD = Datos en formato ASCII
- ETX  = 0x03 (End of Text)
- LRC  = XOR de todos los bytes anteriores (incluyendo ETX)
```

**Ejemplo Visual**:
```
Hexadecimal:
02 | 30 32 30 31 30 33 30 30 30 35 ... | 03 | 4E

ASCII:
STX | 0 2 0 1 0 3 0 0 0 5 ...        | ETX| LRC

Estructura:
STX | COMMAND + FIELDS               | ETX| LRC
```

### 2.2 Cálculo del LRC

**Algoritmo**:
```
LRC = byte[0] XOR byte[1] XOR ... XOR byte[n] XOR ETX

Donde byte[0..n] son todos los bytes del payload
```

**Ejemplo en Código**:
```kotlin
fun calculateLRC(data: ByteArray): Byte {
    var lrc: Byte = 0
    for (byte in data) {
        lrc = (lrc.toInt() xor byte.toInt()).toByte()
    }
    return lrc
}
```

**Proceso Completo**:
```
Payload: "020100000100"
Bytes:   [30 32 30 31 30 30 30 30 30 31 30 30]

Cálculo paso a paso:
LRC = 0x00
LRC = 0x00 XOR 0x30 = 0x30
LRC = 0x30 XOR 0x32 = 0x02
LRC = 0x02 XOR 0x30 = 0x32
... (continuar para todos)
LRC = ... XOR 0x03 (ETX) = resultado final
```

**Validación al Recibir**:
```kotlin
// Extraer LRC del mensaje
val receivedLRC = message.last()

// Calcular LRC esperado
val dataForLRC = message.sliceArray(0 until message.size - 1)
val calculatedLRC = calculateLRC(dataForLRC)

// Comparar
if (receivedLRC != calculatedLRC) {
    throw Exception("Bad LRC - mensaje corrupto")
}
```

### 2.3 Comandos Futurex

#### 2.3.1 Comando 02: Inyección de Llave Simétrica

**Estructura del Comando**:
```
02[VERSION][KEY_SLOT][KTK_SLOT][KEY_TYPE][ENCRYPTION_TYPE]
[KEY_CHECKSUM][KTK_CHECKSUM][KSN][KEY_LENGTH][KEY_DATA]
```

**Campos**:

| Campo | Longitud | Descripción | Formato | Ejemplo |
|-------|----------|-------------|---------|---------|
| COMMAND | 2 | Comando "02" | ASCII | "02" |
| VERSION | 2 | Versión del comando | ASCII hex | "01" |
| KEY_SLOT | 2 | Slot destino (0-99) | ASCII hex | "0F" (slot 15) |
| KTK_SLOT | 2 | Slot de KEK (0-99) | ASCII hex | "05" (slot 5) |
| KEY_TYPE | 2 | Tipo de llave | ASCII hex | "05" (PIN) |
| ENCRYPTION_TYPE | 2 | Tipo de cifrado | ASCII hex | "00"=claro, "01"=KEK |
| KEY_CHECKSUM | Variable | KCV de llave | ASCII hex | "AABB12" |
| KTK_CHECKSUM | Variable | KCV de KEK | ASCII hex | "0000" si no hay KEK |
| KSN | 20 | Key Serial Number | ASCII hex | "00000000000000000000" |
| KEY_LENGTH | 3 | Longitud en bytes | ASCII hex | "010" (16 bytes) |
| KEY_DATA | Variable | Datos de llave | ASCII hex | "AABBCCDDEEFF..." |

**Ejemplo Completo**:
```
Comando: 02
Version: 01
Key Slot: 0F (15)
KTK Slot: 00
Key Type: 05 (PIN)
Encryption Type: 00 (claro)
Key Checksum: AABB
KTK Checksum: 0000
KSN: 00000000000000000000
Key Length: 010 (16 bytes)
Key Data: AABBCCDDEEFF00112233445566778899

Payload ASCII:
"02010F000500AABB000000000000000000000000000010AABBCCDDEEFF00112233445566778899"

Mensaje completo:
<STX>02010F000500AABB000000000000000000000000000010AABBCCDDEEFF00112233445566778899<ETX><LRC>
```

**Respuesta Exitosa**:
```
Estructura:
02[RESPONSE_CODE][KEY_CHECKSUM]

Campos:
- RESPONSE_CODE: "00" (éxito) o código de error
- KEY_CHECKSUM: KCV de la llave inyectada

Ejemplo:
<STX>0200AABB<ETX><LRC>

Significa:
- Código 00: Successful
- KCV: AABB (coincide con llave enviada)
```

#### 2.3.2 Formato de Longitud de Llave

**Característica Especial**: La longitud se envía en formato ASCII HEX de 3 dígitos

**Conversión**:
```kotlin
fun formatKeyLength(lengthInBytes: Int): String {
    // Convertir a hexadecimal
    val hexValue = lengthInBytes.toString(16).uppercase()
    
    // Rellenar a 3 dígitos
    return hexValue.padStart(3, '0')
}
```

**Ejemplos**:

| Bytes | Hex | Formato ASCII | Resultado |
|-------|-----|---------------|-----------|
| 8 | 0x08 | "008" | "008" |
| 16 | 0x10 | "010" | "010" |
| 24 | 0x18 | "018" | "018" |
| 32 | 0x20 | "020" | "020" |
| 48 | 0x30 | "030" | "030" |

**En el Mensaje**:
```
Llave de 16 bytes:
KEY_LENGTH = "010"

Llave de 32 bytes:
KEY_LENGTH = "020"
```

#### 2.3.3 Comando 03: Lectura de Número de Serie

**Comando**:
```
03[VERSION]

Ejemplo:
<STX>0301<ETX><LRC>
```

**Respuesta**:
```
03[RESPONSE_CODE][SERIAL_NUMBER]

Donde:
- RESPONSE_CODE: "00" si éxito
- SERIAL_NUMBER: 16 caracteres ASCII

Ejemplo:
<STX>03001234567890ABCDEF<ETX><LRC>
```

#### 2.3.4 Comando 04: Escritura de Número de Serie

**Comando**:
```
04[VERSION][SERIAL_NUMBER]

Ejemplo:
<STX>04011234567890ABCDEF<ETX><LRC>
```

**Respuesta**:
```
04[RESPONSE_CODE]

Ejemplo:
<STX>0400<ETX><LRC>
```

#### 2.3.5 Comando 05: Eliminación Total de Llaves

**Comando**:
```
05[VERSION]

Ejemplo:
<STX>0501<ETX><LRC>
```

**Respuesta**:
```
05[RESPONSE_CODE]

Ejemplo:
<STX>0500<ETX><LRC>
```

**Efecto**:
- Elimina TODAS las llaves del PED
- Operación irreversible
- Requiere confirmación del usuario

#### 2.3.6 Comando 06: Eliminación de Llave Específica

**Comando**:
```
06[VERSION][KEY_SLOT][KEY_TYPE]

Ejemplo (eliminar PIN en slot 15):
<STX>06010F05<ETX><LRC>
```

**Respuesta**:
```
06[RESPONSE_CODE]

Ejemplo:
<STX>0600<ETX><LRC>
```

### 2.4 Códigos de Respuesta Futurex

**Tabla Completa**:

| Código | Hex | Descripción | Acción Recomendada |
|--------|-----|-------------|-------------------|
| 0x00 | 00 | Successful | Continuar |
| 0x01 | 01 | Invalid command | Verificar comando enviado |
| 0x02 | 02 | Invalid command version | Usar versión "01" |
| 0x03 | 03 | Invalid length | Verificar KEY_LENGTH |
| 0x04 | 04 | Unsupported characters | Solo ASCII válido |
| 0x05 | 05 | Device is busy | Esperar y reintentar |
| 0x06 | 06 | Not in injection mode | Activar modo inyección |
| 0x07 | 07 | Device is in tamper | Resolver tamper físico |
| 0x08 | 08 | Bad LRC | Recalcular LRC |
| 0x09 | 09 | Duplicate key | Eliminar llave existente primero |
| 0x0A | 0A | Duplicate KSN | Usar KSN diferente |
| 0x0B | 0B | Key deletion failed | Verificar slot y tipo |
| 0x0C | 0C | Invalid key slot | Slot debe estar en rango 0-99 |
| 0x0D | 0D | Invalid KTK slot | Verificar slot de KEK |
| 0x0E | 0E | Missing KTK | Exportar KEK primero |
| 0x0F | 0F | Key slot not empty | Eliminar llave existente |
| 0x10 | 10 | Invalid key type | Verificar mapeo de tipos |
| 0x11 | 11 | Invalid key encryption type | Usar "00" o "01" |
| 0x12 | 12 | Invalid key checksum | KCV incorrecto |
| 0x13 | 13 | Invalid KTK checksum | KCV de KEK incorrecto |
| 0x14 | 14 | Invalid KSN | KSN debe ser 20 chars hex |
| 0x15 | 15 | Invalid key length | Longitud no soportada |
| 0x16 | 16 | Invalid KTK length | KEK con longitud inválida |
| 0x17 | 17 | Invalid TR-31 version | No aplicable en este sistema |
| 0x18 | 18 | Invalid key usage | Tipo de uso no válido |
| 0x19 | 19 | Invalid algorithm | Algoritmo no soportado |
| 0x1A | 1A | Invalid mode of use | Modo de uso inválido |
| 0x1B | 1B | MAC verification failed | Problema con MAC |
| 0x1C | 1C | Decryption failed | KEK incorrecta o corrupta |

**Manejo de Errores en Código**:
```kotlin
when (responseCode) {
    "00" -> {
        // Éxito
        log("Inyección exitosa")
    }
    "08" -> {
        // Bad LRC
        throw Exception("LRC inválido - mensaje corrupto")
    }
    "09" -> {
        // Duplicate key
        throw Exception("Llave duplicada en slot - eliminar primero")
    }
    "0E" -> {
        // Missing KTK
        throw Exception("KEK no encontrada - exportar KEK primero")
    }
    "1C" -> {
        // Decryption failed
        throw Exception("Fallo al descifrar - KEK incorrecta")
    }
    else -> {
        throw Exception("Error Futurex: $responseCode")
    }
}
```

### 2.5 Parsing de Mensajes Futurex

**FuturexMessageParser**:

```kotlin
class FuturexMessageParser : IMessageParser {
    private val buffer = mutableListOf<Byte>()
    
    override fun appendData(newData: ByteArray) {
        buffer.addAll(newData.toList())
    }
    
    override fun nextMessage(): ParsedMessage? {
        // Buscar STX
        val stxIndex = buffer.indexOf(STX)
        if (stxIndex == -1) return null
        
        // Buscar ETX
        val etxIndex = buffer.indexOf(ETX, stxIndex + 1)
        if (etxIndex == -1) return null
        
        // Verificar que hay LRC
        if (buffer.size <= etxIndex + 1) return null
        
        // Extraer mensaje completo
        val messageBytes = buffer.subList(
            stxIndex, 
            etxIndex + 2  // Incluye ETX + LRC
        ).toByteArray()
        
        // Validar LRC
        val receivedLRC = messageBytes.last()
        val dataForLRC = messageBytes.sliceArray(
            1 until messageBytes.size - 1  // Sin STX, sin LRC
        ) + ETX
        val calculatedLRC = calculateLRC(dataForLRC)
        
        if (receivedLRC != calculatedLRC) {
            throw Exception("Bad LRC")
        }
        
        // Extraer payload (sin STX, ETX, LRC)
        val payload = String(
            messageBytes.sliceArray(1 until messageBytes.size - 2),
            Charsets.US_ASCII
        )
        
        // Identificar comando
        val command = payload.substring(0, 2)
        
        // Parsear según comando
        return when (command) {
            "02" -> parseInjectCommand(payload)
            "03" -> parseReadSerialCommand(payload)
            "04" -> parseWriteSerialCommand(payload)
            "05" -> parseDeleteAllCommand(payload)
            "06" -> parseDeleteKeyCommand(payload)
            else -> UnknownMessage(payload)
        }
    }
}
```

**Parsing de Comando 02**:
```kotlin
private fun parseInjectCommand(payload: String): ParsedMessage {
    val isResponse = payload.length < 10  // Respuesta es corta
    
    if (isResponse) {
        // Respuesta: 02[CODE][KCV]
        return InjectSymmetricKeyResponse(
            responseCode = payload.substring(2, 4),
            keyChecksum = payload.substring(4)
        )
    } else {
        // Comando: extraer todos los campos
        var pos = 2
        val version = payload.substring(pos, pos + 2); pos += 2
        val keySlot = payload.substring(pos, pos + 2); pos += 2
        val ktkSlot = payload.substring(pos, pos + 2); pos += 2
        val keyType = payload.substring(pos, pos + 2); pos += 2
        val encType = payload.substring(pos, pos + 2); pos += 2
        
        // KCV variable (hasta encontrar el siguiente campo conocido)
        // ... parsing complejo de campos variables
        
        return InjectSymmetricKeyCommand(
            version, keySlot, ktkSlot, keyType,
            encType, kcv, ktkKcv, ksn, keyLength, keyData
        )
    }
}
```

---

## 3. PROTOCOLO LEGACY

### 3.1 Estructura General

**Formato de Mensaje**:
```
<STX> COMMAND(4) | DATA <ETX> <LRC>

Donde:
- STX  = 0x02
- COMMAND = 4 caracteres ASCII
- | = Separador literal
- DATA = Datos variables
- ETX  = 0x03
- LRC  = XOR de todos los bytes
```

**Ejemplo**:
```
Hexadecimal:
02 | 30 31 30 30 | 7C | 50 4F 4C 4C | 03 | LRC

ASCII:
STX| 0  1  0  0  | |  | P  O  L  L  |ETX| LRC

Estructura:
STX| COMMAND     |SEP | DATA        |ETX| LRC
```

### 3.2 Comandos Legacy

#### 3.2.1 Comando 0100: POLL (Ping)

**Propósito**: Verificar presencia de SubPOS

**Mensaje**:
```
<STX>0100|POLL<ETX><LRC>
```

**Uso**:
- Enviado por Master cada 2 segundos
- SubPOS debe responder rápidamente
- Detección de conexión/desconexión

#### 3.2.2 Comando 0110: ACK (Respuesta)

**Propósito**: Confirmar presencia

**Mensaje**:
```
<STX>0110|ACK<ETX><LRC>
```

**Flujo**:
```
Master (Injector):
  └─ Envía: <STX>0100|POLL<ETX><LRC>
      ↓
SubPOS (App):
  └─ Recibe POLL
  └─ Responde: <STX>0110|ACK<ETX><LRC>
      ↓
Master:
  └─ Recibe ACK
  └─ Estado: CONNECTED
```

### 3.3 Parsing de Mensajes Legacy

**LegacyMessageParser**:

```kotlin
class LegacyMessageParser : IMessageParser {
    private val buffer = mutableListOf<Byte>()
    
    override fun nextMessage(): ParsedMessage? {
        // Buscar STX y ETX
        val stxIndex = buffer.indexOf(STX)
        if (stxIndex == -1) return null
        
        val etxIndex = buffer.indexOf(ETX, stxIndex + 1)
        if (etxIndex == -1) return null
        
        // Extraer mensaje
        val messageBytes = buffer.subList(
            stxIndex,
            etxIndex + 2
        ).toByteArray()
        
        // Validar LRC
        validateLRC(messageBytes)
        
        // Extraer contenido
        val content = String(
            messageBytes.sliceArray(1 until messageBytes.size - 2),
            Charsets.US_ASCII
        )
        
        // Separar comando y datos
        val parts = content.split(SEPARATOR)
        val command = parts[0]
        val data = parts.getOrNull(1) ?: ""
        
        // Identificar mensaje
        return when (command) {
            "0100" -> PollCommand(data)
            "0110" -> PollResponse(data)
            else -> UnknownMessage(content)
        }
    }
}
```

### 3.4 Formateo de Mensajes Legacy

**LegacyMessageFormatter**:

```kotlin
object LegacyMessageFormatter : IMessageFormatter {
    private const val STX: Byte = 0x02
    private const val ETX: Byte = 0x03
    private const val SEPARATOR = '|'
    
    override fun format(command: String, fields: List<String>): ByteArray {
        // Validar comando
        if (command.length != 4) {
            throw IllegalArgumentException("Comando debe tener 4 caracteres")
        }
        
        // Construir contenido
        val dataString = fields.joinToString(SEPARATOR.toString())
        val contentString = "$command$SEPARATOR$dataString"
        val contentBytes = contentString.toByteArray(Charsets.US_ASCII)
        
        // Agregar ETX
        val etxByte = byteArrayOf(ETX)
        
        // Calcular LRC (incluye ETX)
        val bytesForLrc = contentBytes + etxByte
        val lrc = calculateLrc(bytesForLrc)
        
        // Construir mensaje completo
        return byteArrayOf(STX) + contentBytes + etxByte + byteArrayOf(lrc)
    }
}
```

---

## 4. SERVICIO DE POLLING

### 4.1 PollingService

**Propósito**: Detectar y mantener conexión con SubPOS

**Características**:
- Envía POLL cada 2 segundos
- Detecta conexión cuando recibe ACK
- Detecta desconexión por timeout
- Callback de cambio de estado

### 4.2 Flujo de Polling

#### 4.2.1 Desde Master (Injector)

```kotlin
class PollingService {
    private var isPollingActive = false
    private var pollingJob: Job? = null
    
    fun startMasterPolling(
        onConnectionStatusChanged: (Boolean) -> Unit
    ) {
        pollingJob = viewModelScope.launch {
            isPollingActive = true
            var isConnected = false
            
            while (isPollingActive) {
                try {
                    // Construir mensaje POLL
                    val pollMessage = messageFormatter.format(
                        "0100", 
                        listOf("POLL")
                    )
                    
                    // Enviar POLL
                    val writeResult = comController.write(
                        pollMessage,
                        timeout = 1000
                    )
                    
                    if (writeResult <= 0) {
                        // Error al enviar
                        if (isConnected) {
                            isConnected = false
                            onConnectionStatusChanged(false)
                        }
                        delay(2000)
                        continue
                    }
                    
                    // Esperar respuesta ACK
                    val buffer = ByteArray(1024)
                    val readResult = comController.readData(
                        expectedLen = 1024,
                        buffer = buffer,
                        timeout = 5000
                    )
                    
                    if (readResult > 0) {
                        // Datos recibidos, parsear
                        messageParser.appendData(buffer.sliceArray(0 until readResult))
                        val message = messageParser.nextMessage()
                        
                        if (message is PollResponse) {
                            // ACK recibido
                            if (!isConnected) {
                                isConnected = true
                                onConnectionStatusChanged(true)
                            }
                        }
                    } else {
                        // Timeout - sin respuesta
                        if (isConnected) {
                            isConnected = false
                            onConnectionStatusChanged(false)
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error en polling", e)
                    if (isConnected) {
                        isConnected = false
                        onConnectionStatusChanged(false)
                    }
                }
                
                // Esperar 2 segundos para próximo POLL
                delay(2000)
            }
        }
    }
    
    fun stopPolling() {
        isPollingActive = false
        pollingJob?.cancel()
        pollingJob = null
    }
}
```

#### 4.2.2 Desde SubPOS (App)

```kotlin
// En MainViewModel (listening loop)

private suspend fun processReceivedMessage(message: ParsedMessage) {
    when (message) {
        is PollCommand -> {
            handlePollRequest()
        }
        // ... otros comandos
    }
}

private suspend fun handlePollRequest() {
    try {
        Log.i(TAG, "📥 POLL recibido desde MasterPOS")
        
        // Construir respuesta ACK
        val ackMessage = messageFormatter.format(
            "0110",
            listOf("ACK")
        )
        
        // Enviar ACK
        val writeResult = comController?.write(
            ackMessage,
            timeout = 1000
        )
        
        if (writeResult != null && writeResult > 0) {
            Log.i(TAG, "📤 Respuesta POLL enviada exitosamente")
        } else {
            Log.e(TAG, "Error al enviar respuesta POLL")
        }
        
    } catch (e: Exception) {
        Log.e(TAG, "Error manejando POLL", e)
    }
}
```

### 4.3 Detección de Conexión/Desconexión

**Timeline de Eventos**:

```
T=0s:   Master inicia polling
        └─ Envía POLL cada 2s

T=2s:   Master envía POLL #1
        └─ Espera ACK (timeout 5s)
        └─ Sin respuesta (SubPOS no conectado)
        └─ Estado: DISCONNECTED

T=4s:   Master envía POLL #2
        └─ Espera ACK
        └─ Sin respuesta
        └─ Estado: DISCONNECTED

T=6s:   SubPOS se conecta
        └─ Inicia listening

T=8s:   Master envía POLL #3
        └─ SubPOS recibe POLL
        └─ SubPOS responde ACK
        └─ Master recibe ACK
        └─ Estado: CONNECTED ✓
        └─ Callback: onConnectionStatusChanged(true)

T=10s:  Master envía POLL #4
        └─ ACK recibido
        └─ Estado: CONNECTED

T=15s:  SubPOS se desconecta físicamente

T=16s:  Master envía POLL #5
        └─ Espera ACK (timeout 5s)
        └─ Sin respuesta (timeout alcanzado)
        └─ Estado: DISCONNECTED
        └─ Callback: onConnectionStatusChanged(false)
```

**Métricas**:
- **Tiempo de detección de conexión**: < 2 segundos (próximo POLL)
- **Tiempo de detección de desconexión**: < 7 segundos (timeout 5s + margen)
- **Intervalo de polling**: 2 segundos (configurable)

### 4.4 Integración con Inyección

**Detener Polling Durante Inyección**:

```kotlin
// En KeyInjectionViewModel

suspend fun startKeyInjection(profile: ProfileEntity) {
    try {
        // 1. Detener polling
        Log.i(TAG, "Deteniendo polling antes de inyección...")
        pollingService.stopPolling()
        delay(500)  // Dar tiempo a liberar puerto
        
        // 2. Inyectar llaves
        initializeCommunication()
        injectKeysFromProfile(profile)
        
        // 3. Finalizar
        closeCommunication()
        
    } finally {
        // 4. Reiniciar polling (siempre)
        Log.i(TAG, "Reiniciando polling después de inyección...")
        delay(1000)  // Dar tiempo antes de reiniciar
        pollingService.restartPolling()
    }
}
```

**Razón**:
- Evitar conflictos en el puerto serial
- Un solo proceso puede usar el puerto a la vez
- Polling libera puerto para inyección
- Polling se reanuda después

---

## 5. COMUNICACIÓN SERIAL USB

### 5.1 Parámetros de Configuración

**Configuración Estándar**:
```kotlin
comController.init(
    baudRate = EnumCommConfBaudRate.BPS_115200,  // 115200 bps
    parity = EnumCommConfParity.NOPAR,           // Sin paridad
    dataBits = EnumCommConfDataBits.DB_8         // 8 bits de datos
)
```

**Parámetros Soportados**:

| Parámetro | Valores | Recomendado |
|-----------|---------|-------------|
| Baud Rate | 9600, 115200 | 115200 |
| Parity | NONE, EVEN, ODD | NONE |
| Data Bits | 7, 8 | 8 |
| Stop Bits | 1, 2 | 1 |

### 5.2 Auto-scan de Puertos (Aisino)

**Proceso**:

```kotlin
suspend fun autoScanPortsAndBauds(): Pair<Int, Int>? {
    Log.i(TAG, "=== AUTO-SCAN DE PUERTOS SERIAL ===")
    
    val candidatePorts = SystemConfig.aisinoCandidatePorts  // [0, 1]
    val candidateBauds = SystemConfig.aisinoCandidateBauds  // [9600, 115200]
    
    for (port in candidatePorts) {
        for (baud in candidateBauds) {
            Log.d(TAG, "Probando puerto $port con baudrate $baud...")
            
            try {
                // Intentar abrir
                val openResult = portOpen(port, baud)
                if (openResult != 0) continue
                
                // Intentar leer
                val buffer = ByteArray(256)
                val readResult = portRead(buffer, timeout = 2000)
                
                if (readResult > 0) {
                    // Datos recibidos - configuración correcta
                    Log.i(TAG, "✓ Puerto $port con baud $baud: DATOS RECIBIDOS")
                    selectedPort = port
                    selectedBaud = baud
                    return Pair(port, baud)
                }
                
                // Cerrar para próximo intento
                portClose()
                
            } catch (e: Exception) {
                Log.w(TAG, "Error en puerto $port baud $baud: ${e.message}")
                portClose()
            }
        }
    }
    
    Log.w(TAG, "✗ Auto-scan completado sin éxito")
    return null
}
```

**Logs de Auto-scan**:
```
=== AUTO-SCAN DE PUERTOS SERIAL ===
Probando puerto 0 con baudrate 9600...
  - Apertura: OK
  - Lectura: 0 bytes (timeout)
  - Resultado: Sin datos
Probando puerto 0 con baudrate 115200...
  - Apertura: OK
  - Lectura: 15 bytes recibidos
  ✓ Puerto 0 con baud 115200: DATOS RECIBIDOS
  
Configuración seleccionada: Puerto 0, Baud 115200
```

### 5.3 Re-scan Automático

**Trigger**: No se reciben datos por 5 lecturas consecutivas

```kotlin
private var silentReads = 0

private suspend fun listeningLoop() {
    while (connectionMutex.isLocked) {
        val buffer = ByteArray(1024)
        val bytesRead = comController.readData(
            expectedLen = 1024,
            buffer = buffer,
            timeout = 5000
        )
        
        if (bytesRead > 0) {
            // Datos recibidos
            silentReads = 0
            processData(buffer.sliceArray(0 until bytesRead))
        } else {
            // Sin datos
            silentReads++
            
            if (silentReads % 5 == 0) {
                // Cada 5 lecturas sin datos → re-scan
                Log.w(TAG, "5 lecturas sin datos - ejecutando re-scan...")
                CommunicationSDKManager.rescanIfSupported()
                Log.i(TAG, "Re-scan completado, puerto reabierto")
            }
        }
    }
}
```

**Razón**:
- Puerto puede cambiar dinámicamente
- Configuración puede desincronizarse
- Re-scan corrige automáticamente

---

## 6. MANEJO DE ERRORES Y TIMEOUTS

### 6.1 Timeouts

**Timeouts Configurados**:

| Operación | Timeout | Razón |
|-----------|---------|-------|
| write() | 1000ms | Escritura debe ser rápida |
| readData() | 5000ms | Dispositivo puede estar procesando |
| Espera de respuesta Futurex | 10000ms | Inyección puede tardar |
| Polling - espera ACK | 5000ms | Detectar desconexión |

**Manejo de Timeout en Lectura**:
```kotlin
val bytesRead = comController.readData(
    expectedLen = 1024,
    buffer = buffer,
    timeout = 5000
)

when {
    bytesRead > 0 -> {
        // Datos recibidos
        processData(buffer.sliceArray(0 until bytesRead))
    }
    bytesRead == 0 -> {
        // Timeout - sin datos
        Log.w(TAG, "Timeout esperando datos")
    }
    bytesRead < 0 -> {
        // Error
        throw Exception("Error en lectura: $bytesRead")
    }
}
```

### 6.2 Errores de Comunicación

**Códigos de Error Comunes**:

| Código | Significado | Acción |
|--------|-------------|--------|
| 0 | Éxito | Continuar |
| -1 | Error general | Revisar conexión |
| -2 | Timeout | Esperar y reintentar |
| -3 | Puerto no disponible | Verificar cable |
| -4 | Configuración inválida | Ajustar parámetros |

**Estrategia de Retry**:
```kotlin
suspend fun writeWithRetry(
    data: ByteArray,
    maxRetries: Int = 3
): Boolean {
    repeat(maxRetries) { attempt ->
        try {
            val result = comController.write(data, 1000)
            if (result > 0) return true
            
            Log.w(TAG, "Intento ${attempt + 1} fallido")
            if (attempt < maxRetries - 1) {
                delay(500)  // Pausa antes de reintentar
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en intento ${attempt + 1}", e)
            if (attempt == maxRetries - 1) throw e
        }
    }
    return false
}
```

### 6.3 Recuperación de Estado

**Después de Error Fatal**:
```kotlin
private suspend fun recoverFromError() {
    try {
        // 1. Cerrar puerto
        comController?.close()
        
        // 2. Limpiar buffers
        messageParser?.clear()
        
        // 3. Resetear estado
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        
        // 4. Esperar
        delay(2000)
        
        // 5. Re-escanear (si Aisino)
        if (SystemConfig.managerSelected == EnumManufacturer.AISINO) {
            CommunicationSDKManager.rescanIfSupported()
        }
        
        // 6. Reiniciar listening
        startListening()
        
        Log.i(TAG, "✓ Recuperación completada")
        
    } catch (e: Exception) {
        Log.e(TAG, "Error en recuperación", e)
    }
}
```

---

## 7. LOGS Y DEBUGGING

### 7.1 Categorías de Logs

**Niveles**:
```kotlin
CommLog.d(TAG, "Debug info")      // DEBUG
CommLog.i(TAG, "Info message")    // INFO
CommLog.w(TAG, "Warning")         // WARNING
CommLog.e(TAG, "Error", exception)// ERROR
```

**Categorías Especiales**:
```kotlin
// Datos raw del puerto serial
Log.v(TAG, "RAW_SERIAL_OUT: ${data.toHexString()}")
Log.v(TAG, "RAW_SERIAL_IN: ${buffer.toHexString()}")

// Mensajes parseados
Log.d(TAG, "ParsedMessage: ${message::class.simpleName}")

// Protocolo
Log.i(TAG, "TX POLL (${bytes} bytes): ${data.toHexString()}")
Log.i(TAG, "RX ACK (${bytes} bytes): ${buffer.toHexString()}")
```

### 7.2 Logs de Debugging de Protocolo

**Ejemplo Completo**:
```
[DEBUG] === ENVIANDO COMANDO FUTUREX 02 ===
[DEBUG] Construyendo payload...
[DEBUG]   Command: 02
[DEBUG]   Version: 01
[DEBUG]   Key Slot: 0F (15)
[DEBUG]   KTK Slot: 05
[DEBUG]   Key Type: 05 (PIN)
[DEBUG]   Encryption: 01 (cifrado)
[DEBUG]   Key KCV: AABB12
[DEBUG]   KTK KCV: A1B2
[DEBUG]   KSN: 00000000000000000000
[DEBUG]   Length: 010 (16 bytes)
[DEBUG]   Data: [primeros 32 chars]...
[DEBUG] Payload completo: "02010F0505......"
[DEBUG] Agregando STX (0x02)
[DEBUG] Agregando ETX (0x03)
[DEBUG] Calculando LRC...
[DEBUG]   LRC = 0x4E
[DEBUG] Mensaje final (73 bytes):
[DEBUG] RAW_SERIAL_OUT: 02 30 32 30 31 30 46 30 35 30 35 30 31 41 41 42 42 31 32 41 31 42 32 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 31 30 45 37 41 31 42 32 43 33 44 34 45 35 46 36 30 31 38 46 39 41 30 42 31 43 03 4E
[INFO]  TX: Comando 02 enviado exitosamente (73 bytes)

[DEBUG] === ESPERANDO RESPUESTA FUTUREX ===
[DEBUG] Timeout configurado: 10000ms
[INFO]  RX: Datos recibidos (13 bytes)
[DEBUG] RAW_SERIAL_IN: 02 30 32 30 30 41 41 42 42 31 32 03 5A
[DEBUG] Bytes: STX=02, Payload="0200AABB12", ETX=03, LRC=5A
[DEBUG] Validando LRC...
[DEBUG]   LRC recibido: 0x5A
[DEBUG]   LRC calculado: 0x5A
[DEBUG]   ✓ LRC válido
[DEBUG] Parsing respuesta...
[DEBUG]   Command: 02
[DEBUG]   Response Code: 00 (Successful)
[DEBUG]   Key Checksum: AABB12
[INFO]  Respuesta Futurex: Código 00 (éxito), KCV: AABB12
[DEBUG] ✓ KCV coincide con llave enviada
```

---

## 8. CONCLUSIÓN

El sistema de protocolos proporciona:

✅ **Protocolo Futurex**: Inyección robusta de llaves con validación de integridad  
✅ **Protocolo Legacy**: Detección rápida de conexión mediante polling  
✅ **LRC**: Verificación de integridad de mensajes  
✅ **Auto-scan**: Detección automática de configuración de puerto  
✅ **Manejo de Errores**: Códigos detallados y recuperación automática  
✅ **Logs Detallados**: Debugging completo de comunicación  
✅ **Timeouts Configurables**: Adaptables según necesidad  
✅ **Re-scan Automático**: Corrección dinámica de configuración  

La implementación de estos protocolos garantiza una comunicación confiable y segura entre el dispositivo maestro y los dispositivos terminales, con capacidad de recuperación ante errores y detección automática de problemas de conexión.

---

**Siguiente Documento**: [Parte 6: Usuarios y Persistencia](DOCUMENTACION_06_USUARIOS_PERSISTENCIA.md)


