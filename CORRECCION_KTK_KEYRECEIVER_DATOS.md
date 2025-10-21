# Corrección: KTK No Se Guardaba Con Datos en KeyReceiver

## Problema Identificado

El keyreceiver estaba fallando al descifrar llaves porque la KTK (Key Transfer Key) no se guardaba con sus datos completos en la base de datos. El error específico era:

```
KEK/KTK (hex): ... (0 bytes)
Longitud KEK/KTK: 0 bytes
✗ Error al descifrar con KEK: KEK/KTK debe ser de 16, 24 o 32 bytes, recibido: 0
```

## Causa Raíz

El keyreceiver estaba usando `recordKeyInjection()` que **NO** guarda los datos de la llave, solo metadatos:

```kotlin
// ❌ PROBLEMA: No guarda keyData
injectedKeyRepository.recordKeyInjection(
    keySlot = command.keySlot,
    keyType = genericKeyType.name,
    keyAlgorithm = genericAlgorithm.name,
    kcv = command.keyChecksum,
    status = injectionStatus
)
```

Cuando intentaba descifrar, accedía a `ktkFromDb.keyData` que estaba vacío.

## Solución Implementada

### Modificación en MainViewModel.kt del KeyReceiver

**Antes:**
```kotlin
if (injectionStatus != "SKIPPED") {
    injectedKeyRepository.recordKeyInjection(
        keySlot = command.keySlot,
        keyType = genericKeyType.name,
        keyAlgorithm = genericAlgorithm.name,
        kcv = command.keyChecksum,
        status = injectionStatus
    )
}
```

**Después:**
```kotlin
if (injectionStatus != "SKIPPED") {
    // Para KTK (TRANSPORT_KEY), guardar con datos para poder descifrar posteriormente
    if (genericKeyType == GenericKeyType.TRANSPORT_KEY) {
        injectedKeyRepository.recordKeyInjectionWithData(
            keySlot = command.keySlot,
            keyType = genericKeyType.name,
            keyAlgorithm = genericAlgorithm.name,
            kcv = command.keyChecksum,
            keyData = command.keyHex, // ✅ Guardar los datos de la KTK
            status = injectionStatus,
            isKEK = true, // Marcar como KEK
            kekType = "KTK",
            customName = "KTK Slot ${command.keySlot}"
        )
        Log.i(TAG, "KTK guardada con datos completos para descifrado posterior")
    } else {
        injectedKeyRepository.recordKeyInjection(
            keySlot = command.keySlot,
            keyType = genericKeyType.name,
            keyAlgorithm = genericAlgorithm.name,
            kcv = command.keyChecksum,
            status = injectionStatus
        )
    }
}
```

## Comportamiento Actualizado

### Para KTK (TRANSPORT_KEY):
- ✅ Se guarda con `recordKeyInjectionWithData()`
- ✅ Se almacenan los datos completos de la llave (`keyData`)
- ✅ Se marca como `isKEK = true`
- ✅ Se puede usar para descifrar llaves posteriores

### Para Otras Llaves:
- ✅ Se mantiene `recordKeyInjection()` (solo metadatos)
- ✅ No se almacenan datos sensibles innecesariamente

## Flujo Corregido

1. **Inyección de KTK (tipo "06")**:
   ```
   EncryptionType: 00 (en claro)
   → Se inyecta al dispositivo
   → Se guarda en BD con datos completos
   ```

2. **Inyección de Llave Operacional (tipo "05")**:
   ```
   EncryptionType: 02 (cifrada con KTK)
   → Se busca KTK en BD (ahora con datos)
   → Se descifra la llave con KTK
   → Se inyecta al dispositivo
   ```

## Logs Esperados

**KTK guardada:**
```
KTK guardada con datos completos para descifrado posterior
Resultado de inyección para slot 0 registrado en la BD como: SUCCESSFUL
```

**Descifrado exitoso:**
```
KTK encontrada en BD:
  - Slot: 0
  - KCV: 3456
  - Algoritmo: DES_TRIPLE
Llave descifrada exitosamente
  - Longitud: 24 bytes
  - KCV esperado: 3456
```

## Archivos Modificados

- `keyreceiver/src/main/java/com/vigatec/keyreceiver/viewmodel/MainViewModel.kt`
  - Líneas 676-700: Lógica condicional para guardar KTK con datos

## Resultado

Ahora el keyreceiver puede:
1. ✅ Guardar KTK con datos completos
2. ✅ Encontrar KTK en la base de datos
3. ✅ Usar KTK para descifrar llaves operacionales
4. ✅ Completar el flujo de inyección cifrada

El error "KEK/KTK debe ser de 16, 24 o 32 bytes, recibido: 0" ya no debería ocurrir.
