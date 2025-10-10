# Propuesta de Mejora UX/UI - Sistema KEK

## Problema Actual
- KEK Manager es una pantalla separada difícil de entender
- No está claro cuándo y cómo usar la KEK
- Falta integración con el flujo de trabajo natural
- Usuario tiene que ir a múltiples pantallas para configurar KEK

## Solución Propuesta

### 1. **Ceremonia de Llaves - Agregar Opción KEK**

**Pantalla**: `CeremonyScreen` - Paso de Finalización

```
┌─────────────────────────────────────────────────────────┐
│  ✅ ¡Ceremonia Completada!                              │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  🔑 Llave Generada Exitosamente                         │
│  KCV: CBB14C                                            │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │ Tipo de llave:                                  │    │
│  │                                                  │    │
│  │ ⚪ Llave Operacional (Normal)                   │    │
│  │ ⚪ Llave de Cifrado KEK                         │    │
│  │                                                  │    │
│  │ ℹ️  Las llaves KEK se usan para cifrar         │    │
│  │    todas las demás llaves antes de enviarlas.  │    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
│  Nombre de la llave (opcional):                         │
│  ┌────────────────────────────────────────────────┐    │
│  │ [KEK Principal Master                          ]│    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
│  [Guardar Llave]  [Nueva Ceremonia]                    │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

**Cambios**:
- Radio buttons para elegir: Operacional / KEK
- Campo de nombre opcional
- Guardar con tipo correspondiente en BD

---

### 2. **Pantalla de Perfiles - Selector de KEK**

**Pantalla**: `ProfilesScreen` - Creación/Edición de Perfil

```
┌─────────────────────────────────────────────────────────┐
│  Crear Perfil de Inyección                              │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Nombre del Perfil:                                     │
│  ┌────────────────────────────────────────────────┐    │
│  │ [Perfil POS Principal                          ]│    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
│  Descripción:                                           │
│  ┌────────────────────────────────────────────────┐    │
│  │ [Perfil para terminales POS con cifrado       ]│    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
│  ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓    │
│  ┃ 🔐 CONFIGURACIÓN DE CIFRADO                   ┃    │
│  ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛    │
│                                                          │
│  [ ✓ ] Usar cifrado KEK                                │
│        (Cifra todas las llaves antes de enviarlas)      │
│                                                          │
│  Seleccionar KEK a usar:                                │
│  ┌────────────────────────────────────────────────┐    │
│  │ 🔑 KEK Principal Master (KCV: CBB14C)   [●]   │    │
│  │    └─ Estado: ACTIVA | Creada: 10/10/2025     │    │
│  │                                                 │    │
│  │ 🔑 KEK Backup (KCV: A3F28D)             [ ]   │    │
│  │    └─ Estado: EXPORTADA | Creada: 09/10/2025  │    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
│  ⚠️  La KEK se exportará automáticamente al SubPOS     │
│     la primera vez que inyectes este perfil.           │
│                                                          │
│  ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓    │
│  ┃ 🗝️  CONFIGURACIÓN DE LLAVES                   ┃    │
│  ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛    │
│                                                          │
│  1. PIN Key (Slot 01)                                   │
│     Llave: CBB14C | Tipo: 3DES                         │
│     [Editar] [Eliminar]                                 │
│                                                          │
│  2. MAC Key (Slot 02)                                   │
│     Llave: D4A9B2 | Tipo: 3DES                         │
│     [Editar] [Eliminar]                                 │
│                                                          │
│  [+ Agregar Llave]                                      │
│                                                          │
│  [Guardar Perfil]  [Cancelar]                          │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

**Cambios**:
- Sección dedicada "CONFIGURACIÓN DE CIFRADO"
- Toggle "Usar cifrado KEK"
- Radio buttons para seleccionar KEK disponible
- Muestra estado y fecha de cada KEK
- Warning sobre exportación automática

---

### 3. **Listado de Llaves - Indicadores Visuales**

**Pantalla**: `KeyVaultScreen` - Lista de Llaves

```
┌─────────────────────────────────────────────────────────┐
│  Bóveda de Llaves                         [+ Nueva]     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  🔍 [Buscar por KCV o nombre...              ]          │
│                                                          │
│  Filtros: [Todas ▼] [Tipo ▼] [Estado ▼]               │
│                                                          │
│  ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓    │
│  ┃ 🔐 KEK Principal Master              [ACTIVA] ┃    │
│  ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛    │
│  │ KCV: CBB14C | 3DES | 16 bytes                 │    │
│  │ Creada: 10/10/2025 14:30                      │    │
│  │ 📦 Usada en: 2 perfiles                       │    │
│  │ [Ver Datos] [Desactivar] [●●●]                │    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │ 🔑 PIN Key Principal                          │    │
│  ├────────────────────────────────────────────────┤    │
│  │ KCV: D4A9B2 | 3DES | 16 bytes                 │    │
│  │ Creada: 10/10/2025 14:25                      │    │
│  │ Usada en: PIN Key (Perfil POS Principal)      │    │
│  │ [Ver Datos] [Editar] [Eliminar]               │    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │ 🔑 MAC Key Principal                          │    │
│  ├────────────────────────────────────────────────┤    │
│  │ KCV: A3F28D | 3DES | 16 bytes                 │    │
│  │ Creada: 10/10/2025 14:25                      │    │
│  │ Usada en: MAC Key (Perfil POS Principal)      │    │
│  │ [Ver Datos] [Editar] [Eliminar]               │    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

**Cambios**:
- Las KEK se muestran con borde dorado y icono 🔐
- Badge con estado: ACTIVA, EXPORTADA, INACTIVA
- Contador de perfiles que la usan
- Filtros: Todas / Solo KEK / Solo Operacionales

---

### 4. **Flujo de Inyección con KEK**

**Cuando el usuario inyecta un perfil con KEK activada:**

```
1. Usuario selecciona perfil "POS Principal"
   ↓
2. Sistema detecta que tiene KEK activada
   ↓
3. ¿KEK ya exportada?
   ├─ SÍ → Continuar con inyección de llaves
   └─ NO → Modal de confirmación:

   ┌─────────────────────────────────────────────────┐
   │ 🔐 Exportar KEK al SubPOS                      │
   ├─────────────────────────────────────────────────┤
   │                                                  │
   │ El perfil seleccionado usa cifrado KEK.        │
   │                                                  │
   │ ⚠️  IMPORTANTE:                                 │
   │ La KEK se enviará EN CLARO una sola vez        │
   │ al SubPOS. Asegúrate de que:                   │
   │                                                  │
   │ ✓ El SubPOS correcto está conectado           │
   │ ✓ La conexión USB es segura                   │
   │ ✓ El área es privada                          │
   │                                                  │
   │ KEK a exportar:                                 │
   │ 🔑 KEK Principal Master                        │
   │    KCV: CBB14C | Slot: 00                      │
   │                                                  │
   │ [Cancelar]  [Exportar y Continuar]             │
   │                                                  │
   └─────────────────────────────────────────────────┘

   ↓ (Usuario confirma)

4. Sistema exporta KEK al SubPOS
   └─ Envía comando Futurex con KEK en claro
   └─ SubPOS confirma recepción
   └─ Sistema marca KEK como "EXPORTADA"
   ↓
5. Sistema inyecta llaves operacionales
   └─ Cada llave se cifra con KEK antes de enviar
   └─ SubPOS descifra con KEK y valida KCV
   ↓
6. ✓ Inyección completada
```

---

## Cambios en Base de Datos

### InjectedKeyEntity
```kotlin
// Agregar campo para diferenciar KEK de llaves operacionales
data class InjectedKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keySlot: Int,
    val keyType: String, // "KEK", "PINKEY", "MACKEY", etc.
    val keyAlgorithm: String,
    val kcv: String,
    val keyData: String,
    val status: String, // "ACTIVE", "EXPORTED", "INACTIVE"
    val injectionTimestamp: Long,
    val customName: String = "", // 🆕 Nombre personalizado
    val isKEK: Boolean = false    // 🆕 Flag para identificar KEK rápidamente
)
```

### ProfileEntity
```kotlin
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val applicationType: String,
    val keyConfigurations: List<KeyConfiguration>,
    val useKEK: Boolean = false,          // 🆕 Activar cifrado KEK
    val selectedKEKKcv: String = ""       // 🆕 KCV de la KEK seleccionada
)
```

---

## Ventajas de esta Propuesta

### 1. **Flujo Natural**
- El usuario genera llaves en Ceremonia
- Elige si es KEK o Operacional en el mismo flujo
- No necesita ir a otra pantalla

### 2. **Configuración Simple**
- Toggle ON/OFF para KEK en cada perfil
- Selección visual de qué KEK usar
- Ver estado de KEK directamente

### 3. **Exportación Automática**
- Sistema detecta si KEK ya fue exportada
- Muestra warning y pide confirmación solo si es necesario
- Evita errores de usuario

### 4. **Trazabilidad**
- Cada KEK muestra en qué perfiles se usa
- Estado visible: ACTIVA / EXPORTADA / INACTIVA
- Log completo de operaciones

### 5. **Seguridad**
- Warning claro al exportar KEK
- Confirmación obligatoria
- Marca automática de estado EXPORTADA

---

## Plan de Implementación

1. ✅ Crear KEKManager (singleton) - COMPLETADO
2. ⏳ Modificar CeremonyScreen - Agregar selector KEK/Operacional
3. ⏳ Actualizar ProfileEntity - Agregar campos useKEK y selectedKEKKcv
4. ⏳ Modificar ProfilesScreen - Agregar sección de configuración KEK
5. ⏳ Actualizar KeyVaultScreen - Agregar indicadores visuales
6. ⏳ Modificar KeyInjectionViewModel - Exportar KEK automáticamente
7. ⏳ Eliminar KEKManagerScreen - Integrado en Ceremonia
8. ⏳ Actualizar navegación - Remover ruta KEKManager

---

## Mockups de Flujo Completo

### Escenario: Primera Inyección con KEK

```
Usuario                         Sistema
   │                               │
   │ 1. Generar KEK en Ceremonia  │
   ├──────────────────────────────>│
   │                               │ Guardar como KEK
   │ 2. Crear Perfil              │
   ├──────────────────────────────>│
   │   ✓ Activar KEK              │
   │   ✓ Seleccionar KEK CBB14C   │
   │                               │ Guardar perfil
   │ 3. Conectar SubPOS           │
   ├──────────────────────────────>│
   │                               │ Detectar USB
   │ 4. Inyectar Perfil           │
   ├──────────────────────────────>│
   │                               │ ¿KEK exportada? NO
   │                               │ Mostrar modal
   │<──────────────────────────────┤
   │ 5. Confirmar exportación     │
   ├──────────────────────────────>│
   │                               │ Exportar KEK (claro)
   │                               │ Marcar como EXPORTADA
   │                               │ Cifrar PIN Key
   │                               │ Enviar cifrada (tipo 01)
   │                               │ Cifrar MAC Key
   │                               │ Enviar cifrada (tipo 01)
   │<──────────────────────────────┤
   │ 6. ✓ Inyección completada    │
   │                               │
```

### Escenario: Segunda Inyección (KEK ya exportada)

```
Usuario                         Sistema
   │                               │
   │ 1. Inyectar Perfil           │
   ├──────────────────────────────>│
   │                               │ ¿KEK exportada? SÍ
   │                               │ Cifrar llaves con KEK
   │                               │ Enviar cifradas (tipo 01)
   │<──────────────────────────────┤
   │ 2. ✓ Inyección completada    │
   │    (Sin modal de KEK)         │
   │                               │
```

---

## Consideraciones de Seguridad

1. **Exportación de KEK**
   - Solo se exporta en claro UNA vez
   - Requiere confirmación explícita del usuario
   - Se marca inmediatamente como EXPORTADA

2. **Almacenamiento**
   - KEK guardada en Android Keystore (cifrado hardware)
   - También en BD para persistencia
   - Acceso solo por KEKManager

3. **Trazabilidad**
   - Log completo de cuándo se exportó
   - Registro de qué perfiles la usan
   - Auditoría de cambios de estado

4. **Rotación**
   - Si se genera nueva KEK, la anterior pasa a INACTIVE
   - Los perfiles deben actualizarse manualmente
   - Warning si hay perfiles usando KEK inactiva

---

## Conclusión

Esta propuesta simplifica significativamente la UX del sistema KEK:
- ✅ Flujo natural e intuitivo
- ✅ Menos clics y pantallas
- ✅ Configuración clara y visible
- ✅ Exportación automática con confirmación
- ✅ Indicadores visuales en toda la app
- ✅ Trazabilidad y seguridad mejoradas

El usuario solo necesita:
1. Generar KEK en ceremonia (marcar como KEK)
2. Activar KEK en perfil
3. Inyectar perfil (sistema hace el resto)
