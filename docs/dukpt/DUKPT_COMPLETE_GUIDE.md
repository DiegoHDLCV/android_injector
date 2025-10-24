# Guía Completa: DUKPT desde Generación hasta Inyección

## Tabla de Contenidos
1. [Conceptos Básicos](#conceptos-básicos)
2. [Generación de IPEK](#generación-de-ipek)
3. [Derivación de Claves de Sesión](#derivación-de-claves-de-sesión)
4. [Importar Llaves en la Aplicación](#importar-llaves-en-la-aplicación)
5. [Crear un Perfil de Inyección](#crear-un-perfil-de-inyección)
6. [Ejecutar la Inyección](#ejecutar-la-inyección)
7. [Verificación y Troubleshooting](#verificación-y-troubleshooting)

---

## Conceptos Básicos

### ¿Qué es DUKPT?

**DUKPT = Derived Unique Key Per Transaction**

Es un estándar de seguridad que genera claves únicas para cada transacción a partir de una llave maestra inicial (IPEK).

**Flujo básico:**
```
IPEK (Llave Maestra)
      ↓
    + KSN (Key Serial Number)
      ↓
  [Algoritmo de Derivación DUKPT]
      ↓
  Claves de Sesión Únicas
  (Una por cada transacción)
```

### Estándar: ANSI X9.24-1

Se implementa para:
- **Sistemas de Pago (POS)**
- **Cajeros Automáticos (ATM)**
- **Lectores de Tarjetas**
- **Dispositivos de Encriptación**

### Algoritmos Soportados en NewPOS

| Algoritmo | IPEK | Derivación | Uso |
|-----------|------|-----------|-----|
| DUKPT 3DES 2TDEA | 16 bytes | 3DES | Legacy, aún soportado |
| DUKPT 3DES 3TDEA | 16 bytes | 3DES | Legacy, recomendado vs 2TDEA |
| DUKPT AES-128 | 16 bytes | AES | Moderno, recomendado |
| DUKPT AES-192 | 24 bytes | AES | Mayor seguridad |
| DUKPT AES-256 | 32 bytes | AES | Máxima seguridad |

---

## Generación de IPEK

### Opción 1: Usar el Script de Generación (Recomendado)

El proyecto incluye un script Python que genera IPEKs de prueba:

```bash
cd /Users/diegoherreradelacalle/StudioProjects/android_injector
python3 import_dukpt_test_keys.py
```

**Salida:**
```
✅ Archivo de llaves generado: test_keys_dukpt.json

📊 Llaves incluidas:
  - KEK_STORAGE (AES-256)
    KCV: 112A8B

  - DUKPT_IPEK (AES-128)
    KCV: 072043

  - DUKPT_IPEK (AES-192)
    KCV: 5D614B

  - DUKPT_IPEK (AES-256)
    KCV: AB1234

  - DUKPT_IPEK (DES_DOUBLE)
    KCV: 3F8D42

  - DUKPT_IPEK (DES_TRIPLE)
    KCV: 7B5E9C
```

**Archivo generado: `test_keys_dukpt.json`**

```json
{
  "generated": "2025-10-24T09:03:46.519142",
  "description": "Llaves de prueba DUKPT para inyección",
  "totalKeys": 6,
  "keys": [
    {
      "keyType": "KEK_STORAGE",
      "algorithm": "AES-256",
      "keyHex": "E14007267311EBDA872B46AF9B1A086A...",
      "kcv": "112A8B",
      "bytes": 32
    },
    {
      "keyType": "DUKPT_IPEK",
      "algorithm": "AES-128",
      "keyHex": "12101FFF4ED412459F4E727CC3A4895A",
      "kcv": "072043",
      "bytes": 16
    },
    // ... más llaves
  ]
}
```

### Opción 2: Generar IPEKs Manualmente

Si necesitas generar una IPEK específica:

**Requisitos:**
- Generador de números aleatorios criptográfico
- Implementación del algoritmo elegido (AES o 3DES)

**Pasos:**
1. **Generar 16/24/32 bytes aleatorios** (según algoritmo)
2. **Calcular el KCV (Key Check Value):**
   - Para AES: Encriptar bloque de ceros → primeros 4 caracteres hex
   - Para 3DES: Encriptar bloque de ceros → primeros 4 caracteres hex
3. **Registrar la IPEK y su KCV**

**Ejemplo Python:**
```python
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend
import os

# Generar IPEK aleatoria de 16 bytes (AES-128)
ipek_bytes = os.urandom(16)
print(f"IPEK: {ipek_bytes.hex().upper()}")

# Calcular KCV (encriptar bloque de ceros)
plain_block = b'\x00' * 16
cipher = Cipher(
    algorithms.AES(ipek_bytes),
    modes.ECB(),
    backend=default_backend()
)
encryptor = cipher.encryptor()
kcv_full = encryptor.update(plain_block) + encryptor.finalize()
kcv = kcv_full[:4].hex().upper()
print(f"KCV: {kcv}")
```

---

## Derivación de Claves de Sesión

### Concepto: KSN (Key Serial Number)

**KSN** es un número de serie único que se incrementa con cada transacción:

- **Longitud:** 10 bytes en formato Futurex / 12 bytes en NewPOS
- **Estructura:** TDES (Transaction Device Encryption Standard)
- **Incremento:** Automático tras cada operación criptográfica

**Formato KSN típico:**
```
FFFF 9876 5432 1000 0000
└─┬─┘ └─────────┬─────┘ └──┬──┘
  │        Device ID     Counter/Sequence
Device
```

### Proceso de Derivación DUKPT

#### Para AES:

```
Entrada:
  - IPEK: 16/24/32 bytes (según AES-128/192/256)
  - KSN: 10 bytes (Futurex) → 12 bytes (NewPOS con padding)

Proceso:
  1. Aplicar KSN a través del algoritmo de derivación DUKPT
  2. Generar claves de sesión derivadas
  3. Cada transacción usa una clave única

Salida:
  - Clave de sesión derivada (mismo tamaño que IPEK)
```

#### Para 3DES (2TDEA/3TDEA):

```
Entrada:
  - IPEK: 16 bytes (Nota: ambos usan 16 bytes para DUKPT)
  - KSN: 10 bytes (sin padding para 3DES)

Proceso:
  1. Aplicar KSN a través del algoritmo de derivación DUKPT 3DES
  2. Generar claves de sesión derivadas
  3. Cada transacción usa una clave única

Salida:
  - Clave de sesión derivada (16 bytes)
```

### Implementación en NewPOS

El dispositivo PED de NewPOS realiza automáticamente la derivación:

```kotlin
// En NewposPedController.kt línea 506-551
override suspend fun createDukptAESKey(
    keyIndex: Int,
    keyAlgorithm: GenericKeyAlgorithm,
    ipekBytes: ByteArray,
    ksnBytes: ByteArray,
    kcvBytes: ByteArray?
): Boolean {
    // NewPOS maneja la derivación internamente
    val result = when (keyAlgorithm) {
        GenericKeyAlgorithm.DES_DOUBLE, GenericKeyAlgorithm.DES_TRIPLE -> {
            pedInstance.createDukptKey(keyIndex, ipekBytes, ksnBytes)
        }
        GenericKeyAlgorithm.AES_128, GenericKeyAlgorithm.AES_192, GenericKeyAlgorithm.AES_256 -> {
            val npDukptType = mapToNewposDukptType(keyAlgorithm)
            pedInstance.createDukptAESKey(keyIndex, npDukptType, ipekBytes, ksnBytes)
        }
        // ...
    }
}
```

---

## Importar Llaves en la Aplicación

### Paso 1: Preparar el Archivo de Llaves

Tienes dos opciones:

#### a) Usar las Llaves de Prueba Generadas

```bash
# Las llaves ya están en:
test_keys_dukpt.json
```

#### b) Usar Llaves Propias

Estructura requerida:
```json
{
  "generated": "ISO-8601 timestamp",
  "description": "Descripción",
  "totalKeys": número,
  "keys": [
    {
      "keyType": "KEK_STORAGE",  // Llave maestra obligatoria
      "algorithm": "AES-256",
      "keyHex": "hexadecimal",
      "kcv": "primeros 4 caracteres hex del valor de comprobación",
      "bytes": 32
    },
    {
      "keyType": "DUKPT_IPEK",   // Llave IPEK
      "algorithm": "AES-128|AES-192|AES-256|DES_DOUBLE|DES_TRIPLE",
      "keyHex": "hexadecimal",
      "kcv": "valor de comprobación",
      "bytes": 16  // o 24 para AES-192, 32 para AES-256
    }
  ]
}
```

### Paso 2: Importar en la Aplicación Injector

**En el dispositivo o emulador:**

1. **Abre la app Injector**
   - ícono de llave en la pantalla principal

2. **Ve a: Key Vault → Import Keys**
   - Menú superior derecho

3. **Selecciona el archivo `test_keys_dukpt.json`**
   - Se importarán automáticamente

4. **Verifica la importación**
   - Deberías ver 6 llaves en la lista
   - KEK_STORAGE en rojo (obligatoria)
   - 5 llaves DUKPT_IPEK en azul

### Paso 3: Anotar los KCVs

Necesitarás estos valores para crear el perfil:

```
KEK_STORAGE:
  kcv: 112A8B

DUKPT IPEK (AES-128):
  kcv: 072043
  ksn: FFFF9876543210000000

DUKPT IPEK (AES-192):
  kcv: 5D614B
  ksn: FFFF9876543210000001

DUKPT IPEK (AES-256):
  kcv: AB1234
  ksn: FFFF9876543210000002

DUKPT IPEK (2TDEA):
  kcv: 3F8D42
  ksn: FFFF9876543210000001

DUKPT IPEK (3TDEA):
  kcv: 7B5E9C
  ksn: FFFF9876543210000002
```

---

## Crear un Perfil de Inyección

### Conceptos

Un **perfil** define qué llaves se inyectarán, en qué slots y con qué configuración.

**Slots disponibles:** 1-10 (normalmente)

### Opción 1: Usar Perfiles Predefinidos

El proyecto incluye 4 perfiles listos:

```bash
dukpt_test_profile.json          # Simple AES-128
dukpt_2tdea_profile.json         # Simple 2TDEA
dukpt_3tdea_profile.json         # Simple 3TDEA
dukpt_multikey_profile.json      # Todos los algoritmos (5 slots)
```

### Opción 2: Crear un Perfil Personalizado

**Estructura:**
```json
{
  "name": "Mi Perfil DUKPT",
  "description": "Descripción de qué contiene",
  "applicationType": "Retail",
  "useKEK": false,
  "selectedKEKKcv": "",
  "keyConfigurations": [
    {
      "usage": "DUKPT",
      "keyType": "DUKPT Initial Key (IPEK)",
      "slot": "01",
      "selectedKey": "072043",    // KCV de la IPEK
      "injectionMethod": "auto",
      "ksn": "FFFF9876543210000000"
    }
  ]
}
```

### Paso a Paso: Crear Perfil Personalizado

1. **Abre el archivo `dukpt_multikey_profile.json`**
   - Usa como referencia

2. **Modifica los valores:**
   ```json
   "name": "Mi Configuración DUKPT",
   "description": "Perfil para pruebas en mi dispositivo"
   ```

3. **Configura los slots:**
   - Slot 01: Tu IPEK AES-128 (kcv: 072043)
   - Slot 02: Tu IPEK AES-256 (kcv: AB1234)
   - etc.

4. **Guarda como: `mi_perfil_dukpt.json`**

5. **Verifica la estructura:**
   - KCVs deben coincidir con las llaves importadas
   - KSNs deben ser válidos (10 bytes = 20 caracteres hex)
   - Slots no deben repetirse

---

## Ejecutar la Inyección

### Requisito Previo

- **USB conectado:** KeyReceiver conectado al dispositivo POS/PED
- **Llaves importadas:** En la app Injector
- **Perfil listo:** Creado según paso anterior

### Proceso Paso a Paso

#### 1. Abrir KeyReceiver

```
Dispositivo USB PED ─usb─> App KeyReceiver
```

En la pantalla verás:
```
Estado: Conectado en protocolo FUTUREX
Puerto: /dev/ttyUSB0 (o similar)
```

#### 2. Abrir Injector

En la app Injector en el dispositivo host:

```
Injector → Profiles → Import Profile
```

Selecciona: `dukpt_multikey_profile.json`

#### 3. Enviar Comando de Inyección

En Injector:
```
Profiles → [Tu Perfil] → Inject All Keys
```

O selecciona un slot específico:
```
Key Vault → [Llave] → Inject to Slot X
```

#### 4. Monitorear en KeyReceiver

En KeyReceiver verás los logs:

```
08:57:57.563 I Bytes recibidos: 82
08:57:57.563 I Parser configurado: FuturexMessageParser
08:57:57.563 I Protocolo actual: FUTUREX
08:57:57.573 I === PARSEANDO COMANDO DE INYECCIÓN '02' ===
08:57:57.574 I    - Versión: '01'
08:57:57.574 I    - KeySlot: '01' (1)
08:57:57.574 I    - KeyType: '03'
08:57:57.574 I    - EncryptionType: '05'
08:57:57.574 I    - KeyAlgorithm: '02'
08:57:57.574 I    - KSN: 'FFFF9876543210000000'
08:57:57.583 I Expected KCV: 072043
08:57:57.711 I ✓ DUKPT key created successfully in slot 1
08:57:57.712 I ✓ IPEK DUKPT inyectada exitosamente en slot 1
```

#### 5. Verificar Resultado

**En KeyReceiver:**
- ✅ Log dice "SUCCESSFUL"
- ✅ No hay excepciones

**En Injector:**
- Ve a: Key Vault → Injected Keys
- Deberías ver la llave en el slot correspondiente
- Estado: SUCCESSFUL
- KCV coincide

### Tipos de EncryptionType

Hay varios modos de inyección:

| EncryptionType | Nombre | Descripción |
|---|---|---|
| 00 | Plaintext | Llave en claro (solo testing) |
| 01 | Encrypted by KTK | Cifrada con Transport Key |
| 02 | Double-length | (Legado) |
| 05 | DUKPT Plaintext | DUKPT en claro (solo testing) |

**Recomendado para producción:** EncryptionType 01 (con KTK)

---

## Verificación y Troubleshooting

### Verificaciones Exitosas

```
✅ KeyReceiver muestra: "✓ IPEK DUKPT inyectada exitosamente en slot X"
✅ Log muestra: "Status: SUCCESSFUL"
✅ Injector muestra la llave en "Injected Keys"
✅ KCV coincide con lo esperado
```

### Errores Comunes

#### Error 2012 (KEY_LEN_ERR)

**Causa:** Longitud de IPEK incorrecta

**Solución:**
- Verifica el tamaño de la IPEK según algoritmo:
  ```
  AES-128:   16 bytes (32 hex chars)
  AES-192:   24 bytes (48 hex chars)
  AES-256:   32 bytes (64 hex chars)
  3DES:      16 bytes (32 hex chars) ← NO 24 bytes
  ```

**En KeyReceiver:**
```
KeyAlgorithm: '05'  // AES-256
KeyLength: '020'    // 020 → 32 bytes ✅
```

#### Error 2004 (KEY_INDEX_ERR)

**Causa:** Slot inválido (fuera de rango o ya usado)

**Solución:**
- Usa slots 1-10
- Elimina primero si ya existe: KeyReceiver → Delete Key → Slot X

#### Error 2001 (KEY_FULL)

**Causa:** Almacenamiento de llaves lleno

**Solución:**
```
KeyReceiver → Delete All Keys
(o delete selectivos)
```

#### Error "KSN inválido"

**Causa:** KSN está vacío o mal formado

**Solución:**
- KSN debe ser: 20 caracteres hex (10 bytes)
- Ejemplo válido: `FFFF9876543210000000`
- NO: `0000000000000000000` (sin FFFF al inicio)

### Verificar KSN Después de Inyección

Después de inyectar, el PED almacena el KSN y lo incrementa automáticamente.

Para ver el KSN actual:
```
KeyReceiver → Show DUKPT Info → Slot X
```

Ejemplo de salida:
```
Slot 1 - AES-128:
  KSN: FFFF9876543210000000
  Key System: DUKPT_AES
```

### Logs Detallados

En KeyReceiver, habilita logs verbosos:

```
MainViewModel → Tag: "MainViewModel"
Level: VERBOSE (V)
```

Filtra por:
- `FuturexMessageParser` - Parsing de comandos
- `NewposPedController` - Operaciones PED
- `MainViewModel` - Flujo general

---

## Ejemplo Completo: Paso a Paso

### Escenario: Inyectar AES-128 DUKPT en Slot 1

#### Paso 1: Generar Llaves (5 min)

```bash
python3 import_dukpt_test_keys.py
```

Output:
```
✅ Archivo de llaves generado: test_keys_dukpt.json
📊 Llaves incluidas:
  - DUKPT_IPEK (AES-128)
    KCV: 072043
```

#### Paso 2: Importar en Injector (2 min)

1. Abre Injector
2. Key Vault → Import Keys
3. Selecciona: test_keys_dukpt.json
4. Verifica: 6 llaves importadas ✅

#### Paso 3: Crear Perfil (3 min)

Archivo: `mi_perfil_simple.json`

```json
{
  "name": "AES-128 DUKPT Simple",
  "description": "Inyección de AES-128 en slot 1",
  "applicationType": "Retail",
  "useKEK": false,
  "selectedKEKKcv": "",
  "keyConfigurations": [
    {
      "usage": "DUKPT",
      "keyType": "DUKPT Initial Key (IPEK)",
      "slot": "01",
      "selectedKey": "072043",
      "injectionMethod": "auto",
      "ksn": "FFFF9876543210000000"
    }
  ]
}
```

#### Paso 4: Importar Perfil (1 min)

1. Abre Injector
2. Profiles → Import Profile
3. Selecciona: mi_perfil_simple.json
4. Verifica: Perfil cargado ✅

#### Paso 5: Conectar PED (1 min)

1. Conecta cable USB PED ↔ Dispositivo
2. Abre KeyReceiver
3. Verifica: "Conectado en protocolo FUTUREX" ✅

#### Paso 6: Ejecutar Inyección (30 seg)

1. En Injector: Profiles → mi_perfil_simple
2. Click: "Inject All Keys"
3. Espera confirmación

#### Paso 7: Verificar (30 seg)

En KeyReceiver:
```
✓ IPEK DUKPT inyectada exitosamente en slot 1
Status: SUCCESSFUL
```

En Injector:
```
Key Vault → Injected Keys → Slot 1
Llave: DUKPT_INITIAL_KEY
Status: SUCCESSFUL
KCV: 072043 ✅
```

**¡Listo! Inyección completada en ~12 minutos**

---

## Referencia Rápida

### Comandos Python Útiles

```bash
# Generar llaves
python3 import_dukpt_test_keys.py

# Generar perfil
python3 import_dukpt_profile.py
```

### Estructura de Archivos

```
android_injector/
├── test_keys_dukpt.json              # Llaves generadas
├── dukpt_test_profile.json           # Perfil simple
├── dukpt_multikey_profile.json       # Perfil multi
├── DUKPT_COMPLETE_GUIDE.md          # Esta guía
├── DUKPT_3DES_SUMMARY.md            # Detalles técnicos
└── import_dukpt_test_keys.py        # Script generador
```

### Valores por Defecto

| Parámetro | Valor |
|-----------|-------|
| Slots | 1-10 |
| KEK Obligatoria | Sí |
| EncryptionType Recomendado | 05 (testing) o 01 (producción) |
| KSN Inicial | FFFF9876543210000000 |

---

## Próximos Pasos

1. **Testing:**
   - Genera llaves
   - Importa en Injector
   - Inyecta en todos los slots
   - Verifica logs

2. **Producción:**
   - Cambia EncryptionType a 01 (encrypted)
   - Usa llaves reales (no generadas)
   - Aumenta seguridad KSN
   - Implementa auditoría

3. **Troubleshooting:**
   - Revisa logs en KeyReceiver
   - Verifica KCVs coincidan
   - Valida formato de datos
   - Consulta DUKPT_3DES_SUMMARY.md

---

**Última actualización:** 2025-10-24
**Versión:** 1.0
**Estatus:** ✅ Completo
