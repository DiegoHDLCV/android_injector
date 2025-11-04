# Documentación del Sistema de Inyección de Llaves Criptográficas

## Parte 1: Introducción y Arquitectura General

### Versión: 1.0
### Fecha: Octubre 2025

---

## 1. INTRODUCCIÓN

### 1.1 ¿Qué es el Sistema de Inyección de Llaves?

El Sistema de Inyección de Llaves Criptográficas es una solución empresarial desarrollada para dispositivos Android POS (Point of Sale) que permite:

- **Generar llaves criptográficas** de forma segura mediante ceremonia de llaves
- **Inyectar llaves** en módulos de seguridad PED (Pin Entry Device) de dispositivos terminales
- **Gestionar perfiles** de configuración de llaves para diferentes aplicaciones
- **Transferir llaves** de forma segura entre dispositivos mediante comunicación USB serial

### 1.2 Propósito del Sistema

El sistema está diseñado para resolver la necesidad de:

1. **Inicialización Segura de Terminales POS**: Cargar llaves criptográficas en dispositivos POS nuevos o resetear dispositivos existentes
2. **Distribución Centralizada**: Un dispositivo maestro puede inyectar llaves en múltiples dispositivos subordinados
3. **Gestión de Llaves por Perfil**: Diferentes configuraciones de llaves según el tipo de aplicación (transacciones, PIN, MAC, etc.)
4. **Trazabilidad**: Registro completo de todas las operaciones de inyección

### 1.3 Casos de Uso Principales

#### Caso de Uso 1: Ceremonia de Llaves
**Actor**: Administrador de Seguridad  
**Descripción**: Generación de llaves criptográficas mediante el método de división de secretos

**Flujo**:
1. El administrador inicia la ceremonia de llaves
2. Define el número de custodios (componentes)
3. Cada custodio ingresa su componente secreto
4. El sistema combina los componentes mediante XOR
5. Se genera la llave final y se almacena de forma segura
6. Se calcula y registra el KCV (Key Check Value)

#### Caso de Uso 2: Inyección de Llaves desde Perfil
**Actor**: Técnico de Inicialización  
**Descripción**: Inyección de un conjunto de llaves en un dispositivo terminal

**Flujo**:
1. El técnico conecta el dispositivo maestro (Injector) con el dispositivo terminal (SubPOS) mediante cable USB
2. Selecciona un perfil de configuración
3. Inicia el proceso de inyección
4. El sistema transfiere cada llave del perfil al PED del dispositivo terminal
5. Valida la inyección mediante KCV
6. Registra el resultado en la base de datos

#### Caso de Uso 3: Inyección Masiva en Múltiples Dispositivos
**Actor**: Técnico de Producción  
**Descripción**: Inyectar el mismo perfil de llaves en múltiples dispositivos

**Flujo**:
1. El técnico prepara el dispositivo maestro con el perfil deseado
2. Conecta el primer dispositivo terminal
3. El sistema detecta la conexión automáticamente
4. Inyecta las llaves del perfil
5. El técnico desconecta el primer dispositivo y conecta el siguiente
6. El sistema repite el proceso automáticamente

---

## 2. ARQUITECTURA DEL SISTEMA

### 2.1 Arquitectura de Aplicaciones

El sistema consta de **dos aplicaciones principales** que trabajan de forma coordinada:

#### 2.1.1 Aplicación INJECTOR (Dispositivo Maestro)
- **Nombre del paquete**: `com.vigatec.injector`
- **Rol del dispositivo**: MASTER
- **Funciones principales**:
  - Generación de llaves mediante ceremonia
  - Gestión de perfiles de inyección
  - Envío de comandos de inyección
  - Control de autenticación de usuarios
  - Gestión de KEK (Key Encryption Key)

#### 2.1.2 Aplicación APP (Dispositivo Receptor)
- **Nombre del paquete**: `com.vigatec.android_injector`
- **Rol del dispositivo**: SUBPOS
- **Funciones principales**:
  - Recepción de comandos de inyección
  - Escritura de llaves en el PED
  - Respuesta a comandos de polling
  - Gestión de llaves inyectadas
  - Visualización de estado de llaves

### 2.2 Diagrama de Arquitectura General

```
┌─────────────────────────────────────────────────────────────┐
│                    APLICACIÓN INJECTOR                       │
│                  (Dispositivo Maestro - MASTER)              │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  [UI Layer - Jetpack Compose]                                │
│    ├─ LoginScreen                                            │
│    ├─ SplashScreen                                           │
│    ├─ CeremonyScreen (Generación de llaves)                  │
│    ├─ ProfilesScreen (Gestión de perfiles)                   │
│    ├─ InjectedKeysScreen (Visualización de llaves)           │
│    └─ KeyInjectionModal (Proceso de inyección)               │
│                                                               │
│  [ViewModel Layer]                                            │
│    ├─ LoginViewModel                                         │
│    ├─ SplashViewModel                                        │
│    ├─ CeremonyViewModel                                      │
│    ├─ ProfileViewModel                                       │
│    ├─ KeyInjectionViewModel ⭐                               │
│    └─ KEKManager (Gestión de llaves de cifrado)              │
│                                                               │
│  [Business Logic]                                             │
│    ├─ PollingService (Detección de conexión)                 │
│    ├─ FuturexMessageFormatter (Construcción de mensajes)     │
│    └─ KeyValidation (Validación de llaves)                   │
│                                                               │
└───────────────────────┬─────────────────────────────────────┘
                        │
                  [USB Serial]
                  Protocolo Futurex
                        │
┌───────────────────────┴─────────────────────────────────────┐
│                      APLICACIÓN APP                          │
│                  (Dispositivo Receptor - SUBPOS)             │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  [UI Layer - Jetpack Compose]                                │
│    ├─ SplashScreen                                           │
│    ├─ MainScreen (Estado de conexión)                        │
│    ├─ InjectedKeysScreen (Llaves recibidas)                  │
│    └─ MasterKeyEntryScreen (Entrada manual de llaves)        │
│                                                               │
│  [ViewModel Layer]                                            │
│    ├─ SplashViewModel                                        │
│    ├─ MainViewModel ⭐                                       │
│    ├─ InjectedKeysViewModel                                  │
│    └─ MasterKeyEntryViewModel                                │
│                                                               │
│  [Business Logic]                                             │
│    ├─ FuturexMessageParser (Parsing de comandos)             │
│    ├─ CommandHandler (Ejecución de comandos)                 │
│    ├─ UsbCableDetector (Detección de cable USB)              │
│    └─ KeyInjector (Escritura en PED)                         │
│                                                               │
└─────────────────────────────────────────────────────────────┘

[Módulos Compartidos]
┌─────────────────────────────────────────────────────────────┐
│                                                               │
│  [communication] - Comunicación Serial                        │
│    ├─ CommunicationSDKManager                                │
│    ├─ AisinoCommunicationManager                             │
│    ├─ NewposCommunicationManager                             │
│    └─ UrovoCommunicationManager                              │
│                                                               │
│  [manufacturer] - Control de PED                              │
│    ├─ KeySDKManager                                          │
│    ├─ AisinoPedController                                    │
│    ├─ NewposPedController                                    │
│    └─ UrovoPedController                                     │
│                                                               │
│  [format] - Protocolos de Mensajería                          │
│    ├─ FuturexMessageFormatter                                │
│    ├─ FuturexMessageParser                                   │
│    ├─ LegacyMessageFormatter                                 │
│    └─ LegacyMessageParser                                    │
│                                                               │
│  [persistence] - Base de Datos                                │
│    ├─ AppDatabase (Room)                                     │
│    ├─ ProfileEntity                                          │
│    ├─ InjectedKeyEntity                                      │
│    └─ Repositories                                           │
│                                                               │
│  [config] - Configuración del Sistema                         │
│    ├─ SystemConfig                                           │
│    ├─ DeviceRole (MASTER/SUBPOS)                             │
│    ├─ CommProtocol (FUTUREX/LEGACY)                          │
│    └─ EnumManufacturer                                       │
│                                                               │
│  [utils] - Utilidades                                         │
│    ├─ CryptoUtils (Funciones criptográficas)                 │
│    ├─ TripleDESCrypto (Cifrado 3DES)                         │
│    ├─ KcvCalculator (Cálculo de KCV)                         │
│    └─ KeyStoreManager (Android KeyStore)                     │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 Arquitectura de Módulos

El proyecto está organizado en módulos independientes para facilitar el mantenimiento y la reutilización:

#### Módulos de Aplicación

1. **app** - Aplicación receptora (SubPOS)
2. **injector** - Aplicación inyectora (MasterPOS)
3. **dev_injector** - Aplicación de desarrollo y testing

#### Módulos Compartidos

4. **communication** - Gestión de comunicación serial USB
5. **manufacturer** - Abstracción de SDKs de fabricantes y control de PED
6. **format** - Formateo y parsing de protocolos de mensajería
7. **persistence** - Capa de persistencia con Room Database
8. **config** - Configuración centralizada del sistema
9. **utils** - Utilidades criptográficas y herramientas comunes

### 2.4 Flujo de Datos

#### 2.4.1 Flujo de Inyección de Llaves

```
[INJECTOR - Aplicación Maestro]
    │
    ├─1. Usuario selecciona perfil con configuración de llaves
    │
    ├─2. KeyInjectionViewModel procesa configuración
    │    └─ Obtiene llaves desde InjectedKeyRepository
    │
    ├─3. Construye comandos Futurex
    │    └─ FuturexMessageFormatter.format()
    │
    ├─4. Envía datos por puerto serial
    │    └─ CommunicationSDKManager.getComController().write()
    │
    ▼
[USB SERIAL - Cable físico]
    ▼
[APP - Aplicación Receptora]
    │
    ├─5. MainViewModel escucha puerto serial
    │    └─ comController.readData()
    │
    ├─6. Parsea comando recibido
    │    └─ FuturexMessageParser.nextMessage()
    │
    ├─7. Identifica tipo de comando
    │    └─ InjectSymmetricKeyCommand
    │
    ├─8. Ejecuta comando
    │    ├─ Valida integridad de llave
    │    ├─ KeySDKManager.getPedController()
    │    ├─ pedController.writeKey() / writeKeyPlain()
    │    └─ Escribe llave en PED físico
    │
    ├─9. Registra en base de datos
    │    └─ InjectedKeyRepository.recordKeyInjectionWithData()
    │
    ├─10. Construye respuesta
    │    └─ FuturexMessageFormatter con código de éxito/error
    │
    └─11. Envía respuesta al Injector
         └─ comController.write()
```

#### 2.4.2 Flujo de Polling (Detección de Conexión)

```
[INJECTOR - PollingService]
    │
    ├─ Cada 2 segundos:
    │   └─ Envía mensaje POLL (0100)
    │   └─ Espera respuesta ACK (0110)
    │
    ▼
[APP - MainViewModel]
    │
    ├─ Escucha continuamente
    ├─ Detecta mensaje POLL (0100)
    ├─ Responde inmediatamente con ACK (0110)
    │
    ▼
[INJECTOR]
    │
    ├─ Recibe ACK (0110)
    ├─ Actualiza estado: isConnected = true
    └─ Callback: onConnectionStatusChanged(true)
```

### 2.5 Patrones de Arquitectura Utilizados

#### 2.5.1 MVVM (Model-View-ViewModel)
- **View**: Composables de Jetpack Compose (UI declarativa)
- **ViewModel**: Gestión de estado y lógica de presentación
- **Model**: Repositorios y entidades de datos

#### 2.5.2 Repository Pattern
- Abstracción de la fuente de datos
- Separación entre lógica de negocio y acceso a datos
- Ejemplos:
  - `InjectedKeyRepository`
  - `ProfileRepository`
  - `UserRepository`

#### 2.5.3 Dependency Injection (Hilt)
- Inyección de dependencias en ViewModels
- Provisión de instancias singleton
- Módulos de configuración:
  - `AppModule`
  - `DatabaseModule`

#### 2.5.4 Strategy Pattern
- Selección dinámica de implementaciones según fabricante
- `KeySDKManager` selecciona controlador PED apropiado
- `CommunicationSDKManager` selecciona controlador serial apropiado

#### 2.5.5 Factory Pattern
- `FuturexMessageParser.create()`
- Creación de mensajes según tipo de comando

#### 2.5.6 Observer Pattern
- `Flow` de Kotlin Coroutines para streams reactivos
- StateFlow para estado observable en ViewModels
- Ejemplo: `connectionStatus: StateFlow<ConnectionStatus>`

---

## 3. COMPONENTES PRINCIPALES

### 3.1 Módulo COMMUNICATION

**Responsabilidad**: Gestión de comunicación serial USB entre dispositivos

#### 3.1.1 Componentes Clave

**CommunicationSDKManager**
- Singleton que delega a managers específicos de cada fabricante
- Selecciona automáticamente el manager según `SystemConfig.managerSelected`
- Métodos principales:
  - `initialize(application)` - Inicializa SDK nativo
  - `getComController()` - Obtiene controlador de comunicación
  - `rescanIfSupported()` - Re-escanea puertos (Aisino)

**IComController (Interfaz)**
- Abstracción de operaciones de comunicación serial
- Métodos:
  - `init(baudRate, parity, dataBits)` - Configura puerto
  - `open()` - Abre conexión serial
  - `close()` - Cierra conexión
  - `write(data, timeout)` - Envía datos
  - `readData(expectedLen, buffer, timeout)` - Lee datos

**Implementaciones por Fabricante**
- `AisinoComController` - Para dispositivos Aisino/Vanstone
- `NewposComController` - Para dispositivos Newpos
- `UrovoComController` - Para dispositivos Urovo

#### 3.1.2 Características Especiales

**Auto-scan de Puertos (Solo Aisino)**
- Prueba automáticamente diferentes puertos (0, 1)
- Prueba diferentes baudrates (9600, 115200)
- Selecciona la combinación que recibe datos
- Tiempo típico: < 10 segundos

**Re-scan Automático**
- Se ejecuta cuando no se reciben datos por 5 lecturas consecutivas
- Cierra y reabre el puerto con nueva configuración
- Evita quedarse bloqueado en configuración incorrecta

### 3.2 Módulo MANUFACTURER

**Responsabilidad**: Abstracción de SDKs nativos de fabricantes y control del PED

#### 3.2.1 Componentes Clave

**KeySDKManager**
- Singleton que delega a managers específicos de cada fabricante
- Selecciona según `SystemConfig.managerSelected`
- Métodos:
  - `initialize(application)` - Inicializa SDK de llaves
  - `connect()` - Conecta con PED
  - `getPedController()` - Obtiene controlador PED

**IPedController (Interfaz)**
- Abstracción de operaciones con el PED
- Métodos principales:
  - `initializePed(application)` - Inicializa PED
  - `writeKey()` - Escribe llave cifrada con KEK
  - `writeKeyPlain()` - Escribe llave en claro
  - `writeDukptInitialKeyEncrypted()` - Escribe IPEK DUKPT
  - `deleteKey()` - Elimina llave específica
  - `deleteAllKeys()` - Elimina todas las llaves
  - `getKeyInfo()` - Obtiene información de llave
  - `getStatus()` - Obtiene estado del PED

**Implementaciones**
- `AisinoPedController` - Usa SDK de Vanstone
- `NewposPedController` - Usa SDK de Newpos
- `UrovoPedController` - Usa SDK de Urovo

#### 3.2.2 Tipos de Operaciones

**Inyección de Llaves en Claro**
- Para Master Keys (llaves maestras)
- `writeKeyPlain(slot, keyType, algorithm, keyBytes, kcvBytes)`

**Inyección de Llaves Cifradas**
- Para Working Keys cifradas con Master Key
- Para llaves cifradas con KEK
- `writeKey(slot, keyType, algorithm, keyData, transportKeySlot, transportKeyType)`

**Inyección de Llaves DUKPT**
- Para IPEK (Initial Pin Encryption Key) DUKPT
- `writeDukptInitialKeyEncrypted(groupIndex, algorithm, encryptedIpek, ksn, ktkIndex)`

### 3.3 Módulo FORMAT

**Responsabilidad**: Formateo y parsing de protocolos de comunicación

#### 3.3.1 Protocolo Futurex

**Características**:
- Protocolo principal para inyección de llaves
- Basado en comandos ASCII con verificación LRC
- Estructura: `<STX> + PAYLOAD + <ETX> + <LRC>`

**FuturexMessageFormatter**
- Construye mensajes según especificación Futurex
- Calcula LRC (XOR de todos los bytes)
- Formato de campos específicos:
  - Longitud de llave en formato ASCII HEX (3 dígitos)
  - Ejemplo: 16 bytes → "010", 32 bytes → "020"

**FuturexMessageParser**
- Parsea mensajes recibidos
- Valida LRC
- Identifica tipo de mensaje
- Extrae campos específicos

**Tipos de Mensaje Soportados**:
- **Comando 02**: Inyección de llave simétrica
- **Comando 03**: Lectura de número de serie
- **Comando 04**: Escritura de número de serie
- **Comando 05**: Eliminación total de llaves
- **Comando 06**: Eliminación de llave específica

#### 3.3.2 Protocolo Legacy

**Características**:
- Protocolo simple para polling y detección
- Usa separador `|` entre campos
- Estructura: `<STX> + COMMAND(4) + "|" + DATA + <ETX> + <LRC>`

**Mensajes**:
- **0100 (POLL)**: Mensaje de ping desde Master
- **0110 (ACK)**: Respuesta de SubPOS confirmando presencia

**LegacyMessageFormatter**
- Construye mensajes con separador `|`

**LegacyMessageParser**
- Parsea mensajes con separador `|`

---

## 4. SEGURIDAD Y CRIPTOGRAFÍA

### 4.1 Almacenamiento Seguro

#### 4.1.1 Android KeyStore
- Utilizado para almacenamiento de llaves maestras
- Protección a nivel hardware (si disponible)
- `KeyStoreManager.storeMasterKey(alias, keyBytes)`

#### 4.1.2 Base de Datos Cifrada
- Room Database con cifrado opcional
- Almacenamiento de llaves operacionales
- Registro de auditoría de inyecciones

### 4.2 Cifrado de Llaves

#### 4.2.1 Modo Claro (Sin KEK)
- Llaves enviadas sin cifrado adicional
- Solo para entornos de desarrollo o confianza total
- Encriptación tipo: "00"

#### 4.2.2 Modo Cifrado (Con KEK)
- Llaves cifradas con KEK antes de enviar
- KEK debe estar pre-cargada en SubPOS
- Encriptación tipo: "01"
- Algoritmo: Triple DES en modo ECB

#### 4.2.3 Ceremonia de Llaves
- Método de división de secretos
- Múltiples custodios (2-5 componentes)
- Combinación mediante XOR
- Previene conocimiento total por un solo individuo

### 4.3 Validación de Integridad

#### 4.3.1 KCV (Key Check Value)
- Checksum de 3 bytes derivado de la llave
- Permite validar llave sin exponerla
- Calculado mediante cifrado de bloque cero

#### 4.3.2 LRC (Longitudinal Redundancy Check)
- Verificación de integridad de mensajes
- XOR de todos los bytes del mensaje
- Detecta corrupción durante transmisión

---

## 5. CONFIGURACIÓN DEL SISTEMA

### 5.1 SystemConfig

**Archivo**: `config/src/main/java/com/example/config/SystemConfig.kt`

**Parámetros Principales**:

```kotlin
// Fabricante del dispositivo (detectado automáticamente)
var managerSelected: EnumManufacturer

// Protocolo de comunicación
var commProtocolSelected: CommProtocol
  // FUTUREX - Para inyección de llaves
  // LEGACY - Para polling/detección

// Rol del dispositivo
var deviceRole: DeviceRole
  // MASTER - Dispositivo inyector
  // SUBPOS - Dispositivo receptor

// Configuración de auto-scan (Aisino)
var aisinoCandidatePorts: List<Int> = listOf(0, 1)
var aisinoCandidateBauds: List<Int> = listOf(9600, 115200)
```

### 5.2 Detección Automática de Fabricante

El sistema detecta automáticamente el fabricante basándose en `Build.MODEL`:

- **Aisino/Vanstone**: "Vanstone", "Aisino", "A90 Pro"
- **Newpos**: "NEWPOS", "NEW9220", "NEW9830"
- **Urovo**: "UROVO"

### 5.3 Configuración de Comunicación Serial

**Parámetros estándar**:
- **Baud Rate**: 9600 o 115200 bps (auto-detectado en Aisino)
- **Data Bits**: 8
- **Parity**: None
- **Stop Bits**: 1

---

## 6. ESTADOS Y TRANSICIONES

### 6.1 Estados de Conexión

```
DISCONNECTED → Esperando conexión
    ↓
INITIALIZING → Configurando comunicación
    ↓
LISTENING → Escuchando comandos (SubPOS)
CONNECTED → Listo para inyectar (Injector)
    ↓
INJECTING → Inyectando llaves
    ↓
SUCCESS → Inyección exitosa
ERROR → Error durante operación
```

### 6.2 Estados de Inyección

```
IDLE → Estado inicial
    ↓
CONNECTING → Estableciendo comunicación
    ↓
VALIDATING → Validando configuración
    ↓
INJECTING → Inyectando llaves (progreso 0-100%)
    ↓
SUCCESS / ERROR → Resultado final
```

### 6.3 Flujo de Detección de Cable USB

**Métodos de Detección** (se usan 4 métodos en paralelo):

1. **UsbManager API**: Detecta dispositivos USB conectados
2. **Nodos /dev/**: Verifica archivos de dispositivo serial
3. **Sistema /sys/bus/usb**: Verifica dispositivos con interfaz serial
4. **TTY Class /sys/class/tty**: Verifica puertos TTY USB

**Criterio de Detección**:
- Cable presente si AL MENOS 2 de 4 métodos lo detectan
- O si método 1 (UsbManager - más confiable) lo detecta

---

## 7. GESTIÓN DE ERRORES

### 7.1 Códigos de Error Futurex

| Código | Descripción | Acción |
|--------|-------------|--------|
| 0x00 | Successful | Continuar |
| 0x01 | Invalid command | Verificar comando |
| 0x02 | Invalid version | Verificar versión del protocolo |
| 0x03 | Invalid length | Verificar longitud de llave |
| 0x05 | Device is busy | Reintentar |
| 0x08 | Bad LRC | Reconstruir mensaje |
| 0x09 | Duplicate key | Eliminar llave existente |
| 0x0C | Invalid key slot | Verificar slot |
| 0x10 | Invalid key type | Verificar tipo de llave |
| 0x12 | Invalid key checksum | Verificar KCV |
| 0x14 | Invalid KSN | Verificar KSN (20 chars hex) |
| 0x15 | Invalid key length | Verificar longitud |

### 7.2 Manejo de Timeouts

**Timeout de Lectura**:
- Configurado en 5000ms por defecto
- Ajustable según necesidad
- Retorna código de error al exceder

**Timeout de Escritura**:
- 1000ms típico
- Fallo si no puede escribir en tiempo

### 7.3 Recuperación de Errores

**Estrategias**:
1. **Retry automático**: Para errores transitorios (timeout, busy)
2. **Re-scan de puertos**: Para problemas de comunicación (Aisino)
3. **Estado limpio**: Reseteo completo del estado tras error fatal
4. **Logging detallado**: Registro completo para debugging

---

## 8. LOGGING Y DEBUGGING

### 8.1 Sistema de Logs

**Niveles de Log**:
- `CommLog.d()` - Debug: Información de desarrollo
- `CommLog.i()` - Info: Operaciones normales
- `CommLog.w()` - Warning: Situaciones anómalas no críticas
- `CommLog.e()` - Error: Errores que requieren atención

**Categorías de Log**:
- **RAW_SERIAL_OUT**: Datos enviados al puerto serial
- **RAW_SERIAL_IN**: Datos recibidos del puerto serial
- **ParsedMessage**: Mensajes parseados
- **KeyInjection**: Proceso de inyección
- **PedOperation**: Operaciones con PED

### 8.2 Logs Visibles en UI

El sistema muestra logs en tiempo real en la interfaz para:
- Proceso de inyección
- Estado de conexión
- Detección de cable USB
- Errores y advertencias

---

## 9. CONCLUSIÓN

Esta arquitectura proporciona:

✅ **Modularidad**: Componentes independientes y reutilizables  
✅ **Escalabilidad**: Fácil agregar nuevos fabricantes o protocolos  
✅ **Seguridad**: Múltiples capas de protección criptográfica  
✅ **Confiabilidad**: Manejo robusto de errores y recuperación  
✅ **Mantenibilidad**: Código organizado con patrones claros  
✅ **Trazabilidad**: Logging completo y persistencia de operaciones  

El sistema está diseñado para ser una solución empresarial robusta y segura para la gestión de llaves criptográficas en entornos POS.

---

**Siguiente Documento**: [Parte 2: Aplicaciones y Módulos del Sistema](DOCUMENTACION_02_APLICACIONES_Y_MODULOS.md)


