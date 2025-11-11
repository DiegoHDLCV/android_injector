# ANÁLISIS DE ESTRUCTURA DEL PROYECTO - VALIDACIÓN AL ELIMINAR LLAVES

## 1. ENTIDADES Y SUS RELACIONES

### 1.1 Entidades Principales

#### KeyEntity (Tabla: "key")
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/entities/KeyEntity.kt`

```kotlin
@Entity(tableName = "key")
data class KeyEntity(
    @PrimaryKey(autoGenerate = true) 
    override val id: Long = 0L,
    val keyValue: String,           // El valor de la llave
    val description: String?,        // Descripción opcional
    val createdByAdminId: Long,      // ID del admin que creó la llave
    val creationDate: Long,          // Fecha de creación
    val isActive: Boolean = true     // Estado activo/inactivo
): Identifiable
```

**Propósito:** Almacena definiciones base de llaves (metadata administrativo).

---

#### InjectedKeyEntity (Tabla: "injected_keys")
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/entities/InjectedKeyEntity.kt`

```kotlin
@Entity(
    tableName = "injected_keys",
    indices = [
        Index(value = ["keySlot", "keyType"], unique = false),
        Index(value = ["kcv", "kekType"], unique = true)
    ]
)
data class InjectedKeyEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    // Datos de inyección
    val keySlot: Int,              // Posición en el PED (< 0 = ceremonia)
    val keyType: String,           // "MASTER_KEY", "WORKING_PIN_KEY", "CEREMONY_KEY"
    val keyAlgorithm: String,      // "DES_TRIPLE", "AES_256", etc.
    val kcv: String,               // Key Checksum Value (HEX)
    
    // Datos cifrados (cuando existe KEK Storage)
    val encryptedKeyData: String,  // Datos cifrados en HEX
    val encryptionIV: String,      // Vector inicialización (12 bytes = 24 chars HEX)
    val encryptionAuthTag: String, // Tag autenticación GCM (16 bytes = 32 chars HEX)
    
    // Metadatos
    val injectionTimestamp: Long,
    val status: String,            // "SUCCESSFUL", "FAILED", "ACTIVE", "EXPORTED", "INACTIVE"
    
    // KEK (Key Encryption Key)
    val isKEK: Boolean = false,    // DEPRECATED - usar kekType
    val kekType: String,           // "NONE", "KEK_STORAGE", "KEK_TRANSPORT"
    val customName: String = ""
)
```

**Propósito:** Almacena llaves criptográficas reales inyectadas en el PED, con soporte para cifrado.

**Tipos de KEK:**
- `NONE`: Llave operacional regular
- `KEK_STORAGE`: Llave que cifra otras llaves en la BD (almacenada en Android Keystore)
- `KEK_TRANSPORT` (KTK): Llave que cifra llaves para envío a SubPOS

---

#### ProfileEntity (Tabla: "profiles")
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/entities/ProfileEntity.kt`

```kotlin
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    val name: String,
    val description: String,
    val applicationType: String,
    val keyConfigurations: List<KeyConfiguration>,  // ⭐ Referencia a llaves
    
    // Configuración de cifrado KEK
    val useKEK: Boolean = false,
    val selectedKEKKcv: String = "",  // ⭐ Referencia a llave KEK específica
    
    // Configuración del dispositivo
    val deviceType: String = "AISINO"  // AISINO, NEWPOS, etc.
)

data class KeyConfiguration(
    val id: Long,
    val usage: String,           // "PIN", "MAC", "DATA", etc.
    val keyType: String,         // "MASTER_KEY", "WORKING_KEY", etc.
    val slot: String,
    val selectedKey: String,     // ⭐ KCV de la llave seleccionada
    val injectionMethod: String,
    val ksn: String = ""         // Para DUKPT
)
```

**Propósito:** Agrupa llaves en configuraciones por aplicación/caso de uso.

---

### 1.2 Relaciones entre Entidades

```
┌─────────────────┐
│   KeyEntity     │
│  (Metadata)     │
└────────┬────────┘
         │
         │ (administrativo)
         │
         ▼
┌────────────────────────────┐
│  InjectedKeyEntity         │
│  (Llave Real + Datos)      │
│                            │
│  - kcv (KCV)               │  ◄──────┐
│  - keySlot                 │         │
│  - kekType (NONE/KEK_...) │         │
│  - encryptedKeyData        │         │
└────────┬───────────────────┘         │
         │                             │
         │ (KCV como referencia)       │
         ▼                             │
┌─────────────────────────────────┐   │
│   ProfileEntity                 │   │
│                                 │   │
│ - keyConfigurations:           │   │
│   - selectedKey (KCV) ──────────┼───┘
│   - usage, keyType, slot       │
│                                 │
│ - selectedKEKKcv (KCV) ─────────┼───┐
│   (Referencia a KEK específica) │   │
│                                 │   │
│ - deviceType (AISINO/NEWPOS)    │   │
└─────────────────────────────────┘   │
                                       │
                   (Si existe KEK)     │
                                       │
                   ┌───────────────────┘
                   │
                   ▼ (Buscar por KCV)
          ┌──────────────────┐
          │ InjectedKeyEntity│
          │  (KEK_STORAGE)   │
          └──────────────────┘
```

---

## 2. DAOS Y REPOSITORIOS

### 2.1 KeyDao
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/dao/KeyDao.kt`

```kotlin
@Dao
interface KeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: KeyEntity): Long

    @Query("SELECT * FROM key WHERE id = :keyId")
    suspend fun getKeyById(keyId: Long): KeyEntity?

    @Query("SELECT * FROM key WHERE createdByAdminId = :adminId")
    fun getKeysByAdmin(adminId: Long): Flow<List<KeyEntity>>

    @Query("SELECT * FROM key WHERE isActive = 1")
    fun getAllActiveKeys(): Flow<List<KeyEntity>>

    @Query("DELETE FROM key WHERE id = :keyId")
    suspend fun deleteKeyById(keyId: Long)

    @Query("DELETE FROM key")
    suspend fun deleteAllKeys()
}
```

---

### 2.2 InjectedKeyDao ⭐ PRINCIPAL
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/dao/InjectedKeyDao.kt`

**Métodos de consulta (IMPORTANTES para validación):**

```kotlin
@Query("SELECT * FROM injected_keys")
fun getAllInjectedKeys(): Flow<List<InjectedKeyEntity>>

@Query("SELECT * FROM injected_keys WHERE keySlot = :slot AND keyType = :type LIMIT 1")
suspend fun getKeyBySlotAndType(slot: Int, type: String): InjectedKeyEntity?

@Query("SELECT * FROM injected_keys WHERE kcv = :kcv LIMIT 1")
suspend fun getKeyByKcv(kcv: String): InjectedKeyEntity?

@Query("SELECT * FROM injected_keys WHERE isKEK = 1 LIMIT 1")
suspend fun getCurrentKEK(): InjectedKeyEntity?

@Query("SELECT * FROM injected_keys WHERE kekType = 'KEK_TRANSPORT' LIMIT 1")
suspend fun getCurrentKTK(): InjectedKeyEntity?
```

**Métodos de eliminación (CRÍTICOS):**

```kotlin
@Delete
suspend fun deleteKey(key: InjectedKeyEntity)

@Query("DELETE FROM injected_keys WHERE id = :keyId")
suspend fun deleteKey(keyId: Long)

@Query("DELETE FROM injected_keys")
suspend fun deleteAllKeys()

@Query("UPDATE injected_keys SET status = :newStatus")
suspend fun updateStatusForAllKeys(newStatus: String)
```

---

### 2.3 ProfileDao
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/dao/ProfileDao.kt`

```kotlin
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT name FROM profiles WHERE keyConfigurations LIKE '%' || :kcv || '%'")
    suspend fun getProfileNamesByKeyKcv(kcv: String): List<String>  // ⭐ BUSCA PERFILES POR KCV

    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun getProfileByName(name: String): ProfileEntity?

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)
}
```

---

### 2.4 InjectedKeyRepository ⭐ CRÍTICO
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/repository/InjectedKeyRepository.kt`

**Métodos de eliminación:**

```kotlin
suspend fun deleteKey(keyId: Long)  // Por ID
suspend fun deleteKey(key: InjectedKeyEntity)  // Por entidad
suspend fun deleteAllKeys()  // Todas
```

**Métodos para gestión de KEK:**

```kotlin
suspend fun getCurrentKEK(): InjectedKeyEntity?          // Obtener KEK Storage activa
suspend fun getCurrentKTK(): InjectedKeyEntity?          // Obtener KTK activa
suspend fun setKeyAsKEK(kcv: String)                    // Marcar como KEK Storage
suspend fun removeKeyAsKEK(kcv: String)                 // Desmarcar KEK Storage
suspend fun setKeyAsKTK(kcv: String)                    // Marcar como KTK
suspend fun removeKeyAsKTK(kcv: String)                 // Desmarcar KTK
```

---

### 2.5 ProfileRepository
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/repository/ProfileRepository.kt`

```kotlin
suspend fun getProfileNamesByKeyKcv(kcv: String): List<String>
    // Retorna lista de nombres de perfiles que usan la llave con ese KCV

suspend fun updateProfile(profile: ProfileEntity)
suspend fun deleteProfile(profile: ProfileEntity)
```

---

## 3. LÓGICA ACTUAL DE ELIMINACIÓN DE LLAVES

### 3.1 KeyVaultViewModel (Punto de entrada principal)
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/injector/src/main/java/com/vigatec/injector/viewmodel/KeyVaultViewModel.kt`

```kotlin
data class KeyWithProfiles(
    val key: InjectedKeyEntity,
    val assignedProfiles: List<String> = emptyList()  // ⭐ Perfiles que usan la llave
)

fun loadKeys() {
    injectedKeyRepository.getAllInjectedKeys().collect { keys ->
        val keysWithProfiles = keys.map { key ->
            val profiles = profileRepository.getProfileNamesByKeyKcv(key.kcv)  // 🔍 BUSCA PERFILES
            KeyWithProfiles(key = key, assignedProfiles = profiles)
        }
    }
}

fun onDeleteKey(key: InjectedKeyEntity) {
    // 1. Verificar si es admin
    if (!_uiState.value.isAdmin) {
        Log.w(TAG, "Usuario no autorizado intentó eliminar una llave")
        return
    }

    viewModelScope.launch {
        try {
            // 2. Si es KEK Storage, eliminar del Android Keystore
            if (key.isKEKStorage()) {
                StorageKeyManager.deleteStorageKEK()
            }

            // 3. Eliminar de la base de datos
            injectedKeyRepository.deleteKey(key)

            // 4. Recargar
            loadKeys()
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar llave", e)
        }
    }
}

fun onConfirmClearAllKeys() {
    // Similar a onDeleteKey pero para todas las llaves
}
```

**PROBLEMA ACTUAL:** No valida si la llave está siendo usada por perfiles antes de eliminarla.

---

### 3.2 KeyVaultScreen (UI)
**Ubicación:** `/Users/diegoherreradelacalle/StudioProjects/android_injector/injector/src/main/java/com/vigatec/injector/ui/screens/KeyVaultScreen.kt`

Muestra:
- Llave con su KCV
- Estado de la llave
- Lista de perfiles asignados (desde `KeyWithProfiles.assignedProfiles`)
- Botón para eliminar

---

## 4. FLUJO DE DATOS CLAVE

### Eliminación de una llave (Flujo actual):

```
KeyVaultScreen (UI)
    │
    ├─ Mostrar KeyWithProfiles
    │  - key: InjectedKeyEntity
    │  - assignedProfiles: List<String>  (cargados en loadKeys())
    │
    └─ onDeleteKey() en ViewModel
        │
        ├─ Verificar admin ✓
        ├─ Si es KEK Storage:
        │  └─ StorageKeyManager.deleteStorageKEK()
        │
        └─ injectedKeyRepository.deleteKey(key)
           │
           └─ InjectedKeyDao.deleteKey(keyId)
              │
              └─ Base de datos: DELETE FROM injected_keys WHERE id = ?

❌ FALTA: No valida si profileRepository.getProfileNamesByKeyKcv(key.kcv) 
         retorna perfiles que usan esta llave
```

---

## 5. CÓMO SE RELACIONAN LAS LLAVES CON LOS PERFILES

### 5.1 Búsqueda de relación:

```sql
-- ProfileDao.kt consulta:
SELECT name FROM profiles WHERE keyConfigurations LIKE '%' || :kcv || '%'
```

**¿Cómo funciona?**

El campo `keyConfigurations: List<KeyConfiguration>` en ProfileEntity se serializa como JSON:

```json
{
  "id": 1,
  "name": "Profile de Venta",
  "keyConfigurations": [
    {
      "id": 1,
      "usage": "PIN",
      "keyType": "MASTER_KEY",
      "slot": "0",
      "selectedKey": "A1B2C3",      // ⭐ KCV DE LA LLAVE
      "injectionMethod": "DUKPT",
      "ksn": ""
    },
    {
      "id": 2,
      "usage": "MAC",
      "keyType": "WORKING_KEY",
      "slot": "1",
      "selectedKey": "D4E5F6",       // ⭐ OTRO KCV
      "injectionMethod": "AES",
      "ksn": ""
    }
  ]
}
```

La consulta LIKE busca el KCV dentro del JSON serializado.

### 5.2 Relación con KEK:

```json
{
  "id": 1,
  "name": "Profile Seguro",
  "useKEK": true,
  "selectedKEKKcv": "789ABC",        // ⭐ REFERENCIA A KEK Storage
  "keyConfigurations": [...]
}
```

---

## 6. ESTRUCTURA DE DIRECTORIOS CLAVE

```
/persistence/src/main/java/com/vigatec/persistence/
├── entities/
│   ├── KeyEntity.kt                    # Metadata de llave
│   ├── InjectedKeyEntity.kt            # ⭐ Llave real (PRINCIPAL)
│   └── ProfileEntity.kt                # Agrupación de llaves
│
├── dao/
│   ├── KeyDao.kt
│   ├── InjectedKeyDao.kt               # ⭐ Acceso a BD de llaves
│   └── ProfileDao.kt                   # Busca por KCV
│
└── repository/
    ├── KeyRepository.kt
    ├── InjectedKeyRepository.kt        # ⭐ Lógica de negocio
    └── ProfileRepository.kt

/injector/src/main/java/com/vigatec/injector/
├── viewmodel/
│   ├── KeyVaultViewModel.kt            # ⭐ Lógica de eliminación (ACTUAL)
│   └── ProfileViewModel.kt             # Manejo de perfiles
│
└── ui/screens/
    └── KeyVaultScreen.kt               # UI para eliminar
```

---

## 7. RESUMEN DE RELACIONES

| Entidad | Campo clave | Referencia a | Tabla objetivo |
|---------|------------|-------------|-----------------|
| **ProfileEntity** | `keyConfigurations[].selectedKey` | InjectedKeyEntity.kcv | injected_keys |
| **ProfileEntity** | `selectedKEKKcv` | InjectedKeyEntity.kcv | injected_keys |
| **InjectedKeyEntity** | `kcv` | Clave única | Referenciada por Profiles |

---

## 8. INFORMACIÓN NECESARIA PARA VALIDACIÓN

Antes de eliminar una llave `InjectedKeyEntity`, necesitamos:

1. **¿Está la llave siendo usada en algún perfil?**
   ```kotlin
   val profiles = profileRepository.getProfileNamesByKeyKcv(key.kcv)
   if (profiles.isNotEmpty()) {
       // LA LLAVE ESTÁ EN USO - VALIDAR
   }
   ```

2. **¿Es la llave actual KEK Storage?**
   ```kotlin
   if (key.isKEKStorage()) {
       // Determinar si puede eliminarse
   }
   ```

3. **¿Es la llave actual KTK activa?**
   ```kotlin
   if (key.isKEKTransport()) {
       // Determinar si puede eliminarse
   }
   ```

4. **¿Está la llave referenciada por su KCV en algún perfil?**
   ```kotlin
   val isReferencedAsPIN = profiles.any { profileName ->
       // Buscar en ProfileDao si este perfil usa key.kcv como PIN
   }
   ```

---

