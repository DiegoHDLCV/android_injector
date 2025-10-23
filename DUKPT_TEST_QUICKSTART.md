# Guía Rápida: Prueba DUKPT AES-128

## ⚡ Inicio Rápido (5 minutos)

Esta guía te permite hacer la prueba de inyección DUKPT en 5 pasos simples.

---

## 📋 Requisitos Previos

- ✅ Aplicación **Injector** compilada y instalada
- ✅ Aplicación **KeyReceiver** compilada e instalada
- ✅ Cable USB conectado entre Injector y KeyReceiver
- ✅ Terminal con Python 3 disponible

---

## 🚀 Pasos de Prueba

### Paso 1: Generar Llaves y Perfil (Terminal)

```bash
cd /Users/diegoherreradelacalle/StudioProjects/android_injector

# Generar archivo de llaves para importación
python3 import_dukpt_test_keys.py

# Generar archivo de perfil para importación
python3 import_dukpt_profile.py
```

**Archivos generados:**
- `test_keys_dukpt.json` - Llaves DUKPT para importar
- `dukpt_test_profile.json` - Perfil DUKPT simple
- `dukpt_multikey_profile.json` - Perfil con 3 algoritmos

---

### Paso 2: Importar Llaves en Injector

1. **Abre la app Injector**
2. **Navega a**: `Key Vault` → `Import Keys`
3. **Selecciona**: `test_keys_dukpt.json`
4. **Verifica**:
   - ✅ Se importan 3 llaves DUKPT IPEK
   - ✅ Los KCVs se ven en la lista

**KCVs que deberías ver:**
- AES-128: `072043`
- AES-192: `5D614B`
- AES-256: `AB1234`

---

### Paso 3: Importar Perfil en Injector

1. **En Injector**: `Profiles` → `Import Profile`
2. **Selecciona**: `dukpt_test_profile.json`
3. **Verifica**:
   - ✅ Perfil "DUKPT AES-128 Test" aparece
   - ✅ Contiene 1 configuración de llave
   - ✅ Slot: `01`, KCV: `072043`, KSN: `FFFF9876543210000000`

---

### Paso 4: Conectar Hardware y Escuchar

#### En KeyReceiver:
1. **Conecta cable USB**
2. **Abre la app**
3. **Verifica**:
   - ✅ "Cable USB Conectado" (verde)
4. **Click**: `Iniciar Escucha`
5. **Verifica**:
   - ✅ Estado: "ESCUCHANDO" (verde)

#### En Terminal (monitor de logs):
```bash
# Opcional: monitorear logs en tiempo real
adb logcat | grep -E "MainViewModel|EncryptionType|DUKPT"
```

---

### Paso 5: Enviar Comando de Inyección

#### En Injector:
1. **Ve a**: `Raw Data Listener`
2. **Pega el comando Futurex**:
   ```
   02020101000505040007200000FFFF987654321000000001612101FFF4ED412459F4E727CC3A4895A0336
   ```
3. **Click**: `Send`
4. **Verifica**:
   - ✅ Aparece un ✓ de confirmación
   - ✅ Se recibe respuesta del PED

#### En KeyReceiver:
**Verifica en los logs:**
```
D  === EncryptionType 05: DUKPT IPEK Plaintext ===
D  Inyectando IPEK DUKPT sin cifrado (solo para testing)
D    - Slot: 1
D    - Algoritmo: AES_128
D    - KSN: FFFF9876543210000000
D  === createDukptAESKey (DUKPT Plaintext) ===
D  ✓ DUKPT key created successfully in slot 1
D  ✓ IPEK DUKPT inyectada exitosamente en slot 1
W  ⚠️ ADVERTENCIA: IPEK enviada en plaintext - SOLO USAR PARA TESTING
```

---

## 📊 Estructura del Comando Futurex

El comando enviado tiene esta estructura:

```
Frame:  02 [PAYLOAD] 03 [LRC]
        STX Datos ETX CheckSum

Payload decodificado:
  02      - Comando: Inyección de llave simétrica
  01      - Versión: 01
  01      - KeySlot: 01 (slot 1)
  00      - KtkSlot: 00 (no usado para DUKPT plaintext)
  05      - KeyType: 05 (DUKPT IPEK)
  05      - EncryptionType: 05 (DUKPT Plaintext)
  04      - KeyAlgorithm: 04 (AES-128)
  00      - KeySubType: 00
  0720    - KeyChecksum: 0720 (primeros 4 chars del KCV)
  00      - KtkChecksum: 0000 (no usado)
  FFFF9876543210000000  - KSN: 20 caracteres hex
  016     - KeyLength: 016 hex = 22 decimal = 16 bytes en hex (3 dígitos)
  12101FFF4ED412459F4E727CC3A4895A  - IPEK: 32 caracteres hex = 16 bytes
```

---

## ✅ Checklist de Verificación

- [ ] Llaves importadas en Injector
- [ ] Perfil importado en Injector
- [ ] Cable USB conectado físicamente
- [ ] KeyReceiver abierto y escuchando
- [ ] Comando Futurex copiado correctamente
- [ ] Respuesta recibida en Injector
- [ ] Logs muestran "DUKPT key created successfully"

---

## 🔧 Troubleshooting

### ❌ "Llave con KCV no encontrada"
**Solución**: Asegúrate de haber importado primero las llaves (`test_keys_dukpt.json`)

### ❌ "Error 2012" o "KSN inválido"
**Solución**: Verifica que el comando Futurex esté copiado correctamente (sin espacios)

### ❌ "Cable USB No Detectado"
**Solución**:
- Desconecta y reconecta el cable
- Verifica permisos USB en Android
- Reinicia KeyReceiver

### ❌ "No recibe respuesta"
**Solución**:
- Verifica que KeyReceiver está en estado "ESCUCHANDO"
- Revisa los logs en KeyReceiver
- Intenta enviar nuevamente

---

## 📚 Próximos Pasos

### Si la prueba es exitosa:
1. ✅ **Validar derivación de llaves** - DUKPT debería derivar llaves para cada transacción
2. ✅ **Probar otros algoritmos** - Usa `dukpt_multikey_profile.json` para AES-192 y AES-256
3. ✅ **Implementar TR-31** - Para producción (EncryptionType "04" con KBPK)

### Documentación adicional:
- `generate_dukpt_keys.py` - Generar llaves DUKPT personalizadas
- `DUKPT_GUIDE.md` - Guía técnica completa
- `QUICK_GUIDE_PROFILES.md` - Guía de configuración de perfiles

---

## 📝 Notas Importantes

⚠️ **DUKPT Plaintext (EncryptionType "05") es SOLO para testing**
- La IPEK se envía sin cifrar
- **NO usar en producción**
- Para producción, implementar TR-31 (EncryptionType "04")

✅ **DUKPT genera llaves únicas por transacción**
- Mayor seguridad que llaves estáticas
- No requiere re-inyección periódica
- Compatible con PCI-DSS moderno

---

## 📞 Contacto y Soporte

Para problemas o preguntas:
- Revisa los logs completos: `adb logcat | grep MainViewModel`
- Compara con ejemplos en `DUKPT_GUIDE.md`
- Verifica la estructura del perfil en `dukpt_test_profile.json`

---

**Última actualización**: 2025-10-23
**Versión**: 1.0
