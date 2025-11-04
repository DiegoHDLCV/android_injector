# Guía Rápida: Configuración de Perfiles de Inyección

## Configuración Actual (3DES con KTK)

### ✅ Escenario 1: Inyección 3DES con KTK 3DES (SOPORTADO)

**Pasos**:

1. **Preparar KTK en el almacén**:
   - Algoritmo: `DES_DOUBLE` o `DES_TRIPLE`
   - KCV: Por ejemplo `D4D9`
   - Marcar como KTK activa en el almacén

2. **Crear perfil**:
   ```
   Nombre: "Retail 3DES Producción"
   Tipo: Retail
   useKTK: ✅ Activado
   ```

3. **Agregar llaves al perfil**:
   ```
   Llave 1:
   - Tipo: Master Session Key
   - Slot: 01
   - Llave: [Seleccionar llave 3DES del almacén]

   Llave 2:
   - Tipo: PIN Encryption Key
   - Slot: 02
   - Llave: [Seleccionar llave 3DES del almacén]
   ```

4. **Resultado**:
   - ✅ Solo se mostrarán llaves 3DES compatibles
   - ✅ Advertencia: "KTK 3DES seleccionada: Solo se mostrarán llaves 3DES compatibles"
   - ✅ Inyección funcionará con `writeKey()` + EncryptionType "02"

---

### ❌ Escenario 2: Inyección AES con KTK AES (NO SOPORTADO)

**Pasos**:

1. **Preparar KTK en el almacén**:
   - Algoritmo: `AES_128`, `AES_192`, o `AES_256`
   - KCV: Por ejemplo `112A`
   - Marcar como KTK activa en el almacén

2. **Crear perfil**:
   ```
   Nombre: "Retail AES-256 Producción"
   Tipo: Retail
   useKTK: ✅ Activado
   ```

3. **Resultado**:
   - ❌ **ERROR**: "KTK AES no soportada: El método writeKey() solo acepta 3DES"
   - ❌ No se mostrarán llaves disponibles
   - ❌ Sugerencia: "Para inyectar llaves AES, necesitas usar DUKPT (requiere implementación futura)"

**Alternativa**: Esperar implementación de DUKPT (ver `DUKPT_GUIDE.md`)

---

## Mejoras de UX Implementadas

### 1. Cards de Llaves Contraídas por Defecto

**Antes**:
```
[Card expandida mostrando todos los campos]
  Tipo de llave: ____________
  Slot: __
  KSN: ____________________
  Llave seleccionada: ____________
  [Botones]
```

**Ahora**:
```
[Card contraída - más compacto]
  🔑 Configuración de llave    [▼][🗑️]
  Tipo: PIN Encryption Key • Slot: 02 • KCV: A1B2C3... (AES_128)

[Usuario hace clic en ▼ para expandir]
```

### 2. Validación de Compatibilidad KTK

**Antes**:
- Se mostraban TODAS las llaves disponibles
- Usuario podía seleccionar llaves AES con KTK 3DES
- Error ocurría en tiempo de inyección

**Ahora**:
- Solo se muestran llaves compatibles con KTK seleccionada
- Advertencia visual inmediata si hay incompatibilidad
- Prevención de errores antes de inyectar

### 3. Advertencias Visuales

#### Advertencia KTK 3DES (Warning):
```
⚠️ KTK 3DES seleccionada: Solo se mostrarán llaves 3DES compatibles con writeKey()
```
- Color: Amarillo (tertiaryContainer)
- Icono: Warning
- Aparece en sección de llaves del perfil

#### Error KTK AES (Error):
```
❌ KTK AES no soportada: El método writeKey() solo acepta 3DES.
   Para inyectar llaves AES, necesitas usar DUKPT (requiere implementación futura)
```
- Color: Rojo (errorContainer)
- Icono: Error
- Dropdown de llaves vacío

---

## Comparación de Métodos

| Método | Algoritmos | Estado | Uso Recomendado |
|--------|-----------|--------|-----------------|
| `writeKey()` | 3DES | ✅ Funcional | Producción (solo 3DES) |
| `writeDukptIPEK()` | 3DES, AES | ⏳ Pendiente | Futuro (AES + 3DES) |
| `injectKey()` | 3DES, AES | ✅ Funcional | Solo testing (plaintext) |

---

## Checklist de Configuración de Perfil

### Antes de Crear el Perfil:

- [ ] Verificar que tengo una KTK activa en el almacén
- [ ] Confirmar el algoritmo de la KTK (3DES o AES)
- [ ] Si KTK es AES → esperar implementación DUKPT
- [ ] Si KTK es 3DES → proceder normalmente

### Al Crear el Perfil:

- [ ] Nombre descriptivo (ej: "Retail 3DES Prod")
- [ ] Tipo de aplicación correcto
- [ ] useKTK activado si necesito cifrado
- [ ] Verificar advertencia de compatibilidad

### Al Agregar Llaves:

- [ ] Cards inician contraídas (expandir solo si necesito editar)
- [ ] Solo aparecen llaves compatibles con KTK
- [ ] Completar todos los campos requeridos:
  - Tipo de llave
  - Slot (2 dígitos hex)
  - Llave seleccionada (KCV)
  - KSN (solo para DUKPT - 20 dígitos hex)

### Antes de Inyectar:

- [ ] Todas las llaves tienen status "Configurado"
- [ ] Perfil muestra estado "Listo"
- [ ] Cable USB conectado
- [ ] Listener activo en MainScreen

---

## Ejemplos Prácticos

### Ejemplo 1: Perfil Retail Básico (3DES)

```
Nombre: "POS Vigatec Retail"
Descripción: "Perfil estándar para terminales de venta"
Tipo: Retail
useKTK: ✅ Sí
KTK Activa: KCV D4D9 (DES_TRIPLE)

Llaves:
1. Master Session Key - Slot 01 - KCV: ABC123 (DES_TRIPLE)
2. PIN Encryption Key - Slot 02 - KCV: DEF456 (DES_TRIPLE)
3. MAC Key - Slot 03 - KCV: GHI789 (DES_TRIPLE)

Estado: ✅ Listo para inyectar (3/3 configuradas)
```

### Ejemplo 2: Perfil H2H (3DES)

```
Nombre: "POS Bancolombia H2H"
Descripción: "Integración host-to-host Bancolombia"
Tipo: H2H
useKTK: ✅ Sí
KTK Activa: KCV D4D9 (DES_TRIPLE)

Llaves:
1. Data Encryption Key - Slot 05 - KCV: JKL012 (DES_TRIPLE)
2. MAC Key - Slot 06 - KCV: MNO345 (DES_TRIPLE)

Estado: ✅ Listo para inyectar (2/2 configuradas)
```

### Ejemplo 3: Perfil AES (NO SOPORTADO - Requiere DUKPT)

```
Nombre: "POS AES-256 Futuro"
Descripción: "Perfil para AES cuando se implemente DUKPT"
Tipo: Retail
useKTK: ✅ Sí
KTK Activa: KCV 112A (AES_256)

Error: ❌ KTK AES no soportada
Solución: Usar DUKPT cuando esté implementado
```

---

## Preguntas Frecuentes

### ¿Por qué mis llaves AES no aparecen en el dropdown?

Porque tienes una KTK 3DES seleccionada. El sistema solo muestra llaves compatibles con tu KTK.

**Solución**:
- Si necesitas inyectar llaves 3DES → perfecto, continúa
- Si necesitas inyectar llaves AES → espera implementación DUKPT

### ¿Puedo desactivar useKTK y enviar llaves en plaintext?

Sí, pero **NO recomendado para producción**. Solo para testing.

**Pasos**:
1. Desactivar toggle "Usar cifrado KTK"
2. Las llaves se enviarán con EncryptionType "00" (plaintext)
3. ⚠️ NO cumple con PCI-DSS

### ¿Por qué las cards de llaves están colapsadas?

Mejora de usabilidad. Cuando tienes múltiples llaves, las cards contraídas:
- Ahorran espacio vertical
- Muestran información clave (tipo/slot/KCV/algoritmo)
- Permiten enfoque en una llave a la vez

**Para expandir**: Clic en el botón ▼

### ¿Qué significa "Slot (HEX)"?

El slot es la posición de memoria en el PED donde se guardará la llave.

**Formato**:
- 2 dígitos hexadecimales: `00` a `FF`
- Ejemplos válidos: `00`, `01`, `0A`, `10`, `FF`
- Ejemplos inválidos: `0`, `100`, `XY`

---

## Troubleshooting

### Problema: "No hay llaves disponibles en el dropdown"

**Causas posibles**:
1. KTK es AES (no soportada)
2. No hay llaves compatibles en el almacén
3. Todas las llaves son de algoritmo diferente a la KTK

**Soluciones**:
1. Verificar algoritmo de KTK activa
2. Inyectar llaves compatibles en el almacén primero
3. Si necesitas AES, esperar implementación DUKPT

### Problema: "Perfil muestra estado 'Pendiente'"

**Causa**: No todas las llaves están configuradas

**Solución**:
1. Expandir cada card de llave
2. Verificar que todos los campos estén llenos:
   - Tipo de llave ✓
   - Slot ✓
   - Llave seleccionada ✓
   - KSN (solo DUKPT) ✓

### Problema: Error 2255 al inyectar

**Causa**: Intentando inyectar llave AES con `writeKey()`

**Solución**:
- Usar solo llaves 3DES
- O esperar implementación DUKPT para AES

---

## Versión

- **Documento**: v1.0
- **Fecha**: 2025-01-23
- **Autor**: Diego Herrera (VIGATEC)
