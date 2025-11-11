# DIAGRAMA RÁPIDO DE REFERENCIA - ESTRUCTURA DE LLAVES

## TABLA RÁPIDA DE UBICACIONES

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         UBICACIÓN DE ARCHIVOS CLAVE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  persistence/src/main/java/com/vigatec/persistence/                         │
│  ├── entities/                                                               │
│  │   ├── KeyEntity.kt                    → Metadata de llave                │
│  │   ├── InjectedKeyEntity.kt            → Llave real (IMPORTANTE)          │
│  │   └── ProfileEntity.kt                → Agrupación de llaves             │
│  │                                                                           │
│  ├── dao/                                                                    │
│  │   ├── KeyDao.kt                       → Acceso a KeyEntity              │
│  │   ├── InjectedKeyDao.kt               → Acceso a llaves reales ⭐       │
│  │   └── ProfileDao.kt                   → Acceso a perfiles ⭐            │
│  │                                                                           │
│  └── repository/                                                             │
│      ├── KeyRepository.kt                → Lógica de KeyEntity             │
│      ├── InjectedKeyRepository.kt        → Lógica de llaves ⭐             │
│      └── ProfileRepository.kt            → Lógica de perfiles ⭐           │
│                                                                              │
│  injector/src/main/java/com/vigatec/injector/                              │
│  ├── viewmodel/                                                              │
│  │   ├── KeyVaultViewModel.kt            → Lógica eliminación ⭐           │
│  │   └── ProfileViewModel.kt             → Manejo de perfiles              │
│  │                                                                           │
│  └── ui/screens/                                                             │
│      └── KeyVaultScreen.kt               → UI de llaves ⭐                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## FLUJO DE DATOS - ELIMINACIÓN DE LLAVE

```
┌──────────────────────────┐
│   KeyVaultScreen (UI)    │
│  (Botón "Eliminar")      │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────────────────────────┐
│ KeyVaultViewModel.onDeleteKey(key)           │
│ (Líneas 128-154)                             │
└─────────────┬──────────────────────────────┬─┘
              │                              │
              │ VALIDACIÓN (NUEVA)           │ LÓGICA ACTUAL
              ▼                              ▼
    ┌──────────────────────┐    ┌──────────────────────┐
    │ validateKeyDeletion()│    │ 1. Verificar admin   │
    │ (A IMPLEMENTAR)      │    │ 2. Si KEK → Keystore │
    │                      │    │ 3. Eliminar de BD    │
    │ Retorna:             │    │ 4. Recargar          │
    │ - canDelete          │    └──────────────────────┘
    │ - severity           │
    │ - reason             │
    │ - affectedProfiles   │
    └──────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
BLOCKED         ALLOWED/WARNING
(Error)         (Proceder)
    │                 │
    ▼                 ▼
ShowDialog      executeKeyDeletion()
    │                 │
    └─────────┬───────┘
              ▼
    ┌──────────────────────┐
    │ InjectedKeyRepository│
    │ .deleteKey(key)      │
    └──────────┬───────────┘
               ▼
    ┌──────────────────────┐
    │ InjectedKeyDao       │
    │ .deleteKey(keyId)    │
    └──────────┬───────────┘
               ▼
    ┌──────────────────────┐
    │ Base de datos        │
    │ DELETE injected_keys │
    │ WHERE id = ?         │
    └──────────────────────┘
```

---

## RELACIONES ENTRE TABLAS

```
    ┌──────────────────┐
    │   KeyEntity      │
    │   (metadata)     │
    │                  │
    │  - id            │
    │  - keyValue      │
    │  - description   │
    └────────┬─────────┘
             │ (referencia administrativa)
             ▼
    ┌──────────────────────────────┐
    │  InjectedKeyEntity           │
    │  (llave real + datos)        │
    │                              │
    │  - id                        │
    │  - kcv (Key Check Value) ◄───┼─── Identificador único
    │  - keySlot                   │
    │  - keyType                   │
    │  - kekType                   │
    │  - encryptedKeyData          │
    │  - status                    │
    └────────┬──────────┬──────────┘
             │          │
    ┌────────▼────┐     │
    │ Referenciado│     │ Referenciado
    │ por Profile │     │ como KEK
    │             │     │
    └─────┬───────┘     │
          │             │
          ▼             ▼
    ┌────────────────────────────┐
    │  ProfileEntity             │
    │  (agrupación de llaves)    │
    │                            │
    │  - id                      │
    │  - name                    │
    │  - keyConfigurations[]:    │
    │    - selectedKey (KCV) ────┼─ = InjectedKeyEntity.kcv
    │    - usage                 │
    │    - keyType               │
    │  - selectedKEKKcv ─────────┼─ = InjectedKeyEntity.kcv
    │  - useKEK                  │   (si es KEK Storage)
    │  - deviceType              │
    └────────────────────────────┘
```

---

## BÚSQUEDA DE PERFILES QUE USAN UNA LLAVE

```
QuerySQL:
SELECT name FROM profiles 
WHERE keyConfigurations LIKE '%' || :kcv || '%'

Ejemplo:
Buscar perfiles que usan KCV "A1B2C3"

Profile JSON serializado:
{
  "id": 1,
  "name": "Perfil Venta",
  "keyConfigurations": [
    {
      "selectedKey": "A1B2C3" ◄─── ENCONTRADO
    }
  ]
}

Resultado:
List<String> = ["Perfil Venta"]
```

---

## VALIDACIONES NECESARIAS (MATRIZ RÁPIDA)

```
┌─────────────────────┬──────────┬───────┬───────┬─────────────┐
│ Tipo de Llave       │ En Uso?  │ KEK?  │ KTK?  │ Acción      │
├─────────────────────┼──────────┼───────┼───────┼─────────────┤
│ Operacional         │ NO       │ NO    │ NO    │ ✓ ELIMINAR  │
│ Operacional         │ SÍ       │ NO    │ NO    │ ❌ RECHAZAR │
│ KEK Storage         │ NO       │ SÍ    │ NO    │ ✓ ELIMINAR  │
│ KEK Storage         │ SÍ       │ SÍ    │ NO    │ ❌ RECHAZAR │
│ KTK                 │ NO       │ SÍ    │ SÍ    │ ⚠️ ADVERTIR │
│ KTK                 │ SÍ       │ SÍ    │ SÍ    │ ❌ RECHAZAR │
└─────────────────────┴──────────┴───────┴───────┴─────────────┘
```

---

## CÓDIGO - MÉTODOS CLAVE LLAMADOS

```kotlin
// 1. OBTENER PERFILES QUE USAN UNA LLAVE
profileRepository.getProfileNamesByKeyKcv(key.kcv)
  → Retorna: List<String> con nombres de perfiles

// 2. ELIMINAR UNA LLAVE
injectedKeyRepository.deleteKey(key)
  → Llama a: InjectedKeyDao.deleteKey(keyId)

// 3. OBTENER KEK ACTUAL
injectedKeyRepository.getCurrentKEK()
  → Retorna: InjectedKeyEntity? (KEK Storage activa)

// 4. OBTENER KTK ACTUAL
injectedKeyRepository.getCurrentKTK()
  → Retorna: InjectedKeyEntity? (KTK activa)

// 5. VALIDAR ANTES DE ELIMINAR (A IMPLEMENTAR)
injectedKeyRepository.validateKeyDeletion(key)
  → Retorna: KeyDeletionValidation
```

---

## CAMPOS DE InjectedKeyEntity

```kotlin
data class InjectedKeyEntity(
    val id: Long,                        // ID único en BD
    val keySlot: Int,                    // Posición en PED (< 0 = ceremonia)
    val keyType: String,                 // "MASTER_KEY", "WORKING_KEY", etc
    val keyAlgorithm: String,            // "DES_TRIPLE", "AES_256", etc
    val kcv: String,                     // KCV en HEX (identificador)
    val encryptedKeyData: String,        // Datos cifrados (HEX)
    val encryptionIV: String,            // IV de cifrado (12 bytes HEX)
    val encryptionAuthTag: String,       // Auth tag (16 bytes HEX)
    val injectionTimestamp: Long,        // Cuándo se inyectó
    val status: String,                  // "ACTIVE", "EXPORTED", "INACTIVE"
    val isKEK: Boolean,                  // DEPRECATED
    val kekType: String,                 // "NONE", "KEK_STORAGE", "KEK_TRANSPORT"
    val customName: String               // Nombre personalizado
)
```

---

## CAMPOS DE ProfileEntity

```kotlin
data class ProfileEntity(
    val id: Long,
    val name: String,
    val description: String,
    val applicationType: String,
    val keyConfigurations: List<KeyConfiguration>,
    val useKEK: Boolean,
    val selectedKEKKcv: String,          // ◄─ Referencia a KEK
    val deviceType: String
)

data class KeyConfiguration(
    val id: Long,
    val usage: String,                   // "PIN", "MAC", etc
    val keyType: String,
    val slot: String,
    val selectedKey: String,             // ◄─ Referencia a llave (KCV)
    val injectionMethod: String,
    val ksn: String
)
```

---

## CASOS DE USO - EJEMPLOS

### Caso 1: Llave sin uso
```
Llave: KCV="A1B2C3", kekType="NONE"
¿En perfiles? → []
¿Es KEK? → NO
¿Es KTK? → NO
Acción: ELIMINAR ✓
```

### Caso 2: Llave en uso
```
Llave: KCV="D4E5F6", kekType="NONE"
¿En perfiles? → ["Perfil Venta", "Perfil Factura"]
¿Es KEK? → NO
¿Es KTK? → NO
Acción: RECHAZAR ❌
Mensaje: "Llave usada en 2 perfiles"
```

### Caso 3: KEK Storage en uso
```
Llave: KCV="789ABC", kekType="KEK_STORAGE"
¿En perfiles? → []
¿Es KEK Storage? → SÍ
¿Algún perfil usa como selectedKEKKcv? → ["Perfil Seguro"]
Acción: RECHAZAR ❌
Mensaje: "KEK usada por Perfil Seguro para cifrado"
```

### Caso 4: KTK activa
```
Llave: KCV="DEF012", kekType="KEK_TRANSPORT"
¿En perfiles? → []
¿Es KEK? → SÍ
¿Es KTK activa? → SÍ (getCurrentKTK().kcv == "DEF012")
Acción: ADVERTENCIA ⚠️
Mensaje: "Es la KTK activa - ¿desea continuar?"
```

---

## CHECKLIST - ANTES DE ELIMINAR LLAVE

```
□ 1. ¿Usuario es administrador?
     └─ profileRepository.getProfileNamesByKeyKcv()

□ 2. ¿Está siendo usada en algún perfil?
     └─ Si SÍ → RECHAZAR

□ 3. ¿Es KEK Storage?
     └─ if (key.isKEKStorage())
     └─ ¿Algún perfil tiene selectedKEKKcv = key.kcv?
     └─ Si SÍ → RECHAZAR

□ 4. ¿Es KTK activa?
     └─ injectedKeyRepository.getCurrentKTK()?.kcv == key.kcv
     └─ Si SÍ → ADVERTENCIA

□ 5. Si todas pasan → ELIMINAR
     └─ injectedKeyRepository.deleteKey(key)
     └─ Si es KEK → StorageKeyManager.deleteStorageKEK()
```

---

## RESUMEN RÁPIDO

**Problema:** No valida si llave está en uso antes de eliminar

**Solución:** Agregar validación en 4 archivos:
1. InjectedKeyRepository - método `validateKeyDeletion()`
2. KeyVaultViewModel - modificar `onDeleteKey()`
3. KeyVaultScreen - agregar diálogos
4. ProfileDao - (opcional) agregar query

**Archivos a leer:**
- RESUMEN_ANALISIS_ESTRUCTURA.md (5 min)
- ANALISIS_ESTRUCTURA_VALIDACION_LLAVES.md (15 min)
- VALIDACION_ELIMINACION_LLAVES_RECOMENDACIONES.md (15 min)

**Total:** ~1500 líneas de documentación + código

