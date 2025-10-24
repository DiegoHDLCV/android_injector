# Guía de Configuración DUKPT AES

## Resumen Ejecutivo

Esta guía explica cómo configurar perfiles para inyección de llaves DUKPT AES en dispositivos POS usando NewPOS PED.

**Estado actual**: ⚠️ **DUKPT AES no está implementado todavía**

## 1. ¿Qué es DUKPT?

**DUKPT (Derived Unique Key Per Transaction)** es un estándar de seguridad que:
- Genera una llave única para cada transacción
- Elimina la necesidad de re-inyección periódica de llaves
- Cumple con estándares modernos PCI-DSS
- Soporta AES-128, AES-192, y AES-256

### Componentes DUKPT:

1. **IPEK (Initial PIN Encryption Key)**: Llave maestra inicial
2. **KSN (Key Serial Number)**: Número de serie de 20 dígitos hexadecimales
3. **KBPK (Key Block Protection Key)**: Llave de transporte para cifrar IPEK (equivalente a KTK)

---

## 2. Limitaciones Actuales del Sistema

### ❌ NO SOPORTADO ACTUALMENTE:

El método `writeKey()` de NewPOS **solo acepta 3DES**, NO soporta AES:

```kotlin
// ❌ ESTO FALLA CON ERROR 2255 (UNSUPPORTED)
pedInstance.writeKey(
    KeySystem.MS_AES,  // ❌ No soportado
    keyType,
    masterKeyIndex,
    destKeyIndex,
    mode,
    keyData
)
```

### ✅ ALTERNATIVA: DUKPT con `writeDukptIPEK()`

NewPOS **sí soporta DUKPT AES** usando el método específico `writeDukptIPEK()`:

```kotlin
// ✅ MÉTODO CORRECTO PARA AES DUKPT (requiere implementación)
pedInstance.writeDukptIPEK(
    KeySystem.MS_AES,       // ✅ Soportado para DUKPT
    kbpkIndex,              // Índice de KBPK (KTK para DUKPT)
    ipekIndex,              // Índice donde guardar IPEK
    ksn,                    // 12 bytes
    ipekHeader,             // Header TR-31
    ipekData                // IPEK cifrada en formato TR-31
)
```

---

## 3. ¿Cómo Configurar un Perfil DUKPT AES? (Cuando se implemente)

### Paso 1: Preparar Llaves en el Almacén

Necesitas tener estas llaves inyectadas primero:

#### 1.1 KBPK (Key Block Protection Key)
- **Algoritmo**: AES-128, AES-192, o AES-256
- **Tipo**: TRANSPORT_KEY
- **Método de inyección**:
  - Plaintext (solo pruebas): EncryptionType "00"
  - Cifrada con KTK previa: EncryptionType "01"
- **Ejemplo**:
  ```
  Llave: KBPK_AES256_TEST
  Algoritmo: AES_256
  KCV: A1B2C3D4E5F6
  Slot: 00
  ```

#### 1.2 IPEK (Initial PIN Encryption Key)
- **Algoritmo**: Debe coincidir con el tipo DUKPT deseado (AES-128/192/256)
- **Formato**: Cifrada con KBPK en formato TR-31
- **Incluye**: KSN de 20 dígitos hexadecimales
- **Ejemplo**:
  ```
  Llave: IPEK_AES128_PROD
  Algoritmo: AES_128
  KCV: 7F8E9D0A1B2C
  KSN: F876543210000000000A (20 hex)
  Cifrada con: KBPK del slot 00
  ```

### Paso 2: Configurar el Perfil

```
Perfil: "DUKPT AES-128 Producción"
├── Descripción: "Perfil para inyección DUKPT AES-128 en producción"
├── Tipo de App: Retail
├── useKTK: false (KBPK se envía primero, no requiere KTK)
│
├── Configuración de Llaves:
│
│   1. KBPK (Llave de transporte para DUKPT)
│      ├── Tipo: "Master Session Key" (o "KBPK" cuando se agregue)
│      ├── Slot: "00" (hexadecimal)
│      ├── Llave seleccionada: [KBPK_AES256_TEST]
│      ├── EncryptionType: "00" (plaintext, solo pruebas)
│      └── NOTA: Esta llave se envía PRIMERO usando writeKey()
│
│   2. IPEK DUKPT AES-128
│      ├── Tipo: "DUKPT Initial Key (IPEK)"
│      ├── Slot: "01" (hexadecimal)
│      ├── Llave seleccionada: [IPEK_AES128_PROD]
│      ├── KSN: "F876543210000000000A" (20 dígitos hex)
│      ├── EncryptionType: "04" (NUEVO - DUKPT TR-31)
│      └── NOTA: Esta llave viene cifrada con KBPK del slot 00
```

### Paso 3: Orden de Inyección

1. **Primera inyección**: KBPK en slot 00
   - Usa `writeKey()` con EncryptionType "00" (plaintext) o "01" (cifrada)

2. **Segunda inyección**: IPEK en slot 01
   - Usa `writeDukptIPEK()` con IPEK cifrada + header TR-31 + KSN
   - El PED descifra internamente usando KBPK del slot 00

---

## 4. Tipos DUKPT Soportados por NewPOS

Según la documentación de NewPOS (DukptType.html):

```kotlin
enum class DukptType {
    DUKPT_TYPE_2TDEA,   // 0: 2TDEA (TDES 2 keys)
    DUKPT_TYPE_3TDEA,   // 1: 3TDEA (TDES 3 keys)
    DUKPT_TYPE_AES128,  // 2: AES-128 ✅ SOPORTADO
    DUKPT_TYPE_AES192,  // 3: AES-192 ✅ SOPORTADO
    DUKPT_TYPE_AES256   // 4: AES-256 ✅ SOPORTADO
}
```

---

## 5. Qué Falta Implementar

### 5.1 En el Protocolo Futurex
- Nuevo EncryptionType "04" para DUKPT TR-31
- Parseo de header TR-31
- Extracción de IPEK cifrada y KSN del comando

### 5.2 En IPedController
```kotlin
interface IPedController {
    /**
     * Escribe una IPEK DUKPT cifrada con KBPK en formato TR-31.
     * El PED usará la KBPK para descifrar la IPEK internamente.
     */
    @Throws(PedException::class)
    suspend fun writeDukptIPEK(
        kbpkIndex: Int,           // Índice de KBPK
        ipekIndex: Int,           // Índice donde guardar IPEK
        dukptType: DukptType,     // AES128/192/256
        ksn: ByteArray,           // 12 bytes
        ipekHeader: ByteArray,    // Header TR-31
        ipekData: ByteArray       // IPEK cifrada
    ): Boolean
}
```

### 5.3 En MainViewModel (KeyReceiver)
- Handler para EncryptionType "04"
- Validación de formato TR-31
- Llamada a `writeDukptIPEK()` en lugar de `writeKey()`

---

## 6. Comparación: writeKey() vs writeDukptIPEK()

| Característica | `writeKey()` | `writeDukptIPEK()` |
|----------------|--------------|---------------------|
| Soporta 3DES | ✅ Sí | ✅ Sí |
| Soporta AES | ❌ **NO** (Error 2255) | ✅ **SÍ** |
| Formato entrada | Llave cifrada con KTK | IPEK en TR-31 + KSN |
| Uso | Llaves estáticas | DUKPT (llaves dinámicas) |
| Seguridad | Requiere re-inyección | Una sola inyección |
| PCI-DSS moderno | Cumple | **Cumple mejor** |

---

## 7. Ejemplo de Comando Futurex con DUKPT (Futuro)

```
Comando hipotético cuando se implemente:

[Header]
02                           # STX
0255                         # Longitud total
04                           # EncryptionType: DUKPT TR-31
F876543210000000000A         # KSN (20 hex)
00                           # KBPK slot (2 hex)

[TR-31 Header]
D0112K0TB00E0000              # TR-31 header

[IPEK Cifrada]
ABCD1234...                  # IPEK cifrada con KBPK

[Footer]
03                           # ETX
```

---

## 8. Roadmap de Implementación

### Fase 1: Investigación ✅ COMPLETADA
- [x] Analizar documentación NewPOS
- [x] Confirmar soporte AES DUKPT
- [x] Identificar métodos disponibles

### Fase 2: Validación UI ✅ COMPLETADA
- [x] Filtro de llaves compatibles con KTK
- [x] Advertencias de compatibilidad
- [x] Cards contraídas por defecto

### Fase 3: Implementación DUKPT (Pendiente)
- [ ] Agregar soporte EncryptionType "04" en protocolo Futurex
- [ ] Implementar `writeDukptIPEK()` en NewposPedController
- [ ] Agregar handler en MainViewModel
- [ ] Testing con dispositivo real

### Fase 4: Producción (Pendiente)
- [ ] Documentación de uso
- [ ] Casos de prueba
- [ ] Certificación PCI-DSS

---

## 9. Preguntas Frecuentes

### ¿Por qué writeKey() no soporta AES?
Probablemente es una limitación del firmware NewPOS en este dispositivo específico. El método está diseñado para "master keys" tradicionales (3DES).

### ¿DUKPT es obligatorio para AES?
No es obligatorio, pero es la **única forma soportada** por NewPOS para inyectar llaves AES de manera segura en este dispositivo.

### ¿Puedo mezclar 3DES y AES en el mismo perfil?
No recomendado. Si tu KTK es 3DES, solo puedes inyectar llaves 3DES. Para AES necesitas DUKPT.

### ¿Qué es TR-31?
**TR-31** es un estándar de la industria para empaquetar llaves criptográficas de manera segura. Incluye:
- Header con metadatos de la llave
- Llave cifrada
- Checksum de integridad

---

## 10. Referencias

- **NewPOS PED API**: `asmart_api_doc/com/pos/device/ped/Ped.html`
- **DukptType Enum**: `asmart_api_doc/com/pos/device/ped/DukptType.html`
- **Estándar TR-31**: ASC X9 TR-31:2018
- **PCI-DSS**: Payment Card Industry Data Security Standard

---

## 11. Contacto y Soporte

Para preguntas sobre esta implementación:
- **Desarrollador**: Diego Herrera
- **Proyecto**: Android Injector (VIGATEC)
- **Versión documento**: 1.0
- **Fecha**: 2025-01-23
