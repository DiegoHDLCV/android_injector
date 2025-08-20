# Logs de Inyección Futurex - Documentación

## Resumen
Se han agregado logs detallados en toda la cadena de inyección de llaves usando el protocolo Futurex. Estos logs permiten rastrear y debuggear todo el proceso de inyección, desde la selección del perfil hasta la respuesta del dispositivo.

## Ubicación de los Logs

### 1. KeyInjectionViewModel.kt
**Tag:** `KeyInjectionViewModel`

#### Inicialización
- `=== INICIALIZANDO KEYINJECTIONVIEWMODEL FUTUREX ===`
- `=== CONFIGURANDO MANEJADORES DE PROTOCOLO FUTUREX ===`
- `=== INICIALIZANDO SERVICIO DE POLLING FUTUREX ===`

#### Gestión del Modal
- `=== MOSTRANDO MODAL DE INYECCIÓN FUTUREX ===`
- `=== OCULTANDO MODAL DE INYECCIÓN FUTUREX ===`

#### Proceso de Inyección
- `=== INICIANDO PROCESO DE INYECCIÓN FUTUREX ===`
- `=== PROCESANDO LLAVE X/Y ===`
- `=== INYECCIÓN FUTUREX COMPLETADA EXITOSAMENTE ===`

#### Comunicación Serial
- `=== INICIALIZANDO COMUNICACIÓN SERIAL FUTUREX ===`
- `=== ENVIANDO DATOS FUTUREX ===`
- `=== ESPERANDO RESPUESTA FUTUREX ===`
- `=== PROCESANDO RESPUESTA FUTUREX ===`
- `=== CERRANDO COMUNICACIÓN SERIAL FUTUREX ===`

#### Estructura de Comando
- `=== ESTRUCTURA FUTUREX PARA INYECCIÓN DE LLAVE ===`
- `=== MAPEO DE TIPO DE LLAVE FUTUREX ===`
- `=== PAYLOAD FINAL FUTUREX ===`

#### Inyección Individual
- `=== INICIANDO INYECCIÓN DE LLAVE FUTUREX ===`
- `=== INYECCIÓN DE LLAVE FUTUREX COMPLETADA ===`

#### Reinicio de Polling
- `=== REINICIANDO POLLING FUTUREX ===`

### 2. KeyInjectionScreen.kt
**Tag:** `KeyInjectionModal`

#### Eventos de UI
- `=== EVENTO SNACKBAR FUTUREX ===`
- `=== INICIANDO INYECCIÓN FUTUREX DESDE UI ===`
- `=== REINTENTANDO INYECCIÓN FUTUREX DESDE UI ===`
- `=== CERRANDO MODAL FUTUREX DESDE UI ===`
- `=== CERRANDO MODAL FUTUREX DESDE UI (ÉXITO) ===`
- `=== CERRANDO MODAL FUTUREX DESDE UI (ERROR) ===`

### 3. ProfilesScreen.kt
**Tags:** `ProfilesScreen`, `ProfileCard`

#### Apertura del Modal
- `=== ABRIENDO MODAL DE INYECCIÓN FUTUREX ===`

#### Botón de Inyección
- `=== PRESIONANDO BOTÓN DE INYECCIÓN FUTUREX ===`

## Estructura de los Logs

### Formato General
```
=== TÍTULO DEL LOG ===
Información detallada
================================================
```

### Información Registrada

#### Configuración del Perfil
- Nombre del perfil
- Número de configuraciones de llave
- Detalles de cada configuración (uso, slot, tipo, llave seleccionada)

#### Estructura Futurex
- Comando (02 para inyección simétrica)
- Versión del comando
- Slot de llave y KTK
- Tipo de llave mapeado
- Tipo de encriptación
- Checksums de llave y KTK
- KSN (20 caracteres)
- Longitud de la llave
- Datos de la llave en hexadecimal

#### Comunicación Serial
- Configuración del puerto (baud rate, paridad, bits de datos)
- Datos enviados y recibidos en hexadecimal y ASCII
- Tamaños de los mensajes
- Códigos de respuesta del dispositivo

#### Estados del Proceso
- IDLE, CONNECTING, INJECTING, SUCCESS, ERROR, COMPLETED
- Progreso de la inyección
- Pasos actuales y totales

## Tipos de Log

### Información (Log.i)
- Proceso normal de inyección
- Configuraciones del protocolo
- Estados del sistema

### Debug (Log.d)
- Mapeo de tipos de llave
- Conversiones de datos

### Warning (Log.w)
- Respuestas inesperadas
- Estados de perfil vacíos

### Error (Log.e)
- Errores de comunicación
- Errores de inyección
- Fallos en la configuración

### Verbose (Log.v)
- Conversiones a hexadecimal
- Detalles de bajo nivel

## Ejemplo de Flujo de Logs

```
=== INICIALIZANDO KEYINJECTIONVIEWMODEL FUTUREX ===
=== CONFIGURANDO MANEJADORES DE PROTOCOLO FUTUREX ===
=== INICIALIZANDO SERVICIO DE POLLING FUTUREX ===
=== MOSTRANDO MODAL DE INYECCIÓN FUTUREX ===
=== INICIANDO PROCESO DE INYECCIÓN FUTUREX ===
=== INICIALIZANDO COMUNICACIÓN SERIAL FUTUREX ===
=== PROCESANDO LLAVE 1/3 ===
=== INICIANDO INYECCIÓN DE LLAVE FUTUREX ===
=== ESTRUCTURA FUTUREX PARA INYECCIÓN DE LLAVE ===
=== ENVIANDO DATOS FUTUREX ===
=== ESPERANDO RESPUESTA FUTUREX ===
=== PROCESANDO RESPUESTA FUTUREX ===
=== INYECCIÓN DE LLAVE FUTUREX COMPLETADA ===
=== INYECCIÓN FUTUREX COMPLETADA EXITOSAMENTE ===
=== CERRANDO COMUNICACIÓN SERIAL FUTUREX ===
=== REINICIANDO POLLING FUTUREX ===
```

## Beneficios

1. **Debugging Completo:** Rastreo de todo el proceso de inyección
2. **Análisis de Protocolo:** Verificación de la estructura Futurex
3. **Monitoreo de Comunicación:** Seguimiento de datos enviados/recibidos
4. **Diagnóstico de Errores:** Identificación rápida de fallos
5. **Auditoría:** Registro completo de todas las operaciones

## Configuración

Los logs están configurados para mostrar información detallada en tiempo real. Para reducir el nivel de detalle, se pueden cambiar los `Log.i` por `Log.d` en las funciones correspondientes.
