# RESUMEN EJECUTIVO - ESTRUCTURA Y VALIDACIÓN DE LLAVES

## Información Rápida

**Documentos generados:**
1. `ANALISIS_ESTRUCTURA_VALIDACION_LLAVES.md` - Análisis técnico completo
2. `VALIDACION_ELIMINACION_LLAVES_RECOMENDACIONES.md` - Guía de implementación
3. `RESUMEN_ANALISIS_ESTRUCTURA.md` - Este documento

---

## 1. ESTRUCTURA DE ENTIDADES

### Tres tablas principales:

**KeyEntity** (Tabla: "key")
- Almacena metadatos administrativos de llaves
- Campos: id, keyValue, description, createdByAdminId, creationDate, isActive

**InjectedKeyEntity** (Tabla: "injected_keys") ⭐ MÁS IMPORTANTE
- Almacena llaves criptográficas reales inyectadas en el PED
- Campos críticos: id, keySlot, keyType, kcv, encryptedKeyData, status, kekType
- Tipos de KEK: NONE (operacional), KEK_STORAGE (cifra la BD), KEK_TRANSPORT (cifra para SubPOS)

**ProfileEntity** (Tabla: "profiles")
- Agrupa configuraciones de llaves por aplicación
- Referencias a llaves mediante:
  - `keyConfigurations[].selectedKey` = KCV de la llave
  - `selectedKEKKcv` = KCV de la KEK para cifrado

---

## 2. RELACIONES ENTRE ENTIDADES

```
ProfileEntity.keyConfigurations[].selectedKey = InjectedKeyEntity.kcv
ProfileEntity.selectedKEKKcv = InjectedKeyEntity.kcv (cuando es KEK Storage)
```

**Cómo funciona la búsqueda:**
```kotlin
// En ProfileDao - busca perfiles que usan una llave específica
@Query("SELECT name FROM profiles WHERE keyConfigurations LIKE '%' || :kcv || '%'")
suspend fun getProfileNamesByKeyKcv(kcv: String): List<String>
```

---

## 3. UBICACIONES DE ARCHIVOS CLAVE

### Base de datos y DAOs:
- `/persistence/src/main/java/com/vigatec/persistence/entities/KeyEntity.kt`
- `/persistence/src/main/java/com/vigatec/persistence/entities/InjectedKeyEntity.kt`
- `/persistence/src/main/java/com/vigatec/persistence/entities/ProfileEntity.kt`
- `/persistence/src/main/java/com/vigatec/persistence/dao/InjectedKeyDao.kt` ⭐
- `/persistence/src/main/java/com/vigatec/persistence/dao/ProfileDao.kt`

### Repositorios:
- `/persistence/src/main/java/com/vigatec/persistence/repository/InjectedKeyRepository.kt` ⭐
- `/persistence/src/main/java/com/vigatec/persistence/repository/ProfileRepository.kt`

### Interfaz de Usuario:
- `/injector/src/main/java/com/vigatec/injector/viewmodel/KeyVaultViewModel.kt` ⭐
- `/injector/src/main/java/com/vigatec/injector/ui/screens/KeyVaultScreen.kt`

---

## 4. LÓGICA ACTUAL DE ELIMINACIÓN

**Ubicación:** KeyVaultViewModel.kt - función `onDeleteKey()`

```kotlin
fun onDeleteKey(key: InjectedKeyEntity) {
    1. Verificar admin ✓
    2. Si es KEK Storage → eliminar del Keystore
    3. Eliminar de BD
    4. Recargar lista
}
```

**PROBLEMA:** No valida si la llave está siendo usada en perfiles

---

## 5. LO QUE NECESITA VALIDARSE ANTES DE ELIMINAR

### 1. Está siendo usada en keyConfigurations de algún perfil?
```kotlin
val profilesUsingKey = profileRepository.getProfileNamesByKeyKcv(key.kcv)
if (profilesUsingKey.isNotEmpty()) {
    // ERROR BLOQUEANTE - No permitir eliminación
}
```

### 2. Es KEK Storage?
```kotlin
if (key.isKEKStorage()) {
    // Buscar si algún perfil tiene useKEK=true y selectedKEKKcv=key.kcv
    // Si sí → ERROR BLOQUEANTE
}
```

### 3. Es KTK activa?
```kotlin
if (key.isKEKTransport()) {
    // Es la KTK activa → ADVERTENCIA (permitir con confirmación extra)
}
```

---

## 6. RESPUESTA A TUS PREGUNTAS

### P1: Estructura de Key, Package, Profile y sus relaciones

**Key** (KeyEntity) → Metadata administrativo
**InjectedKey** (InjectedKeyEntity) → Llave real con datos + cifrado
**Profile** (ProfileEntity) → Agrupación de llaves

Relación:
```
InjectedKeyEntity.kcv ← referenciado por → ProfileEntity.keyConfigurations[].selectedKey
InjectedKeyEntity.kcv ← referenciado por → ProfileEntity.selectedKEKKcv
```

No hay tabla "Package" - solo ProfileEntity.

### P2: Dónde se implementa la lógica de eliminación

**Archivo:** `/injector/src/main/java/com/vigatec/injector/viewmodel/KeyVaultViewModel.kt`
**Función:** `onDeleteKey(key: InjectedKeyEntity)`
**Líneas:** 128-154

### P3: DAOs relacionados

- **InjectedKeyDao** - Operaciones de llaves (deleteKey, deleteAllKeys, getCurrentKEK, etc.)
- **ProfileDao** - Búsqueda de relaciones (getProfileNamesByKeyKcv)
- **KeyDao** - Operaciones de metadata

### P4: Obtener perfiles que contienen una llave

```kotlin
// Ya implementado:
val profiles = profileRepository.getProfileNamesByKeyKcv(key.kcv)

// Retorna: List<String> con nombres de perfiles que usan esta llave
```

---

## 7. MATRIZ DE DECISIÓN PARA ELIMINACIÓN

| Llave Type | ¿Está en Perfiles? | ¿Es KEK? | ¿Es KTK? | Acción |
|-------------|-----------------|---------|---------|--------|
| Operacional | NO | NO | NO | ELIMINAR ✓ |
| Operacional | SÍ | NO | NO | RECHAZAR ❌ |
| KEK Storage | NO | SÍ | NO | ELIMINAR ✓ |
| KEK Storage | SÍ | SÍ | NO | RECHAZAR ❌ |
| KTK | NO | NO/SÍ | SÍ | ADVERTENCIA ⚠ |
| KTK | SÍ | NO/SÍ | SÍ | RECHAZAR ❌ |

---

## 8. CÓMO IMPLEMENTAR LA VALIDACIÓN

**Pasos:**

1. Crear data class en KeyVaultViewModel.kt:
```kotlin
data class KeyDeletionValidation(
    val canDelete: Boolean,
    val severity: DeletionSeverity,  // ALLOWED, WARNING, BLOCKED
    val reason: String,
    val affectedProfiles: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)
```

2. Agregar función en InjectedKeyRepository.kt:
```kotlin
suspend fun validateKeyDeletion(key: InjectedKeyEntity): KeyDeletionValidation
```

3. Modificar onDeleteKey() en KeyVaultViewModel.kt para validar antes de eliminar

4. Agregar diálogos en KeyVaultScreen.kt para mostrar errores/advertencias

---

## 9. BENEFICIOS CLAVE

✓ Previene eliminación accidental de llaves en uso
✓ Integridad de datos - los perfiles no quedaran con referencias rotas
✓ Experiencia de usuario mejorada con mensajes claros
✓ Diferencia entre error bloqueante y advertencia
✓ Registros de auditoría limpios

---

## 10. PRÓXIMOS PASOS

1. Leer `ANALISIS_ESTRUCTURA_VALIDACION_LLAVES.md` para entender la arquitectura
2. Leer `VALIDACION_ELIMINACION_LLAVES_RECOMENDACIONES.md` para código específico
3. Implementar `validateKeyDeletion()` en InjectedKeyRepository
4. Actualizar KeyVaultViewModel y KeyVaultScreen con nuevos diálogos
5. Probar con casos de uso especiales (KEK, KTK, perfiles múltiples)

---

## 11. ARCHIVOS A MODIFICAR

1. **InjectedKeyRepository.kt**
   - Agregar: `validateKeyDeletion(key: InjectedKeyEntity)`

2. **KeyVaultViewModel.kt**
   - Modificar: `onDeleteKey()`
   - Agregar: `onConfirmDeleteKeyAfterWarning()`
   - Actualizar: KeyVaultState con campos de validación

3. **KeyVaultScreen.kt**
   - Agregar: Dialog para errores de validación
   - Agregar: Dialog para advertencias

4. **ProfileDao.kt** (Opcional)
   - Agregar: `getProfilesUsingKEK(kcv: String)`

---

Documentación completa disponible en:
- `ANALISIS_ESTRUCTURA_VALIDACION_LLAVES.md`
- `VALIDACION_ELIMINACION_LLAVES_RECOMENDACIONES.md`

