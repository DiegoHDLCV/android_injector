# Corrección: KTK Siempre Debe Inyectarse

## Problema Identificado

Según el log proporcionado, el sistema estaba verificando si la KEK ya fue exportada previamente y se saltaba la inyección si el estado era "EXPORTED". Esto causaba que la KTK no se inyectara en inyecciones subsecuentes.

**Log problemático:**
```
2025-10-21 10:14:56.345 10883-10883 KeyInjectionViewModel   com.vigatec.injector                 I    - Estado: EXPORTED
2025-10-21 10:14:56.345 10883-10883 KeyInjectionViewModel   com.vigatec.injector                 I  ✓ KEK ya fue exportada previamente
```

## Solución Implementada

### 1. Modificación en KeyInjectionViewModel.kt

**Antes:**
```kotlin
// Verificar si la KEK ya fue exportada
if (kek.status != "EXPORTED") {
    // Exportar la KEK
    exportKEKToDevice(kek)
    // Actualizar estado a EXPORTED
    injectedKeyRepository.updateKeyStatus(kek.kcv, "EXPORTED")
} else {
    Log.i(TAG, "✓ KEK ya fue exportada previamente")
}
```

**Después:**
```kotlin
// SIEMPRE inyectar la KTK seleccionada (sin verificar estado exported)
Log.i(TAG, "Inyectando KTK al SubPOS (siempre requerida)...")
exportKEKToDevice(kek)
Log.i(TAG, "✓ KTK inyectada exitosamente")
```

### 2. Modificación en KEKManager.kt

**Cambios realizados:**

1. **Eliminación del estado EXPORTED:**
   - `hasActiveKEK()` ahora solo verifica estado "ACTIVE"
   - `getActiveKEKEntity()` solo filtra por estado "ACTIVE"
   - Eliminada la función `markKEKAsExported()`

2. **Nueva función `markKEKAsInactive()`:**
   - Reemplaza la funcionalidad de marcar como exportada
   - Marca KEK como "INACTIVE" cuando ya no se usa

### 3. Comportamiento Actualizado

**Antes:**
- Primera inyección: Exporta KEK + inyecta llaves cifradas
- Inyecciones posteriores: Solo inyecta llaves cifradas (KEK ya exportada)

**Después:**
- **Todas las inyecciones**: Siempre inyecta KTK + inyecta llaves cifradas
- No hay verificación de estado "EXPORTED"
- La KTK se inyecta en cada inyección de perfil

## Archivos Modificados

1. `injector/src/main/java/com/vigatec/injector/viewmodel/KeyInjectionViewModel.kt`
   - Líneas 238-284: Eliminada verificación de estado EXPORTED
   - Siempre inyecta KTK seleccionada

2. `injector/src/main/java/com/vigatec/injector/manager/KEKManager.kt`
   - Líneas 28-38: Solo verifica estado ACTIVE
   - Líneas 189-209: Nueva función markKEKAsInactive()
   - Líneas 211-250: Solo filtra por estado ACTIVE

## Resultado Esperado

Ahora cuando se ejecute una inyección de perfil:

1. **Siempre** se inyectará la KTK seleccionada al SubPOS
2. **No** se verificará si ya fue exportada previamente
3. **Después** se inyectarán las llaves operacionales cifradas con la KTK
4. El log mostrará: "Inyectando KTK al SubPOS (requerida para cada inyección)..."

## Logs Esperados

```
=== INYECTANDO KTK SELECCIONADA ===
KTK encontrada:
  - KCV: D4E5F6
  - Nombre: DUKPT BDK 3DES
  - Estado: ACTIVE
  - Es KEK: true
Inyectando KTK al SubPOS (siempre requerida)...
✓ KTK inyectada exitosamente al slot 00
```

## Notas Técnicas

- El estado "EXPORTED" ya no se usa en el sistema
- Solo se mantienen estados: "ACTIVE", "INACTIVE", "GENERATED", "SUCCESSFUL"
- La KTK se inyecta en el slot 00 (fijo para KEKs)
- Las llaves operacionales se cifran con la KTK antes de enviarse
