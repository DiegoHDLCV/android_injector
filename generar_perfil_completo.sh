#!/bin/bash

# =============================================================================
# Generador de Perfil Completo de Prueba (Maestras + Working)
# =============================================================================
# Este script genera un perfil JSON completo que incluye tanto llaves maestras
# (GENERIC) como llaves working para un escenario de prueba completo
# =============================================================================

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🏗️ Generador de Perfil Completo de Prueba${NC}"
echo -e "${BLUE}========================================${NC}"

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
OUTPUT_FILE="perfil_completo_${TIMESTAMP}.json"

echo -e "${YELLOW}📖 Leyendo llaves del archivo: $KEYS_FILE${NC}"

# Extraer todas las llaves del JSON
GENERIC_KEYS=$(jq -r '.keys[] | select(.keyType == "GENERIC") | .kcv' "$KEYS_FILE")
WORKING_PIN_KEYS=$(jq -r '.keys[] | select(.keyType == "WORKING_PIN_KEY") | .kcv' "$KEYS_FILE")
WORKING_MAC_KEYS=$(jq -r '.keys[] | select(.keyType == "WORKING_MAC_KEY") | .kcv' "$KEYS_FILE")
WORKING_DATA_KEYS=$(jq -r '.keys[] | select(.keyType == "WORKING_DATA_KEY") | .kcv' "$KEYS_FILE")

# Contar llaves de cada tipo
GENERIC_COUNT=$(echo "$GENERIC_KEYS" | wc -l | tr -d ' ')
PIN_COUNT=$(echo "$WORKING_PIN_KEYS" | wc -l | tr -d ' ')
MAC_COUNT=$(echo "$WORKING_MAC_KEYS" | wc -l | tr -d ' ')
DATA_COUNT=$(echo "$WORKING_DATA_KEYS" | wc -l | tr -d ' ')

echo -e "${GREEN}✅ Encontradas llaves:${NC}"
echo -e "   🏛️  GENERIC (Maestras): $GENERIC_COUNT llaves"
echo -e "   🔐 WORKING_PIN_KEY: $PIN_COUNT llaves"
echo -e "   🔒 WORKING_MAC_KEY: $MAC_COUNT llaves"
echo -e "   📄 WORKING_DATA_KEY: $DATA_COUNT llaves"

# Crear el JSON del perfil
echo -e "${YELLOW}📝 Generando perfil completo...${NC}"

# Iniciar el JSON del perfil
cat > "$OUTPUT_FILE" << EOF
{
  "name": "Perfil Completo Test System",
  "description": "Perfil completo de prueba con llaves maestras y working para configuración completa del sistema",
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

# Agregar configuraciones de llaves GENERIC como maestras (slots 01-05)
echo -e "${BLUE}🏛️ Agregando configuraciones de llaves maestras...${NC}"
GENERIC_COUNTER=0
for kcv in $GENERIC_KEYS; do
    SLOT_HEX=$(printf "%02X" $SLOT_COUNTER)
    add_key_config "MASTER" "Master Session Key" "$SLOT_HEX" "$kcv" "AES-256"
    add_comma_if_needed "false"
    ((SLOT_COUNTER++))
    ((GENERIC_COUNTER++))
done

# Agregar configuraciones de PIN keys (slots 06-08)
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

# Agregar configuraciones de MAC keys (slots 09-10)
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

# Agregar configuraciones de DATA keys (slots 11-12)
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
echo -e "${GREEN}✅ Perfil completo generado exitosamente!${NC}"
echo -e "${GREEN}📁 Archivo: $OUTPUT_FILE${NC}"
echo ""
echo -e "${BLUE}📊 Resumen del perfil generado:${NC}"
echo -e "   📝 Nombre: Perfil Completo Test System"
echo -e "   🏷️  Tipo: Retail"
echo -e "   🔑 Configuraciones: $((GENERIC_COUNTER + PIN_COUNTER + MAC_COUNTER + DATA_COUNTER)) llaves"
echo -e "   🏛️  Llaves Maestras: $GENERIC_COUNTER"
echo -e "   🔐 PIN Keys: $PIN_COUNTER"
echo -e "   🔒 MAC Keys: $MAC_COUNTER"
echo -e "   📄 DATA Keys: $DATA_COUNTER"
echo ""

# Mostrar las llaves seleccionadas
echo -e "${YELLOW}🔑 Llaves incluidas en el perfil completo:${NC}"
echo ""

# Mostrar llaves maestras
echo -e "${BLUE}🏛️ Llaves Maestras (GENERIC):${NC}"
GENERIC_COUNTER=0
for kcv in $GENERIC_KEYS; do
    SLOT_HEX=$(printf "%02X" $((GENERIC_COUNTER + 1)))
    echo -e "   Slot $SLOT_HEX: $kcv (Master)"
    ((GENERIC_COUNTER++))
done

# Mostrar PIN keys
echo -e "${BLUE}🔐 PIN Keys:${NC}"
PIN_COUNTER=0
for kcv in $WORKING_PIN_KEYS; do
    if [ $PIN_COUNTER -lt 3 ]; then
        SLOT_HEX=$(printf "%02X" $((PIN_COUNTER + 6)))
        echo -e "   Slot $SLOT_HEX: $kcv"
        ((PIN_COUNTER++))
    fi
done

# Mostrar MAC keys
echo -e "${BLUE}🔒 MAC Keys:${NC}"
MAC_COUNTER=0
for kcv in $WORKING_MAC_KEYS; do
    if [ $MAC_COUNTER -lt 2 ]; then
        SLOT_HEX=$(printf "%02X" $((MAC_COUNTER + 9)))
        echo -e "   Slot $SLOT_HEX: $kcv"
        ((MAC_COUNTER++))
    fi
done

# Mostrar DATA keys
echo -e "${BLUE}📄 DATA Keys:${NC}"
DATA_COUNTER=0
for kcv in $WORKING_DATA_KEYS; do
    if [ $DATA_COUNTER -lt 2 ]; then
        SLOT_HEX=$(printf "%02X" $((DATA_COUNTER + 11)))
        echo -e "   Slot $SLOT_HEX: $kcv"
        ((DATA_COUNTER++))
    fi
done

echo ""
echo -e "${YELLOW}💡 Instrucciones:${NC}"
echo -e "   1. Primero importa las llaves del archivo $KEYS_FILE al almacén"
echo -e "   2. Luego importa este perfil usando la funcionalidad de importar perfiles"
echo -e "   3. Este perfil contiene TODAS las llaves necesarias para un sistema completo"
echo -e "   4. Incluye llaves maestras (slots 01-05) y llaves working (slots 06-12)"
echo -e "   5. Perfecto para pruebas completas del sistema"
echo ""
echo -e "${GREEN}🎉 ¡Perfil completo listo para usar!${NC}"
