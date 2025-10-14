# Documentación del Sistema de Inyección de Llaves Criptográficas

## Parte 2: Aplicaciones y Módulos del Sistema

### Versión: 1.0
### Fecha: Octubre 2025

---

## 1. APLICACIÓN INJECTOR (Dispositivo Maestro)

### 1.1 Descripción General

La aplicación **Injector** es el componente maestro del sistema que:
- Genera llaves criptográficas mediante ceremonia
- Gestiona perfiles de configuración de llaves
- Inyecta llaves en dispositivos terminales
- Controla autenticación de usuarios
- Gestiona llaves de cifrado (KEK)

**Rol del Dispositivo**: MASTER  
**Paquete**: `com.vigatec.injector`  
**Dispositivo Típico**: Aisino A90 Pro

### 1.2 Pantallas Principales

#### 1.2.1 Pantalla de Login

**Archivo**: `LoginScreen.kt`  
**ViewModel**: `LoginViewModel`

**Funcionalidad**:
- Autenticación de usuarios mediante usuario y contraseña
- Usuario por defecto: `admin` / `admin`
- Validación contra base de datos local

**Flujo**:
1. Usuario ingresa credenciales
2. ViewModel valida contra `UserRepository`
3. Si es exitoso, navega a pantalla principal
4. Si falla, muestra mensaje de error

#### 1.2.2 Pantalla de Ceremonia de Llaves

**Archivo**: `CeremonyScreen.kt`  
**ViewModel**: `CeremonyViewModel`

**Funcionalidad**: Generación segura de llaves mediante división de secretos

**Parámetros**:
- **Número de custodios**: 2-5 custodios
- **Tipo de llave**: TDES, AES (128/192/256), DUKPT
- **Slot de almacenamiento**: 0-99
- **Tipo de llave generada**: Operacional o KEK

**Proceso**:
1. **Configuración Inicial**:
   - Selección de tipo de llave
   - Definición de número de custodios
   - Selección de slot de destino

2. **Recolección de Componentes**:
   - Cada custodio ingresa su componente en formato hexadecimal
   - Validación de longitud (16, 24 o 32 bytes según tipo)
   - Ocultamiento de componentes con asteriscos

3. **Generación de Llave**:
   - Combinación mediante operación XOR de todos los componentes
   - Cálculo del KCV (Key Check Value)
   - Determinación del algoritmo según longitud

4. **Almacenamiento**:
   - Guardado en Android KeyStore (seguridad hardware)
   - Registro en base de datos con datos completos
   - Marcado como KEK o llave operacional

**Logs Detallados**:
```
=== PROCESANDO COMPONENTES PARA GENERAR LLAVE FINAL ===
Componentes recolectados:
  1. AABBCCDDEEFF0011... (16 bytes)
  2. 1122334455667788... (16 bytes)
  3. FFEEDDCCBBAA9988... (16 bytes)

Aplicando operación XOR a los componentes...
  - Componente AABBCCDDEEFF0011... → 16 bytes
  - XOR: 16 bytes ⊕ 16 bytes = 16 bytes
  - XOR: 16 bytes ⊕ 16 bytes = 16 bytes

✓ Llave final generada exitosamente
  - Longitud: 16 bytes
  - Datos (hex): [primeros 32 caracteres]
  - KCV: AABBCC
```

**Verificación Post-Almacenamiento**:
- Consulta a base de datos para confirmar
- Comparación de datos almacenados vs originales
- Validación de integridad completa

#### 1.2.3 Pantalla de Perfiles

**Archivo**: `ProfilesScreen.kt`  
**ViewModel**: `ProfileViewModel`

**Funcionalidad**: Gestión completa de perfiles de inyección

**Componentes UI**:

**Header con Estadísticas**:
- Total de perfiles
- Perfiles configurados (con llaves)
- Perfiles listos para inyectar

**Tarjetas de Perfil**:
- Avatar con gradiente según tipo de aplicación
- Nombre y descripción del perfil
- Tipo de aplicación
- Badges de estado (configurado/listo)
- Vista previa de configuraciones de llaves
- Botones de acción:
  - Editar perfil
  - Gestionar llaves
  - Inyectar llaves (▶️)
  - Eliminar perfil

**Modal de Creación/Edición de Perfil**:

**Información Básica**:
- Nombre del perfil
- Descripción
- Tipo de aplicación (Transaccional, PIN, etc.)

**Configuración de Cifrado KEK** (Opcional):
- Toggle para activar cifrado con KEK
- Selector de KEK disponible (por KCV)
- Indicador visual de KEK activa/exportada/inactiva

**Gestión de Configuraciones de Llaves**:

Cada configuración contiene:
- **Uso**: Descripción del propósito (ej: "PIN Entry")
- **Tipo de Llave**:
  - TDES/3DES
  - AES
  - DUKPT_TDES
  - DUKPT_AES
  - PIN
  - MAC
  - DATA
- **Slot**: Posición en el PED (0-99)
- **Llave Seleccionada**: Dropdown con llaves disponibles (por KCV)
- **KSN** (solo para DUKPT): Key Serial Number de 20 caracteres hexadecimales

**Validaciones**:
- KSN obligatorio para tipos DUKPT
- KSN debe tener exactamente 20 caracteres hexadecimales
- Slot debe ser único por perfil
- Al menos una configuración de llave requerida

**Layouts Adaptativos**:
- **2 Columnas**: Para pantallas anchas
- **1 Columna**: Para pantallas estrechas

#### 1.2.4 Modal de Inyección de Llaves

**Archivo**: `KeyInjectionModal.kt`  
**ViewModel**: `KeyInjectionViewModel`

**Funcionalidad**: Proceso de inyección en tiempo real

**Componentes de UI**:

**Header**:
- Nombre del perfil
- Icono de estado animado

**Información de Conexión**:
- Estado de conexión con código de color
- Puerto y baudrate detectados
- Protocolo utilizado (Futurex)

**Progreso de Inyección**:
- Barra de progreso visual
- Contador: "Llave X de Y"
- Porcentaje completado

**Log de Inyección**:
- Área de logs en tiempo real
- Fuente monoespaciada para mejor lectura
- Auto-scroll al último mensaje
- Colores por nivel de log

**Estados**:
- **IDLE**: Esperando inicio
- **CONNECTING**: Estableciendo comunicación
- **INJECTING**: Inyectando llaves
- **SUCCESS**: Completado exitosamente
- **ERROR**: Error durante proceso

**Botones Contextuales**:
- **Iniciar Inyección**: Estado IDLE
- **Cancelar**: Durante inyección
- **Ver Detalles**: Tras completar
- **Cerrar**: Al finalizar

**Flujo de Inyección**:

1. **Pre-validación**:
   - Verificar llaves en base de datos
   - Validar integridad de cada llave
   - Comprobar KEK si es necesaria

2. **Conexión**:
   - Inicializar comunicación serial
   - Configurar puerto (115200 bps, 8N1)
   - Abrir puerto
   - Detener polling durante inyección

3. **Inyección Secuencial**:
   - Para cada configuración en el perfil:
     - Obtener llave de base de datos
     - Validar integridad (KCV, longitud, formato)
     - Construir comando Futurex
     - Cifrar con KEK si está configurada
     - Enviar comando
     - Esperar respuesta (timeout 10s)
     - Validar respuesta
     - Pausa de 500ms entre llaves

4. **Finalización**:
   - Cerrar comunicación
   - Reiniciar polling
   - Actualizar estado
   - Mostrar resumen

#### 1.2.5 Pantalla de Llaves Inyectadas

**Archivo**: `InjectedKeysScreen.kt`  
**ViewModel**: Compartido con `ProfileViewModel`

**Funcionalidad**: Visualización de llaves generadas/inyectadas

**Información Mostrada**:
- KCV (Key Check Value)
- Tipo de llave
- Algoritmo
- Slot de almacenamiento
- Fecha de creación
- Estado (ACTIVE, EXPORTED, INACTIVE)
- Nombre personalizado (si tiene)
- Flag de KEK

**Acciones**:
- Ver detalles de llave
- Exportar llave (marca como EXPORTED)
- Eliminar llave
- Marcar como KEK / Operacional

#### 1.2.6 Pantalla de Gestión de KEK

**Funcionalidad**: Gestión de Key Encryption Keys

**Operaciones**:

**Generación de KEK**:
- Selección de longitud (16, 24, 32 bytes)
- Generación segura con SecureRandom
- Cálculo de KCV
- Almacenamiento con flag `isKEK = true`
- Estado inicial: ACTIVE

**Estados de KEK**:
- **ACTIVE**: Lista para usar, no exportada
- **EXPORTED**: Ya fue enviada a SubPOS (no reutilizar)
- **INACTIVE**: Reemplazada por nueva KEK

**Flujo de Uso**:
1. Generar nueva KEK en Injector
2. Configurar perfil para usar KEK
3. Al inyectar:
   - Si KEK es ACTIVE → Exportar automáticamente
   - Si KEK es EXPORTED → Mostrar warning + confirmar
   - Todas las llaves se cifran con KEK antes de enviar

### 1.3 ViewModels Clave

#### 1.3.1 KeyInjectionViewModel

**Responsabilidades**:
- Coordinación del proceso completo de inyección
- Gestión de comunicación serial
- Construcción de comandos Futurex
- Validación de llaves
- Manejo de KEK

**Métodos Principales**:

```kotlin
suspend fun startKeyInjection(profile: ProfileEntity)
```
- Inicia el proceso de inyección para un perfil completo
- Establece comunicación
- Itera sobre configuraciones de llave
- Envía cada llave secuencialmente
- Maneja errores y timeouts

```kotlin
suspend fun injectSingleKey(config: KeyConfiguration)
```
- Inyecta una sola llave
- Obtiene llave de repositorio
- Valida integridad
- Construye comando Futurex
- Envía y espera respuesta

```kotlin
private fun validateKeyIntegrity(key: InjectedKeyEntity)
```
- Valida que la llave tenga datos
- Valida que tenga KCV
- Valida longitud según tipo
- Valida formato hexadecimal
- Valida KSN para llaves DUKPT

```kotlin
private fun mapKeyTypeToFuturex(type: String): String
```
Mapeo de tipos:
- PIN → "05"
- MAC → "04"
- TDES/3DES → "01"
- AES → "01"
- DUKPT_TDES → "08"
- DUKPT_AES → "10"
- DATA → "0C"
- Desconocido → "01" (default)

#### 1.3.2 CeremonyViewModel

**Responsabilidades**:
- Gestión del flujo de ceremonia de llaves
- Recolección de componentes
- Combinación mediante XOR
- Cálculo de KCV
- Almacenamiento seguro

**Estado**:
```kotlin
data class CeremonyState(
    val numCustodians: Int = 2,
    val components: List<String> = emptyList(),
    val component: String = "",
    val showComponent: Boolean = false,
    val finalKey: String = "",
    val kcv: String = "",
    val isLoading: Boolean = false,
    val logs: List<String> = emptyList()
)
```

**Métodos Principales**:

```kotlin
fun addComponent()
```
- Valida componente actual
- Agrega a lista de componentes
- Limpia campo de entrada

```kotlin
fun finalizeCeremony()
```
- Combina componentes con XOR
- Genera llave final
- Calcula KCV
- Almacena en KeyStore y BD

#### 1.3.3 ProfileViewModel

**Responsabilidades**:
- Gestión CRUD de perfiles
- Gestión de configuraciones de llaves
- Obtención de llaves disponibles
- Validaciones de perfil

**Estado**:
```kotlin
data class ProfilesScreenState(
    val profiles: List<ProfileEntity> = emptyList(),
    val availableKeys: List<InjectedKeyEntity> = emptyList(),
    val isLoading: Boolean = true,
    val selectedProfile: ProfileEntity? = null,
    val showCreateModal: Boolean = false,
    val showManageKeysModal: Boolean = false,
    val showInjectModal: Boolean = false,
    val formData: ProfileFormData = ProfileFormData()
)
```

### 1.4 Flujo de Datos en Injector

```
[UI - ProfilesScreen]
      ↓ (Usuario selecciona perfil y presiona "Inyectar")
[ProfileViewModel]
      ↓ (Abre modal de inyección)
[KeyInjectionModal]
      ↓ (Usuario presiona "Iniciar Inyección")
[KeyInjectionViewModel.startKeyInjection()]
      ↓
[1. Detener Polling]
      ↓
[2. Inicializar Comunicación]
  ├─ CommunicationSDKManager.getComController()
  ├─ comController.init(BPS_115200, NOPAR, DB_8)
  └─ comController.open()
      ↓
[3. Verificar/Exportar KEK (si configurada)]
  ├─ KEKManager.getActiveKEKEntity()
  ├─ Si estado = ACTIVE → Exportar con comando especial
  └─ Si estado = EXPORTED → Confirmar con usuario
      ↓
[4. Procesar Cada Configuración de Llave]
  Para cada config en profile.keyConfigurations:
      ↓
  [4.1 Obtener Llave]
    └─ InjectedKeyRepository.getKeyByKcv()
      ↓
  [4.2 Validar Integridad]
    └─ validateKeyIntegrity()
      ↓
  [4.3 Cifrar (si hay KEK)]
    └─ TripleDESCrypto.encryptKeyForTransmission()
      ↓
  [4.4 Construir Comando Futurex]
    ├─ Determinar tipo Futurex
    ├─ Formatear longitud (ASCII HEX 3 dígitos)
    ├─ Incluir KSN si es DUKPT
    └─ FuturexMessageFormatter.format("02", fields)
      ↓
  [4.5 Enviar]
    └─ comController.write(data, timeout=1000)
      ↓
  [4.6 Esperar Respuesta]
    ├─ comController.readData(1024, buffer, timeout=10000)
    └─ FuturexMessageParser.nextMessage()
      ↓
  [4.7 Validar Respuesta]
    ├─ Verificar código de respuesta
    ├─ Comparar KCV
    └─ Registrar resultado
      ↓
  [4.8 Pausa]
    └─ delay(500ms)
      ↓
[5. Finalizar]
  ├─ comController.close()
  ├─ Reiniciar polling
  └─ Actualizar UI con resultado
```

---

## 2. APLICACIÓN APP (Dispositivo Receptor)

### 2.1 Descripción General

La aplicación **App** es el componente receptor que:
- Recibe comandos de inyección de llaves
- Escribe llaves en el PED del dispositivo
- Responde a comandos de polling
- Gestiona llaves inyectadas localmente
- Permite entrada manual de llaves maestras

**Rol del Dispositivo**: SUBPOS  
**Paquete**: `com.vigatec.android_injector`  
**Dispositivos Típicos**: Aisino A90 Pro, Newpos NEW9220

### 2.2 Pantallas Principales

#### 2.2.1 Pantalla Principal (MainScreen)

**Archivo**: `MainScreen.kt`  
**ViewModel**: `MainViewModel`

**Funcionalidad**: Dashboard con estado del sistema

**Componentes UI**:

**Sección de Estado de Conexión**:
- Indicador visual de conexión USB
- Detección automática con 4 métodos
- Indicador de cable presente/ausente
- Puerto y baudrate detectados

**Sección de Estado de Comunicación**:
- Estado del protocolo (FUTUREX/LEGACY)
- Estado de listening (DISCONNECTED/LISTENING)
- Mensajes recibidos (contador)
- Última actividad (timestamp)

**Sección de Llaves Inyectadas**:
- Total de llaves en PED
- Últimas 3 llaves inyectadas
- Botón para ver todas

**Logs en Tiempo Real**:
- Panel de logs con scroll automático
- Colores por nivel
- Filtrado por categoría

**Botones de Acción**:
- Iniciar/Detener Listening
- Cambiar Protocolo
- Ver Llaves Inyectadas
- Entrada Manual de Master Key

#### 2.2.2 Pantalla de Llaves Inyectadas

**Archivo**: `InjectedKeysScreen.kt`  
**ViewModel**: `InjectedKeysViewModel`

**Funcionalidad**: Visualización de llaves en el PED

**Información por Llave**:
- Slot
- Tipo de llave
- Algoritmo
- KCV
- Timestamp de inyección
- Estado (SUCCESSFUL/FAILED)

**Acciones**:
- Ver detalles
- Eliminar llave específica
- Eliminar todas las llaves

#### 2.2.3 Pantalla de Entrada Manual de Master Key

**Archivo**: `MasterKeyEntryScreen.kt`  
**ViewModel**: `MasterKeyEntryViewModel`

**Funcionalidad**: Inyección manual de llaves maestras

**Campos**:
- Slot de destino (0-99)
- Valor de llave (hexadecimal)
- KCV (opcional, calculado automáticamente)
- Tipo de llave (MASTER_KEY, WORKING_PIN_KEY, etc.)
- Algoritmo (DES_TRIPLE, AES_128, etc.)

**Proceso**:
1. Usuario ingresa datos
2. Validación de formato hexadecimal
3. Cálculo automático de KCV
4. Confirmación
5. Escritura en PED
6. Registro en base de datos

### 2.3 ViewModels Clave

#### 2.3.1 MainViewModel

**Responsabilidades**:
- Gestión de comunicación serial (modo listening)
- Detección de cable USB
- Parsing de comandos recibidos
- Ejecución de comandos
- Gestión de polling (responder a POLL)

**Métodos Principales**:

```kotlin
fun startListening(baudRate, parity, dataBits)
```
- Inicializa comunicación serial
- Abre puerto
- Inicia loop de lectura continua
- Procesa mensajes recibidos

```kotlin
private suspend fun listeningLoop()
```
- Loop infinito mientras connectionMutex.isLocked
- Lee datos: `comController.readData()`
- Alimenta parser: `messageParser.appendData()`
- Procesa mensajes: `processReceivedMessage()`

```kotlin
private suspend fun processReceivedMessage(message: ParsedMessage)
```
- Identifica tipo de comando
- Delega a handler apropiado:
  - InjectSymmetricKeyCommand → `handleInjectSymmetricKey()`
  - ReadSerialNumberCommand → `handleReadSerialNumber()`
  - WriteSerialNumberCommand → `handleWriteSerialNumber()`
  - DeleteAllKeysCommand → `handleDeleteAllKeys()`
  - DeleteKeyCommand → `handleDeleteKey()`
  - PollCommand → `handlePollRequest()`

```kotlin
private suspend fun handleInjectSymmetricKey(cmd: InjectSymmetricKeyCommand)
```
- Valida comando
- Obtiene PedController
- Determina si llave viene cifrada (encryptionType)
- Escribe llave en PED:
  - Si es claro: `pedController.writeKeyPlain()`
  - Si es cifrado: `pedController.writeKey()` con KEK
- Calcula KCV de verificación
- Registra en base de datos
- Construye respuesta
- Envía respuesta al Master

```kotlin
private suspend fun handlePollRequest()
```
- Detecta mensaje POLL (0100)
- Log: "📥 POLL recibido desde MasterPOS"
- Construye respuesta ACK (0110)
- Envía respuesta
- Log: "📤 Respuesta POLL enviada"

```kotlin
private fun detectCableConnection(): Boolean
```
- Utiliza `UsbCableDetector` con 4 métodos
- Devuelve true si cable está presente
- Actualiza indicador visual

**Detección de Cable USB (4 Métodos)**:

1. **UsbManager API**:
   - Más confiable
   - Detecta dispositivos USB físicos
   - Proporciona VID/PID

2. **Nodos /dev/**:
   - Verifica archivos de dispositivo
   - Comprueba permisos de lectura/escritura
   - Puertos: /dev/ttyUSB0, /dev/ttyACM0, /dev/ttyGS0

3. **Sistema /sys/bus/usb**:
   - Busca dispositivos USB con interfaz serial
   - Filtra por clase CDC (02, 0a)
   - Excluye cámaras y almacenamiento

4. **TTY Class /sys/class/tty/**:
   - Detecta puertos TTY USB
   - Verifica enlaces simbólicos a USB

**Criterio**: Cable presente si ≥2 métodos lo detectan O método 1 lo detecta

#### 2.3.2 InjectedKeysViewModel

**Responsabilidades**:
- Obtención de llaves desde repositorio
- Filtrado y búsqueda
- Eliminación de llaves

**Flow de Datos**:
```kotlin
val injectedKeys: StateFlow<List<InjectedKeyEntity>>
```

### 2.4 Flujo de Datos en App

```
[MainViewModel - Listening Loop]
      ↓
[1. Lectura Continua]
  └─ comController.readData(1024, buffer, 5000ms)
      ↓
[2. Alimentar Parser]
  └─ messageParser.appendData(rawBytes)
      ↓
[3. Extraer Mensaje]
  └─ val msg = messageParser.nextMessage()
      ↓
[4. Identificar Tipo]
  ├─ InjectSymmetricKeyCommand
  ├─ ReadSerialNumberCommand
  ├─ WriteSerialNumberCommand
  ├─ DeleteAllKeysCommand
  ├─ DeleteKeyCommand
  └─ PollCommand
      ↓
[5. Ejecutar Comando]
  
  Ejemplo: InjectSymmetricKeyCommand
      ↓
  [5.1 Parsear Campos]
    ├─ keySlot
    ├─ keyType (Futurex code)
    ├─ encryptionType
    ├─ keyData
    ├─ kcv
    └─ ksn (si DUKPT)
      ↓
  [5.2 Mapear Tipo]
    └─ "05" (Futurex) → KeyType.WORKING_PIN_KEY
      ↓
  [5.3 Obtener PedController]
    └─ KeySDKManager.getPedController()
      ↓
  [5.4 Escribir en PED]
    ├─ Si encryptionType = "00" (claro):
    │   └─ pedController.writeKeyPlain(slot, type, algorithm, keyBytes, kcvBytes)
    │
    └─ Si encryptionType = "01" (cifrado):
        └─ pedController.writeKey(slot, type, algorithm, keyData, ktkSlot, ktkType)
      ↓
  [5.5 Validar Resultado]
    ├─ Leer KCV del PED
    ├─ Comparar con KCV esperado
    └─ Si coincide → Éxito
      ↓
  [5.6 Registrar en BD]
    └─ InjectedKeyRepository.recordKeyInjectionWithData()
      ↓
  [5.7 Construir Respuesta]
    ├─ Código: "00" (éxito) o código de error
    ├─ KCV de la llave
    └─ FuturexMessageFormatter.format("02", [code, kcv])
      ↓
  [5.8 Enviar Respuesta]
    └─ comController.write(response, 1000ms)
```

---

## 3. MÓDULOS COMPARTIDOS

### 3.1 Módulo COMMUNICATION

#### 3.1.1 Estructura

```
communication/
├── base/
│   ├── IComController.kt
│   └── controllers/
│       └── manager/
│           └── ICommunicationManager.kt
├── libraries/
│   ├── aisino/
│   │   ├── AisinoCommunicationManager.kt
│   │   └── wrapper/
│   │       └── AisinoComController.kt
│   ├── newpos/
│   │   ├── NewposCommunicationManager.kt
│   │   └── wrapper/
│   │       └── NewposComController.kt
│   ├── urovo/
│   │   └── UrovoCommunicationManager.kt
│   ├── CommunicationSDKManager.kt
│   └── DummyCommunicationManager.kt
└── polling/
    ├── PollingService.kt
    └── CommLog.kt
```

#### 3.1.2 Interfaz IComController

```kotlin
interface IComController {
    fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int
    
    fun open(): Int
    fun close(): Int
    
    fun write(
        data: ByteArray,
        timeout: Int = 1000
    ): Int
    
    fun readData(
        expectedLen: Int,
        buffer: ByteArray,
        timeout: Int = 5000
    ): Int
}
```

**Códigos de Retorno**:
- `0`: Éxito
- `-1`: Error general
- `-2`: Timeout
- `-3`: Puerto no disponible
- `-4`: Configuración inválida

#### 3.1.3 AisinoCommunicationManager

**Características Especiales**:

**Auto-scan de Puertos**:
```kotlin
suspend fun autoScanPortsAndBauds(): Pair<Int, Int>?
```
- Prueba puertos: 0, 1
- Prueba baudrates: 9600, 115200
- Selecciona combinación que recibe datos
- Almacena en `selectedPort` y `selectedBaud`

**Re-scan Automático**:
```kotlin
suspend fun rescan()
```
- Ejecutado cuando no se reciben datos por 5 lecturas
- Cierra puerto actual
- Ejecuta auto-scan nuevamente
- Reabre con nueva configuración

#### 3.1.4 PollingService

**Funcionalidad**: Servicio de detección de conexión mediante polling

**Métodos**:

```kotlin
suspend fun initialize()
```
- Inicializa parser y formatter
- Prepara controlador de comunicación

```kotlin
fun startMasterPolling(
    onConnectionStatusChanged: (Boolean) -> Unit
)
```
- Inicia polling desde Master
- Envía POLL (0100) cada 2 segundos
- Espera ACK (0110)
- Callback cuando estado cambia

```kotlin
fun stopPolling()
```
- Detiene polling activo
- Limpia estado

**Flujo de Polling**:
```
Master (PollingService):
  └─ Cada 2000ms:
      ├─ Construir mensaje POLL (0100)
      ├─ Enviar: comController.write()
      ├─ Esperar respuesta: readData(timeout=5000ms)
      ├─ Si recibe ACK (0110):
      │   ├─ isConnected = true
      │   └─ onConnectionStatusChanged(true)
      └─ Si timeout:
          ├─ isConnected = false
          └─ onConnectionStatusChanged(false)
```

### 3.2 Módulo MANUFACTURER

#### 3.2.1 Estructura

```
manufacturer/
├── base/
│   ├── models/
│   │   ├── KeyType.kt
│   │   ├── KeyAlgorithm.kt
│   │   ├── PedKeyData.kt
│   │   ├── PedKeyInfo.kt
│   │   ├── PedStatusInfo.kt
│   │   └── PedException.kt
│   └── controllers/
│       ├── ped/
│       │   └── IPedController.kt
│       └── manager/
│           └── IKeyManager.kt
├── libraries/
│   ├── aisino/
│   │   ├── AisinoKeyManager.kt
│   │   └── wrapper/
│   │       └── AisinoPedController.kt
│   ├── newpos/
│   │   ├── NewposKeyManager.kt
│   │   └── wrapper/
│   │       └── NewposPedController.kt
│   ├── urovo/
│   │   ├── UrovoKeyManager.kt
│   │   └── wrapper/
│   │       ├── UrovoPedController.kt
│   │       └── UrovoConstants.kt
│   └── KeySDKManager.kt
└── PedApi.java (SDK nativo)
```

#### 3.2.2 Interfaz IPedController

```kotlin
interface IPedController {
    // Lifecycle
    suspend fun initializePed(application: Application): Boolean
    fun releasePed()
    
    // Status
    suspend fun getStatus(): PedStatusInfo
    suspend fun getConfig(): PedConfigInfo
    
    // Key Management
    suspend fun writeKey(
        keyIndex: Int,
        keyType: KeyType,
        keyAlgorithm: KeyAlgorithm,
        keyData: PedKeyData,
        transportKeyIndex: Int? = null,
        transportKeyType: KeyType? = null
    ): Boolean
    
    suspend fun writeKeyPlain(
        keyIndex: Int,
        keyType: KeyType,
        keyAlgorithm: KeyAlgorithm,
        keyBytes: ByteArray,
        kcvBytes: ByteArray?
    ): Boolean
    
    suspend fun writeDukptInitialKeyEncrypted(
        groupIndex: Int,
        keyAlgorithm: KeyAlgorithm,
        encryptedIpek: ByteArray,
        initialKsn: ByteArray,
        transportKeyIndex: Int,
        keyChecksum: String?
    ): Boolean
    
    suspend fun deleteKey(
        keyIndex: Int,
        keyType: KeyType
    ): Boolean
    
    suspend fun deleteAllKeys(): Boolean
    
    suspend fun isKeyPresent(
        keyIndex: Int,
        keyType: KeyType
    ): Boolean
    
    suspend fun getKeyInfo(
        keyIndex: Int,
        keyType: KeyType
    ): PedKeyInfo?
}
```

#### 3.2.3 Constantes de Urovo

**Tipos de Llave**:
```kotlin
object KeyType {
    const val MAIN_KEY = 0
    const val MAC_KEY = 1
    const val PIN_KEY = 2
    const val TD_KEY = 3
}
```

**Algoritmos**:
```kotlin
object KeyAlgorithm {
    const val DES = 0
    const val SM4 = 1
    const val AES = 2
}
```

**Modos de Cifrado**:
```kotlin
object Algorithm {
    const val DES_ECB = 1
    const val DES_CBC = 2
    const val SM4 = 3
    const val AES_ECB = 7
    const val AES_CBC = 8
}
```

**DUKPT**:
```kotlin
object DukptKeyTypeParam {
    const val PIN = 0x01
    const val MAC = 0x02
    const val TRACK_DATA = 0x03
    const val MAC_ALT = 0x04
}

object DukptKeySetNum {
    const val TDK_SET = 0x01
    const val PEK_SET = 0x03
    const val MAC_SET = 0x04
}
```

### 3.3 Módulo FORMAT

#### 3.3.1 Estructura

```
format/
├── base/
│   ├── IMessageFormatter.kt
│   └── IMessageParser.kt
├── FuturexFormat.kt
├── FuturexMessageFormatter.kt
├── FuturexMessageParser.kt
├── LegacyMessageFormatter.kt
├── LegacyMessageParser.kt
└── ParsedMessage.kt
```

#### 3.3.2 ParsedMessage (Sellado)

```kotlin
sealed class ParsedMessage {
    // Futurex Commands
    data class InjectSymmetricKeyCommand(...)
    data class InjectSymmetricKeyResponse(...)
    data class ReadSerialNumberCommand(...)
    data class ReadSerialNumberResponse(...)
    data class WriteSerialNumberCommand(...)
    data class WriteSerialNumberResponse(...)
    data class DeleteAllKeysCommand(...)
    data class DeleteAllKeysResponse(...)
    data class DeleteKeyCommand(...)
    data class DeleteKeyResponse(...)
    
    // Legacy Commands
    data class PollCommand(...)
    data class PollResponse(...)
    
    // Unknown
    data class UnknownMessage(...)
}
```

#### 3.3.3 FuturexMessageFormatter

**Método Principal**:
```kotlin
fun format(command: String, fields: List<String>): ByteArray
```

**Proceso**:
1. Construir payload: command + fields
2. Agregar STX (0x02)
3. Agregar ETX (0x03)
4. Calcular LRC: XOR de todos los bytes
5. Agregar LRC al final
6. Retornar ByteArray completo

**Ejemplo**:
```
Entrada:
  command = "02"
  fields = ["01", "03", "00", "05", "00", "AABB", ...]

Salida:
  [0x02][30 32 30 31 30 33...][0x03][LRC]
   STX    A S C I I           ETX   LRC
```

### 3.4 Módulo PERSISTENCE

#### 3.4.1 Estructura

```
persistence/
├── common/
│   └── Converters.kt
├── dao/
│   ├── InjectedKeyDao.kt
│   ├── KeyDao.kt
│   └── ProfileDao.kt
├── entities/
│   ├── InjectedKeyEntity.kt
│   ├── KeyEntity.kt
│   └── ProfileEntity.kt
├── repository/
│   ├── InjectedKeyRepository.kt
│   ├── KeyRepository.kt
│   └── ProfileRepository.kt
├── AppDatabase.kt
├── DatabaseModule.kt
└── DatabaseProvider.kt
```

#### 3.4.2 InjectedKeyEntity

```kotlin
@Entity(
    tableName = "injected_keys",
    indices = [
        Index(value = ["keySlot", "keyType"], unique = true),
        Index(value = ["kcv"], unique = false)
    ]
)
data class InjectedKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val keySlot: Int,
    val keyType: String,
    val keyAlgorithm: String,
    val kcv: String,
    val keyData: String = "",
    
    val injectionTimestamp: Long = System.currentTimeMillis(),
    val status: String,
    
    val isKEK: Boolean = false,
    val customName: String = ""
)
```

**Características**:
- **Índice único**: (keySlot, keyType) - Solo una llave por slot/tipo
- **Índice no único**: kcv - Permite misma llave en diferentes slots
- **keyData**: Datos completos en hexadecimal
- **isKEK**: Flag para identificar KEK
- **customName**: Nombre personalizado opcional

#### 3.4.3 ProfileEntity

```kotlin
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val applicationType: String,
    val keyConfigurations: List<KeyConfiguration>,
    
    val useKEK: Boolean = false,
    val selectedKEKKcv: String = ""
)

data class KeyConfiguration(
    val id: Long,
    val usage: String,
    val keyType: String,
    val slot: String,
    val selectedKey: String,
    val injectionMethod: String,
    val ksn: String = ""
)
```

**TypeConverter para List<KeyConfiguration>**:
- Serialización a JSON para almacenamiento
- Deserialización al leer

#### 3.4.4 Repositorios

**InjectedKeyRepository**:
```kotlin
// Inserción con datos completos
suspend fun recordKeyInjectionWithData(
    keySlot: Int,
    keyType: String,
    keyAlgorithm: String,
    kcv: String,
    keyData: String,
    status: String = "SUCCESSFUL",
    isKEK: Boolean = false,
    customName: String = ""
)

// Obtención
fun getAllInjectedKeys(): Flow<List<InjectedKeyEntity>>
suspend fun getKeyByKcv(kcv: String): InjectedKeyEntity?
suspend fun getKeyBySlotAndType(slot: Int, type: String): InjectedKeyEntity?

// Actualización
suspend fun updateKeyStatus(kcv: String, status: String)

// Eliminación
suspend fun deleteKey(keyId: Long)
suspend fun deleteAllKeys()
```

**ProfileRepository**:
```kotlin
fun getAllProfiles(): Flow<List<ProfileEntity>>
suspend fun getProfileById(id: Long): ProfileEntity?
suspend fun insertProfile(profile: ProfileEntity): Long
suspend fun updateProfile(profile: ProfileEntity)
suspend fun deleteProfile(id: Long)
```

### 3.5 Módulo CONFIG

#### 3.5.1 SystemConfig

```kotlin
object SystemConfig {
    var managerSelected: EnumManufacturer = 
        getManufacturerFromString(Build.MODEL)
    
    var keyCombinationMethod: KeyCombinationMethod = 
        KeyCombinationMethod.XOR_PLACEHOLDER
    
    var commProtocolSelected: CommProtocol = CommProtocol.FUTUREX
    
    @Volatile 
    var deviceRole: DeviceRole = DeviceRole.SUBPOS
    
    var aisinoCandidatePorts: List<Int> = listOf(0, 1)
    var aisinoCandidateBauds: List<Int> = listOf(9600, 115200)
    
    fun isMaster(): Boolean = deviceRole == DeviceRole.MASTER
    fun isSubPOS(): Boolean = deviceRole == DeviceRole.SUBPOS
}
```

#### 3.5.2 Enums

**CommProtocol**:
```kotlin
enum class CommProtocol {
    LEGACY,   // Protocolo con separador '|'
    FUTUREX   // Protocolo del manual Futurex
}
```

**DeviceRole**:
```kotlin
enum class DeviceRole { 
    MASTER,   // Dispositivo inyector
    SUBPOS    // Dispositivo receptor
}
```

**EnumManufacturer**:
```kotlin
enum class EnumManufacturer {
    NEWPOS,
    AISINO,
    UROVO,
    INGENICO,
    PAX,
    UNKNOWN
}
```

### 3.6 Módulo UTILS

#### 3.6.1 Estructura

```
utils/
├── CryptoUtils.kt
├── TripleDESCrypto.kt
├── KcvCalculator.kt
├── KeyStoreManager.kt
├── FormatUtils.kt
└── enums/
    └── Role.kt
```

#### 3.6.2 CryptoUtils

**Funciones Principales**:
```kotlin
// Cifrado/Descifrado
fun encryptTripleDES(key: ByteArray, data: ByteArray): ByteArray
fun decryptTripleDES(key: ByteArray, data: ByteArray): ByteArray

// Conversiones
fun hexStringToByteArray(hex: String): ByteArray
fun byteArrayToHexString(bytes: ByteArray): String

// Generación
fun generateRandomKey(length: Int): ByteArray
fun generateRandomHexKey(length: Int): String
```

#### 3.6.3 TripleDESCrypto

```kotlin
object TripleDESCrypto {
    fun encryptKeyForTransmission(
        keyData: String,
        kekData: String,
        keyKcv: String
    ): String {
        // Cifra llave con KEK usando 3DES ECB
        // Retorna datos cifrados en hexadecimal
    }
    
    fun decryptKey(
        encryptedKeyData: String,
        kekData: String
    ): String {
        // Descifra llave con KEK
        // Retorna datos en claro
    }
}
```

#### 3.6.4 KcvCalculator

```kotlin
object KcvCalculator {
    fun calculateKcv(keyBytes: ByteArray): String {
        // Cifra bloque de ceros con la llave
        // Toma primeros 3 bytes del resultado
        // Retorna en hexadecimal
    }
    
    fun xorByteArrays(a: ByteArray, b: ByteArray): ByteArray {
        // Combina arrays mediante XOR
        // Para ceremonia de llaves
    }
}
```

#### 3.6.5 KeyStoreManager

```kotlin
object KeyStoreManager {
    fun storeMasterKey(alias: String, keyBytes: ByteArray)
    fun retrieveMasterKey(alias: String): ByteArray?
    fun deleteKey(alias: String)
    fun encryptData(alias: String, data: ByteArray): ByteArray
    fun decryptData(alias: String, encryptedData: ByteArray): ByteArray
}
```

**Características**:
- Usa Android KeyStore para seguridad hardware
- Almacenamiento AES/GCM/NoPadding
- Protección a nivel sistema operativo

---

## 4. FLUJO DE INTEGRACIÓN ENTRE MÓDULOS

### 4.1 Inicialización del Sistema

```
[Application.onCreate()]
      ↓
[Hilt DI - Inyección de Dependencias]
  ├─ DatabaseModule → AppDatabase
  ├─ Repositories
  └─ ViewModels
      ↓
[SplashViewModel.initializeSystem()]
      ↓
[1. Inicializar SDK de Comunicación]
  └─ CommunicationSDKManager.initialize(application)
      └─ AisinoCommunicationManager.initialize()
          └─ SDK nativo inicializado
      ↓
[2. Inicializar SDK de Llaves]
  └─ KeySDKManager.initialize(application)
      └─ AisinoKeyManager.initialize()
          └─ KeySDKManager.connect()
              └─ PedController inicializado
      ↓
[3. Configurar Rol del Dispositivo]
  ├─ INJECTOR → SystemConfig.deviceRole = MASTER
  └─ APP → SystemConfig.deviceRole = SUBPOS
      ↓
[4. Configurar Protocolo]
  └─ SystemConfig.commProtocolSelected = FUTUREX
      ↓
[5. Inicializar Polling (solo si MASTER)]
  └─ PollingService.initialize()
      ↓
[✓ Sistema Listo]
```

### 4.2 Integración Completa: Ejemplo de Inyección

```
[INJECTOR - UI]
  Usuario selecciona perfil "Transaccional"
      ↓
[ProfileViewModel]
  showInjectModal = true
      ↓
[KeyInjectionViewModel.startKeyInjection(profile)]
      ↓
[1. Módulo CONFIG]
  SystemConfig.isMaster() → true
  SystemConfig.commProtocolSelected → FUTUREX
      ↓
[2. Módulo COMMUNICATION - Polling]
  PollingService.stopPolling()
  "Deteniendo polling antes de inyección"
      ↓
[3. Módulo COMMUNICATION - Serial]
  CommunicationSDKManager.getComController()
    → AisinoComController
  comController.init(BPS_115200, NOPAR, DB_8)
  comController.open() → 0 (éxito)
      ↓
[4. Módulo PERSISTENCE]
  InjectedKeyRepository.getKeyByKcv("AABBCC")
    → InjectedKeyEntity
      ↓
[5. Módulo UTILS - Validación]
  Validar keyData no vacío
  Validar kcv presente
  Validar longitud según tipo
      ↓
[6. Módulo UTILS - Cifrado (si KEK)]
  KEKManager.getActiveKEKEntity()
  TripleDESCrypto.encryptKeyForTransmission(
    keyData, kekData, kcv
  ) → encryptedKey
      ↓
[7. Módulo FORMAT - Construcción]
  FuturexMessageFormatter.format("02", [
    "01", slot, ktkSlot, keyType, encType,
    kcv, ktkKcv, ksn, keyLength, keyData
  ]) → ByteArray con LRC
      ↓
[8. Módulo COMMUNICATION - Envío]
  comController.write(message, 1000)
    → bytes escritos
  "TX: [bytes en hex]"
      ↓
[USB SERIAL FÍSICO]
      ↓
[APP - Módulo COMMUNICATION - Recepción]
  comController.readData(1024, buffer, 5000)
    → bytesRead
  "RX: [bytes en hex]"
      ↓
[9. Módulo FORMAT - Parsing]
  FuturexMessageParser.appendData(rawBytes)
  messageParser.nextMessage()
    → InjectSymmetricKeyCommand
      ↓
[10. APP - MainViewModel]
  processReceivedMessage(command)
  handleInjectSymmetricKey(command)
      ↓
[11. Módulo MANUFACTURER]
  KeySDKManager.getPedController()
    → AisinoPedController
  pedController.writeKeyPlain(
    slot, keyType, algorithm, keyBytes, kcvBytes
  ) → true (éxito)
      ↓
[12. Módulo PERSISTENCE]
  InjectedKeyRepository.recordKeyInjectionWithData(
    slot, type, algorithm, kcv, keyData, "SUCCESSFUL"
  )
      ↓
[13. Módulo FORMAT - Respuesta]
  FuturexMessageFormatter.format("02", ["00", kcv])
    → ByteArray respuesta
      ↓
[14. Módulo COMMUNICATION - Envío Respuesta]
  comController.write(response, 1000)
      ↓
[USB SERIAL]
      ↓
[INJECTOR - Recepción]
  comController.readData()
  FuturexMessageParser.nextMessage()
    → InjectSymmetricKeyResponse
  Validar responseCode = "00"
  ✓ Inyección exitosa
      ↓
[15. Finalización]
  comController.close()
  PollingService.restartPolling()
  UI actualizada con éxito
```

---

## 5. CONCLUSIÓN

El sistema está compuesto por:

**2 Aplicaciones Principales**:
- **Injector**: Generación y envío de llaves
- **App**: Recepción y almacenamiento en PED

**6 Módulos Compartidos**:
- **communication**: Comunicación serial USB
- **manufacturer**: Control del PED
- **format**: Protocolos de mensajería
- **persistence**: Base de datos Room
- **config**: Configuración centralizada
- **utils**: Herramientas criptográficas

Esta arquitectura modular facilita:
✅ Mantenimiento independiente de componentes  
✅ Pruebas unitarias aisladas  
✅ Extensión a nuevos fabricantes  
✅ Reutilización de código  
✅ Separación de responsabilidades  

---

**Siguiente Documento**: [Parte 3: Tipos de Llaves y Criptografía](DOCUMENTACION_03_TIPOS_LLAVES_CRIPTOGRAFIA.md)


