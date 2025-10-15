# Mejoras de Logging USB y Detección de Cable

## Fecha
2025-10-10

## Objetivo
Mejorar la robustez y visibilidad del sistema de comunicación USB/COM en la aplicación **app** que recibe llaves, con énfasis en:

1. Logs extremadamente detallados del comportamiento de puertos COM
2. Control manual del inicio de escucha (botón en lugar de inicio automático)
3. Detección automática de cable USB conectado
4. Robustez en manejo de puertos COM (apertura, reinicio, errores)
5. Compatibilidad verificada con Aisino y NewPOS

## Cambios Implementados

### 1. Logs Detallados en `AisinoComController` ✅

**Archivo:** `/communication/src/main/java/com/example/communication/libraries/aisino/wrapper/AisinoComController.kt`

**Cambios:**
- **`init()`**: Logs estructurados con formato de caja que muestran:
  - Parámetros configurados (Baud Rate, Data Bits, Parity, Stop Bits)
  - Advertencias si el puerto ya está abierto

- **`open()`**: Logs paso a paso del proceso de apertura:
  - PASO 1/4: Apertura del puerto con `PortOpen_Api()`
  - PASO 2/4: Reset del puerto con `PortReset_Api()`
  - PASO 3/4: Configuración de parámetros con `PortSetBaud_Api()`
  - PASO 4/4: Confirmación de puerto listo
  - Códigos de error detallados con posibles causas:
    - Cable USB no conectado
    - Puerto en uso
    - Permisos insuficientes

- **`close()`**: Logs de cierre con manejo de errores

- **`write()`**: Logs de datos enviados (HEX y ASCII)

- **`readData()`**:
  - Logs muestreados (5% de lecturas) para evitar spam
  - Logs completos cuando se reciben datos (HEX y ASCII formateado)
  - Timeouts silenciados para evitar spam de logs

### 2. Logs Detallados en `NewposComController` ✅

**Archivo:** `/communication/src/main/java/com/example/communication/libraries/newpos/wrapper/NewposComController.kt`

**Cambios:**
- **`init()`**: Logs de búsqueda secuencial de puertos:
  - Intento con ttyUSB0 (id=7)
  - Intento con ttyACM0 (id=8)
  - Intento con ttyGS0 (id=6)
  - Causas de fallo si no se encuentra puerto

- **`open()`**: Validación de puerto inicializado

- **`close()`**: Liberación de recursos con logs

- **`write()`**: Logs de transmisión HEX

- **`readData()`**: Logs de recepción con HEX y ASCII formateado

### 3. Detección Automática de Cable USB ✅

**Archivo:** `/app/src/main/java/com/vigatec/android_injector/viewmodel/MainViewModel.kt`

**Cambios:**
- Agregado `StateFlow<Boolean> cableConnected` para estado del cable
- Agregado `cableDetectionJob` que ejecuta cada 3 segundos
- Método `startCableDetection()` iniciado automáticamente en `init{}`
- Método `detectCableConnection()` que:
  - Intenta obtener `ComController`
  - Intenta inicializar el puerto sin abrirlo
  - Retorna `true` si init exitoso (cable presente)
  - Retorna `false` si init falla (cable ausente)
  - Logs detallados de cada detección
- Notificaciones al usuario cuando:
  - Se detecta cable: "Cable USB detectado. Pulse 'Iniciar Escucha' para comenzar."
  - Se desconecta cable: "Cable USB desconectado"

### 4. UI de Estado de Cable ✅

**Archivo:** `/app/src/main/java/com/vigatec/android_injector/ui/screens/MainScreen.kt`

**Cambios:**
- **Eliminado auto-inicio** automático de escucha en `LaunchedEffect`
- **Agregado Card indicador** de estado de cable:
  - Fondo verde + texto "🔌 Cable USB CONECTADO" cuando detectado
  - Fondo rojo + texto "⚠️ Cable USB NO DETECTADO" cuando ausente
- Usuario ahora controla manualmente cuándo iniciar la escucha con el botón "Iniciar Escucha"

## Flujo de Uso

### Inicio de la Aplicación
1. **App se inicia** → `MainViewModel.init()` ejecuta
2. **Se inicia detección automática** de cable USB cada 3 segundos
3. **Usuario ve el estado** del cable en la UI (verde/rojo)
4. **NO se inicia escucha automáticamente** (cambio principal)

### Cuando se conecta el cable
1. **Detección identifica** cable conectado en próximo ciclo (max 3 seg)
2. **UI cambia** a verde con mensaje "🔌 Cable USB CONECTADO"
3. **Snackbar notifica**: "Cable USB detectado. Pulse 'Iniciar Escucha' para comenzar."
4. **Usuario presiona** "Iniciar Escucha" manualmente
5. **Se ejecuta** `startListening()` con logs detallados

### Durante la escucha
- **Logs de apertura de puerto** (4 pasos para Aisino, 3 para Newpos)
- **Logs de cada lectura con datos** (HEX + ASCII)
- **Logs de escritura** si se envían datos
- **Logs de errores** con causas probables

### Cuando se desconecta el cable
1. **Detección identifica** desconexión
2. **UI cambia** a rojo con mensaje "⚠️ Cable USB NO DETECTADO"
3. **Snackbar notifica**: "Cable USB desconectado"
4. *Nota: La escucha puede continuar activa hasta que el usuario la detenga o falle*

## Compatibilidad

### Aisino ✅
- Logs detallados en apertura (4 pasos)
- Detección mediante `Rs232Api.PortOpen_Api()`
- Manejo robusto de errores con códigos Aisino
- Reset de puerto tras apertura

### NewPOS ✅
- Logs detallados en búsqueda de puertos (ttyUSB0, ttyACM0, ttyGS0)
- Detección mediante `SerialPort.getInstance()`
- Manejo de IOException con logs claros

## Logs a Observar

### Al Iniciar la App
```
MainViewModel: === INICIALIZANDO MAINVIEWMODEL ===
MainViewModel: ╔══════════════════════════════════════════════════════════════
MainViewModel: ║ INICIANDO DETECCIÓN AUTOMÁTICA DE CABLE USB
MainViewModel: ║ ✓ Job de detección de cable iniciado
MainViewModel: ╚══════════════════════════════════════════════════════════════
```

### Detección de Cable (cada 3 seg)
```
MainViewModel: ║ 🔍 Detección: Cable USB presente (init exitoso)
// o
MainViewModel: ║ 🔍 Detección: Cable USB ausente (init falló con código -1)
```

### Al Conectar Cable
```
MainViewModel: ║ ✅ CABLE USB DETECTADO!
MainViewModel: ║    El usuario puede iniciar la escucha manualmente
```

### Al Iniciar Escucha Manualmente (Aisino)
```
AisinoComController: ╔══════════════════════════════════════════════════════════════
AisinoComController: ║ AISINO COM INIT - Puerto 0
AisinoComController: ║ ✓ Parámetros configurados:
AisinoComController: ║   • Baud Rate: 9600 bps
AisinoComController: ║   • Data Bits: 8
AisinoComController: ║   • Parity: 0
AisinoComController: ╚══════════════════════════════════════════════════════════════
AisinoComController: ╔══════════════════════════════════════════════════════════════
AisinoComController: ║ AISINO COM OPEN - Puerto 0
AisinoComController: ║ 🔌 PASO 1/4: Intentando abrir puerto 0...
AisinoComController: ║ ✓ Puerto 0 abierto exitosamente
AisinoComController: ║ 🔄 PASO 2/4: Reseteando puerto 0...
AisinoComController: ║ ✓ Puerto 0 reseteado
AisinoComController: ║ ⚙️  PASO 3/4: Configurando parámetros de comunicación...
AisinoComController: ║ ✓ Parámetros configurados correctamente
AisinoComController: ║ ✅ PASO 4/4: Puerto 0 LISTO PARA COMUNICACIÓN
AisinoComController: ╚══════════════════════════════════════════════════════════════
```

### Al Recibir Datos (Aisino)
```
AisinoComController: ╔══════════════════════════════════════════════════════════════
AisinoComController: ║ 📥 DATOS RECIBIDOS - Puerto 0
AisinoComController: ║ Bytes leídos: 24
AisinoComController: ║ Datos HEX: 024130303031323334353637383941424344454603
AisinoComController: ║ Datos ASCII: .A00012345678 9ABCDEF.
AisinoComController: ╚══════════════════════════════════════════════════════════════
```

### Al Iniciar Escucha Manualmente (NewPOS)
```
NewposComController: ╔══════════════════════════════════════════════════════════════
NewposComController: ║ NEWPOS COM INIT
NewposComController: ║ 🔍 PASO 1/3: Buscando puerto serial disponible...
NewposComController: ║     Intentando ttyUSB0 (id=7)...
NewposComController: ║ ✓ Puerto serial encontrado y configurado
NewposComController: ╚══════════════════════════════════════════════════════════════
```

## Ventajas

1. **Debugging más fácil**: Logs estructurados con formato de caja y emojis facilitan identificar problemas
2. **Control del usuario**: Usuario decide cuándo iniciar la escucha en lugar de auto-inicio
3. **Feedback visual**: Indicador de cable conectado/desconectado visible en todo momento
4. **Robustez**: Detección continua de cable permite al usuario saber cuándo reconectar
5. **Compatibilidad**: Funciona tanto con Aisino como con NewPOS
6. **Eficiencia**: Logs muestreados en lecturas para evitar spam de logcat

## Correcciones Aplicadas (2025-10-10)

### Problema 1 Identificado
El método original de detección usaba solo `init()`, que **mantiene estado** en el controller singleton. Esto causaba que:
- Una vez que `init()` tenía éxito, **siempre retornaba éxito** aunque se desconectara el cable
- No detectaba desconexiones físicas del cable USB

### Problema 2 Identificado (Crítico)
El método mejorado seguía usando `CommunicationSDKManager.getComController()` que **retorna la misma instancia singleton**:
- El singleton ya tenía estado previo de `init()` y `open()`
- Aunque se cerrara el puerto, la próxima llamada a `open()` en la misma instancia siempre retornaba éxito
- **No detectaba desconexión real del cable**

### Solución FINAL Implementada
Ahora el método `detectCableConnection()`:
1. **Crea una instancia NUEVA** del ComController específico (Aisino/Newpos) en cada detección:
   ```kotlin
   // NO usar singleton, crear instancia fresca
   tempController = when (SystemConfig.managerSelected) {
       AISINO -> AisinoComController(comport = 0)  // Nueva instancia
       NEWPOS -> NewposComController()             // Nueva instancia
   }
   ```
2. **Llama a `init()`** y luego **`open()`** para probar el hardware real
3. **Cierra inmediatamente** el puerto para no bloquearlo
4. **Respeta el estado de escucha** activa (no interfiere si está LISTENING)
5. Solo prueba la conexión cuando está DISCONNECTED o ERROR

### Resultado
✅ Ahora detecta correctamente cuando se desconecta/conecta el cable físicamente
✅ Cada detección usa una instancia fresca sin estado previo
✅ La prueba de `open()` realmente verifica el hardware físico

## Próximos Pasos (Opcionales)

- [ ] Agregar auto-inicio opcional al detectar cable (toggle en Settings)
- [ ] Agregar historial de eventos de conexión/desconexión
- [ ] Exportar logs de comunicación a archivo
- [ ] Agregar indicador de actividad de lectura/escritura en tiempo real
- [ ] Optimizar detección de cable (reducir intervalo cuando desconectado)

## Notas de Prueba

### Para probar con Aisino:
1. Conectar cable USB entre MasterPOS y SubPOS Aisino
2. Observar indicador verde en UI
3. Presionar "Iniciar Escucha"
4. Observar logs de apertura en Logcat (filtrar por "AisinoComController")
5. Enviar datos desde MasterPOS
6. Observar logs de recepción con HEX/ASCII

### Para probar con NewPOS:
1. Conectar cable USB entre MasterPOS y SubPOS NewPOS
2. Observar indicador verde en UI
3. Presionar "Iniciar Escucha"
4. Observar logs de apertura en Logcat (filtrar por "NewposComController")
5. Enviar datos desde MasterPOS
6. Observar logs de recepción con HEX/ASCII

### Para probar detección de cable:
1. Iniciar app sin cable conectado → Indicador rojo
2. Conectar cable → Esperar max 3 seg → Indicador cambia a verde + snackbar
3. Desconectar cable → Esperar max 3 seg → Indicador cambia a rojo + snackbar
4. Reconectar cable → Verificar cambio a verde nuevamente
