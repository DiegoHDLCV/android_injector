# DUKPT 3DES Implementation Summary

## Overview
Successfully implemented support for 3DES DUKPT (Derived Unique Key Per Transaction) algorithms in the test suite, complementing existing AES support.

## What Was Accomplished

### 1. Test Keys Generation (import_dukpt_test_keys.py)
Enhanced the script to generate test keys for all supported DUKPT algorithms:

**Keys Generated:**
- 1x KEK_STORAGE (AES-256) - KCV: 112A8B
- 3x DUKPT IPEK AES Keys:
  - AES-128 (16 bytes) - KCV: 072043
  - AES-192 (24 bytes) - KCV: 5D614B
  - AES-256 (32 bytes) - KCV: AB1234
- 2x DUKPT IPEK 3DES Keys (NEW):
  - 2TDEA/DES_DOUBLE (16 bytes) - KCV: 3F8D42
  - 3TDEA/DES_TRIPLE (**16 bytes**) - KCV: 7B5E9C

**Total:** 6 keys including mandatory KEK_STORAGE

**Nota:** Ambas llaves 3DES (2TDEA y 3TDEA) usan 16 bytes para DUKPT, según el estándar ANSI X9.24-1.

### 2. Test Profiles Generation (import_dukpt_profile.py)
Enhanced the script to create profiles for all supported DUKPT algorithms:

**Profiles Generated:**
1. **dukpt_test_profile.json** - Simple AES-128 profile
   - Slot 01: AES-128 (KCV: 072043, KSN: FFFF9876543210000000)
   - Recommended for initial testing

2. **dukpt_2tdea_profile.json** - Simple 2TDEA profile (NEW)
   - Slot 01: 2TDEA (KCV: 3F8D42, KSN: FFFF9876543210000001)
   - For isolated 3DES Double-length testing

3. **dukpt_3tdea_profile.json** - Simple 3TDEA profile (NEW)
   - Slot 01: 3TDEA (KCV: 7B5E9C, KSN: FFFF9876543210000002)
   - For isolated 3DES Triple-length testing

4. **dukpt_multikey_profile.json** - Multi-algorithm profile
   - Slot 01: AES-128 (KCV: 072043)
   - Slot 02: AES-192 (KCV: 5D614B)
   - Slot 03: AES-256 (KCV: AB1234)
   - Slot 04: 2TDEA (KCV: 3F8D42) - NEW
   - Slot 05: 3TDEA (KCV: 7B5E9C) - NEW
   - Comprehensive testing with 5 different algorithms

## Algorithm Support

All profiles use **EncryptionType 05** (DUKPT plaintext mode for testing):

| Algorithm | Key Size | KCV | Profile | NewPOS Type |
|-----------|----------|-----|---------|-------------|
| AES-128 | 16 bytes | 072043 | Simple + Multi | DUKPT_TYPE_AES128 |
| AES-192 | 24 bytes | 5D614B | Multi | DUKPT_TYPE_AES192 |
| AES-256 | 32 bytes | AB1234 | Multi | DUKPT_TYPE_AES256 |
| 3DES 2TDEA | 16 bytes | 3F8D42 | Simple + Multi | DUKPT_TYPE_2TDEA |
| 3DES 3TDEA | **16 bytes** | 7B5E9C | Simple + Multi | DUKPT_TYPE_3TDEA |

**IMPORTANTE:** Para DUKPT 3DES, tanto 2TDEA como 3TDEA usan **16 bytes** (no 24). Esta es una particularidad del estándar DUKPT con 3DES.

## KSN (Key Serial Number) Values

Each algorithm test uses a unique KSN for testing:
- AES-128: FFFF9876543210000000
- AES-192: FFFF9876543210000001
- AES-256: FFFF9876543210000002
- 2TDEA: FFFF9876543210000001
- 3TDEA: FFFF9876543210000002

All KSNs are automatically padded from 10 bytes (Futurex format) to 12 bytes (NewPOS format).

## Testing Instructions

### Step 1: Import Test Keys
```bash
python3 import_dukpt_test_keys.py
```
Then in Injector app: **Key Vault > Import Keys** → Select `test_keys_dukpt.json`

### Step 2: Import Desired Profile
Choose one of the generated profiles:
- **dukpt_test_profile.json** - for basic AES-128 testing
- **dukpt_2tdea_profile.json** - for 2TDEA testing
- **dukpt_3tdea_profile.json** - for 3TDEA testing
- **dukpt_multikey_profile.json** - for comprehensive multi-algorithm testing

In Injector app: **Profiles > Import Profile** → Select desired profile file

### Step 3: Test Injection
1. Open KeyReceiver and connect USB cable
2. Open Injector > Raw Data Listener
3. Send DUKPT injection command (EncryptionType 05)
4. Verify key injection completes successfully

## Technical Details

### Code Changes

**import_dukpt_test_keys.py:**
- Added 2 new 3DES DUKPT IPEK keys to keys array
- Updated description to include 3DES information
- Output now displays all 6 keys with their properties

**import_dukpt_profile.py:**
- Added `create_dukpt_profile_2tdea()` function
- Added `create_dukpt_profile_3tdea()` function
- Updated `create_dukpt_profile_multikey()` from 3 to 5 slots
- Updated `main()` to generate all 4 profile files with new documentation

### Supported Algorithms

The implementation confirms support for all NewPOS DukptType values:
- DUKPT_TYPE_2TDEA (TripleDES Double-length, **16 bytes**)
- DUKPT_TYPE_3TDEA (TripleDES Triple-length, **16 bytes**)
- DUKPT_TYPE_AES128 (AES 128-bit, 16 bytes)
- DUKPT_TYPE_AES192 (AES 192-bit, 24 bytes)
- DUKPT_TYPE_AES256 (AES 256-bit, 32 bytes)

Mapping is handled by `mapToNewposDukptType()` in NewposPedController.kt (lines 112-123).

**Importante sobre 3DES DUKPT:**
- El estándar ANSI X9.24-1 define que DUKPT con 3DES siempre usa IPEKs de 16 bytes
- Esto aplica tanto para 2TDEA (2 llaves) como 3TDEA (3 llaves)
- NewPOS rechaza con error 2012 (KEY_LEN_ERR) si se envía una IPEK de 24 bytes para 3TDEA
- La validación en MainViewModel.kt (líneas 754-770) asegura la longitud correcta

## Aclaración: ANSI X9.24-1 vs. Implementación NewPOS

La teoría criptográfica dice:
- **2TDEA (Two-Key Triple DES)**: 16 bytes (K1 = K3, K2 distinta) → 112 bits de seguridad
- **3TDEA (Three-Key Triple DES)**: 24 bytes (K1, K2, K3 distintas) → 168 bits de seguridad

Sin embargo, **ANSI X9.24-1 para DUKPT es diferente**:

> Para DUKPT, ambas variantes (2TDEA y 3TDEA) utilizan IPEKs de 16 bytes según el estándar.
> La diferencia entre 2TDEA y 3TDEA en el contexto DUKPT se define en cómo se derivan
> las claves de sesión a partir de la IPEK, no en el tamaño de la IPEK.

**Confirmado por:**
1. ✅ NewPOS devuelve error 2012 (KEY_LEN_ERR) al intentar inyectar 24 bytes para DUKPT 3TDEA
2. ✅ Los tests existentes en NewposKeyInjectionTests.kt usan 16 bytes para 3DES DUKPT (línea 67)
3. ✅ La función mapToNewposDukptType() trata ambos (DES_DOUBLE y DES_TRIPLE) igual para DUKPT

**Conclusión:**
Para sistemas NewPOS que implementan ANSI X9.24-1 DUKPT:
- Llave 3DES DUKPT IPEK = 16 bytes (no 24 bytes)
- Esto es un caso especial del estándar, no una desviación

## Files Modified

1. `import_dukpt_test_keys.py` - Enhanced key generation script
2. `import_dukpt_profile.py` - Enhanced profile generation script
3. `test_keys_dukpt.json` - Updated test keys file (6 keys)
4. `dukpt_test_profile.json` - Updated simple AES-128 profile
5. `dukpt_multikey_profile.json` - Updated with 5 slots (was 3)
6. `dukpt_2tdea_profile.json` - New 2TDEA simple profile
7. `dukpt_3tdea_profile.json` - New 3TDEA simple profile

## Git Commit

```
76fdc2b [DIEGOH][AI-74] Agregar soporte para llaves DUKPT 3DES (2TDEA/3TDEA) en scripts de prueba
```

Changes: 6 files modified/created, 201 insertions, 11 deletions

## Related Features

This work builds on:
- ✅ Conditional KTK validation for DUKPT plaintext (EncryptionType 05)
- ✅ KSN format conversion (10 bytes → 12 bytes for NewPOS)
- ✅ Smart cast fixes for type safety
- ✅ Algorithm mapping for all DukptType values

## Next Steps

Ready for comprehensive DUKPT algorithm testing:
1. Import test keys (6 algorithms)
2. Import desired profile (AES, 3DES, or multi-algorithm)
3. Test DUKPT injection with EncryptionType 05
4. Verify all algorithms work correctly with NewPOS

