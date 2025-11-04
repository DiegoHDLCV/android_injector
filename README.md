# Android Injector - Sistema de Inyección de Llaves Criptográficas

[![Android CI](https://github.com/DiegoHDLCV/android_injector/actions/workflows/android_ci.yml/badge.svg)](https://github.com/DiegoHDLCV/android_injector/actions)
[![License](https://img.shields.io/badge/license-Proprietary-red.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-blue.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/language-Kotlin-purple.svg)](https://kotlinlang.org)

> Sistema empresarial para gestión, generación e inyección segura de llaves criptográficas en dispositivos POS Android.

## 📋 Descripción

Sistema de inyección de llaves criptográficas para dispositivos Android POS que permite:

- **Generación segura** de llaves mediante ceremonia de división de secretos
- **Inyección remota** de llaves en módulos de seguridad PED (Pin Entry Device)
- **Gestión de perfiles** de configuración para diferentes aplicaciones
- **Transferencia segura** entre dispositivos mediante comunicación USB serial
- **Sin conectividad a internet** - Operación completamente aislada

## 🚀 Características Principales

### 🔐 Seguridad

- **Ceremonia de llaves**: Generación mediante división de secretos (2-5 custodios)
- **Android KeyStore**: Almacenamiento seguro a nivel hardware
- **Cifrado KEK**: Protección durante transmisión con Triple DES
- **Validación KCV**: Verificación de integridad sin exponer llaves
- **Sin internet**: Zero conectividad de red para máxima seguridad

### 🔑 Tipos de Llaves Soportadas

| Tipo | Algoritmos | Longitudes |
|------|-----------|------------|
| Master Key, Working Keys | DES, 3DES, AES, SM4 | 8, 16, 24, 32 bytes |
| DUKPT (BDK/IPEK) | 3DES | 16, 24 bytes |
| Transport Key (KEK) | 3DES | 16, 24 bytes |
| RSA | RSA 1024/2048 | 128, 256 bytes |

### 🏭 Fabricantes Soportados

- ✅ **Aisino/Vanstone** (A90 Pro)
- ✅ **Newpos** (NEW9220, NEW9830)
- ✅ **Urovo** (i9000S, i9100)

## 🏗️ Arquitectura

### Aplicaciones

El sistema consta de dos aplicaciones que trabajan coordinadas:

#### 1. **Injector** (Dispositivo Maestro)
- Generación y gestión de llaves
- Configuración de perfiles de inyección
- Envío de comandos Futurex
- Control de autenticación

#### 2. **KeyReceiver** (Dispositivo Receptor)
- Recepción de comandos de inyección
- Escritura de llaves en PED
- Gestión de llaves almacenadas
- Respuesta a polling

### Módulos

```
android_injector/
├── injector/              # Aplicación maestra (inyector)
├── keyreceiver/           # Aplicación receptora (SubPOS)
├── communication/         # Comunicación serial USB
├── manufacturer/          # Control de PED y SDKs
├── format/                # Protocolos Futurex y Legacy
├── persistence/           # Base de datos Room
├── config/                # Configuración del sistema
└── utils/                 # Utilidades criptográficas
```

## 🛠️ Tecnologías

- **UI**: Jetpack Compose + Material Design 3
- **Arquitectura**: MVVM + Clean Architecture
- **DI**: Hilt / Dagger
- **BD**: Room Database (SQLite)
- **Async**: Kotlin Coroutines + Flow
- **Criptografía**: Android KeyStore, Triple DES, AES
- **Build**: Gradle 8.10.2, AGP 8.6.0

## 📱 Requisitos

- **Android**: 8.0 (API 26) o superior
- **Recomendado**: Android 10+ (API 29+)
- **Arquitectura**: ARM, ARM64
- **Hardware**: Puerto USB, Módulo PED
- **Permisos**: USB, Almacenamiento, NFC

## 🚀 Inicio Rápido

### Instalación

```bash
# Clonar repositorio
git clone https://github.com/DiegoHDLCV/android_injector.git
cd android_injector

# Compilar aplicaciones
./gradlew :injector:assembleDebug
./gradlew :keyreceiver:assembleDebug

# Instalar en dispositivos
adb -s <MASTER_DEVICE> install injector/build/outputs/apk/debug/injector-debug.apk
adb -s <SUBPOS_DEVICE> install keyreceiver/build/outputs/apk/debug/keyreceiver-debug.apk
```

### Configuración Inicial

1. **Dispositivo Maestro (Injector)**:
   - Iniciar sesión: `admin` / `admin`
   - Crear ceremonia de llaves (KEK)
   - Configurar perfiles de inyección

2. **Dispositivo Receptor (KeyReceiver)**:
   - Conectar cable USB al maestro
   - Inyectar KEK desde maestro
   - Listo para recibir llaves

### Primer Uso

```kotlin
// 1. Generar KEK mediante ceremonia
CeremonyScreen -> "Nueva Llave" -> "KEK" -> Ingresar componentes

// 2. Crear perfil de inyección
ProfilesScreen -> "Nuevo Perfil" -> Configurar llaves

// 3. Conectar dispositivos por USB

// 4. Inyectar perfil
ProfilesScreen -> Seleccionar perfil -> "Inyectar"
```

## 📚 Documentación

### Documentación Principal

- **[📖 Índice General](docs/README.md)** - Punto de entrada a toda la documentación
- **[🏗️ Arquitectura](docs/DOCUMENTACION_01_INTRODUCCION_Y_ARQUITECTURA.md)** - Arquitectura y componentes
- **[📦 Módulos](docs/DOCUMENTACION_02_APLICACIONES_Y_MODULOS.md)** - Detalle de aplicaciones y módulos
- **[🔐 Criptografía](docs/DOCUMENTACION_03_TIPOS_LLAVES_CRIPTOGRAFIA.md)** - Tipos de llaves y algoritmos
- **[⚙️ Perfiles](docs/DOCUMENTACION_04_PERFILES_CONFIGURACION.md)** - Configuración de perfiles
- **[📡 Protocolos](docs/DOCUMENTACION_05_PROTOCOLOS_COMUNICACION.md)** - Protocolos de comunicación
- **[👥 Usuarios](docs/DOCUMENTACION_06_USUARIOS_PERSISTENCIA.md)** - Gestión de usuarios y datos
- **[🏭 Fabricantes](docs/DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md)** - Dispositivos soportados
- **[📖 Manual de Uso](docs/DOCUMENTACION_08_MANUAL_DE_USO.md)** - Guía de usuario

### Guías Técnicas

- **[🔌 Integración CH340](docs/guides/CH340_CABLE_INTEGRATION.md)** - Cable USB CH340
- **[🔧 Implementación DUKPT](docs/dukpt/)** - Documentación completa DUKPT
- **[📋 Plan de Pruebas](docs/guides/DOCUMENTACION_09_PLAN_PRUEBAS_QA.md)** - QA y testing

### Análisis Técnicos

Documentación detallada de análisis e implementaciones en [`docs/analysis/`](docs/analysis/).

## 🔒 Seguridad

### Capas de Protección

1. **Generación**: Ceremonia con división de secretos
2. **Almacenamiento**: Android KeyStore (hardware-backed)
3. **Transmisión**: Cifrado Triple DES con KEK
4. **Validación**: KCV + LRC checksums
5. **Auditoría**: Logs completos de operaciones

### Zero Internet Connectivity

🚫 **Sin conectividad a internet**:
- Permisos `INTERNET` y `ACCESS_NETWORK_STATE` eliminados
- Sin dependencias de red
- Operación completamente aislada
- Máxima seguridad para manejo de llaves

### Mejores Prácticas

- ✅ Cambiar contraseña por defecto (`admin/admin`)
- ✅ Generar KEK con ceremonia (mínimo 3 custodios)
- ✅ Usar perfiles específicos por aplicación
- ✅ Revisar logs de inyección regularmente
- ✅ Mantener dispositivos físicamente seguros

## 🧪 Testing

```bash
# Tests unitarios
./gradlew test

# Tests instrumentados
./gradlew connectedAndroidTest

# Cobertura de código
./gradlew jacocoTestReport

# Análisis estático
./gradlew lint
```

## 📊 CI/CD

El proyecto utiliza GitHub Actions para:

- ✅ Build automatizado
- ✅ Tests unitarios y instrumentados
- ✅ Análisis de SonarQube
- ✅ Lint y code coverage (JaCoCo)
- ✅ OWASP Dependency Check

Ver [`.github/workflows/android_ci.yml`](.github/workflows/android_ci.yml)

## 🤝 Contribución

Este es un proyecto propietario de uso interno. Para contribuciones:

1. Crear branch feature: `git checkout -b feature/nueva-funcionalidad`
2. Commit cambios: `git commit -m 'feat: descripción'`
3. Push branch: `git push origin feature/nueva-funcionalidad`
4. Crear Pull Request

### Convención de Commits

Seguimos [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` Nueva funcionalidad
- `fix:` Corrección de bug
- `docs:` Cambios en documentación
- `refactor:` Refactorización de código
- `test:` Añadir o modificar tests
- `chore:` Tareas de mantenimiento

## 📝 Changelog

Ver [CHANGELOG.md](CHANGELOG.md) para historial de versiones.

### Versión Actual: 1.4

**Últimos cambios:**
- 🚫 Eliminación completa de conectividad a internet
- ✨ Timeout configurable para custodios
- 🎨 Mejora UI de perfiles compactos
- 🔧 Actualización Java 11 → 17 en CI/CD

## 📄 Licencia

Este proyecto es propietario. Todos los derechos reservados.

© 2025 Vigatec S.A. - Sistema de Inyección de Llaves Criptográficas

## 👥 Equipo

**Organización**: Vigatec S.A.
**Proyecto**: Android Injector
**Contacto**: [contacto@vigatec.com](mailto:contacto@vigatec.com)

---

## 🔗 Enlaces Rápidos

| Recurso | Enlace |
|---------|--------|
| 📖 Documentación Completa | [docs/README.md](docs/README.md) |
| 🚀 Inicio Rápido | [docs/guides/QUICK_START.md](docs/guides/QUICK_START.md) |
| 🐛 Reportar Bug | [GitHub Issues](https://github.com/DiegoHDLCV/android_injector/issues) |
| 📋 Roadmap | [GitHub Projects](https://github.com/DiegoHDLCV/android_injector/projects) |
| 🔒 Security Policy | [SECURITY.md](SECURITY.md) |

---

**⭐ Si este proyecto te resulta útil, considera darle una estrella en GitHub**

