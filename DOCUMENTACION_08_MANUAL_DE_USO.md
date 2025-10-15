# Manual de Uso del Sistema de Inyección de Llaves Criptográficas

## Parte 8: Guía de Uso y Operación

### Versión: 1.0
### Fecha: Octubre 2025

---

## 1. INICIO RÁPIDO

### 1.1 Credenciales por Defecto

**Aplicación Injector (Dispositivo Maestro)**:
- Usuario: `admin`
- Contraseña: `admin`

**Aplicación App (SubPOS)**:
- Sin autenticación requerida
- Acceso directo a pantalla principal

### 1.2 Primera Ejecución - Injector

**Al iniciar la aplicación por primera vez**:

1. **Pantalla de Login**
   - Ingresar usuario: `admin`
   - Ingresar contraseña: `admin`
   - Presionar "Iniciar Sesión"

2. **Inicialización Automática**
   - El sistema detecta el fabricante del dispositivo
   - Inicializa el SDK de comunicación
   - Inicializa el SDK de llaves (PED)
   - Configura el rol como MASTER

3. **Pantalla Principal**
   - Acceso a: Ceremonia, Llaves, Perfiles
   - Sistema listo para operar

**Tiempo estimado**: 5-10 segundos

### 1.3 Primera Ejecución - App (SubPOS)

**Al iniciar la aplicación**:

1. **Splash Screen**
   - Inicialización del SDK
   - Detección de fabricante
   - Configuración como SUBPOS

2. **Pantalla Principal Automática**
   - Modo de escucha activo
   - Indicador de cable USB
   - Estado de conexión
   - Área de logs

**Tiempo estimado**: 3-5 segundos

**El sistema inicia automáticamente en modo escucha, esperando comandos del Master**

### 1.4 Configuración Inicial Recomendada

**Antes de operar en producción**:

1. **Generar KEK Maestra** (recomendado)
   - Ir a Ceremonia de Llaves
   - Generar llave AES-256
   - Marcar como KEK
   - Nombre: "KEK Master Producción"

2. **Crear Perfil Base**
   - Ir a Perfiles
   - Crear perfil "Transaccional Básico"
   - Configurar llaves PIN, MAC, DATA

3. **Verificar Conexión USB**
   - Conectar Injector y SubPOS con cable USB
   - Verificar indicador de cable en SubPOS
   - Confirmar polling activo en Injector

---

## 2. FLUJOS PRINCIPALES (ALTO NIVEL)

### 2.1 Generación de Llaves - Ceremonia

#### Objetivo del Flujo
Generar llaves criptográficas de forma segura mediante el método de división de secretos, donde múltiples custodios aportan componentes que se combinan para crear la llave final.

#### Puntos Clave del Proceso

**Entrada**:
- Número de custodios (2-5)
- Tipo de llave (3DES, AES-128, AES-192, AES-256)
- Slot de destino (0-99)
- Indicar si es KEK (opcional)

**Proceso**:
1. Cada custodio ingresa su componente secreto (hexadecimal)
2. Sistema combina componentes mediante operación XOR
3. Calcula el KCV (Key Check Value)
4. Almacena en Android KeyStore y Base de Datos

**Salida**:
- Llave generada con KCV único
- Almacenada de forma segura
- Disponible para uso en perfiles

#### Roles de los Custodios

**Custodio 1**: Primer componente (inicio de ceremonia)
**Custodios 2-4**: Componentes intermedios
**Custodio Final**: Último componente (finaliza ceremonia)

**Importante**: Ningún custodio conoce la llave completa, solo su componente

#### Resultado Esperado

✅ Llave generada exitosamente  
✅ KCV calculado (ej: "A1B2C3")  
✅ Almacenada en base de datos  
✅ Visible en pantalla "Llaves Inyectadas"  
✅ Disponible para selección en perfiles  

#### Errores Comunes

❌ **Componente con longitud incorrecta**
- Causa: Longitud no coincide con tipo de llave
- Solución: Verificar que todos los componentes tengan la misma longitud

❌ **Caracteres no hexadecimales**
- Causa: Componente contiene caracteres inválidos
- Solución: Usar solo 0-9, A-F

❌ **Número incorrecto de componentes**
- Causa: No se completaron todos los componentes
- Solución: Asegurar que N custodios ingresen sus componentes

---

### 2.2 Creación y Gestión de Perfiles

#### Crear Perfil Nuevo

**Información Básica**:
- **Nombre**: Identificador único descriptivo
- **Descripción**: Propósito del perfil
- **Tipo de Aplicación**: Transaccional, E-Commerce, ATM, etc.

**Configurar Llaves en Perfil**:
1. Presionar "+ Agregar Llave"
2. Para cada llave:
   - **Uso**: Descripción (ej: "PIN Entry")
   - **Tipo**: PIN, MAC, DATA, DUKPT_TDES, etc.
   - **Slot**: Posición en PED (0-99)
   - **Llave**: Seleccionar por KCV del dropdown
   - **KSN**: Solo para DUKPT (20 chars hexadecimales)

**Puntos Clave**:
- Slots deben ser únicos dentro del perfil
- KSN obligatorio para llaves DUKPT
- Mínimo 1 llave por perfil
- Máximo recomendado: 10 llaves por perfil

#### Configurar KEK (Opcional pero Recomendado)

**Para Producción**:
1. Activar toggle "Usar Cifrado KEK"
2. Seleccionar KEK disponible del dropdown
3. Verificar estado de KEK:
   - **ACTIVE**: Lista para usar (primera vez)
   - **EXPORTED**: Ya fue exportada (reutilizable)
   - **INACTIVE**: No usar

**Ventajas de usar KEK**:
- Llaves viajan cifradas por USB
- Mayor seguridad
- Cumplimiento de estándares

#### Editar y Eliminar Perfiles

**Editar**:
- Presionar ícono de edición en tarjeta de perfil
- Modificar información o configuraciones
- Guardar cambios

**Eliminar**:
- Presionar ícono de eliminación
- Confirmar en modal de advertencia
- Las llaves NO se eliminan, solo la configuración del perfil

#### Puntos Clave de Perfiles

✅ Un perfil = una configuración reutilizable  
✅ Mismo perfil para múltiples dispositivos del mismo tipo  
✅ Validación automática al guardar  
✅ Fácil duplicación para variantes  

---

### 2.3 Inyección de Llaves desde Perfil

#### Conectar Dispositivos

1. **Físicamente**:
   - Conectar cable USB entre Injector y SubPOS
   - Verificar que ambos dispositivos están encendidos

2. **En SubPOS**:
   - Verificar indicador "Cable USB: CONECTADO"
   - Confirmar estado "LISTENING"

3. **En Injector**:
   - Esperar detección automática (2-5 segundos)
   - Verificar estado "CONNECTED" en pantalla de inyección

#### Seleccionar Perfil

1. Ir a pantalla "Perfiles"
2. Localizar perfil deseado en la lista
3. Presionar botón "▶️ Inyectar" en la tarjeta del perfil

#### Ejecutar Inyección

1. **Modal de Inyección se Abre**:
   - Muestra nombre del perfil
   - Lista de llaves a inyectar
   - Estado inicial: "Listo para iniciar"

2. **Presionar "Iniciar Inyección"**:
   - Sistema valida llaves disponibles
   - Exporta KEK si es necesaria (primera vez)
   - Inicia inyección secuencial

3. **Proceso Automático**:
   - Llave 1/N → Llave 2/N → ... → Llave N/N
   - Pausa de 500ms entre llaves
   - Validación de KCV por cada llave

#### Monitorear Progreso

**Indicadores Visuales**:
- **Barra de Progreso**: 0% → 100%
- **Contador**: "Llave X de Y"
- **Logs en Tiempo Real**: Detalles de cada operación

**Estados Posibles**:
- CONNECTING: Estableciendo comunicación
- INJECTING: Inyectando llaves
- SUCCESS: Completado exitosamente
- ERROR: Fallo durante proceso

#### Verificar Resultado

**Al Finalizar con Éxito**:
✅ Mensaje: "¡Inyección completada exitosamente!"  
✅ Todas las llaves muestran estado exitoso  
✅ Logs confirman cada inyección  
✅ En SubPOS: Llaves visibles en pantalla "Llaves Inyectadas"  

**Si Hay Error**:
❌ Mensaje específico del error  
❌ Indicación de qué llave falló  
❌ Logs con detalle del problema  
❌ Opción de reintentar  

#### Puntos Clave de Inyección

⏱️ **Duración típica**: 3-5 segundos por llave  
🔄 **Secuencial**: Una llave a la vez  
🔐 **Cifrado**: Automático si KEK configurada  
✔️ **Validación**: KCV verificado por cada llave  
📊 **Progreso**: Visible en tiempo real  

---

### 2.4 Gestión de KEK

#### Crear Llave para KEK

**Proceso**:
1. Ir a "Ceremonia de Llaves"
2. Seleccionar tipo: **AES-256** (recomendado para KEK)
3. Número de custodios: **3 o más** (mayor seguridad)
4. Ingresar componentes de cada custodio
5. Asignar nombre descriptivo: "KEK Master Octubre 2025" (opcional)
6. Finalizar ceremonia

**Resultado**:
- Llave creada como **operacional** (isKEK = false)
- Estado inicial: **GENERATED**
- Disponible en almacén de llaves

#### Seleccionar KEK desde Almacén

**Proceso**:
1. Ir a "Llaves Inyectadas" (Almacén de Llaves)
2. Usar filtros para encontrar llaves AES-256
3. Localizar la llave deseada
4. Presionar botón **"Usar como KEK"**
5. Confirmar en el diálogo que aparece

**Resultado**:
- Llave seleccionada marcada como KEK activa
- Estado cambia a **ACTIVE**
- Cualquier KEK anterior se desmarca automáticamente
- Badge visual "KEK ACTIVA" aparece en la tarjeta

#### Exportar KEK a SubPOS

**Primera Inyección con KEK Nueva**:

1. Configurar perfil con KEK ACTIVE
2. Al iniciar inyección:
   - Sistema detecta KEK no exportada
   - Muestra modal de confirmación:
     ```
     ⚠️ Esta KEK debe exportarse al SubPOS primero
     Se enviará en CLARO para inicializar el SubPOS
     
     [Cancelar] [Exportar y Continuar]
     ```
3. Usuario confirma "Exportar y Continuar"
4. Sistema exporta KEK en claro
5. SubPOS almacena KEK en su PED
6. Sistema marca KEK como **EXPORTED**
7. Continúa con inyección de llaves operacionales (cifradas)

**Inyecciones Subsecuentes**:
- KEK ya está en SubPOS (estado EXPORTED)
- Todas las llaves se cifran automáticamente
- No se vuelve a exportar KEK

#### Usar KEK en Perfiles

**Configuración**:
1. Crear/Editar perfil
2. Activar toggle "Usar Cifrado KEK"
3. El sistema muestra automáticamente la KEK activa actual
4. Guardar perfil

**Comportamiento**:
- Primera inyección: Exporta KEK + inyecta llaves cifradas
- Inyecciones posteriores: Solo inyecta llaves cifradas
- Solo puede haber una KEK activa a la vez

#### Rotación de KEK

**Cada 3-6 Meses** (recomendado):

1. **Crear Nueva Llave**:
   - Ceremonia nueva
   - AES-256
   - Nombre: "KEK Master Q1 2026"

2. **Seleccionar Nueva KEK**:
   - Ir a "Llaves Inyectadas"
   - Encontrar la nueva llave AES-256
   - Presionar "Usar como KEK"

3. **Sistema Automáticamente**:
   - Desmarca KEK anterior (vuelve a ser operacional)
   - Nueva llave queda como **KEK ACTIVA**

4. **Actualizar Perfiles**:
   - Editar perfiles que usan KEK antigua
   - Seleccionar nueva KEK
   - Guardar cambios

#### Filtros del Almacén de Llaves

**Funcionalidad**:
La pantalla "Llaves Inyectadas" incluye filtros avanzados para facilitar la gestión:

**Campo de Búsqueda**:
- Buscar por KCV (ej: "ABC123")
- Buscar por nombre personalizado
- Buscar por tipo de llave

**Filtros por Categoría**:
- **Algoritmo**: Todos, 3DES, AES-128, AES-192, AES-256
- **Estado**: Todos, SUCCESSFUL, GENERATED, ACTIVE, EXPORTED, INACTIVE
- **Tipo**: Todas, Solo KEK, Solo Operacionales

**Ejemplos de Uso**:
- Filtrar por "AES-256" + "Solo Operacionales" para encontrar llaves candidatas a KEK
- Filtrar por "Solo KEK" para ver únicamente la KEK activa
- Buscar "Master" para encontrar llaves con nombres específicos

4. **Re-inyectar Terminales** (gradual):
   - Inyectar con perfiles actualizados
   - Nueva KEK se exporta a cada SubPOS
   - Llaves operacionales se cifran con nueva KEK

#### Puntos Clave de KEK

🔑 **Una KEK activa** a la vez  
📤 **Exportación automática** en primera inyección  
🔄 **Rotación periódica** para seguridad  
⚠️ **NEVER reuse KEK** con estado INACTIVE  
✅ **Estado EXPORTED** es reutilizable para mismo SubPOS  

---

### 2.5 Operación con Múltiples SubPOS

#### Cambio de Dispositivo

**Escenario**: Inyectar el mismo perfil en 10 terminales

**Proceso Optimizado**:

1. **Preparar Injector**:
   - Perfil configurado y validado
   - KEK lista (si aplica)
   - En pantalla de Perfiles

2. **Terminal 1**:
   - Conectar SubPOS #1
   - Esperar detección (indicador de cable)
   - Presionar "Inyectar" en perfil
   - Confirmar inicio
   - Esperar finalización (3-30 seg según cantidad de llaves)
   - Verificar éxito

3. **Cambio a Terminal 2**:
   - Desconectar SubPOS #1
   - Conectar SubPOS #2
   - Sistema detecta nuevo dispositivo automáticamente
   - Repetir proceso de inyección

4. **Terminales 3-10**:
   - Repetir pasos de cambio
   - No requiere configuración adicional
   - Mismo perfil se aplica a todos

**Tiempo Estimado**:
- Cambio de dispositivo: 5-10 segundos
- Inyección por terminal: 10-30 segundos
- **Total para 10 terminales: 3-7 minutos**

#### Inyección Masiva

**Escenario Avanzado**: 50+ terminales en producción

**Estrategia**:

1. **Preparación**:
   - Perfil único validado
   - Área de trabajo organizada
   - Terminales numerados/etiquetados
   - Cable USB de calidad
   - Hoja de registro

2. **Flujo de Trabajo**:
   ```
   OPERADOR 1 (Injector):
   - Mantiene Injector conectado
   - Inicia cada inyección
   - Verifica éxito
   - Registra en hoja

   OPERADOR 2 (Manejo físico):
   - Conecta terminal
   - Espera señal de operador 1
   - Desconecta terminal completado
   - Mueve a área "Completados"
   ```

3. **Registro**:
   - Terminal ID | Hora | Resultado | Observaciones
   - Ej: "POS-001 | 14:30 | ✅ OK | 4 llaves"

4. **Validación Periódica**:
   - Cada 10 terminales: Verificar uno completo
   - Confirmar llaves en PED
   - Realizar transacción de prueba

#### Puntos Clave Multi-SubPOS

⚡ **Cambio rápido**: 5-10 segundos entre dispositivos  
🔄 **Sin reconfiguración**: Mismo perfil automáticamente  
📊 **Tracking**: Llevar registro de cada terminal  
✅ **Validación periódica**: Cada 10 terminales  
⏱️ **Eficiencia**: 3-5 minutos por terminal (incluyendo cambio)  

**Optimización**:
- Cable USB de calidad (evita errores)
- Batería de Injector al 100%
- Terminales pre-encendidos
- Área de trabajo ordenada

---

## 3. PANTALLAS Y FUNCIONALIDADES

### 3.1 Login (Solo Injector)

**Elementos**:
- Campo "Usuario"
- Campo "Contraseña"
- Botón "Iniciar Sesión"
- Indicador de carga

**Funcionalidad**:
- Validación de credenciales contra BD local
- Mensaje de error si credenciales incorrectas
- Navegación automática tras login exitoso

**Credenciales por Defecto**:
- Usuario: `admin`
- Contraseña: `admin`

---

### 3.2 Ceremonia de Llaves

**Elementos Principales**:
- Selector de número de custodios (2-5)
- Selector de tipo de llave (3DES, AES-128, AES-192, AES-256)
- Campo de slot
- Checkbox "Esta es una KEK"
- Campo nombre personalizado (opcional)
- Área de ingreso de componente
- Botón "Siguiente Componente" / "Finalizar"
- Área de logs

**Flujo Visual**:
1. Configuración inicial
2. Ingreso de componente 1/N
3. Ingreso de componente 2/N
4. ...
5. Finalización y resultado (KCV mostrado)

**Indicadores**:
- Contador: "Componente X de N"
- Progreso visual
- Validación en tiempo real de formato

---

### 3.3 Llaves Inyectadas

**Vista de Lista**:
- Tarjetas de llaves generadas
- Información por llave:
  - KCV (identificador principal)
  - Tipo de llave
  - Algoritmo
  - Slot
  - Fecha de creación
  - Estado (ACTIVE, EXPORTED, INACTIVE)
  - Flag de KEK (si aplica)
  - Nombre personalizado (si tiene)

**Acciones por Llave**:
- Ver detalles
- Eliminar llave
- Marcar/Desmarcar como KEK

**Filtros**:
- Por tipo
- Por algoritmo
- Solo KEKs
- Por estado

---

### 3.4 Perfiles

**Vista Principal**:
- Header con estadísticas:
  - Total de perfiles
  - Perfiles configurados
  - Perfiles listos
- Tarjetas de perfiles con:
  - Avatar/ícono según tipo
  - Nombre
  - Descripción
  - Tipo de aplicación
  - Número de llaves configuradas
  - Badge de estado
  - Indicador de KEK (si usa)

**Acciones por Perfil**:
- ✏️ Editar
- 🔧 Gestionar llaves
- ▶️ Inyectar
- 🗑️ Eliminar

**Modal de Creación/Edición**:
- Información básica
- Configuración de KEK
- Lista de configuraciones de llaves
- Botón "+ Agregar Llave"
- Validaciones en tiempo real

---

### 3.5 Modal de Inyección

**Secciones**:

1. **Header**:
   - Nombre del perfil
   - Ícono de estado

2. **Información de Conexión**:
   - Estado actual (IDLE, CONNECTING, INJECTING, SUCCESS, ERROR)
   - Puerto y baudrate
   - Protocolo (Futurex)

3. **Progreso**:
   - Barra de progreso (0-100%)
   - Contador "Llave X de Y"
   - Tiempo estimado

4. **Logs en Tiempo Real**:
   - Área scrollable
   - Fuente monoespaciada
   - Auto-scroll a último mensaje
   - Códigos de color por nivel

5. **Botones Contextuales**:
   - "Iniciar Inyección" (estado IDLE)
   - "Cancelar" (durante inyección)
   - "Cerrar" (al finalizar)

---

### 3.6 Pantalla Principal App (SubPOS)

**Dashboard**:

1. **Estado de Cable USB**:
   - Indicador visual (conectado/desconectado)
   - 4 métodos de detección
   - Actualización en tiempo real

2. **Estado de Comunicación**:
   - Protocolo activo
   - Estado de listening
   - Mensajes recibidos (contador)
   - Última actividad

3. **Resumen de Llaves**:
   - Total de llaves en PED
   - Últimas 3 inyectadas
   - Acceso rápido a "Ver Todas"

4. **Logs en Tiempo Real**:
   - Eventos de comunicación
   - Comandos recibidos
   - Respuestas enviadas
   - Errores y warnings

5. **Controles**:
   - Iniciar/Detener Listening
   - Cambiar Protocolo
   - Ver Llaves Inyectadas
   - Limpiar Logs

---

## 4. MENSAJERÍA Y VALIDACIONES

### 4.1 Mensajes de Éxito Esperados

**Durante Ceremonia**:
- ✅ "Componente X agregado exitosamente"
- ✅ "Llave generada exitosamente - KCV: XXXXXX"
- ✅ "Llave almacenada en KeyStore"
- ✅ "Llave registrada en base de datos"

**Durante Inyección (Injector)**:
- ✅ "Comunicación inicializada exitosamente"
- ✅ "KEK exportada exitosamente"
- ✅ "Llave 1/4 inyectada exitosamente"
- ✅ "¡Inyección completada exitosamente!"
- ✅ "Polling reiniciado"

**Durante Recepción (SubPOS)**:
- ✅ "Cable USB detectado"
- ✅ "POLL recibido desde MasterPOS"
- ✅ "Comando de inyección recibido"
- ✅ "Llave almacenada en PED exitosamente"
- ✅ "Respuesta enviada con código 00 (éxito)"

### 4.2 Mensajes de Error Comunes

**Ceremonia**:
- ❌ "Componente con longitud incorrecta"
- ❌ "Caracteres no hexadecimales detectados"
- ❌ "Número incorrecto de componentes"
- ❌ "Error al almacenar llave en KeyStore"

**Perfiles**:
- ❌ "Slot duplicado en configuración"
- ❌ "KSN inválido - debe tener 20 caracteres hexadecimales"
- ❌ "Llave seleccionada no encontrada"
- ❌ "KEK seleccionada está INACTIVE"

**Inyección**:
- ❌ "Error al abrir puerto serial"
- ❌ "Timeout esperando respuesta"
- ❌ "KCV no coincide - esperado: XXXX, recibido: YYYY"
- ❌ "KEK no encontrada - exportar primero"
- ❌ "Llave duplicada en slot"
- ❌ "Cable USB desconectado"

**Comunicación**:
- ❌ "Bad LRC - mensaje corrupto"
- ❌ "Puerto serial no disponible"
- ❌ "Auto-scan sin éxito"
- ❌ "Dispositivo no responde"

### 4.3 Códigos de Respuesta Futurex

**Tabla de Referencia Rápida**:

| Código | Descripción | Significado para Usuario |
|--------|-------------|--------------------------|
| 00 | Successful | Llave inyectada correctamente |
| 01 | Invalid command | Comando no reconocido - contactar soporte |
| 02 | Invalid version | Versión de protocolo incorrecta |
| 03 | Invalid length | Longitud de llave incorrecta |
| 05 | Device is busy | Dispositivo ocupado - reintentar |
| 06 | Not in injection mode | Dispositivo no en modo inyección |
| 08 | Bad LRC | Mensaje corrupto - reintentar |
| 09 | Duplicate key | Llave ya existe en slot - eliminar primero |
| 0C | Invalid key slot | Slot fuera de rango (0-99) |
| 0E | Missing KTK | KEK no encontrada - exportar primero |
| 0F | Key slot not empty | Slot ocupado - eliminar llave existente |
| 10 | Invalid key type | Tipo de llave no soportado |
| 12 | Invalid key checksum | KCV incorrecto |
| 14 | Invalid KSN | KSN inválido (debe ser 20 chars hex) |
| 15 | Invalid key length | Longitud no permitida |
| 1C | Decryption failed | KEK incorrecta o corrupta |

**Códigos Críticos** (requieren atención inmediata):
- **07 (Device in tamper)**: Dispositivo con tamper físico - no operar
- **0E (Missing KTK)**: Exportar KEK antes de continuar
- **1C (Decryption failed)**: Verificar KEK correcta

### 4.4 Interpretación de Logs

**Niveles de Log**:

**[DEBUG]**: Información técnica detallada
```
[DEBUG] Construyendo comando Futurex 02
[DEBUG] Payload: "02010F0505..."
[DEBUG] LRC calculado: 0x4E
```

**[INFO]**: Operaciones normales
```
[INFO] Comunicación inicializada exitosamente
[INFO] TX: Comando 02 enviado (73 bytes)
[INFO] RX: Respuesta recibida (13 bytes)
```

**[WARNING]**: Situaciones anómalas no críticas
```
[WARNING] Timeout esperando datos (intento 1 de 3)
[WARNING] KEK ya exportada - reutilizando
```

**[ERROR]**: Errores que requieren atención
```
[ERROR] Error al abrir puerto serial: -3
[ERROR] KCV no coincide - inyección fallida
```

**Patrones Importantes**:

**Inyección Exitosa**:
```
[INFO] === INICIANDO INYECCIÓN ===
[INFO] Llave 1/3: PIN (slot 10)
[DEBUG] TX: 73 bytes enviados
[DEBUG] RX: 13 bytes recibidos
[INFO] Respuesta: Código 00 (éxito), KCV: AABB
[INFO] ✓ Llave 1/3 inyectada exitosamente
```

**Error de Comunicación**:
```
[INFO] TX: Comando enviado
[WARNING] Esperando respuesta...
[WARNING] Timeout (5000ms)
[ERROR] Sin respuesta del SubPOS
[ERROR] Verificar conexión física
```

**Error de Validación**:
```
[INFO] Respuesta recibida: 13 bytes
[DEBUG] Código: 12 (Invalid key checksum)
[ERROR] KCV inválido
[ERROR] Esperado: AABB, Recibido: CCDD
```

---

## 5. MEJORES PRÁCTICAS OPERATIVAS

### 5.1 Cuándo Usar KEK

**SIEMPRE usar KEK en**:
- ✅ Producción
- ✅ Entornos no controlados
- ✅ Inyección con múltiples operadores
- ✅ Cumplimiento de estándares de seguridad
- ✅ Transmisión por cable largo

**Puede omitirse KEK en**:
- ⚠️ Desarrollo local (laboratorio seguro)
- ⚠️ Testing interno
- ⚠️ Prototipado rápido
- ⚠️ Cable muy corto en entorno controlado

**Recomendación General**: **Usar KEK siempre, excepto en desarrollo**

### 5.2 Organización de Slots

**Convención Recomendada**:

| Rango | Tipo de Llave | Uso |
|-------|---------------|-----|
| 0-9 | Master Keys / KEK | Llaves maestras y de transporte |
| 10-29 | PIN Keys | Cifrado de PIN |
| 30-49 | MAC Keys | Autenticación de mensajes |
| 50-69 | Data Keys | Cifrado de datos de track |
| 70-89 | DUKPT Keys | Esquemas DUKPT (BDK/IPEK) |
| 90-99 | RSA / Especiales | Llaves asimétricas y casos especiales |

**Ventajas**:
- Fácil identificación visual
- Evita conflictos entre perfiles
- Estandarización entre terminales
- Simplifica troubleshooting

**Ejemplo Práctico**:
```
Terminal Tienda Estándar:
- Slot 0: KEK Master
- Slot 10: PIN Key Principal
- Slot 11: PIN Key Backup
- Slot 30: MAC Key Transacciones
- Slot 50: Data Key Track 2
```

### 5.3 Nomenclatura de Perfiles

**Convención Recomendada**:
```
[Entorno] - [Tipo Terminal] - [Variante] - [Versión]

Ejemplos:
- PROD - Tienda - Básico - v1.0
- PROD - ATM - Completo - v2.1
- TEST - E-Commerce - DUKPT - v1.5
- DEV - General - Testing - v0.1
```

**Elementos**:
- **Entorno**: PROD, TEST, DEV
- **Tipo Terminal**: Tienda, ATM, E-Commerce, Móvil
- **Variante**: Básico, Avanzado, Completo, DUKPT
- **Versión**: Control de cambios

**Descripción**:
- Incluir propósito específico
- Mencionar algoritmos principales
- Indicar casos de uso
- Máximo 200 caracteres

**Ejemplo**:
```
Nombre: PROD - Tienda - Básico - v1.0
Descripción: Configuración estándar para terminales de tienda con 
llaves PIN (3DES), MAC (3DES) y DATA (AES-128). Incluye cifrado 
con KEK Master. Para transacciones con tarjeta presente.
```

### 5.4 Backup de Configuraciones

**Frecuencia Recomendada**:
- **Diario**: Backup automático de BD
- **Semanal**: Export manual de perfiles a JSON
- **Mensual**: Backup completo del sistema

**Qué Respaldar**:

1. **Base de Datos**:
   - Archivo completo: `injector_database`
   - Ubicación: `/data/data/com.vigatec.injector/databases/`

2. **Perfiles**:
   - Export a JSON
   - Incluye todas las configuraciones
   - No incluye llaves (solo referencias por KCV)

3. **Llaves (Solo KCVs)**:
   - Lista de KCVs generados
   - Tipos y slots asignados
   - NO exportar datos de llaves (seguridad)

4. **Documentación**:
   - Registro de llaves generadas (fecha, tipo, KCV)
   - Registro de KEKs activas
   - Registro de terminales inyectados

**Procedimiento de Backup Manual**:

1. **Exportar Perfiles**:
   - (Funcionalidad futura - actualmente manual)
   - Copiar archivo de BD a almacenamiento externo

2. **Documentar KEKs**:
   - Registro escrito de:
     - KCV de KEK activa
     - Fecha de generación
     - Fecha de última rotación
     - Próxima rotación programada

3. **Almacenamiento Seguro**:
   - USB cifrado
   - Servidor seguro con acceso restringido
   - Múltiples copias en ubicaciones separadas

**Recuperación**:
- Restaurar archivo de BD
- Validar integridad de perfiles
- Verificar que KEK activa está disponible
- Re-generar llaves si es necesario

---

## 6. RESOLUCIÓN DE PROBLEMAS COMUNES

### 6.1 Problemas de Conexión USB

#### Problema: Cable USB No Detectado

**Síntomas**:
- Indicador "Cable USB: DESCONECTADO" en SubPOS
- 0/4 métodos de detección confirman cable
- Sin polling en Injector

**Soluciones**:

1. **Verificar Cable Físico**:
   - Desconectar y reconectar firmemente
   - Probar con cable diferente (de calidad)
   - Verificar que no esté dañado

2. **Verificar Puertos**:
   - Limpiar puertos USB
   - Probar con puerto USB diferente
   - Verificar que no haya suciedad/polvo

3. **Reiniciar Aplicaciones**:
   - Cerrar App en SubPOS
   - Cerrar Injector
   - Reconectar cable
   - Abrir App primero
   - Abrir Injector después

4. **Reiniciar Dispositivos**:
   - Apagar ambos dispositivos
   - Esperar 10 segundos
   - Encender SubPOS primero
   - Encender Injector
   - Conectar cable

#### Problema: Auto-scan Falla (Solo Aisino)

**Síntomas**:
- Mensaje "Auto-scan sin éxito"
- Puerto y baudrate no detectados
- Comunicación no se establece

**Soluciones**:

1. **Forzar Re-scan**:
   - En SubPOS: Reiniciar listening
   - Sistema ejecuta auto-scan nuevamente
   - Esperar 10-15 segundos

2. **Verificar Configuración**:
   - Verificar que `aisinoCandidatePorts = [0, 1]`
   - Verificar que `aisinoCandidateBauds = [9600, 115200]`

3. **Prueba Manual** (avanzado):
   - Contactar soporte para configuración manual
   - Especificar puerto y baudrate directamente

#### Problema: Polling Sin Respuesta

**Síntomas**:
- Injector envía POLL pero no recibe ACK
- Estado permanece "DISCONNECTED"
- Timeout en cada polling

**Soluciones**:

1. **Verificar SubPOS en Listening**:
   - Confirmar estado "LISTENING" en SubPOS
   - Reiniciar listening si está detenido

2. **Verificar Protocolo**:
   - Ambos dispositivos deben usar mismo protocolo
   - Verificar "FUTUREX" en ambos

3. **Revisar Logs**:
   - En SubPOS: Verificar si recibe POLL
   - Si recibe pero no responde: problema de escritura
   - Si no recibe: problema de lectura

### 6.2 Errores de Inyección

#### Error: "Llave X no encontrada"

**Causa**: KCV en configuración no existe en BD

**Solución**:
1. Ir a "Llaves Inyectadas"
2. Verificar que llave con ese KCV existe
3. Si no existe:
   - Generar llave mediante ceremonia
   - O corregir KCV en perfil
4. Editar perfil y actualizar KCV

#### Error: "KCV no coincide"

**Causa**: KCV calculado por PED no coincide con esperado

**Solución**:
1. **Verificar Llave Correcta**:
   - Confirmar que KCV en perfil es correcto
   - Verificar datos de llave en BD

2. **Regenerar Llave**:
   - Si llave está corrupta, eliminar
   - Generar nueva mediante ceremonia
   - Actualizar perfil con nuevo KCV

3. **Verificar Algoritmo**:
   - Confirmar que tipo de llave coincide con algoritmo
   - 3DES debe ser múltiplo de 8 bytes
   - AES debe ser 16, 24 o 32 bytes

#### Error: "KEK no encontrada"

**Causa**: KEK configurada en perfil no existe o fue eliminada

**Solución**:
1. **Generar Nueva KEK**:
   - Ir a Ceremonia
   - Generar KEK AES-256
   - Marcar como KEK

2. **Actualizar Perfil**:
   - Editar perfil problemático
   - Seleccionar nueva KEK
   - Guardar cambios

3. **O Desactivar KEK**:
   - Si no se requiere (solo desarrollo)
   - Desactivar "Usar Cifrado KEK"
   - Guardar perfil

#### Error: "Timeout esperando respuesta"

**Causa**: SubPOS no responde en tiempo esperado (10s)

**Solución**:
1. **Verificar Conexión**:
   - Confirmar cable conectado
   - Verificar indicador en SubPOS

2. **Verificar SubPOS Activo**:
   - Confirmar pantalla encendida
   - Confirmar app en primer plano
   - Confirmar en modo listening

3. **Aumentar Timeout** (avanzado):
   - Para dispositivos lentos
   - Contactar soporte

4. **Revisar Logs SubPOS**:
   - Verificar si comando fue recibido
   - Verificar si hay error en procesamiento

### 6.3 Problemas de Perfiles

#### Problema: "Slot duplicado"

**Causa**: Dos configuraciones usan el mismo slot

**Solución**:
1. Revisar lista de configuraciones en perfil
2. Identificar slots duplicados
3. Reasignar a slots únicos según convención:
   - PIN: 10-29
   - MAC: 30-49
   - DATA: 50-69
   - DUKPT: 70-89

#### Problema: "KSN inválido para DUKPT"

**Causa**: KSN no tiene exactamente 20 caracteres hexadecimales

**Solución**:
1. **Verificar Formato**:
   - Exactamente 20 caracteres
   - Solo 0-9, A-F (hexadecimal)

2. **Ejemplo Válido**: `F876543210000000000A`

3. **Dejar Vacío**:
   - Sistema genera KSN automáticamente
   - Basado en KCV + Slot

4. **Copiar de Documentación**:
   - Usar KSN estándar de documentación
   - Ajustar contador según necesidad

### 6.4 FAQ Operativo

**P: ¿Cuántas llaves puedo inyectar en un perfil?**
R: Técnicamente hasta 100 (slots 0-99), pero recomendamos máximo 10 por perfil para eficiencia.

**P: ¿Puedo usar la misma llave en diferentes slots?**
R: Sí, seleccionar el mismo KCV en múltiples configuraciones. Útil para redundancia.

**P: ¿Qué pasa si inyecto en un slot que ya tiene llave?**
R: Error "Duplicate key" (código 09). Debes eliminar llave existente primero.

**P: ¿Puedo cancelar una inyección en progreso?**
R: Sí, presionar "Cancelar" en modal. Las llaves ya inyectadas permanecen, las pendientes no se procesan.

**P: ¿Cómo sé si una KEK ya fue exportada?**
R: Verificar estado en lista de llaves. ACTIVE = no exportada, EXPORTED = ya exportada.

**P: ¿Cada cuánto rotar KEK?**
R: Recomendado cada 3-6 meses en producción.

**P: ¿Puedo inyectar sin Internet?**
R: Sí, el sistema funciona completamente offline. Solo requiere USB entre dispositivos.

**P: ¿Los logs se guardan automáticamente?**
R: Los logs en pantalla son temporales. Para debugging, habilitar logs en archivo en configuración de desarrollador.

**P: ¿Qué hacer si un dispositivo muestra tamper?**
R: Error crítico (código 07). NO operar el dispositivo. Contactar soporte técnico inmediatamente.

**P: ¿Puedo usar Injector sin SubPOS para solo generar llaves?**
R: Sí, la ceremonia funciona independientemente. Útil para preparar llaves antes de inyectar.

---

## 7. GLOSARIO DE TÉRMINOS

| Término | Definición |
|---------|------------|
| **Injector** | Aplicación/Dispositivo maestro que envía comandos de inyección |
| **SubPOS** | Aplicación/Dispositivo receptor que recibe y almacena llaves |
| **PED** | Pin Entry Device - Módulo de seguridad del dispositivo |
| **KEK** | Key Encryption Key - Llave para cifrar otras llaves |
| **KCV** | Key Check Value - Identificador único de llave (3 bytes) |
| **KSN** | Key Serial Number - Número de serie para DUKPT (20 chars hex) |
| **DUKPT** | Derived Unique Key Per Transaction - Esquema de llaves derivadas |
| **Ceremonia** | Proceso de generación de llaves con división de secretos |
| **Custodio** | Persona que aporta un componente en la ceremonia |
| **Slot** | Posición de almacenamiento en el PED (0-99) |
| **Perfil** | Configuración predefinida de llaves para inyección |
| **Futurex** | Protocolo de comunicación para inyección de llaves |
| **LRC** | Longitudinal Redundancy Check - Verificación de integridad |
| **Polling** | Mensajes periódicos para detectar conexión |
| **Auto-scan** | Detección automática de puerto y baudrate (Aisino) |

---

## 8. CONCLUSIÓN

Este manual proporciona una guía completa para el uso del Sistema de Inyección de Llaves Criptográficas a nivel operativo. Los flujos descritos cubren todos los escenarios principales de uso, desde la generación inicial de llaves hasta la inyección masiva en múltiples dispositivos.

**Puntos Clave para Recordar**:

✅ **Seguridad Primero**: Usar KEK en producción siempre  
✅ **Organización**: Seguir convención de slots y nomenclatura  
✅ **Validación**: Verificar KCV en cada inyección  
✅ **Backup**: Respaldar configuraciones regularmente  
✅ **Logs**: Revisar logs ante cualquier error  

Para información técnica detallada, consultar los documentos de arquitectura (Partes 1-7).

Para plan de pruebas completo, consultar: [Parte 9: Plan de Pruebas QA](DOCUMENTACION_09_PLAN_PRUEBAS_QA.md)

---

**Versión del Manual**: 1.0  
**Última Actualización**: Octubre 2025  
**Soporte**: Consultar documentación técnica o contactar equipo de desarrollo


