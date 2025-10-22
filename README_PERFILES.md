# Generadores de Perfiles de Prueba

Este directorio contiene scripts para generar perfiles JSON que pueden ser importados directamente en el inyector de llaves.

## Scripts Disponibles

### 1. `generar_perfil_masters.sh`
**Script para generar perfil con llaves maestras (GENERIC)**

- Lee el archivo `llaves_prueba_20251022_114542.json`
- Extrae automáticamente las llaves GENERIC (maestras)
- Genera un perfil con 5 configuraciones de llaves maestras
- Asigna slots automáticamente (10-14)

**Uso:**
```bash
./generar_perfil_masters.sh
```

**Resultado:**
- Archivo: `perfil_masters_YYYYMMDD_HHMMSS.json`
- 5 llaves maestras (slots 10-14)
- Perfecto para configuración inicial del sistema

### 2. `generar_perfil_working.sh`
**Script para generar perfil con llaves working**

- Lee el archivo `llaves_prueba_20251022_114542.json`
- Extrae automáticamente las llaves working (PIN, MAC, DATA)
- Genera un perfil con 7 configuraciones de llaves working
- Asigna slots automáticamente (01-07)

**Uso:**
```bash
./generar_perfil_working.sh
```

**Resultado:**
- Archivo: `perfil_working_YYYYMMDD_HHMMSS.json`
- 3 llaves PIN (slots 01-03)
- 2 llaves MAC (slots 04-05)  
- 2 llaves DATA (slots 06-07)

### 3. `generar_perfil_completo.sh`
**Script para generar perfil completo (maestras + working)**

- Lee el archivo `llaves_prueba_20251022_114542.json`
- Extrae TODAS las llaves (GENERIC + WORKING)
- Genera un perfil con 12 configuraciones de llaves
- Asigna slots automáticamente (01-12)

**Uso:**
```bash
./generar_perfil_completo.sh
```

**Resultado:**
- Archivo: `perfil_completo_YYYYMMDD_HHMMSS.json`
- 5 llaves maestras (slots 01-05)
- 3 llaves PIN (slots 06-08)
- 2 llaves MAC (slots 09-0A)
- 2 llaves DATA (slots 0B-0C)

### 4. `generar_perfil_simple.sh`
**Script interactivo para generar perfiles personalizados**

- Permite personalizar nombre, descripción, tipo de app
- Configuración interactiva de parámetros
- Genera perfil básico con 4 llaves working fijas
- Ideal para pruebas rápidas

**Uso:**
```bash
./generar_perfil_simple.sh
```

**Resultado:**
- Archivo: `perfil_[nombre]_YYYYMMDD_HHMMSS.json`
- 2 llaves PIN (slots 01-02)
- 1 llave MAC (slot 03)
- 1 llave DATA (slot 04)

## Formato del JSON Generado

Los scripts generan archivos JSON con la siguiente estructura:

```json
{
  "name": "Nombre del Perfil",
  "description": "Descripción del perfil",
  "applicationType": "Retail|H2H|Posint|ATM|Custom",
  "useKEK": false,
  "selectedKEKKcv": "",
  "keyConfigurations": [
    {
      "usage": "PIN|MAC|DATA",
      "keyType": "WORKING_PIN_KEY|WORKING_MAC_KEY|WORKING_DATA_KEY",
      "slot": "01",
      "selectedKey": "KCV_DE_LA_LLAVE",
      "injectionMethod": "auto",
      "ksn": ""
    }
  ]
}
```

## Flujo de Trabajo

### Paso 1: Generar Perfil
Ejecuta uno de los scripts para generar el JSON del perfil:

```bash
# Opción 1: Perfil completo (maestras + working) - RECOMENDADO
./generar_perfil_completo.sh

# Opción 2: Solo llaves maestras (para configuración inicial)
./generar_perfil_masters.sh

# Opción 3: Solo llaves working (para operaciones diarias)
./generar_perfil_working.sh

# Opción 4: Perfil personalizado e interactivo
./generar_perfil_simple.sh
```

### Paso 2: Importar Llaves al Almacén
Antes de importar el perfil, asegúrate de que las llaves estén en el almacén:

1. Ve a la sección "Almacén de Llaves"
2. Importa las llaves del archivo `llaves_prueba_20251022_114542.json`
3. Verifica que las llaves estén disponibles

### Paso 3: Importar Perfil
1. Ve a la sección "Perfiles de Inyección"
2. Haz clic en el botón "Importar" (📤) en la barra superior
3. Pega el contenido del archivo JSON generado
4. Haz clic en "Importar"
5. El perfil aparecerá en la lista listo para usar

### Paso 4: Inyectar Llaves
1. Selecciona el perfil importado
2. Haz clic en "Inyectar llaves"
3. Las llaves se inyectarán automáticamente según la configuración

## Llaves de Prueba Incluidas

El archivo `llaves_prueba_20251022_114542.json` contiene 21 llaves de prueba:

### KEK Storage
- **D4E5F6**: KEK Storage fija del sistema

### Llaves Working PIN (5 llaves)
- **B47475**: 3DES-16
- **2EB338**: 3DES-24
- **051E37**: AES-128
- **7F09C8**: AES-192
- **1AE2B4**: AES-256

### Llaves Working MAC (5 llaves)
- **AA3968**: 3DES-16
- **97B00C**: 3DES-24
- **0528CF**: AES-128
- **91C730**: AES-192
- **1D61D9**: AES-256

### Llaves Working DATA (5 llaves)
- **7E52D4**: 3DES-16
- **D5E2D7**: 3DES-24
- **BBBC6E**: AES-128
- **BD041F**: AES-192
- **3B71F8**: AES-256

## Requisitos

- **jq**: Para procesar JSON (instalar con `brew install jq`)
- **bash**: Shell compatible con bash 4.0+
- Archivo de llaves: `llaves_prueba_20251022_114542.json`

## Solución de Problemas

### Error: "jq no está instalado"
```bash
brew install jq
```

### Error: "No se encontró el archivo de llaves"
- Verifica que `llaves_prueba_20251022_114542.json` esté en el directorio actual
- Ejecuta el script desde el directorio correcto

### Error al importar perfil: "Llave no encontrada"
- Asegúrate de haber importado las llaves al almacén primero
- Verifica que los KCVs en el perfil coincidan con los del almacén

## Recomendaciones de Uso

### 🏗️ Para Configuración Completa del Sistema (RECOMENDADO)
```bash
./generar_perfil_completo.sh
# Genera perfil con 12 llaves (5 maestras + 7 working)
# Perfecto para pruebas completas del sistema
```

### 🏛️ Para Configuración Inicial (Solo Maestras)
```bash
./generar_perfil_masters.sh
# Genera perfil con 5 llaves maestras
# Usar primero para configurar el sistema
```

### ⚙️ Para Operaciones Diarias (Solo Working)
```bash
./generar_perfil_working.sh
# Genera perfil con 7 llaves working
# Usar después de configurar las maestras
```

### 🔧 Para Pruebas Rápidas
```bash
./generar_perfil_simple.sh
# Perfil personalizado con 4 llaves working
# Ideal para pruebas rápidas
```

## Ejemplos de Uso

### Configuración Completa de Sistema
```bash
# 1. Generar perfil completo
./generar_perfil_completo.sh

# 2. Importar llaves al almacén
# 3. Importar perfil
# 4. Inyectar - ¡Sistema completo listo!
```

### Configuración por Etapas
```bash
# 1. Primero las maestras
./generar_perfil_masters.sh
# Importar y configurar maestras

# 2. Luego las working
./generar_perfil_working.sh
# Importar y configurar working keys
```

### Perfil Personalizado
```bash
./generar_perfil_simple.sh
# Personaliza nombre, descripción y tipo de aplicación
```

¡Los perfiles generados están listos para importar y usar en el inyector de llaves!
