# Datos DUKPT

Llaves y perfiles de inyección para DUKPT.

## Estructura

```
data/dukpt/
├── README.md (este archivo)
├── keys/
│   └── test_keys_dukpt.json    Llaves de prueba (6 llaves)
└── profiles/
    ├── dukpt_test_profile.json        AES-128 simple
    ├── dukpt_2tdea_profile.json       2TDEA simple
    ├── dukpt_3tdea_profile.json       3TDEA simple
    └── dukpt_multikey_profile.json    Todos (5 slots)
```

## Archivos

### test_keys_dukpt.json

**Contiene:** 6 llaves DUKPT listas para importar

**Estructura:**
```json
{
  "generated": "ISO timestamp",
  "description": "Llaves de prueba DUKPT...",
  "totalKeys": 6,
  "keys": [
    {
      "keyType": "KEK_STORAGE",
      "algorithm": "AES-256",
      "keyHex": "...",
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

**Llaves incluidas:**

| # | Tipo | Algoritmo | KCV | Bytes | Uso |
|---|------|-----------|-----|-------|-----|
| 1 | KEK_STORAGE | AES-256 | 112A8B | 32 | Llave maestra (obligatoria) |
| 2 | DUKPT_IPEK | AES-128 | 072043 | 16 | Testing AES moderno |
| 3 | DUKPT_IPEK | AES-192 | 5D614B | 24 | Testing AES + seguro |
| 4 | DUKPT_IPEK | AES-256 | AB1234 | 32 | Testing AES máximo |
| 5 | DUKPT_IPEK | 2TDEA | 3F8D42 | 16 | Testing 3DES legacy |
| 6 | DUKPT_IPEK | 3TDEA | 7B5E9C | 16 | Testing 3DES (16 bytes) |

**Cómo usar:**
```
En Injector app:
Key Vault → Import Keys → test_keys_dukpt.json
```

### Perfiles de Inyección

Cada perfil define qué se inyecta, dónde y cómo.

#### dukpt_test_profile.json

**Descripción:** Perfil simple para AES-128

**Contenido:**
```json
{
  "name": "DUKPT Test - AES-128",
  "description": "Perfil simple con AES-128 para testing",
  "applicationType": "Retail",
  "useKEK": false,
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

**Slots:** 1 (solo AES-128)
**KCV:** 072043
**KSN:** FFFF9876543210000000

**Ideal para:** Primera prueba

#### dukpt_2tdea_profile.json

**Descripción:** Perfil simple para 2TDEA

**Contenido:**
```json
{
  "name": "DUKPT Test - 2TDEA",
  "description": "Perfil simple con 2TDEA para testing",
  "applicationType": "Retail",
  "useKEK": false,
  "keyConfigurations": [
    {
      "usage": "DUKPT",
      "keyType": "DUKPT Initial Key (IPEK)",
      "slot": "01",
      "selectedKey": "3F8D42",
      "injectionMethod": "auto",
      "ksn": "FFFF9876543210000001"
    }
  ]
}
```

**Slots:** 1 (solo 2TDEA)
**KCV:** 3F8D42
**KSN:** FFFF9876543210000001

**Ideal para:** Testing 3DES legacy

#### dukpt_3tdea_profile.json

**Descripción:** Perfil simple para 3TDEA

**Contenido:**
```json
{
  "name": "DUKPT Test - 3TDEA",
  "description": "Perfil simple con 3TDEA para testing",
  "applicationType": "Retail",
  "useKEK": false,
  "keyConfigurations": [
    {
      "usage": "DUKPT",
      "keyType": "DUKPT Initial Key (IPEK)",
      "slot": "01",
      "selectedKey": "7B5E9C",
      "injectionMethod": "auto",
      "ksn": "FFFF9876543210000002"
    }
  ]
}
```

**Slots:** 1 (solo 3TDEA)
**KCV:** 7B5E9C
**KSN:** FFFF9876543210000002
**Nota:** Usa 16 bytes (no 24) según ANSI X9.24-1

**Ideal para:** Testing 3DES actual

#### dukpt_multikey_profile.json

**Descripción:** Perfil completo con todos los algoritmos

**Slots:**
1. AES-128 (KCV: 072043)
2. AES-192 (KCV: 5D614B)
3. AES-256 (KCV: AB1234)
4. 2TDEA (KCV: 3F8D42)
5. 3TDEA (KCV: 7B5E9C)

**Ideal para:** Prueba integral de todos los algoritmos

**Estructura:**
```json
{
  "name": "DUKPT Multi-Algorithm Test",
  "description": "Perfil con llaves DUKPT AES y 3DES",
  "keyConfigurations": [
    {
      "slot": "01",
      "selectedKey": "072043",  // AES-128
      "ksn": "FFFF9876543210000000"
    },
    {
      "slot": "02",
      "selectedKey": "5D614B",   // AES-192
      "ksn": "FFFF9876543210000001"
    },
    {
      "slot": "03",
      "selectedKey": "AB1234",   // AES-256
      "ksn": "FFFF9876543210000002"
    },
    {
      "slot": "04",
      "selectedKey": "3F8D42",   // 2TDEA
      "ksn": "FFFF9876543210000001"
    },
    {
      "slot": "05",
      "selectedKey": "7B5E9C",   // 3TDEA
      "ksn": "FFFF9876543210000002"
    }
  ]
}
```

## Valores de Referencia Rápida

### KCVs (Key Check Values)
```
AES-128:  072043
AES-192:  5D614B
AES-256:  AB1234
2TDEA:    3F8D42
3TDEA:    7B5E9C
KEK:      112A8B
```

### KSNs Iniciales (Key Serial Numbers)
```
AES-128:  FFFF9876543210000000
AES-192:  FFFF9876543210000001
AES-256:  FFFF9876543210000002
2TDEA:    FFFF9876543210000001
3TDEA:    FFFF9876543210000002
```

### Tamaños de IPEK (Importante)
```
AES-128:  16 bytes (32 hex chars)
AES-192:  24 bytes (48 hex chars)
AES-256:  32 bytes (64 hex chars)
2TDEA:    16 bytes (32 hex chars)
3TDEA:    16 bytes (32 hex chars) ← NO 24 bytes
```

## Cómo Usar

### 1. Importar Llaves

```
En Injector app:
1. Key Vault → Import Keys
2. Selecciona: data/dukpt/test_keys_dukpt.json
3. Espera importación
4. Verifica: 6 llaves en la lista
```

### 2. Importar Perfil

```
En Injector app:
1. Profiles → Import Profile
2. Selecciona un perfil:
   - dukpt_test_profile.json (recomendado para empezar)
   - o dukpt_multikey_profile.json (todos los algoritmos)
3. Espera importación
4. Verifica: Perfil en la lista
```

### 3. Inyectar

```
En Injector app:
1. Profiles → [Tu Perfil]
2. Click: "Inject All Keys"
3. Monitorea en KeyReceiver app
4. Espera: "SUCCESSFUL"
```

## Personalización

Para crear un perfil personalizado:

```bash
1. Copia dukpt_test_profile.json
2. Renómbralo: mi_perfil.json
3. Edita con editor de texto:
   - "name": Tu nombre
   - "slot": El slot donde inyectar (1-10)
   - "selectedKey": KCV de la llave (del test_keys_dukpt.json)
   - "ksn": KSN inicial (20 caracteres hex)
4. Importa en Injector
5. Inyecta
```

## Regenerar Archivos

Si necesitas actualizar los archivos:

```bash
# Regenerar llaves
python3 scripts/dukpt/import_dukpt_test_keys.py

# Regenerar perfiles
python3 scripts/dukpt/import_dukpt_profile.py

# Ambos
python3 scripts/dukpt/import_dukpt_test_keys.py && \
python3 scripts/dukpt/import_dukpt_profile.py
```

## Importante: 3TDEA = 16 Bytes

⚠️ **Nota especial para 3TDEA:**

- Tamaño correcto: **16 bytes** (no 24 bytes)
- Razón: ANSI X9.24-1 para DUKPT
- NewPOS rechaza con error 2012 si no se respeta
- Ver: `docs/dukpt/DUKPT_3DES_SUMMARY.md`

## Estructura de Archivos

```
data/dukpt/
├── README.md (este archivo)
├── keys/
│   └── test_keys_dukpt.json
└── profiles/
    ├── dukpt_test_profile.json
    ├── dukpt_2tdea_profile.json
    ├── dukpt_3tdea_profile.json
    └── dukpt_multikey_profile.json
```

## Véase También

- **Scripts:** `scripts/dukpt/` (generar estos archivos)
- **Documentación:** `docs/dukpt/` (guías y referencias)
- **Código:** `MainViewModel.kt` (validación)

---

**Última actualización:** 2025-10-24
**Estado:** ✅ Listo para usar
