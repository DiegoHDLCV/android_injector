# ÍNDICE DE DOCUMENTACIÓN - ESTRUCTURA DE LLAVES Y VALIDACIÓN

Este índice te ayuda a navegar la documentación generada sobre la estructura del proyecto y cómo implementar validación al eliminar llaves.

---

## DOCUMENTOS DISPONIBLES

### 1. RESUMEN_ANALISIS_ESTRUCTURA.md (EMPIEZA AQUÍ)
**Extensión:** 6.9 KB | **Tiempo de lectura:** 5-10 minutos

Documento ejecutivo que responde directamente a tus preguntas:
- Estructura de entidades (Key, InjectedKey, Profile)
- Relaciones entre entidades
- Ubicación de archivos clave
- Lógica actual de eliminación
- Qué necesita validarse

**Recomendación:** Leer primero para obtener una visión general rápida.

---

### 2. ANALISIS_ESTRUCTURA_VALIDACION_LLAVES.md (ANÁLISIS TÉCNICO PROFUNDO)
**Extensión:** 16 KB | **Tiempo de lectura:** 15-20 minutos

Análisis técnico detallado con:
- Definición completa de cada entidad con código Kotlin
- Diagramas ASCII de relaciones
- Descripción de todos los DAOs y métodos
- Explicación de la lógica actual de eliminación
- Cómo se relacionan llaves con perfiles (búsqueda LIKE en JSON)
- Estructura de directorios del proyecto
- Matriz de relaciones entre entidades

**Recomendación:** Leer para entender a fondo cómo funciona la arquitectura.

---

### 3. VALIDACION_ELIMINACION_LLAVES_RECOMENDACIONES.md (GUÍA IMPLEMENTACIÓN)
**Extensión:** 18 KB | **Tiempo de lectura:** 15-20 minutos

Guía paso a paso para implementar la validación con:
- Diagrama de flujo del proceso
- 4 casos de uso detallados (operacional, usada, KEK, KTK)
- Matriz de decisión para cada tipo de llave
- Código Kotlin completo para:
  - Data classes
  - Función validateKeyDeletion()
  - Actualización de ViewModel
  - Cambios en UI
  - Nuevos métodos en DAOs
- Lista de archivos a modificar

**Recomendación:** Usar como guía al implementar la validación.

---

## ESTRUCTURA RÁPIDA DE NAVEGACIÓN

### Si tienes poco tiempo (5 minutos):
1. Lee la sección "1. ESTRUCTURA DE ENTIDADES" en `RESUMEN_ANALISIS_ESTRUCTURA.md`
2. Mira la "Matriz de Decisión" en la misma sección

### Si tienes 15 minutos:
1. Lee `RESUMEN_ANALISIS_ESTRUCTURA.md` completo
2. Revisa los casos de uso en `VALIDACION_ELIMINACION_LLAVES_RECOMENDACIONES.md`

### Si tienes 30+ minutos:
1. Lee todos los documentos en orden
2. Comienza a implementar usando `VALIDACION_ELIMINACION_LLAVES_RECOMENDACIONES.md` como guía

---

## RESPUESTA RÁPIDA A TUS PREGUNTAS INICIALES

### P: Busca y explica la estructura de entidades/modelos relacionadas

**Respuesta:** Ver `RESUMEN_ANALISIS_ESTRUCTURA.md` sección "1. ESTRUCTURA DE ENTIDADES"

Tres tablas:
- **KeyEntity** - Metadata administrativo
- **InjectedKeyEntity** - Llaves reales con datos cifrados
- **ProfileEntity** - Agrupaciones de llaves

### P: Encuentra dónde se implementa la lógica de eliminación de llaves

**Respuesta:** Ver `ANALISIS_ESTRUCTURA_VALIDACION_LLAVES.md` sección "3. LÓGICA ACTUAL DE ELIMINACIÓN"

Ubicación: `/injector/viewmodel/KeyVaultViewModel.kt`
Función: `onDeleteKey(key: InjectedKeyEntity)` (líneas 128-154)

### P: Busca los DAOs o repositorios relacionados

**Respuesta:** Ver `ANALISIS_ESTRUCTURA_VALIDACION_LLAVES.md` secciones "2.1" a "2.5"

DAOs clave:
- **InjectedKeyDao** - Operaciones de llaves
- **ProfileDao** - Búsqueda de relaciones
- **KeyDao** - Metadata

### P: Indica dónde se manejan los perfiles y cómo obtener perfiles con una llave

**Respuesta:** Ver `ANALISIS_ESTRUCTURA_VALIDACION_LLAVES.md` sección "5. CÓMO SE RELACIONAN"

Implementado en: ProfileRepository.kt
Método: `getProfileNamesByKeyKcv(kcv: String): List<String>`

---

## DIAGRAMA SIMPLIFICADO DE RELACIONES

```
InjectedKeyEntity
    ↑
    │ (referenciado por)
    │
ProfileEntity
├─ keyConfigurations[].selectedKey = InjectedKeyEntity.kcv
└─ selectedKEKKcv = InjectedKeyEntity.kcv (si usa KEK)
```

---

## ARCHIVOS DEL PROYECTO MODIFICADOS/RELEVANTES

### Rutas completas de archivos clave:

**Entidades:**
- `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/entities/KeyEntity.kt`
- `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/entities/InjectedKeyEntity.kt`
- `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/entities/ProfileEntity.kt`

**DAOs:**
- `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/dao/InjectedKeyDao.kt`
- `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/dao/ProfileDao.kt`

**Repositorios:**
- `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/repository/InjectedKeyRepository.kt`
- `/Users/diegoherreradelacalle/StudioProjects/android_injector/persistence/src/main/java/com/vigatec/persistence/repository/ProfileRepository.kt`

**UI/ViewModel:**
- `/Users/diegoherreradelacalle/StudioProjects/android_injector/injector/src/main/java/com/vigatec/injector/viewmodel/KeyVaultViewModel.kt`
- `/Users/diegoherreradelacalle/StudioProjects/android_injector/injector/src/main/java/com/vigatec/injector/ui/screens/KeyVaultScreen.kt`

---

## LISTA DE VERIFICACIÓN PARA IMPLEMENTACIÓN

### Fase 1: Preparación
- [ ] Leer `RESUMEN_ANALISIS_ESTRUCTURA.md`
- [ ] Leer `ANALISIS_ESTRUCTURA_VALIDACION_LLAVES.md`
- [ ] Leer `VALIDACION_ELIMINACION_LLAVES_RECOMENDACIONES.md`

### Fase 2: Implementación
- [ ] Crear data classes `KeyDeletionValidation` y enum `DeletionSeverity`
- [ ] Implementar método `validateKeyDeletion()` en `InjectedKeyRepository`
- [ ] Modificar `onDeleteKey()` en `KeyVaultViewModel`
- [ ] Agregar nuevos métodos en `KeyVaultViewModel`
- [ ] Actualizar estado `KeyVaultState`
- [ ] Agregar diálogos en `KeyVaultScreen`

### Fase 3: Testing
- [ ] Probar eliminación de llave sin uso
- [ ] Probar eliminación de llave en uso (debe rechazar)
- [ ] Probar eliminación de KEK Storage en uso
- [ ] Probar eliminación de KTK activa (debe advertir)
- [ ] Verificar mensajes de error/advertencia
- [ ] Verificar sugerencias para usuario

---

## CONCEPTOS CLAVE

### KCV (Key Checksum Value)
Identificador único hexadecimal de una llave. Usado para referenciarse entre tablas.
Ejemplo: `"A1B2C3"`, `"D4E5F6"`

### KEK (Key Encryption Key)
Llave que cifra otras llaves:
- **KEK Storage** - Cifra llaves en BD local
- **KEK Transport (KTK)** - Cifra llaves para envío a SubPOS

### Perfil
Agrupación de configuraciones de llaves para un caso de uso específico.
Ejemplo: "Perfil Venta", "Perfil Factura"

### Validación de eliminación
Proceso que verifica antes de eliminar una llave si está siendo usada en perfiles.

---

## CAMBIOS PRINCIPALES A REALIZAR

1. **InjectedKeyRepository.kt**
   - Agregar: `validateKeyDeletion(key: InjectedKeyEntity): KeyDeletionValidation`

2. **KeyVaultViewModel.kt**
   - Modificar: `onDeleteKey(key: InjectedKeyEntity)`
   - Agregar: `executeKeyDeletion(key: InjectedKeyEntity)`
   - Agregar: `onConfirmDeleteKeyAfterWarning()`
   - Actualizar: `KeyVaultState` con 4 nuevos campos

3. **KeyVaultScreen.kt**
   - Agregar: Dialog para error de validación
   - Agregar: Dialog para advertencia

4. **ProfileDao.kt** (Opcional)
   - Agregar: `getProfilesUsingKEK(kcv: String): List<String>`

---

## PREGUNTAS FRECUENTES

**P: ¿Por qué hay dos tipos de Key (KeyEntity e InjectedKeyEntity)?**
R: KeyEntity es metadata administrativa, InjectedKeyEntity es la llave real con datos criptográficos.

**P: ¿Cómo sabe el sistema qué perfiles usan una llave?**
R: ProfileDao.getProfileNamesByKeyKcv() busca el KCV en el JSON de keyConfigurations.

**P: ¿Qué pasa si elimino una llave usada en un perfil?**
R: Actualmente nada - eso es el PROBLEMA que necesitas resolver. Después será BLOQUEADO.

**P: ¿Cuál es la diferencia entre ERROR BLOQUEANTE y ADVERTENCIA?**
R: BLOQUEANTE = no permite eliminación | ADVERTENCIA = permite pero pide confirmación extra

---

## CONTACTO RÁPIDO

Para más detalles sobre cualquier aspecto, consulta:
- Documentación completa en los 3 archivos principales
- Código fuente comentado en los archivos .kt
- Diagramas y matrices en los documentos

---

**Generado:** 11 de noviembre, 2025
**Rama:** feature/AI-85
**Estado:** Documentación completa, listo para implementación

