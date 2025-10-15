# Documentación del Sistema de Inyección de Llaves Criptográficas

## Parte 7: Fabricantes y Dispositivos Soportados

### Versión: 1.0
### Fecha: Octubre 2025

---

## 1. FABRICANTES SOPORTADOS

### 1.1 Visión General

El sistema soporta múltiples fabricantes de dispositivos POS mediante una arquitectura modular que abstrae las diferencias entre SDKs nativos.

**Fabricantes Implementados**:
1. **Aisino/Vanstone** - Implementación completa
2. **Newpos** - Implementación completa
3. **Urovo** - Implementación completa

**Fabricantes Definidos** (sin implementación completa):
4. **Ingenico** - Estructura preparada
5. **PAX** - Estructura preparada

### 1.2 Enum de Fabricantes

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

### 1.3 Detección Automática

**Basada en Build.MODEL**:
```kotlin
fun getManufacturerFromString(deviceName: String): EnumManufacturer {
    Log.d("DeviceCheck", "Revisando nombre de dispositivo: '$deviceName'")
    
    return when {
        // Newpos
        deviceName.contains("NEWPOS", ignoreCase = true) -> EnumManufacturer.NEWPOS
        deviceName.contains("NEW9220", ignoreCase = true) -> EnumManufacturer.NEWPOS
        deviceName.contains("NEW9830", ignoreCase = true) -> EnumManufacturer.NEWPOS
        
        // Aisino/Vanstone
        deviceName.contains("Vanstone", ignoreCase = true) -> EnumManufacturer.AISINO
        deviceName.contains("Aisino", ignoreCase = true) -> EnumManufacturer.AISINO
        deviceName.contains("A90 Pro", ignoreCase = true) -> EnumManufacturer.AISINO
        
        // Urovo
        deviceName.contains("UROVO", ignoreCase = true) -> EnumManufacturer.UROVO
        
        // Desconocido
        else -> EnumManufacturer.UNKNOWN
    }
}
```

**Inicialización**:
```kotlin
// En SystemConfig
var managerSelected: EnumManufacturer = getManufacturerFromString(Build.MODEL)
```

---

## 2. AISINO/VANSTONE

### 2.1 Información del Dispositivo

**Modelos Soportados**:
- Aisino A90 Pro
- Vanstone Series

**Características**:
- Android-based POS terminal
- Módulo PED integrado
- Comunicación serial USB interna
- Soporte para múltiples algoritmos

### 2.2 SDK de Vanstone

**Archivo**: `vanstoneSdkClient-noemv_20220114.jar`

**Clases Principales**:
- `Rs232Api` - Comunicación serial
- `PedApi` - Control del PED

**Inicialización**:
```kotlin
object AisinoCommunicationManager : ICommunicationManager {
    override suspend fun initialize(application: Application) {
        Log.i(TAG, "Inicializando SDK de Vanstone...")
        try {
            // SDK se inicializa automáticamente al cargar librería
            Log.i(TAG, "✓ SDK de Vanstone inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando SDK Vanstone", e)
            throw e
        }
    }
}
```

### 2.3 Comunicación Serial (Aisino)

#### 2.3.1 AisinoComController

**Operaciones Principales**:

```kotlin
class AisinoComController : IComController {
    
    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        // Configurar parámetros
        this.baudRate = baudRate.value
        this.parity = parity.value
        this.dataBits = dataBits.value
        return 0
    }
    
    override fun open(): Int {
        return try {
            Rs232Api.PortOpen_Api(port, baudRate)
        } catch (e: Exception) {
            -1
        }
    }
    
    override fun close(): Int {
        return try {
            Rs232Api.PortClose_Api(port)
            0
        } catch (e: Exception) {
            -1
        }
    }
    
    override fun write(data: ByteArray, timeout: Int): Int {
        return try {
            Rs232Api.PortSend_Api(port, data, data.size)
        } catch (e: Exception) {
            -1
        }
    }
    
    override fun readData(
        expectedLen: Int,
        buffer: ByteArray,
        timeout: Int
    ): Int {
        return try {
            Rs232Api.PortRecv_Api(port, buffer, timeout)
        } catch (e: Exception) {
            -1
        }
    }
}
```

#### 2.3.2 Auto-scan de Puertos

**Característica Única de Aisino**:
- Puertos disponibles: 0, 1
- Baudrates comunes: 9600, 115200
- Auto-detección mediante lectura de datos

**Configuración**:
```kotlin
// En SystemConfig
var aisinoCandidatePorts: List<Int> = listOf(0, 1)
var aisinoCandidateBauds: List<Int> = listOf(9600, 115200)
```

**Proceso**:
```kotlin
suspend fun autoScanPortsAndBauds(): Pair<Int, Int>? {
    for (port in candidatePorts) {
        for (baud in candidateBauds) {
            // Abrir puerto
            val openResult = Rs232Api.PortOpen_Api(port, baud)
            if (openResult != 0) continue
            
            // Intentar leer
            val buffer = ByteArray(256)
            val bytesRead = Rs232Api.PortRecv_Api(port, buffer, 2000)
            
            if (bytesRead > 0) {
                // Datos recibidos - configuración correcta
                selectedPort = port
                selectedBaud = baud
                return Pair(port, baud)
            }
            
            // Cerrar para próximo intento
            Rs232Api.PortClose_Api(port)
        }
    }
    
    return null
}
```

### 2.4 Control del PED (Aisino)

#### 2.4.1 AisinoPedController

**Wrapper sobre PedApi de Vanstone**:

```kotlin
class AisinoPedController : IPedController {
    
    override suspend fun initializePed(application: Application): Boolean {
        return try {
            // PedApi se inicializa con el SDK
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun writeKeyPlain(
        keyIndex: Int,
        keyType: KeyType,
        keyAlgorithm: KeyAlgorithm,
        keyBytes: ByteArray,
        kcvBytes: ByteArray?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Mapear tipos internos a Vanstone
            val vanstoneyType = mapToVanstoneKeyType(keyType)
            val vanstoneAlgorithm = mapToVanstoneAlgorithm(keyAlgorithm)
            
            // Inyectar llave
            val result = PedApi.writeKey(
                keyIndex,
                vanstoneKeyType,
                vanstoneAlgorithm,
                keyBytes
            )
            
            result == 0  // 0 = éxito
            
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo llave", e)
            false
        }
    }
    
    override suspend fun deleteAllKeys(): Boolean {
        return try {
            val result = PedApi.deleteAllKeys()
            result == 0
        } catch (e: Exception) {
            false
        }
    }
}
```

#### 2.4.2 Mapeo de Tipos

**De Sistema a Vanstone**:
```kotlin
private fun mapToVanstoneKeyType(keyType: KeyType): Int {
    return when (keyType) {
        KeyType.MASTER_KEY -> 0           // Master Key
        KeyType.WORKING_PIN_KEY -> 2      // PIN Key
        KeyType.WORKING_MAC_KEY -> 1      // MAC Key
        KeyType.WORKING_DATA_ENCRYPTION_KEY -> 3  // Data Key
        else -> 0  // Default a Master Key
    }
}

private fun mapToVanstoneAlgorithm(algorithm: KeyAlgorithm): Int {
    return when (algorithm) {
        KeyAlgorithm.DES_TRIPLE -> 0      // 3DES
        KeyAlgorithm.AES_128 -> 1         // AES
        KeyAlgorithm.AES_192 -> 1
        KeyAlgorithm.AES_256 -> 1
        else -> 0
    }
}
```

### 2.5 Limitaciones de Aisino

**Conocidas**:
1. Auto-scan puede tardar hasta 10 segundos
2. Puertos limitados (0, 1)
3. Re-scan necesario si configuración cambia
4. Algunas operaciones PED pueden requerir permisos especiales

---

## 3. NEWPOS

### 3.1 Información del Dispositivo

**Modelos Soportados**:
- Newpos NEW9220
- Newpos NEW9830

**Características**:
- Android POS terminal
- PED integrado
- Comunicación serial estable
- Sin necesidad de auto-scan

### 3.2 SDK de Newpos

**Archivo**: `AppSdkAidl_buildBy_20220217.jar`

**Clases Principales**:
- `SerialPort` - Comunicación serial
- `PinPad` - Control del PED

**Inicialización**:
```kotlin
object NewposKeyManager : IKeyManager {
    override suspend fun initialize(application: Application) {
        Log.i(TAG, "Inicializando SDK de Newpos...")
        try {
            // SDK se inicializa al usar
            pedController = NewposPedController()
            Log.i(TAG, "✓ SDK de Newpos inicializado")
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando SDK Newpos", e)
            throw e
        }
    }
}
```

### 3.3 Comunicación Serial (Newpos)

#### 3.3.1 NewposComController

**Configuración Fija**:
- Puerto: `/dev/ttyS4` (típico)
- Baudrate: 115200 (fijo)
- Sin auto-scan necesario

```kotlin
class NewposComController : IComController {
    
    private var serialPort: SerialPort? = null
    
    override fun init(
        baudRate: EnumCommConfBaudRate,
        parity: EnumCommConfParity,
        dataBits: EnumCommConfDataBits
    ): Int {
        // Configuración se usa al abrir
        this.baudRate = baudRate.value
        return 0
    }
    
    override fun open(): Int {
        return try {
            serialPort = SerialPort.getInstance()
            serialPort?.open("/dev/ttyS4", baudRate) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    override fun close(): Int {
        return try {
            serialPort?.close()
            0
        } catch (e: Exception) {
            -1
        }
    }
    
    override fun write(data: ByteArray, timeout: Int): Int {
        return try {
            serialPort?.write(data) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    override fun readData(
        expectedLen: Int,
        buffer: ByteArray,
        timeout: Int
    ): Int {
        return try {
            serialPort?.read(buffer, timeout) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
}
```

### 3.4 Control del PED (Newpos)

#### 3.4.1 NewposPedController

```kotlin
class NewposPedController : IPedController {
    
    private var pinPad: PinPad? = null
    
    override suspend fun initializePed(application: Application): Boolean {
        return try {
            pinPad = PinPad.getInstance(application)
            pinPad?.init() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun writeKeyPlain(
        keyIndex: Int,
        keyType: KeyType,
        keyAlgorithm: KeyAlgorithm,
        keyBytes: ByteArray,
        kcvBytes: ByteArray?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val newposKeyType = mapToNewposKeyType(keyType)
            val newposAlgorithm = mapToNewposAlgorithm(keyAlgorithm)
            
            val result = pinPad?.loadPlainKey(
                newposKeyType,
                keyIndex,
                keyBytes,
                newposAlgorithm
            )
            
            result == 0
            
        } catch (e: Exception) {
            false
        }
    }
}
```

#### 3.4.2 Mapeo de Tipos (Newpos)

```kotlin
private fun mapToNewposKeyType(keyType: KeyType): Int {
    return when (keyType) {
        KeyType.MASTER_KEY -> 0           // Main Key
        KeyType.WORKING_PIN_KEY -> 2      // PIN Key
        KeyType.WORKING_MAC_KEY -> 1      // MAC Key
        KeyType.WORKING_DATA_ENCRYPTION_KEY -> 3
        else -> 0
    }
}
```

### 3.5 Ventajas de Newpos

✅ Sin necesidad de auto-scan  
✅ Puerto serial fijo y predecible  
✅ Configuración más simple  
✅ Comunicación estable  
✅ SDK bien documentado  

---

## 4. UROVO

### 4.1 Información del Dispositivo

**Modelos Soportados**:
- Urovo i9000S
- Urovo i9100
- Serie Urovo POS

**Características**:
- Android POS robusto
- PED certificado
- Soporte completo de algoritmos
- SM4 (algoritmo chino)

### 4.2 SDK de Urovo

**Archivo**: `urovo-sdk-v1.0.20.aar`

**Clases Principales**:
- `com.urovo.sdk.pinpad.PinPadManager`
- `com.urovo.sdk.serialport.SerialPortManager`

### 4.3 Constantes de Urovo

#### 4.3.1 UrovoConstants

```kotlin
object UrovoConstants {
    
    // Tipos de Llave
    object KeyType {
        const val MAIN_KEY = 0      // Master Key
        const val MAC_KEY = 1       // MAC Key
        const val PIN_KEY = 2       // PIN Key
        const val TD_KEY = 3        // Track Data Key
    }
    
    // Algoritmos de Llave
    object KeyAlgorithm {
        const val DES = 0
        const val SM4 = 1
        const val AES = 2
    }
    
    // Algoritmos de Cifrado
    object Algorithm {
        const val DES_ECB = 1
        const val DES_CBC = 2
        const val SM4 = 3
        const val AES_ECB = 7
        const val AES_CBC = 8
    }
    
    // Modos MAC
    object MacMode {
        const val XOR = 0x00
        const val ANSI_X9_9 = 0x01
        const val ANSI_X9_19 = 0x11
        const val POS_ECB = 0x10
        const val CMAC = 0x07
    }
    
    // DUKPT
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
}
```

### 4.4 Control del PED (Urovo)

#### 4.4.1 UrovoPedController

**Características Especiales**:
- Soporte completo de DUKPT
- Algoritmo SM4 (chino)
- Múltiples modos de MAC

```kotlin
class UrovoPedController : IPedController {
    
    private var pinPadManager: PinPadManager? = null
    
    override suspend fun initializePed(application: Application): Boolean {
        return try {
            pinPadManager = PinPadManager.getInstance(application)
            pinPadManager?.open() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun writeKeyPlain(
        keyIndex: Int,
        keyType: KeyType,
        keyAlgorithm: KeyAlgorithm,
        keyBytes: ByteArray,
        kcvBytes: ByteArray?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val urovoKeyType = mapToUrovoKeyType(keyType)
            val urovoAlgorithm = mapToUrovoAlgorithm(keyAlgorithm)
            
            val result = pinPadManager?.loadKey(
                urovoKeyType,
                keyIndex,
                keyBytes,
                urovoAlgorithm
            )
            
            result == 0
            
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun writeDukptInitialKeyEncrypted(
        groupIndex: Int,
        keyAlgorithm: KeyAlgorithm,
        encryptedIpek: ByteArray,
        initialKsn: ByteArray,
        transportKeyIndex: Int,
        keyChecksum: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val urovoAlgorithm = when (keyAlgorithm) {
                KeyAlgorithm.DES_TRIPLE -> UrovoConstants.KeyAlgorithm.DES
                KeyAlgorithm.AES_128, KeyAlgorithm.AES_256 -> 
                    UrovoConstants.KeyAlgorithm.AES
                else -> UrovoConstants.KeyAlgorithm.DES
            }
            
            val result = pinPadManager?.loadDukptKey(
                groupIndex,
                urovoAlgorithm,
                encryptedIpek,
                initialKsn,
                transportKeyIndex
            )
            
            result == 0
            
        } catch (e: Exception) {
            false
        }
    }
}
```

#### 4.4.2 Soporte de SM4

**Algoritmo Chino**:
```kotlin
// Detección de SM4
if (keyAlgorithm == KeyAlgorithm.SM4) {
    val result = pinPadManager?.loadKey(
        UrovoConstants.KeyType.MAIN_KEY,
        keyIndex,
        keyBytes,
        UrovoConstants.KeyAlgorithm.SM4
    )
}
```

**Uso**:
- Mercado chino
- Alternativa a AES
- 128 bits (16 bytes)

### 4.5 Ventajas de Urovo

✅ Soporte completo de DUKPT  
✅ Algoritmo SM4  
✅ Múltiples modos de MAC  
✅ SDK robusto y actualizado  
✅ Certificaciones internacionales  
✅ Buen soporte técnico  

---

## 5. COMPARATIVA DE FABRICANTES

### 5.1 Tabla Comparativa

| Característica | Aisino/Vanstone | Newpos | Urovo |
|----------------|-----------------|--------|-------|
| **Auto-scan de puertos** | ✅ Sí (necesario) | ❌ No (puerto fijo) | ❌ No |
| **Puertos serial** | 0, 1 (variable) | `/dev/ttyS4` (fijo) | Configurado |
| **Baudrates** | 9600, 115200 | 115200 | Múltiples |
| **Soporte DUKPT completo** | ⚠️ Básico | ⚠️ Básico | ✅ Completo |
| **Algoritmo SM4** | ❌ No | ❌ No | ✅ Sí |
| **Complejidad SDK** | Media | Baja | Alta |
| **Estabilidad** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Documentación** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Uso en producción** | Común | Común | Profesional |

### 5.2 Recomendaciones de Uso

#### 5.2.1 Aisino/Vanstone

**Ideal para**:
- Proyectos con presupuesto limitado
- Tiendas pequeñas/medianas
- Transacciones básicas
- Entornos donde auto-scan es aceptable

**No recomendado para**:
- Alto volumen de transacciones
- DUKPT complejo
- Aplicaciones críticas de tiempo

#### 5.2.2 Newpos

**Ideal para**:
- Implementaciones estándar
- Configuración simple
- Comunicación estable requerida
- Proyectos con plazos ajustados

**No recomendado para**:
- Algoritmos avanzados (SM4)
- DUKPT complejo
- Mercado chino

#### 5.2.3 Urovo

**Ideal para**:
- Aplicaciones empresariales
- Alto volumen de transacciones
- DUKPT completo
- Mercado chino (SM4)
- Certificaciones requeridas

**No recomendado para**:
- Proyectos de bajo presupuesto
- Desarrollo rápido inicial

---

## 6. ARQUITECTURA DE ABSTRACCIÓN

### 6.1 Strategy Pattern

**Objetivo**: Intercambiar implementaciones de fabricante sin cambiar código cliente

**Diagrama**:
```
[KeySDKManager]
        ↓
    (selecciona según SystemConfig.managerSelected)
        ↓
┌───────┴────────┬──────────────┐
│                │              │
[AisinoKeyManager] [NewposKeyManager] [UrovoKeyManager]
│                │              │
[AisinoPedController] [NewposPedController] [UrovoPedController]
│                │              │
[VanstoneSDK]   [NewposSDK]   [UrovoSDK]
```

### 6.2 Código de Selección

```kotlin
object KeySDKManager : IKeyManager {
    
    private val manager: IKeyManager by lazy {
        when (SystemConfig.managerSelected) {
            EnumManufacturer.NEWPOS -> NewposKeyManager
            EnumManufacturer.AISINO -> AisinoKeyManager
            EnumManufacturer.UROVO -> UrovoKeyManager
            else -> throw IllegalStateException("Fabricante no soportado")
        }
    }
    
    override suspend fun initialize(application: Application) {
        manager.initialize(application)
    }
    
    override fun getPedController(): IPedController? {
        return manager.getPedController()
    }
}
```

**Ventajas**:
- Cambio de fabricante sin tocar código de negocio
- Fácil agregar nuevos fabricantes
- Testing con mocks simplificado

### 6.3 Agregar Nuevo Fabricante

**Pasos**:

1. **Agregar al Enum**:
   ```kotlin
   enum class EnumManufacturer {
       // ...
       NUEVO_FABRICANTE
   }
   ```

2. **Crear Manager**:
   ```kotlin
   object NuevoFabricanteKeyManager : IKeyManager {
       override suspend fun initialize(application: Application) { ... }
       override fun getPedController(): IPedController? { ... }
   }
   ```

3. **Crear PedController**:
   ```kotlin
   class NuevoFabricantePedController : IPedController {
       // Implementar todos los métodos
   }
   ```

4. **Crear ComController**:
   ```kotlin
   class NuevoFabricanteComController : IComController {
       // Implementar comunicación serial
   }
   ```

5. **Registrar en KeySDKManager**:
   ```kotlin
   when (SystemConfig.managerSelected) {
       // ...
       EnumManufacturer.NUEVO_FABRICANTE -> NuevoFabricanteKeyManager
   }
   ```

6. **Agregar Detección**:
   ```kotlin
   fun getManufacturerFromString(deviceName: String): EnumManufacturer {
       return when {
           // ...
           deviceName.contains("NuevoModelo", ignoreCase = true) -> 
               EnumManufacturer.NUEVO_FABRICANTE
           // ...
       }
   }
   ```

---

## 7. CONFIGURACIÓN POR DISPOSITIVO

### 7.1 Configuración de Aisino

```kotlin
// SystemConfig
if (managerSelected == EnumManufacturer.AISINO) {
    aisinoCandidatePorts = listOf(0, 1)
    aisinoCandidateBauds = listOf(9600, 115200)
    
    // Ejecutar auto-scan al iniciar
    CommunicationSDKManager.rescanIfSupported()
}
```

### 7.2 Configuración de Newpos

```kotlin
// Configuración fija
val NEWPOS_SERIAL_PORT = "/dev/ttyS4"
val NEWPOS_BAUDRATE = 115200

// Sin auto-scan necesario
```

### 7.3 Configuración de Urovo

```kotlin
// Configuración desde SDK
val urovoConfig = UrovoConfig()
urovoConfig.serialPort = "/dev/ttyS1"  // Por defecto
urovoConfig.baudRate = 115200
```

---

## 8. TROUBLESHOOTING POR FABRICANTE

### 8.1 Aisino/Vanstone

#### Error: "Auto-scan no encuentra puerto"

**Causa**: Puertos no disponibles o baudrate incorrecto

**Solución**:
1. Verificar que app tenga permisos de serial
2. Probar manualmente cada puerto:
   ```kotlin
   Rs232Api.PortOpen_Api(0, 9600)
   Rs232Api.PortOpen_Api(0, 115200)
   Rs232Api.PortOpen_Api(1, 9600)
   Rs232Api.PortOpen_Api(1, 115200)
   ```
3. Revisar logs del auto-scan
4. Reiniciar dispositivo

#### Error: "PedApi retorna -1"

**Causa**: PED no inicializado o ocupado

**Solución**:
1. Verificar inicialización de SDK
2. Cerrar otras apps que usen PED
3. Reiniciar app
4. Reiniciar dispositivo

### 8.2 Newpos

#### Error: "SerialPort.open() falla"

**Causa**: Puerto `/dev/ttyS4` no disponible

**Solución**:
1. Verificar que el puerto existe:
   ```bash
   ls -la /dev/ttyS*
   ```
2. Verificar permisos:
   ```bash
   chmod 666 /dev/ttyS4
   ```
3. Probar puerto alternativo si aplica

#### Error: "PinPad.init() retorna error"

**Causa**: SDK no inicializado correctamente

**Solución**:
1. Verificar versión de SDK compatible
2. Limpiar y reconstruir proyecto
3. Verificar que AAR está incluido
4. Revisar logs de inicialización

### 8.3 Urovo

#### Error: "PinPadManager.open() falla"

**Causa**: Permisos o inicialización incorrecta

**Solución**:
1. Verificar permisos en AndroidManifest:
   ```xml
   <uses-permission android:name="urovo.permission.PINPAD" />
   ```
2. Inicializar en Application.onCreate()
3. Verificar versión de SDK

#### Error: "loadDukptKey() retorna error"

**Causa**: Parámetros DUKPT incorrectos

**Solución**:
1. Verificar longitud de IPEK
2. Verificar longitud de KSN (10 bytes)
3. Verificar que KEK existe en transportKeyIndex
4. Revisar algoritmo (DES vs AES)

---

## 9. CERTIFICACIONES Y CUMPLIMIENTO

### 9.1 Certificaciones por Fabricante

| Fabricante | PCI PTS | EMV Level 1 | EMV Level 2 | PCI PIN | Otras |
|------------|---------|-------------|-------------|---------|-------|
| Aisino | ✅ | ✅ | ✅ | ✅ | - |
| Newpos | ✅ | ✅ | ✅ | ✅ | UnionPay |
| Urovo | ✅ | ✅ | ✅ | ✅ | PBOC, JCB |

### 9.2 Algoritmos Certificados

| Algoritmo | Aisino | Newpos | Urovo |
|-----------|--------|--------|-------|
| 3DES | ✅ | ✅ | ✅ |
| AES-128 | ✅ | ✅ | ✅ |
| AES-256 | ✅ | ✅ | ✅ |
| SM4 | ❌ | ❌ | ✅ |
| RSA | ⚠️ Limitado | ⚠️ Limitado | ✅ |
| DUKPT 3DES | ✅ | ✅ | ✅ |
| DUKPT AES | ⚠️ Básico | ⚠️ Básico | ✅ |

---

## 10. CONCLUSIÓN

El sistema de soporte multi-fabricante proporciona:

✅ **Abstracción Completa**: Interfaz uniforme para todos los fabricantes  
✅ **Detección Automática**: Identificación de dispositivo por modelo  
✅ **Fácil Extensión**: Strategy pattern para agregar fabricantes  
✅ **Configuración Específica**: Parámetros optimizados por fabricante  
✅ **Robustez**: Manejo de diferencias entre SDKs  
✅ **Compatibilidad**: Soporte de características avanzadas donde disponible  

**Fabricantes Recomendados**:
- **Desarrollo/Testing**: Newpos (simplicidad)
- **Producción Estándar**: Aisino o Newpos
- **Empresarial/Crítico**: Urovo (características completas)

La arquitectura modular permite una fácil expansión a nuevos fabricantes mientras mantiene la estabilidad del código existente.

---

**Fin de la Documentación Completa del Sistema**

Esta documentación cubre todos los aspectos del Sistema de Inyección de Llaves Criptográficas, desde la arquitectura general hasta los detalles específicos de cada fabricante soportado.

**Documentos de la Serie**:
1. [Introducción y Arquitectura General](DOCUMENTACION_01_INTRODUCCION_Y_ARQUITECTURA.md)
2. [Aplicaciones y Módulos del Sistema](DOCUMENTACION_02_APLICACIONES_Y_MODULOS.md)
3. [Tipos de Llaves y Criptografía](DOCUMENTACION_03_TIPOS_LLAVES_CRIPTOGRAFIA.md)
4. [Perfiles y Configuración](DOCUMENTACION_04_PERFILES_CONFIGURACION.md)
5. [Protocolos de Comunicación](DOCUMENTACION_05_PROTOCOLOS_COMUNICACION.md)
6. [Usuarios y Persistencia](DOCUMENTACION_06_USUARIOS_PERSISTENCIA.md)
7. [Fabricantes y Dispositivos Soportados](DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md)


