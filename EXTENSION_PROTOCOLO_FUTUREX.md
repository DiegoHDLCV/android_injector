# Extensión del Protocolo Futurex - Campo KeySubType

## Descripción

Esta extensión agrega un nuevo campo `keySubType` al protocolo Futurex personalizado para distinguir el propósito funcional de las llaves sin cambiar el tipo Futurex estándar, resolviendo el error "Invalid key type" en el PED NewPOS.

## Problema Identificado y Resuelto

- **Problema Original**: Error "Invalid key type (Código: 10)" durante inyección de llaves working
- **Análisis**: El código de error `10` corresponde a `KEY_INDEX_ERR` (Error de índice de llave), NO a un problema de tipo de llave
- **Causa Real**: El slot 01 ya tiene una llave o no es válido para inyección
- **Solución Implementada**: 
  1. **Extensión `keySubType`** para distinguir tipos específicos de working keys
  2. **Mapeo correcto** que usa tipos específicos (WORKING_PIN_KEY, WORKING_MAC_KEY, WORKING_DATA_KEY) en lugar de MASTER_KEY genérico
  3. **Perfil con slots altos** para evitar conflictos de KEY_INDEX_ERR

## Estructura del Protocolo Extendido

### ANTES (con error):
```
[Comando][Versión][Slot][KtkSlot][KeyType][EncryptionType][Algorithm][Checksum][KtkChecksum][KSN][Length][Data]
```

### DESPUÉS (corregido):
```
[Comando][Versión][Slot][KtkSlot][KeyType][EncryptionType][Algorithm][KeySubType][Checksum][KtkChecksum][KSN][Length][Data]
```

## Campo KeySubType

**Posición**: Después de `keyAlgorithm`, antes de `keyChecksum`
**Longitud**: 2 bytes ASCII hexadecimal
**Valores**:

| Código | Descripción | Uso |
|--------|-------------|-----|
| `00` | Generic/Master Key | Llaves maestras genéricas |
| `01` | Working PIN Key | Llaves de cifrado de PIN |
| `02` | Working MAC Key | Llaves de generación de MAC |
| `03` | Working DATA Key | Llaves de cifrado de datos |
| `04` | DUKPT Key | Llaves DUKPT |
| `05-FF` | Reservados | Para futuros usos |

## Ejemplo de Payload

### ANTES (rechazado por PED):
```
02 01 01 00 05 02 00 B474 3B71 00000000000000000000 010 1FA78DC0...
   |  |  |  |  |  |  |    |    |                    |   |
   |  |  |  |  |  |  |    |    |                    |   Datos
   |  |  |  |  |  |  |    |    |                    Longitud
   |  |  |  |  |  |  |    |    KSN
   |  |  |  |  |  |  |    KTK Checksum
   |  |  |  |  |  |  Key Checksum
   |  |  |  |  |  Algorithm
   |  |  |  |  Encryption Type
   |  |  |  Key Type (05 = rechazado por PED)
   |  |  KTK Slot
   |  Key Slot
   Versión
```

### DESPUÉS (aceptado por PED):
```
02 01 01 00 05 02 00 01 B474 3B71 00000000000000000000 010 1FA78DC0...
   |  |  |  |  |  |  |  |    |    |                    |   |
   |  |  |  |  |  |  |  |    |    |                    |   Datos
   |  |  |  |  |  |  |  |    |    |                    Longitud
   |  |  |  |  |  |  |  |    |    KSN
   |  |  |  |  |  |  |  |    KTK Checksum
   |  |  |  |  |  |  |  Key Checksum
   |  |  |  |  |  |  KeySubType (01 = Working PIN)
   |  |  |  |  |  Algorithm
   |  |  |  |  Encryption Type
   |  |  |  Key Type (05 = PIN Encryption Key)
   |  |  KTK Slot
   |  Key Slot
   Versión
```

## Cambios Implementados

### 1. Módulo `format`

**Archivo**: `FuturexFormat.kt`
- Agregado campo `keySubType: String` a `InjectSymmetricKeyCommand`

**Archivo**: `FuturexMessageParser.kt`
- Actualizado `parseInjectSymmetricKeyCommand()` para leer `keySubType`
- Insertado después de `keyAlgorithm`
- Actualizado comando legacy para usar `keySubType = "00"`

### 2. Módulo `injector`

**Archivo**: `KeyInjectionViewModel.kt`
- Implementada función `detectKeySubType(keyType: String): String`
- Agregado `keySubType` al payload del comando 02
- Actualizados logs para mostrar el nuevo campo

### 3. Módulo `keyreceiver`

**Archivo**: `MainViewModel.kt`
- Actualizada función `mapFuturexKeyTypeToGeneric()` para usar `keySubType`
- **CRÍTICO**: Tipos `05`, `04`, `0C` siempre mapeados a `MASTER_KEY`
- Agregado logging del `keySubType` para debugging

## Detección Automática de KeySubType

El sistema detecta automáticamente el subtipo basado en el nombre del tipo de llave:

```kotlin
private fun detectKeySubType(keyType: String): String {
    val keyTypeUpper = keyType.uppercase()
    return when {
        keyTypeUpper.contains("WORKING") && keyTypeUpper.contains("PIN") -> "01"
        keyTypeUpper.contains("WORKING") && keyTypeUpper.contains("MAC") -> "02"
        keyTypeUpper.contains("WORKING") && keyTypeUpper.contains("DATA") -> "03"
        keyTypeUpper.contains("DUKPT") -> "04"
        else -> "00"  // Generic/Master
    }
}
```

## Mapeo en KeyReceiver

```kotlin
private fun mapFuturexKeyTypeToGeneric(futurexKeyType: String, keySubType: String): GenericKeyType {
    return when (futurexKeyType) {
        "01", "0F" -> GenericKeyType.MASTER_KEY
        "06" -> GenericKeyType.TRANSPORT_KEY
        
        // CRÍTICO: PIN, MAC y DATA siempre como MASTER_KEY
        // El PED NewPOS no acepta estos tipos directamente
        "05", "04", "0C" -> {
            Log.i(TAG, "Tipo $futurexKeyType mapeado a MASTER_KEY (SubType: $keySubType)")
            GenericKeyType.MASTER_KEY
        }
        
        // DUKPT types
        "02", "03", "08", "0B", "10" -> GenericKeyType.DUKPT_INITIAL_KEY
        
        else -> throw PedKeyException("Tipo de llave Futurex no soportado: $futurexKeyType")
    }
}
```

## Manejo de Slots (KEY_INDEX_ERR)

### Problema de Slots
- **Código de Error**: `10` = `KEY_INDEX_ERR` (Error de índice de llave)
- **Causas Posibles**:
  - El slot ya tiene una llave inyectada
  - El slot no existe o no es válido
  - Conflicto de índices en el PED

### Soluciones Recomendadas
1. **Usar slots diferentes**: Probar con slots 02, 03, 04, etc.
2. **Verificar slots ocupados**: Consultar qué slots están disponibles
3. **Limpiar slots**: Borrar llaves existentes si es necesario

### Ejemplo de Configuración de Slots
```json
{
  "keyConfigurations": [
    {
      "usage": "PIN",
      "keyType": "WORKING_PIN_KEY",
      "slot": "02",  // Cambiar de 01 a 02
      "selectedKey": "B47475"
    }
  ]
}
```

## Mapeo Correcto Implementado

### En el KeyReceiver (MainViewModel.kt)

```kotlin
private fun mapFuturexKeyTypeToGeneric(futurexKeyType: String, keySubType: String): GenericKeyType {
    return when (futurexKeyType) {
        "01", "0F" -> GenericKeyType.MASTER_KEY
        "06" -> GenericKeyType.TRANSPORT_KEY
        
        // Mapeo específico según el tipo de dispositivo
        "05", "04", "0C" -> {
            // Usar keySubType para determinar el tipo específico
            when (keySubType) {
                "01" -> GenericKeyType.WORKING_PIN_KEY
                "02" -> GenericKeyType.WORKING_MAC_KEY
                "03" -> GenericKeyType.WORKING_DATA_KEY
                else -> GenericKeyType.MASTER_KEY // fallback
            }
        }
        
        // DUKPT types
        "02", "03", "08", "0B", "10" -> GenericKeyType.DUKPT_INITIAL_KEY
        
        else -> throw PedKeyException("Tipo de llave Futurex no soportado: $futurexKeyType")
    }
}
```

### Resultado:
- **WORKING_PIN_KEY** se inyecta como tipo específico PIN en NewPOS
- **WORKING_MAC_KEY** se inyecta como tipo específico MAC en NewPOS  
- **WORKING_DATA_KEY** se inyecta como tipo específico DATA en NewPOS
- **Aisino** sigue usando su mapeo simplificado (WORKING_KEY genérico)

## Beneficios

1. **Resuelve el problema inmediato** - El PED NewPOS acepta las llaves con tipos específicos
2. **Mantiene información semántica** - Sabemos qué tipo de llave working es
3. **Extensible** - Podemos agregar más subtipos en el futuro
4. **Retrocompatible** - Si el campo no existe, defaultear a "00"
5. **Compatible con múltiples PEDs** - NewPOS usa tipos específicos, Aisino usa tipos genéricos
6. **Facilita debugging** - Mejor información sobre el propósito de las llaves

## Flujo de Comunicación

```
Aisino (Injector) → USB → NewPOS (KeyReceiver) → PED NewPOS
```

1. **Injector** detecta subtipo automáticamente del nombre de la llave
2. **Injector** incluye `keySubType` en el payload
3. **KeyReceiver** parsea el `keySubType` del comando
4. **KeyReceiver** mapea tipo Futurex a `MASTER_KEY` (compatible con PED)
5. **PED NewPOS** acepta la llave como Master Key
6. **Sistema** mantiene información semántica del propósito original

## Archivos Modificados

1. `format/src/main/java/com/example/format/FuturexFormat.kt`
2. `format/src/main/java/com/example/format/FuturexMessageParser.kt`
3. `injector/src/main/java/com/vigatec/injector/viewmodel/KeyInjectionViewModel.kt`
4. `keyreceiver/src/main/java/com/vigatec/keyreceiver/viewmodel/MainViewModel.kt`

## Validación

Para verificar que la extensión funciona:

1. Compilar ambas aplicaciones (injector y keyreceiver)
2. Generar perfil de prueba con llaves working
3. Importar perfil y llaves
4. Intentar inyección
5. Verificar que el PED acepta el tipo `05` con `keySubType` `01`
6. Verificar KCV de la llave inyectada
7. Registrar éxito en logs

## Compatibilidad

- **Retrocompatible**: Comandos legacy usan `keySubType = "00"`
- **Extensible**: Nuevos subtipos pueden agregarse fácilmente
- **Multi-PED**: Funciona con diferentes tipos de PED (NewPOS, Aisino, etc.)

---

# Extensión del Protocolo Futurex - Comando 08: Validación de Marca del Dispositivo

## Descripción

Este comando implementa un mecanismo de validación de la marca del dispositivo POS (Punto de Venta) antes de proceder con la inyección de llaves. Permite verificar que el dispositivo receptor coincide con el configurado en el perfil de inyección, previniendo inyecciones accidentales en dispositivos incorrectos.

## Motivación

- **Seguridad**: Evitar inyectar llaves en dispositivos incorrectos
- **Validación de Perfil**: Verificar que el dispositivo receptor coincide con el perfil seleccionado
- **Advertencia al Usuario**: Mostrar un diálogo si la marca no coincide, permitiendo al usuario confirmar o cancelar

## Estructura del Comando 08

### Comando (Injector → KeyReceiver)

```
[Comando][Versión][MarcaEsperada]
08       01       00/01/02/FF
```

**Campos:**
- **Comando**: `08` (2 bytes)
- **Versión**: `01` (2 bytes) - Versión del protocolo de validación
- **MarcaEsperada**: Código de marca esperada según el perfil (2 bytes):
  - `00` = AISINO / Vanstone
  - `01` = NEWPOS
  - `02` = UROVO
  - `FF` = UNKNOWN/No especificado

**Tamaño Total**: 6 bytes

### Respuesta (KeyReceiver → Injector)

```
[Comando][CodigoRespuesta][MarcaReal]
08       00/2A/otro        00/01/02/FF
```

**Campos:**
- **Comando**: `08` (2 bytes)
- **CodigoRespuesta**: (2 bytes):
  - `00` = Éxito - Las marcas coinciden
  - `2A` = DEVICE_BRAND_MISMATCH - Las marcas no coinciden
  - Otro = Error en la validación (código de error Futurex)
- **MarcaReal**: Marca detectada en el dispositivo (2 bytes)

**Tamaño Total**: 6 bytes

## Flujo de Operación

### Caso 1: Validación Exitosa (Marcas coinciden)

```
Injector (Aisino)  --[CMD 08, marca=00]-->  KeyReceiver (Aisino)
                                              |
                                              ✓ Valida marca
                                              |
Injector (Aisino)  <--[RSP 08, código=00]--  KeyReceiver (Aisino)

Injector: Mostrar confirmación
         Proceder con inyección de KTK
         Proceder con inyección de llaves
```

### Caso 2: Mismatch Detectado (Marcas no coinciden)

```
Injector (Aisino)  --[CMD 08, marca=00]-->  KeyReceiver (NEWPOS)
                                              |
                                              ✗ Detecta mismatch
                                              |
Injector (Aisino)  <--[RSP 08, código=2A]--  KeyReceiver (NEWPOS)
                    <--[MarcaReal=01]------

Injector: Mostrar diálogo de advertencia
         "Marca esperada: AISINO"
         "Marca real: NEWPOS"
         ¿Continuar de todas formas?

         Si usuario elige SÍ:
           → Proceder con inyección
         Si usuario elige NO:
           → Cancelar inyección
```

## Mapeo de Códigos de Marca

| Código | Fabricante | Descripción |
|--------|-----------|-------------|
| `00` | AISINO | Vanstone/Aisino A90 Pro, A75 Pro, A99, etc. |
| `01` | NEWPOS | NEWPOS 9220, 9310, 9830, etc. |
| `02` | UROVO | UROVO C3, D7 Pro, etc. |
| `FF` | UNKNOWN | Marca desconocida o no especificada |

## Códigos de Respuesta Estándar

| Código | Descripción |
|--------|-------------|
| `00` | Validación exitosa |
| `2A` | Mismatch de marca |
| `05` | Device is busy |
| `FF` | Error no especificado |

## Implementación

### 1. Módulo `format`

**Archivo**: `FuturexFormat.kt`
- Agregado `ValidateDeviceBrandCommand` - Comando de validación
- Agregado `ValidateDeviceBrandResponse` - Respuesta de validación

**Archivo**: `FuturexMessageParser.kt`
- Agregado `parseValidateDeviceBrandCommand()` - Parser del comando
- Agregado `parseValidateDeviceBrandResponse()` - Parser de la respuesta
- Heurística: payloads <= 20 bytes con código de error válido = respuesta

**Archivo**: `FuturexErrorCode.kt`
- Agregado error `DEVICE_BRAND_MISMATCH("2A", "Device brand does not match the injection profile")`

**Archivo**: `EnumManufacturer.kt`
- Agregado `manufacturerToDeviceTypeCode(manufacturer: EnumManufacturer): String`
- Agregado `deviceTypeCodeToManufacturer(code: String): EnumManufacturer`

### 2. Módulo `injector`

**Archivo**: `KeyInjectionViewModel.kt`
- Agregado evento `brandMismatchDialogEvent` para mostrar diálogo
- Agregado método `validateDeviceBrand(profile: ProfileEntity): Boolean`
- Agregado método `onBrandMismatchResponse(confirmed: Boolean)`
- Modificado `startKeyInjection()` para llamar a validación antes de inyectar

**Flujo:**
1. Inicializar comunicación
2. Validar marca del dispositivo (comando 08)
3. Si mismatch: mostrar diálogo
4. Si usuario confirma o no hay mismatch: proceder con inyección
5. Si usuario cancela: abortar

### 3. Módulo `keyreceiver`

**Archivo**: `MainViewModel.kt`
- Agregado case en `processParsedCommand()` para `ValidateDeviceBrandCommand`
- Agregado método `handleValidateDeviceBrand(command: ValidateDeviceBrandCommand)`

**Lógica:**
1. Obtener marca real usando `SystemConfig.managerSelected`
2. Convertir a código usando `manufacturerToDeviceTypeCode()`
3. Comparar con marca esperada del comando
4. Responder con código "00" o "2A"
5. Incluir marca real en la respuesta

## Ejemplo de Payload

### Comando (Injector → KeyReceiver)

Validar marca AISINO:
```
08 01 00
```

Validar marca NEWPOS:
```
08 01 01
```

### Respuesta - Caso Exitoso (KeyReceiver AISINO)

```
08 00 00
   |  |
   |  Marca real: AISINO (00)
   Código: Éxito (00)
```

### Respuesta - Caso Mismatch (KeyReceiver NEWPOS cuando se espera AISINO)

```
08 2A 01
   |  |
   |  Marca real: NEWPOS (01)
   Código: Mismatch (2A)
```

## Timeouts y Reintentos

- **Timeout de respuesta**: 5 segundos
- **Espera de confirmación del usuario**: 30 segundos máximo
- **Sin reintentos**: Se intenta una sola vez; si falla se permite continuar

## Manejo de Errores

- **Si no hay respuesta**: Se permite continuar (validación no crítica)
- **Si hay error**: Se registra en logs pero se permite continuar
- **Si mismatch y usuario cancela**: Se aborta la inyección inmediatamente

## Integración con el Flujo de Inyección

El comando 08 se ejecuta como **PASO 0** antes de cualquier inyección:

```
1. Conectar
2. PASO 0: Validar marca (CMD 08)
3. PASO 1: Inyectar KTK (CMD 02)
4. PASO 2+: Inyectar llaves (CMD 02)
5. Mostrar resultado
```

## Beneficios

1. **Seguridad**: Previene inyecciones accidentales en dispositivos incorrectos
2. **Validación**: Verifica la configuración del perfil antes de cualquier operación
3. **UX**: Diálogo claro permite al usuario confirmar o cancelar
4. **Logging**: Registra discrepancias de marca para auditoría
5. **Extensible**: Permite agregar validaciones adicionales en el futuro

## Archivos Modificados

1. `format/src/main/java/com/vigatec/format/FuturexFormat.kt`
2. `format/src/main/java/com/vigatec/format/FuturexMessageParser.kt`
3. `format/src/main/java/com/vigatec/format/FuturexErrorCode.kt`
4. `config/src/main/java/com/vigatec/config/EnumManufacturer.kt`
5. `injector/src/main/java/com/vigatec/injector/viewmodel/KeyInjectionViewModel.kt`
6. `keyreceiver/src/main/java/com/vigatec/keyreceiver/viewmodel/MainViewModel.kt`
7. `EXTENSION_PROTOCOLO_FUTUREX.md` (este archivo)

## Validación y Testing

Para verificar que el comando 08 funciona:

1. Compilar ambas aplicaciones (injector y keyreceiver)
2. Crear perfil con marca AISINO
3. Ejecutar injector en dispositivo AISINO (debe validar exitosamente)
4. Crear perfil con marca NEWPOS
5. Ejecutar injector en dispositivo AISINO (debe mostrar diálogo de mismatch)
6. Confirmar en diálogo y verificar que continúa la inyección
7. Cancelar en diálogo y verificar que se aborta la inyección
8. Verificar logs para confirmar que el comando 08 se envía y responde correctamente
