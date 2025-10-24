# Implementación de Soporte DUKPT con KSN

## Resumen de Cambios

Se ha implementado soporte completo para llaves DUKPT con la capacidad de especificar el KSN (Key Serial Number) manualmente.

## Archivos Modificados

### 1. `persistence/src/main/java/com/example/persistence/entities/ProfileEntity.kt`
- ✅ Agregado campo `ksn: String = ""` a `KeyConfiguration`
- ✅ Permite almacenar el KSN de 20 caracteres para llaves DUKPT

### 2. `injector/src/main/java/com/vigatec/injector/viewmodel/ProfileViewModel.kt`
- ✅ Actualizado `addKeyConfiguration()` para inicializar campo KSN
- ✅ Actualizado `updateKeyConfiguration()` para manejar campo "ksn"

### 3. `injector/src/main/java/com/vigatec/injector/ui/screens/ProfilesScreen.kt`
- ✅ Expandido `keyTypeOptions` para incluir: `["TDES", "AES", "DUKPT_TDES", "DUKPT_AES", "PIN", "MAC", "DATA"]`
- ✅ Agregado campo KSN condicional que aparece solo para tipos DUKPT
- ✅ Validación automática de 20 caracteres hexadecimales
- ✅ Implementado en ambos layouts (2 columnas y 1 columna)

### 4. `injector/src/main/java/com/vigatec/injector/viewmodel/KeyInjectionViewModel.kt`
- ✅ Actualizado `mapKeyTypeToFuturex()` para nuevos tipos:
  - `DUKPT_TDES` → "08" (DUKPT 3DES BDK)
  - `DUKPT_AES` → "10" (DUKPT AES BDK)
  - `AES` → "01" (Master Session Key)
- ✅ Modificado manejo de KSN para usar el del perfil cuando esté disponible
- ✅ Agregada validación de KSN en `validateKeyIntegrity()`
- ✅ Actualizada función `getKeyTypeDescription()`
- ✅ Agregada función `isDukptKeyTypeFromConfig()`

### 5. `INYECCION_LLAVES_PERFIL.md`
- ✅ Actualizada tabla de tipos de llave con nuevos tipos DUKPT
- ✅ Agregada sección sobre manejo del KSN
- ✅ Documentación de validaciones y formato requerido

## Nuevas Funcionalidades

### 🔑 Tipos de Llave Expandidos
| Tipo UI | Código Futurex | Descripción | KSN |
|---------|----------------|-------------|-----|
| TDES | "01" | Master Session Key | ❌ |
| AES | "01" | Master Session Key | ❌ |
| **DUKPT_TDES** | "08" | DUKPT 3DES BDK Key | ✅ |
| **DUKPT_AES** | "10" | DUKPT AES BDK Key | ✅ |
| PIN | "05" | PIN Encryption Key | ❌ |
| MAC | "04" | MAC Key | ❌ |
| DATA | "0C" | Data Encryption Key | ❌ |

### 📱 Interfaz de Usuario
- **Campo KSN dinámico**: Aparece automáticamente cuando se selecciona tipo DUKPT
- **Validación en tiempo real**: 
  - Solo caracteres hexadecimales (0-9, A-F)
  - Exactamente 20 caracteres
  - Indicador visual de estado (válido/inválido)
- **Placeholder sugestivo**: `F876543210000000000A`

### 🛠️ Lógica de Inyección
- **Prioridad de KSN**:
  1. Si se especifica KSN válido en perfil → usar ese KSN
  2. Si KSN inválido/vacío → generar automáticamente basado en KCV + Slot
  3. Si no es DUKPT → usar `"00000000000000000000"`

### 🔍 Validaciones
- **KSN Format**: 20 caracteres hexadecimales exactos
- **Tipos DUKPT**: Validación automática de KSN requerido
- **Longitudes de llave**: Específicas por algoritmo (TDES/AES)

## Ejemplo de Uso

### Configuración de Perfil DUKPT
```
Perfil: "Terminal DUKPT - Banco ABC"
├── Llave 1: PIN (Tipo: DUKPT_TDES, Slot: 01, KSN: F876543210000000000A)
├── Llave 2: MAC (Tipo: DUKPT_AES, Slot: 02, KSN: A123456789BCDEF00123)
└── Llave 3: DATA (Tipo: DATA, Slot: 03, KSN: N/A)
```

### Comando Enviado para DUKPT_TDES
```
<STX>020101000800A1B20000F876543210000000000A010[KEY_DATA]<ETX><LRC>
│                   │     │
│                   │     └─ KSN especificado en perfil
│                   └─────── Código "08" = DUKPT 3DES BDK
└─────────────────────────── Comando "02" = Inyección simétrica
```

## Beneficios

1. ✅ **Control total del KSN**: El usuario puede especificar KSNs exactos
2. ✅ **Soporte completo DUKPT**: Tanto TDES como AES
3. ✅ **Compatibilidad hacia atrás**: Tipos existentes siguen funcionando
4. ✅ **Validación robusta**: Previene errores de formato
5. ✅ **Interfaz intuitiva**: Campo KSN aparece solo cuando es necesario
6. ✅ **Fallback automático**: Genera KSN si no se especifica

## Testing

Para probar la implementación:

1. **Crear perfil DUKPT**:
   - Tipo: `DUKPT_TDES` o `DUKPT_AES`
   - KSN: `F876543210000000000A`
   - Verificar que aparece campo KSN

2. **Validar inyección**:
   - Confirmar que se usa KSN especificado
   - Verificar logs de comando Futurex generado

3. **Probar validaciones**:
   - KSN con menos de 20 caracteres → Error
   - KSN con caracteres inválidos → Error
   - KSN vacío en DUKPT → Generación automática

## Notas Técnicas

- **Compatibilidad**: Los perfiles existentes siguen funcionando (campo `ksn` tiene valor por defecto)
- **Persistencia**: El KSN se almacena en base de datos JSON dentro de `keyConfigurations`
- **Validación**: Se valida tanto en UI como en lógica de inyección
- **Logs**: Se registra si se usa KSN del perfil o generado automáticamente
