# Implementación de Terminal Management System (TMS)

## Descripción General

Se ha implementado un sistema completo de TMS (Terminal Management System) para gestionar la configuración de terminales POS desde una plataforma centralizada. La implementación sigue la arquitectura existente de la aplicación, utilizando el patrón de managers específicos por fabricante.

## Arquitectura

### Ubicación de Módulos

La funcionalidad TMS se ha implementado en el módulo `manufacturer` porque:

1. **TMS es específico del fabricante**: Cada fabricante tiene su propio SDK y forma de acceder a los parámetros del TMS
2. **Coherencia con la arquitectura existente**: Similar a `KeySDKManager`, que delega a implementaciones específicas por fabricante
3. **Separación de responsabilidades**: El módulo `communication` se enfoca en protocolos de comunicación serial/USB

### Componentes Principales

```
manufacturer/
├── base/
│   └── controllers/
│       ├── manager/
│       │   └── ITmsManager.kt              # Interfaz base para TMS
│       └── tms/
│           ├── ITmsController.kt           # Interfaz para operaciones TMS
│           └── TmsException.kt             # Excepción personalizada TMS
├── libraries/
│   └── aisino/
│       ├── AisinoTmsManager.kt             # Manager específico Aisino/Vanstone
│       └── wrapper/
│           └── AisinoTmsController.kt      # Controller usando SDK Vanstone
└── TmsSDKManager.kt                        # Delegador principal (similar a KeySDKManager)

injector/
├── viewmodel/
│   └── TmsConfigViewModel.kt               # ViewModel para gestión de estado
└── ui/screens/
    └── TmsConfigScreen.kt                  # Pantalla de configuración TMS
```

## Implementación por Fabricante

### Aisino/Vanstone

La implementación para Aisino utiliza el SDK de Vanstone, específicamente la clase `SystemApi` con el método `GetEnv_Api`.

#### Ejemplo de Uso del SDK

```kotlin
import com.vanstone.trans.api.SystemApi

// Leer un parámetro del TMS
val paramValueBuffer = ByteArray(256)
val result = SystemApi.GetEnv_Api(
    "url_api",           // Nombre del parámetro
    paramValueBuffer,    // Buffer para el valor
    0,                   // Offset
    256,                 // Longitud del buffer
    1,                   // Flag
    256                  // Tamaño máximo
)

if (result == 1) {
    val value = String(paramValueBuffer).trim()
    // Usar el valor...
}
```

#### AisinoTmsController

El controlador implementa `ITmsController` y proporciona métodos para:

- `getTmsParameter(paramName: String)`: Lee un parámetro específico
- `getTmsParameter(paramName, defaultValue)`: Lee con valor por defecto
- `getTmsParameters(paramNames: List<String>)`: Lee múltiples parámetros
- `hasTmsParameter(paramName)`: Verifica existencia de un parámetro

**Nota**: Se utiliza reflexión para evitar dependencias en tiempo de compilación del SDK. En producción, se recomienda usar la importación directa si el SDK está disponible.

### Otros Fabricantes

Para agregar soporte para otros fabricantes (NewPos, Urovo, etc.):

1. Crear `XxxTmsManager` en `manufacturer/libraries/xxx/`
2. Crear `XxxTmsController` con la implementación específica del SDK
3. Actualizar `TmsSDKManager` para incluir el nuevo fabricante:

```kotlin
private val manager: ITmsManager by lazy {
    when (SystemConfig.managerSelected) {
        EnumManufacturer.AISINO -> AisinoTmsManager
        EnumManufacturer.NEWPOS -> NewposTmsManager  // Agregar aquí
        EnumManufacturer.UROVO -> UrovoTmsManager    // Agregar aquí
        else -> DummyTmsManager
    }
}
```

## Configuración en la Plataforma TMS

### Parámetros Soportados

La aplicación busca automáticamente los siguientes parámetros comunes:

| Parámetro | Descripción |
|-----------|-------------|
| `url_api` | URL del API del servidor |
| `timeout_ms` | Timeout de conexión (milisegundos) |
| `merchant_id` | ID del comercio |
| `terminal_id` | ID del terminal |
| `api_key` | Clave API para autenticación |
| `env` | Ambiente (prod/test) |
| `log_level` | Nivel de log |
| `max_retries` | Número máximo de reintentos |

### Formato JSON en TMS

En la interfaz web del TMS, configura los parámetros en formato JSON:

```json
{
  "url_api": "https://api.example.com/v1",
  "timeout_ms": "30000",
  "merchant_id": "MERCHANT_001",
  "terminal_id": "TERM_12345",
  "api_key": "sk_live_xxxxxxxxxx",
  "env": "prod",
  "log_level": "info",
  "max_retries": "3"
}
```

### Flujo de Sincronización

1. **En el servidor TMS**: Configuras los parámetros para la aplicación o dispositivo específico
2. **El terminal se comunica con TMS**: Los parámetros se descargan y almacenan localmente en el dispositivo
3. **La aplicación lee los parámetros**: Usando el SDK del fabricante (`SystemApi.GetEnv_Api` para Aisino)

**Importante**: Los parámetros no se guardan en un archivo de texto simple. El SDK actúa como una capa de abstracción segura para acceder a estos valores.

## Uso en la Aplicación

### Desde el Menú de Configuración

1. Navegar a **Configuración** (ícono de engranaje en el dashboard)
2. Seleccionar **Terminal Management System (TMS)**
3. La pantalla mostrará:
   - Estado de disponibilidad del TMS
   - Lista de parámetros configurados
   - Buscador de parámetros personalizados

### Desde el Código

#### Inicialización

El TMS se inicializa automáticamente al inicio de la aplicación a través de `SDKInitManager`, que inicializa todos los SDKs (Key, Communication y TMS) de forma secuencial.

**Nota**: La inicialización de TMS está protegida con try-catch ya que puede no estar disponible en todos los dispositivos. Si falla, solo se registra un warning y la aplicación continúa normalmente.

El proceso de inicialización se dispara desde el `SplashViewModel` cuando la app arranca:

```kotlin
// En SDKInitManager.kt (ya implementado)
suspend fun initializeOnce(app: Application) {
    // ...
    Log.d(TAG, "-> Inicializando TmsSDKManager...")
    try {
        TmsSDKManager.initialize(app)
        Log.i(TAG, "-> TmsSDKManager inicializado con éxito.")
    } catch (e: Exception) {
        // TMS puede no estar disponible en todos los dispositivos, no es crítico
        Log.w(TAG, "-> TmsSDKManager no pudo inicializarse: ${e.message}")
    }
    // ...
}
```

#### Leer Parámetros

```kotlin
import com.example.manufacturer.TmsSDKManager

// Obtener el controller
val tmsController = TmsSDKManager.getTmsController()

if (tmsController != null) {
    // Leer un parámetro
    val apiUrl = tmsController.getTmsParameter("url_api")

    // Leer con valor por defecto
    val timeout = tmsController.getTmsParameter("timeout_ms", "30000")

    // Verificar existencia
    if (tmsController.hasTmsParameter("api_key")) {
        val apiKey = tmsController.getTmsParameter("api_key")
    }

    // Leer múltiples parámetros
    val params = tmsController.getTmsParameters(
        listOf("url_api", "merchant_id", "terminal_id")
    )
} else {
    Log.w("TMS", "TMS no disponible en este dispositivo")
}
```

#### Integración con Retrofit

```kotlin
// Configurar cliente HTTP con parámetros del TMS
val tmsController = TmsSDKManager.getTmsController()
val baseUrl = tmsController?.getTmsParameter("url_api") ?: "https://default.api.com"
val timeout = tmsController?.getTmsParameter("timeout_ms", "30000")?.toLongOrNull() ?: 30000L

val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

## Pantalla de Configuración TMS

### Características

1. **Indicador de Estado**: Muestra si el TMS está disponible en el dispositivo
2. **Búsqueda de Parámetros**: Permite buscar cualquier parámetro por nombre
3. **Lista de Parámetros Comunes**: Muestra automáticamente los parámetros configurados
4. **Actualización Manual**: Botón de refresh para recargar parámetros

### Estados de la UI

- **TMS Disponible (verde)**: El SDK está disponible y responde correctamente
- **TMS No Disponible (rojo)**: El fabricante no soporta TMS o el SDK no está instalado
- **Cargando**: Mostrando indicador de progreso mientras se leen parámetros
- **Parámetro Encontrado**: Muestra el valor en un contenedor destacado
- **Parámetro No Encontrado**: Mensaje informativo

## Manejo de Errores

### TmsException

Se ha creado una excepción personalizada para errores relacionados con TMS:

```kotlin
try {
    val value = tmsController.getTmsParameter("url_api")
} catch (e: TmsException) {
    Log.e("TMS", "Error al leer parámetro TMS: ${e.message}")
    // Manejar error específico de TMS
} catch (e: Exception) {
    Log.e("TMS", "Error inesperado", e)
}
```

### Casos Comunes

| Error | Causa | Solución |
|-------|-------|----------|
| SDK no disponible | El SDK de Vanstone no está instalado | Verificar que el SDK esté incluido en el proyecto |
| Método no encontrado | Versión incorrecta del SDK | Actualizar el SDK a la versión correcta |
| Parámetro no encontrado | No configurado en TMS | Configurar el parámetro en la plataforma TMS |
| TMS no inicializado | No se llamó a `initialize()` | Inicializar TmsSDKManager al inicio |

## Pruebas

### Pruebas Manuales

1. **Dispositivo sin TMS**:
   - La pantalla debe mostrar "TMS No Disponible"
   - No debe crashear la aplicación

2. **Dispositivo con TMS pero sin parámetros**:
   - Debe mostrar "TMS Disponible"
   - Lista vacía de parámetros
   - Mensaje informativo

3. **Dispositivo con TMS y parámetros**:
   - Lista de parámetros configurados
   - Búsqueda funcional
   - Valores correctos

### Pruebas de Integración

```kotlin
@Test
fun testTmsParameterRetrieval() {
    // Dado: TMS inicializado
    TmsSDKManager.initialize(mockApplication)

    // Cuando: Se lee un parámetro
    val controller = TmsSDKManager.getTmsController()
    val value = controller?.getTmsParameter("url_api")

    // Entonces: Se obtiene el valor correcto
    assertNotNull(value)
    assertTrue(value!!.startsWith("http"))
}
```

## Seguridad

### Consideraciones

1. **Almacenamiento Seguro**: Los parámetros no se guardan en archivos de texto simples. El SDK del fabricante proporciona acceso seguro.

2. **Parámetros Sensibles**: Para parámetros como `api_key`, considera:
   - No mostrarlos en logs
   - Enmascarar valores en la UI
   - Usar HTTPS para comunicación

3. **Validación**: Valida siempre los valores recibidos del TMS:

```kotlin
val timeout = tmsController.getTmsParameter("timeout_ms")
val timeoutValue = timeout?.toLongOrNull()?.takeIf { it > 0 } ?: 30000L
```

## Troubleshooting

### El TMS no está disponible

1. Verificar que el dispositivo sea Aisino/Vanstone
2. Verificar logs: buscar "TmsSDKManager" y "AisinoTmsController"
3. Verificar que el SDK de Vanstone esté instalado

### Error: FileNotFoundException param.env (SOLUCIONADO)

**Problema**: El SDK de Vanstone busca un archivo `param.env` en `/data/data/[tu_app]/files/param.env` y no lo encuentra.

**Causa**: El archivo `param.env` no existe porque:
- No se ha sincronizado con el servidor TMS, O
- No se han creado parámetros de prueba para testing

**Solución**:

#### Opción 1: Crear Parámetros de Prueba (Recomendado para Testing)

1. **Desde la UI** (Más fácil):
   - Abre la app y ve a Configuración → TMS
   - Si ves el mensaje "Archivo param.env no encontrado"
   - Presiona el botón **"Crear Parámetros de Prueba"**
   - Los parámetros de prueba serán creados automáticamente
   - ¡Listo! Ahora puedes leer los parámetros

2. **Desde el Código** (Programáticamente):
   ```kotlin
   import com.example.manufacturer.libraries.aisino.wrapper.AisinoTmsParameterHelper

   // Crear parámetros de prueba con valores por defecto
   AisinoTmsParameterHelper.createSampleParamEnvFile(context)

   // O crear parámetros personalizados
   AisinoTmsParameterHelper.createCustomParamEnvFile(
       context = context,
       urlApi = "https://api.myserver.com/v1",
       timeoutMs = "30000",
       merchantId = "MERCHANT_001",
       terminalId = "TERMINAL_001",
       apiKey = "my_api_key",
       env = "prod"
   )
   ```

3. **Manualmente** (Archivo INI):
   Crea el archivo `/data/data/com.vigatec.injector/files/param.env` con este formato:
   ```ini
   [ENV]
   url_api=https://api.example.com/v1
   timeout_ms=30000
   merchant_id=MERCHANT_001
   terminal_id=TERMINAL_001
   api_key=test_key_12345
   env=test
   ```

#### Opción 2: Sincronizar desde el Servidor TMS (Producción)

En producción, los parámetros deben descargarse desde la plataforma TMS:

1. Configurar los parámetros en la interfaz web del TMS
2. El terminal debe sincronizar con el servidor TMS
3. Los parámetros se descargarán automáticamente al archivo `param.env`
4. La aplicación podrá leerlos usando `SystemApi.GetEnv_Api`

**Notas Importantes**:
- El archivo `param.env` usa formato INI con sección `[ENV]`
- Cada parámetro se define como `clave=valor`
- El SDK de Vanstone requiere que `SystemApi.SystemInit_Api()` se haya llamado primero (ya lo hace `AisinoPedController`)
- Después de crear el archivo, los parámetros están disponibles inmediatamente

### No se encuentran parámetros

1. Verificar configuración en la plataforma TMS
2. Verificar que el terminal haya sincronizado con el TMS
3. Verificar que el archivo `param.env` existe y contiene los parámetros
4. Usar la búsqueda manual con nombres exactos de parámetros
5. Verificar logs para ver errores de lectura

### Error de reflexión

Si ves errores de "ClassNotFoundException" o "NoSuchMethodException":

1. Verificar que el SDK esté incluido en las dependencias
2. Considerar usar importación directa en lugar de reflexión
3. Verificar la versión del SDK

### Los parámetros no se actualizan

Si modificas `param.env` pero los cambios no se ven:

1. El SDK puede estar cacheando los valores
2. Reinicia la aplicación completamente
3. O llama a `release()` y luego `initialize()` en el TmsSDKManager

## Próximos Pasos

### Mejoras Futuras

1. **Caché de Parámetros**: Guardar localmente para acceso offline
2. **Sincronización Automática**: Polling periódico al servidor TMS
3. **Notificaciones**: Alertar cuando cambien parámetros críticos
4. **Validación de Parámetros**: Schema JSON para validar configuraciones
5. **Historial**: Tracking de cambios en configuraciones

### Implementación para Otros Fabricantes

Ver sección "Otros Fabricantes" arriba para instrucciones de cómo agregar soporte para NewPos, Urovo, etc.

## Referencias

- Manual del SDK: "Vanstone Android POS API programming manual v2.00"
- Clase principal: `com.vanstone.trans.api.SystemApi`
- Método clave: `GetEnv_Api`

## Contacto y Soporte

Para dudas o problemas con la implementación de TMS, consultar:
- Documentación del SDK del fabricante
- Logs de la aplicación (tag: "TmsSDKManager", "AisinoTmsController")
- Manual de la plataforma TMS
