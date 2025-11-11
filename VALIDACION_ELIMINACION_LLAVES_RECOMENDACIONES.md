# RECOMENDACIONES PARA VALIDACIÓN AL ELIMINAR LLAVES

## DIAGRAMA DE DEPENDENCIAS VISUAL

```
                        ┌─────────────────────────────┐
                        │  KeyVaultScreen (UI)        │
                        │  Muestra llaves y botones   │
                        └──────────────┬──────────────┘
                                       │
                                       │ onClick(deleteButton)
                                       ▼
                        ┌─────────────────────────────┐
                        │ KeyVaultViewModel           │
                        │ onDeleteKey(key)            │
                        └──────────────┬──────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
                    ▼                  ▼                  ▼
         ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
         │ 1. AUTORIZACIÓN  │ │ 2. VALIDACIONES  │ │ 3. ELIMINACIÓN   │
         ├──────────────────┤ ├──────────────────┤ ├──────────────────┤
         │ ✓ ¿Es admin?     │ │ ✗ En perfiles?   │ │ ✓ Eliminar BD    │
         │ (Ya existe)      │ │ (FALTA)          │ │ ✓ Eliminar       │
         │                  │ │ ✗ Es KEK?        │ │   Keystore       │
         │                  │ │ (PARCIAL)        │ │ ✓ Recargar       │
         │                  │ │ ✗ Es KTK activa? │ │                  │
         │                  │ │ (FALTA)          │ │                  │
         └──────────────────┘ └──────────────────┘ └──────────────────┘
```

---

## CASOS DE USO CON VALIDACIONES NECESARIAS

### Caso 1: Llave Operacional Normal (Sin usar en perfiles)

```
InjectedKeyEntity {
    id: 1
    kcv: "A1B2C3"
    keyType: "WORKING_KEY"
    kekType: "NONE"           // No es KEK
    status: "ACTIVE"
}

VALIDACIONES A HACER:
✓ ¿Es admin? → SÍ
✓ ¿Está en perfiles? → profileRepository.getProfileNamesByKeyKcv("A1B2C3") → []
✓ ¿Es KEK? → NO
✓ ¿Es KTK? → NO

RESULTADO: ELIMINAR SIN PROBLEMAS
```

---

### Caso 2: Llave Usada en Perfiles (CRÍTICO)

```
InjectedKeyEntity {
    id: 2
    kcv: "D4E5F6"
    keyType: "MASTER_KEY"
    kekType: "NONE"
    status: "ACTIVE"
}

VALIDACIONES A HACER:
✓ ¿Es admin? → SÍ
✗ ¿Está en perfiles? → profileRepository.getProfileNamesByKeyKcv("D4E5F6") 
                      → ["Perfil Venta", "Perfil Factura"]  ← ESTÁ EN USO

RESULTADO: ❌ NO ELIMINAR
Mostrar error: "No se puede eliminar la llave D4E5F6
               porque está siendo usada por los siguientes perfiles:
               - Perfil Venta
               - Perfil Factura
               
               Primero debe actualizar estos perfiles para usar otra llave."
```

---

### Caso 3: KEK Storage Activa

```
InjectedKeyEntity {
    id: 3
    kcv: "789ABC"
    keyType: "CEREMONY_KEY"
    kekType: "KEK_STORAGE"     ← Es KEK Storage
    status: "ACTIVE"
}

VALIDACIONES A HACER:
✓ ¿Es admin? → SÍ
✗ ¿Está en perfiles? → profileRepository.getProfileNamesByKeyKcv("789ABC") → []
✗ ¿Es KEK Storage? → SÍ - ¿está referenciada en perfiles?
                      profileRepository.getProfileByName(*).selectedKEKKcv = "789ABC"
                      → ["Perfil Seguro"]  ← ALGUNOS PERFILES USAN ESTA KEK

RESULTADO: ❌ NO ELIMINAR
Mostrar error: "No se puede eliminar la KEK Storage 789ABC
               porque los siguientes perfiles la usan para cifrado:
               - Perfil Seguro
               
               Primero debe:
               1. Actualizar esos perfiles para no usar esta KEK
               2. O eliminar la KEK Storage desde Perfil Seguro"
```

---

### Caso 4: KTK Activa

```
InjectedKeyEntity {
    id: 4
    kcv: "DEF012"
    keyType: "CEREMONY_KEY"
    kekType: "KEK_TRANSPORT"   ← Es KTK
    status: "ACTIVE"
}

VALIDACIONES A HACER:
✓ ¿Es admin? → SÍ
✓ ¿Está en keyConfigurations? → profileRepository.getProfileNamesByKeyKcv("DEF012")
                                 → []
✗ ¿Es KTK activa? → injectedKeyRepository.getCurrentKTK()?.kcv == "DEF012" → SÍ

RESULTADO: ⚠️ ADVERTENCIA (Permitir pero con confirmación adicional)
Mostrar dialogo: "ADVERTENCIA: Esta es la KTK (Key Transport Key) activa.

               Si la elimina:
               - Ya no se podrán cifrar llaves para envío a SubPOS
               - Los perfiles con esta KTK serán marcados como incompatibles
               
               ¿Desea continuar?"
```

---

## MATRIZ DE DECISIÓN

```
┌───────────────────────┬──────────────┬─────────────┬─────────────┬──────────────┐
│ Tipo de Llave         │ ¿Está usada? │ ¿Es KEK?    │ ¿Es KTK?    │ ACCIÓN       │
├───────────────────────┼──────────────┼─────────────┼─────────────┼──────────────┤
│ Operacional regular   │ NO           │ NO          │ NO          │ ELIMINAR ✓   │
│ Operacional regular   │ SÍ           │ NO          │ NO          │ RECHAZAR ❌  │
│ KEK Storage           │ NO           │ SÍ - solo BD │ NO          │ ELIMINAR ✓   │
│ KEK Storage           │ SÍ en perfiles│ SÍ         │ NO          │ RECHAZAR ❌  │
│ KTK activa            │ NO           │ NO/SÍ      │ SÍ          │ ADVERTENCIA ⚠│
│ KTK activa            │ SÍ en perfiles│ NO/SÍ     │ SÍ          │ RECHAZAR ❌  │
└───────────────────────┴──────────────┴─────────────┴─────────────┴──────────────┘
```

---

## ESTRUCTURA DE CÓDIGO RECOMENDADA

### 1. Crear data class para resultado de validación

```kotlin
// En KeyVaultViewModel.kt o un nuevo archivo ValidateKeyDeletion.kt

data class KeyDeletionValidation(
    val canDelete: Boolean,
    val severity: DeletionSeverity,  // ALLOWED, WARNING, BLOCKED
    val reason: String,              // Mensaje para mostrar al usuario
    val affectedProfiles: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)

enum class DeletionSeverity {
    ALLOWED,    // Permitir eliminación directa
    WARNING,    // Permitir pero mostrar advertencia
    BLOCKED     // No permitir eliminación
}
```

---

### 2. Función de validación en InjectedKeyRepository

```kotlin
// Agregar a: InjectedKeyRepository.kt

suspend fun validateKeyDeletion(key: InjectedKeyEntity): KeyDeletionValidation {
    return try {
        Log.i(TAG, "=== VALIDANDO ELIMINACIÓN DE LLAVE ===")
        Log.i(TAG, "KCV: ${key.kcv}, Tipo: ${key.keyType}, kekType: ${key.kekType}")
        
        // 1. Buscar en perfiles si se usa como selectedKey
        val profilesUsingKey = profileRepository.getProfileNamesByKeyKcv(key.kcv)
        Log.d(TAG, "Perfiles usando llave como selectedKey: ${profilesUsingKey.size}")
        
        if (profilesUsingKey.isNotEmpty()) {
            return KeyDeletionValidation(
                canDelete = false,
                severity = DeletionSeverity.BLOCKED,
                reason = buildString {
                    append("No se puede eliminar la llave ${key.kcv}:\n\n")
                    append("Está siendo usada en los siguientes perfiles:\n")
                    profilesUsingKey.forEach { append("  • $it\n") }
                    append("\nPrimero actualice estos perfiles para usar otra llave.")
                },
                affectedProfiles = profilesUsingKey,
                suggestions = listOf(
                    "Edite cada perfil y asigne una llave diferente",
                    "O elimine los perfiles que ya no necesite"
                )
            )
        }
        
        // 2. Si es KEK Storage, verificar si algún perfil lo usa como selectedKEKKcv
        if (key.isKEKStorage()) {
            Log.d(TAG, "Llave es KEK Storage - verificando referencias...")
            
            val profilesUsingAsKEK = injectedKeyDao.getAllProfilesUsingKEK(key.kcv)
            if (profilesUsingAsKEK.isNotEmpty()) {
                return KeyDeletionValidation(
                    canDelete = false,
                    severity = DeletionSeverity.BLOCKED,
                    reason = buildString {
                        append("No se puede eliminar la KEK Storage ${key.kcv}:\n\n")
                        append("Los siguientes perfiles la usan para cifrado:\n")
                        profilesUsingAsKEK.forEach { append("  • $it\n") }
                        append("\nPrimero desactive la KEK en estos perfiles.")
                    },
                    affectedProfiles = profilesUsingAsKEK,
                    suggestions = listOf(
                        "Edite cada perfil y desactive 'Usar KEK'",
                        "O seleccione una KEK diferente en los perfiles"
                    )
                )
            }
        }
        
        // 3. Si es KTK activa, mostrar advertencia
        if (key.isKEKTransport()) {
            Log.d(TAG, "Llave es KTK activa")
            return KeyDeletionValidation(
                canDelete = true,
                severity = DeletionSeverity.WARNING,
                reason = buildString {
                    append("ADVERTENCIA: Esta es la KTK (Key Transport Key) activa.\n\n")
                    append("Si la elimina:\n")
                    append("  • Ya no se podrán cifrar llaves para SubPOS\n")
                    append("  • Será necesario generar una nueva KTK\n\n")
                    append("¿Desea continuar?")
                },
                affectedProfiles = emptyList(),
                suggestions = listOf(
                    "Genere una nueva KTK antes de eliminar esta",
                    "Actualice los perfiles para usar la nueva KTK"
                )
            )
        }
        
        // 4. Caso normal: llave sin referencias
        Log.d(TAG, "✓ Llave puede eliminarse sin problemas")
        KeyDeletionValidation(
            canDelete = true,
            severity = DeletionSeverity.ALLOWED,
            reason = "La llave se eliminará de la base de datos",
            affectedProfiles = emptyList()
        )
        
    } catch (e: Exception) {
        Log.e(TAG, "Error validando eliminación de llave", e)
        KeyDeletionValidation(
            canDelete = false,
            severity = DeletionSeverity.BLOCKED,
            reason = "Error al validar: ${e.message}",
            affectedProfiles = emptyList()
        )
    }
}
```

---

### 3. Actualizar KeyVaultViewModel

```kotlin
// Modificar en: KeyVaultViewModel.kt

fun onDeleteKey(key: InjectedKeyEntity) {
    if (!_uiState.value.isAdmin) {
        Log.w(TAG, "Usuario no autorizado intentó eliminar una llave")
        return
    }

    viewModelScope.launch {
        try {
            // ✨ NUEVO: Validar antes de eliminar
            Log.d(TAG, "Validando llave para eliminación...")
            val validation = injectedKeyRepository.validateKeyDeletion(key)
            
            when (validation.severity) {
                DeletionSeverity.BLOCKED -> {
                    // Mostrar error y no permitir eliminación
                    Log.w(TAG, "Eliminación bloqueada: ${validation.reason}")
                    _uiState.value = _uiState.value.copy(
                        deletionError = validation.reason,
                        deletionSuggestions = validation.suggestions
                    )
                    return@launch
                }
                
                DeletionSeverity.WARNING -> {
                    // Mostrar advertencia y esperar confirmación adicional
                    Log.w(TAG, "Advertencia de eliminación: ${validation.reason}")
                    _uiState.value = _uiState.value.copy(
                        showDeletionWarning = true,
                        selectedKey = key,
                        deletionWarningMessage = validation.reason
                    )
                    return@launch  // Esperar onConfirmDeleteKeyAfterWarning()
                }
                
                DeletionSeverity.ALLOWED -> {
                    // Permitir eliminación directa
                    Log.d(TAG, "Validación aprobada - procediendo con eliminación")
                    executeKeyDeletion(key)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validando llave para eliminación", e)
            _uiState.value = _uiState.value.copy(
                deletionError = "Error al validar: ${e.message}"
            )
        }
    }
}

// Nuevo método para ejecutar eliminación después de validación
private suspend fun executeKeyDeletion(key: InjectedKeyEntity) {
    try {
        // Si es KEK Storage, también eliminar del Android Keystore
        if (key.isKEKStorage()) {
            Log.w(TAG, "Eliminando KEK Storage del Android Keystore...")
            StorageKeyManager.deleteStorageKEK()
            Log.d(TAG, "✓ KEK Storage eliminada del Keystore")
        }

        // Eliminar de la base de datos
        injectedKeyRepository.deleteKey(key)
        Log.d(TAG, "✓ Llave eliminada de la base de datos")
        
        // Limpiar UI
        _uiState.value = _uiState.value.copy(
            showDeleteModal = false,
            selectedKey = null
        )

        loadKeys() // Recargar
    } catch (e: Exception) {
        Log.e(TAG, "Error al eliminar llave", e)
        _uiState.value = _uiState.value.copy(
            deletionError = "Error al eliminar: ${e.message}"
        )
    }
}

// Nuevo método para confirmar después de advertencia
fun onConfirmDeleteKeyAfterWarning() {
    val key = _uiState.value.selectedKey ?: return
    
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(showDeletionWarning = false)
        executeKeyDeletion(key)
    }
}
```

---

### 4. Agregar método en ProfileDao

```kotlin
// Agregar a: ProfileDao.kt (OPCIONAL - si necesita búsqueda más eficiente)

@Query("SELECT name FROM profiles WHERE selectedKEKKcv = :kcv")
suspend fun getProfilesUsingKEK(kcv: String): List<String>
```

---

## CAMBIOS EN ESTADOS DE UI

```kotlin
// Actualizar KeyVaultState en KeyVaultViewModel.kt

data class KeyVaultState(
    val keysWithProfiles: List<KeyWithProfiles> = emptyList(),
    val loading: Boolean = true,
    val selectedKey: InjectedKeyEntity? = null,
    val showDeleteModal: Boolean = false,
    val showViewModal: Boolean = false,
    val showClearAllConfirmation: Boolean = false,
    
    // ✨ NUEVOS CAMPOS PARA VALIDACIÓN
    val showDeletionWarning: Boolean = false,     // Para advertencias
    val deletionWarningMessage: String? = null,   // Mensaje de advertencia
    val deletionError: String? = null,            // Mensaje de error de validación
    val deletionSuggestions: List<String> = emptyList(),  // Sugerencias al usuario
    
    val currentUser: User? = null,
    val isAdmin: Boolean = false,
    val showKEKStoragePasswordDialog: Boolean = false,
    val showKEKStorage: Boolean = false,
    val kekStoragePasswordError: String? = null
)
```

---

## CAMBIOS EN UI (KeyVaultScreen)

```kotlin
// Agregar diálogos en KeyVaultScreen.kt

// Diálogo para error de validación
if (state.deletionError != null) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDeletionError() },
        title = { Text("No se puede eliminar la llave") },
        text = { 
            Column {
                Text(state.deletionError!!)
                if (state.deletionSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sugerencias:", fontWeight = FontWeight.Bold)
                    state.deletionSuggestions.forEach {
                        Text("• $it", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.dismissDeletionError() }) {
                Text("Entendido")
            }
        }
    )
}

// Diálogo para advertencia
if (state.showDeletionWarning) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissDeletionWarning() },
        title = { Text("Advertencia de Eliminación") },
        text = { Text(state.deletionWarningMessage ?: "") },
        confirmButton = {
            Button(onClick = { viewModel.onConfirmDeleteKeyAfterWarning() }) {
                Text("Eliminar Igualmente")
            }
        },
        dismissButton = {
            Button(onClick = { viewModel.dismissDeletionWarning() }) {
                Text("Cancelar")
            }
        }
    )
}
```

---

## RESUMEN DE ARCHIVOS A MODIFICAR

1. **InjectedKeyRepository.kt** - Agregar método `validateKeyDeletion()`
2. **KeyVaultViewModel.kt** - Modificar `onDeleteKey()`, agregar estado y nuevos métodos
3. **KeyVaultScreen.kt** - Agregar diálogos de validación
4. **ProfileDao.kt** - (Opcional) Agregar método `getProfilesUsingKEK()`

---

## BENEFICIOS

✓ Previene eliminación accidental de llaves en uso
✓ Guía al usuario con sugerencias claras
✓ Protege integridad de perfiles y configuraciones
✓ Diferencia entre error bloqueante y advertencia
✓ Mantiene registros de auditoría claros

