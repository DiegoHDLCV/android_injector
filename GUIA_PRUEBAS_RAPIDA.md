# Guía Rápida de Pruebas - Comunicación USB

## 🎯 Objetivo
Validar la comunicación USB entre Injector y App de forma práctica y rápida.

---

## 📋 Pre-requisitos

### Hardware Necesario
- ✅ 1x Dispositivo Aisino (para Injector - MasterPOS)
- ✅ 1x Dispositivo Aisino o Newpos (para App - SubPOS)
- ✅ 1x Cable USB OTG de calidad
- ✅ Carga de batería > 50% en ambos dispositivos

### Software
- ✅ Aplicación **Injector** instalada en MasterPOS
- ✅ Aplicación **App** instalada en SubPOS
- ✅ Acceso a `adb logcat` para ver logs (opcional pero recomendado)

---

## 🧪 NIVEL 1: Pruebas Básicas (15 minutos)

### TEST 1: Verificar Inicialización de SDKs

**Objetivo:** Confirmar que los SDKs se inicializan correctamente.

**Pasos:**

1. **En MasterPOS (Injector):**
   ```bash
   # Limpiar logs
   adb -s <SERIAL_MASTER> logcat -c

   # Ver logs en tiempo real
   adb -s <SERIAL_MASTER> logcat | grep -E "(KeyInjectionViewModel|AisinoCommunicationManager|CommSDKManager)"
   ```

2. Iniciar la aplicación **Injector**

3. **Buscar en logs:**
   ```
   ✅ "=== INICIALIZANDO KEYINJECTIONVIEWMODEL FUTUREX ==="
   ✅ "Inicializando AisinoCommunicationManager..."
   ✅ "SDK de Vanstone inicializado correctamente"
   ✅ "AutoScan: seleccionando puerto X baud Y"
   ✅ "AisinoCommunicationManager inicializado correctamente"
   ```

4. **En SubPOS (App):**
   ```bash
   # En otra terminal
   adb -s <SERIAL_SUBPOS> logcat -c
   adb -s <SERIAL_SUBPOS> logcat | grep -E "(MainViewModel|AisinoCommunicationManager|NewposCommunicationManager)"
   ```

5. Iniciar la aplicación **App**

6. **Buscar en logs:**
   ```
   ✅ "=== INICIALIZANDO MAINVIEWMODEL ==="
   ✅ "Manager seleccionado: AISINO" (o NEWPOS)
   ✅ "Protocolo seleccionado: FUTUREX"
   ✅ "SDK de Vanstone inicializado correctamente" (Aisino)
   ✅ "MainViewModel inicializado completamente"
   ```

**✅ Resultado Esperado:** Ambas aplicaciones inician sin errores en logs.

**❌ Si falla:** Verificar permisos de USB en AndroidManifest.xml y estado de SDKs nativos.

---

### TEST 2: Verificar Auto-Scan de Puertos (Solo Aisino)

**Objetivo:** Confirmar que el auto-scan detecta el puerto correcto.

**Pasos:**

1. En logs de **MasterPOS**, buscar:
   ```
   "AutoScan: probando puertos [0, 1] con baudios [9600, 115200]"
   "AutoScan: puerto 0 baud 9600 read=0 data="
   "AutoScan: puerto 0 baud 115200 read=0 data="
   ...
   "AutoScan: seleccionando puerto X baud Y (datos recibidos)"
   ```

2. Anotar puerto seleccionado: `Puerto: ___ Baudrate: ___`

3. Repetir para **SubPOS** (si es Aisino)

**✅ Resultado Esperado:** Puerto y baudrate detectados correctamente.

**📝 Nota:** En Newpos no hay auto-scan, usa configuración fija.

---

### TEST 3: Conexión Física USB

**Objetivo:** Verificar que los dispositivos se detectan al conectar el cable.

**Pasos:**

1. **Conectar** el cable USB entre MasterPOS y SubPOS
   - MasterPOS: Puerto USB OTG
   - SubPOS: Puerto USB OTG

2. En **SubPOS (App):**
   - Ir a la pantalla principal
   - Presionar botón **"Iniciar Escucha"** o similar

3. **Buscar en logs (SubPOS):**
   ```
   ✅ "startListeningInternal: Lanzando job de escucha"
   ✅ "Intento de conexión #1 de 3"
   ✅ "open() intento #1 => 0"  (0 = éxito)
   ✅ "¡Puerto abierto exitosamente en intento #1!"
   ✅ "Estado de conexión cambiado a LISTENING"
   ```

4. **Verificar en UI:**
   - Estado debería cambiar a **"Escuchando"** o similar
   - Indicador de conexión activo

**✅ Resultado Esperado:** Puerto abierto con código `0` en primer o segundo intento.

**❌ Si falla con código -1, -2, -3, -4:**
- `-1`: Puerto no disponible → Verificar cable
- `-2`: Permisos → Verificar permisos USB
- `-3`: Puerto no encontrado → Revisar configuración de puertos
- `-4`: Puerto ya abierto → Cerrar y reintentar

---

### TEST 4: Polling - Detección de Conexión

**Objetivo:** Verificar que el MasterPOS detecta al SubPOS mediante polling.

**Pasos:**

1. En **MasterPOS (Injector):**
   - Navegar a **Dashboard** o pantalla principal
   - Observar estado de conexión (debería mostrar "Buscando SubPOS..." o similar)

2. **Buscar en logs (MasterPOS):**
   ```
   ✅ "Iniciando polling desde MasterPOS…"
   ✅ "📤 Enviado POLL (N bytes, write=N): 0x02 0x30 0x31 0x30 0x30..."
   ✅ "TX POLL (N B, write=N): 0x02..."
   ```

3. **Buscar en logs (SubPOS):**
   ```
   ✅ "📥 Datos recibidos: N bytes - 0x02 0x30 0x31..."
   ✅ "RX N B: 0x02..."
   ✅ "📥 POLL (0100) recibido desde MasterPOS"
   ✅ "📤 Respuesta POLL enviada (N bytes, write=N): 0x02 0x30 0x31 0x31 0x30..."
   ```

4. **Buscar en logs (MasterPOS):**
   ```
   ✅ "📥 Respuesta POLL (0110) recibida"
   ✅ "✅ Respuesta POLL recibida - SubPOS conectado"
   ✅ "RX ACK de POLL (0110) - Conectado"
   ```

5. **Verificar en UI (MasterPOS):**
   - Estado cambia a **"SubPOS Conectado"** o ícono verde

**✅ Resultado Esperado:** Conexión detectada en < 5 segundos.

**⏱️ Tiempo de Ciclo:** El polling se repite cada 2 segundos.

---

### TEST 5: Desconexión y Reconexión

**Objetivo:** Validar detección de desconexión y reconexión automática.

**Pasos:**

1. Con polling activo y SubPOS conectado:
   - **Desconectar físicamente** el cable USB

2. **Buscar en logs (MasterPOS):**
   ```
   ✅ "⚠️ Timeout esperando respuesta POLL - SubPOS no responde"
   ✅ "Timeout esperando ACK de POLL (0110)"
   ```

3. **Verificar en UI (MasterPOS):**
   - Estado cambia a **"Desconectado"** o ícono rojo
   - Debería ocurrir en < 7 segundos

4. **Reconectar cable USB**

5. **Buscar en logs:**
   ```
   ✅ (SubPOS) "open() intento #1 => 0"
   ✅ (SubPOS) "📥 POLL (0100) recibido"
   ✅ (MasterPOS) "✅ Respuesta POLL recibida - SubPOS conectado"
   ```

6. **Verificar en UI:**
   - Estado vuelve a **"Conectado"**

**✅ Resultado Esperado:**
- Desconexión detectada en < 7s
- Reconexión en < 10s

---

## 🧪 NIVEL 2: Pruebas de Inyección (30 minutos)

### TEST 6: Preparar Datos de Prueba

**Objetivo:** Crear llaves y perfil de prueba en la base de datos.

**Opción A: Desde la UI (Recomendado)**

1. En **Injector**:
   - Ir a **"Key Vault"** o **"Bóveda de Llaves"**
   - Crear llaves manualmente:

   | Campo | Valor |
   |-------|-------|
   | KCV | `AABB` |
   | Key Data | `AABBCCDDEEFF00112233445566778899` (32 caracteres = 16 bytes) |
   | Key Type | `PIN` |

   - Crear más llaves:
     - KCV: `CCDD`, Data: `CCDD0011223344556677889900AABBCC`, Type: `MAC`
     - KCV: `EEFF`, Data: `EEFF00112233445566778899AABBCCDDEEFF0011223344556677889900AABBCC` (64 chars = 32 bytes), Type: `DATA`

2. Ir a **"Profiles"** o **"Perfiles"**:
   - Crear nuevo perfil: `Test Profile`
   - Agregar configuraciones de llave:

   | Uso | Tipo | Slot | Llave (KCV) | KSN |
   |-----|------|------|-------------|-----|
   | PIN Device | PIN | 1 | AABB | `00000000000000000000` |
   | MAC Key | MAC | 2 | CCDD | `00000000000000000000` |
   | Data Encryption | DATA | 3 | EEFF | `00000000000000000000` |

**Opción B: Desde ADB (Rápido)**

```bash
# Conectar al dispositivo Injector
adb -s <SERIAL_MASTER> shell

# Acceder a la base de datos (ajustar ruta si es necesario)
su
cd /data/data/com.vigatec.injector/databases/
sqlite3 app_database.db

# Insertar llaves
INSERT INTO injected_keys (kcv, keyData, keyType, keyAlgorithm, status, timestamp)
VALUES ('AABB', 'AABBCCDDEEFF00112233445566778899', 'PIN', 'DES_TRIPLE', 'SUCCESSFUL', datetime('now'));

INSERT INTO injected_keys (kcv, keyData, keyType, keyAlgorithm, status, timestamp)
VALUES ('CCDD', 'CCDD0011223344556677889900AABBCC', 'MAC', 'DES_TRIPLE', 'SUCCESSFUL', datetime('now'));

# Verificar
SELECT * FROM injected_keys;

.exit
exit
```

---

### TEST 7: Inyección de Llave Simple

**Objetivo:** Inyectar una sola llave y validar el proceso completo.

**Pasos:**

1. **Setup:**
   - MasterPOS y SubPOS conectados (polling activo, estado "Conectado")
   - Perfil con 1 llave configurado

2. En **MasterPOS (Injector):**
   - Ir a **"Profiles"**
   - Seleccionar `Test Profile`
   - Presionar botón **"▶️ Inyectar Llaves"**

3. **Aparece Modal de Inyección:**
   - Presionar **"Iniciar Inyección"**

4. **Buscar en logs (MasterPOS):**
   ```
   ✅ "=== INICIANDO PROCESO DE INYECCIÓN FUTUREX ==="
   ✅ "Perfil: Test Profile"
   ✅ "Configuraciones de llave: 1"
   ✅ "Deteniendo polling antes de iniciar inyección..."
   ✅ "Inicializando comunicación serial..."
   ✅ "open() => 0"
   ✅ "Conexión establecida. Iniciando inyección..."

   ✅ "=== PROCESANDO LLAVE 1/1 ==="
   ✅ "Uso: PIN Device"
   ✅ "Slot: 1"
   ✅ "Tipo: PIN"

   ✅ "=== INICIANDO INYECCIÓN DE LLAVE FUTUREX ==="
   ✅ "Llave encontrada en base de datos:"
   ✅ "  - KCV: AABB"
   ✅ "  - Longitud de datos: 16 bytes"

   ✅ "=== VALIDANDO INTEGRIDAD DE LLAVE FUTUREX ==="
   ✅ "✓ Integridad de llave validada"

   ✅ "=== ESTRUCTURA FUTUREX PARA INYECCIÓN DE LLAVE ==="
   ✅ "Comando: 02 (Inyección de llave simétrica)"
   ✅ "Versión: 01"
   ✅ "Slot de llave: 01 (1)"
   ✅ "Tipo de llave: 05 (PIN)"
   ✅ "Checksum de llave: AABB"
   ✅ "Longitud de llave: 010 (16 bytes)"

   ✅ "=== ENVIANDO DATOS FUTUREX ==="
   ✅ "Tamaño de datos: XX bytes"
   ✅ "Datos enviados exitosamente: XX bytes escritos"

   ✅ "=== ESPERANDO RESPUESTA FUTUREX ==="
   ✅ "Respuesta recibida exitosamente:"
   ✅ "  - Bytes leídos: XX"
   ```

5. **Buscar en logs (SubPOS):**
   ```
   ✅ "RAW_SERIAL_IN (HEX): 0x02 0x30 0x32..."
   ✅ "=== PROCESANDO DATOS RECIBIDOS ==="
   ✅ "Bytes recibidos: XX"
   ✅ "Parser configurado: FuturexMessageParser"
   ✅ "Mensaje parseado #1: InjectSymmetricKeyCommand(...)"

   ✅ "Procesando mensaje parseado: InjectSymmetricKeyCommand"
   ✅ "Recibido CMD: Inyectar Llave"
   ✅ "handleFuturexInjectKey: Iniciando proceso para slot 1 | Tipo: WORKING_PIN_KEY"

   ✅ "Manejando EncryptionType 00: Carga en Claro"
   ✅ "Llamando a 'writeKeyPlain'..."
   ✅ "Inyección en slot 1 procesada exitosamente"

   ✅ "Resultado de inyección para slot 1 registrado en la BD como: SUCCESSFUL"
   ✅ "RAW_SERIAL_OUT (HEX): 0x02 0x30 0x32 0x30 0x30..." (Respuesta)
   ```

6. **Buscar en logs (MasterPOS - continuación):**
   ```
   ✅ "=== PROCESANDO RESPUESTA FUTUREX ==="
   ✅ "Respuesta parseada como InjectSymmetricKeyResponse:"
   ✅ "  - Código de respuesta: 00"
   ✅ "  - Checksum de llave: AABB"
   ✅ "✓ Inyección exitosa para PIN Device"

   ✅ "=== INYECCIÓN FUTUREX COMPLETADA EXITOSAMENTE ==="
   ✅ "¡Inyección completada exitosamente!"
   ✅ "Reiniciando polling después de la inyección..."
   ```

7. **Verificar en UI (MasterPOS):**
   - Modal muestra:
     - Progreso: 100%
     - Log: "✓ PIN Device: Inyectada exitosamente"
     - Estado: "¡Inyección completada exitosamente!"
   - Botón "Cerrar" disponible

8. **Verificar en Base de Datos (SubPOS):**
   ```bash
   adb -s <SERIAL_SUBPOS> shell
   su
   sqlite3 /data/data/com.vigatec.android_injector/databases/app_database.db
   SELECT * FROM injected_keys WHERE keySlot = 1;
   # Debería mostrar la llave con status = 'SUCCESSFUL'
   .exit
   ```

**✅ Resultado Esperado:**
- Llave inyectada en slot 1 del PED
- KCV coincide: `AABB`
- Registro en base de datos con status `SUCCESSFUL`
- Tiempo total: < 5 segundos

**❌ Si falla:**
- Verificar que el PED esté disponible (pedController != null)
- Revisar código de respuesta en logs
- Si código != "00", buscar en tabla de errores Futurex

---

### TEST 8: Inyección de Perfil Completo (3 llaves)

**Objetivo:** Validar inyección secuencial de múltiples llaves.

**Pasos:**

1. Configurar perfil con 3 llaves (ver TEST 6)

2. Iniciar inyección

3. **Observar progreso:**
   - Progreso: 0% → 33% → 66% → 100%
   - Log:
     ```
     "Inyectando llave 1/3: PIN Device"
     "✓ PIN Device: Inyectada exitosamente"
     "Inyectando llave 2/3: MAC Key"
     "✓ MAC Key: Inyectada exitosamente"
     "Inyectando llave 3/3: Data Encryption"
     "✓ Data Encryption: Inyectada exitosamente"
     ```

4. **Verificar pausa entre inyecciones:**
   - Debería haber ~500ms entre cada inyección

5. **Verificar en base de datos:**
   ```sql
   SELECT keySlot, keyType, kcv, status FROM injected_keys
   WHERE keySlot IN (1, 2, 3)
   ORDER BY keySlot;

   -- Resultado esperado:
   -- 1 | WORKING_PIN_KEY | AABB | SUCCESSFUL
   -- 2 | WORKING_MAC_KEY | CCDD | SUCCESSFUL
   -- 3 | WORKING_DATA_ENCRYPTION_KEY | EEFF | SUCCESSFUL
   ```

**✅ Resultado Esperado:**
- 3/3 llaves inyectadas exitosamente
- Tiempo total: < 10 segundos

---

### TEST 9: Inyección de Llave DUKPT con KSN

**Objetivo:** Validar inyección de llave DUKPT con KSN.

**Pasos:**

1. **Crear llave DUKPT:**
   - KCV: `1122`
   - Data: `11223344556677889900AABBCCDDEEFF` (32 chars = 16 bytes)
   - Type: `DUKPT_TDES`

2. **Configurar en perfil:**
   - Uso: `DUKPT Key`
   - Tipo: `DUKPT_TDES`
   - Slot: `4`
   - Llave: `1122`
   - **KSN: `F876543210000000000A`** (¡Importante! 20 caracteres hex)

3. Iniciar inyección

4. **Buscar en logs (MasterPOS):**
   ```
   ✅ "Tipo de llave: 08 (DUKPT_TDES)"
   ✅ "KSN: F876543210000000000A (20 caracteres)"
   ✅ "Usando KSN proporcionado en el perfil: F876543210000000000A"
   ```

5. **Verificar comando enviado:**
   - Campo KSN debe contener: `F876543210000000000A`

6. **Buscar en logs (SubPOS):**
   ```
   ✅ "Llamando a 'writeDukptInitialKey'..."
   ✅ "KSN: F876543210000000000A"
   ```

**✅ Resultado Esperado:**
- Llave DUKPT inyectada con KSN correcto
- Tipo mapeado a `08` (DUKPT 3DES BDK)

---

## 🧪 NIVEL 3: Pruebas de Robustez (30 minutos)

### TEST 10: Cambio de SubPOS

**Objetivo:** Validar cambio de dispositivo SubPOS.

**Pasos:**

1. **Con SubPOS #1 conectado:**
   - Verificar polling activo y estado "Conectado"

2. **Inyectar perfil completo en SubPOS #1**
   - Anotar llaves inyectadas

3. **Desconectar SubPOS #1**
   - Verificar desconexión detectada en < 7s

4. **Conectar SubPOS #2**
   - Verificar reconexión en < 10s

5. **Inyectar DIFERENTE perfil en SubPOS #2**

6. **Verificar independencia:**
   - SubPOS #1 debe tener solo sus llaves
   - SubPOS #2 debe tener solo sus llaves

**✅ Resultado Esperado:**
- Cambio transparente
- Sin interferencia entre dispositivos

---

### TEST 11: Reconexión Tras Desconexión Durante Inyección

**Objetivo:** Validar manejo de error cuando se desconecta cable durante inyección.

**Pasos:**

1. Configurar perfil con 4 llaves

2. Iniciar inyección

3. **Durante la llave 2:** Desconectar cable USB

4. **Buscar en logs:**
   ```
   ✅ "Error durante la inyección de llaves"
   ✅ "Timeout o error al leer respuesta"
   ✅ "Estado: INJECTING → ERROR"
   ✅ "Cerrando comunicación..."
   ✅ "Reiniciando polling después de la inyección..."
   ```

5. **Verificar en UI:**
   - Modal muestra error
   - Log muestra qué llave falló

6. **Reconectar cable**

7. **Reintentar inyección completa**

**✅ Resultado Esperado:**
- Error manejado sin crash
- Estado limpio para reintento
- Segunda inyección exitosa (4/4 llaves)

---

### TEST 12: Múltiples Ciclos de Inyección

**Objetivo:** Validar estabilidad con inyecciones repetidas.

**Pasos:**

1. Repetir 10 veces:
   - Eliminar todas las llaves del PED (comando 05)
   - Inyectar perfil completo (3 llaves)
   - Verificar inyección exitosa

2. **Anotar resultados:**
   - Ciclo 1: ✅ 3/3
   - Ciclo 2: ✅ 3/3
   - ...
   - Ciclo 10: ✅ 3/3

3. **Verificar tiempos:**
   - Tiempo por ciclo debería ser constante (~8-10s)

**✅ Resultado Esperado:**
- 10/10 ciclos exitosos
- Sin degradación de performance
- Sin memory leaks

---

## 📊 Checklist de Validación

### Comunicación Serial
- [ ] SDK inicializado en MasterPOS
- [ ] SDK inicializado en SubPOS
- [ ] Auto-scan detecta puerto correcto (Aisino)
- [ ] Puerto abierto con código `0`
- [ ] Puerto cerrado correctamente

### Polling
- [ ] MasterPOS envía POLL cada 2s
- [ ] SubPOS recibe y responde POLL
- [ ] Conexión detectada en < 5s
- [ ] Desconexión detectada en < 7s
- [ ] Reconexión automática funcional

### Inyección
- [ ] Llave simple inyectada exitosamente
- [ ] Perfil completo (3 llaves) inyectado
- [ ] Llave DUKPT con KSN inyectada
- [ ] KCVs validados correctamente
- [ ] Registros en base de datos

### Robustez
- [ ] Cambio de SubPOS funcional
- [ ] Reconexión tras error
- [ ] 10 ciclos de inyección exitosos
- [ ] Sin memory leaks
- [ ] Sin crashes

---

## 🐛 Troubleshooting Rápido

### Problema: Puerto no abre (código -1, -2, -3, -4)

**Soluciones:**
```bash
# 1. Verificar permisos USB
adb shell pm grant com.vigatec.injector android.permission.USB_PERMISSION
adb shell pm grant com.vigatec.android_injector android.permission.USB_PERMISSION

# 2. Verificar cable USB
# - Usar cable OTG de calidad
# - Probar cable diferente

# 3. Reiniciar aplicaciones
adb shell am force-stop com.vigatec.injector
adb shell am force-stop com.vigatec.android_injector

# 4. Limpiar estado USB
adb shell su -c "killall usb"
```

---

### Problema: Polling no detecta conexión

**Verificaciones:**
```bash
# 1. Verificar que SubPOS está en modo listening
# Buscar en logs: "Estado de conexión cambiado a LISTENING"

# 2. Verificar que MasterPOS está enviando POLL
# Buscar en logs: "📤 Enviado POLL"

# 3. Verificar que SubPOS recibe datos
# Buscar en logs: "📥 Datos recibidos: N bytes"

# 4. Verificar parser
# Buscar en logs: "Mensaje parseado: LegacyMessage(command=0100)"
```

---

### Problema: Inyección falla con código de error

**Códigos comunes:**
- `0x05 - Device is busy`: PED ocupado → Esperar y reintentar
- `0x0C - Invalid key slot`: Slot fuera de rango → Verificar slot (1-99)
- `0x10 - Invalid key type`: Tipo incorrecto → Verificar mapeo de tipos
- `0x12 - Invalid key checksum`: KCV incorrecto → Verificar datos de llave
- `0x15 - Invalid key length`: Longitud incorrecta → Verificar 16/32/48 bytes

**Solución general:**
```bash
# Ver logs detallados
adb logcat | grep -E "(FUTUREX|Inyección|handleFuturex)"

# Verificar datos de llave
sqlite3 /data/data/.../app_database.db "SELECT * FROM injected_keys WHERE kcv='AABB';"
```

---

### Problema: LRC inválido (Bad LRC)

**Causas:**
- Datos corruptos durante transmisión
- Interferencia electromagnética
- Cable de mala calidad

**Solución:**
```bash
# 1. Reemplazar cable USB
# 2. Alejar de fuentes de interferencia
# 3. Reintentar inyección
```

---

## 📈 Logs Útiles para Debug

### Ver logs en tiempo real (2 terminales)

**Terminal 1 - MasterPOS:**
```bash
adb -s <SERIAL_MASTER> logcat -v time | grep -E "(KeyInjection|Polling|FUTUREX|TX|RX|AutoScan)"
```

**Terminal 2 - SubPOS:**
```bash
adb -s <SERIAL_SUBPOS> logcat -v time | grep -E "(MainViewModel|FUTUREX|TX|RX|handleFuturex|PED)"
```

### Guardar logs completos

```bash
# MasterPOS
adb -s <SERIAL_MASTER> logcat -d > logs_master_$(date +%Y%m%d_%H%M%S).txt

# SubPOS
adb -s <SERIAL_SUBPOS> logcat -d > logs_subpos_$(date +%Y%m%d_%H%M%S).txt
```

---

## ✅ Siguiente Paso

Una vez completados los tests básicos (NIVEL 1), continuar con:
- **NIVEL 2:** Inyección de llaves
- **NIVEL 3:** Pruebas de robustez
- **Pruebas Automatizadas:** Ver sección en `PLAN_PRUEBAS_USB.md`

---

**¡Buena suerte con las pruebas! 🚀**
