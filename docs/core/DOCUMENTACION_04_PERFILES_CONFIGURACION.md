# Documentación del Sistema de Inyección de Llaves Criptográficas

## Parte 4: Perfiles y Configuración

### Versión: 1.0
### Fecha: Octubre 2025

---

## 1. CONCEPTO DE PERFILES

### 1.1 ¿Qué es un Perfil?

Un **Perfil** es una configuración predefinida que agrupa múltiples llaves criptográficas destinadas a un propósito específico. Funciona como una "receta" o plantilla que define:

- **Qué llaves inyectar**: Identificadas por su KCV
- **Dónde inyectarlas**: Slots específicos en el PED
- **Cómo inyectarlas**: Tipo de llave, algoritmo, método
- **Configuración de seguridad**: Uso de KEK, cifrado

### 1.2 Propósito de los Perfiles

**Ventajas**:

1. **Estandarización**: Misma configuración para todos los terminales de un tipo
2. **Rapidez**: Inyección con un solo clic
3. **Reducción de Errores**: Configuración pre-validada
4. **Trazabilidad**: Registro de qué perfil se usó
5. **Mantenimiento**: Fácil actualización de configuraciones

**Casos de Uso Típicos**:
- Terminal de tienda física (PIN + MAC + Data)
- Terminal E-Commerce (DUKPT)
- ATM (PIN + DUKPT + RSA)
- POS móvil (Llaves básicas)

### 1.3 Estructura de un Perfil

```kotlin
data class ProfileEntity(
    val id: Long = 0,                    // ID único
    val name: String,                    // Nombre descriptivo
    val description: String,             // Descripción detallada
    val applicationType: String,         // Tipo de aplicación
    val keyConfigurations: List<KeyConfiguration>, // Llaves
    val useKEK: Boolean = false,         // Usar cifrado KEK
    val selectedKEKKcv: String = ""      // KEK a usar (por KCV)
)
```

**Configuración de Llave**:
```kotlin
data class KeyConfiguration(
    val id: Long,                        // ID único
    val usage: String,                   // Descripción de uso
    val keyType: String,                 // Tipo de llave
    val slot: String,                    // Slot en PED
    val selectedKey: String,             // KCV de llave
    val injectionMethod: String,         // Método de inyección
    val ksn: String = ""                 // KSN (solo DUKPT)
)
```

---

## 2. GESTIÓN DE PERFILES

### 2.1 Creación de Perfiles

#### 2.1.1 Información Básica

**Campos Requeridos**:

1. **Nombre**:
   - Identificador único del perfil
   - Descriptivo y claro
   - Ejemplo: "Terminal Tienda - Básico"

2. **Descripción**:
   - Detalle del propósito
   - Ejemplo: "Perfil para terminales de tiendas físicas con llaves básicas PIN, MAC y Data"

3. **Tipo de Aplicación**:
   - Categorización del uso
   - Opciones típicas:
     - Transaccional
     - E-Commerce
     - ATM
     - POS Móvil
     - Desarrollo/Testing

#### 2.1.2 Configuración de Llaves

**Proceso**:

1. **Agregar Nueva Configuración**:
   - Botón "+ Agregar Llave"
   - Se crea configuración vacía

2. **Definir Uso**:
   - Campo de texto libre
   - Ejemplos:
     - "PIN Entry"
     - "MAC Calculation"
     - "Track Data Encryption"
     - "DUKPT Initial Key"

3. **Seleccionar Tipo**:
   - Dropdown con opciones:
     - TDES
     - AES
     - DUKPT_TDES
     - DUKPT_AES
     - PIN
     - MAC
     - DATA

4. **Asignar Slot**:
   - Campo numérico (0-99)
   - Validación de unicidad en perfil
   - Sugerencias según tipo:
     - PIN: 10-29
     - MAC: 30-49
     - Data: 50-69
     - DUKPT: 70-89

5. **Seleccionar Llave**:
   - Dropdown con llaves disponibles
   - Mostradas por KCV
   - Filtradas por tipo compatible (opcional)

6. **Configurar KSN** (solo DUKPT):
   - Campo de texto hexadecimal
   - Exactamente 20 caracteres
   - Validación en tiempo real
   - Generación automática si vacío

**Ejemplo de Configuración**:
```
Uso:               PIN Entry
Tipo:              PIN
Slot:              15
Llave Seleccionada: AABB12 (PIN Key - 3DES)
KSN:               (no aplica)
```

#### 2.1.3 Configuración de KEK

**Activación**:
- Toggle "Usar Cifrado KEK"
- Al activar:
  - Sistema muestra automáticamente la KEK activa actual
  - No hay selector manual (solo una KEK activa a la vez)

**Información de KEK Mostrada**:
- KCV de la KEK activa
- Estado actual (ACTIVE/EXPORTED)
- Nombre personalizado (si tiene)
- Algoritmo (debe ser AES-256)

**Recomendaciones**:
- Asegurar que existe una KEK activa antes de crear perfiles
- Si no hay KEK activa, ir a "Llaves Inyectadas" para seleccionar una
- Solo llaves AES-256 pueden ser KEK

### 2.2 Edición de Perfiles

**Características**:

1. **Edición de Información Básica**:
   - Modificar nombre
   - Actualizar descripción
   - Cambiar tipo de aplicación

2. **Gestión de Configuraciones**:
   - Agregar nuevas configuraciones
   - Editar configuraciones existentes
   - Eliminar configuraciones
   - Reordenar (opcional)

3. **Modificación de KEK**:
   - Activar/desactivar uso de KEK
   - El sistema usa automáticamente la KEK activa actual
   - Warning si no hay KEK activa disponible

**Validaciones**:
- No permitir slots duplicados
- Validar que llaves seleccionadas existan
- Verificar KSN para llaves DUKPT
- Comprobar compatibilidad de tipos

### 2.3 Eliminación de Perfiles

**Proceso**:
1. Usuario solicita eliminar perfil
2. Modal de confirmación:
   ```
   ⚠️ ¿Eliminar perfil "Terminal Tienda - Básico"?
   
   Esta acción no se puede deshacer.
   Las llaves asociadas NO se eliminarán, solo la configuración.
   
   [Cancelar] [Eliminar Perfil]
   ```
3. Si confirma:
   - Eliminar de base de datos
   - Actualizar UI
   - Mensaje de éxito

**Consideraciones**:
- Las llaves NO se eliminan (solo la configuración del perfil)
- Historial de inyecciones se mantiene
- Operación permanente

---

## 3. TIPOS DE CONFIGURACIÓN

### 3.1 Perfil Básico (Sin KEK)

**Características**:
- Llaves enviadas en claro
- No requiere KEK previa
- Más simple y rápido
- **Solo para entornos seguros o desarrollo**

**Ejemplo**:
```
Nombre: "Desarrollo - Básico"
Descripción: "Perfil de desarrollo sin cifrado"
Tipo: Desarrollo/Testing
Usar KEK: No

Configuraciones:
1. PIN Entry
   - Tipo: PIN
   - Slot: 10
   - Llave: AABB12
   
2. MAC Calculation
   - Tipo: MAC
   - Slot: 30
   - Llave: CCDD34
```

**Comando Futurex Generado**:
```
Encryption Type: "00" (claro)
KTK Slot:        "00"
KTK Checksum:    "0000"
Key Data:        [datos en claro]
```

### 3.2 Perfil con KEK

**Características**:
- Llaves cifradas con KEK
- Mayor seguridad
- Requiere KEK pre-exportada o se exporta automáticamente
- **Recomendado para producción**

**Ejemplo**:
```
Nombre: "Producción - Seguro"
Descripción: "Perfil para terminales de producción con cifrado KEK"
Tipo: Transaccional
Usar KEK: Sí
KEK Seleccionada: A1B2C3 (KEK Master - AES-256) [ACTIVE]

Configuraciones:
1. PIN Entry
   - Tipo: PIN
   - Slot: 15
   - Llave: AABB12
   
2. MAC Calculation
   - Tipo: MAC
   - Slot: 35
   - Llave: CCDD34
   
3. Data Encryption
   - Tipo: DATA
   - Slot: 55
   - Llave: EEFF56
```

**Comando Futurex Generado**:
```
Encryption Type: "01" (cifrado)
KTK Slot:        "05" (slot de KEK)
KTK Checksum:    "A1B2" (KCV de KEK)
Key Data:        [datos cifrados con KEK]
```

### 3.3 Perfil DUKPT

**Características**:
- Incluye llaves DUKPT (BDK o IPEK)
- Requiere KSN de 20 caracteres
- Puede combinar DUKPT con llaves estáticas
- Opcional: KEK para cifrar DUKPT

**Ejemplo**:
```
Nombre: "E-Commerce DUKPT"
Descripción: "Terminal para transacciones online con DUKPT"
Tipo: E-Commerce
Usar KEK: Sí
KEK Seleccionada: A1B2C3

Configuraciones:
1. DUKPT PIN BDK
   - Tipo: DUKPT_TDES
   - Slot: 70
   - Llave: 112233
   - KSN: F876543210000000000A
   
2. DUKPT AES BDK
   - Tipo: DUKPT_AES
   - Slot: 71
   - Llave: 445566
   - KSN: F876543210000000000B
   
3. MAC Static
   - Tipo: MAC
   - Slot: 30
   - Llave: CCDD34
   - KSN: (no aplica)
```

**Comando Futurex para DUKPT**:
```
Key Type:        "08" (DUKPT 3DES BDK)
KSN:             "F876543210000000000A"
Encryption Type: "01" (cifrado con KEK)
KTK Slot:        "05"
Key Data:        [DUKPT BDK cifrada]
```

### 3.4 Perfil Mixto (Varios Algoritmos)

**Características**:
- Combina llaves de diferentes algoritmos
- 3DES y AES en mismo perfil
- Útil para migración gradual

**Ejemplo**:
```
Nombre: "Migración 3DES → AES"
Descripción: "Terminal en transición de 3DES a AES"
Tipo: Transaccional

Configuraciones:
1. PIN Entry (Legacy)
   - Tipo: PIN
   - Slot: 10
   - Llave: AABB12 (3DES)
   
2. PIN Entry (New)
   - Tipo: AES
   - Slot: 11
   - Llave: 778899 (AES-256)
   
3. MAC (Legacy)
   - Tipo: MAC
   - Slot: 30
   - Llave: CCDD34 (3DES)
   
4. MAC (New)
   - Tipo: AES
   - Slot: 31
   - Llave: AABBCC (AES-256)
```

---

## 4. FLUJO DE INYECCIÓN DESDE PERFIL

### 4.1 Preparación

**Paso 1: Verificación de Llaves**:
```
Para cada configuración en perfil:
  ├─ Buscar llave por KCV en BD
  ├─ Si no existe → Error: "Llave X no encontrada"
  ├─ Si existe:
  │   ├─ Validar que tenga datos (keyData no vacío)
  │   ├─ Validar formato hexadecimal
  │   ├─ Validar longitud según tipo
  │   └─ Validar KSN si es DUKPT
  └─ Continuar
```

**Paso 2: Verificación de KEK** (si useKEK = true):
```
├─ Obtener KEK por KCV
├─ Si no existe → Error: "KEK no encontrada"
├─ Si estado = INACTIVE → Error: "KEK inactiva"
├─ Si estado = ACTIVE:
│   ├─ Mostrar warning: "KEK debe exportarse primero"
│   ├─ Modal de confirmación
│   └─ Si acepta → Marcar para exportación
├─ Si estado = EXPORTED:
│   └─ Continuar (KEK ya está en SubPOS)
└─ OK
```

### 4.2 Proceso de Inyección

**Secuencia Completa**:

```
[1. DETENER POLLING]
  └─ PollingService.stopPolling()
  └─ Liberar puerto serial
      ↓
[2. INICIALIZAR COMUNICACIÓN]
  ├─ CommunicationSDKManager.getComController()
  ├─ comController.init(BPS_115200, NOPAR, DB_8)
  └─ comController.open()
      ↓
[3. EXPORTAR KEK] (si useKEK && KEK.estado == ACTIVE)
  ├─ Construir comando especial KEK
  │   └─ Encryption Type: "00" (KEK en claro)
  ├─ Enviar KEK
  ├─ Esperar confirmación
  ├─ Actualizar KEK: estado = EXPORTED
  └─ Log: "KEK exportada exitosamente"
      ↓
[4. INYECTAR LLAVES] (secuencial)
  Para cada configuración (índice i de N):
      ↓
  [4.1 Obtener Llave]
    └─ InjectedKeyRepository.getKeyByKcv(config.selectedKey)
      ↓
  [4.2 Validar Integridad]
    └─ validateKeyIntegrity(key)
      ↓
  [4.3 Cifrar (si useKEK)]
    ├─ TripleDESCrypto.encryptKeyForTransmission(
    │     keyData, kekData, kcv
    │   )
    └─ Log: "Llave cifrada con KEK"
      ↓
  [4.4 Construir Comando Futurex]
    ├─ Mapear tipo: config.keyType → código Futurex
    ├─ Slot: config.slot
    ├─ KSN: config.ksn (si DUKPT)
    ├─ Longitud: formato ASCII HEX 3 dígitos
    ├─ Encryption Type: "00" o "01"
    └─ FuturexMessageFormatter.format("02", fields)
      ↓
  [4.5 Enviar Comando]
    ├─ comController.write(command, 1000ms)
    └─ Log: "TX: [bytes]"
      ↓
  [4.6 Esperar Respuesta]
    ├─ comController.readData(1024, buffer, 10000ms)
    ├─ FuturexMessageParser.nextMessage()
    └─ Log: "RX: [bytes]"
      ↓
  [4.7 Validar Respuesta]
    ├─ Si responseCode != "00":
    │   └─ Error: "Código de error Futurex: XX"
    ├─ Si responseKcv != expectedKcv:
    │   └─ Error: "KCV no coincide"
    └─ OK
      ↓
  [4.8 Actualizar Progreso]
    ├─ Progreso: (i+1)/N * 100%
    ├─ Log: "Llave i+1/N inyectada exitosamente"
    └─ Pausa: delay(500ms)
      ↓
  (Repetir para siguiente configuración)
      ↓
[5. FINALIZAR]
  ├─ comController.close()
  ├─ PollingService.restartPolling()
  ├─ Estado: SUCCESS
  └─ Log: "¡Inyección completada exitosamente!"
```

### 4.3 Manejo de Errores Durante Inyección

**Error en Llave 2 de 5**:
```
[Llave 1] ✓ Exitosa
[Llave 2] ✗ Error: "KCV no coincide"
           └─ Detener inyección
           └─ Estado: ERROR
           └─ Mensaje: "Error en llave 2/5: KCV no coincide"
           └─ Cerrar comunicación
           └─ Reiniciar polling
[Llaves 3-5] No procesadas
```

**Acciones Post-Error**:
1. Mostrar llave problemática
2. Indicar configuración específica
3. Sugerir revisión de llave en BD
4. Permitir reintentar inyección

**Timeout en Comunicación**:
```
[Esperando respuesta...]
  └─ Timeout 10s alcanzado
  └─ Error: "Timeout esperando respuesta"
  └─ Posibles causas:
      ├─ Cable USB desconectado
      ├─ SubPOS apagado
      └─ Puerto ocupado
  └─ Sugerencia: "Verificar conexión física"
```

### 4.4 Logs Detallados de Inyección

**Log Completo de Ejemplo**:
```
=== INICIANDO PROCESO DE INYECCIÓN FUTUREX ===
Perfil: Producción - Seguro
Configuraciones de llave: 3
  1. PIN Entry - Slot: 15 - Tipo: PIN
  2. MAC Calculation - Slot: 35 - Tipo: MAC
  3. Data Encryption - Slot: 55 - Tipo: DATA

=== PASO 1: DETENER POLLING ===
✓ Polling detenido exitosamente

=== PASO 2: INICIALIZAR COMUNICACIÓN ===
Obteniendo controlador de comunicación...
✓ Controlador obtenido: AisinoComController
Configurando comunicación serial:
  - Baud Rate: 115200
  - Paridad: NOPAR
  - Bits de datos: 8
Abriendo conexión serial...
✓ Comunicación inicializada exitosamente

=== PASO 3: VERIFICAR/EXPORTAR KEK ===
KEK seleccionada: A1B2C3 (estado: ACTIVE)
⚠️ KEK debe exportarse al SubPOS
Usuario confirmó exportación
Construyendo comando de exportación KEK...
Enviando KEK en claro al SubPOS...
✓ KEK exportada exitosamente
✓ KEK actualizada: estado = EXPORTED

=== PROCESANDO LLAVE 1/3 ===
Uso: PIN Entry
Slot: 15
Tipo: PIN

Llave encontrada en base de datos:
  - KCV: AABB12
  - Longitud de datos: 16 bytes
  - Datos (primeros 32 bytes): AABBCCDDEEFF00112233445566778899

=== VALIDANDO INTEGRIDAD DE LLAVE ===
✓ Integridad validada:
  - KCV: AABB12
  - Longitud: 16 bytes (válida para 3DES)
  - Tipo: PIN
  - Datos hexadecimales: Sí

=== CIFRANDO LLAVE CON KEK ===
KEK: A1B2C3
Cifrando llave AABB12 con KEK...
✓ Llave cifrada exitosamente
  - Datos originales (primeros 32): AABBCCDDEEFF00112233445566778899
  - Datos cifrados (primeros 32): E7A1B2C3D4E5F6018F9A0B1C2D3E4F50
  - Tipo de encriptación: 01 (Cifrado bajo KTK)
  - Slot de KTK: 05
  - Checksum de KTK: A1B2

=== CONSTRUYENDO COMANDO FUTUREX ===
Comando: 02 (Inyección de llave simétrica)
Versión: 01
Slot de llave: 0F (15)
Slot KTK: 05
Tipo de llave: 05 (PIN)
Tipo de encriptación: 01 (Cifrado bajo KTK)
Checksum de llave: AABB12 (KCV)
Checksum KTK: A1B2
KSN: 00000000000000000000
Longitud de llave: 010 (16 bytes)
  - Formato: ASCII HEX (3 dígitos)
  - Valor: '010'
  - Validación: ✓ Válido
Datos de llave (hex): E7A1B2C3D4E5F6018F9A0B1C2D3E4F50

=== ENVIANDO COMANDO ===
Tamaño: 73 bytes
TX: 02 30 32 30 31 30 46 30 35 30 35 30 31 41 41 42 42 31 32 ...

=== ESPERANDO RESPUESTA ===
Timeout: 10000ms
RX: 02 30 32 30 30 41 41 42 42 31 32 03 5A
Respuesta recibida exitosamente (13 bytes)

=== PROCESANDO RESPUESTA ===
Respuesta parseada: InjectSymmetricKeyResponse
  - Código de respuesta: 00 (Successful)
  - Checksum de llave: AABB12
✓ KCV coincide

✓ Llave 1/3 inyectada exitosamente
Progreso: 33%
Pausa de 500ms...

=== PROCESANDO LLAVE 2/3 ===
[Proceso similar...]

=== PROCESANDO LLAVE 3/3 ===
[Proceso similar...]

=== FINALIZANDO INYECCIÓN ===
Cerrando comunicación serial...
✓ Puerto cerrado exitosamente
Reiniciando polling...
✓ Polling reiniciado

=== INYECCIÓN COMPLETADA EXITOSAMENTE ===
Total de llaves inyectadas: 3/3
Tiempo total: 12.5 segundos
Perfil: Producción - Seguro
Estado: SUCCESS
```

---

## 5. EJEMPLOS DE PERFILES COMUNES

### 5.1 Perfil "Terminal Tienda - Básico"

**Descripción**: Terminal POS de tienda física con llaves básicas

**Configuración**:
```yaml
Nombre: Terminal Tienda - Básico
Descripción: Configuración estándar para terminales de tienda con operaciones básicas
Tipo de Aplicación: Transaccional
Usar KEK: Sí
KEK Seleccionada: A1B2C3 (KEK Master)

Configuraciones de Llaves:
  1. PIN Entry:
     Tipo: PIN
     Slot: 10
     Llave: AABB12 (PIN Key - 3DES)
     
  2. MAC Calculation:
     Tipo: MAC
     Slot: 30
     Llave: CCDD34 (MAC Key - 3DES)
     
  3. Data Encryption:
     Tipo: DATA
     Slot: 50
     Llave: EEFF56 (Data Key - AES-128)
```

**Uso**:
- Transacciones con tarjeta presente
- Validación de PIN
- Cálculo de MAC para autenticación
- Cifrado de datos de track

### 5.2 Perfil "E-Commerce DUKPT Completo"

**Descripción**: Terminal para comercio electrónico con DUKPT

**Configuración**:
```yaml
Nombre: E-Commerce DUKPT Completo
Descripción: Terminal online con DUKPT para máxima seguridad
Tipo de Aplicación: E-Commerce
Usar KEK: Sí
KEK Seleccionada: B2C3D4 (KEK E-Commerce)

Configuraciones de Llaves:
  1. DUKPT PIN BDK:
     Tipo: DUKPT_TDES
     Slot: 70
     Llave: 112233 (BDK 3DES)
     KSN: F876543210000000000A
     
  2. DUKPT AES BDK:
     Tipo: DUKPT_AES
     Slot: 71
     Llave: 445566 (BDK AES-256)
     KSN: F876543210000000000B
     
  3. MAC Static:
     Tipo: MAC
     Slot: 30
     Llave: 778899 (MAC Key - 3DES)
```

**Uso**:
- Transacciones online
- PIN con llave única por transacción (DUKPT)
- Múltiples algoritmos (3DES + AES)
- MAC estático para autenticación

### 5.3 Perfil "ATM Avanzado"

**Descripción**: Cajero automático con llaves completas

**Configuración**:
```yaml
Nombre: ATM Avanzado
Descripción: Configuración completa para cajeros automáticos
Tipo de Aplicación: ATM
Usar KEK: Sí
KEK Seleccionada: C3D4E5 (KEK ATM)

Configuraciones de Llaves:
  1. Master Key TMK:
     Tipo: TDES
     Slot: 0
     Llave: 998877 (TMK)
     
  2. PIN Key:
     Tipo: PIN
     Slot: 10
     Llave: AABBCC (PEK - 3DES)
     
  3. MAC Key:
     Tipo: MAC
     Slot: 30
     Llave: DDEEFF (MAK - 3DES)
     
  4. DUKPT PIN:
     Tipo: DUKPT_TDES
     Slot: 70
     Llave: 112233 (BDK)
     KSN: A123456789000000000F
     
  5. RSA Private:
     Tipo: TDES (placeholder - RSA no implementado aún)
     Slot: 90
     Llave: FEDCBA (RSA Key cifrada)
```

### 5.4 Perfil "Desarrollo y Testing"

**Descripción**: Perfil sin seguridad para desarrollo

**Configuración**:
```yaml
Nombre: Desarrollo y Testing
Descripción: Perfil simplificado para pruebas sin cifrado KEK
Tipo de Aplicación: Desarrollo/Testing
Usar KEK: No

Configuraciones de Llaves:
  1. Test PIN Key:
     Tipo: PIN
     Slot: 15
     Llave: TESTAA (Test PIN - 3DES)
     
  2. Test MAC Key:
     Tipo: MAC
     Slot: 35
     Llave: TESTBB (Test MAC - 3DES)
```

**Advertencias**:
⚠️ Solo para desarrollo  
⚠️ No usar en producción  
⚠️ Llaves enviadas en claro  

---

## 6. MEJORES PRÁCTICAS

### 6.1 Nomenclatura de Perfiles

**Convención Recomendada**:
```
[Entorno] - [Tipo de Terminal] - [Variante]

Ejemplos:
- Producción - Tienda - Básico
- Producción - Tienda - Avanzado
- Producción - ATM - Completo
- Desarrollo - General - Testing
```

**Descripción Clara**:
- Incluir propósito específico
- Mencionar algoritmos principales
- Indicar casos de uso

### 6.2 Organización de Slots

**Rangos Sugeridos**:

| Tipo de Llave | Rango de Slots | Uso |
|---------------|----------------|-----|
| Master Keys (TMK, KEK) | 0-9 | Llaves maestras |
| PIN Keys | 10-29 | Cifrado de PIN |
| MAC Keys | 30-49 | Autenticación |
| Data Keys | 50-69 | Cifrado de datos |
| DUKPT Keys | 70-89 | Esquemas DUKPT |
| RSA/Special | 90-99 | Llaves especiales |

**Ventajas**:
- Fácil identificación
- Evita conflictos
- Estandarización entre terminales

### 6.3 Uso de KEK

**Cuándo Usar KEK**:
✅ Producción (siempre)  
✅ Entornos no controlados  
✅ Inyección remota  
✅ Múltiples operadores  

**Cuándo NO Usar KEK**:
❌ Desarrollo local  
❌ Testing interno  
❌ Laboratorio seguro  
❌ Prototipado rápido  

**Gestión de KEK**:
1. Una KEK maestra por entorno
2. Rotación periódica (3-6 meses)
3. Registro de uso (qué perfiles usan qué KEK)
4. Backup seguro de KEK

### 6.4 Validación de Perfiles

**Antes de Inyectar**:
1. ✓ Verificar que todas las llaves existan
2. ✓ Validar slots únicos
3. ✓ Comprobar tipos compatibles
4. ✓ Validar KSN para DUKPT
5. ✓ Confirmar KEK si está configurada

**Durante Inyección**:
1. ✓ Monitorear progreso
2. ✓ Revisar logs en tiempo real
3. ✓ Validar cada KCV recibido
4. ✓ Confirmar finalización exitosa

**Post-Inyección**:
1. ✓ Verificar todas las llaves en PED
2. ✓ Realizar transacción de prueba
3. ✓ Documentar terminal inyectado
4. ✓ Registrar en sistema de inventario

### 6.5 Backup y Recuperación

**Backup de Perfiles**:
- Exportar configuración a JSON/XML
- Almacenar en sistema de control de versiones
- Documentar cambios importantes

**Recuperación**:
- Importar perfiles desde backup
- Validar llaves necesarias disponibles
- Re-asociar KEKs si es necesario

---

## 7. TROUBLESHOOTING

### 7.1 Problemas Comunes

#### Error: "Llave X no encontrada"

**Causa**: KCV en configuración no existe en BD

**Solución**:
1. Verificar KCV en base de datos
2. Actualizar configuración con KCV correcto
3. O generar llave faltante

#### Error: "KSN inválido para llave DUKPT"

**Causa**: KSN no cumple formato (20 chars hex)

**Solución**:
1. Verificar longitud (exactamente 20)
2. Verificar caracteres (solo 0-9, A-F)
3. Dejar vacío para generación automática

#### Error: "KEK no encontrada"

**Causa**: KEK configurada no existe o fue eliminada

**Solución**:
1. Generar nueva KEK
2. Actualizar perfil con nueva KEK
3. O desactivar uso de KEK (no recomendado)

#### Error: "Slot duplicado"

**Causa**: Dos configuraciones usan mismo slot

**Solución**:
1. Revisar configuraciones del perfil
2. Asignar slots únicos
3. Seguir convención de rangos

### 7.2 Diagnóstico

**Verificación Rápida**:
```
1. Abrir perfil en editor
2. Revisar cada configuración:
   ✓ Llave existe (KCV en BD)
   ✓ Slot único
   ✓ Tipo válido
   ✓ KSN correcto (si DUKPT)
3. Revisar KEK:
   ✓ Existe en BD
   ✓ Estado válido (ACTIVE/EXPORTED)
4. Guardar cambios
5. Reintentar inyección
```

**Logs de Diagnóstico**:
- Activar logs DEBUG
- Revisar validaciones pre-inyección
- Identificar configuración problemática
- Corregir y reintentar

---

## 8. CONCLUSIÓN

El sistema de perfiles permite:

✅ **Configuración Centralizada**: Definir una vez, usar muchas veces  
✅ **Inyección Rápida**: Un clic para inyectar múltiples llaves  
✅ **Estandarización**: Misma configuración en todos los terminales del mismo tipo  
✅ **Seguridad Configurable**: KEK opcional según entorno  
✅ **Flexibilidad**: Soporte para múltiples tipos de llaves y algoritmos  
✅ **Trazabilidad**: Registro completo de qué perfil se usó  
✅ **Mantenimiento Fácil**: Actualizar perfil actualiza todos los terminales  

Los perfiles son el núcleo del sistema de inyección, permitiendo una gestión eficiente y segura de llaves criptográficas en entornos POS.

---

**Siguiente Documento**: [Parte 5: Protocolos de Comunicación](DOCUMENTACION_05_PROTOCOLOS_COMUNICACION.md)


