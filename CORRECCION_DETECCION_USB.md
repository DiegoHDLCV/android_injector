# Corrección de Detección de Cable USB

## Fecha
2025-10-10

## Problema Reportado
El indicador de cable USB siempre mostraba "CONECTADO" incluso cuando el cable estaba desconectado. La detección no era confiable.

## Causa Raíz Identificada

La lógica anterior intentaba hacer pruebas complejas (write, read) que no eran confiables:

1. **Escritura sin respuesta**: `write()` puede retornar éxito incluso sin cable en el otro extremo
2. **Lectura con timeout**: `readData()` retorna timeout (-1) cuando no hay datos, lo cual se interpretaba como "cable presente"
3. **Lógica compleja**: Demasiadas condiciones que causaban falsos positivos

## Solución Implementada

### 1. Simplificación del Método `detectCableConnection()`

**Antes**: 120+ líneas con lógica compleja de write/read
**Ahora**: ~40 líneas simples y directas

**Lógica nueva**:
```kotlin
1. Crear instancia NUEVA del controller (Aisino o Newpos)
2. Llamar a init() para configurar parámetros
3. Llamar a open() - ESTA ES LA PRUEBA REAL
4. Cerrar inmediatamente el puerto
5. Si open() == 0 → Cable PRESENTE
6. Si open() != 0 → Cable AUSENTE
```

**Por qué funciona**: El método `open()` internamente llama a `Rs232Api.PortOpen_Api()` (Aisino) o `SerialPort.getInstance()` (Newpos), que solo tienen éxito si el hardware físico está disponible.

### 2. Logs Visibles en la UI

**Antes**: Solo `Log.v()` que no se mostraban en el panel de logs
**Ahora**: Uso de `CommLog` para logs visibles

**Logs implementados**:
- `CommLog.d()` - Debug: Inicio de detección, pasos del proceso
- `CommLog.i()` - Info: Cable detectado con éxito
- `CommLog.w()` - Warning: Cable no detectado
- `CommLog.e()` - Error: Errores durante detección

**Ejemplo de logs visibles**:
```
[D] MainViewModel: 🔍 Iniciando detección de cable USB...
[D] MainViewModel: → Fabricante: AISINO
[D] MainViewModel: → Paso 1/2: Configurando parámetros COM...
[D] MainViewModel: → Paso 2/2: Intentando abrir puerto físico...
[I] MainViewModel: ✓ Cable USB DETECTADO (open exitoso)
[I] MainViewModel: 🔌 ✅ CABLE USB CONECTADO - Listo para comunicación
```

### 3. Mejora de la UI

**Indicador de cable mejorado**:
- Título más grande y visible
- Texto descriptivo adicional con instrucciones
- Mejor contraste de colores (verde oscuro / rojo)

**Estructura**:
```
┌─────────────────────────────────────────┐
│      🔌 Cable USB CONECTADO             │ ← Título grande
│                                         │
│  ✓ Puerto físico disponible. Pulse     │ ← Instrucción
│    'Iniciar Escucha' para comenzar.     │
└─────────────────────────────────────────┘
```

## Archivos Modificados

### 1. `app/src/main/java/com/vigatec/android_injector/viewmodel/MainViewModel.kt`

**Método `detectCableConnection()`** (líneas 841-920):
- ✅ Simplificado de 120+ líneas a ~40 líneas
- ✅ Lógica directa: solo init() + open()
- ✅ Logs visibles con CommLog
- ✅ Cierre inmediato del puerto temporal

**Método `startCableDetection()`** (líneas 814-826):
- ✅ Agregados logs visibles cuando cambia el estado del cable
- ✅ `CommLog.i()` cuando se detecta cable
- ✅ `CommLog.w()` cuando se desconecta cable

### 2. `app/src/main/java/com/vigatec/android_injector/ui/screens/MainScreen.kt`

**Indicador de cable USB** (líneas 85-119):
- ✅ Cambiado de Row simple a Column con más información
- ✅ Título más grande (titleLarge en vez de titleMedium)
- ✅ Agregado texto descriptivo con instrucciones
- ✅ Mejor espaciado y padding

**Panel de logs** (línea 262):
- ✅ Actualizado comentario para indicar que incluye logs de detección

## Cómo Probar

### Prueba 1: Detección de cable desconectado
1. **Desconectar** el cable USB
2. **Iniciar** la app
3. **Verificar**:
   - Indicador muestra "⚠️ Cable USB NO DETECTADO" (fondo rojo)
   - Panel de logs muestra: `[W] MainViewModel: ✗ Cable USB NO DETECTADO (open falló: -3)`

### Prueba 2: Detección de cable conectado
1. **Conectar** el cable USB
2. **Esperar** máximo 3 segundos (intervalo de detección)
3. **Verificar**:
   - Indicador cambia a "🔌 Cable USB CONECTADO" (fondo verde)
   - Panel de logs muestra: `[I] MainViewModel: ✓ Cable USB DETECTADO (open exitoso)`
   - Snackbar notifica: "Cable USB detectado. Pulse 'Iniciar Escucha' para comenzar."

### Prueba 3: Desconexión durante uso
1. **Conectar** cable y **pulsar** "Iniciar Escucha"
2. **Desconectar** el cable físicamente
3. **Esperar** máximo 3 segundos
4. **Verificar**:
   - Indicador cambia a rojo "⚠️ Cable USB NO DETECTADO"
   - Panel de logs muestra: `[W] MainViewModel: ⚠️ CABLE USB DESCONECTADO - Reconecte el cable`
   - Snackbar notifica: "Cable USB desconectado"

### Prueba 4: Reconexión
1. Con cable desconectado, **reconectar** el cable
2. **Esperar** máximo 3 segundos
3. **Verificar**:
   - Indicador vuelve a verde
   - Logs muestran detección exitosa

## Códigos de Error

### Para AISINO
- `0`: Éxito (cable presente)
- `-3`: ERROR_OPEN_FAILED (cable no conectado, puerto en uso, o sin permisos)
- `-10`: ERROR_SET_BAUD_FAILED (error al configurar baud rate)
- `-99`: ERROR_GENERAL_EXCEPTION (excepción inesperada)

### Para NEWPOS
- `0`: Éxito (cable presente)
- `-3`: ERROR_OPEN_FAILED (ningún puerto disponible)
- `-99`: ERROR_GENERAL_EXCEPTION (IOException u otra excepción)

## Ventajas de la Nueva Implementación

1. ✅ **Confiable**: Usa la API nativa de apertura de puerto que verifica hardware real
2. ✅ **Simple**: Lógica directa sin condiciones complejas
3. ✅ **Visible**: Logs aparecen en el panel de comunicación
4. ✅ **Eficiente**: No hace operaciones innecesarias de lectura/escritura
5. ✅ **No invasiva**: Cierra el puerto inmediatamente sin interferir con la escucha
6. ✅ **Informativa**: UI mejorada con instrucciones claras

## Notas Técnicas

### ¿Por qué funciona open() pero no write()?

**`open()`**: 
- Intenta acceder directamente al hardware USB/Serial
- Retorna error si el dispositivo físico no está disponible
- Es una verificación de bajo nivel del kernel/driver

**`write()`**:
- Puede tener éxito si el puerto está abierto, incluso sin cable en el otro extremo
- El sistema operativo bufferiza los datos
- No verifica si hay un receptor físico

### Intervalo de Detección

**Actual**: 3 segundos
**Configurable en**: `MainViewModel.kt` línea 830

```kotlin
kotlinx.coroutines.delay(3000) // Cambiar aquí el intervalo
```

**Recomendaciones**:
- 3 segundos: Balance entre responsividad y consumo de batería
- 1 segundo: Más rápido pero más consumo
- 5 segundos: Más lento pero menos consumo

## Próximos Pasos Opcionales

- [ ] Agregar configuración del intervalo de detección en Settings
- [ ] Agregar botón "Detectar Ahora" para forzar detección inmediata
- [ ] Agregar estadísticas de detección (intentos, éxitos, fallos)
- [ ] Agregar log histórico de conexiones/desconexiones con timestamp
- [ ] Vibración o sonido cuando se detecta/desconecta cable

## Referencias

- Manual Vanstone Android POS API v2.00 - Sección Rs232Api
- Código fuente: `AisinoComController.kt` líneas 91-154
- Código fuente: `NewposComController.kt`


