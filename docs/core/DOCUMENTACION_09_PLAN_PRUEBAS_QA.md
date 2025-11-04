# Plan de Pruebas QA - Sistema de Inyección de Llaves Criptográficas

## Parte 9: Plan Completo de Pruebas Funcionales

### Versión: 1.0
### Fecha: Octubre 2025

---

## 1. INTRODUCCIÓN AL PLAN DE PRUEBAS

### 1.1 Objetivo

Garantizar la correcta funcionalidad del Sistema de Inyección de Llaves Criptográficas a nivel de usuario final, validando todos los flujos operativos, manejo de errores, y comportamiento en condiciones normales y extremas.

**Objetivos Específicos**:
- ✅ Validar flujos completos end-to-end
- ✅ Verificar manejo de errores y recuperación
- ✅ Confirmar seguridad en transmisión de llaves
- ✅ Validar compatibilidad entre fabricantes
- ✅ Asegurar robustez en condiciones adversas

### 1.2 Alcance

**Incluye**:
- ✅ Autenticación y acceso
- ✅ Ceremonia de llaves (2-5 custodios)
- ✅ Gestión de llaves generadas
- ✅ Creación y gestión de perfiles
- ✅ Inyección de llaves con/sin KEK
- ✅ Comunicación USB (detección, polling, auto-scan)
- ✅ Recepción en SubPOS
- ✅ Mensajería Futurex y Legacy
- ✅ Validaciones de KCV, KSN, LRC
- ✅ Casos edge y límites

**Excluye**:
- ❌ Pruebas de código/unitarias
- ❌ Pruebas de rendimiento (stress testing)
- ❌ Auditoría de código
- ❌ Pruebas de penetración

### 1.3 Dispositivos de Prueba

**Configuración Mínima**:

| Fabricante | Modelo | Rol | Cantidad |
|------------|--------|-----|----------|
| Aisino | A8 | Injector | 1 |
| Aisino | A8 | SubPOS | 1 |
| Newpos | N910 | Injector | 1 |
| Newpos | N910 | SubPOS | 1 |
| Urovo | i9000s | Injector | 1 |
| Urovo | i9000s | SubPOS | 1 |

**Configuración Extendida** (para matriz completa):
- 2 dispositivos Aisino adicionales
- 2 dispositivos Newpos adicionales
- 2 dispositivos Urovo adicionales
- Total: 12 dispositivos

### 1.4 Configuraciones de Prueba

**Ambientes**:
- **DEV**: Desarrollo y pruebas iniciales
- **TEST**: Validación pre-producción
- **PROD-SIM**: Simulación de producción

**Variantes de Protocolos**:
- Futurex (inyección)
- Legacy (polling)

**Variantes de Llaves**:
- 3DES (8, 16, 24 bytes)
- AES-128 (16 bytes)
- AES-192 (24 bytes)
- AES-256 (32 bytes)
- DUKPT_TDES
- DUKPT_AES

**Variantes de Configuración**:
- Con KEK
- Sin KEK
- KEK ya exportada
- KEK nueva

---

## 2. MATRIZ DE CASOS DE PRUEBA

### 2.1 Autenticación (AUTH)

| ID | Escenario | Precondiciones | Pasos | Resultado Esperado | Prioridad |
|----|-----------|----------------|-------|-------------------|-----------|
| AUTH-001 | Login exitoso con credenciales correctas | App Injector instalada, usuario 'admin' existe | 1. Abrir app<br>2. Ingresar 'admin'/'admin'<br>3. Presionar Login | Navegación a pantalla principal, mensaje de bienvenida | Alta |
| AUTH-002 | Login fallido con contraseña incorrecta | App abierta en pantalla login | 1. Ingresar 'admin'/'wrong'<br>2. Presionar Login | Mensaje "Credenciales incorrectas", permanece en login | Alta |
| AUTH-003 | Login fallido con usuario incorrecto | App abierta en pantalla login | 1. Ingresar 'user123'/'admin'<br>2. Presionar Login | Mensaje "Credenciales incorrectas", permanece en login | Alta |
| AUTH-004 | Validación de campos vacíos | App abierta en pantalla login | 1. Dejar campos vacíos<br>2. Presionar Login | Mensaje de validación "Campos requeridos" | Media |
| AUTH-005 | Persistencia de sesión | Usuario previamente logueado | 1. Cerrar app<br>2. Reabrir app | Navegación directa a pantalla principal (sesión activa) | Media |
| AUTH-006 | Logout y re-login | Usuario logueado | 1. Presionar logout<br>2. Login nuevamente | Cerrar sesión correctamente, login exitoso | Baja |

---

### 2.2 Ceremonia de Llaves (CER)

| ID | Escenario | Precondiciones | Pasos | Resultado Esperado | Prioridad |
|----|-----------|----------------|-------|-------------------|-----------|
| CER-001 | Ceremonia 2 custodios 3DES exitosa | Usuario logueado | 1. Ir a Ceremonia<br>2. Seleccionar 2 custodios, 3DES<br>3. Componente 1: 16 chars hex<br>4. Componente 2: 16 chars hex<br>5. Finalizar | Llave generada, KCV mostrado, almacenada en BD | Alta |
| CER-002 | Ceremonia 5 custodios AES-256 exitosa | Usuario logueado | 1. Seleccionar 5 custodios, AES-256<br>2. Ingresar 5 componentes de 64 chars hex cada uno<br>3. Finalizar | Llave AES-256 generada correctamente con KCV | Alta |
| CER-003 | Componente con longitud incorrecta | En ceremonia, tipo 3DES seleccionado | 1. Componente 1: 10 chars hex (incorrecto)<br>2. Intentar continuar | Error: "Longitud incorrecta", no permite continuar | Alta |
| CER-004 | Componente con caracteres no hex | En ceremonia | 1. Componente 1: "GHIJK12345"<br>2. Intentar continuar | Error: "Caracteres no hexadecimales", no permite continuar | Alta |
| CER-005 | Marcar llave como KEK | En ceremonia AES-256 | 1. Configurar AES-256<br>2. Activar checkbox "Esta es KEK"<br>3. Completar ceremonia | Llave generada con flag isKEK=true, estado ACTIVE | Alta |
| CER-006 | Asignar nombre personalizado a llave | En ceremonia | 1. Configurar ceremonia<br>2. Nombre: "KEK Master Oct 2025"<br>3. Completar | Llave con nombre personalizado visible en lista | Media |
| CER-007 | Cancelar ceremonia en progreso | Ceremonia en custodio 3 de 5 | 1. Presionar Cancelar<br>2. Confirmar | Ceremonia cancelada, sin llave generada, volver a pantalla anterior | Media |
| CER-008 | Ceremonia con slot específico | En configuración de ceremonia | 1. Especificar slot 10<br>2. Completar ceremonia | Llave asignada a slot 10 | Media |
| CER-009 | Límite inferior - 2 custodios | En ceremonia | 1. Seleccionar 2 custodios<br>2. Completar | Funciona correctamente (mínimo válido) | Baja |
| CER-010 | Límite superior - 5 custodios | En ceremonia | 1. Seleccionar 5 custodios<br>2. Completar | Funciona correctamente (máximo válido) | Baja |
| CER-011 | Todos los tipos de llave | En ceremonia (repetir) | 1. Generar 3DES<br>2. Generar AES-128<br>3. Generar AES-192<br>4. Generar AES-256 | Todas las llaves generadas correctamente con KCV únicos | Alta |

---

### 2.3 Gestión de Llaves (KEY)

| ID | Escenario | Precondiciones | Pasos | Resultado Esperado | Prioridad |
|----|-----------|----------------|-------|-------------------|-----------|
| KEY-001 | Ver llaves generadas | 3 llaves generadas previamente | 1. Ir a "Llaves Inyectadas"<br>2. Revisar lista | 3 tarjetas visibles con KCV, tipo, algoritmo, fecha | Alta |
| KEY-002 | Eliminar llave específica | Al menos 1 llave existe | 1. Presionar ícono eliminar en llave<br>2. Confirmar | Llave eliminada de lista y BD, mensaje de confirmación | Alta |
| KEY-003 | Eliminar todas las llaves | Múltiples llaves existen | 1. Presionar "Eliminar Todas"<br>2. Confirmar | Todas las llaves eliminadas, lista vacía | Media |
| KEY-004 | Verificar KCV único | Generar 2 llaves idénticas (mismo proceso) | 1. Ceremonia con componentes X, Y<br>2. Ceremonia con mismos X, Y | Ambas llaves tienen mismo KCV (mismos datos = mismo KCV) | Media |
| KEY-005 | Visualizar detalles de llave | 1 llave existe | 1. Seleccionar llave<br>2. Ver detalles | Muestra: KCV, tipo, algoritmo, slot, fecha, estado, isKEK, nombre | Media |
| KEY-006 | Filtrar solo KEKs | 5 llaves totales, 2 son KEK | 1. Activar filtro "Solo KEKs" | Solo muestra las 2 KEKs | Baja |
| KEY-007 | Filtrar por tipo | 3 PIN, 2 MAC, 1 DATA generadas | 1. Filtrar "Solo PIN" | Solo muestra las 3 llaves PIN | Baja |
| KEY-008 | Ordenar por fecha | Llaves generadas en diferentes fechas | 1. Ordenar por fecha descendente | Llaves más recientes primero | Baja |
| KEY-009 | Verificar estados | KEK exportada, KEK nueva, llave normal | 1. Ver lista de llaves | KEK exportada=EXPORTED, KEK nueva=ACTIVE, Normal=ACTIVE | Alta |

---

### 2.4 Gestión de Perfiles (PRF)

| ID | Escenario | Precondiciones | Pasos | Resultado Esperado | Prioridad |
|----|-----------|----------------|-------|-------------------|-----------|
| PRF-001 | Crear perfil básico sin KEK | 3 llaves generadas | 1. Ir a Perfiles<br>2. Presionar "+ Crear"<br>3. Nombre: "Perfil Test"<br>4. Agregar 2 llaves (diferentes slots)<br>5. Guardar | Perfil creado exitosamente, visible en lista | Alta |
| PRF-002 | Crear perfil con KEK | 1 KEK y 2 llaves operacionales | 1. Crear perfil<br>2. Activar "Usar KEK"<br>3. Seleccionar KEK<br>4. Agregar 2 llaves<br>5. Guardar | Perfil con KEK guardado, badge "KEK" visible | Alta |
| PRF-003 | Crear perfil DUKPT | 1 llave DUKPT_TDES generada | 1. Crear perfil<br>2. Agregar llave DUKPT<br>3. KSN: "F876543210000000000A"<br>4. Guardar | Perfil DUKPT creado con KSN válido | Alta |
| PRF-004 | Editar perfil existente | 1 perfil creado | 1. Presionar editar en perfil<br>2. Cambiar nombre<br>3. Agregar 1 llave más<br>4. Guardar | Cambios guardados, perfil actualizado | Alta |
| PRF-005 | Eliminar perfil | 1 perfil creado | 1. Presionar eliminar<br>2. Confirmar | Perfil eliminado, llaves permanecen intactas | Alta |
| PRF-006 | Error: Slots duplicados | En edición de perfil | 1. Agregar llave en slot 10<br>2. Agregar otra llave en slot 10<br>3. Intentar guardar | Error: "Slot 10 duplicado", no permite guardar | Alta |
| PRF-007 | Error: KSN inválido | En configuración DUKPT | 1. Agregar llave DUKPT<br>2. KSN: "ABC" (muy corto)<br>3. Intentar guardar | Error: "KSN debe tener 20 caracteres hex", no guarda | Alta |
| PRF-008 | KSN vacío (auto-generado) | En configuración DUKPT | 1. Agregar llave DUKPT<br>2. Dejar KSN vacío<br>3. Guardar | Sistema genera KSN automáticamente basado en KCV+Slot | Media |
| PRF-009 | Perfil sin llaves | En creación de perfil | 1. Crear perfil<br>2. No agregar llaves<br>3. Intentar guardar | Error: "Perfil debe tener al menos 1 llave" | Media |
| PRF-010 | Máximo de llaves por perfil | En creación de perfil | 1. Agregar 15 llaves<br>2. Guardar | Perfil guardado (límite funcional, no técnico) | Baja |
| PRF-011 | Cambiar KEK en perfil | Perfil con KEK_A, existe KEK_B | 1. Editar perfil<br>2. Cambiar a KEK_B<br>3. Guardar | KEK actualizada en perfil, próxima inyección usará KEK_B | Media |
| PRF-012 | Desactivar KEK en perfil | Perfil con KEK activa | 1. Editar perfil<br>2. Desactivar "Usar KEK"<br>3. Guardar | KEK removida, próximas inyecciones sin cifrado | Media |

---

### 2.5 Inyección de Llaves (INJ)

| ID | Escenario | Precondiciones | Pasos | Resultado Esperado | Prioridad |
|----|-----------|----------------|-------|-------------------|-----------|
| INJ-001 | Inyección perfil básico (sin KEK) | Perfil con 3 llaves, sin KEK, cable conectado | 1. Seleccionar perfil<br>2. Presionar Inyectar<br>3. Confirmar inicio | 3 llaves inyectadas exitosamente, código 00 en cada una, KCV validados | Alta |
| INJ-002 | Primera inyección con KEK nueva | Perfil con KEK ACTIVE + 2 llaves, cable conectado | 1. Iniciar inyección<br>2. Confirmar exportación de KEK<br>3. Esperar finalización | KEK exportada en claro, 2 llaves cifradas inyectadas, KEK → EXPORTED | Alta |
| INJ-003 | Inyección con KEK ya exportada | Perfil con KEK EXPORTED + 2 llaves | 1. Iniciar inyección | No se exporta KEK, 2 llaves cifradas inyectadas directamente | Alta |
| INJ-004 | Cancelar inyección en progreso | Inyección con 5 llaves, en llave 3/5 | 1. Presionar Cancelar | Inyección detenida, llaves 1-2 quedan en PED, 3-5 no se inyectan | Media |
| INJ-005 | Error: Llave faltante | Perfil configurado con KCV inexistente | 1. Intentar inyectar | Error antes de inicio: "Llave con KCV XXXX no encontrada" | Alta |
| INJ-006 | Error: KCV incorrecto | Llave corrupta en BD | 1. Iniciar inyección | Código 12 (Invalid checksum), inyección falla en esa llave | Alta |
| INJ-007 | Error: Timeout sin respuesta | SubPOS no responde (app cerrada) | 1. Cerrar app en SubPOS<br>2. Iniciar inyección | Timeout (10s), error: "Sin respuesta del SubPOS" | Alta |
| INJ-008 | Cable desconectado durante inyección | Inyección en progreso | 1. Iniciar inyección<br>2. Desconectar cable en llave 2/4 | Error de comunicación, logs indican desconexión, inyección falla | Alta |
| INJ-009 | Slot duplicado en PED | Slot 10 ya ocupado en PED | 1. Inyectar llave en slot 10 | Código 09 (Duplicate key), error indicando slot ocupado | Alta |
| INJ-010 | Inyección DUKPT con KSN | Perfil DUKPT con KSN manual | 1. Iniciar inyección | KSN incluido en comando, llave DUKPT inyectada correctamente | Alta |
| INJ-011 | Inyección secuencial de 10 llaves | Perfil con 10 llaves | 1. Iniciar inyección<br>2. Observar progreso | Inyección secuencial 1→10, pausa 500ms entre llaves, todas exitosas | Alta |
| INJ-012 | Validación de KCV en cada llave | Perfil con 3 llaves | 1. Iniciar inyección<br>2. Revisar logs | Logs muestran KCV esperado vs recibido para cada llave, todos coinciden | Media |
| INJ-013 | Reinicio de polling post-inyección | Inyección exitosa | 1. Completar inyección<br>2. Observar estado | Polling reiniciado automáticamente tras 1 segundo | Media |
| INJ-014 | Re-inyección mismo perfil | Perfil ya inyectado antes | 1. Limpiar PED en SubPOS<br>2. Re-inyectar mismo perfil | Inyección exitosa nuevamente (idempotencia) | Baja |
| INJ-015 | Inyección con batería baja (<15%) | Batería Injector al 10% | 1. Iniciar inyección | Warning pero funcional, o error si batería crítica | Media |

---

### 2.6 Comunicación USB (COM)

| ID | Escenario | Precondiciones | Pasos | Resultado Esperado | Prioridad |
|----|-----------|----------------|-------|-------------------|-----------|
| COM-001 | Detección de cable conectado | Ambas apps abiertas, cable conectado | 1. Observar indicadores | Injector: "CONNECTED", SubPOS: "Cable USB: CONECTADO" (≥2/4 métodos) | Alta |
| COM-002 | Detección de cable desconectado | Cable previamente conectado | 1. Desconectar cable físicamente<br>2. Esperar 2-3 seg | Injector: "DISCONNECTED", SubPOS: "Cable USB: DESCONECTADO" | Alta |
| COM-003 | Polling exitoso (Legacy) | Cable conectado, protocolo Legacy | 1. Observar logs en SubPOS | POLL recibido cada 2 seg, ACK enviado, logs confirman | Alta |
| COM-004 | Cambio de SubPOS durante polling | Polling activo con SubPOS_A | 1. Desconectar SubPOS_A<br>2. Conectar SubPOS_B<br>3. Esperar | Polling detecta nuevo dispositivo, conecta exitosamente | Alta |
| COM-005 | Auto-scan de puertos (Aisino) | Aisino como Injector y SubPOS | 1. Conectar cable<br>2. Esperar auto-scan | Sistema detecta puerto y baudrate automáticamente (dentro de 10-15s) | Alta |
| COM-006 | Re-scan automático tras fallo | Auto-scan falló inicialmente | 1. Reiniciar listening en SubPOS | Auto-scan ejecuta nuevamente, detecta correctamente | Media |
| COM-007 | Comunicación con puerto fijo (Newpos/Urovo) | Newpos o Urovo | 1. Conectar cable | Comunicación en puerto/baudrate predefinidos sin auto-scan | Alta |
| COM-008 | Múltiples métodos de detección USB | Cable conectado | 1. Ver logs de detección | UsbManager=true, DeviceNodes=true, SysFS=true, API-Level=true (4/4) | Media |
| COM-009 | Detección solo con UsbManager | Cable USB-C especial | 1. Conectar cable especial | UsbManager=true suficiente, indicador "CONECTADO" | Media |
| COM-010 | Write failure recuperable | Interferencia momentánea | 1. Simular interferencia<br>2. Observar comportamiento | Reintento automático, éxito en segundo intento | Media |
| COM-011 | Write failure permanente | Cable defectuoso | 1. Usar cable dañado<br>2. Intentar comunicación | Tras 3 fallos consecutivos, error de comunicación | Alta |
| COM-012 | Validación de LRC | Mensaje Futurex enviado | 1. Enviar comando<br>2. Revisar logs | LRC calculado correctamente (XOR de bytes), mensaje aceptado | Alta |
| COM-013 | Bad LRC detectado | Mensaje corrupto simulado | 1. (Test interno) Corromper mensaje | Código 08 (Bad LRC), mensaje rechazado | Media |

---

### 2.7 Recepción en SubPOS (REC)

| ID | Escenario | Precondiciones | Pasos | Resultado Esperado | Prioridad |
|----|-----------|----------------|-------|-------------------|-----------|
| REC-001 | Recibir comando de inyección | SubPOS en listening, cable conectado | 1. Injector envía comando 02<br>2. Observar SubPOS | Comando recibido, parseado correctamente, logs muestran detalles | Alta |
| REC-002 | Validar llave recibida | Comando con llave 3DES | 1. Recibir comando<br>2. Validar datos | Tipo, slot, algoritmo validados, llave extraída correctamente | Alta |
| REC-003 | Calcular KCV correcto | Llave recibida | 1. Almacenar en PED<br>2. Calcular KCV | KCV calculado coincide con esperado, respuesta código 00 | Alta |
| REC-004 | Responder con código 00 (éxito) | Inyección exitosa | 1. Almacenar llave<br>2. Enviar respuesta | Respuesta: STX + "00" + KCV + ETX + LRC, Injector confirma éxito | Alta |
| REC-005 | Rechazar llave con slot duplicado | Slot 10 ya ocupado | 1. Recibir comando para slot 10<br>2. Detectar duplicado | Respuesta código 09 (Duplicate key), llave no almacenada | Alta |
| REC-006 | Rechazar llave con tipo inválido | Comando con tipo no soportado | 1. Recibir comando tipo "99"<br>2. Validar | Respuesta código 10 (Invalid key type) | Media |
| REC-007 | Almacenar en PED exitosamente | Llave validada | 1. Llamar PedController.writeKey()<br>2. Verificar | Retorno código 0, llave en PED, visible en pantalla llaves | Alta |
| REC-008 | Desencriptar con KEK | Llave cifrada recibida | 1. Buscar KEK en PED<br>2. Desencriptar<br>3. Almacenar | Desencriptación exitosa, llave operacional en PED | Alta |
| REC-009 | Error: KEK no encontrada | Llave cifrada, KEK ausente | 1. Recibir llave cifrada<br>2. Buscar KEK | Código 0E (Missing KTK), inyección falla | Alta |
| REC-010 | Error: Decryption failed | KEK incorrecta | 1. KEK corrupta en PED<br>2. Intentar desencriptar | Código 1C (Decryption failed) | Media |
| REC-011 | Recibir y almacenar KEK en claro | Primera exportación de KEK | 1. Recibir KEK sin cifrado<br>2. Almacenar | KEK almacenada en slot especial, disponible para desencriptar llaves futuras | Alta |
| REC-012 | Procesar llave DUKPT con KSN | Comando DUKPT con KSN | 1. Recibir comando<br>2. Extraer KSN<br>3. Almacenar | DUKPT almacenada con KSN, lista para derivaciones | Alta |
| REC-013 | Validar longitud de llave | Llave de 48 bytes (longitud máxima) | 1. Recibir llave 48 bytes<br>2. Validar | Longitud aceptada, almacenada correctamente | Media |
| REC-014 | Rechazar longitud inválida | Llave de 50 bytes | 1. Recibir comando<br>2. Validar longitud | Código 15 (Invalid key length) | Media |

---

### 2.8 Casos Edge y Límites (EDGE)

| ID | Escenario | Precondiciones | Pasos | Resultado Esperado | Prioridad |
|----|-----------|----------------|-------|-------------------|-----------|
| EDGE-001 | Inyección de 100 llaves consecutivas | Perfil con 100 llaves (slots 0-99) | 1. Iniciar inyección<br>2. Esperar finalización (~8 minutos) | Todas las 100 llaves inyectadas exitosamente, sin errores | Media |
| EDGE-002 | Inyección con batería baja SubPOS | Batería SubPOS al 5% | 1. Iniciar inyección | Warning o error por batería crítica, recomendación de cargar | Media |
| EDGE-003 | Cambio rápido de 10 SubPOS | 10 dispositivos listos | 1. Inyectar en SubPOS_1<br>2. Cambiar a SubPOS_2 inmediatamente<br>...<br>10. SubPOS_10 | Todos inyectados correctamente, sin cruces de datos | Alta |
| EDGE-004 | Llave de longitud máxima (48 bytes) | Generar llave AES de 48 bytes | 1. Ceremonia con componentes de 96 chars hex<br>2. Inyectar | Llave generada, inyectada y almacenada correctamente | Media |
| EDGE-005 | Slot máximo (99) | Configurar llave en slot 99 | 1. Crear perfil con slot 99<br>2. Inyectar | Llave inyectada en slot 99 exitosamente | Media |
| EDGE-006 | Slot mínimo (0) | Configurar KEK en slot 0 | 1. Crear perfil con slot 0<br>2. Inyectar | Llave inyectada en slot 0 exitosamente | Media |
| EDGE-007 | Nombre de perfil muy largo | Crear perfil con nombre de 200 chars | 1. Nombre: "A" * 200<br>2. Guardar | Perfil guardado, nombre truncado o completo según límite de BD | Baja |
| EDGE-008 | Nombre de llave muy largo | Ceremonia con nombre de 150 chars | 1. Nombre: "Mi llave maestra..." (150)<br>2. Finalizar | Llave guardada con nombre completo o truncado | Baja |
| EDGE-009 | 50 perfiles en sistema | Crear 50 perfiles diferentes | 1. Crear 50 perfiles<br>2. Navegar lista | Todos los perfiles visibles, scroll funcional, rendimiento aceptable | Baja |
| EDGE-010 | 200 llaves generadas | Generar 200 llaves | 1. Múltiples ceremonias<br>2. Ver lista | Todas visibles, filtros funcionales, BD estable | Baja |
| EDGE-011 | Re-inyección inmediata post-cancelación | Cancelar inyección en 50% | 1. Cancelar inyección<br>2. Re-iniciar inmediatamente | Re-inyección exitosa, no hay conflictos | Media |
| EDGE-012 | Desconexión y reconexión rápida | Cable conectado | 1. Desconectar cable<br>2. Reconectar en <1 segundo | Sistema detecta cambio, re-establece comunicación correctamente | Media |
| EDGE-013 | Rotación de 10 KEKs | 10 KEKs generadas secuencialmente | 1. Generar KEK_1 (ACTIVE)<br>...<br>10. Generar KEK_10 | Solo KEK_10 ACTIVE, anteriores INACTIVE, sistema estable | Baja |
| EDGE-014 | Inyección con componentes máximos (5) | Ceremonia de 5 custodios | 1. 5 componentes de 64 chars cada uno<br>2. Finalizar<br>3. Inyectar | Llave generada y funcional en inyección | Media |
| EDGE-015 | Cable USB de 3 metros | Usar cable largo | 1. Conectar con cable de 3m<br>2. Inyectar | Comunicación estable, inyección exitosa (puede requerir baudrate menor) | Baja |

---

## 3. VALIDACIONES DE MENSAJERÍA

### 3.1 Tabla de Códigos de Respuesta Futurex

| Código | Nombre | Descripción | Escenario de Prueba | Acción QA | Severidad |
|--------|--------|-------------|---------------------|-----------|-----------|
| **00** | Successful | Comando ejecutado exitosamente | Cualquier inyección exitosa | ✅ Verificar KCV coincide | N/A |
| **01** | Invalid command | Comando no reconocido por el dispositivo | Enviar comando con código inválido (ej: 99) | ❌ Reportar, verificar protocolo | Alta |
| **02** | Invalid version | Versión del protocolo incorrecta | Modificar versión en comando | ❌ Verificar versión configurada | Media |
| **03** | Invalid length | Longitud del comando o llave incorrecta | Enviar llave de 7 bytes para 3DES | ❌ Verificar configuración de llave | Alta |
| **05** | Device is busy | PED ocupado procesando otro comando | Enviar 2 comandos simultáneamente | ⚠️ Reintentar tras 500ms | Baja |
| **06** | Not in injection mode | Dispositivo no está en modo inyección | SubPOS no en listening | ❌ Verificar estado de SubPOS | Alta |
| **07** | Device in tamper | Dispositivo con tamper físico detectado | Manipulación física del PED | 🚨 CRÍTICO: No operar dispositivo | Crítica |
| **08** | Bad LRC | Checksum LRC incorrecto, mensaje corrupto | Corromper mensaje intencionalmente | ❌ Verificar cálculo de LRC | Alta |
| **09** | Duplicate key | Llave ya existe en el slot especificado | Inyectar 2 veces en mismo slot sin limpiar | ✅ Esperado, limpiar slot primero | Media |
| **0C** | Invalid key slot | Slot fuera del rango permitido (0-99) | Configurar slot 100 | ❌ Verificar rango en configuración | Media |
| **0E** | Missing KTK | KEK (Key Transport Key) no encontrada | Inyectar llave cifrada sin exportar KEK antes | ❌ Exportar KEK primero | Alta |
| **0F** | Key slot not empty | Slot ya ocupado (similar a 09) | Inyectar en slot ocupado | ✅ Esperado, validar comportamiento | Media |
| **10** | Invalid key type | Tipo de llave no soportado por el PED | Usar tipo no disponible en fabricante | ❌ Verificar tipos soportados | Alta |
| **12** | Invalid key checksum | KCV calculado no coincide con el esperado | Llave corrupta o modificada | ❌ Regenerar llave, verificar integridad | Alta |
| **14** | Invalid KSN | KSN para DUKPT inválido (formato o longitud) | KSN de 15 chars (debe ser 20) | ❌ Validar formato KSN (20 hex) | Alta |
| **15** | Invalid key length | Longitud de llave no permitida para el tipo | Llave AES-128 de 20 bytes (debe ser 16) | ❌ Verificar longitud según tipo | Alta |
| **1C** | Decryption failed | Falló desencriptación con KEK | KEK incorrecta o corrupta | ❌ Verificar KEK correcta exportada | Alta |

### 3.2 Validación de Formato de Mensajes

**Estructura General Futurex**:
```
[STX] [PAYLOAD] [ETX] [LRC]
```

**Componentes**:
- **STX**: 0x02 (1 byte)
- **PAYLOAD**: Datos del comando (variable)
- **ETX**: 0x03 (1 byte)
- **LRC**: Checksum (1 byte)

**Payload Comando de Inyección (02)**:
```
[CMD] [VERSION] [SLOT] [KEY_TYPE] [ALGORITHM] [ENCRYPTION_TYPE] [KCV] [KSN_FLAG] [KSN?] [KEY_LENGTH] [KEY_DATA]
```

**Ejemplo Decodificado**:
```
Comando: 02
Versión: 01
Slot: 0F (slot 15)
Tipo: 05 (PIN)
Algoritmo: 05 (3DES)
Cifrado: 00 (sin cifrado)
KCV: AABBCC (3 bytes)
KSN Flag: 00 (sin KSN)
Longitud Llave: 0010 (16 bytes hex)
Datos Llave: 32 bytes (16 bytes hex = 32 chars)
```

**Pruebas de Formato**:

| Caso | Validación | Resultado Esperado |
|------|------------|-------------------|
| FORM-001 | STX presente al inicio | Byte 0 = 0x02 |
| FORM-002 | ETX presente antes de LRC | Byte[N-1] = 0x03 |
| FORM-003 | LRC en última posición | Byte[N] = XOR de todos los bytes anteriores |
| FORM-004 | Longitud de campo correcta | CMD=1, VERSION=1, SLOT=1, etc. |
| FORM-005 | Formato hexadecimal | Solo caracteres 0-9, A-F |
| FORM-006 | KCV 3 bytes | Exactamente 6 chars hex |
| FORM-007 | KSN 10 bytes (si presente) | Exactamente 20 chars hex |
| FORM-008 | KEY_LENGTH coincide con datos | Si LENGTH=16, KEY_DATA=32 chars |

**Validación de LRC Manual**:

**Ejemplo**:
```
Mensaje (sin LRC): 02 41 42 43 03
Cálculo: 02 XOR 41 XOR 42 XOR 43 XOR 03 = 01
LRC: 01
Mensaje Completo: 02 41 42 43 03 01
```

**Prueba QA**:
1. Capturar mensaje en logs
2. Extraer bytes hasta ETX (inclusive)
3. Calcular XOR manual
4. Comparar con LRC enviado
5. ✅ Deben coincidir

---

### 3.3 Validación de Respuestas

**Formato Respuesta SubPOS**:
```
[STX] [RESPONSE_CODE] [KCV?] [ETX] [LRC]
```

**Ejemplo Éxito**:
```
STX: 02
Código: 00
KCV: AABBCC (si exitoso)
ETX: 03
LRC: (calculado)
```

**Ejemplo Error**:
```
STX: 02
Código: 12 (Invalid checksum)
ETX: 03
LRC: (calculado)
```

**Pruebas de Respuestas**:

| Caso | Escenario | Respuesta Esperada | Validación QA |
|------|-----------|-------------------|---------------|
| RESP-001 | Inyección exitosa | Código 00 + KCV | ✅ KCV coincide con esperado |
| RESP-002 | Slot duplicado | Código 09 | ✅ Sin KCV en respuesta |
| RESP-003 | KEK faltante | Código 0E | ✅ Sin KCV, mensaje claro |
| RESP-004 | Timeout (sin respuesta) | Ninguna tras 10s | ❌ Error en Injector |
| RESP-005 | Respuesta corrupta | LRC inválido | ❌ Error "Bad LRC", reintentar |

---

## 4. CHECKLIST DE QA

### 4.1 Checklist Pre-Pruebas

**Preparación de Dispositivos**:
- [ ] Todos los dispositivos cargados al 100%
- [ ] Cables USB de calidad disponibles (mínimo 3)
- [ ] Apps instaladas en versión correcta (verificar build number)
- [ ] Permisos USB otorgados manualmente si es necesario
- [ ] Dispositivos con fechas/hora sincronizadas

**Preparación de Ambiente**:
- [ ] Base de datos limpia en Injector (o respaldada si se requiere estado específico)
- [ ] PED limpio en SubPOS (sin llaves previas)
- [ ] Logs habilitados en ambas apps (nivel DEBUG)
- [ ] Herramienta de captura de logs lista (ADB o log viewer)
- [ ] Hoja de registro de pruebas preparada

**Validación de Instalación**:
- [ ] Injector: Splash screen → Login correcto
- [ ] SubPOS: Splash screen → Pantalla principal
- [ ] Fabricante detectado correctamente en ambos
- [ ] SDKs inicializados sin errores (revisar logs)

**Materiales de Apoyo**:
- [ ] Tabla de códigos de respuesta impresa
- [ ] Matriz de compatibilidad de dispositivos
- [ ] Valores de prueba (KSN, componentes, KCVs esperados)
- [ ] Checklist físico impreso

### 4.2 Checklist Durante Inyección

**Antes de Iniciar**:
- [ ] Conexión física verificada (cable firme)
- [ ] Indicador USB: CONECTADO en SubPOS
- [ ] Estado: CONNECTED en Injector
- [ ] Perfil válido seleccionado
- [ ] Llaves en perfil verificadas disponibles en BD
- [ ] KEK exportada (si perfil requiere, y no es primera vez)
- [ ] PED en SubPOS sin llaves en slots a usar

**Durante Inyección**:
- [ ] Progreso visible (barra avanzando)
- [ ] Logs mostrando TX/RX en tiempo real
- [ ] Códigos de respuesta 00 por cada llave
- [ ] KCV validados automáticamente
- [ ] Sin errores de timeout
- [ ] Sin errores de LRC

**Post-Inyección Exitosa**:
- [ ] Mensaje "¡Inyección completada exitosamente!"
- [ ] Todas las llaves con estado exitoso
- [ ] Logs completos sin errores
- [ ] Polling reiniciado (verificar en logs tras 1-2 seg)
- [ ] En SubPOS: Llaves visibles en lista

**En Caso de Error**:
- [ ] Mensaje de error específico capturado
- [ ] Código de respuesta identificado
- [ ] Screenshot de error tomado
- [ ] Logs completos guardados
- [ ] Detalles registrados (hora, dispositivos, perfil, paso donde falló)

### 4.3 Checklist Post-Pruebas

**Documentación de Resultados**:
- [ ] Hoja de registro completada con todos los casos
- [ ] Logs guardados por cada caso (especialmente los fallidos)
- [ ] Screenshots de errores capturados y organizados
- [ ] Defectos identificados registrados en sistema de tracking
- [ ] Casos exitosos confirmados con ✅
- [ ] Casos fallidos confirmados con ❌ y evidencia

**Limpieza de Dispositivos**:
- [ ] PED limpiado en todos los SubPOS (eliminar llaves de prueba)
- [ ] BD respaldada en Injector (si contiene datos importantes)
- [ ] BD limpiada para próxima sesión (o restaurada a estado inicial)
- [ ] Logs exportados y almacenados en carpeta del proyecto
- [ ] Apps cerradas correctamente

**Preparación de Reporte**:
- [ ] Resumen ejecutivo: X casos ejecutados, Y exitosos, Z fallidos
- [ ] Tabla de resultados por módulo
- [ ] Lista de defectos priorizados
- [ ] Evidencia adjunta (logs, screenshots)
- [ ] Recomendaciones para desarrollo

**Seguimiento**:
- [ ] Defectos asignados a desarrolladores
- [ ] Plan de re-testing definido
- [ ] Próxima sesión de QA agendada
- [ ] Dispositivos guardados de forma segura

---

## 5. MATRIZ DE COMPATIBILIDAD

### 5.1 Combinaciones de Dispositivos

**Objetivo**: Validar comunicación entre diferentes fabricantes

| Injector | SubPOS | Protocolo | Auto-scan | Resultado Esperado | Prioridad |
|----------|--------|-----------|-----------|-------------------|-----------|
| Aisino | Aisino | Futurex | Sí | Comunicación exitosa, auto-scan funcional | Alta |
| Aisino | Newpos | Futurex | Aisino: Sí | Comunicación exitosa | Alta |
| Aisino | Urovo | Futurex | Aisino: Sí | Comunicación exitosa | Alta |
| Newpos | Aisino | Futurex | Aisino: Sí | Comunicación exitosa | Alta |
| Newpos | Newpos | Futurex | No | Comunicación exitosa en puerto fijo | Alta |
| Newpos | Urovo | Futurex | No | Comunicación exitosa | Alta |
| Urovo | Aisino | Futurex | Aisino: Sí | Comunicación exitosa | Alta |
| Urovo | Newpos | Futurex | No | Comunicación exitosa | Alta |
| Urovo | Urovo | Futurex | No | Comunicación exitosa en puerto fijo | Alta |

**Pruebas por Combinación**:
1. Conectar cable entre dispositivos especificados
2. Verificar detección de cable (4 métodos en SubPOS)
3. Iniciar polling (si aplica)
4. Verificar auto-scan exitoso (si aplica)
5. Inyectar perfil de prueba (3 llaves)
6. Validar éxito completo

**Criterio de Aceptación**: 9/9 combinaciones exitosas

### 5.2 Compatibilidad de Algoritmos por Fabricante

| Fabricante | 3DES | AES-128 | AES-192 | AES-256 | DUKPT_TDES | DUKPT_AES | RSA |
|------------|------|---------|---------|---------|------------|-----------|-----|
| Aisino | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Newpos | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ❌ |
| Urovo | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ |

**Leyenda**:
- ✅ Totalmente soportado
- ⚠️ Soportado con limitaciones (verificar documentación)
- ❌ No soportado

**Pruebas**:
- COMPAT-001: Generar cada tipo de llave en Injector
- COMPAT-002: Inyectar en cada fabricante
- COMPAT-003: Validar almacenamiento en PED
- COMPAT-004: Verificar funcionalidad de llave

### 5.3 Compatibilidad de Tipos de Llave

| Tipo de Llave | Aisino | Newpos | Urovo | Notas |
|---------------|--------|--------|-------|-------|
| MASTER_KEY | ✅ | ✅ | ✅ | - |
| WORKING_PIN_KEY | ✅ | ✅ | ✅ | - |
| WORKING_MAC_KEY | ✅ | ✅ | ✅ | - |
| WORKING_DATA_KEY | ✅ | ✅ | ✅ | - |
| DUKPT_INITIAL_KEY | ✅ | ✅ | ✅ | - |
| TRANSPORT_KEY | ✅ | ✅ | ✅ | KEK equivalente |

---

## 6. ESCENARIOS DE ERROR Y RECUPERACIÓN

### 6.1 Tabla de Errores Provocados

| ID | Error Provocado | Cómo Provocarlo | Comportamiento Esperado | Recuperación Esperada | Validación |
|----|-----------------|-----------------|------------------------|----------------------|------------|
| ERR-001 | Cable desconectado | Desconectar físicamente durante inyección | Error: "Cable desconectado", inyección detenida | Reconectar cable, re-iniciar inyección, llaves previas permanecen | ✅ Re-inyección exitosa |
| ERR-002 | SubPOS apagado | Apagar SubPOS durante inyección | Timeout (10s), error: "Sin respuesta" | Encender SubPOS, re-iniciar inyección | ✅ Re-inyección exitosa |
| ERR-003 | App SubPOS cerrada | Cerrar app durante listening | Injector: Sin ACK en polling, estado DISCONNECTED | Reabrir app, polling detecta automáticamente | ✅ Conexión restaurada |
| ERR-004 | Batería crítica (<5%) | Iniciar inyección con batería baja | Warning o error antes de iniciar | Cargar dispositivo, re-intentar | ✅ Funciona tras carga |
| ERR-005 | Slot duplicado en PED | Inyectar 2 veces sin limpiar | Código 09 (Duplicate key), error claro | Limpiar slot en PED, re-inyectar | ✅ Llave inyectada tras limpieza |
| ERR-006 | KEK faltante | Perfil con KEK, pero KEK no exportada y se salta modal | Código 0E (Missing KTK) | Exportar KEK, re-intentar inyección | ✅ Funciona tras exportar |
| ERR-007 | Llave corrupta en BD | Modificar datos de llave directamente en BD | Código 12 (Invalid checksum) | Eliminar llave corrupta, regenerar, actualizar perfil | ✅ Funciona con nueva llave |
| ERR-008 | Puerto USB sucio | Polvo/suciedad en puerto | Conexión intermitente, fallos aleatorios | Limpiar puerto con aire comprimido, reconectar | ✅ Comunicación estable |
| ERR-009 | Auto-scan fallo (Aisino) | Interferencia durante scan | Error: "Auto-scan sin éxito" | Re-iniciar listening, auto-scan ejecuta nuevamente | ✅ Detecta en segundo intento |
| ERR-010 | Timeout en comando | Red saturada o dispositivo lento | Timeout tras 10s, error en logs | Verificar estado, re-intentar con mismo comando | ✅ Funciona en reintento |
| ERR-011 | LRC inválido | (Test interno) Corromper mensaje | Código 08 (Bad LRC), mensaje rechazado | Sistema re-calcula LRC, reenvía automáticamente | ✅ Segundo envío exitoso |
| ERR-012 | Decryption failed | KEK incorrecta en SubPOS | Código 1C | Eliminar KEK incorrecta, re-exportar correcta | ✅ Funciona con KEK correcta |
| ERR-013 | Perfil sin llaves | Guardar perfil vacío (burlar validación) | Error al intentar inyectar | Editar perfil, agregar llaves, guardar | ✅ Inyección exitosa tras corrección |
| ERR-014 | Cable defectuoso | Usar cable dañado | Conexión detectada pero fallos de comunicación | Cambiar cable por uno de calidad | ✅ Comunicación estable con cable nuevo |
| ERR-015 | Cambio de SubPOS sin desconectar | Conectar nuevo SubPOS con cable ya conectado a otro | Detección de nuevo dispositivo | Sistema detecta cambio, re-inicializa comunicación | ✅ Nuevo SubPOS detectado correctamente |

### 6.2 Matriz de Recuperación

**Para cada error, seguir matriz**:

| Error | Severidad | Acción Inmediata | Tiempo Recuperación | Re-testing |
|-------|-----------|------------------|---------------------|------------|
| Cable desconectado | Media | Reconectar cable | 5-10 seg | Caso INJ-001 |
| SubPOS apagado | Alta | Encender dispositivo | 30 seg | Caso INJ-001 |
| App cerrada | Media | Reabrir app | 5 seg | Caso COM-003 |
| Batería crítica | Alta | Cargar dispositivo | 15+ min | Caso INJ-001 |
| Slot duplicado | Media | Limpiar PED | 30 seg | Caso INJ-001 |
| KEK faltante | Alta | Exportar KEK | 10 seg | Caso INJ-002 |
| Llave corrupta | Alta | Regenerar llave | 2-3 min | Caso CER-001 + INJ-001 |
| Puerto sucio | Media | Limpiar puerto | 2 min | Caso COM-001 |
| Auto-scan fallo | Media | Re-iniciar listening | 15 seg | Caso COM-005 |
| Timeout | Baja | Re-intentar comando | 5 seg | Caso actual |
| Bad LRC | Media | Sistema auto-recupera | 1 seg | N/A (automático) |
| Decryption failed | Alta | Re-exportar KEK | 30 seg | Caso INJ-002 |

---

## 7. CRITERIOS DE ACEPTACIÓN

### 7.1 Métricas de Éxito

**Funcionalidad Core**:
- ✅ 100% de casos AUTH exitosos (6/6)
- ✅ 95% de casos CER exitosos (10/11, permitir 1 edge case)
- ✅ 100% de casos KEY críticos exitosos (5/9)
- ✅ 100% de casos PRF críticos exitosos (8/12)
- ✅ 100% de casos INJ críticos exitosos (12/15)
- ✅ 100% de casos COM críticos exitosos (9/13)
- ✅ 100% de casos REC críticos exitosos (10/14)
- ✅ 90% de casos EDGE exitosos (13/15)

**Comunicación**:
- ✅ 100% de combinaciones de fabricantes funcionales (9/9)
- ✅ Auto-scan exitoso en 95% de intentos (Aisino)
- ✅ Polling estable sin fallos por 10 minutos continuos
- ✅ Detección USB <5 segundos en 95% de casos

**Seguridad**:
- ✅ 100% de inyecciones con KEK cifran correctamente
- ✅ 100% de validaciones KCV exitosas
- ✅ 0% de llaves transmitidas en claro (excepto KEK en exportación inicial)
- ✅ 100% de códigos de respuesta correctos según tabla

### 7.2 Umbrales de Rendimiento

| Métrica | Umbral Mínimo | Objetivo | Medición |
|---------|---------------|----------|----------|
| Tiempo de inyección (1 llave) | <10 seg | <5 seg | Timer manual |
| Tiempo de inyección (10 llaves) | <2 min | <1 min | Timer manual |
| Detección de cable | <10 seg | <3 seg | Logs timestamp |
| Auto-scan (Aisino) | <30 seg | <15 seg | Logs timestamp |
| Re-conexión post-desconexión | <15 seg | <5 seg | Timer manual |
| Inicio de polling | <5 seg | <2 seg | Logs timestamp |
| Tiempo de ceremonia (2 custodios) | <30 seg | <15 seg | Timer manual |
| Tiempo de respuesta UI | <500ms | <200ms | Percepción visual |

**Cómo Medir**:
1. **Inyección**: Desde "Iniciar Inyección" hasta "Completada exitosamente"
2. **Detección Cable**: Desde conexión física hasta indicador "CONECTADO"
3. **Auto-scan**: Desde inicio de listening hasta puerto/baud detectado en logs
4. **Re-conexión**: Desde reconexión física hasta polling activo
5. **UI**: Desde tap hasta respuesta visual

### 7.3 Cobertura Mínima

**Por Módulo**:
- Autenticación: 100% de casos (6/6)
- Ceremonia: 90% de casos (10/11)
- Gestión Llaves: 80% de casos (7/9)
- Gestión Perfiles: 90% de casos (11/12)
- Inyección: 100% de casos críticos (12/15 críticos)
- Comunicación: 100% de casos críticos (9/13 críticos)
- Recepción: 100% de casos críticos (10/14 críticos)
- Edge Cases: 80% de casos (12/15)

**Por Prioridad**:
- Alta: 100% de casos ejecutados
- Media: 90% de casos ejecutados
- Baja: 70% de casos ejecutados

**Cobertura de Códigos de Respuesta**:
- Códigos críticos (00, 08, 09, 0E, 12, 1C): 100%
- Códigos de error (01-15): 80%
- Códigos edge (07, otros): 50%

### 7.4 Criterios de Bloqueo (Blocker)

**Release NO puede proceder si**:
- ❌ Cualquier caso de prioridad ALTA falla
- ❌ >20% de casos de prioridad MEDIA fallan
- ❌ Cualquier combinación de fabricantes falla
- ❌ KEK no se exporta/cifra correctamente
- ❌ KCV no se valida correctamente
- ❌ Cualquier llave se transmite en claro (excepto KEK export)
- ❌ Datos de llaves se exponen en logs en producción
- ❌ Código 07 (Tamper) no detiene operación

**Release puede proceder con warnings si**:
- ⚠️ <10% de casos de prioridad BAJA fallan
- ⚠️ Edge cases específicos no críticos fallan
- ⚠️ UI tiene delays <500ms
- ⚠️ Auto-scan toma >15s pero funciona

---

## 8. REPORTE DE DEFECTOS

### 8.1 Template de Reporte

**Información Obligatoria**:

```
ID Defecto: BUG-XXX
Título: [Breve descripción del problema]

Módulo: [AUTH/CER/KEY/PRF/INJ/COM/REC/EDGE]
Severidad: [Crítica/Alta/Media/Baja]
Prioridad: [P0/P1/P2/P3]

Ambiente:
- Build Number: [ej: 1.0.5-debug]
- Dispositivo Injector: [Fabricante + Modelo]
- Dispositivo SubPOS: [Fabricante + Modelo]
- Android Version: [ej: 10]
- Cable: [Tipo, Longitud]

Precondiciones:
- [Estado inicial requerido]
- [Configuración específica]

Pasos para Reproducir:
1. [Paso 1]
2. [Paso 2]
3. ...

Resultado Actual:
[Qué sucede]

Resultado Esperado:
[Qué debería suceder]

Evidencia:
- Logs: [Adjunto logs_bug_XXX.txt]
- Screenshot: [Adjunto screenshot_bug_XXX.png]
- Video: [Opcional]

Frecuencia:
[Siempre / A veces (X%) / Rara vez]

Workaround:
[Si existe forma de evitar/solucionar temporalmente]

Notas Adicionales:
[Cualquier observación relevante]
```

### 8.2 Severidad y Prioridad

**Severidad** (Impacto):

| Nivel | Descripción | Ejemplos |
|-------|-------------|----------|
| **Crítica** | Bloquea funcionalidad core, pérdida de datos, seguridad comprometida | - Llaves en claro visible<br>- App crash en inyección<br>- Tamper no detectado |
| **Alta** | Funcionalidad principal afectada, workaround existe pero complejo | - KEK no se exporta<br>- KCV no valida<br>- Auto-scan siempre falla |
| **Media** | Funcionalidad afectada, workaround simple existe | - UI delay >500ms<br>- Log incorrecto<br>- Mensaje de error confuso |
| **Baja** | Cosmético, no afecta funcionalidad | - Alineación de texto<br>- Color incorrecto<br>- Typo en mensaje |

**Prioridad** (Urgencia):

| Nivel | Cuándo Arreglar | Ejemplos |
|-------|-----------------|----------|
| **P0** | Inmediatamente (hotfix) | Severidad Crítica en producción |
| **P1** | Próximo sprint | Severidad Alta |
| **P2** | Backlog prioritario | Severidad Media |
| **P3** | Backlog normal | Severidad Baja |

### 8.3 Ejemplos de Defectos

**Ejemplo 1 - Crítico**:
```
ID: BUG-001
Título: Llaves operacionales transmitidas sin cifrado cuando KEK está marcada

Módulo: INJ
Severidad: Crítica
Prioridad: P0

Ambiente:
- Build: 1.0.5-debug
- Injector: Aisino A8
- SubPOS: Newpos N910
- Android: 10
- Cable: USB-C 1m

Precondiciones:
- KEK generada y marcada como ACTIVE
- Perfil configurado con KEK seleccionada
- Llaves operacionales en perfil

Pasos:
1. Iniciar inyección de perfil con KEK
2. Confirmar exportación de KEK
3. Observar logs durante inyección de llaves operacionales

Resultado Actual:
Logs muestran llaves operacionales en claro: "KEY_DATA: 0123456789ABCDEF..."

Resultado Esperado:
Llaves operacionales deben estar cifradas, logs deben mostrar datos cifrados

Evidencia:
- logs_bug_001.txt (líneas 145-150)
- screenshot_bug_001.png

Frecuencia: Siempre (100%)

Workaround: Ninguno

Notas: CRÍTICO - Compromete seguridad de transmisión
```

**Ejemplo 2 - Alta**:
```
ID: BUG-015
Título: KCV recibido no se valida contra esperado

Módulo: INJ
Severidad: Alta
Prioridad: P1

Ambiente:
- Build: 1.0.5-debug
- Injector: Urovo i9000s
- SubPOS: Urovo i9000s
- Android: 9

Precondiciones:
- Perfil con 3 llaves configuradas
- Cable conectado

Pasos:
1. Iniciar inyección
2. Observar logs y resultado

Resultado Actual:
Inyección reporta éxito aunque KCV no coincide
Logs: "Esperado: A1B2C3, Recibido: D4E5F6" pero estado es SUCCESS

Resultado Esperado:
Error: "KCV no coincide", inyección debe fallar

Evidencia:
- logs_bug_015.txt

Frecuencia: Siempre

Workaround: Validación manual de KCV post-inyección
```

---

## 9. GUÍA DE EJECUCIÓN DE PRUEBAS

### 9.1 Orden Recomendado de Ejecución

**Día 1 - Funcionalidad Core** (4-6 horas):
1. AUTH (30 min): Casos AUTH-001 a AUTH-006
2. CER (1 hora): Casos CER-001 a CER-011
3. KEY (45 min): Casos KEY-001 a KEY-009
4. PRF (1 hora): Casos PRF-001 a PRF-012

**Día 2 - Comunicación** (4-6 horas):
1. COM (1.5 horas): Casos COM-001 a COM-013
2. Matriz de Compatibilidad (2 horas): 9 combinaciones
3. Validación de Formato (1 hora): FORM-001 a FORM-008

**Día 3 - Inyección y Recepción** (6-8 horas):
1. INJ (3 horas): Casos INJ-001 a INJ-015
2. REC (2 horas): Casos REC-001 a REC-014
3. Códigos de Respuesta (1 hora): Provocar códigos 01-1C

**Día 4 - Edge Cases y Errores** (4-6 horas):
1. EDGE (2 horas): Casos EDGE-001 a EDGE-015
2. ERR (2 horas): Casos ERR-001 a ERR-015

**Día 5 - Re-testing y Reporte** (4 horas):
1. Re-ejecutar casos fallidos (2 horas)
2. Validación final de criterios (1 hora)
3. Preparación de reporte (1 hora)

### 9.2 Configuración de Sesión de Pruebas

**Setup Inicial** (20 min):
```
1. Cargar dispositivos (100%)
2. Instalar apps (build específico)
3. Limpiar BD Injector: 
   - Adb: adb shell rm /data/data/com.vigatec.injector/databases/*
   - O reinstalar app
4. Limpiar PED SubPOS:
   - Abrir app → Llaves → Eliminar Todas
5. Habilitar logs DEBUG:
   - Configuración → Logs → Nivel DEBUG
6. Preparar hoja de registro:
   - Columnas: ID Caso | Descripción | Resultado | Evidencia | Notas
7. Iniciar captura de logs:
   - ADB: adb logcat > session_logs.txt
```

**Durante Pruebas**:
- Marcar cada caso ejecutado en checklist
- Capturar screenshot de cada error
- Anotar timestamp de cada evento importante
- Registrar observaciones inmediatamente

**Cierre de Sesión** (15 min):
- Detener captura de logs
- Exportar logs: adb pull /sdcard/logs/
- Organizar screenshots en carpetas por módulo
- Actualizar hoja de registro
- Respaldar datos

### 9.3 Tips para QA

**Eficiencia**:
- Ejecutar casos de mismo módulo consecutivamente (reduce setup)
- Preparar múltiples perfiles de prueba al inicio
- Generar llaves necesarias en batch (ceremonia múltiple)
- Usar mismos dispositivos para casos relacionados

**Detección de Errores**:
- Siempre revisar logs, no solo UI
- Validar KCV manualmente en casos críticos
- Probar edge cases al final (pueden dejar sistema inestable)
- Reproducir errores 2-3 veces antes de reportar

**Seguridad**:
- Nunca usar llaves de producción en QA
- Limpiar dispositivos tras cada sesión
- No compartir logs que contengan llaves (en claro)
- Verificar que ambiente es TEST/DEV

**Comunicación con Desarrollo**:
- Reportar defectos inmediatamente (blocker)
- Incluir siempre logs completos
- Describir pasos en detalle (reproducibilidad)
- Proponer solución si es evidente

---

## 10. RESUMEN EJECUTIVO

### 10.1 Alcance del Plan

Este plan cubre **108 casos de prueba funcionales** distribuidos en:
- 6 casos de Autenticación
- 11 casos de Ceremonia de Llaves
- 9 casos de Gestión de Llaves
- 12 casos de Gestión de Perfiles
- 15 casos de Inyección de Llaves
- 13 casos de Comunicación USB
- 14 casos de Recepción en SubPOS
- 15 casos Edge y Límites
- 15 casos de Errores y Recuperación

**Además**:
- 9 combinaciones de compatibilidad de fabricantes
- 29 códigos de respuesta Futurex validados
- 8 validaciones de formato de mensajería
- 3 checklists operativos
- 15 escenarios de error provocado

### 10.2 Tiempo Estimado

**Ejecución Completa**: 5 días (40 horas)
**Ejecución Crítica** (solo casos Alta prioridad): 2 días (16 horas)
**Ejecución Smoke Test** (funcionalidad básica): 4 horas

### 10.3 Recursos Necesarios

- **Personal**: 1-2 QA engineers
- **Dispositivos**: 6-12 dispositivos Android (según alcance de compatibilidad)
- **Cables**: 3-5 cables USB de calidad
- **Herramientas**: ADB, log viewer, captura de pantalla
- **Espacio**: Área de trabajo organizada para múltiples dispositivos

### 10.4 Entregables

1. **Reporte de Ejecución**:
   - Casos ejecutados vs planificados
   - Tasa de éxito por módulo
   - Defectos encontrados con severidad

2. **Evidencia**:
   - Logs completos por sesión
   - Screenshots de errores
   - Videos de casos complejos (opcional)

3. **Matriz de Resultados**:
   - Tabla con todos los casos y su estado (✅/❌)
   - Métricas de rendimiento
   - Recomendaciones

4. **Lista de Defectos**:
   - Defectos críticos/altos con detalle completo
   - Priorización para desarrollo
   - Sugerencias de fix

---

## ANEXOS

### A. Valores de Prueba

**Componentes para Ceremonia**:
```
3DES (16 bytes = 32 hex chars):
Componente 1: 0123456789ABCDEF0123456789ABCDEF
Componente 2: FEDCBA9876543210FEDCBA9876543210

AES-256 (32 bytes = 64 hex chars):
Componente 1: 0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
Componente 2: FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
```

**KSN para DUKPT**:
```
Válidos:
- F876543210000000000A
- FFFFFFFFFF0000000001
- 00000000000000000000

Inválidos:
- ABC (muy corto)
- F876543210000000000G (char no hex)
```

**Configuración de Perfiles de Prueba**:
```
Perfil Básico:
- Nombre: "QA Test Básico"
- Llave 1: PIN, slot 10, KCV: [generado]
- Llave 2: MAC, slot 30, KCV: [generado]
- Llave 3: DATA, slot 50, KCV: [generado]
- Sin KEK

Perfil con KEK:
- Nombre: "QA Test con KEK"
- KEK: slot 0, KCV: [generado]
- Llave 1: PIN (cifrado), slot 11
- Llave 2: MAC (cifrado), slot 31
- Usar KEK: Sí

Perfil DUKPT:
- Nombre: "QA Test DUKPT"
- Llave 1: DUKPT_TDES, slot 70, KSN: F876543210000000000A
```

### B. Comandos ADB Útiles

```bash
# Limpiar base de datos Injector
adb shell rm -rf /data/data/com.vigatec.injector/databases/

# Capturar logs en tiempo real
adb logcat | grep -E "KeyInjection|Futurex|CommLog"

# Exportar logs a archivo
adb logcat -d > qa_session_$(date +%Y%m%d_%H%M%S).txt

# Ver permisos USB
adb shell dumpsys usb

# Reiniciar app
adb shell am force-stop com.vigatec.injector
adb shell am start -n com.vigatec.injector/.MainActivity
```

### C. Glosario de Términos QA

| Término | Definición |
|---------|------------|
| **Smoke Test** | Pruebas básicas para verificar funcionalidad mínima |
| **Happy Path** | Flujo exitoso sin errores |
| **Negative Test** | Prueba de casos de error intencionados |
| **Edge Case** | Caso en límites o extremos del sistema |
| **Blocker** | Defecto que impide continuar pruebas o release |
| **Regression** | Prueba de funcionalidad previamente validada |
| **Idempotencia** | Capacidad de ejecutar múltiples veces con mismo resultado |

---

**Fin del Plan de Pruebas QA**

**Próximos Pasos**:
1. Revisión del plan con equipo de desarrollo
2. Asignación de recursos y dispositivos
3. Setup de ambiente de pruebas
4. Ejecución según cronograma
5. Reporte de resultados

**Contacto**:
Para consultas sobre este plan, contactar al equipo de QA o referirse a la documentación técnica (Partes 1-8).

---

**Versión del Plan**: 1.0  
**Última Actualización**: Octubre 2025  
**Estado**: Aprobado para Ejecución


