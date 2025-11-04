# Documentación del Sistema de Inyección de Llaves Criptográficas

## Parte 6: Usuarios y Persistencia de Datos

### Versión: 1.0
### Fecha: Octubre 2025

---

## 1. SISTEMA DE USUARIOS

### 1.1 Gestión de Usuarios

#### 1.1.1 Modelo de Usuario

**Entidad**:
```kotlin
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val pass: String
)
```

**Características**:
- Almacenamiento local en SQLite
- Autenticación simple usuario/contraseña
- Un usuario por defecto en inicialización

#### 1.1.2 Usuario por Defecto

**Credenciales Iniciales**:
- **Usuario**: `admin`
- **Contraseña**: `admin`

**Creación Automática**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        userDaoProvider: Provider<UserDao>
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "injector_database"
        ).addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    // Usuario por defecto
                    userDaoProvider.get().insertUser(
                        User(username = "admin", pass = "admin")
                    )
                }
            }
        }).build()
    }
}
```

### 1.2 Autenticación

#### 1.2.1 Proceso de Login

**Flujo**:
```
[LoginScreen]
  └─ Usuario ingresa credenciales
      ↓
[LoginViewModel]
  └─ Valida formato de entrada
  └─ Llama a UserRepository.login()
      ↓
[UserRepository]
  └─ Busca usuario en BD por username
  └─ Compara contraseña
  └─ Retorna true/false
      ↓
[LoginViewModel]
  └─ Si éxito:
      ├─ loginSuccess = true
      └─ Navega a pantalla principal
  └─ Si fallo:
      ├─ loginError = "Credenciales inválidas"
      └─ Muestra error en UI
```

**Código**:
```kotlin
// LoginViewModel
fun login() {
    viewModelScope.launch {
        isLoading = true
        loginError = null
        
        val success = userRepository.login(username, password)
        
        if (success) {
            loginSuccess = true
        } else {
            loginError = "Credenciales inválidas"
        }
        
        isLoading = false
    }
}

// UserRepository
suspend fun login(username: String, pass: String): Boolean {
    val user = userDao.findByUsername(username)
    return user != null && user.pass == pass
}
```

#### 1.2.2 Roles de Usuario

**Enum de Roles**:
```kotlin
enum class Role {
    ADMIN,
    USER
}
```

**Nota**: En la versión actual, el sistema de roles está definido pero no implementado completamente. Todos los usuarios tienen acceso completo.

**Futuro**:
- **ADMIN**: Acceso completo (ceremonia, perfiles, inyección)
- **USER**: Solo inyección desde perfiles predefinidos

### 1.3 Gestión de Sesión

#### 1.3.1 Estado de Sesión

**En Injector**:
- Login requerido al iniciar
- Sesión persiste durante ejecución de app
- No hay logout explícito (cerrar app)

**En App (SubPOS)**:
- Sin autenticación
- Inicia directamente en pantalla principal
- Modo escucha automático

#### 1.3.2 Seguridad de Contraseñas

**Situación Actual**:
- Contraseñas almacenadas en texto claro
- Solo para entorno de desarrollo/testing

**Recomendaciones Futuras**:
```kotlin
// Hash de contraseñas con bcrypt o argon2
val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

// Validación
val isValid = BCrypt.checkpw(inputPassword, storedHash)
```

---

## 2. PERSISTENCIA DE DATOS

### 2.1 Base de Datos Room

#### 2.1.1 AppDatabase

**Configuración**:
```kotlin
@Database(
    entities = [
        KeyEntity::class,
        InjectedKeyEntity::class,
        ProfileEntity::class,
        UserEntity::class  // Solo en Injector
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun keyDao(): KeyDao
    abstract fun injectedKeyDao(): InjectedKeyDao
    abstract fun profileDao(): ProfileDao
    abstract fun userDao(): UserDao  // Solo en Injector
}
```

**Versión**: 10 (última actualización: agregado campos `isKEK` y `customName`)

**Ubicación**:
- **Injector**: `/data/data/com.vigatec.injector/databases/injector_database`
- **App**: `/data/data/com.vigatec.android_injector/databases/app_database`

#### 2.1.2 Migraciones

**Sin Migraciones Actualmente**:
- Base de datos se destruye y recrea en cambio de versión
- Solo para desarrollo
- **Producción requiere migraciones**

**Ejemplo de Migración Futura**:
```kotlin
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Agregar columnas isKEK y customName
        database.execSQL(
            "ALTER TABLE injected_keys ADD COLUMN isKEK INTEGER NOT NULL DEFAULT 0"
        )
        database.execSQL(
            "ALTER TABLE injected_keys ADD COLUMN customName TEXT NOT NULL DEFAULT ''"
        )
    }
}

// En DatabaseProvider
Room.databaseBuilder(...)
    .addMigrations(MIGRATION_9_10)
    .build()
```

### 2.2 Entidades Principales

#### 2.2.1 InjectedKeyEntity

**Propósito**: Almacenar llaves criptográficas generadas o inyectadas

**Estructura**:
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
    
    // Identificación
    val keySlot: Int,           // Slot en PED (0-99)
    val keyType: String,        // "MASTER_KEY", "WORKING_PIN_KEY", etc.
    val keyAlgorithm: String,   // "DES_TRIPLE", "AES_128", etc.
    
    // Datos de llave
    val kcv: String,            // Key Check Value (identificador)
    val keyData: String = "",   // Datos completos en hexadecimal
    
    // Auditoría
    val injectionTimestamp: Long = System.currentTimeMillis(),
    val status: String,         // "SUCCESSFUL", "FAILED", "ACTIVE", etc.
    
    // Características especiales
    val isKEK: Boolean = false,     // Es una KEK
    val customName: String = ""     // Nombre personalizado
)
```

**Índices**:
1. **Único en (keySlot, keyType)**: Una sola llave por slot/tipo
2. **No único en kcv**: Permite misma llave en diferentes slots

**Campos Importantes**:
- `keyData`: **Dato más sensible** - llave completa en hex
- `kcv`: Identificador público para selección
- `isKEK`: Flag para diferenciar KEK de llaves operacionales
- `status`: Estados posibles:
  - "SUCCESSFUL": Inyección exitosa
  - "FAILED": Inyección fallida
  - "ACTIVE": KEK activa (no exportada)
  - "EXPORTED": KEK exportada a SubPOS
  - "INACTIVE": KEK reemplazada

#### 2.2.2 ProfileEntity

**Propósito**: Almacenar perfiles de configuración de inyección

**Estructura**:
```kotlin
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Información básica
    val name: String,
    val description: String,
    val applicationType: String,
    
    // Configuraciones de llaves
    val keyConfigurations: List<KeyConfiguration>,
    
    // Configuración de KEK
    val useKEK: Boolean = false,
    val selectedKEKKcv: String = ""
)
```

**KeyConfiguration** (dato embebido):
```kotlin
data class KeyConfiguration(
    val id: Long,
    val usage: String,              // Descripción de uso
    val keyType: String,            // Tipo de llave
    val slot: String,               // Slot en PED
    val selectedKey: String,        // KCV de llave
    val injectionMethod: String,    // Método de inyección
    val ksn: String = ""           // KSN (solo DUKPT)
)
```

**TypeConverter para List<KeyConfiguration>**:
```kotlin
class Converters {
    @TypeConverter
    fun fromKeyConfigList(value: List<KeyConfiguration>): String {
        return Gson().toJson(value)
    }
    
    @TypeConverter
    fun toKeyConfigList(value: String): List<KeyConfiguration> {
        val listType = object : TypeToken<List<KeyConfiguration>>() {}.type
        return Gson().fromJson(value, listType)
    }
}
```

#### 2.2.3 UserEntity (Solo Injector)

**Estructura**:
```kotlin
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val pass: String
)
```

### 2.3 DAOs (Data Access Objects)

#### 2.3.1 InjectedKeyDao

**Operaciones Principales**:
```kotlin
@Dao
interface InjectedKeyDao {
    // Inserción/Actualización
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(key: InjectedKeyEntity): Long
    
    @Insert
    suspend fun insertAll(keys: List<InjectedKeyEntity>)
    
    // Consultas
    @Query("SELECT * FROM injected_keys ORDER BY injectionTimestamp DESC")
    fun getAllInjectedKeys(): Flow<List<InjectedKeyEntity>>
    
    @Query("SELECT * FROM injected_keys WHERE kcv = :kcv LIMIT 1")
    suspend fun getKeyByKcv(kcv: String): InjectedKeyEntity?
    
    @Query("SELECT * FROM injected_keys WHERE keySlot = :slot AND keyType = :type LIMIT 1")
    suspend fun getKeyBySlotAndType(slot: Int, type: String): InjectedKeyEntity?
    
    @Query("SELECT * FROM injected_keys WHERE isKEK = 1 AND status = :status")
    suspend fun getKEKsByStatus(status: String): List<InjectedKeyEntity>
    
    // Actualización
    @Query("UPDATE injected_keys SET status = :status WHERE kcv = :kcv")
    suspend fun updateKeyStatus(kcv: String, status: String)
    
    // Eliminación
    @Query("DELETE FROM injected_keys WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM injected_keys")
    suspend fun deleteAll()
}
```

#### 2.3.2 ProfileDao

**Operaciones**:
```kotlin
@Dao
interface ProfileDao {
    @Insert
    suspend fun insert(profile: ProfileEntity): Long
    
    @Update
    suspend fun update(profile: ProfileEntity)
    
    @Delete
    suspend fun delete(profile: ProfileEntity)
    
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>
    
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ProfileEntity?
}
```

#### 2.3.3 UserDao (Solo Injector)

**Operaciones**:
```kotlin
@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: User): Long
    
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): User?
    
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}
```

### 2.4 Repositorios

#### 2.4.1 InjectedKeyRepository

**Funcionalidad Completa**:
```kotlin
@Singleton
class InjectedKeyRepository @Inject constructor(
    private val injectedKeyDao: InjectedKeyDao
) {
    // Obtener llaves
    fun getAllInjectedKeys(): Flow<List<InjectedKeyEntity>> {
        return injectedKeyDao.getAllInjectedKeys()
    }
    
    suspend fun getKeyByKcv(kcv: String): InjectedKeyEntity? {
        return injectedKeyDao.getKeyByKcv(kcv)
    }
    
    suspend fun getKeyBySlotAndType(
        slot: Int,
        type: String
    ): InjectedKeyEntity? {
        return injectedKeyDao.getKeyBySlotAndType(slot, type)
    }
    
    // Registrar inyección (solo metadatos)
    suspend fun recordKeyInjection(
        keySlot: Int,
        keyType: String,
        keyAlgorithm: String,
        kcv: String,
        status: String = "SUCCESSFUL"
    ) {
        val injectedKey = InjectedKeyEntity(
            keySlot = keySlot,
            keyType = keyType,
            keyAlgorithm = keyAlgorithm,
            kcv = kcv,
            status = status,
            injectionTimestamp = System.currentTimeMillis()
        )
        injectedKeyDao.insertOrUpdate(injectedKey)
    }
    
    // Registrar inyección (con datos completos)
    suspend fun recordKeyInjectionWithData(
        keySlot: Int,
        keyType: String,
        keyAlgorithm: String,
        kcv: String,
        keyData: String,
        status: String = "SUCCESSFUL",
        isKEK: Boolean = false,
        customName: String = ""
    ) {
        Log.i(TAG, "=== REGISTRANDO LLAVE CON DATOS COMPLETOS ===")
        Log.i(TAG, "Slot: $keySlot")
        Log.i(TAG, "Tipo: $keyType")
        Log.i(TAG, "Algoritmo: $keyAlgorithm")
        Log.i(TAG, "KCV: $kcv")
        Log.i(TAG, "Longitud de datos: ${keyData.length / 2} bytes")
        Log.i(TAG, "Es KEK: $isKEK")
        
        val injectedKey = InjectedKeyEntity(
            keySlot = keySlot,
            keyType = keyType,
            keyAlgorithm = keyAlgorithm,
            kcv = kcv,
            keyData = keyData,
            status = status,
            isKEK = isKEK,
            customName = customName,
            injectionTimestamp = System.currentTimeMillis()
        )
        
        injectedKeyDao.insertOrUpdate(injectedKey)
        
        Log.i(TAG, "✓ Llave almacenada en base de datos")
        
        // Verificación post-almacenamiento
        val storedKey = injectedKeyDao.getKeyByKcv(kcv)
        if (storedKey != null) {
            Log.i(TAG, "✓ Verificación: Llave encontrada en BD")
            Log.i(TAG, "  - KCV almacenado: ${storedKey.kcv}")
            Log.i(TAG, "  - Datos almacenados: ${storedKey.keyData.length / 2} bytes")
            
            if (storedKey.keyData == keyData) {
                Log.i(TAG, "  ✓ Datos coinciden perfectamente")
            } else {
                Log.w(TAG, "  ⚠️ Discrepancia en datos almacenados")
            }
        } else {
            Log.e(TAG, "  ✗ Error: Llave no encontrada después de insertar")
        }
    }
    
    // Gestión de KEK
    suspend fun getActiveKEK(): InjectedKeyEntity? {
        val keks = injectedKeyDao.getKEKsByStatus("ACTIVE")
        return keks.firstOrNull()
    }
    
    suspend fun markKEKAsExported(kcv: String) {
        injectedKeyDao.updateKeyStatus(kcv, "EXPORTED")
    }
    
    suspend fun markKEKAsInactive(kcv: String) {
        injectedKeyDao.updateKeyStatus(kcv, "INACTIVE")
    }
    
    // Eliminación
    suspend fun deleteKey(keyId: Long) {
        injectedKeyDao.deleteById(keyId)
    }
    
    suspend fun deleteAllKeys() {
        injectedKeyDao.deleteAll()
    }
}
```

#### 2.4.2 ProfileRepository

**Operaciones**:
```kotlin
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {
    fun getAllProfiles(): Flow<List<ProfileEntity>> {
        return profileDao.getAllProfiles()
    }
    
    suspend fun getProfileById(id: Long): ProfileEntity? {
        return profileDao.getProfileById(id)
    }
    
    suspend fun insertProfile(profile: ProfileEntity): Long {
        return profileDao.insert(profile)
    }
    
    suspend fun updateProfile(profile: ProfileEntity) {
        profileDao.update(profile)
    }
    
    suspend fun deleteProfile(id: Long) {
        val profile = profileDao.getProfileById(id)
        profile?.let { profileDao.delete(it) }
    }
}
```

#### 2.4.3 UserRepository (Solo Injector)

```kotlin
@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    suspend fun login(username: String, pass: String): Boolean {
        val user = userDao.findByUsername(username)
        return user != null && user.pass == pass
    }
    
    suspend fun getUserCount(): Int {
        return userDao.getUserCount()
    }
}
```

---

## 3. FLUJOS DE DATOS

### 3.1 Flujo Reactivo con Flow

**Observación de Cambios**:
```kotlin
// En ViewModel
val injectedKeys: StateFlow<List<InjectedKeyEntity>> = 
    injectedKeyRepository.getAllInjectedKeys()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

// En UI (Compose)
val keys by viewModel.injectedKeys.collectAsState()

LazyColumn {
    items(keys) { key ->
        KeyItem(key)
    }
}
```

**Ventajas**:
- Actualización automática de UI
- Cambios en BD se reflejan inmediatamente
- Sin necesidad de refresh manual

### 3.2 Operaciones CRUD

#### 3.2.1 Crear Llave (Ceremonia)

```
[CeremonyViewModel]
  └─ finalizeCeremony()
      ├─ Combinar componentes (XOR)
      ├─ Calcular KCV
      ├─ Determinar algoritmo
      ↓
[KeyStoreManager]
  └─ storeMasterKey(alias, keyBytes)
      └─ Almacenar en Android KeyStore
      ↓
[InjectedKeyRepository]
  └─ recordKeyInjectionWithData(
        keySlot = slot,
        keyType = "MASTER_KEY",
        keyAlgorithm = algorithm,
        kcv = kcv,
        keyData = keyHex,
        isKEK = (usuario seleccionó KEK)
      )
      ↓
[InjectedKeyDao]
  └─ insertOrUpdate()
      └─ INSERT o UPDATE en tabla injected_keys
      ↓
[Flow]
  └─ Emite nueva lista de llaves
      ↓
[UI]
  └─ Actualiza automáticamente
```

#### 3.2.2 Leer Llaves

```
[ProfileViewModel]
  └─ Necesita llaves disponibles para dropdown
      ↓
[InjectedKeyRepository]
  └─ getAllInjectedKeys()
      └─ Flow<List<InjectedKeyEntity>>
      ↓
[ViewModel]
  └─ Convierte Flow a StateFlow
  └─ Filtra llaves según criterio (opcional)
      ↓
[UI]
  └─ Dropdown muestra KCVs de llaves
  └─ Usuario selecciona llave
```

#### 3.2.3 Actualizar Estado de Llave

```
[KEKManager]
  └─ Exportar KEK a SubPOS
      ├─ Enviar KEK
      ├─ Recibir confirmación
      ↓
[InjectedKeyRepository]
  └─ markKEKAsExported(kcv)
      ↓
[InjectedKeyDao]
  └─ UPDATE injected_keys 
      SET status = 'EXPORTED' 
      WHERE kcv = :kcv
      ↓
[Flow]
  └─ Emite lista actualizada
      ↓
[UI]
  └─ KEK muestra estado "EXPORTED"
```

#### 3.2.4 Eliminar Llave

```
[InjectedKeysScreen]
  └─ Usuario presiona "Eliminar" en llave
  └─ Modal de confirmación
      ↓
[InjectedKeysViewModel]
  └─ deleteKey(keyId)
      ↓
[InjectedKeyRepository]
  └─ deleteKey(keyId)
      ↓
[InjectedKeyDao]
  └─ DELETE FROM injected_keys WHERE id = :id
      ↓
[Flow]
  └─ Emite lista sin la llave eliminada
      ↓
[UI]
  └─ Lista se actualiza (llave desaparece)
```

### 3.3 Sincronización entre Injector y App

**No Hay Sincronización Directa**:
- Injector tiene su propia BD
- App (SubPOS) tiene su propia BD
- Cada uno mantiene registro independiente

**Flujo de Datos**:
```
[Injector BD]
  ├─ Llaves generadas en ceremonia
  ├─ Perfiles de configuración
  └─ Registro de inyecciones enviadas
      ↓
  [Comunicación USB]
      ↓
[App BD]
  ├─ Llaves recibidas e inyectadas en PED
  └─ Registro de inyecciones recibidas
```

**Reconciliación**:
- No automática
- Ambas BDs son fuente de verdad para su respectivo dispositivo
- Auditoría se hace comparando logs

---

## 4. SEGURIDAD DE DATOS

### 4.1 Datos Sensibles

**Información Crítica**:
1. **keyData**: Llave completa en hexadecimal
2. **pass**: Contraseñas de usuario
3. **keyData en KEK**: KEK completa

**Protección Actual**:
- SQLite sin cifrado (solo en desarrollo)
- Android KeyStore para llaves maestras
- Permisos de app (aislamiento)

### 4.2 Recomendaciones de Seguridad

#### 4.2.1 Cifrado de Base de Datos

**SQLCipher** (para producción):
```kotlin
// En build.gradle
implementation "net.zetetic:android-database-sqlcipher:4.5.4"

// En DatabaseProvider
val passphrase = // Obtener de forma segura
val factory = SupportFactory(passphrase.toByteArray())

Room.databaseBuilder(...)
    .openHelperFactory(factory)
    .build()
```

#### 4.2.2 Hash de Contraseñas

**Bcrypt**:
```kotlin
// En build.gradle
implementation "org.mindrot:jbcrypt:0.4"

// Al crear usuario
val hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12))

// Al validar
val isValid = BCrypt.checkpw(inputPassword, user.pass)
```

#### 4.2.3 Rotación de Llaves

**KEK Rotation**:
1. Generar nueva KEK
2. Marcar KEK anterior como INACTIVE
3. Actualizar perfiles para usar nueva KEK
4. Re-inyectar terminales gradualmente

**Proceso Automatizado**:
```kotlin
suspend fun rotateKEK() {
    // 1. Marcar KEK actual como INACTIVE
    val currentKEK = getActiveKEK()
    currentKEK?.let { markKEKAsInactive(it.kcv) }
    
    // 2. Generar nueva KEK
    val newKEK = KEKManager.generateKEK(keyLength = 32)
    
    // 3. Almacenar como ACTIVE
    recordKeyInjectionWithData(
        keySlot = 0,
        keyType = "TRANSPORT_KEY",
        keyAlgorithm = "AES_256",
        kcv = newKEK.kcv,
        keyData = newKEK.keyData,
        isKEK = true,
        status = "ACTIVE"
    )
    
    // 4. Actualizar perfiles (manual o automático)
    // ...
}
```

### 4.3 Auditoría y Logs

#### 4.3.1 Registro de Operaciones

**Tabla de Auditoría** (futura):
```kotlin
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val userId: Long,
    val action: String,          // "CREATE_KEY", "INJECT", "DELETE", etc.
    val targetType: String,       // "KEY", "PROFILE", "KEK"
    val targetId: String,         // KCV, Profile ID, etc.
    val details: String,          // JSON con detalles
    val result: String            // "SUCCESS", "FAILURE"
)
```

**Eventos a Auditar**:
- Generación de llaves (ceremonia)
- Creación/modificación de perfiles
- Inyección de llaves (éxito/fallo)
- Exportación de KEK
- Eliminación de llaves
- Login de usuarios

#### 4.3.2 Consultas de Auditoría

**Llaves Generadas Hoy**:
```sql
SELECT * FROM injected_keys 
WHERE DATE(injectionTimestamp/1000, 'unixepoch') = DATE('now')
ORDER BY injectionTimestamp DESC
```

**KEKs Activas**:
```sql
SELECT * FROM injected_keys 
WHERE isKEK = 1 AND status = 'ACTIVE'
```

**Perfiles que Usan KEK Específica**:
```sql
SELECT * FROM profiles 
WHERE selectedKEKKcv = :kcv
```

**Llaves por Tipo**:
```sql
SELECT keyType, COUNT(*) as count
FROM injected_keys
GROUP BY keyType
ORDER BY count DESC
```

---

## 5. BACKUP Y RECUPERACIÓN

### 5.1 Backup de Base de Datos

#### 5.1.1 Backup Manual

**Exportar BD**:
```kotlin
suspend fun backupDatabase(context: Context) {
    val dbPath = context.getDatabasePath("injector_database")
    val backupPath = File(
        context.getExternalFilesDir(null),
        "backup_${System.currentTimeMillis()}.db"
    )
    
    dbPath.copyTo(backupPath, overwrite = true)
    
    Log.i(TAG, "Backup creado: ${backupPath.absolutePath}")
}
```

#### 5.1.2 Backup Automático

**Room Auto-Backup** (configuración):
```kotlin
Room.databaseBuilder(...)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .enableMultiInstanceInvalidation()
    .build()
```

### 5.2 Exportación de Datos

#### 5.2.1 Exportar Perfiles a JSON

**Funcionalidad**:
```kotlin
suspend fun exportProfilesToJson(context: Context): File {
    val profiles = profileRepository.getAllProfiles().first()
    val json = Gson().toJson(profiles)
    
    val exportFile = File(
        context.getExternalFilesDir(null),
        "profiles_export_${System.currentTimeMillis()}.json"
    )
    
    exportFile.writeText(json)
    
    return exportFile
}
```

**Formato JSON**:
```json
[
  {
    "id": 1,
    "name": "Terminal Tienda - Básico",
    "description": "Perfil estándar para tiendas",
    "applicationType": "Transaccional",
    "keyConfigurations": [
      {
        "id": 1,
        "usage": "PIN Entry",
        "keyType": "PIN",
        "slot": "10",
        "selectedKey": "AABB12",
        "injectionMethod": "Futurex",
        "ksn": ""
      }
    ],
    "useKEK": true,
    "selectedKEKKcv": "A1B2C3"
  }
]
```

#### 5.2.2 Importar Perfiles desde JSON

```kotlin
suspend fun importProfilesFromJson(context: Context, file: File) {
    val json = file.readText()
    val profiles = Gson().fromJson<List<ProfileEntity>>(
        json,
        object : TypeToken<List<ProfileEntity>>() {}.type
    )
    
    profiles.forEach { profile ->
        // Verificar que llaves existan
        val allKeysExist = profile.keyConfigurations.all { config ->
            injectedKeyRepository.getKeyByKcv(config.selectedKey) != null
        }
        
        if (allKeysExist) {
            profileRepository.insertProfile(
                profile.copy(id = 0)  // Nuevo ID
            )
        } else {
            Log.w(TAG, "Perfil ${profile.name} omitido - llaves faltantes")
        }
    }
}
```

### 5.3 Recuperación de Desastres

**Escenario**: Pérdida de BD completa

**Pasos de Recuperación**:
1. **Restaurar Backup de BD** (si existe)
2. **Re-generar Llaves Críticas** (ceremonia)
3. **Re-crear Perfiles** (desde JSON export)
4. **Validar Integridad** (todas las llaves presentes)
5. **Re-inyectar Terminales** (si es necesario)

**Prevención**:
- Backups automáticos diarios
- Exports de perfiles tras cambios
- Documentación de llaves críticas (KCVs)
- Redundancia de KEKs

---

## 6. CONCLUSIÓN

El sistema de persistencia y usuarios proporciona:

✅ **Autenticación Simple**: Login con usuario/contraseña  
✅ **Base de Datos Room**: Persistencia robusta con SQLite  
✅ **Entidades Bien Definidas**: Llaves, Perfiles, Usuarios  
✅ **Flujos Reactivos**: Actualización automática de UI con Flow  
✅ **Repositorios**: Abstracción de acceso a datos  
✅ **Registro Completo**: Llaves con datos completos (keyData)  
✅ **Auditoría**: Timestamps y estados para trazabilidad  
✅ **Backup/Restore**: Exportación e importación de datos  
✅ **Seguridad**: Recomendaciones para cifrado y hash  

La arquitectura de persistencia permite una gestión completa del ciclo de vida de llaves criptográficas con trazabilidad total y capacidad de recuperación ante fallos.

---

**Siguiente Documento**: [Parte 7: Fabricantes y Dispositivos Soportados](DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md)


