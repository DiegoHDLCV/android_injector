# Implementación de Conexión con VTMS en Android Injector

## Resumen

Se ha implementado exitosamente la funcionalidad de conexión con VTMS (Vanstone Terminal Management System) en el proyecto Android Injector, siguiendo la arquitectura multi-fabricante existente. Esta implementación permite descargar parámetros de configuración desde el servidor VTMS mediante AIDL.

## Cambios Realizados

### 1. Módulo `manufacturer`

#### 1.1 Configuración AIDL en `build.gradle.kts`
- ✅ Habilitado soporte AIDL
- ✅ Configurado directorio de archivos AIDL

```kotlin
buildFeatures {
    aidl = true
}
sourceSets {
    getByName("main") {
        aidl.srcDirs("src/main/aidl")
    }
}
```

#### 1.2 Archivos AIDL
Se crearon los archivos AIDL necesarios para comunicación con VTMS:

- ✅ `VTmsManager.aidl` - Interface principal del servicio VTMS
- ✅ `IParamManager.aidl` - Gestor de descarga de parámetros
- ✅ `OnTransResultListener.aidl` - Listener para callbacks de resultados

**Ubicación:** `manufacturer/src/main/aidl/com/vtms/client/`

#### 1.3 VTMSClientConnectionManager
- ✅ Implementación Singleton para gestión de conexión AIDL
- ✅ Manejo de ciclo de vida de la conexión
- ✅ Timeout de 20 segundos para conexión
- ✅ Logging detallado de todas las operaciones
- ✅ Verificación de disponibilidad del servicio VTMS

**Ubicación:** `manufacturer/src/main/java/com/example/manufacturer/libraries/aisino/vtms/VTMSClientConnectionManager.kt`

**Métodos principales:**
- `init(context: Application)` - Inicializa el manager
- `requestApplicationParameter()` - Solicita descarga de parámetros
- `closeConnection()` - Cierra la conexión
- `isVtmsServiceAvailable()` - Verifica disponibilidad

#### 1.4 Extensión de `ITmsController`
Se agregaron nuevos métodos a la interfaz base:

```kotlin
fun downloadParametersFromVtms(
    packageName: String,
    onSuccess: (parametersJson: String) -> Unit,
    onError: (errorMessage: String) -> Unit
)

fun isVtmsAvailable(): Boolean
```

**Ubicación:** `manufacturer/src/main/java/com/example/manufacturer/base/controllers/tms/ITmsController.kt`

#### 1.5 Implementación en `AisinoTmsController`
- ✅ Implementados métodos de descarga VTMS
- ✅ Integración con VTMSClientConnectionManager
- ✅ Verificación de disponibilidad de servicio
- ✅ Logging exhaustivo de operaciones

**Ubicación:** `manufacturer/src/main/java/com/example/manufacturer/libraries/aisino/wrapper/AisinoTmsController.kt`

#### 1.6 Actualización de `AisinoTmsManager`
- ✅ Inicialización de VTMSClientConnectionManager
- ✅ Paso de contexto a AisinoTmsController

**Ubicación:** `manufacturer/src/main/java/com/example/manufacturer/libraries/aisino/AisinoTmsManager.kt`

### 2. Módulo `injector`

#### 2.1 ViewModel - `TmsConfigViewModel`
Se agregaron nuevas propiedades y métodos:

**Nuevas propiedades en `TmsConfigUiState`:**
- `isVtmsAvailable: Boolean` - Indica si VTMS está disponible
- `isDownloadingFromVtms: Boolean` - Indica si hay descarga en progreso

**Nuevo método:**
```kotlin
fun downloadParametersFromVtms()
```

Este método:
1. Verifica disponibilidad del TMS y VTMS
2. Solicita descarga de parámetros del package actual
3. Actualiza UI con el resultado
4. Recarga los parámetros locales después de descarga exitosa

**Ubicación:** `injector/src/main/java/com/vigatec/injector/viewmodel/TmsConfigViewModel.kt`

#### 2.2 UI - `TmsConfigScreen`
Se agregó un nuevo componente `VtmsStatusCard`:

- ✅ Card informativa sobre disponibilidad de VTMS
- ✅ Botón "Descargar Parámetros desde VTMS"
- ✅ Indicador de progreso durante descarga
- ✅ Descripción clara de la funcionalidad

**Ubicación:** `injector/src/main/java/com/vigatec/injector/ui/screens/TmsConfigScreen.kt`

#### 2.3 AndroidManifest
Se agregaron los permisos necesarios para VTMS:

```xml
<!-- Permisos para VTMS -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>

<!-- Queries para visibilidad del servicio VTMS -->
<queries>
    <package android:name="com.vanstone.appsdk.api"/>
    <package android:name="com.vtms.client"/>
</queries>
```

**Ubicación:** `injector/src/main/AndroidManifest.xml`

## Arquitectura de la Solución

```
┌─────────────────────────────────────────────────────────────────┐
│                      Aplicación Injector                        │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           TmsConfigScreen (UI)                           │  │
│  │  • Muestra disponibilidad de VTMS                        │  │
│  │  • Botón para descargar desde VTMS                       │  │
│  └────────────────────────┬─────────────────────────────────┘  │
│                           ↓                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         TmsConfigViewModel                               │  │
│  │  • downloadParametersFromVtms()                          │  │
│  │  • Manejo de estado UI                                   │  │
│  └────────────────────────┬─────────────────────────────────┘  │
│                           ↓                                     │
└───────────────────────────┼─────────────────────────────────────┘
                            ↓
┌───────────────────────────┼─────────────────────────────────────┐
│                  Módulo Manufacturer                            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         TmsSDKManager (Delegator)                        │  │
│  │  • Delega a implementación específica del fabricante     │  │
│  └────────────────────────┬─────────────────────────────────┘  │
│                           ↓                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         AisinoTmsManager                                 │  │
│  │  • Inicializa VTMSClientConnectionManager                │  │
│  │  • Crea AisinoTmsController                              │  │
│  └────────────────────────┬─────────────────────────────────┘  │
│                           ↓                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         AisinoTmsController (ITmsController)             │  │
│  │  • getTmsParameter() - Lee parámetros locales            │  │
│  │  • downloadParametersFromVtms() - Descarga desde VTMS    │  │
│  │  • isVtmsAvailable() - Verifica disponibilidad           │  │
│  └────────────────────────┬─────────────────────────────────┘  │
│                           ↓                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │    VTMSClientConnectionManager (Singleton)               │  │
│  │  • Maneja conexión AIDL con servicio VTMS                │  │
│  │  • requestApplicationParameter()                         │  │
│  │  • Callbacks de éxito/error                              │  │
│  └────────────────────────┬─────────────────────────────────┘  │
│                           ↓                                     │
│                   ServiceConnection (AIDL)                      │
│                           ↓                                     │
└───────────────────────────┼─────────────────────────────────────┘
                            ↓
┌───────────────────────────┼─────────────────────────────────────┐
│                   Servicio VTMS                                 │
│                   (com.vtms.client)                             │
│                                                                 │
│  ┌──────────────────┐    ┌───────────────────────────────┐    │
│  │  VTmsManager     │───→│    IParamManager              │    │
│  │  (Servicio AIDL) │    │  • paramDownLoad()            │    │
│  │                  │    │  • paramUseResult()           │    │
│  └──────────────────┘    └───────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## Flujo de Operación

### 1. Inicialización
1. La app arranca y `SDKInitManager` inicializa todos los SDKs
2. `AisinoTmsManager.initialize()` es llamado
3. Se inicializa `VTMSClientConnectionManager` con el contexto de aplicación
4. Se crea `AisinoTmsController` con el contexto

### 2. Verificación de Disponibilidad
1. `TmsConfigViewModel` verifica disponibilidad del TMS
2. Llama a `controller.isVtmsAvailable()`
3. Se verifica si el servicio VTMS está instalado
4. UI muestra estado de disponibilidad

### 3. Descarga de Parámetros
1. Usuario presiona botón "Descargar Parámetros desde VTMS"
2. `viewModel.downloadParametersFromVtms()` es invocado
3. Se obtiene el controller del TMS
4. Se verifica disponibilidad de VTMS
5. Se llama a `controller.downloadParametersFromVtms(packageName, onSuccess, onError)`
6. `VTMSClientConnectionManager` establece conexión AIDL:
   - Crea Intent con action `com.vtms.api_service`
   - Bind con servicio VTMS
   - Espera conexión (máx 20s)
7. Una vez conectado:
   - Obtiene `IParamManager` del servicio
   - Llama a `paramDownLoad()` con el package name
   - Registra listener para callbacks
8. El servicio VTMS procesa la solicitud:
   - Se conecta al servidor VTMS
   - Descarga parámetros configurados para la app
   - Retorna JSON con los parámetros
9. Callback de éxito:
   - Los parámetros son procesados
   - UI muestra mensaje de éxito
   - Se recargan parámetros locales
10. Se cierra conexión AIDL

### 4. Lectura de Parámetros
Después de descargar desde VTMS, los parámetros se pueden leer normalmente:
```kotlin
val tmsController = TmsSDKManager.getTmsController()
val apiUrl = tmsController?.getTmsParameter("url_api")
```

## Uso de la Funcionalidad

### Desde la UI
1. Abre la app Injector
2. Navega a **Configuración** (ícono engranaje)
3. Selecciona **Terminal Management System (TMS)**
4. Si VTMS está disponible, verás una card azul/terciaria
5. Presiona **"Descargar Parámetros desde VTMS"**
6. Espera la descarga (verás un indicador de progreso)
7. Los parámetros descargados aparecerán en la lista

### Desde el Código
```kotlin
// Obtener el controller
val tmsController = TmsSDKManager.getTmsController()

// Verificar si VTMS está disponible
if (tmsController?.isVtmsAvailable() == true) {
    // Descargar parámetros
    tmsController.downloadParametersFromVtms(
        packageName = context.packageName,
        onSuccess = { parametersJson ->
            Log.d("VTMS", "Parámetros descargados: $parametersJson")
            // Los parámetros ahora están disponibles
            val apiUrl = tmsController.getTmsParameter("url_api")
        },
        onError = { errorMessage ->
            Log.e("VTMS", "Error: $errorMessage")
        }
    )
}
```

## Requisitos del Sistema

### En el Dispositivo
1. ✅ Dispositivo Aisino/Vanstone con Android 7.0+ (API 24+)
2. ⚠️ **Servicio VTMS instalado** (package: `com.vtms.client`) - **CRÍTICO**
3. ✅ App debe tener permiso `QUERY_ALL_PACKAGES` (solo para Android 11+)
4. ✅ Conectividad de red (para que VTMS descargue desde servidor)

### ⚠️ IMPORTANTE: Archivo param.env

**NO crear archivos `param.env` manualmente**. Según la documentación oficial de VTMS:

1. Los parámetros se descargan automáticamente desde el servidor VTMS
2. El servicio VTMS escribe el archivo `param.env` automáticamente
3. La aplicación solo debe LEER parámetros con `SystemApi.GetEnv_Api`
4. Solo VTMS debe ESCRIBIR en `param.env`

**Flujo correcto:**
```
Usuario presiona "Descargar desde VTMS" 
  → App conecta vía AIDL con servicio VTMS
  → VTMS descarga del servidor y escribe param.env
  → App lee parámetros con SystemApi.GetEnv_Api
```

### En el Servidor TMS
1. Configurar parámetros para la aplicación en la plataforma web VTMS
2. Formato JSON recomendado:
```json
{
  "url_api": "https://api.example.com/v1",
  "timeout_ms": "30000",
  "merchant_id": "MERCHANT_001",
  "terminal_id": "TERM_12345",
  "api_key": "sk_live_xxxxxxxxxx",
  "env": "prod"
}
```

## Logging y Debugging

### Tags de Log Importantes
- `VTMSClientConnectionManager` - Operaciones de conexión AIDL
- `AisinoTmsController` - Operaciones de descarga
- `TmsConfigViewModel` - Lógica de UI y estado

### Logs de Descarga Exitosa
```
VTMSClientConnectionManager: ════════════════════════════════════════════════════════════
VTMSClientConnectionManager: Iniciando binding con servicio VTMS...
VTMSClientConnectionManager: ✓ bindService() exitoso, esperando onServiceConnected()...
VTMSClientConnectionManager: ✓ Servicio VTMS conectado exitosamente
VTMSClientConnectionManager: ✓ Conexión VTMS establecida exitosamente
AisinoTmsController: ✓ Parámetros descargados exitosamente desde VTMS
TmsConfigViewModel: ✓ Parámetros descargados exitosamente desde VTMS
```

### Logs de Error Común
```
VTMSClientConnectionManager: ✗ bindService() retornó false - Servicio VTMS no disponible
  Posibles causas:
  1. La app VTMS (com.vtms.client) no está instalada
  2. El servicio no está exportado correctamente
  3. Faltan permisos en el Manifest
```

## Diferencias con Otros Fabricantes

Esta implementación es **específica para Aisino/Vanstone**. Para otros fabricantes:

### NEWPOS
- ❌ VTMS no soportado
- ✅ Usa `DummyTmsManager` (retorna error al intentar descargar)
- ✅ `isVtmsAvailable()` retorna `false`

### UROVO
- ❌ VTMS no soportado
- ✅ Usa `DummyTmsManager` (retorna error al intentar descargar)
- ✅ `isVtmsAvailable()` retorna `false`

La arquitectura permite agregar implementaciones específicas de VTMS para otros fabricantes en el futuro.

## Ventajas de esta Implementación

1. ✅ **Coherente con la arquitectura existente** - Sigue el patrón de managers por fabricante
2. ✅ **No invasiva** - No rompe funcionalidad existente
3. ✅ **Extensible** - Fácil agregar VTMS para otros fabricantes
4. ✅ **Robusta** - Manejo de errores y timeouts
5. ✅ **Bien documentada** - Logging detallado en cada paso
6. ✅ **Testeable** - Separación clara de responsabilidades
7. ✅ **UX clara** - UI intuitiva con feedback visual

## Posibles Mejoras Futuras

1. **Persistencia de parámetros** - Guardar parámetros descargados en base de datos local
2. **Sincronización automática** - Descarga periódica de parámetros
3. **Notificación de cambios** - Alertar cuando hay nuevos parámetros en el servidor
4. **Histórico de descargas** - Log de todas las descargas realizadas
5. **Configuración de servidor** - Permitir cambiar servidor VTMS desde UI
6. **Soporte multi-fabricante** - Implementar VTMS para Newpos/Urovo si tienen sistema similar

## Notas Importantes

⚠️ **QUERY_ALL_PACKAGES**: Este permiso requiere justificación especial en Google Play Store. Documentar su uso para revisión de la tienda.

⚠️ **Timeout**: Si el servicio VTMS tarda más de 20 segundos en responder, la conexión fallará. Ajustar `CONNECTING_PENDING_DURATION` si es necesario.

⚠️ **Thread Safety**: Los callbacks de AIDL se ejecutan en threads del Binder. La implementación usa `Dispatchers.Main` para actualizar UI correctamente.

⚠️ **Ciclo de Vida**: La conexión AIDL se cierra automáticamente después de descargar. No mantener conexiones abiertas innecesariamente.

⚠️ **Servicio VTMS Requerido**: La funcionalidad de descarga SOLO funciona si el servicio VTMS (`com.vtms.client`) está instalado. Sin él, solo se puede usar la creación manual de parámetros de prueba (para desarrollo).

## Troubleshooting: "VTMS disponible: false"

Si ves en los logs `VTMS disponible: false`, significa:

1. **El paquete VTMS no está instalado**:
   ```
   VTMSClientConnectionManager: ✗ Servicio VTMS NO disponible
   VTMSClientConnectionManager:   - El paquete VTMS NO está instalado en el dispositivo
   ```
   **Solución**: Instalar la app VTMS en el dispositivo

2. **El servicio no es accesible**:
   ```
   VTMSClientConnectionManager: ✗ Servicio VTMS NO disponible
   VTMSClientConnectionManager:   - El paquete VTMS SÍ está instalado, pero el servicio no es accesible
   ```
   **Solución**: Verificar que el servicio esté exportado y accesible

3. **Faltan permisos**:
   - Verificar que `QUERY_ALL_PACKAGES` esté en el Manifest
   - Verificar que `<queries>` incluya `com.vtms.client`

### Modo de Desarrollo Sin VTMS

Si VTMS no está disponible, la app ofrece un botón para **"Crear Parámetros de Prueba"** que:
- Crea un archivo `param.env` manualmente con valores de prueba
- Solo para desarrollo/testing
- En producción, SIEMPRE usar VTMS para descargar parámetros reales

## Conclusión

La implementación de conexión con VTMS está completa y lista para usar. Sigue la arquitectura del proyecto, es robusta y bien documentada. La funcionalidad está disponible en la pantalla de Configuración TMS y puede ser utilizada programáticamente desde cualquier parte de la aplicación.

---

**Fecha de implementación:** 16 de Octubre, 2025  
**Versión:** 1.0  
**Autor:** Sistema de IA - Claude  

