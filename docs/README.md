# 📚 Documentación Completa - Android Injector

Bienvenido a la documentación del **Sistema de Inyección de Llaves Criptográficas** para dispositivos Android POS.

## 📖 Índice de Contenidos

### 🎯 Documentación Core

Documentación principal del sistema organizada en 9 partes:

1. **[📖 Índice General](core/DOCUMENTACION_00_INDICE.md)** - Vista general y navegación
2. **[🏗️ Introducción y Arquitectura](core/DOCUMENTACION_01_INTRODUCCION_Y_ARQUITECTURA.md)** - Arquitectura del sistema, componentes y patrones
3. **[📦 Aplicaciones y Módulos](core/DOCUMENTACION_02_APLICACIONES_Y_MODULOS.md)** - Detalle de Injector, KeyReceiver y módulos compartidos
4. **[🔐 Tipos de Llaves y Criptografía](core/DOCUMENTACION_03_TIPOS_LLAVES_CRIPTOGRAFIA.md)** - Algoritmos, tipos de llaves, KCV, KEK y DUKPT
5. **[⚙️ Perfiles y Configuración](core/DOCUMENTACION_04_PERFILES_CONFIGURACION.md)** - Gestión de perfiles y configuraciones
6. **[📡 Protocolos de Comunicación](core/DOCUMENTACION_05_PROTOCOLOS_COMUNICACION.md)** - Futurex, Legacy y polling
7. **[👥 Usuarios y Persistencia](core/DOCUMENTACION_06_USUARIOS_PERSISTENCIA.md)** - Sistema de usuarios y base de datos Room
8. **[🏭 Fabricantes y Dispositivos](core/DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md)** - Aisino, Newpos, Urovo
9. **[📖 Manual de Uso](core/DOCUMENTACION_08_MANUAL_DE_USO.md)** - Guía de usuario completa

### 🚀 Guías de Implementación

Guías técnicas y de integración:

#### Integración de Hardware
- **[🔌 CH340 Cable Integration](guides/CH340_CABLE_INTEGRATION.md)** - Integración del cable USB CH340
- **[⚡ CH340 Quick Reference](guides/CH340_QUICK_REFERENCE.md)** - Referencia rápida CH340

#### Implementaciones Específicas
- **[🔧 Ruta C-B Complete Summary](guides/RUTA_C_B_COMPLETE_SUMMARY.md)** - Resumen implementación Ruta C-B
- **[🏗️ Architecture Ruta C-B](guides/ARCHITECTURE_RUTA_C_B.md)** - Arquitectura Ruta C-B
- **[📋 Expected Logs Ruta C-B](guides/EXPECTED_LOGS_RUTA_C_B.md)** - Logs esperados
- **[🚀 Quick Start Ruta C-B](guides/QUICK_START_RUTA_C_B.md)** - Inicio rápido
- **[📝 README Ruta C-B](guides/README_RUTA_C_B.md)** - Documentación Ruta C-B

#### Integración Aisino
- **[📘 Aisino Integration Complete](guides/README_AISINO_INTEGRATION_COMPLETE.md)** - Guía completa de integración Aisino
- **[🎯 Integration Strategy](guides/INTEGRATION_STRATEGY_AISINO_DEMO.md)** - Estrategia de integración
- **[💡 Practical Examples](guides/PRACTICAL_EXAMPLES_INTEGRATION.md)** - Ejemplos prácticos
- **[🔀 Decision Matrix](guides/DECISION_MATRIX_AISINO_INTEGRATION.md)** - Matriz de decisiones

#### Puertos Virtuales
- **[🔧 Virtual Ports Implementation](guides/IMPLEMENTATION_VIRTUAL_PORTS.md)** - Implementación puertos virtuales
- **[📋 Virtual Ports Summary](guides/SUMMARY_VIRTUAL_PORTS_IMPLEMENTATION.md)** - Resumen implementación
- **[🚀 Deployment Guide](guides/DEPLOYMENT_GUIDE_VIRTUAL_PORTS.md)** - Guía de despliegue

#### Perfiles y Testing
- **[⚙️ README Perfiles](guides/README_PERFILES.md)** - Documentación de perfiles
- **[📝 Quick Guide Profiles](guides/QUICK_GUIDE_PROFILES.md)** - Guía rápida de perfiles
- **[🧪 Plan de Pruebas QA](guides/DOCUMENTACION_09_PLAN_PRUEBAS_QA.md)** - Plan de pruebas y QA
- **[🔬 Plan Pruebas USB](guides/PLAN_PRUEBAS_USB.md)** - Pruebas de USB
- **[🧪 Test Plan Aisino-Aisino](guides/TEST_PLAN_AISINO_AISINO.md)** - Plan de pruebas Aisino

#### Extensiones
- **[🔐 Extensión Protocolo Futurex](guides/EXTENSION_PROTOCOLO_FUTUREX.md)** - Extensiones al protocolo Futurex

### 🔬 Análisis Técnicos

Documentos de análisis e investigación:

#### Análisis Aisino
- **[🔍 Comparison Aisino Demo](analysis/ANALYSIS_COMPARISON_AISINO_DEMO.md)** - Comparación con demo de Aisino
- **[⚠️ Aisino TX Error](analysis/ANALYSIS_AISINO_TX_ERROR.md)** - Análisis de error de transmisión
- **[⏱️ Listening Timeout](analysis/ANALYSIS_AISINO_LISTENING_TIMEOUT.md)** - Análisis de timeout
- **[🔌 Puerto Compartido](analysis/ANALYSIS_AISINO_PUERTO_COMPARTIDO.md)** - Análisis puerto compartido
- **[📡 Detección Cable](analysis/ANÁLISIS_DETECCIÓN_CABLE_AISINO_AISINO.md)** - Detección de cable USB

### 📚 Documentación DUKPT

Documentación completa de DUKPT (Derived Unique Key Per Transaction):

- **[📖 DUKPT Index](dukpt/DUKPT_INDEX.md)** - Índice completo DUKPT
- **[📘 Complete Guide](dukpt/DUKPT_COMPLETE_GUIDE.md)** - Guía completa
- **[📗 DUKPT Guide](dukpt/DUKPT_GUIDE.md)** - Guía general
- **[🔐 3DES Summary](dukpt/DUKPT_3DES_SUMMARY.md)** - Resumen 3DES
- **[🔑 KSN Implementation](dukpt/DUKPT_KSN_IMPLEMENTATION.md)** - Implementación KSN
- **[⚡ Test Quickstart](dukpt/DUKPT_TEST_QUICKSTART.md)** - Inicio rápido para testing
- **[📄 README](dukpt/README.md)** - README DUKPT

## 🎓 Rutas de Aprendizaje

### Para Desarrolladores Nuevos

**Orden recomendado de lectura:**

1. 📖 [Índice General](core/DOCUMENTACION_00_INDICE.md)
2. 🏗️ [Introducción y Arquitectura](core/DOCUMENTACION_01_INTRODUCCION_Y_ARQUITECTURA.md)
3. 📦 [Aplicaciones y Módulos](core/DOCUMENTACION_02_APLICACIONES_Y_MODULOS.md)
4. 📡 [Protocolos de Comunicación](core/DOCUMENTACION_05_PROTOCOLOS_COMUNICACION.md)
5. 🔐 [Tipos de Llaves](core/DOCUMENTACION_03_TIPOS_LLAVES_CRIPTOGRAFIA.md)
6. ⚙️ [Perfiles](core/DOCUMENTACION_04_PERFILES_CONFIGURACION.md)

**Tiempo estimado:** 4-6 horas

### Para Administradores del Sistema

**Lectura esencial:**

1. 🏗️ [Introducción y Arquitectura](core/DOCUMENTACION_01_INTRODUCCION_Y_ARQUITECTURA.md) - Conceptos generales
2. 🔐 [Tipos de Llaves](core/DOCUMENTACION_03_TIPOS_LLAVES_CRIPTOGRAFIA.md) - Seguridad
3. ⚙️ [Perfiles y Configuración](core/DOCUMENTACION_04_PERFILES_CONFIGURACION.md) - Gestión de perfiles
4. 📖 [Manual de Uso](core/DOCUMENTACION_08_MANUAL_DE_USO.md) - Operación diaria

**Tiempo estimado:** 2-3 horas

### Para Operadores

**Lectura esencial:**

1. 📖 [Manual de Uso](core/DOCUMENTACION_08_MANUAL_DE_USO.md) - Guía completa de usuario
2. ⚙️ [Perfiles](core/DOCUMENTACION_04_PERFILES_CONFIGURACION.md) - Uso de perfiles
3. 📝 [Quick Guide Profiles](guides/QUICK_GUIDE_PROFILES.md) - Guía rápida

**Tiempo estimado:** 1 hora

### Para Integradores

**Lectura esencial:**

1. 📦 [Módulos del Sistema](core/DOCUMENTACION_02_APLICACIONES_Y_MODULOS.md)
2. 📡 [Protocolos](core/DOCUMENTACION_05_PROTOCOLOS_COMUNICACION.md)
3. 🏭 [Fabricantes](core/DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md)
4. 🔌 [CH340 Integration](guides/CH340_CABLE_INTEGRATION.md)
5. 📘 [Aisino Integration](guides/README_AISINO_INTEGRATION_COMPLETE.md)

**Tiempo estimado:** 3-4 horas

## 📊 Estadísticas del Proyecto

- **📝 Documentos totales**: ~50 archivos
- **📚 Palabras**: ~150,000
- **⏱️ Tiempo lectura completa**: ~12 horas
- **🔧 Líneas de código**: ~50,000+
- **📦 Módulos**: 9
- **🏭 Fabricantes soportados**: 3

## 🔍 Búsqueda Rápida por Tema

### Por Componente
- **Injector**: [Aplicaciones y Módulos](core/DOCUMENTACION_02_APLICACIONES_Y_MODULOS.md#211-aplicación-injector)
- **KeyReceiver**: [Aplicaciones y Módulos](core/DOCUMENTACION_02_APLICACIONES_Y_MODULOS.md#212-aplicación-app)
- **Communication**: [Protocolos](core/DOCUMENTACION_05_PROTOCOLOS_COMUNICACION.md#3-comunicación-serial-usb)
- **Manufacturer**: [Fabricantes](core/DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md)

### Por Funcionalidad
- **Ceremonia de Llaves**: [Criptografía](core/DOCUMENTACION_03_TIPOS_LLAVES_CRIPTOGRAFIA.md#3-generación-de-llaves)
- **Inyección**: [Perfiles](core/DOCUMENTACION_04_PERFILES_CONFIGURACION.md#4-flujo-de-inyección-desde-perfil)
- **Polling**: [Protocolos](core/DOCUMENTACION_05_PROTOCOLOS_COMUNICACION.md#3-servicio-de-polling)
- **DUKPT**: [DUKPT Complete Guide](dukpt/DUKPT_COMPLETE_GUIDE.md)

### Por Fabricante
- **Aisino**: [Fabricantes](core/DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md#3-aisino-vanstone)
- **Newpos**: [Fabricantes](core/DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md#4-newpos)
- **Urovo**: [Fabricantes](core/DOCUMENTACION_07_FABRICANTES_DISPOSITIVOS.md#5-urovo)

## 🆘 Soporte y Ayuda

### Problemas Comunes

Ver sección de **Troubleshooting** en:
- [Manual de Uso](core/DOCUMENTACION_08_MANUAL_DE_USO.md#troubleshooting)
- [Perfiles](core/DOCUMENTACION_04_PERFILES_CONFIGURACION.md#troubleshooting)

### Reportar Issues

- **GitHub Issues**: [https://github.com/DiegoHDLCV/android_injector/issues](https://github.com/DiegoHDLCV/android_injector/issues)
- **Email**: contacto@vigatec.com

## 🔄 Actualizaciones

**Última actualización de documentación**: Noviembre 2025
**Versión del sistema**: 1.4

Para ver cambios recientes, consulta:
- [CHANGELOG.md](../CHANGELOG.md)
- [Historial de commits](https://github.com/DiegoHDLCV/android_injector/commits/main)

## 📄 Licencia

Esta documentación describe un sistema propietario.
© 2025 Vigatec S.A. - Todos los derechos reservados.

---

**🏠 [Volver al README Principal](../README.md)**

