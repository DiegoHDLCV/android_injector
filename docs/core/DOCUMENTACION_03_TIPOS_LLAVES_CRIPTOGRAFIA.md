# Documentación del Sistema de Inyección de Llaves Criptográficas

## Parte 3: Tipos de Llaves y Criptografía

### Versión: 1.0
### Fecha: Octubre 2025

---

## 1. TIPOS DE LLAVES SOPORTADAS

### 1.1 Clasificación por Propósito

El sistema soporta múltiples tipos de llaves criptográficas, cada una con un propósito específico en el ecosistema de seguridad POS:

#### 1.1.1 Master Key (Llave Maestra)

**Descripción**: Llave de nivel superior utilizada para cifrar/descifrar otras llaves.

**Características**:
- **Código en Sistema**: `KeyType.MASTER_KEY`
- **Almacenamiento**: Android KeyStore (protección hardware)
- **Uso**: Cifrado de Working Keys
- **Inyección**: Siempre en claro (plaintext)
- **Slots típicos**: 0-9

**Ejemplos de Uso**:
- TMK (Terminal Master Key)
- MFK (Master File Key)
- KEK (Key Encryption Key) - tipo especial de Master Key

#### 1.1.2 Working PIN Key (Llave de Cifrado de PIN)

**Descripción**: Llave específica para cifrado y traducción de PINs.

**Características**:
- **Código en Sistema**: `KeyType.WORKING_PIN_KEY`
- **Código Futurex**: "05"
- **Inyección**: En claro o cifrada con Master Key
- **Uso**: Operaciones de PIN
- **Slots típicos**: 10-29

**Ejemplos**:
- PEK (PIN Encryption Key)
- TPK (Terminal PIN Key)

#### 1.1.3 Working MAC Key (Llave de MAC)

**Descripción**: Llave para cálculo y verificación de MAC (Message Authentication Code).

**Características**:
- **Código en Sistema**: `KeyType.WORKING_MAC_KEY`
- **Código Futurex**: "04"
- **Inyección**: En claro o cifrada con Master Key
- **Uso**: Autenticación de mensajes
- **Slots típicos**: 30-49

**Ejemplos**:
- MAK (MAC Authentication Key)
- TAK (Terminal Authentication Key)

#### 1.1.4 Working Data Encryption Key

**Descripción**: Llave para cifrado/descifrado de datos sensibles.

**Características**:
- **Código en Sistema**: `KeyType.WORKING_DATA_KEY`
- **Código Futurex**: "0C"
- **Inyección**: En claro o cifrada con Master Key
- **Uso**: Cifrado de datos de track, CVV, etc.
- **Slots típicos**: 50-69

**Ejemplos**:
- DEK (Data Encryption Key)
- TDK (Track Data Key)

#### 1.1.5 DUKPT Initial Key (BDK/IPEK)

**Descripción**: Llave inicial para esquemas DUKPT (Derived Unique Key Per Transaction).

**Características**:
- **Código en Sistema**: `KeyType.DUKPT_INITIAL_KEY`
- **Requiere**: KSN (Key Serial Number) de 20 caracteres hex
- **Derivación**: Genera llaves únicas por transacción
- **Variantes**:
  - DUKPT 3DES BDK (Base Derivation Key) - Código Futurex "08"
  - DUKPT 3DES IPEK (Initial PIN Encryption Key) - Código Futurex "03"
  - DUKPT AES BDK - Código Futurex "10"
  - DUKPT AES IPEK - Código Futurex "0B"

**Slots típicos**: 70-89

**Ejemplo de KSN**:
```
F876543210000000000A
│││││││││└─ Transaction Counter
│││││││└───── Device ID
│└──┴──────────── IIN (Issuer ID)
```

#### 1.1.6 RSA Keys (Llaves Asimétricas)

**Descripción**: Par de llaves pública/privada para criptografía asimétrica.

**Características**:
- **Privada**: `KeyType.RSA_PRIVATE_KEY`
- **Pública**: `KeyType.RSA_PUBLIC_KEY`
- **Longitudes**: 1024, 2048, 4096 bits
- **Uso**: Firma digital, intercambio seguro de llaves

**Slots típicos**: 90-99

#### 1.1.7 Transport Key (KEK - Key Encryption Key)

**Descripción**: Llave específica para transportar otras llaves de forma segura.

**Características**:
- **Código en Sistema**: `KeyType.TRANSPORT_KEY`
- **Función**: Cifrar llaves antes de transmisión
- **Almacenamiento**: Marcada con flag `isKEK = true`
- **Estados**:
  - ACTIVE: Lista para usar, no exportada
  - EXPORTED: Ya fue enviada al SubPOS
  - INACTIVE: Reemplazada por nueva KEK

**Uso en el Sistema**:
1. Se genera en Injector mediante ceremonia
2. Se marca como KEK
3. Se exporta al SubPOS (comando especial)
4. Se usa para cifrar todas las llaves operacionales

---

### 1.2 Clasificación por Algoritmo

#### 1.2.1 DES (Data Encryption Standard)

**DES Simple (Obsoleto)**:
- **Código**: `KeyAlgorithm.DES_SINGLE`
- **Longitud de llave**: 8 bytes (64 bits, 56 bits efectivos)
- **Uso**: Solo para compatibilidad legacy
- **Seguridad**: NO recomendado

**DES Doble**:
- **Código**: `KeyAlgorithm.DES_DOUBLE`
- **Longitud de llave**: 16 bytes (K1 = K3)
- **Uso**: Transición a 3DES
- **Seguridad**: Moderada

**Triple DES (3DES/TDES)**:
- **Código**: `KeyAlgorithm.DES_TRIPLE`
- **Longitudes**: 
  - 16 bytes (128 bits) - K1, K2, K1
  - 24 bytes (192 bits) - K1, K2, K3
- **Código Futurex**: "01" (Master Session Key)
- **Uso**: Estándar en la industria POS
- **Seguridad**: Alta (hasta 2030 según NIST)

**Modos de Operación**:
- ECB (Electronic Codebook)
- CBC (Cipher Block Chaining)

#### 1.2.2 AES (Advanced Encryption Standard)

**AES-128**:
- **Código**: `KeyAlgorithm.AES_128`
- **Longitud de llave**: 16 bytes (128 bits)
- **Código Futurex**: "01" (Master Session Key)
- **Uso**: Estándar moderno
- **Seguridad**: Muy alta

**AES-192**:
- **Código**: `KeyAlgorithm.AES_192`
- **Longitud de llave**: 24 bytes (192 bits)
- **Uso**: Mayor seguridad
- **Seguridad**: Muy alta

**AES-256**:
- **Código**: `KeyAlgorithm.AES_256`
- **Longitud de llave**: 32 bytes (256 bits)
- **Uso**: Máxima seguridad
- **Seguridad**: Extremadamente alta

**Modos de Operación**:
- ECB
- CBC
- GCM (Galois/Counter Mode)
- CMAC (Cipher-based MAC)

#### 1.2.3 SM4 (Estándar Chino)

**Características**:
- **Código**: `KeyAlgorithm.SM4`
- **Longitud de llave**: 16 bytes (128 bits)
- **Estándar**: GB/T 32907-2016
- **Uso**: Mercado chino
- **Soporte**: Dispositivos Urovo

---

### 1.3 Tabla de Mapeo de Tipos Futurex

El sistema traduce tipos de llave internos a códigos del protocolo Futurex:

| Tipo Interno | Código Futurex | Descripción | Requiere KSN |
|--------------|----------------|-------------|--------------|
| PIN | "05" | PIN Encryption Key | No |
| MAC | "04" | MAC Key | No |
| TDES | "01" | Master Session Key (3DES) | No |
| 3DES | "01" | Master Session Key (3DES) | No |
| AES | "01" | Master Session Key (AES) | No |
| DUKPT | "08" | DUKPT 3DES BDK | Sí |
| DUKPT_TDES | "08" | DUKPT 3DES BDK | Sí |
| DUKPT_3DES | "08" | DUKPT 3DES BDK | Sí |
| DUKPT_AES | "10" | DUKPT AES BDK | Sí |
| DUKPT_INITIAL | "03" | DUKPT 3DES IPEK | Sí |
| IPEK | "03" | DUKPT 3DES IPEK | Sí |
| DUKPT_AES_INITIAL | "0B" | DUKPT AES IPEK | Sí |
| AES_IPEK | "0B" | DUKPT AES IPEK | Sí |
| DATA | "0C" | Data Encryption Key | No |
| (desconocido) | "01" | Master Session Key (default) | No |

---

## 2. CRIPTOGRAFÍA Y SEGURIDAD

### 2.1 Generación de Llaves

#### 2.1.1 Ceremonia de Llaves (Método de División de Secretos)

**Principio de Shamir**:
- Ningún individuo conoce la llave completa
- Se requieren N componentes para reconstruir la llave
- Cada custodio aporta un componente

**Proceso en el Sistema**:

1. **Configuración**:
   ```
   Número de custodios: N (2-5)
   Tipo de llave: TDES, AES-128, AES-192, AES-256
   Longitud requerida: L bytes (16, 24, 32)
   ```

2. **Generación de Componentes**:
   - Cada custodio genera aleatoriamente su componente
   - Longitud de cada componente = L bytes
   - Formato: Hexadecimal (2L caracteres)

3. **Combinación (XOR)**:
   ```
   Componente 1: AABBCCDDEEFF00112233445566778899
   Componente 2: 1122334455667788AABBCCDDEEFF0011
                 ⊕ (XOR)
   Resultado:    BBAAFFAA99AA88BBAA88AA8899881111
   
   Si hay más componentes:
   Resultado ⊕ Componente 3 → Llave Final
   ```

4. **Propiedades del XOR**:
   - **Reversible**: A ⊕ B ⊕ B = A
   - **Sin pérdida**: Información completa preservada
   - **Seguro**: Conocer N-1 componentes no revela nada

5. **Almacenamiento Seguro**:
   - Llave final → Android KeyStore
   - KCV → Base de datos
   - Datos completos → Base de datos (cifrados)

**Ejemplo Completo**:
```
Ceremonia de 3 Custodios - AES-128 (16 bytes)

Custodio 1: 0123456789ABCDEF0011223344556677
Custodio 2: FEDCBA9876543210FFEEDDCCBBAA9988
Custodio 3: AAAA5555FFFF0000AAAA5555FFFF0000

XOR(C1, C2):     FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF
XOR intermedio:  FFFF FFFF FFFF FFFF FFFF FFFF FFFF FFFF

XOR(intermedio, C3): 5555 AAAA 0000 FFFF 5555 AAAA 0000 FFFF

Llave Final:     5555AAAA0000FFFF5555AAAA0000FFFF
KCV calculado:   A1B2C3
```

#### 2.1.2 Generación Aleatoria Segura (SecureRandom)

**Para KEK y llaves especiales**:

```kotlin
val secureRandom = SecureRandom()
val keyBytes = ByteArray(keyLength)
secureRandom.nextBytes(keyBytes)
```

**Características**:
- **Fuente de entropía**: Hardware RNG (si disponible)
- **Algoritmo**: SHA1PRNG o NativePRNG
- **Calidad**: Criptográficamente seguro
- **Distribución**: Uniforme

**Proceso Completo**:
1. Inicializar SecureRandom()
2. Generar bytes aleatorios
3. Convertir a hexadecimal
4. Calcular KCV
5. Determinar algoritmo según longitud
6. Almacenar

### 2.2 Cálculo del KCV (Key Check Value)

**Propósito**: Validar integridad de llaves sin exponerlas

**Algoritmo**:
1. Tomar la llave (K)
2. Cifrar bloque de ceros: E(K, 0x0000000000000000)
3. Tomar primeros 3 bytes del resultado
4. Convertir a hexadecimal

**Ejemplo para 3DES**:
```
Llave (K):          AABBCCDDEEFF00112233445566778899
Bloque de ceros:    0000000000000000
Cifrado 3DES(K, 0): E7A1B2C3D4E5F601...
KCV (3 bytes):      E7A1B2
```

**Ejemplo para AES**:
```
Llave (K):          0123456789ABCDEF0011223344556677
Bloque de ceros:    00000000000000000000000000000000
Cifrado AES(K, 0):  8F3C4B5A6D7E8F90...
KCV (3 bytes):      8F3C4B
```

**Uso en el Sistema**:
- **Generación**: Al crear nueva llave
- **Validación**: Al recibir llave inyectada
- **Identificación**: Clave única en base de datos
- **Verificación**: Comparar KCV esperado vs calculado

### 2.3 Cifrado de Llaves con KEK

#### 2.3.1 Modo Sin KEK (En Claro)

**Características**:
- Llaves enviadas sin cifrado adicional
- Solo para desarrollo o red confiable
- **Tipo de encriptación Futurex**: "00"

**Estructura del Comando**:
```
Encryption Type: "00"
KTK Slot:        "00"
KTK Checksum:    "0000"
Key Data:        [Datos en claro]
```

**Advertencia de Seguridad**:
```
⚠️ No hay KEK activa - enviando llave en CLARO
⚠️ RECOMENDACIÓN: Generar y exportar KEK primero
```

#### 2.3.2 Modo Con KEK (Cifrado)

**Características**:
- Llaves cifradas con KEK antes de enviar
- KEK debe estar pre-cargada en SubPOS
- **Tipo de encriptación Futurex**: "01"

**Proceso de Cifrado**:

1. **Obtener KEK**:
   ```kotlin
   val kekEntity = KEKManager.getActiveKEKEntity()
   val kekData = kekEntity.keyData
   val kekKcv = kekEntity.kcv
   val kekSlot = kekEntity.keySlot
   ```

2. **Cifrar Llave Operacional**:
   ```kotlin
   val encryptedKey = TripleDESCrypto.encryptKeyForTransmission(
       keyData = originalKey,
       kekData = kekData,
       keyKcv = originalKcv
   )
   ```

3. **Algoritmo 3DES ECB**:
   ```
   Llave Operacional: AABBCCDDEEFF00112233445566778899
   KEK:               1122334455667788AABBCCDDEEFF0011
   
   Cifrado 3DES ECB:
   Resultado:         E7A1B2C3D4E5F6018F9A0B1C2D3E4F50
   ```

4. **Estructura del Comando**:
   ```
   Encryption Type: "01" (Cifrado bajo KTK)
   KTK Slot:        "05" (Slot donde está la KEK)
   KTK Checksum:    "A1B2" (KCV de la KEK)
   Key Data:        [Datos cifrados]
   ```

5. **En el SubPOS**:
   - Recibe llave cifrada
   - Identifica KEK en slot 05
   - Descifra con KEK
   - Valida KCV
   - Almacena en PED

#### 2.3.3 Flujo de Exportación de KEK

**Primera Inyección con KEK**:

```
[Injector]
  ├─ KEK estado = ACTIVE (no exportada)
  ├─ Modal de confirmación:
  │  "⚠️ Esta KEK debe exportarse al SubPOS primero"
  │  "Se enviará en CLARO para inicializar el SubPOS"
  │  [Cancelar] [Exportar y Continuar]
  └─ Usuario confirma
      ↓
  ├─ Construir comando especial de exportación
  │  └─ Encryption Type: "00" (KEK en claro)
  │  └─ Key Type: "01" (Master Session Key)
  │  └─ Slot: [KEK_SLOT]
  └─ Enviar KEK
      ↓
[SubPOS]
  ├─ Recibe KEK en claro
  ├─ Almacena en PED
  ├─ Registra en BD
  └─ Confirma recepción
      ↓
[Injector]
  ├─ Recibe confirmación
  ├─ Actualiza KEK: estado = EXPORTED
  └─ Continúa con inyección de llaves operacionales
      (ahora cifradas con esta KEK)
```

**Inyecciones Subsecuentes**:
- KEK estado = EXPORTED
- No se vuelve a exportar
- Todas las llaves se cifran con esta KEK
- Warning si se intenta usar KEK diferente

### 2.4 DUKPT (Derived Unique Key Per Transaction)

#### 2.4.1 Conceptos Fundamentales

**Principio**:
- Llave única por transacción
- Derivación matemática unidireccional
- Imposible reconstruir BDK desde llaves derivadas

**Componentes**:

1. **BDK (Base Derivation Key)**:
   - Llave maestra del esquema DUKPT
   - Almacenada en servidor seguro
   - Nunca sale del HSM

2. **IPEK (Initial PIN Encryption Key)**:
   - Primera llave derivada de BDK + KSN inicial
   - Inyectada en el dispositivo
   - Punto de partida para derivaciones

3. **KSN (Key Serial Number)**:
   - 20 caracteres hexadecimales (10 bytes)
   - Estructura:
     ```
     [IIN: 8 hex][Device ID: 10 hex][Transaction Counter: 2 hex]
     Ejemplo: F87654321000000000000A
              │││││││││└─ Counter (000A = transacción 10)
              │└──┴──────── Device ID
              └───────────── IIN (Issuer ID)
     ```

4. **Working Keys**:
   - Derivadas de IPEK + KSN actual
   - Una diferente por transacción
   - Se descartan después de uso

#### 2.4.2 Tipos de Llaves DUKPT en el Sistema

**DUKPT 3DES BDK**:
- **Código Futurex**: "08"
- **Algoritmo**: Triple DES
- **Longitud**: 16 o 24 bytes
- **Uso**: Esquema DUKPT tradicional

**DUKPT 3DES IPEK**:
- **Código Futurex**: "03"
- **Derivada de**: BDK + KSN inicial
- **Inyección**: Cifrada con KEK

**DUKPT AES BDK**:
- **Código Futurex**: "10"
- **Algoritmo**: AES
- **Longitud**: 16, 24 o 32 bytes
- **Uso**: Esquema DUKPT moderno

**DUKPT AES IPEK**:
- **Código Futurex**: "0B"
- **Derivada de**: BDK AES + KSN inicial

#### 2.4.3 Validación de KSN

**Requisitos**:
- Exactamente 20 caracteres hexadecimales
- Solo dígitos 0-9 y A-F (case insensitive)
- No puede estar vacío para llaves DUKPT

**Validación en el Sistema**:
```kotlin
fun validateKSN(ksn: String, keyType: String): Boolean {
    if (!isDukptKeyType(keyType)) return true
    
    if (ksn.length != 20) {
        throw Exception("KSN debe tener exactamente 20 caracteres")
    }
    
    if (!ksn.matches(Regex("^[0-9A-Fa-f]{20}$"))) {
        throw Exception("KSN debe contener solo caracteres hexadecimales")
    }
    
    return true
}
```

**Generación Automática (si no se proporciona)**:
```kotlin
// Base: KCV rellenado con ceros
val base = selectedKey.kcv.padEnd(16, '0')

// Suffix: Slot en formato hexadecimal (4 chars)
val suffix = slot.toString(16).padStart(4, '0')

// KSN final (20 chars)
val ksn = base + suffix
```

Ejemplo:
```
KCV:    AABB
Base:   AABB000000000000
Slot:   5
Suffix: 0005
KSN:    AABB000000000000005
```

#### 2.4.4 Inyección de Llaves DUKPT

**Comando Futurex para DUKPT**:
```
<STX>02[VERSION][KEY_SLOT][KTK_SLOT][KEY_TYPE][ENC_TYPE]
[KEY_CHECKSUM][KTK_CHECKSUM][KSN][KEY_LENGTH][KEY_DATA]<ETX><LRC>

Ejemplo DUKPT 3DES BDK:
VERSION:        01
KEY_SLOT:       04 (slot 4)
KTK_SLOT:       05 (KEK en slot 5)
KEY_TYPE:       08 (DUKPT 3DES BDK)
ENC_TYPE:       01 (cifrado con KEK)
KEY_CHECKSUM:   AABB
KTK_CHECKSUM:   1122
KSN:            F876543210000000000A
KEY_LENGTH:     010 (16 bytes)
KEY_DATA:       [datos cifrados con KEK]
```

**En el PED**:
1. Recibe comando
2. Identifica KEK en slot 5
3. Descifra llave DUKPT con KEK
4. Valida KCV
5. Almacena como IPEK o BDK según tipo
6. Asocia con KSN
7. Listo para derivar llaves de trabajo

---

## 3. VALIDACIONES DE INTEGRIDAD

### 3.1 Validación de Llaves Antes de Inyección

**Función**: `validateKeyIntegrity(key: InjectedKeyEntity)`

#### 3.1.1 Validación de Datos

```kotlin
// 1. Verificar que tenga datos
if (key.keyData.isEmpty()) {
    throw Exception("Llave con KCV ${key.kcv} no tiene datos")
}

// 2. Verificar formato hexadecimal
if (!key.keyData.matches(Regex("^[0-9A-Fa-f]+$"))) {
    throw Exception("Datos de llave no son hexadecimales válidos")
}

// 3. Verificar KCV
if (key.kcv.isEmpty()) {
    throw Exception("Llave no tiene KCV válido")
}
```

#### 3.1.2 Validación de Longitud

**Para 3DES**:
```kotlin
val validLengths3DES = listOf(32, 48) // 16 o 24 bytes
if (!validLengths3DES.contains(keyData.length)) {
    throw Exception("Longitud inválida para 3DES: ${keyData.length/2} bytes")
}
```

**Para AES**:
```kotlin
val validLengthsAES = listOf(32, 48, 64) // 16, 24 o 32 bytes
if (!validLengthsAES.contains(keyData.length)) {
    throw Exception("Longitud inválida para AES: ${keyData.length/2} bytes")
}
```

**Para DUKPT**:
```kotlin
// DUKPT 3DES: 16 o 24 bytes
// DUKPT AES: 16, 24 o 32 bytes
val validDukpt = if (is3DES) listOf(32, 48) else listOf(32, 48, 64)
if (!validDukpt.contains(keyData.length)) {
    throw Exception("Longitud inválida para DUKPT")
}
```

#### 3.1.3 Validación de KSN (para DUKPT)

```kotlin
if (isDukptKeyType(config.keyType)) {
    val ksn = config.ksn
    
    if (ksn.isEmpty()) {
        // Generar automáticamente
        generatedKsn = generateKSN(key.kcv, config.slot)
    } else if (ksn.length != 20) {
        throw Exception("KSN inválido: debe tener 20 caracteres")
    } else if (!ksn.matches(Regex("^[0-9A-Fa-f]{20}$"))) {
        throw Exception("KSN debe ser hexadecimal")
    }
}
```

### 3.2 Validación de Respuestas

#### 3.2.1 Códigos de Error Futurex

| Código | Descripción | Acción Recomendada |
|--------|-------------|-------------------|
| 0x00 | Successful | Continuar |
| 0x01 | Invalid command | Verificar comando |
| 0x02 | Invalid version | Usar versión "01" |
| 0x03 | Invalid length | Verificar longitud de llave |
| 0x04 | Unsupported characters | Validar caracteres ASCII |
| 0x05 | Device is busy | Reintentar después de delay |
| 0x06 | Not in injection mode | Entrar en modo inyección |
| 0x07 | Device is in tamper | Resolver tamper físico |
| 0x08 | Bad LRC | Recalcular LRC |
| 0x09 | Duplicate key | Eliminar llave existente |
| 0x0A | Duplicate KSN | Usar KSN diferente |
| 0x0B | Key deletion failed | Verificar slot |
| 0x0C | Invalid key slot | Usar slot válido (0-99) |
| 0x0D | Invalid KTK slot | Verificar slot de KEK |
| 0x0E | Missing KTK | Exportar KEK primero |
| 0x0F | Key slot not empty | Eliminar llave existente |
| 0x10 | Invalid key type | Verificar mapeo de tipos |
| 0x11 | Invalid key encryption type | Usar "00" o "01" |
| 0x12 | Invalid key checksum | Verificar KCV |
| 0x13 | Invalid KTK checksum | Verificar KCV de KEK |
| 0x14 | Invalid KSN | Validar formato KSN |
| 0x15 | Invalid key length | Ajustar longitud |
| 0x16 | Invalid KTK length | Verificar longitud KEK |
| 0x17 | Invalid TR-31 version | No aplicable |
| 0x18 | Invalid key usage | Verificar tipo de llave |
| 0x19 | Invalid algorithm | Verificar algoritmo |
| 0x1A | Invalid mode of use | Verificar modo |
| 0x1B | MAC verification failed | Recalcular MAC |
| 0x1C | Decryption failed | Verificar KEK |

#### 3.2.2 Validación de KCV en Respuesta

```kotlin
// Extraer KCV de respuesta
val responseKcv = response.keyChecksum

// Comparar con KCV esperado
if (responseKcv != expectedKcv) {
    throw Exception(
        "KCV no coincide - " +
        "Esperado: $expectedKcv, " +
        "Recibido: $responseKcv"
    )
}
```

### 3.3 Validación Post-Inyección

**En el SubPOS**:

1. **Verificar Escritura en PED**:
   ```kotlin
   val writeResult = pedController.writeKeyPlain(...)
   if (!writeResult) {
       throw PedException("Fallo al escribir en PED")
   }
   ```

2. **Leer KCV del PED**:
   ```kotlin
   val keyInfo = pedController.getKeyInfo(slot, keyType)
   val pedKcv = keyInfo?.kcv
   ```

3. **Comparar KCVs**:
   ```kotlin
   if (pedKcv != expectedKcv) {
       throw Exception("KCV del PED no coincide")
   }
   ```

4. **Registrar en BD**:
   ```kotlin
   injectedKeyRepository.recordKeyInjectionWithData(
       keySlot = slot,
       keyType = keyType.name,
       keyAlgorithm = algorithm.name,
       kcv = kcv,
       keyData = keyData,
       status = "SUCCESSFUL"
   )
   ```

---

## 4. SEGURIDAD Y MEJORES PRÁCTICAS

### 4.1 Almacenamiento Seguro

#### 4.1.1 Android KeyStore

**Características**:
- Protección a nivel hardware (TEE - Trusted Execution Environment)
- Llaves nunca expuestas fuera del módulo seguro
- Operaciones criptográficas dentro del KeyStore
- Protección contra extracción

**Uso en el Sistema**:
```kotlin
// Almacenar llave maestra
KeyStoreManager.storeMasterKey(
    alias = "master_key_slot_0",
    keyBytes = masterKeyBytes
)

// Recuperar para uso
val masterKey = KeyStoreManager.retrieveMasterKey("master_key_slot_0")

// Cifrar datos con llave en KeyStore
val encrypted = KeyStoreManager.encryptData(
    alias = "master_key_slot_0",
    data = plainData
)
```

#### 4.1.2 Base de Datos

**Llaves Operacionales**:
- Almacenadas en Room Database
- Datos completos en hexadecimal
- KCV para identificación rápida
- Timestamp para auditoría

**Campos Sensibles**:
- `keyData`: Datos completos de la llave
- `kcv`: Key Check Value
- `isKEK`: Flag de KEK
- `status`: ACTIVE, EXPORTED, INACTIVE

**Recomendación**:
- Habilitar cifrado de BD con SQLCipher (opcional)
- Backup cifrado de BD
- Rotación periódica de KEKs

### 4.2 Transmisión Segura

#### 4.2.1 Siempre Usar KEK en Producción

**Configuración Recomendada**:
```
1. Generar KEK mediante ceremonia (3+ custodios)
2. Marcar como KEK en sistema
3. Exportar a SubPOS en primera sesión
4. Todas las llaves subsecuentes cifradas con KEK
5. Rotar KEK periódicamente (ej: cada 3 meses)
```

#### 4.2.2 Validación de Canal

**Cable USB**:
- Verificar cable de calidad
- Evitar cables baratos o dañados
- Detección multi-método antes de enviar

**Entorno**:
- Evitar inyección en lugares públicos
- Sala segura para ceremonias
- Registro de video (opcional)

### 4.3 Auditoría y Trazabilidad

#### 4.3.1 Logs de Operaciones

**Nivel de Detalle**:
```
[INFO]  Iniciando ceremonia de llaves - 3 custodios
[DEBUG] Componente 1 recibido (16 bytes)
[DEBUG] Componente 2 recibido (16 bytes)
[DEBUG] Componente 3 recibido (16 bytes)
[INFO]  Llave generada exitosamente - KCV: A1B2C3
[INFO]  Almacenada en KeyStore con alias: ceremony_key_20251014_143022
[INFO]  Registrada en BD - ID: 123, Slot: 0
```

**Logs de Inyección**:
```
[INFO]  Iniciando inyección - Perfil: Transaccional
[INFO]  KEK activa: KCV A1B2C3 (estado: EXPORTED)
[DEBUG] Procesando llave 1/4 - PIN (slot 1)
[DEBUG] Llave cifrada con KEK
[DEBUG] Comando enviado: 65 bytes
[INFO]  Respuesta recibida: Código 00 (éxito)
[INFO]  Llave 1/4 inyectada exitosamente
...
```

#### 4.3.2 Registro en Base de Datos

**Tabla injected_keys**:
- Cada inyección registrada con timestamp
- Estado de resultado (SUCCESSFUL/FAILED)
- Información completa de llave (datos + KCV)
- Flag isKEK para identificación rápida

**Consultas de Auditoría**:
```sql
-- Llaves inyectadas hoy
SELECT * FROM injected_keys 
WHERE DATE(injectionTimestamp/1000, 'unixepoch') = DATE('now')

-- KEKs exportadas
SELECT * FROM injected_keys 
WHERE isKEK = 1 AND status = 'EXPORTED'

-- Llaves por tipo
SELECT keyType, COUNT(*) FROM injected_keys 
GROUP BY keyType
```

### 4.4 Rotación de Llaves

#### 4.4.1 KEK Rotation

**Frecuencia Recomendada**: Cada 3-6 meses

**Proceso**:
1. Generar nueva KEK mediante ceremonia
2. Sistema marca KEK anterior como INACTIVE
3. Nueva KEK marcada como ACTIVE
4. En próxima sesión:
   - Exportar nueva KEK a SubPOS
   - Marcar como EXPORTED
   - Todas las llaves cifradas con nueva KEK

#### 4.4.2 Working Keys Rotation

**Frecuencia**: Según política de seguridad (mensual/trimestral)

**Proceso**:
1. Generar nuevas llaves operacionales
2. Inyectar en slots alternativos
3. Activar nuevas llaves en aplicación
4. Eliminar llaves antiguas
5. Verificar funcionamiento

---

## 5. CASOS DE USO COMUNES

### 5.1 Inicialización de Nuevo Terminal

**Escenario**: Terminal POS nuevo sin llaves

**Proceso**:
1. **Generar KEK** (una vez):
   - Ceremonia con 3 custodios
   - Algoritmo: AES-256
   - Marcar como KEK
   - Slot: 0

2. **Generar Llaves Operacionales**:
   - PIN Key (3DES, slot 1)
   - MAC Key (3DES, slot 2)
   - Data Key (AES-128, slot 3)

3. **Crear Perfil "Transaccional"**:
   - Configurar 3 llaves
   - Activar cifrado KEK
   - Seleccionar KEK generada

4. **Conectar SubPOS y Inyectar**:
   - Exportar KEK (primera vez)
   - Inyectar llaves cifradas
   - Validar todas las operaciones

### 5.2 Inyección DUKPT para E-Commerce

**Escenario**: Terminal para transacciones online con DUKPT

**Llaves Requeridas**:
1. **DUKPT 3DES BDK** (slot 4):
   - Longitud: 16 bytes
   - KSN: F876543210000000000A
   - Cifrada con KEK

2. **DUKPT AES BDK** (slot 5):
   - Longitud: 32 bytes (AES-256)
   - KSN: F876543210000000000B
   - Cifrada con KEK

**Proceso**:
1. Generar BDKs en HSM externo (fuera del sistema)
2. Importar BDKs al Injector
3. Crear perfil "E-Commerce DUKPT"
4. Configurar KSNs iniciales
5. Inyectar en terminal

### 5.3 Reemplazo de Llaves Comprometidas

**Escenario**: Sospecha de compromiso de llave PIN

**Proceso Rápido**:
1. **Eliminar Llave Comprometida**:
   - En SubPOS: Eliminar llave específica (slot 1)

2. **Generar Nueva PIN Key**:
   - Ceremonia de llaves
   - Mismo algoritmo que anterior

3. **Inyectar Inmediatamente**:
   - Usar perfil existente
   - Actualizar configuración de slot 1
   - Inyectar nueva llave

4. **Verificar**:
   - Realizar transacción de prueba
   - Validar PIN con nueva llave

---

## 6. CONCLUSIÓN

El sistema de llaves criptográficas soporta:

✅ **Múltiples Tipos de Llaves**: Master, Working (PIN/MAC/Data), DUKPT, RSA  
✅ **Múltiples Algoritmos**: 3DES, AES (128/192/256), SM4  
✅ **Generación Segura**: Ceremonia de llaves con división de secretos  
✅ **Validación Robusta**: KCV, validaciones de longitud, formato, integridad  
✅ **Cifrado de Transmisión**: KEK con 3DES ECB  
✅ **Almacenamiento Seguro**: Android KeyStore + Base de datos  
✅ **Trazabilidad Completa**: Logs detallados + registros de auditoría  

Esta implementación cumple con los estándares de seguridad de la industria POS y permite una gestión completa del ciclo de vida de llaves criptográficas.

---

**Siguiente Documento**: [Parte 4: Perfiles y Configuración](DOCUMENTACION_04_PERFILES_CONFIGURACION.md)


