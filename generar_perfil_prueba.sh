#!/bin/bash

# =============================================================================
# Generador de Perfil de Prueba con Llaves Working
# =============================================================================
# Este script genera un perfil JSON que usa las llaves working del archivo
# llaves_prueba_20251022_114542.json para crear un perfil listo para inyectar
# =============================================================================

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}📋 Generador de Perfil de Prueba con Llaves Working${NC}"
echo -e "${BLUE}================================================${NC}"

# Verificar que jq esté disponible
if ! command -v jq &> /dev/null; then
    echo -e "${RED}❌ Error: jq no está instalado${NC}"
    echo -e "${YELLOW}💡 Instala jq: brew install jq${NC}"
    exit 1
fi

# Archivo de llaves de entrada
KEYS_FILE="llaves_prueba_20251022_114542.json"

# Verificar que el archivo de llaves existe
if [ ! -f "$KEYS_FILE" ]; then
    echo -e "${RED}❌ Error: No se encontró el archivo $KEYS_FILE${NC}"
    echo -e "${YELLOW}💡 Asegúrate de que el archivo esté en el directorio actual${NC}"
    exit 1
fi

# Generar timestamp para el nombre del archivo
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
OUTPUT_FILE="perfil_prueba_working_keys_${TIMESTAMP}.json"

echo -e "${YELLOW}📖 Leyendo llaves del archivo: $KEYS_FILE${NC}"

# Extraer llaves working del JSON
WORKING_PIN_KEYS=$(jq -r '.keys[] | select(.keyType == "WORKING_PIN_KEY") | .kcv' "$KEYS_FILE")
WORKING_MAC_KEYS=$(jq -r '.keys[] | select(.keyType == "WORKING_MAC_KEY") | .kcv' "$KEYS_FILE")
WORKING_DATA_KEYS=$(jq -r '.keys[] | select(.keyType == "WORKING_DATA_KEY") | .kcv' "$KEYS_FILE")

# Contar llaves de cada tipo
PIN_COUNT=$(echo "$WORKING_PIN_KEYS" | wc -l | tr -d ' ')
MAC_COUNT=$(echo "$WORKING_MAC_KEYS" | wc -l | tr -d ' ')
DATA_COUNT=$(echo "$WORKING_DATA_KEYS" | wc -l | tr -d ' ')

echo -e "${GREEN}✅ Encontradas llaves working:${NC}"
echo -e "   🔐 WORKING_PIN_KEY: $PIN_COUNT llaves"
echo -e "   🔒 WORKING_MAC_KEY: $MAC_COUNT llaves"
echo -e "   📄 WORKING_DATA_KEY: $DATA_COUNT llaves"

# Crear el JSON del perfil
echo -e "${YELLOW}📝 Generando perfil de prueba...${NC}"

# Iniciar el JSON del perfil
cat > "$OUTPUT_FILE" << EOF
{
  "name": "Perfil Test Working Keys",
  "description": "Perfil de prueba con llaves working para inyección automática",
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

# Contador para slots
SLOT_COUNTER=1

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
cat >> "$OUTPUT_FILE" << EOF

  ]
}
EOF

# Mostrar resumen
echo -e "${GREEN}✅ Perfil de prueba generado exitosamente!${NC}"
echo -e "${GREEN}📁 Archivo: $OUTPUT_FILE${NC}"
echo ""
echo -e "${BLUE}📊 Resumen del perfil generado:${NC}"
echo -e "   📝 Nombre: Perfil Test Working Keys"
echo -e "   🏷️  Tipo: Retail"
echo -e "   🔑 Configuraciones: $((PIN_COUNTER + MAC_COUNTER + DATA_COUNTER)) llaves"
echo -e "   🔐 PIN Keys: $PIN_COUNTER"
echo -e "   🔒 MAC Keys: $MAC_COUNTER"
echo -e "   📄 DATA Keys: $DATA_COUNTER"
echo ""

# Mostrar las llaves seleccionadas
echo -e "${YELLOW}🔑 Llaves seleccionadas para el perfil:${NC}"
echo ""

# Mostrar PIN keys
echo -e "${BLUE}🔐 PIN Keys:${NC}"
PIN_COUNTER=0
for kcv in $WORKING_PIN_KEYS; do
    if [ $PIN_COUNTER -lt 3 ]; then
        SLOT_HEX=$(printf "%02X" $((PIN_COUNTER + 1)))
        echo -e "   Slot $SLOT_HEX: $kcv"
        ((PIN_COUNTER++))
    fi
done

# Mostrar MAC keys
echo -e "${BLUE}🔒 MAC Keys:${NC}"
MAC_COUNTER=0
for kcv in $WORKING_MAC_KEYS; do
    if [ $MAC_COUNTER -lt 2 ]; then
        SLOT_HEX=$(printf "%02X" $((MAC_COUNTER + 4)))
        echo -e "   Slot $SLOT_HEX: $kcv"
        ((MAC_COUNTER++))
    fi
done

# Mostrar DATA keys
echo -e "${BLUE}📄 DATA Keys:${NC}"
DATA_COUNTER=0
for kcv in $WORKING_DATA_KEYS; do
    if [ $DATA_COUNTER -lt 2 ]; then
        SLOT_HEX=$(printf "%02X" $((DATA_COUNTER + 6)))
        echo -e "   Slot $SLOT_HEX: $kcv"
        ((DATA_COUNTER++))
    fi
done

echo ""
echo -e "${YELLOW}💡 Instrucciones:${NC}"
echo -e "   1. Primero importa las llaves del archivo $KEYS_FILE al almacén"
echo -e "   2. Luego importa este perfil usando la funcionalidad de importar perfiles"
echo -e "   3. El perfil estará listo para inyectar en dispositivos POS"
echo ""
echo -e "${GREEN}🎉 ¡Listo para usar!${NC}"
