# Guía de Integración - Sistema de Logs

## Estado de la Implementación

### ✅ Completado

1. **Sistema de Logs**
   - ✅ Entidad `InjectionLogEntity`
   - ✅ DAO `InjectionLogDao`
   - ✅ Repositorio `InjectionLogRepository`
   - ✅ Utilidad `InjectionLogger`
   - ✅ ViewModel `LogsViewModel`
   - ✅ Pantalla `LogsScreen`

2. **Sistema de Usuarios**
   - ✅ Entidad `User` actualizada con roles
   - ✅ DAO `UserDao` mejorado
   - ✅ Repositorio `UserRepository` actualizado
   - ✅ ViewModel `UserManagementViewModel`
   - ✅ Pantalla `UserManagementScreen`

3. **Pantalla de Configuración**
   - ✅ ViewModel `ConfigViewModel`
   - ✅ Pantalla `ConfigScreen`

4. **Navegación**
   - ✅ Rutas agregadas en `Screen.kt`
   - ✅ `AppNavigation.kt` actualizado
   - ✅ `LoginViewModel` devuelve usuario completo
   - ✅ Botón de configuración en `MainScaffold`

5. **Base de Datos**
   - ✅ Versión actualizada a v2
   - ✅ `AppDatabase` con ambas entidades
   - ✅ `AppModule` con providers necesarios

## 🔧 Integración del InjectionLogger en CeremonyViewModel

### Opción 1: Integración Manual (Recomendada)

Ya que el `CeremonyViewModel` es un archivo grande con lógica compleja, aquí te muestro cómo integrar el `InjectionLogger`:

#### Paso 1: Inyectar el InjectionLogger

```kotlin
@HiltViewModel
class CeremonyViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository,
    private val injectionLogger: InjectionLogger  // ← AGREGAR ESTE PARÁMETRO
) : ViewModel() {
    // ... resto del código
}
```

#### Paso 2: Agregar campos para el contexto del log

Agrega estos campos al `CeremonyState`:

```kotlin
data class CeremonyState(
    // ... campos existentes
    val currentUsername: String = "",  // ← AGREGAR
    val profileName: String = ""       // ← AGREGAR
)
```

#### Paso 3: Registrar logs en las operaciones clave

En el método `finalizeCeremony()`, después de guardar exitosamente la llave (línea ~213), agrega:

```kotlin
// Después de injectedKeyRepository.recordKeyInjectionWithData(...)
addToLog("✓ Llave COMPLETA guardada exitosamente en base de datos")

// ← AGREGAR ESTE CÓDIGO AQUÍ
// Registrar el log de la operación
injectionLogger.logSuccess(
    commandSent = "CEREMONY_GENERATE_KEY",
    responseReceived = "KCV: $finalKcv, Length: ${finalKeyBytes.size} bytes",
    username = _uiState.value.currentUsername,
    profileName = "Ceremony: ${_uiState.value.customName.ifEmpty { "Unnamed" }}",
    keyType = keyType,
    keySlot = -1,
    notes = "Key generated via ceremony with ${_uiState.value.numCustodians} custodians"
)
addToLog("✓ Log de operación registrado")
// ← FIN DEL CÓDIGO AGREGADO

addToLog("✓ Verificación: ${finalKeyBytes.size} bytes almacenados")
```

En caso de error (en el catch del método `finalizeCeremony()`, línea ~275):

```kotlin
} catch (e: Exception) {
    addToLog("Error al finalizar la ceremonia: ${e.message}")

    // ← AGREGAR ESTE CÓDIGO AQUÍ
    injectionLogger.logError(
        commandSent = "CEREMONY_GENERATE_KEY",
        responseReceived = "ERROR: ${e.message}",
        username = _uiState.value.currentUsername,
        profileName = "Ceremony: ${_uiState.value.customName.ifEmpty { "Unnamed" }}",
        notes = "Error during key ceremony: ${e.stackTraceToString().take(500)}"
    )
    // ← FIN DEL CÓDIGO AGREGADO

    e.printStackTrace()
    _uiState.value = _uiState.value.copy(isLoading = false)
}
```

#### Paso 4: Inicializar el username en la ceremonia

Cuando se inicia la ceremonia desde la UI, pasa el username:

En `CeremonyScreen.kt` (donde se llame a `startCeremony()`):

```kotlin
// Asegúrate de tener acceso al username del usuario actual
val currentUsername = "admin" // Obtener del contexto/navegación

// Al iniciar la ceremonia
viewModel.startCeremony(username = currentUsername)
```

Y actualiza el método `startCeremony` en el ViewModel:

```kotlin
fun startCeremony(username: String = "") {
    addToLog("=== INICIANDO CEREMONIA DE LLAVES ===")
    addToLog("Usuario: $username")  // ← AGREGAR
    // ... resto del código

    _uiState.value = _uiState.value.copy(
        currentStep = 2,
        isCeremonyInProgress = true,
        isLoading = false,
        currentUsername = username  // ← AGREGAR
    )
}
```

### Opción 2: Integración en Otras Operaciones de Inyección

Si tienes otras operaciones de inyección de llaves (por ejemplo, en perfiles o inyección directa), aplica el mismo patrón:

```kotlin
// Después de una operación exitosa
injectionLogger.logSuccess(
    commandSent = "INJECT_KEY: [comando_real]",
    responseReceived = "SUCCESS: [respuesta_real]",
    username = currentUsername,
    profileName = profileName,
    keyType = keyType,
    keySlot = keySlot,
    notes = "Additional context here"
)

// En caso de error
injectionLogger.logFailure(
    commandSent = "INJECT_KEY: [comando_real]",
    responseReceived = "FAILED: [respuesta_error]",
    username = currentUsername,
    profileName = profileName,
    keyType = keyType,
    keySlot = keySlot,
    notes = "Error details: ${error.message}"
)
```

## 📝 Ejemplo Completo de Uso

```kotlin
@HiltViewModel
class MyInjectionViewModel @Inject constructor(
    private val injectionLogger: InjectionLogger
) : ViewModel() {

    fun performKeyInjection(
        username: String,
        profileName: String,
        keyData: String,
        keySlot: Int,
        keyType: String
    ) {
        viewModelScope.launch {
            try {
                // Construir comando
                val command = "INJECT_KEY slot=$keySlot type=$keyType"

                // Enviar comando al dispositivo
                val response = deviceManager.sendCommand(command, keyData)

                // Registrar éxito
                if (response.isSuccess) {
                    injectionLogger.logSuccess(
                        commandSent = command,
                        responseReceived = response.data,
                        username = username,
                        profileName = profileName,
                        keyType = keyType,
                        keySlot = keySlot,
                        notes = "Injection completed successfully"
                    )
                } else {
                    injectionLogger.logFailure(
                        commandSent = command,
                        responseReceived = response.error ?: "Unknown error",
                        username = username,
                        profileName = profileName,
                        keyType = keyType,
                        keySlot = keySlot,
                        notes = "Injection failed"
                    )
                }
            } catch (e: Exception) {
                injectionLogger.logError(
                    commandSent = "INJECT_KEY",
                    responseReceived = "EXCEPTION: ${e.message}",
                    username = username,
                    profileName = profileName,
                    keyType = keyType,
                    keySlot = keySlot,
                    notes = e.stackTraceToString().take(500)
                )
            }
        }
    }
}
```

## 🎯 Verificación de la Implementación

1. **Ejecuta la aplicación**
2. **Inicia sesión** con el usuario admin (pass: admin)
3. **Haz clic en el botón de configuración** (ícono de engranaje en el TopAppBar)
4. **Accede a "Logs de Inyección"** para ver la pantalla de logs
5. **Accede a "Gestión de Usuarios"** (solo admin) para administrar usuarios

## 🔄 Flujo de Navegación

```
Login → Main (Dashboard/KeyVault/Ceremony/Profiles)
         ↓
         Botón Config
         ↓
      ConfigScreen
         ├─→ Logs Screen (todos los usuarios)
         └─→ User Management Screen (solo admin)
```

## ⚠️ Notas Importantes

1. **Base de Datos**: La versión cambió de 1 a 2. La primera vez que ejecutes la app con el código actualizado, se recreará la BD (se perderán datos existentes).

2. **Usuario por Defecto**: Se crea automáticamente:
   - Username: `admin`
   - Password: `admin`
   - Role: `ADMIN`

3. **Permisos**: Solo los usuarios con rol `ADMIN` pueden:
   - Ver la gestión de usuarios
   - Crear/editar/eliminar usuarios
   - Los usuarios normales solo ven los logs y su información de perfil

4. **Logs**: Los administradores pueden ver logs de todos los usuarios mediante filtros.

## 📚 Documentación Adicional

- Ver `DOCUMENTACION_09_LOGS_Y_USUARIOS.md` para documentación completa
- Archivos creados listados al final del documento principal

## 🚀 Próximos Pasos Sugeridos

1. Implementar hash de contraseñas (BCrypt/Argon2)
2. Agregar exportación de logs a CSV/JSON
3. Implementar date picker para filtros de fecha
4. Agregar búsqueda por texto en logs
5. Implementar migración de BD apropiada
6. Agregar estadísticas en ConfigScreen
