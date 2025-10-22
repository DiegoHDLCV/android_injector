#!/bin/bash

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}⚙️ Generador de Perfil de Prueba con Llaves Working (Slots Altos)${NC}"
echo -e "${BLUE}================================================================${NC}"

# Archivo de entrada con las llaves
INPUT_FILE="llaves_prueba_20251022_114542.json"

# Verificar que el archivo existe
if [ ! -f "$INPUT_FILE" ]; then
    echo -e "${RED}❌ Error: No se encontró el archivo $INPUT_FILE${NC}"
    echo -e "${YELLOW}💡 Asegúrate de que el archivo existe en el directorio actual${NC}"
    exit 1
fi

# Crear archivo de salida con timestamp
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
OUTPUT_FILE="perfil_working_slots_altos_${TIMESTAMP}.json"

echo -e "${YELLOW}📖 Leyendo llaves del archivo: $INPUT_FILE${NC}"

# Extraer llaves working del archivo JSON
WORKING_PIN_KEYS=$(grep -A 5 '"keyType": "WORKING_PIN_KEY"' "$INPUT_FILE" | grep '"kcv":' | sed 's/.*"kcv": "\([^"]*\)".*/\1/' | head -5)
WORKING_MAC_KEYS=$(grep -A 5 '"keyType": "WORKING_MAC_KEY"' "$INPUT_FILE" | grep '"kcv":' | sed 's/.*"kcv": "\([^"]*\)".*/\1/' | head -5)
WORKING_DATA_KEYS=$(grep -A 5 '"keyType": "WORKING_DATA_KEY"' "$INPUT_FILE" | grep '"kcv":' | sed 's/.*"kcv": "\([^"]*\)".*/\1/' | head -5)

# Verificar que se encontraron llaves
if [ -z "$WORKING_PIN_KEYS" ] || [ -z "$WORKING_MAC_KEYS" ] || [ -z "$WORKING_DATA_KEYS" ]; then
    echo -e "${RED}❌ Error: No se pudieron extraer las llaves working del archivo${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Encontradas llaves working:${NC}"
echo -e "   🔐 WORKING_PIN_KEY: $(echo $WORKING_PIN_KEYS | wc -w) llaves"
echo -e "   🔒 WORKING_MAC_KEY: $(echo $WORKING_MAC_KEYS | wc -w) llaves"
echo -e "   📄 WORKING_DATA_KEY: $(echo $WORKING_DATA_KEYS | wc -w) llaves"

echo -e "${YELLOW}📝 Generando perfil de llaves working con slots altos...${NC}"

# Crear el archivo JSON del perfil
cat > "$OUTPUT_FILE" << 'EOF'
{
  "name": "Perfil Working Keys Slots Altos",
  "description": "Perfil de prueba con llaves working usando slots altos para evitar conflictos",
  "applicationType": "Retail",
  "useKEK": false,
  "selectedKEKKcv": "",
  "keyConfigurations": [
EOF

# Función para agregar configuración de llave
add_key_config() {
    local usage="$1"
    local key_type="$2"
    local slot="$3"
    local kcv="$4"
    local algorithm="$5"
    
    echo "    {" >> "$OUTPUT_FILE"
    echo "      \"usage\": \"$usage\"," >> "$OUTPUT_FILE"
    echo "      \"keyType\": \"$key_type\"," >> "$OUTPUT_FILE"
    echo "      \"slot\": \"$slot\"," >> "$OUTPUT_FILE"
    echo "      \"selectedKey\": \"$kcv\"," >> "$OUTPUT_FILE"
    echo "      \"injectionMethod\": \"auto\"," >> "$OUTPUT_FILE"
    echo "      \"ksn\": \"\"" >> "$OUTPUT_FILE"
    echo "    }" >> "$OUTPUT_FILE"
}

# Función para agregar coma si no es la última configuración
add_comma_if_needed() {
    local is_last="$1"
    if [ "$is_last" != "true" ]; then
        echo "," >> "$OUTPUT_FILE"
    fi
}

# Contador para slots (empezar desde slot 10 para evitar conflictos)
SLOT_COUNTER=10

# Agregar configuraciones de PIN keys (usar las primeras 3)
echo -e "${BLUE}🔐 Agregando configuraciones de PIN keys...${NC}"
PIN_COUNTER=0
for kcv in $WORKING_PIN_KEYS; do
    if [ $PIN_COUNTER -lt 3 ]; then
        SLOT_HEX=$(printf "%02X" $SLOT_COUNTER)
        add_key_config "PIN" "WORKING_PIN_KEY" "$SLOT_HEX" "$kcv" "AES-256"
        add_comma_if_needed "false"
        ((SLOT_COUNTER++))
        ((PIN_COUNTER++))
    fi
done

# Agregar configuraciones de MAC keys (usar las primeras 2)
echo -e "${BLUE}🔒 Agregando configuraciones de MAC keys...${NC}"
MAC_COUNTER=0
for kcv in $WORKING_MAC_KEYS; do
    if [ $MAC_COUNTER -lt 2 ]; then
        SLOT_HEX=$(printf "%02X" $SLOT_COUNTER)
        add_key_config "MAC" "WORKING_MAC_KEY" "$SLOT_HEX" "$kcv" "AES-256"
        add_comma_if_needed "false"
        ((SLOT_COUNTER++))
        ((MAC_COUNTER++))
    fi
done

# Agregar configuraciones de DATA keys (usar las primeras 2)
echo -e "${BLUE}📄 Agregando configuraciones de DATA keys...${NC}"
DATA_COUNTER=0
for kcv in $WORKING_DATA_KEYS; do
    if [ $DATA_COUNTER -lt 2 ]; then
        SLOT_HEX=$(printf "%02X" $SLOT_COUNTER)
        add_key_config "DATA" "WORKING_DATA_KEY" "$SLOT_HEX" "$kcv" "AES-256"
        if [ $DATA_COUNTER -lt 1 ]; then
            add_comma_if_needed "false"
        fi
        ((SLOT_COUNTER++))
        ((DATA_COUNTER++))
    fi
done

# Cerrar el JSON
cat >> "$OUTPUT_FILE" << 'EOF'
  ]
}
EOF

echo -e "${GREEN}✅ Perfil de llaves working con slots altos generado exitosamente!${NC}"
echo -e "${GREEN}📁 Archivo: $OUTPUT_FILE${NC}"

# Mostrar resumen del perfil generado
echo ""
echo -e "${BLUE}📊 Resumen del perfil generado:${NC}"
echo -e "   📝 Nombre: Perfil Working Keys Slots Altos"
echo -e "   🏷️  Tipo: Retail"
echo -e "   🔑 Configuraciones: 7 llaves working"
echo -e "   🔐 PIN Keys: 3"
echo -e "   🔒 MAC Keys: 2"
echo -e "   📄 DATA Keys: 2"
echo -e "   🎯 Slots usados: 10-16 (para evitar conflictos)"

echo ""
echo -e "${YELLOW}🔑 Llaves working incluidas en el perfil:${NC}"

echo ""
echo -e "${BLUE}🔐 PIN Keys:${NC}"
SLOT_COUNTER=10
PIN_COUNTER=0
for kcv in $WORKING_PIN_KEYS; do
    if [ $PIN_COUNTER -lt 3 ]; then
        SLOT_HEX=$(printf "%02X" $SLOT_COUNTER)
        echo -e "   Slot $SLOT_HEX: $kcv"
        ((SLOT_COUNTER++))
        ((PIN_COUNTER++))
    fi
done

echo -e "${BLUE}🔒 MAC Keys:${NC}"
MAC_COUNTER=0
for kcv in $WORKING_MAC_KEYS; do
    if [ $MAC_COUNTER -lt 2 ]; then
        SLOT_HEX=$(printf "%02X" $SLOT_COUNTER)
        echo -e "   Slot $SLOT_HEX: $kcv"
        ((SLOT_COUNTER++))
        ((MAC_COUNTER++))
    fi
done

echo -e "${BLUE}📄 DATA Keys:${NC}"
DATA_COUNTER=0
for kcv in $WORKING_DATA_KEYS; do
    if [ $DATA_COUNTER -lt 2 ]; then
        SLOT_HEX=$(printf "%02X" $SLOT_COUNTER)
        echo -e "   Slot $SLOT_HEX: $kcv"
        ((SLOT_COUNTER++))
        ((DATA_COUNTER++))
    fi
done

echo ""
echo -e "${YELLOW}💡 Instrucciones:${NC}"
echo -e "   1. Primero importa las llaves del archivo $INPUT_FILE al almacén"
echo -e "   2. Luego importa este perfil usando la funcionalidad de importar perfiles"
echo -e "   3. Este perfil usa slots altos (10-16) para evitar conflictos KEY_INDEX_ERR"
echo -e "   4. Usa este perfil DESPUÉS de haber configurado las llaves maestras"

echo ""
echo -e "${GREEN}🎉 ¡Perfil de llaves working con slots altos listo para usar!${NC}"
