#!/bin/bash

# =============================================================================
# Generador de Perfil de Prueba con Llaves Maestras (GENERIC)
# =============================================================================
# Este script genera un perfil JSON que usa las llaves GENERIC del archivo
# llaves_prueba_20251022_114542.json como llaves maestras del sistema
# =============================================================================

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🏛️ Generador de Perfil de Prueba con Llaves Maestras${NC}"
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
OUTPUT_FILE="perfil_masters_${TIMESTAMP}.json"

echo -e "${YELLOW}📖 Leyendo llaves del archivo: $KEYS_FILE${NC}"

# Extraer llaves GENERIC del JSON
GENERIC_KEYS=$(jq -r '.keys[] | select(.keyType == "GENERIC") | .kcv' "$KEYS_FILE")

# Contar llaves GENERIC
GENERIC_COUNT=$(echo "$GENERIC_KEYS" | wc -l | tr -d ' ')

echo -e "${GREEN}✅ Encontradas llaves GENERIC (Maestras): $GENERIC_COUNT llaves${NC}"

# Mostrar las llaves encontradas
echo -e "${BLUE}🔑 Llaves GENERIC encontradas:${NC}"
counter=1
for kcv in $GENERIC_KEYS; do
    echo -e "   $counter. KCV: $kcv"
    ((counter++))
done

# Crear el JSON del perfil
echo -e "${YELLOW}📝 Generando perfil de llaves maestras...${NC}"

# Iniciar el JSON del perfil
cat > "$OUTPUT_FILE" << EOF
{
  "name": "Perfil Maestras del Sistema",
  "description": "Perfil de prueba con llaves maestras (GENERIC) para configuración inicial del sistema",
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

# Contador para slots (empezar desde slot 10 para diferenciar de working keys)
SLOT_COUNTER=10

# Agregar configuraciones de llaves GENERIC como maestras
echo -e "${BLUE}🏛️ Agregando configuraciones de llaves maestras...${NC}"
GENERIC_COUNTER=0
for kcv in $GENERIC_KEYS; do
    SLOT_HEX=$(printf "%02X" $SLOT_COUNTER)
    
    # Asignar diferentes usos según el índice
    case $GENERIC_COUNTER in
        0) USAGE="MASTER" ;;
        1) USAGE="MASTER" ;;
        2) USAGE="MASTER" ;;
        3) USAGE="MASTER" ;;
        4) USAGE="MASTER" ;;
        *) USAGE="MASTER" ;;
    esac
    
    add_key_config "$USAGE" "Master Session Key" "$SLOT_HEX" "$kcv" "AES-256"
    add_comma_if_needed "false"
    ((SLOT_COUNTER++))
    ((GENERIC_COUNTER++))
done

# Cerrar el JSON
cat >> "$OUTPUT_FILE" << EOF

  ]
}
EOF

# Mostrar resumen
echo -e "${GREEN}✅ Perfil de llaves maestras generado exitosamente!${NC}"
echo -e "${GREEN}📁 Archivo: $OUTPUT_FILE${NC}"
echo ""
echo -e "${BLUE}📊 Resumen del perfil generado:${NC}"
echo -e "   📝 Nombre: Perfil Maestras del Sistema"
echo -e "   🏷️  Tipo: Retail"
echo -e "   🔑 Configuraciones: $GENERIC_COUNTER llaves maestras"
echo -e "   🏛️  Tipo de llaves: GENERIC (Maestras)"
echo ""

# Mostrar las llaves seleccionadas
echo -e "${YELLOW}🔑 Llaves maestras incluidas en el perfil:${NC}"
echo ""

GENERIC_COUNTER=0
for kcv in $GENERIC_KEYS; do
    SLOT_HEX=$(printf "%02X" $((GENERIC_COUNTER + 10)))
    echo -e "   🏛️  Slot $SLOT_HEX: $kcv (Master)"
    ((GENERIC_COUNTER++))
done

echo ""
echo -e "${YELLOW}💡 Instrucciones:${NC}"
echo -e "   1. Primero importa las llaves del archivo $KEYS_FILE al almacén"
echo -e "   2. Luego importa este perfil usando la funcionalidad de importar perfiles"
echo -e "   3. Este perfil contiene llaves maestras para configuración inicial del sistema"
echo -e "   4. Usa este perfil ANTES de inyectar llaves working"
echo ""
echo -e "${GREEN}🎉 ¡Perfil de llaves maestras listo para usar!${NC}"
