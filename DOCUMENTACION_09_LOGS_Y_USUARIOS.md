# Documentación: Sistema de Logs y Gestión de Usuarios

## Resumen

Este documento describe la implementación del sistema de logs de inyección y la gestión avanzada de usuarios agregada al proyecto Android Injector.

## Características Implementadas

### 1. Sistema de Logs de Inyección

#### Base de Datos
- **Nueva Entidad**: `InjectionLogEntity`
  - Almacena comandos enviados y respuestas recibidas
  - Incluye metadatos: usuario, perfil, fecha, tipo de llave, slot
  - Estados: SUCCESS, FAILED, ERROR
  - Ubicación: `persistence/src/main/java/com/example/persistence/entities/InjectionLogEntity.kt`

- **DAO**: `InjectionLogDao`
  - Operaciones CRUD completas
  - Filtros por: usuario, perfil, fecha, estado
  - Queries con múltiples filtros combinados
  - Ubicación: `persistence/src/main/java/com/example/persistence/dao/InjectionLogDao.kt`

- **Repositorio**: `InjectionLogRepository`
  - Capa de abstracción sobre el DAO
  - Gestión de logs con Flow para actualizaciones reactivas
  - Ubicación: `persistence/src/main/java/com/example/persistence/repository/InjectionLogRepository.kt`

#### Utilidad para Registro
- **Clase**: `InjectionLogger`
  - Métodos convenientes: `logSuccess()`, `logFailure()`, `logError()`
  - Registro asíncrono (no bloquea el hilo principal)
  - Singleton inyectable con Hilt
  - Ubicación: `injector/src/main/java/com/vigatec/injector/util/InjectionLogger.kt`

#### Interfaz de Usuario
- **ViewModel**: `LogsViewModel`
  - Gestión de estado de logs
  - Filtros dinámicos (usuario, perfil, fecha)
  - Operaciones: ver, filtrar, eliminar logs
  - Ubicación: `injector/src/main/java/com/vigatec/injector/viewmodel/LogsViewModel.kt`

- **Pantalla**: `LogsScreen`
  - Visualización de logs con tarjetas informativas
  - Panel de filtros desplegable
  - Código de colores según estado (éxito/fallo/error)
  - Eliminación individual o masiva
  - Ubicación: `injector/src/main/java/com/vigatec/injector/ui/screens/LogsScreen.kt`

### 2. Gestión Avanzada de Usuarios

#### Base de Datos
- **Entidad Actualizada**: `User`
  - **Nuevos campos**:
    - `role`: "ADMIN" o "USER"
    - `fullName`: Nombre completo del usuario
    - `createdAt`: Fecha de creación
    - `isActive`: Estado activo/inactivo
  - Índice único en `username`
  - Ubicación: `injector/src/main/java/com/vigatec/injector/data/local/entity/User.kt`

- **DAO Actualizado**: `UserDao`
  - Operaciones completas: crear, editar, eliminar
  - Filtros por rol y estado
  - Actualización de contraseña
  - Conteo de admins (protección del último admin)
  - Ubicación: `injector/src/main/java/com/vigatec/injector/data/local/dao/UserDao.kt`

- **Repositorio Actualizado**: `UserRepository`
  - Login devuelve el objeto User completo
  - Validación de usuarios activos
  - Verificación de permisos de administrador
  - Ubicación: `injector/src/main/java/com/vigatec/injector/repository/UserRepository.kt`

#### Interfaz de Usuario
- **ViewModel**: `UserManagementViewModel`
  - Gestión completa de usuarios
  - Validaciones de seguridad (último admin)
  - Operaciones: crear, editar, eliminar, activar/desactivar
  - Ubicación: `injector/src/main/java/com/vigatec/injector/viewmodel/UserManagementViewModel.kt`

- **Pantalla**: `UserManagementScreen`
  - Lista de usuarios con información completa
  - Creación de usuarios con diálogo
  - Edición de usuarios (nombre, rol)
  - Cambio de contraseña
  - Toggle activo/inactivo
  - Solo accesible por administradores
  - Ubicación: `injector/src/main/java/com/vigatec/injector/ui/screens/UserManagementScreen.kt`

### 3. Pantalla de Configuración

#### Interfaz de Usuario
- **ViewModel**: `ConfigViewModel`
  - Carga información del usuario actual
  - Verifica permisos de administrador
  - Ubicación: `injector/src/main/java/com/vigatec/injector/viewmodel/ConfigViewModel.kt`

- **Pantalla**: `ConfigScreen`
  - Información del usuario actual
  - Acceso a logs de inyección
  - Acceso a gestión de usuarios (solo admin)
  - Información del sistema
  - Ubicación: `injector/src/main/java/com/vigatec/injector/ui/screens/ConfigScreen.kt`

### 4. Navegación

- **Rutas Agregadas**:
  - `ConfigScreen`: Pantalla de configuración
  - `LogsScreen`: Visualización de logs
  - `UserManagementScreen`: Gestión de usuarios

- Ubicación: `app/src/main/java/com/vigatec/android_injector/ui/navigation/Routes.kt`

### 5. Base de Datos

- **Versión actualizada**: v2
- **Migración**: `fallbackToDestructiveMigration()` configurado
- **Entidades**: `User`, `InjectionLogEntity`
- **Usuario por defecto**:
  - Username: `admin`
  - Password: `admin`
  - Role: `ADMIN`

## Uso del Sistema

### Para Registrar Logs

En tu ViewModel o clase donde se realiza la inyección:

```kotlin
@Inject
lateinit var injectionLogger: InjectionLogger

// Ejemplo de uso durante una inyección
fun performInjection() {
    val command = "COMANDO_ENVIADO"
    val response = sendCommand(command)

    if (response.isSuccess) {
        injectionLogger.logSuccess(
            commandSent = command,
            responseReceived = response.data,
            username = currentUsername,
            profileName = currentProfile,
            keyType = "MASTER_KEY",
            keySlot = 1,
            notes = "Inyección exitosa"
        )
    } else {
        injectionLogger.logFailure(
            commandSent = command,
            responseReceived = response.error,
            username = currentUsername,
            profileName = currentProfile,
            keyType = "MASTER_KEY",
            keySlot = 1,
            notes = "Error: ${response.error}"
        )
    }
}
```

### Para Acceder a las Pantallas

1. **Pantalla de Configuración**:
   - Agregar botón en la pantalla principal
   - Navegar a `Routes.ConfigScreen.route`
   - Pasar el `currentUsername` como parámetro

2. **Pantalla de Logs**:
   - Accesible desde la pantalla de configuración
   - O directamente con `Routes.LogsScreen.route`

3. **Gestión de Usuarios**:
   - Solo visible para administradores
   - Accesible desde la pantalla de configuración
   - O directamente con `Routes.UserManagementScreen.route`

## Integraciones Necesarias

### 1. Actualizar Login
El método `login()` del repositorio ahora devuelve un objeto `User` en lugar de `Boolean`:

```kotlin
// Antes
suspend fun login(username: String, pass: String): Boolean

// Ahora
suspend fun login(username: String, pass: String): User?
```

Actualizar LoginScreen y LoginViewModel para:
- Almacenar el usuario completo después del login
- Pasar el username a las pantallas que lo necesiten
- Usar el rol para mostrar/ocultar opciones de admin

### 2. Actualizar CeremonyViewModel

Inyectar `InjectionLogger` y registrar logs en cada operación de inyección:

```kotlin
@Inject
lateinit var injectionLogger: InjectionLogger

// En el método de inyección
injectionLogger.logSuccess(
    commandSent = command,
    responseReceived = response,
    username = currentUsername,
    profileName = ceremonyProfile?.name ?: "Sin perfil",
    keyType = keyConfig.keyType,
    keySlot = keyConfig.slot.toIntOrNull() ?: -1
)
```

### 3. Actualizar AppNavHost

Agregar las nuevas rutas al sistema de navegación:

```kotlin
composable(Routes.ConfigScreen.route) {
    ConfigScreen(
        currentUsername = currentUsername,
        onNavigateToLogs = { navController.navigate(Routes.LogsScreen.route) },
        onNavigateToUserManagement = { navController.navigate(Routes.UserManagementScreen.route) },
        onBack = { navController.popBackStack() }
    )
}

composable(Routes.LogsScreen.route) {
    LogsScreen(
        onBack = { navController.popBackStack() }
    )
}

composable(Routes.UserManagementScreen.route) {
    UserManagementScreen(
        onBack = { navController.popBackStack() }
    )
}
```

### 4. Agregar Botón de Configuración

En la pantalla principal, agregar un botón que navegue a la configuración:

```kotlin
IconButton(onClick = { navController.navigate(Routes.ConfigScreen.route) }) {
    Icon(Icons.Default.Settings, "Configuración")
}
```

## Permisos y Seguridad

### Protecciones Implementadas

1. **Último Administrador**:
   - No se puede eliminar el último admin
   - No se puede desactivar el último admin
   - No se puede cambiar el rol del último admin a USER

2. **Usuarios Activos**:
   - Los usuarios inactivos no pueden hacer login
   - El estado se puede cambiar fácilmente desde la UI

3. **Filtros de Logs**:
   - Los admins pueden ver logs de cualquier usuario
   - Se puede filtrar por usuario, perfil y fecha

## Base de Datos

### Versión 2 (Actual)

**Tablas**:
1. `users`
   - id (PK)
   - username (UNIQUE)
   - pass
   - role
   - fullName
   - createdAt
   - isActive

2. `injection_logs`
   - id (PK)
   - commandSent
   - responseReceived
   - operationStatus
   - username (INDEX)
   - profileName (INDEX)
   - keyType
   - keySlot
   - timestamp (INDEX)
   - deviceInfo
   - notes

## Consideraciones de Implementación

### Migración de Base de Datos

La versión de la base de datos se incrementó de 1 a 2. Se usa `fallbackToDestructiveMigration()` lo que significa que:
- **IMPORTANTE**: Al instalar la actualización, se perderán los datos existentes
- Para producción, implementar una migración apropiada con `Migration`

### Rendimiento

- Los logs se registran de forma asíncrona usando `CoroutineScope(Dispatchers.IO)`
- Los filtros usan índices de base de datos para búsquedas eficientes
- Las listas usan `Flow` para actualizaciones reactivas

### UX

- Mensajes de confirmación para operaciones destructivas
- Snackbars para feedback de operaciones
- Estados de carga visibles
- Validaciones inline en formularios

## Próximos Pasos Sugeridos

1. **Implementar la integración completa**:
   - Actualizar LoginScreen
   - Actualizar CeremonyViewModel
   - Actualizar AppNavHost
   - Agregar botón de configuración en pantalla principal

2. **Mejorar funcionalidades**:
   - Exportar logs a CSV/JSON
   - Filtros de fecha con date picker
   - Búsqueda por texto en logs
   - Estadísticas de inyecciones

3. **Seguridad**:
   - Implementar hash de contraseñas (BCrypt, Argon2)
   - Agregar autenticación biométrica
   - Logs de auditoría de cambios de usuarios
   - Expiración de sesiones

4. **Migración de BD**:
   - Implementar `Migration` apropiada para no perder datos
   - Backup automático antes de actualizar

## Archivos Creados/Modificados

### Archivos Nuevos
- `persistence/src/main/java/com/example/persistence/entities/InjectionLogEntity.kt`
- `persistence/src/main/java/com/example/persistence/dao/InjectionLogDao.kt`
- `persistence/src/main/java/com/example/persistence/repository/InjectionLogRepository.kt`
- `injector/src/main/java/com/vigatec/injector/util/InjectionLogger.kt`
- `injector/src/main/java/com/vigatec/injector/viewmodel/LogsViewModel.kt`
- `injector/src/main/java/com/vigatec/injector/ui/screens/LogsScreen.kt`
- `injector/src/main/java/com/vigatec/injector/viewmodel/ConfigViewModel.kt`
- `injector/src/main/java/com/vigatec/injector/ui/screens/ConfigScreen.kt`
- `injector/src/main/java/com/vigatec/injector/viewmodel/UserManagementViewModel.kt`
- `injector/src/main/java/com/vigatec/injector/ui/screens/UserManagementScreen.kt`

### Archivos Modificados
- `injector/src/main/java/com/vigatec/injector/data/local/entity/User.kt`
- `injector/src/main/java/com/vigatec/injector/data/local/dao/UserDao.kt`
- `injector/src/main/java/com/vigatec/injector/repository/UserRepository.kt`
- `injector/src/main/java/com/vigatec/injector/data/local/database/AppDatabase.kt`
- `injector/src/main/java/com/vigatec/injector/di/AppModule.kt`
- `app/src/main/java/com/vigatec/android_injector/ui/navigation/Routes.kt`

## Soporte

Para cualquier duda o problema con la implementación, consultar este documento o revisar los comentarios en el código fuente de cada componente.
