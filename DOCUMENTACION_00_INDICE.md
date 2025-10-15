# Documentación Completa del Sistema de Inyección de Llaves Criptográficas

## Índice General

### Versión: 1.0
### Fecha: Octubre 2025
### Autor: Sistema de Inyección de Llaves Android

---

## Descripción del Sistema

El **Sistema de Inyección de Llaves Criptográficas** es una solución empresarial para dispositivos Android POS que permite la generación, gestión e inyección segura de llaves criptográficas en módulos de seguridad PED (Pin Entry Device).

### Componentes Principales

1. **Aplicación Injector** (Dispositivo Maestro)
   - Generación de llaves mediante ceremonia
   - Gestión de perfiles de configuración
   - Envío de comandos de inyección
   - Control de autenticación

2. **Aplicación App** (Dispositivo Receptor)
   - Recepción de comandos de inyección
   - Escritura de llaves en PED
   - Gestión de llaves almacenadas

---

## Documentos de la Serie

### [Parte 1: Introducción y Arquitectura General](DOCUMENTACION_01_INTRODUCCION_Y_ARQUITECTURA.md)

**Contenido**:
- ¿Qué es el Sistema de Inyección de Llaves?
- Casos de uso principales
- Arquitectura del sistema
- Componentes principales
- Módulos compartidos
- Flujo de datos
- Patrones de arquitectura utilizados
- Seguridad y criptografía
- Estados y transiciones

**Temas Clave**:
- Arquitectura MVVM
- Módulos: communication, manufacturer, format, persistence, config, utils
- Protocolo Futurex y Legacy
- Flujo de inyección completo
- Gestión de errores

**Longitud**: ~15,000 tokens

---

### [Parte 2: Aplicaciones y Módulos del Sistema](DOCUMENTACION_02_APLICACIONES_Y_MODULOS.md)

**Contenido**:
- Aplicación Injector detallada
  - Pantallas principales
  - ViewModels clave
  - Flujo de datos
- Aplicación App detallada
  - Pantallas principales
  - ViewModels clave
  - Flujo de recepción
- Módulos compartidos en profundidad
  - communication (Comunicación serial)
  - manufacturer (Control de PED)
  - format (Protocolos)
  - persistence (Base de datos)
  - config (Configuración)
  - utils (Utilidades)

**Temas Clave**:
- CeremonyScreen y generación de llaves
- ProfilesScreen y gestión de perfiles
- KeyInjectionViewModel
- MainViewModel y listening loop
- Integración entre módulos

**Longitud**: ~15,000 tokens

---

### [Parte 3: Tipos de Llaves y Criptografía](DOCUMENTACION_03_TIPOS_LLAVES_CRIPTOGRAFIA.md)

**Contenido**:
- Tipos de llaves soportadas
  - Master Key, Working Keys (PIN/MAC/Data)
  - DUKPT (BDK/IPEK)
  - RSA
  - Transport Key (KEK)
- Algoritmos criptográficos
  - DES, 3DES, AES, SM4, RSA
- Generación de llaves
  - Ceremonia de llaves (división de secretos)
  - SecureRandom
- Cálculo del KCV
- Cifrado de llaves con KEK
- DUKPT detallado
  - Conceptos fundamentales
  - Tipos de llaves DUKPT
  - Validación de KSN
- Validaciones de integridad
- Seguridad y mejores prácticas

**Temas Clave**:
- Ceremonia de llaves con XOR
- KEK y cifrado de transmisión
- DUKPT y KSN
- Validaciones pre-inyección
- Códigos de error Futurex

**Longitud**: ~15,000 tokens

---

### [Parte 4: Perfiles y Configuración](DOCUMENTACION_04_PERFILES_CONFIGURACION.md)

**Contenido**:
- Concepto de perfiles
- Gestión de perfiles
  - Creación, edición, eliminación
- Tipos de configuración
  - Perfil básico (sin KEK)
  - Perfil con KEK
  - Perfil DUKPT
  - Perfil mixto
- Flujo de inyección desde perfil
  - Preparación
  - Proceso completo
  - Manejo de errores
- Ejemplos de perfiles comunes
  - Terminal tienda
  - E-Commerce DUKPT
  - ATM avanzado
  - Desarrollo y testing
- Mejores prácticas
- Troubleshooting

**Temas Clave**:
- KeyConfiguration
- ProfileEntity
- Uso de KEK en perfiles
- Validación de perfiles
- Logs de inyección

**Longitud**: ~14,000 tokens

---

### [Parte 5: Protocolos de Comunicación](DOCUMENTACION_05_PROTOCOLOS_COMUNICACION.md)

**Contenido**:
- Protocolo Futurex
  - Estructura general
  - Cálculo del LRC
  - Comandos (02, 03, 04, 05, 06)
  - Formato de longitud de llave
  - Códigos de respuesta
  - Parsing de mensajes
- Protocolo Legacy
  - Estructura general
  - Comandos POLL/ACK
  - Parsing y formateo
- Servicio de Polling
  - Flujo de polling
  - Detección de conexión/desconexión
  - Integración con inyección
- Comunicación Serial USB
  - Parámetros de configuración
  - Auto-scan de puertos (Aisino)
  - Re-scan automático
- Manejo de errores y timeouts
- Logs y debugging

**Temas Clave**:
- FuturexMessageFormatter/Parser
- LegacyMessageFormatter/Parser
- PollingService
- Auto-scan de Aisino
- Timeline de detección

**Longitud**: ~15,000 tokens

---

### [Parte 6: Usuarios y Persistencia](DOCUMENTACION_06_USUARIOS_PERSISTENCIA.md)

**Contenido**:
- Sistema de usuarios
  - Modelo de usuario
  - Usuario por defecto (admin/admin)
  - Autenticación
  - Roles
- Base de datos Room
  - AppDatabase
  - Migraciones
- Entidades principales
  - InjectedKeyEntity
  - ProfileEntity
  - UserEntity
- DAOs (Data Access Objects)
- Repositorios
  - InjectedKeyRepository
  - ProfileRepository
  - UserRepository
- Flujos de datos
  - Flow reactivo
  - Operaciones CRUD
- Seguridad de datos
  - Datos sensibles
  - Recomendaciones (SQLCipher, Bcrypt)
  - Auditoría
- Backup y recuperación

**Temas Clave**:
- Room Database
- Flow y StateFlow
- TypeConverters
- recordKeyInjectionWithData
- Seguridad de keyData

**Longitud**: ~14,000 tokens

---

### [Parte 7: Fabricantes y Dispositivos Soportados](DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md)

**Contenido**:
- Fabricantes soportados
  - Aisino/Vanstone
  - Newpos
  - Urovo
  - (Ingenico, PAX - definidos)
- Detección automática de fabricante
- Aisino/Vanstone
  - SDK, comunicación serial
  - AisinoComController
  - AisinoPedController
  - Auto-scan de puertos
  - Limitaciones
- Newpos
  - SDK, comunicación serial
  - NewposComController
  - NewposPedController
  - Ventajas
- Urovo
  - SDK, constantes
  - UrovoPedController
  - Soporte de DUKPT completo
  - Soporte de SM4
  - Ventajas
- Comparativa de fabricantes
- Arquitectura de abstracción (Strategy Pattern)
- Configuración por dispositivo
- Troubleshooting por fabricante
- Certificaciones y cumplimiento

**Temas Clave**:
- EnumManufacturer
- IComController / IPedController
- Auto-scan de Aisino
- SM4 en Urovo
- Strategy pattern

**Longitud**: ~14,000 tokens

---

## Resumen de Características del Sistema

### Tecnologías y Frameworks

**Frontend**:
- Jetpack Compose (UI declarativa)
- Material Design 3
- Navigation Compose

**Backend**:
- Kotlin Coroutines
- Flow / StateFlow
- Hilt (Dependency Injection)

**Persistencia**:
- Room Database (SQLite)
- TypeConverters (JSON)

**Seguridad**:
- Android KeyStore
- Triple DES, AES
- KCV (Key Check Value)
- LRC (Longitudinal Redundancy Check)

**Comunicación**:
- Serial USB
- Protocolos Futurex y Legacy
- Auto-scan de puertos

### Tipos de Llaves Soportadas

- **Algoritmos**: DES, 3DES, AES-128, AES-192, AES-256, SM4
- **Tipos**: Master Key, PIN Key, MAC Key, Data Key, DUKPT (BDK/IPEK), RSA, KEK
- **Longitudes**: 8, 16, 24, 32 bytes

### Protocolos

**Futurex** (Inyección):
- Comando 02: Inyección de llave simétrica
- Comando 03: Lectura de número de serie
- Comando 04: Escritura de número de serie
- Comando 05: Eliminación total de llaves
- Comando 06: Eliminación de llave específica

**Legacy** (Polling):
- Comando 0100: POLL (ping)
- Comando 0110: ACK (respuesta)

### Fabricantes

**Completamente Soportados**:
1. Aisino/Vanstone (SDK: vanstoneSdkClient)
2. Newpos (SDK: AppSdkAidl)
3. Urovo (SDK: urovo-sdk-v1.0.20)

**Definidos** (estructura preparada):
4. Ingenico
5. PAX

### Módulos del Proyecto

1. **app** - Aplicación receptora (SubPOS)
2. **injector** - Aplicación inyectora (MasterPOS)
3. **dev_injector** - Aplicación de desarrollo
4. **communication** - Comunicación serial USB
5. **manufacturer** - Control de PED
6. **format** - Protocolos de mensajería
7. **persistence** - Base de datos Room
8. **config** - Configuración del sistema
9. **utils** - Utilidades criptográficas

---

## Flujos Principales

### 1. Ceremonia de Llaves
```
Usuario → CeremonyScreen
  └─ Define número de custodios (2-5)
  └─ Cada custodio ingresa componente
  └─ Sistema combina con XOR
  └─ Calcula KCV
  └─ Almacena en KeyStore + BD
```

### 2. Inyección desde Perfil
```
Usuario → ProfilesScreen
  └─ Selecciona perfil
  └─ Presiona "Inyectar"
  └─ Sistema valida llaves
  └─ Exporta KEK (si necesario)
  └─ Inyecta llaves secuencialmente
  └─ Valida cada KCV
  └─ Completa con éxito
```

### 3. Detección de Conexión
```
Master → Envía POLL cada 2s
SubPOS → Responde ACK
Master → Detecta conexión
  └─ Estado: CONNECTED
  └─ Callback: onConnectionStatusChanged(true)
```

---

## Seguridad

### Capas de Seguridad

1. **Generación**:
   - Ceremonia con división de secretos
   - SecureRandom para KEK

2. **Almacenamiento**:
   - Android KeyStore (hardware)
   - Base de datos (opcional cifrado SQLCipher)

3. **Transmisión**:
   - Cifrado con KEK (3DES)
   - Validación con KCV
   - Verificación con LRC

4. **Validación**:
   - Pre-inyección (integridad)
   - Post-inyección (KCV del PED)
   - Auditoría completa

---

## Compatibilidad

### Dispositivos Probados

**Aisino**:
- Aisino A90 Pro ✅

**Newpos**:
- Newpos NEW9220 ✅
- Newpos NEW9830 ✅

**Urovo**:
- Urovo i9000S ✅
- Urovo i9100 ✅

### Android

- **Versión mínima**: Android 8.0 (API 26)
- **Versión recomendada**: Android 10+ (API 29+)
- **Arquitectura**: ARM, ARM64

---

## Estadísticas del Proyecto

**Líneas de Código**: ~50,000+ (estimado)

**Archivos Kotlin**: ~250

**Módulos**: 9

**Documentación**: 7 documentos (~100,000 palabras)

**Protocolos**: 2 (Futurex, Legacy)

**Fabricantes**: 3 completos, 2 preparados

---

## Uso de la Documentación

### Para Desarrolladores Nuevos

**Orden recomendado**:
1. Parte 1 - Introducción y Arquitectura
2. Parte 2 - Aplicaciones y Módulos
3. Parte 5 - Protocolos de Comunicación
4. Parte 3 - Tipos de Llaves
5. Parte 4 - Perfiles
6. Parte 6 - Usuarios y Persistencia
7. Parte 7 - Fabricantes

### Para Administradores

**Lectura esencial**:
- Parte 1 - Introducción (conceptos generales)
- Parte 3 - Tipos de Llaves (seguridad)
- Parte 4 - Perfiles (configuración)

### Para Operadores

**Lectura esencial**:
- Parte 1 - Casos de uso
- Parte 4 - Perfiles (uso diario)
- Parte 4 - Troubleshooting

### Para Integradores

**Lectura esencial**:
- Parte 2 - Módulos
- Parte 5 - Protocolos
- Parte 7 - Fabricantes

---

## Contacto y Soporte

**Proyecto**: Android Injector  
**Organización**: Vigatec  
**Versión de Documentación**: 1.0  
**Fecha de Última Actualización**: Octubre 2025  

---

## Licencia

Esta documentación describe un sistema propietario. Todos los derechos reservados.

---

**Inicio de Lectura Recomendado**: [Parte 1: Introducción y Arquitectura General](DOCUMENTACION_01_INTRODUCCION_Y_ARQUITECTURA.md)


