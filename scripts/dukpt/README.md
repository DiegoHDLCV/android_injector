# Scripts DUKPT

Herramientas Python para generar llaves y perfiles de inyección DUKPT.

## Scripts Disponibles

### 1. import_dukpt_test_keys.py

Genera archivo `test_keys_dukpt.json` con 6 llaves de prueba.

**Uso:**
```bash
cd /path/to/android_injector
python3 scripts/dukpt/import_dukpt_test_keys.py
```

**Salida:**
- Archivo: `data/dukpt/test_keys_dukpt.json`
- Contiene:
  - 1× KEK_STORAGE (AES-256)
  - 1× DUKPT IPEK AES-128
  - 1× DUKPT IPEK AES-192
  - 1× DUKPT IPEK AES-256
  - 1× DUKPT IPEK 2TDEA
  - 1× DUKPT IPEK 3TDEA

**Ejemplo de salida:**
```
✅ Archivo de llaves generado: test_keys_dukpt.json

📊 Llaves incluidas:
  - KEK_STORAGE (AES-256)
    KCV: 112A8B
    Hex: E14007267311EBDA872B46AF9B1A086A...

  - DUKPT_IPEK (AES-128)
    KCV: 072043
    Hex: 12101FFF4ED412459F4E727CC3A4895A
    ...
```

**Qué contiene cada llave:**
```json
{
  "keyType": "KEK_STORAGE|DUKPT_IPEK",
  "futurexCode": "00|05",
  "algorithm": "AES-256|AES-128|AES-192|AES-256|DES_DOUBLE|DES_TRIPLE",
  "keyHex": "hexadecimal de la llave",
  "kcv": "primeros 4 hex del KCV",
  "bytes": 16,24,32,
  "description": "Descripción"
}
```

### 2. import_dukpt_profile.py

Genera 4 perfiles de inyección predefinidos.

**Uso:**
```bash
cd /path/to/android_injector
python3 scripts/dukpt/import_dukpt_profile.py
```

**Salida:**
- `data/dukpt/dukpt_test_profile.json` - AES-128 simple
- `data/dukpt/dukpt_2tdea_profile.json` - 2TDEA simple
- `data/dukpt/dukpt_3tdea_profile.json` - 3TDEA simple
- `data/dukpt/dukpt_multikey_profile.json` - Todos los algoritmos (5 slots)

**Estructura de un perfil:**
```json
{
  "name": "Nombre del perfil",
  "description": "Descripción",
  "applicationType": "Retail",
  "useKEK": false,
  "selectedKEKKcv": "",
  "keyConfigurations": [
    {
      "usage": "DUKPT",
      "keyType": "DUKPT Initial Key (IPEK)",
      "slot": "01",
      "selectedKey": "072043",    // KCV de la IPEK
      "injectionMethod": "auto",
      "ksn": "FFFF9876543210000000"
    }
  ]
}
```

## Uso Paso a Paso

### Primera ejecución (completa)

```bash
# 1. Generar llaves
python3 scripts/dukpt/import_dukpt_test_keys.py

# 2. Generar perfiles
python3 scripts/dukpt/import_dukpt_profile.py

# 3. Verificar archivos generados
ls -la data/dukpt/

# Debería mostrar:
# test_keys_dukpt.json
# dukpt_test_profile.json
# dukpt_2tdea_profile.json
# dukpt_3tdea_profile.json
# dukpt_multikey_profile.json
```

### Regenerar solo llaves

```bash
python3 scripts/dukpt/import_dukpt_test_keys.py
# Overwrite: y (si se pregunta)
```

### Regenerar solo perfiles

```bash
python3 scripts/dukpt/import_dukpt_profile.py
# Overwrite: y (si se pregunta)
```

## Estructura de Archivos

```
scripts/dukpt/
├── README.md (este archivo)
├── import_dukpt_test_keys.py      ← Genera llaves
└── import_dukpt_profile.py        ← Genera perfiles
```

## Requiere

### Python 3.7+
```bash
python3 --version
# Python 3.7.x, 3.8.x, 3.9.x, 3.10.x, 3.11.x, etc.
```

### Librerías Estándar
- `json` (incluida)
- `os` (incluida)
- `datetime` (incluida)
- `random` (incluida)

**No requiere instalación de dependencias externas**

## Valores Generados

### Llaves de Prueba

Cada ejecución genera las MISMAS llaves (valores hardcodeados para testing):

| Algoritmo | KCV | Bytes | KSN |
|-----------|-----|-------|-----|
| AES-128 | 072043 | 16 | FFFF9876543210000000 |
| AES-192 | 5D614B | 24 | FFFF9876543210000001 |
| AES-256 | AB1234 | 32 | FFFF9876543210000002 |
| 2TDEA | 3F8D42 | 16 | FFFF9876543210000001 |
| 3TDEA | 7B5E9C | 16 | FFFF9876543210000002 |
| KEK | 112A8B | 32 | - |

### Perfiles Generados

**dukpt_test_profile.json:** Slot 1, AES-128
**dukpt_2tdea_profile.json:** Slot 1, 2TDEA
**dukpt_3tdea_profile.json:** Slot 1, 3TDEA
**dukpt_multikey_profile.json:** 5 Slots, todos los algoritmos

## Personalización

### Editar llaves (avanzado)

Si necesitas llaves específicas, edita directamente:

```bash
# Abre el script
nano scripts/dukpt/import_dukpt_test_keys.py

# Sección "KEK_STORAGE" o "DUKPT_IPEK"
# Modifica los valores en la lista "keys"

# Regenéra
python3 scripts/dukpt/import_dukpt_test_keys.py
```

### Editar perfiles (simple)

Más fácil editar el archivo JSON resultante:

```bash
# Abre el perfil
nano data/dukpt/dukpt_test_profile.json

# Modifica los valores
# - name, description
# - slot numbers
# - KCVs, KSNs

# Guarda y usa directamente
```

## Troubleshooting

### Problema: "No such file or directory"

```bash
# Error: scripts/dukpt/import_dukpt_test_keys.py: No such file
# Solución: Verifica ubicación actual
pwd  # Debe estar en android_injector/

# O ejecuta con ruta absoluta
python3 ./scripts/dukpt/import_dukpt_test_keys.py
```

### Problema: "Permission denied"

```bash
# Error: Permission denied: import_dukpt_test_keys.py
# Solución: Dale permisos
chmod +x scripts/dukpt/import_dukpt_test_keys.py
```

### Problema: "ModuleNotFoundError"

```bash
# Error: No module named 'xxx'
# Solución: Scripts usan solo librerías estándar
# Verifica Python versión 3.7+
python3 --version
```

### Problema: Archivo ya existe

```bash
# Pregunta: File already exists. Overwrite? (y/n)
# Opción 1: Escribe 'y' para sobrescribir
y
# Opción 2: Renombra el antiguo
mv data/dukpt/test_keys_dukpt.json data/dukpt/test_keys_dukpt.json.backup
```

## Verificar Salida

Después de ejecutar los scripts:

```bash
# Ver archivo de llaves
cat data/dukpt/test_keys_dukpt.json | head -20

# Contar llaves
grep '"keyType"' data/dukpt/test_keys_dukpt.json | wc -l
# Debe mostrar: 6

# Ver perfiles
ls -1 data/dukpt/dukpt_*_profile.json
# Debe mostrar 4 archivos

# Validar JSON
python3 -m json.tool data/dukpt/test_keys_dukpt.json > /dev/null
echo $?
# Debe mostrar: 0 (OK)
```

## Integración con Injector

### Paso 1: Generar archivos
```bash
python3 scripts/dukpt/import_dukpt_test_keys.py
python3 scripts/dukpt/import_dukpt_profile.py
```

### Paso 2: En app Injector
```
Key Vault → Import Keys → Selecciona data/dukpt/test_keys_dukpt.json
Profiles → Import Profile → Selecciona data/dukpt/dukpt_test_profile.json
```

### Paso 3: Inyectar
```
Profiles → [Tu Perfil] → Inject All Keys
```

## Documentación

- Guía completa: `docs/dukpt/DUKPT_COMPLETE_GUIDE.md`
- Índice: `docs/dukpt/DUKPT_INDEX.md`
- Inicio rápido: `docs/dukpt/DUKPT_TEST_QUICKSTART.md`

---

**Última actualización:** 2025-10-24
**Estado:** ✅ Funcional
