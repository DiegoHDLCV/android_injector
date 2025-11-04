# Plan de Pruebas - Comunicación USB
## Sistema de Inyección de Llaves Criptográficas

**Versión:** 1.0
**Fecha:** 2025-10-10
**Proyecto:** Android Injector - Comunicación USB

---

## 1. INTRODUCCIÓN

### 1.1 Objetivo
Este documento define el plan de pruebas para validar la comunicación USB entre dispositivos MasterPOS (Injector) y SubPOS (App), garantizando una comunicación robusta, confiable y segura durante el proceso de inyección de llaves criptográficas.

### 1.2 Alcance
- Comunicación serial USB entre dispositivos Android
- Protocolos Futurex y Legacy
- Inyección de llaves criptográficas
- Manejo de múltiples SubPOS con cambio dinámico
- Escenarios de error y recuperación

### 1.3 Dispositivos bajo Prueba

#### MasterPOS (Injector)
- **Dispositivo:** Aisino
- **Rol:** Master - Envía comandos de inyección
- **Aplicación:** `injector`
- **Protocolo Principal:** Futurex (inyección) + Legacy (polling)

#### SubPOS (App)
- **Dispositivos:** Aisino o Newpos
- **Rol:** Slave - Recibe e inyecta llaves en PED
- **Aplicación:** `app`
- **Protocolo Principal:** Futurex (recepción) + Legacy (polling)

### 1.4 Arquitectura de Comunicación

```
┌─────────────────────────────────────────────────────────────┐
│                    MasterPOS (Injector)                     │
│                       Aisino Device                         │
├─────────────────────────────────────────────────────────────┤
│  KeyInjectionViewModel                                      │
│    ├─ CommunicationSDKManager (shared module)              │
│    ├─ AisinoCommunicationManager                           │
│    │   └─ AisinoComController (IComController)             │
│    ├─ FuturexMessageFormatter                              │
│    └─ PollingService (Legacy 0100/0110)                    │
└──────────────────────┬──────────────────────────────────────┘
                       │
                  USB Serial
                       │
┌──────────────────────┴──────────────────────────────────────┐
│                     SubPOS (App)                            │
│                  Aisino or Newpos Device                    │
├─────────────────────────────────────────────────────────────┤
│  MainViewModel                                              │
│    ├─ CommunicationSDKManager (shared module)              │
│    ├─ AisinoCommunicationManager / NewposCommunicationMgr  │
│    │   └─ ComController (IComController)                   │
│    ├─ FuturexMessageParser                                 │
│    ├─ KeySDKManager                                        │
│    │   └─ PedController (IPedController)                   │
│    └─ InjectedKeyRepository (persistence)                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. CASOS DE PRUEBA - COMUNICACIÓN SERIAL

### CP-USB-001: Inicialización del SDK de Comunicación

**Objetivo:** Verificar que el SDK de comunicación se inicializa correctamente en ambos dispositivos.

**Pre-condiciones:**
- Dispositivos sin inicializar
- SDK de Vanstone/Newpos disponible

**Pasos:**
1. Iniciar la aplicación Injector en MasterPOS (Aisino)
2. Iniciar la aplicación App en SubPOS (Aisino/Newpos)
3. Verificar logs de inicialización

**Resultado Esperado:**
- ✓ `AisinoCommunicationManager` inicializado en MasterPOS
- ✓ `AisinoCommunicationManager` o `NewposCommunicationManager` inicializado en SubPOS
- ✓ SDKs nativos inicializados sin errores
- ✓ Log: "SDK de Vanstone inicializado correctamente" (Aisino)
- ✓ Log: "AisinoCommunicationManager inicializado correctamente"

**Criterios de Aceptación:**
- No errores en logs
- Estado `isInitialized = true`
- Tiempo de inicialización < 5 segundos

---

### CP-USB-002: Auto-scan de Puertos (Aisino)

**Objetivo:** Validar la detección automática de puerto serial correcto en dispositivos Aisino.

**Pre-condiciones:**
- Dispositivos Aisino conectados por USB
- SDK inicializado
- Configuración: `aisinoCandidatePorts = [0, 1]`, `aisinoCandidateBauds = [9600, 115200]`

**Pasos:**
1. Ejecutar `autoScanPortsAndBauds()` en AisinoCommunicationManager
2. Observar intentos de apertura en diferentes puertos
3. Verificar detección de datos

**Resultado Esperado:**
- ✓ Intenta abrir puerto 0 con cada baudrate
- ✓ Intenta abrir puerto 1 con cada baudrate
- ✓ Selecciona puerto que recibe datos (read > 0)
- ✓ Log: "AutoScan: seleccionando puerto X baud Y (datos recibidos)"
- ✓ Variables `selectedPort` y `selectedBaud` configuradas

**Criterios de Aceptación:**
- Puerto detectado correctamente
- Baudrate alineado entre ambos dispositivos
- Tiempo de scan < 10 segundos

---

### CP-USB-003: Apertura y Cierre de Puerto Serial

**Objetivo:** Verificar el ciclo completo de apertura y cierre del puerto serial.

**Pre-condiciones:**
- SDK inicializado
- Puerto disponible

**Pasos:**
1. Obtener `comController` de `CommunicationSDKManager`
2. Configurar puerto: `init(BPS_115200, NOPAR, DB_8)`
3. Ejecutar `open()`
4. Verificar código de retorno
5. Ejecutar `close()`
6. Verificar código de retorno

**Resultado Esperado:**
- ✓ `comController != null`
- ✓ `open()` retorna `0` (éxito)
- ✓ Puerto abierto y listo para I/O
- ✓ `close()` retorna `0` (éxito)
- ✓ Puerto cerrado sin errores

**Criterios de Aceptación:**
- Sin códigos de error (-1, -2, -3, -4)
- Recursos liberados correctamente
- Puerto puede reabrirse

---

### CP-USB-004: Escritura de Datos Serial

**Objetivo:** Validar el envío de datos por el puerto serial.

**Pre-condiciones:**
- Puerto abierto
- Dispositivos conectados

**Pasos:**
1. Preparar datos de prueba: `ByteArray` con mensaje Futurex
2. Ejecutar `comController.write(data, timeout=1000)`
3. Verificar bytes escritos
4. Capturar logs de transmisión

**Resultado Esperado:**
- ✓ `write()` retorna número de bytes escritos (> 0)
- ✓ No errores de timeout
- ✓ Log: "TX POLL (N bytes, write=N): 0xXX 0xXX..."

**Criterios de Aceptación:**
- Bytes escritos = tamaño del mensaje
- Sin errores de escritura
- Timeout respetado

---

### CP-USB-005: Lectura de Datos Serial

**Objetivo:** Validar la recepción de datos por el puerto serial.

**Pre-condiciones:**
- Puerto abierto
- Datos disponibles en buffer de entrada

**Pasos:**
1. Preparar buffer: `ByteArray(1024)`
2. Ejecutar `comController.readData(expectedLen, buffer, timeout=5000)`
3. Verificar bytes leídos
4. Validar contenido del buffer

**Resultado Esperado:**
- ✓ `readData()` retorna número de bytes leídos (> 0)
- ✓ Buffer contiene datos válidos
- ✓ Log: "RX N bytes: 0xXX 0xXX..."
- ✓ Sin timeout si hay datos disponibles

**Criterios de Aceptación:**
- Bytes leídos coinciden con datos enviados
- Timeout funciona correctamente cuando no hay datos
- Sin corrupción de datos

---

### CP-USB-006: Re-scan Automático (Aisino)

**Objetivo:** Validar el re-scan automático cuando no se reciben datos.

**Pre-condiciones:**
- Dispositivo Aisino en modo listening
- Sin datos recibidos por 5 lecturas consecutivas

**Pasos:**
1. Iniciar `startListening()` en MainViewModel
2. No enviar datos desde MasterPOS
3. Esperar 5 ciclos de lectura silenciosa
4. Observar activación de re-scan

**Resultado Esperado:**
- ✓ Contador `silentReads` incrementa
- ✓ Al llegar a múltiplo de 5: ejecuta `CommunicationSDKManager.rescanIfSupported()`
- ✓ Puerto se cierra y se reabre con nueva configuración
- ✓ Log: "Re-scan aplicado y puerto reabierto"

**Criterios de Aceptación:**
- Re-scan se ejecuta automáticamente
- Puerto sigue funcional después de re-scan
- No interrumpe comunicación activa

---

### CP-USB-007: Manejo de Timeouts

**Objetivo:** Verificar el comportamiento ante timeouts en lectura/escritura.

**Pre-condiciones:**
- Puerto abierto
- No hay dispositivo respondiendo

**Pasos:**
1. Ejecutar `readData(1024, buffer, timeout=1000)` sin datos disponibles
2. Medir tiempo de espera
3. Verificar código de retorno
4. Ejecutar `write(data, timeout=1000)` en puerto sin respuesta
5. Verificar timeout de escritura

**Resultado Esperado:**
- ✓ `readData()` espera exactamente ~1000ms
- ✓ Retorna `0` o código de timeout
- ✓ `write()` retorna error si no puede escribir
- ✓ No bloquea la aplicación

**Criterios de Aceptación:**
- Timeout respetado (±100ms tolerancia)
- Sin deadlocks
- Aplicación responde correctamente

---

## 3. CASOS DE PRUEBA - PROTOCOLO FUTUREX

### CP-FTX-001: Formato de Mensaje Futurex

**Objetivo:** Validar la construcción correcta de mensajes según protocolo Futurex.

**Pre-condiciones:**
- `FuturexMessageFormatter` inicializado

**Pasos:**
1. Construir comando `02` (Inject Symmetric Key)
2. Especificar campos: version, keySlot, ktkSlot, keyType, etc.
3. Ejecutar `messageFormatter.format("02", fields)`
4. Analizar estructura del mensaje

**Resultado Esperado:**
```
Estructura esperada:
[0x02] STX
[ASCII] Payload: "02" + version + keySlot + ktkSlot + keyType + encType + kcv + ktkKcv + ksn + keyLength + keyData
[0x03] ETX
[0xXX] LRC (XOR de todos los bytes anteriores)
```

**Validaciones:**
- ✓ STX = 0x02
- ✓ Payload en formato ASCII
- ✓ ETX = 0x03
- ✓ LRC calculado correctamente (XOR)
- ✓ Longitud de llave en formato "010" (16 bytes), "020" (32 bytes), "030" (48 bytes)

**Criterios de Aceptación:**
- Estructura cumple especificación Futurex
- LRC válido
- Sin caracteres inválidos

---

### CP-FTX-002: Parsing de Respuesta Futurex

**Objetivo:** Validar el parsing correcto de respuestas del dispositivo.

**Pre-condiciones:**
- `FuturexMessageParser` inicializado
- Respuesta válida disponible

**Pasos:**
1. Recibir respuesta: `0x02 + "0200" + 0x03 + LRC`
2. Ejecutar `messageParser.appendData(response)`
3. Ejecutar `messageParser.nextMessage()`
4. Validar objeto parseado

**Resultado Esperado:**
- ✓ Retorna `InjectSymmetricKeyResponse`
- ✓ `responseCode = "00"` (éxito)
- ✓ `keyChecksum` extraído correctamente
- ✓ LRC validado sin errores

**Criterios de Aceptación:**
- Mensaje parseado correctamente
- Tipo de mensaje identificado
- Campos extraídos sin errores

---

### CP-FTX-003: Validación de LRC (Longitudinal Redundancy Check)

**Objetivo:** Verificar el cálculo y validación del LRC en mensajes Futurex.

**Pre-condiciones:**
- Mensaje Futurex construido

**Pasos:**
1. Construir payload: "0201000105000000000000000000000000000000010AABBCCDDEEFF00112233"
2. Agregar ETX
3. Calcular LRC: `XOR de todos los bytes`
4. Validar LRC en recepción

**Resultado Esperado:**
- ✓ LRC calculado = XOR(payload_bytes + ETX)
- ✓ Parser valida LRC correctamente
- ✓ Mensaje con LRC inválido es rechazado

**Criterios de Aceptación:**
- LRC coincide entre emisor y receptor
- Mensajes corruptos son detectados
- Log: "Bad LRC" si falla validación

---

### CP-FTX-004: Comando 02 - Inyección de Llave Simétrica

**Objetivo:** Validar el comando de inyección completo (PIN, MAC, DATA).

**Pre-condiciones:**
- MasterPOS listo para inyectar
- SubPOS en modo listening
- Llave en base de datos con KCV conocido

**Pasos:**
1. Seleccionar perfil con llave tipo "PIN"
2. Iniciar inyección desde KeyInjectionViewModel
3. Enviar comando 02 con:
   - version = "01"
   - keySlot = "03"
   - ktkSlot = "00"
   - keyType = "05" (PIN)
   - encryptionType = "00" (claro)
   - keyChecksum = "AABB"
   - ktkChecksum = "0000"
   - ksn = "00000000000000000000"
   - keyLength = "010" (16 bytes)
   - keyData = "AABBCCDDEEFF00112233445566778899"
4. Esperar respuesta
5. Validar inyección en PED

**Resultado Esperado:**
- ✓ Comando construido correctamente
- ✓ Mensaje enviado sin errores
- ✓ Respuesta recibida: "0200AABB" (éxito)
- ✓ Llave inyectada en slot 3 del PED
- ✓ KCV coincide con el esperado
- ✓ Registro en base de datos con status "SUCCESSFUL"

**Criterios de Aceptación:**
- Llave inyectada correctamente
- KCV validado
- Sin errores de comunicación

---

### CP-FTX-005: Comando 02 - Inyección de Llave DUKPT con KSN

**Objetivo:** Validar inyección de llaves DUKPT con KSN.

**Pre-condiciones:**
- Llave DUKPT_TDES o DUKPT_AES configurada
- KSN de 20 caracteres hexadecimales

**Pasos:**
1. Configurar llave DUKPT en perfil:
   - tipo = "DUKPT_TDES"
   - ksn = "F876543210000000000A"
2. Iniciar inyección
3. Verificar construcción de comando con KSN
4. Validar inyección

**Resultado Esperado:**
- ✓ keyType mapeado a "08" (DUKPT 3DES BDK)
- ✓ KSN incluido en comando: "F876543210000000000A"
- ✓ Longitud de KSN = 20 caracteres
- ✓ Llave inyectada con KSN correcto
- ✓ Respuesta exitosa

**Criterios de Aceptación:**
- KSN presente en comando
- Validación de formato de KSN
- Inyección DUKPT exitosa

---

### CP-FTX-006: Comando 03 - Lectura de Número de Serie

**Objetivo:** Validar lectura del número de serie del dispositivo.

**Pre-condiciones:**
- Comunicación establecida
- Dispositivo con número de serie configurado

**Pasos:**
1. Enviar comando 03 con version "01"
2. Esperar respuesta
3. Parsear número de serie

**Resultado Esperado:**
- ✓ Comando enviado: `0x02 + "0301" + 0x03 + LRC`
- ✓ Respuesta recibida: `0x02 + "0300" + serialNumber(16) + 0x03 + LRC`
- ✓ Número de serie extraído correctamente
- ✓ Longitud = 16 caracteres

**Criterios de Aceptación:**
- Número de serie leído
- Formato válido
- Sin errores

---

### CP-FTX-007: Comando 04 - Escritura de Número de Serie

**Objetivo:** Validar escritura del número de serie.

**Pre-condiciones:**
- Comunicación establecida
- Número de serie nuevo de 16 caracteres

**Pasos:**
1. Enviar comando 04 con version "01" y serialNumber "123456789ABCDEFG"
2. Esperar confirmación
3. Leer número de serie para verificar

**Resultado Esperado:**
- ✓ Comando enviado correctamente
- ✓ Respuesta: "0400" (éxito)
- ✓ Número de serie actualizado

**Criterios de Aceptación:**
- Escritura exitosa
- Número de serie persistente

---

### CP-FTX-008: Comando 05 - Eliminación Total de Llaves

**Objetivo:** Validar eliminación de todas las llaves del dispositivo.

**Pre-condiciones:**
- Dispositivo con llaves inyectadas
- Base de datos con registros de llaves

**Pasos:**
1. Inyectar 3 llaves en diferentes slots
2. Enviar comando 05 (Delete All Keys)
3. Verificar eliminación en PED
4. Verificar eliminación en base de datos

**Resultado Esperado:**
- ✓ Comando enviado: `0x02 + "0501" + 0x03 + LRC`
- ✓ Respuesta: "0500" (éxito)
- ✓ PED retorna `deleteAllKeys() = true`
- ✓ Base de datos: todos los registros eliminados
- ✓ Log: "Todas las llaves eliminadas exitosamente"

**Criterios de Aceptación:**
- Todas las llaves eliminadas
- Sincronización PED-Base de datos
- Sin errores

---

### CP-FTX-009: Comando 06 - Eliminación de Llave Específica

**Objetivo:** Validar eliminación de una llave en slot específico.

**Pre-condiciones:**
- Llave inyectada en slot 5, tipo PIN

**Pasos:**
1. Enviar comando 06 con keySlot="05", keyType="05"
2. Verificar eliminación
3. Validar que otras llaves no se afecten

**Resultado Esperado:**
- ✓ Comando enviado: `0x02 + "06010505" + 0x03 + LRC`
- ✓ Respuesta: "0600" (éxito)
- ✓ Llave en slot 5 eliminada
- ✓ Llaves en otros slots intactas
- ✓ Registro eliminado de base de datos

**Criterios de Aceptación:**
- Solo llave especificada es eliminada
- Otras llaves no afectadas

---

### CP-FTX-010: Manejo de Códigos de Error Futurex

**Objetivo:** Verificar el manejo correcto de todos los códigos de error.

**Pre-condiciones:**
- Protocolo Futurex activo

**Casos de Error:**

| Código | Descripción | Escenario de Prueba |
|--------|-------------|---------------------|
| 0x00 | Successful | Inyección normal |
| 0x01 | Invalid command | Enviar comando "99" no existente |
| 0x02 | Invalid version | Enviar version "FF" |
| 0x03 | Invalid length | Longitud de llave incorrecta |
| 0x05 | Device is busy | Enviar comando durante operación |
| 0x08 | Bad LRC | Alterar LRC intencionalmente |
| 0x09 | Duplicate key | Inyectar misma llave dos veces |
| 0x0C | Invalid key slot | Slot fuera de rango (99) |
| 0x10 | Invalid key type | Tipo "ZZ" inválido |
| 0x12 | Invalid key checksum | KCV incorrecto |
| 0x14 | Invalid KSN | KSN con longitud != 20 |
| 0x15 | Invalid key length | Longitud != 16/32/48 |

**Resultado Esperado:**
- ✓ Cada código de error es detectado
- ✓ Mensaje de error descriptivo mostrado
- ✓ No se inyecta llave cuando hay error
- ✓ Estado de la aplicación se mantiene estable

**Criterios de Aceptación:**
- Todos los errores manejados correctamente
- Sin crashes
- Mensajes informativos al usuario

---

## 4. CASOS DE PRUEBA - PROTOCOLO LEGACY (POLLING)

### CP-POLL-001: Polling desde MasterPOS

**Objetivo:** Validar el envío de mensajes POLL (0100) desde el Injector.

**Pre-condiciones:**
- MasterPOS iniciado
- PollingService activo

**Pasos:**
1. Ejecutar `pollingService.startMasterPolling()`
2. Observar envío de mensajes cada 2 segundos
3. Verificar formato de mensaje

**Resultado Esperado:**
- ✓ Mensaje POLL enviado cada 2000ms
- ✓ Formato: `STX + "0100POLL" + ETX + LRC`
- ✓ Log: "📤 Enviado POLL (N bytes, write=N): 0xXX..."
- ✓ Polling continuo hasta detención

**Criterios de Aceptación:**
- Intervalo de 2 segundos respetado
- Formato correcto
- Sin fallos consecutivos

---

### CP-POLL-002: Respuesta a POLL desde SubPOS

**Objetivo:** Validar que el SubPOS responde a mensajes POLL.

**Pre-condiciones:**
- SubPOS en modo listening
- MasterPOS enviando POLL

**Pasos:**
1. SubPOS recibe mensaje "0100"
2. Parser identifica comando como POLL
3. Ejecutar `handlePollRequest()`
4. Enviar respuesta "0110"

**Resultado Esperado:**
- ✓ Mensaje POLL detectado
- ✓ Respuesta enviada: `STX + "0110ACK" + ETX + LRC`
- ✓ Log: "📥 POLL (0100) recibido desde MasterPOS"
- ✓ Log: "📤 Respuesta POLL enviada"

**Criterios de Aceptación:**
- Respuesta enviada en < 500ms
- Formato correcto
- MasterPOS recibe ACK

---

### CP-POLL-003: Detección de Conexión

**Objetivo:** Validar que el MasterPOS detecta cuando un SubPOS se conecta.

**Pre-condiciones:**
- MasterPOS enviando POLL sin respuesta

**Pasos:**
1. Conectar SubPOS físicamente
2. SubPOS inicia listening
3. MasterPOS recibe respuesta "0110"
4. Estado de conexión cambia

**Resultado Esperado:**
- ✓ `isConnected.value` cambia de `false` a `true`
- ✓ Log: "✅ Respuesta POLL recibida - SubPOS conectado"
- ✓ Callback `onConnectionStatusChanged(true)` ejecutado
- ✓ UI actualizada

**Criterios de Aceptación:**
- Conexión detectada en < 5 segundos
- Estado sincronizado
- Sin falsos positivos

---

### CP-POLL-004: Detección de Desconexión

**Objetivo:** Validar detección de desconexión del SubPOS.

**Pre-condiciones:**
- SubPOS conectado y respondiendo POLL

**Pasos:**
1. Desconectar SubPOS físicamente
2. MasterPOS espera respuesta POLL con timeout
3. Timeout ocurre (5 segundos)
4. Estado de conexión cambia

**Resultado Esperado:**
- ✓ Timeout de 5000ms alcanzado
- ✓ `isConnected.value` cambia de `true` a `false`
- ✓ Log: "⚠️ Timeout esperando respuesta POLL - SubPOS no responde"
- ✓ Callback `onConnectionStatusChanged(false)` ejecutado

**Criterios de Aceptación:**
- Desconexión detectada en < 7 segundos (timeout + margen)
- Sin bloqueos
- Polling continúa

---

### CP-POLL-005: Reinicio de Polling tras Inyección

**Objetivo:** Verificar que el polling se detiene durante inyección y se reinicia después.

**Pre-condiciones:**
- Polling activo
- Perfil listo para inyectar

**Pasos:**
1. Observar polling activo
2. Iniciar inyección de llaves
3. Verificar que polling se detiene
4. Completar inyección
5. Verificar reinicio de polling

**Resultado Esperado:**
- ✓ Al iniciar inyección: `pollingService.stopPolling()` ejecutado
- ✓ Durante inyección: no se envían mensajes POLL
- ✓ Al finalizar: `restartPolling()` ejecutado
- ✓ Polling se reanuda después de 1 segundo
- ✓ Log: "Deteniendo polling antes de iniciar inyección..."
- ✓ Log: "Reiniciando polling después de la inyección..."

**Criterios de Aceptación:**
- Polling se detiene completamente
- Puerto liberado para inyección
- Polling se reinicia automáticamente

---

### CP-POLL-006: Manejo de Fallos Consecutivos de Escritura

**Objetivo:** Validar el comportamiento ante múltiples fallos de escritura.

**Pre-condiciones:**
- Puerto con problemas (cable suelto, interferencia)

**Pasos:**
1. Simular fallos de escritura en `comController.write()`
2. Observar contador `consecutiveWriteFailures`
3. Alcanzar 3 fallos consecutivos

**Resultado Esperado:**
- ✓ Contador incrementa en cada fallo
- ✓ Al llegar a 3: polling se detiene
- ✓ Log: "Demasiados fallos de escritura consecutivos (3). Abortando polling."
- ✓ `isPollingActive.value = false`

**Criterios de Aceptación:**
- Polling se detiene tras 3 fallos
- Sin intentos infinitos
- Estado limpio

---

## 5. CASOS DE PRUEBA - INYECCIÓN DE LLAVES

### CP-INJ-001: Inyección de Perfil Completo

**Objetivo:** Validar inyección de un perfil con múltiples llaves.

**Pre-condiciones:**
- Perfil con 4 llaves configuradas:
  1. PIN (slot 1)
  2. MAC (slot 2)
  3. DATA (slot 3)
  4. DUKPT_TDES (slot 4, con KSN)
- Llaves en base de datos

**Pasos:**
1. Seleccionar perfil
2. Ejecutar `startKeyInjection()`
3. Observar progreso de inyección
4. Verificar resultado final

**Resultado Esperado:**
- ✓ Estado: CONNECTING → INJECTING → SUCCESS
- ✓ Progreso: 0% → 25% → 50% → 75% → 100%
- ✓ 4 comandos enviados secuencialmente
- ✓ Pausa de 500ms entre inyecciones
- ✓ Todas las llaves inyectadas exitosamente
- ✓ Log completo con detalles de cada llave
- ✓ Mensaje: "¡Inyección completada exitosamente!"

**Criterios de Aceptación:**
- 4/4 llaves inyectadas
- KCVs validados
- Sin errores
- Tiempo total < 30 segundos

---

### CP-INJ-002: Validación de Integridad de Llave

**Objetivo:** Verificar validaciones antes de inyectar.

**Pre-condiciones:**
- Llave en base de datos

**Escenarios de Validación:**

| Validación | Dato Inválido | Resultado Esperado |
|------------|---------------|-------------------|
| Datos vacíos | keyData = "" | Exception: "no tiene datos" |
| KCV vacío | kcv = "" | Exception: "no tiene KCV válido" |
| Longitud inválida | keyData = 32 chars (16 bytes DUKPT) | Exception: "Longitud inválida" |
| Longitud válida 3DES | 32, 64, 96 chars | ✓ Validación exitosa |
| Longitud válida AES | 32, 48, 64 chars | ✓ Validación exitosa |
| KSN DUKPT inválido | ksn.length != 20 | Exception: "KSN inválido" |
| Datos no hex | keyData = "ZZGG" | Exception: "no son hexadecimales válidos" |
| KCV no hex | kcv = "XY" | Exception: "KCV no es hexadecimal válido" |

**Criterios de Aceptación:**
- Todas las validaciones funcionan
- Mensajes de error claros
- No se inyectan llaves inválidas

---

### CP-INJ-003: Mapeo de Tipos de Llave Futurex

**Objetivo:** Validar el mapeo correcto de tipos de llave.

**Casos de Mapeo:**

| Tipo Original | Código Futurex | Descripción |
|---------------|----------------|-------------|
| PIN | "05" | PIN Encryption Key |
| MAC | "04" | MAC Key |
| TDES | "01" | Master Session Key |
| 3DES | "01" | Master Session Key |
| AES | "01" | Master Session Key |
| DUKPT | "08" | DUKPT 3DES BDK |
| DUKPT_TDES | "08" | DUKPT 3DES BDK |
| DUKPT_3DES | "08" | DUKPT 3DES BDK |
| DUKPT_AES | "10" | DUKPT AES BDK |
| DUKPT_INITIAL | "03" | DUKPT 3DES IPEK |
| IPEK | "03" | DUKPT 3DES IPEK |
| DUKPT_AES_INITIAL | "0B" | DUKPT AES IPEK |
| AES_IPEK | "0B" | DUKPT AES IPEK |
| DATA | "0C" | Data Encryption Key |
| (desconocido) | "01" | Master Session Key (default) |

**Resultado Esperado:**
- ✓ Cada tipo mapeado correctamente
- ✓ Log: "Tipo mapeado: 'PIN' -> '05'"
- ✓ Descripción correcta en logs

**Criterios de Aceptación:**
- Todos los tipos mapeados
- Sin errores de mapeo

---

### CP-INJ-004: Generación Automática de KSN

**Objetivo:** Validar generación de KSN cuando no se proporciona.

**Pre-condiciones:**
- Llave DUKPT sin KSN configurado
- Llave con KCV conocido

**Pasos:**
1. Configurar llave DUKPT_TDES en slot 5
2. No especificar KSN (vacío)
3. Iniciar inyección
4. Observar generación automática

**Resultado Esperado:**
- ✓ Log: "KSN no válido en perfil, generando automáticamente..."
- ✓ KSN generado: base (KCV padded) + suffix (slot en hex)
- ✓ Ejemplo: KCV="AABB" → base="AABB000000000000" + suffix="0005" → KSN="AABB000000000000005"
- ✓ Longitud final = 20 caracteres
- ✓ Log: "✓ KSN generado exitosamente: AABB000000000000005"

**Criterios de Aceptación:**
- KSN generado correctamente
- Longitud = 20
- Formato hexadecimal válido

---

### CP-INJ-005: Formato de Longitud de Llave (ASCII HEX)

**Objetivo:** Validar formato correcto de longitud en comando Futurex.

**Casos:**

| Longitud Bytes | Formato Esperado | Validación |
|----------------|------------------|------------|
| 16 | "010" | 0x010 = 16 en hex, 3 dígitos |
| 32 | "020" | 0x020 = 32 en hex, 3 dígitos |
| 48 | "030" | 0x030 = 48 en hex, 3 dígitos |

**Pasos:**
1. Preparar llaves de 16, 32 y 48 bytes
2. Construir comando de inyección
3. Verificar campo `keyLength`

**Resultado Esperado:**
- ✓ 16 bytes → keyLength = "010"
- ✓ 32 bytes → keyLength = "020"
- ✓ 48 bytes → keyLength = "030"
- ✓ Log: "Longitud de llave: 010 (16 bytes)"
- ✓ Log: "Formato: ASCII HEX (3 dígitos)"
- ✓ Log: "Validación: ✓ Válido"

**Criterios de Aceptación:**
- Formato ASCII HEX correcto
- 3 dígitos siempre
- Sin errores de longitud

---

### CP-INJ-006: Logs Detallados de Inyección

**Objetivo:** Verificar que los logs proveen información completa para debugging.

**Pre-condiciones:**
- Inyección en curso

**Logs Esperados:**

```
=== INICIANDO PROCESO DE INYECCIÓN FUTUREX ===
Perfil: Test Profile
Configuraciones de llave: 2
  1. PIN - Slot: 1 - Tipo: PIN
  2. MAC - Slot: 2 - Tipo: MAC

=== PROCESANDO LLAVE 1/2 ===
Uso: PIN
Slot: 1
Tipo: PIN

=== INICIANDO INYECCIÓN DE LLAVE FUTUREX ===
Configuración de llave:
  - Uso: PIN
  - Slot: 1
  - Tipo: PIN
  - Llave seleccionada: AABB

Llave encontrada en base de datos:
  - KCV: AABB
  - Longitud de datos: 16 bytes
  - Datos (primeros 32 bytes): AABBCCDDEEFF00112233445566778899

=== VALIDANDO INTEGRIDAD DE LLAVE FUTUREX ===
✓ Integridad de llave validada:
  - KCV: AABB
  - Longitud: 16 bytes
  - Tipo: PIN
  - Datos válidos: Sí

=== ESTRUCTURA FUTUREX PARA INYECCIÓN DE LLAVE ===
Comando: 02 (Inyección de llave simétrica)
Versión: 01
Slot de llave: 01 (1)
Slot KTK: 00
Tipo de llave: 05 (PIN)
Tipo de encriptación: 00 (Carga en claro)
Checksum de llave: AABB (KCV: AABB)
Checksum KTK: 0000
KSN: 00000000000000000000 (20 caracteres)
Longitud de llave: 010 (16 bytes)
  - Formato: ASCII HEX (3 dígitos)
  - Valor: '010'
  - Validación: ✓ Válido
Datos de llave (hex): AABBCCDDEEFF00112233445566778899

=== ENVIANDO DATOS FUTUREX ===
Tamaño de datos: 65 bytes
Datos en hexadecimal: 0x02 0x30 0x32 ...
Datos en ASCII: <STX>02010005000AABB0000...

=== ESPERANDO RESPUESTA FUTUREX ===
Timeout configurado: 10000ms
Respuesta recibida exitosamente:
  - Bytes leídos: 10
  - Datos en hexadecimal: 0x02 0x30 0x32 0x30 0x30 0x41 0x41 0x42 0x42 0x03
  - Datos en ASCII: <STX>0200AABB<ETX>

=== PROCESANDO RESPUESTA FUTUREX ===
Configuración de llave: PIN (Slot: 1)
Respuesta recibida: 10 bytes
Respuesta parseada como InjectSymmetricKeyResponse:
  - Código de respuesta: 00
  - Checksum de llave: AABB
  - Payload completo: 0200AABB
✓ Inyección exitosa para PIN

=== INYECCIÓN DE LLAVE FUTUREX COMPLETADA ===

=== INYECCIÓN FUTUREX COMPLETADA EXITOSAMENTE ===
```

**Criterios de Aceptación:**
- Logs completos y descriptivos
- Información útil para debugging
- Sin datos sensibles expuestos

---

## 6. CASOS DE PRUEBA - CAMBIO DE SUBPOS

### CP-SWAP-001: Cambio de SubPOS Durante Polling

**Objetivo:** Validar el cambio de dispositivo SubPOS mientras el MasterPOS está activo.

**Pre-condiciones:**
- MasterPOS con polling activo
- SubPOS #1 conectado y respondiendo

**Pasos:**
1. Verificar polling activo con SubPOS #1
2. Desconectar SubPOS #1 físicamente
3. Observar detección de desconexión
4. Conectar SubPOS #2
5. SubPOS #2 inicia listening
6. Observar reconexión

**Resultado Esperado:**
- ✓ Desconexión detectada en < 7 segundos
- ✓ `isConnected = false`
- ✓ Polling continúa intentando
- ✓ SubPOS #2 se conecta
- ✓ Primera respuesta POLL recibida
- ✓ `isConnected = true`
- ✓ Comunicación normal restablecida

**Criterios de Aceptación:**
- Cambio de dispositivo transparente
- Sin intervención manual
- Sin pérdida de estado en MasterPOS
- Tiempo de reconexión < 10 segundos

---

### CP-SWAP-002: Cambio de SubPOS Durante Inyección

**Objetivo:** Validar comportamiento si se desconecta SubPOS durante inyección.

**Pre-condiciones:**
- Inyección en progreso (llave 2 de 4)

**Pasos:**
1. Iniciar inyección de perfil con 4 llaves
2. Durante la llave 2, desconectar SubPOS
3. Observar manejo de error

**Resultado Esperado:**
- ✓ Timeout al esperar respuesta de llave 2
- ✓ Exception: "Timeout o error al leer respuesta"
- ✓ Estado: INJECTING → ERROR
- ✓ Log: "Error durante la inyección de llaves"
- ✓ Comunicación cerrada correctamente
- ✓ Polling reiniciado
- ✓ Estado limpio para siguiente intento

**Criterios de Aceptación:**
- Error manejado sin crash
- Estado consistente
- Posibilidad de reintentar
- Sin bloqueos de recursos

---

### CP-SWAP-003: Cambio Rápido de Múltiples SubPOS

**Objetivo:** Validar cambio de 3 SubPOS consecutivos.

**Pre-condiciones:**
- MasterPOS activo
- 3 SubPOS disponibles

**Pasos:**
1. Conectar SubPOS #1, inyectar perfil completo
2. Desconectar SubPOS #1
3. Conectar SubPOS #2, inyectar perfil completo
4. Desconectar SubPOS #2
5. Conectar SubPOS #3, inyectar perfil completo

**Resultado Esperado:**
- ✓ 3 inyecciones exitosas
- ✓ Cada SubPOS detectado correctamente
- ✓ Sin interferencia entre dispositivos
- ✓ Estado del MasterPOS estable

**Criterios de Aceptación:**
- 3/3 inyecciones exitosas
- Sin errores de sincronización
- Tiempo por ciclo < 2 minutos

---

### CP-SWAP-004: Estado de Llaves Entre SubPOS

**Objetivo:** Verificar que cada SubPOS mantiene su propio estado de llaves.

**Pre-condiciones:**
- SubPOS #1 y SubPOS #2 limpios

**Pasos:**
1. Conectar SubPOS #1
2. Inyectar Perfil A (llaves en slots 1, 2, 3)
3. Desconectar SubPOS #1
4. Conectar SubPOS #2
5. Inyectar Perfil B (llaves en slots 4, 5, 6)
6. Verificar que cada dispositivo tiene solo sus llaves

**Resultado Esperado:**
- ✓ SubPOS #1 tiene llaves en slots 1, 2, 3
- ✓ SubPOS #2 tiene llaves en slots 4, 5, 6
- ✓ Sin llaves duplicadas
- ✓ Bases de datos independientes

**Criterios de Aceptación:**
- Independencia de estado
- Sin contaminación entre dispositivos

---

## 7. CASOS DE PRUEBA - ROBUSTEZ

### CP-ROB-001: Reconexión Tras Desconexión Física

**Objetivo:** Validar recuperación automática tras desconexión del cable USB.

**Pre-condiciones:**
- Comunicación activa

**Pasos:**
1. Establecer comunicación normal
2. Desconectar cable USB
3. Esperar 10 segundos
4. Reconectar cable USB
5. Observar recuperación

**Resultado Esperado:**
- ✓ Desconexión detectada (timeout en read/write)
- ✓ Polling continúa intentando
- ✓ Al reconectar: puerto detectado nuevamente
- ✓ Auto-scan ejecutado (Aisino)
- ✓ Comunicación restablecida
- ✓ Estado: DISCONNECTED → INITIALIZING → LISTENING

**Criterios de Aceptación:**
- Recuperación automática
- Sin intervención manual
- Tiempo de recuperación < 15 segundos

---

### CP-ROB-002: Manejo de Datos Corruptos

**Objetivo:** Validar comportamiento ante datos corruptos en recepción.

**Pre-condiciones:**
- Comunicación activa
- Interferencia simulada

**Escenarios:**
1. **LRC inválido**: Alterar último byte del mensaje
2. **STX faltante**: Enviar mensaje sin 0x02 inicial
3. **ETX faltante**: Enviar mensaje sin 0x03
4. **Longitud inconsistente**: Declarar longitud incorrecta

**Resultado Esperado:**
- ✓ LRC inválido → Mensaje rechazado, error "Bad LRC"
- ✓ STX faltante → Mensaje no parseado, esperando siguiente
- ✓ ETX faltante → Timeout, mensaje descartado
- ✓ Longitud incorrecta → Error de parsing

**Criterios de Aceptación:**
- Sin crashes
- Mensajes válidos subsiguientes procesados
- Logs de error informativos

---

### CP-ROB-003: Múltiples Apertura/Cierre de Puerto

**Objetivo:** Validar estabilidad con ciclos repetidos de apertura/cierre.

**Pasos:**
1. Repetir 50 veces:
   - Abrir puerto
   - Enviar mensaje
   - Recibir respuesta
   - Cerrar puerto
2. Verificar sin memory leaks

**Resultado Esperado:**
- ✓ 50/50 ciclos exitosos
- ✓ Sin degradación de performance
- ✓ Sin memory leaks
- ✓ Puerto siempre liberado correctamente

**Criterios de Aceptación:**
- 100% éxito
- Uso de memoria estable
- Tiempo por ciclo constante

---

### CP-ROB-004: Inyección Bajo Carga

**Objetivo:** Validar inyección con carga del sistema.

**Pre-condiciones:**
- Sistema bajo alta carga (CPU, memoria)

**Pasos:**
1. Ejecutar aplicaciones pesadas en paralelo
2. Iniciar inyección de perfil con 10 llaves
3. Verificar completitud

**Resultado Esperado:**
- ✓ 10/10 llaves inyectadas
- ✓ Sin timeouts
- ✓ Sin errores de comunicación
- ✓ Tiempo de inyección dentro de rango aceptable (< 2x tiempo normal)

**Criterios de Aceptación:**
- Inyección exitosa
- Sin errores
- Tolerancia a carga del sistema

---

### CP-ROB-005: Recuperación de Estado Tras Error

**Objetivo:** Validar que el sistema recupera estado limpio tras error.

**Pre-condiciones:**
- Error durante inyección (timeout, LRC inválido, etc.)

**Pasos:**
1. Provocar error durante inyección
2. Observar manejo de error
3. Intentar nueva inyección inmediatamente

**Resultado Esperado:**
- ✓ Error capturado y loggeado
- ✓ Puerto cerrado correctamente
- ✓ Estado resetado: IDLE
- ✓ Segunda inyección exitosa sin residuos del error anterior

**Criterios de Aceptación:**
- Estado limpio
- Sin recursos bloqueados
- Segunda inyección normal

---

### CP-ROB-006: Condiciones de Carrera en Polling

**Objetivo:** Validar sincronización en acceso concurrente al puerto.

**Pre-condiciones:**
- Polling activo
- Múltiples threads accediendo

**Pasos:**
1. Polling activo enviando POLL cada 2s
2. Intentar ejecutar inyección simultáneamente (sin detener polling)
3. Observar manejo de concurrencia

**Resultado Esperado:**
- ✓ Mutex protege acceso al puerto
- ✓ Inyección detecta polling activo
- ✓ Polling se detiene antes de inyección
- ✓ Puerto liberado para inyección
- ✓ Sin deadlocks

**Criterios de Aceptación:**
- Sin condiciones de carrera
- Mutex funcional
- Sin deadlocks

---

## 8. MATRIZ DE COMPATIBILIDAD

### Matriz de Configuraciones

| MasterPOS | SubPOS | Protocolo | Estado | Notas |
|-----------|--------|-----------|--------|-------|
| Aisino | Aisino | Futurex | ✅ Soportado | Configuración principal |
| Aisino | Aisino | Legacy | ✅ Soportado | Solo polling |
| Aisino | Newpos | Futurex | ✅ Soportado | Auto-scan solo en Master |
| Aisino | Newpos | Legacy | ✅ Soportado | Solo polling |

### Pruebas de Compatibilidad

#### CP-COMPAT-001: Aisino MasterPOS ↔ Aisino SubPOS (Futurex)

**Configuración:**
- MasterPOS: Aisino, Protocolo Futurex
- SubPOS: Aisino, Protocolo Futurex

**Pruebas:**
1. Inicialización de ambos SDKs
2. Auto-scan en ambos dispositivos
3. Polling Legacy activo
4. Inyección Futurex completa

**Resultado Esperado:**
- ✅ Todos los componentes funcionales
- ✅ Auto-scan alineado
- ✅ Comunicación estable

---

#### CP-COMPAT-002: Aisino MasterPOS ↔ Newpos SubPOS (Futurex)

**Configuración:**
- MasterPOS: Aisino, Protocolo Futurex
- SubPOS: Newpos, Protocolo Futurex

**Pruebas:**
1. Inicialización mixta (Aisino SDK + Newpos SDK)
2. Auto-scan solo en MasterPOS
3. Polling cruzado
4. Inyección Futurex cruzada

**Resultado Esperado:**
- ✅ SDKs diferentes pero compatible
- ✅ Auto-scan solo en Aisino
- ✅ Newpos usa configuración estática
- ✅ Inyección exitosa

**Consideraciones:**
- Newpos no requiere auto-scan
- Baudrate fijo en Newpos (verificar compatibilidad)

---

## 9. CRITERIOS DE ACEPTACIÓN GLOBAL

### Performance

| Métrica | Objetivo | Crítico |
|---------|----------|---------|
| Tiempo de inicialización SDK | < 5s | < 10s |
| Detección de conexión | < 5s | < 10s |
| Detección de desconexión | < 7s | < 15s |
| Inyección de llave simple | < 3s | < 5s |
| Inyección de perfil (10 llaves) | < 30s | < 60s |
| Respuesta a POLL | < 500ms | < 1s |
| Timeout de lectura | 5s configurable | N/A |
| Intervalo de polling | 2s | N/A |

### Confiabilidad

| Métrica | Objetivo |
|---------|----------|
| Tasa de éxito de inyección | > 99% |
| Tasa de detección de conexión | > 99% |
| Recuperación tras error | 100% |
| Ausencia de memory leaks | 100% |
| Ausencia de deadlocks | 100% |

### Robustez

- ✅ Sistema opera 8 horas continuas sin degradación
- ✅ Maneja 100 ciclos de conexión/desconexión sin errores
- ✅ Inyecta 1000 llaves consecutivas sin fallos
- ✅ Soporta cambio de 20 SubPOS consecutivos
- ✅ Recupera de 100% de errores de comunicación

---

## 10. PROCEDIMIENTOS DE PRUEBA

### 10.1 Setup de Entorno de Pruebas

**Requisitos:**
1. 1x Dispositivo Aisino (MasterPOS / Injector)
2. 2x Dispositivos SubPOS (1x Aisino, 1x Newpos)
3. Cables USB de calidad
4. Fuente de alimentación estable
5. Llaves de prueba en base de datos
6. Perfiles de prueba configurados

**Configuración Inicial:**
```kotlin
// SystemConfig.kt
SystemConfig.managerSelected = EnumManufacturer.AISINO
SystemConfig.commProtocolSelected = CommProtocol.FUTUREX
SystemConfig.deviceRole = DeviceRole.MASTER // en Injector
SystemConfig.deviceRole = DeviceRole.SUBPOS // en App
SystemConfig.aisinoCandidatePorts = listOf(0, 1)
SystemConfig.aisinoCandidateBauds = listOf(9600, 115200)
```

**Base de Datos de Prueba:**
```sql
-- Llaves de prueba
INSERT INTO injected_keys (kcv, keyData, keyType) VALUES
  ('AABB', 'AABBCCDDEEFF00112233445566778899', 'PIN'),
  ('CCDD', 'CCDD0011223344556677889900AABBCC', 'MAC'),
  ('EEFF', 'EEFF0011223344556677889900AABBCC', 'DATA'),
  ('1122', '11223344556677889900AABBCCDDEEFF', 'DUKPT_TDES');

-- Perfiles de prueba
INSERT INTO profiles (name) VALUES ('Test Profile');
INSERT INTO key_configurations (profileId, usage, keyType, slot, selectedKey, ksn) VALUES
  (1, 'PIN', 'PIN', 1, 'AABB', '00000000000000000000'),
  (1, 'MAC', 'MAC', 2, 'CCDD', '00000000000000000000'),
  (1, 'DATA', 'DATA', 3, 'EEFF', '00000000000000000000'),
  (1, 'DUKPT', 'DUKPT_TDES', 4, '1122', 'F876543210000000000A');
```

---

### 10.2 Ejecución de Pruebas

#### Pruebas Manuales

1. **Checklist Pre-Prueba:**
   - [ ] Dispositivos cargados (> 50% batería)
   - [ ] Cables USB funcionales
   - [ ] Aplicaciones instaladas (versión correcta)
   - [ ] Base de datos con datos de prueba
   - [ ] Logs habilitados
   - [ ] Perfiles configurados

2. **Ejecución Paso a Paso:**
   - Ejecutar casos de prueba en orden secuencial
   - Documentar resultados en hoja de registro
   - Capturar logs completos
   - Tomar screenshots de errores
   - Anotar tiempos de ejecución

3. **Checklist Post-Prueba:**
   - [ ] Todos los logs guardados
   - [ ] Resultados documentados
   - [ ] Defectos reportados
   - [ ] Dispositivos restaurados a estado inicial

#### Pruebas Automatizadas

**Framework:** Espresso + JUnit

**Ejemplo de Test Automatizado:**
```kotlin
@Test
fun testSerialCommunication_OpenClose_Success() {
    // Arrange
    val comController = CommunicationSDKManager.getComController()
    assertNotNull(comController)

    // Act
    val initResult = comController.init(
        EnumCommConfBaudRate.BPS_115200,
        EnumCommConfParity.NOPAR,
        EnumCommConfDataBits.DB_8
    )
    val openResult = comController.open()
    val closeResult = comController.close()

    // Assert
    assertEquals(0, openResult)
    assertEquals(0, closeResult)
}

@Test
fun testFuturexMessage_Format_ValidStructure() {
    // Arrange
    val formatter = FuturexMessageFormatter

    // Act
    val message = formatter.format("02", listOf(
        "01", "03", "00", "05", "00", "AABB", "0000",
        "00000000000000000000", "010",
        "AABBCCDDEEFF00112233445566778899"
    ))

    // Assert
    assertEquals(0x02.toByte(), message[0]) // STX
    assertEquals(0x03.toByte(), message[message.size - 2]) // ETX
    assertTrue(message.size > 10) // Tamaño mínimo
    // Validar LRC
    val expectedLrc = calculateLRC(message.sliceArray(0 until message.size - 1))
    assertEquals(expectedLrc, message[message.size - 1])
}

@Test
fun testPolling_MasterSendsSubReceives_Success() = runTest {
    // Arrange
    val pollingService = PollingService()
    pollingService.initialize()
    var pollReceived = false

    // Act
    pollingService.startMasterPolling { connected ->
        // Callback de conexión
    }

    delay(3000) // Esperar 1 ciclo de polling

    // Assert
    assertTrue(pollReceived)
    pollingService.stopPolling()
}
```

---

### 10.3 Métricas de Cobertura

**Objetivo de Cobertura:**
- Líneas de código: > 80%
- Funciones críticas: 100%
- Branches: > 70%

**Módulos Críticos (Cobertura 100%):**
- `AisinoCommunicationManager.kt`
- `FuturexMessageFormatter.kt`
- `FuturexMessageParser.kt`
- `KeyInjectionViewModel.kt` (flujo de inyección)
- `PollingService.kt`

---

## 11. REGISTRO DE DEFECTOS

### Plantilla de Reporte de Defecto

```
ID: DEF-USB-001
Título: [Descripción breve]
Severidad: [Crítica / Alta / Media / Baja]
Prioridad: [Alta / Media / Baja]
Caso de Prueba: [CP-XXX-YYY]

Descripción:
[Descripción detallada del problema]

Pasos para Reproducir:
1. [Paso 1]
2. [Paso 2]
3. [Paso 3]

Resultado Actual:
[Qué ocurrió]

Resultado Esperado:
[Qué debería ocurrir]

Logs:
```
[Fragmento de logs relevantes]
```

Screenshots:
[Adjuntar capturas]

Entorno:
- Dispositivo MasterPOS: [Modelo]
- Dispositivo SubPOS: [Modelo]
- Versión de App: [X.Y.Z]
- Versión de SDK: [X.Y.Z]

Estado: [Abierto / En Progreso / Resuelto / Cerrado]
Asignado a: [Nombre]
```

---

## 12. CHECKLIST DE VALIDACIÓN FINAL

### Pre-Release Checklist

- [ ] **Comunicación Serial**
  - [ ] CP-USB-001 a CP-USB-007 ejecutados: 7/7 ✅
  - [ ] Auto-scan funcional en Aisino
  - [ ] Apertura/cierre estable

- [ ] **Protocolo Futurex**
  - [ ] CP-FTX-001 a CP-FTX-010 ejecutados: 10/10 ✅
  - [ ] Todos los comandos funcionales (02, 03, 04, 05, 06)
  - [ ] LRC validado correctamente
  - [ ] Códigos de error manejados

- [ ] **Protocolo Legacy**
  - [ ] CP-POLL-001 a CP-POLL-006 ejecutados: 6/6 ✅
  - [ ] Polling estable
  - [ ] Detección de conexión/desconexión
  - [ ] Integración con inyección

- [ ] **Inyección de Llaves**
  - [ ] CP-INJ-001 a CP-INJ-006 ejecutados: 6/6 ✅
  - [ ] Perfiles completos inyectados
  - [ ] Validaciones funcionando
  - [ ] KSN para DUKPT
  - [ ] Logs detallados

- [ ] **Cambio de SubPOS**
  - [ ] CP-SWAP-001 a CP-SWAP-004 ejecutados: 4/4 ✅
  - [ ] Cambio transparente
  - [ ] Estado independiente por dispositivo

- [ ] **Robustez**
  - [ ] CP-ROB-001 a CP-ROB-006 ejecutados: 6/6 ✅
  - [ ] Reconexión automática
  - [ ] Manejo de errores
  - [ ] Sin memory leaks
  - [ ] Sin deadlocks

- [ ] **Compatibilidad**
  - [ ] CP-COMPAT-001 ejecutado: Aisino ↔ Aisino ✅
  - [ ] CP-COMPAT-002 ejecutado: Aisino ↔ Newpos ✅

- [ ] **Performance**
  - [ ] Todas las métricas dentro de objetivos
  - [ ] Tiempo de inyección < 30s (10 llaves)
  - [ ] Detección de conexión < 5s

- [ ] **Documentación**
  - [ ] Plan de pruebas completo
  - [ ] Resultados documentados
  - [ ] Defectos cerrados o documentados
  - [ ] Manual de usuario actualizado

---

## 13. CONCLUSIONES Y RECOMENDACIONES

### Resumen

Este plan de pruebas cubre de forma exhaustiva los componentes críticos de la comunicación USB entre dispositivos MasterPOS y SubPOS, incluyendo:

- ✅ Inicialización y configuración de SDKs
- ✅ Comunicación serial robusta con auto-scan
- ✅ Implementación completa del protocolo Futurex
- ✅ Sistema de polling Legacy para detección de conexión
- ✅ Inyección segura de llaves criptográficas
- ✅ Soporte para cambio dinámico de SubPOS
- ✅ Manejo exhaustivo de errores y recuperación

### Recomendaciones

1. **Automatización:** Priorizar automatización de casos críticos (CP-USB-*, CP-FTX-*)
2. **Monitoreo Continuo:** Implementar telemetría para detectar problemas en producción
3. **Stress Testing:** Ejecutar pruebas de carga extendidas (24h+)
4. **Documentación:** Mantener logs detallados de producción para análisis post-mortem
5. **Actualizaciones de SDK:** Validar compatibilidad con nuevas versiones de SDK Vanstone/Newpos

---

**Fin del Plan de Pruebas**

---

## ANEXO A: Glosario

| Término | Definición |
|---------|------------|
| MasterPOS | Dispositivo Aisino que ejecuta la aplicación Injector y envía comandos de inyección |
| SubPOS | Dispositivo (Aisino o Newpos) que ejecuta la aplicación App y recibe llaves |
| PED | Pin Entry Device - Módulo de seguridad para almacenamiento de llaves |
| KCV | Key Check Value - Checksum de validación de llave |
| KSN | Key Serial Number - Número de serie para llaves DUKPT |
| DUKPT | Derived Unique Key Per Transaction - Esquema de llaves derivadas |
| LRC | Longitudinal Redundancy Check - Verificación de integridad |
| STX | Start of Text (0x02) - Inicio de mensaje |
| ETX | End of Text (0x03) - Fin de mensaje |
| Futurex | Protocolo de comunicación para inyección de llaves |
| Legacy | Protocolo simple para polling y detección de conexión |

## ANEXO B: Referencias

1. Manual Técnico Futurex v3.8.3
2. Vanstone SDK Documentation
3. Newpos SDK Documentation
4. `INYECCION_LLAVES_PERFIL.md`
5. `communication/src/main/java/com/example/communication/` (módulo)
6. `injector/src/main/java/com/vigatec/injector/viewmodel/KeyInjectionViewModel.kt`
7. `app/src/main/java/com/vigatec/android_injector/viewmodel/MainViewModel.kt`
