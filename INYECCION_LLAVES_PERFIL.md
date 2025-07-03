# Inyección de Llaves desde Perfil

## Descripción

Esta funcionalidad permite inyectar llaves criptográficas en dispositivos terminales usando el protocolo Futurex desde la interfaz de perfiles. La implementación utiliza el módulo `format` para manejar la comunicación serial según el manual técnico de Futurex.

## Características

- **Protocolo Futurex**: Implementa el protocolo de inyección directa de llaves según el manual técnico
- **Comunicación Serial**: Utiliza la librería de comunicación serial existente
- **Interfaz de Usuario**: Modal con progreso en tiempo real y logs detallados
- **Gestión de Errores**: Manejo robusto de errores con códigos específicos de Futurex
- **Integración con Perfiles**: Permite inyectar múltiples llaves configuradas en un perfil

## Arquitectura

### Componentes Principales

1. **KeyInjectionViewModel**: Maneja la lógica de inyección y comunicación
2. **KeyInjectionModal**: Interfaz de usuario para mostrar el progreso
3. **Módulo Format**: Parser y formateador de mensajes Futurex
4. **CommunicationSDKManager**: Gestión de comunicación serial

### Flujo de Inyección

1. **Selección de Perfil**: El usuario selecciona un perfil desde la pantalla de perfiles
2. **Inicialización**: Se establece la conexión serial con el dispositivo
3. **Procesamiento**: Se procesa cada configuración de llave del perfil
4. **Inyección**: Se envía cada llave usando el protocolo Futurex
5. **Verificación**: Se valida la respuesta del dispositivo
6. **Finalización**: Se muestra el resultado y se cierra la conexión

## Protocolo Futurex Implementado

### Comando de Inyección (02)

El comando "02" se utiliza para inyectar llaves simétricas:

```
<STX>02[VERSION][KEY_SLOT][KTK_SLOT][KEY_TYPE][ENCRYPTION_TYPE][KEY_CHECKSUM][KTK_CHECKSUM][KSN][KEY_LENGTH][KEY_DATA]<ETX><LRC>
```

### Campos del Comando

- **VERSION**: "01" (versión del comando)
- **KEY_SLOT**: Slot donde se inyectará la llave (2 dígitos hex)
- **KTK_SLOT**: Slot de la llave de transferencia (2 dígitos hex)
- **KEY_TYPE**: Tipo de llave según tabla Futurex
- **ENCRYPTION_TYPE**: "00" (claro), "01" (cifrado bajo KTK), "02" (KTK en claro)
- **KEY_CHECKSUM**: Checksum de la llave (4 dígitos hex)
- **KTK_CHECKSUM**: Checksum de la KTK (4 dígitos hex)
- **KSN**: Key Serial Number (20 dígitos hex)
- **KEY_LENGTH**: Longitud de la llave en bytes (3 dígitos hex)
- **KEY_DATA**: Datos de la llave en formato hexadecimal

### Tipos de Llave Soportados

| Tipo | Código | Descripción |
|------|--------|-------------|
| PIN | "05" | PIN Encryption Key |
| MAC | "04" | MAC Key |
| TDES/3DES | "01" | Master Session Key |
| DUKPT | "08" | DUKPT 3DES BDK Key |
| DATA | "0C" | Data Encryption Key |

## Uso

### Desde la Interfaz

1. Navegar a la pantalla de **Perfiles**
2. Seleccionar un perfil existente
3. Hacer clic en el botón **▶️** (Inyectar Llaves)
4. En el modal que aparece, hacer clic en **"Iniciar Inyección"**
5. Seguir el progreso en tiempo real
6. Revisar el log de inyección para detalles

### Configuración de Perfil

Cada perfil debe tener configuraciones de llaves con:

- **Uso**: Descripción del uso (PIN, MAC, etc.)
- **Tipo**: Tipo de llave (TDES, AES, etc.)
- **Slot**: Posición donde se inyectará
- **Llave Seleccionada**: KCV de la llave a inyectar

## Configuración Técnica

### Parámetros de Comunicación

- **Baud Rate**: 115200
- **Data Bits**: 8
- **Parity**: None
- **Stop Bits**: 1

### Configuración del Sistema

```kotlin
// En SystemConfig.kt
var commProtocolSelected: CommProtocol = CommProtocol.FUTUREX
```

## Manejo de Errores

### Códigos de Error Futurex

La implementación maneja todos los códigos de error definidos en el manual Futurex:

- **0x00**: Successful
- **0x01**: Invalid command
- **0x02**: Invalid command version
- **0x03**: Invalid length
- **0x04**: Unsupported characters
- **0x05**: Device is busy
- **0x06**: Not in injection mode
- **0x07**: Device is in tamper
- **0x08**: Bad LRC
- **0x09**: Duplicate key
- **0x0A**: Duplicate KSN
- **0x0B**: Key deletion failed
- **0x0C**: Invalid key slot
- **0x0D**: Invalid KTK slot
- **0x0E**: Missing KTK
- **0x0F**: Key slot not empty
- **0x10**: Invalid key type
- **0x11**: Invalid key encryption type
- **0x12**: Invalid key checksum
- **0x13**: Invalid KTK checksum
- **0x14**: Invalid KSN
- **0x15**: Invalid key length
- **0x16**: Invalid KTK length
- **0x17**: Invalid TR-31 version
- **0x18**: Invalid key usage
- **0x19**: Invalid algorithm
- **0x1A**: Invalid mode of use
- **0x1B**: MAC verification failed
- **0x1C**: Decryption failed

### Logs y Debugging

La funcionalidad incluye logs detallados para debugging:

- **RAW_SERIAL_OUT**: Datos enviados al dispositivo
- **RAW_SERIAL_IN**: Datos recibidos del dispositivo
- **ParsedMessage**: Mensajes parseados del protocolo
- **Error Details**: Detalles específicos de errores

## Dependencias

### Módulos Requeridos

- `communication`: Para comunicación serial
- `format`: Para parsing y formateo de mensajes Futurex
- `persistence`: Para acceso a datos de perfiles y llaves
- `config`: Para configuración del sistema

### Librerías Externas

- **Urovo SDK**: Para dispositivos Urovo
- **Aisino SDK**: Para dispositivos Aisino

## Consideraciones de Seguridad

1. **Validación de Datos**: Todos los datos de entrada se validan antes del envío
2. **Checksums**: Se verifican los checksums de las llaves
3. **Timeouts**: Se implementan timeouts para evitar bloqueos
4. **Logs Seguros**: Los logs no incluyen datos sensibles de las llaves

## Próximas Mejoras

1. **Soporte para TR-31**: Implementar bloques TR-31 para mayor seguridad
2. **Cifrado KTK**: Soporte para inyección cifrada bajo KTK
3. **Validación Avanzada**: Validación de tipos de llave según el dispositivo
4. **Batch Processing**: Procesamiento en lote de múltiples perfiles
5. **Rollback**: Capacidad de revertir inyecciones fallidas

## Troubleshooting

### Problemas Comunes

1. **Error de Conexión**: Verificar que el dispositivo esté conectado y en modo inyección
2. **Timeout**: Aumentar el timeout de comunicación si es necesario
3. **LRC Error**: Verificar la integridad de la comunicación serial
4. **Key Type Error**: Verificar que el tipo de llave sea compatible con el dispositivo

### Logs de Debug

Para habilitar logs detallados, usar:

```kotlin
Log.d("KeyInjectionViewModel", "Debug logs enabled")
```

## Referencias

- [Manual Técnico Futurex v3.8.3](documentación_futurex.pdf)
- [Protocolo de Comunicación Serial](communication/README.md)
- [Módulo de Formateo](format/README.md) 