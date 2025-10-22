#!/bin/bash

# =============================================================================
# Generador Simple de Perfil de Prueba
# =============================================================================
# Script simplificado para generar un perfil básico con llaves working
# =============================================================================

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}📋 Generador Simple de Perfil de Prueba${NC}"
echo -e "${BLUE}====================================${NC}"

# Parámetros por defecto
PROFILE_NAME="Perfil Test Working Keys"
PROFILE_DESCRIPTION="Perfil de prueba con llaves working para inyección automática"
APP_TYPE="Retail"
USE_KEK="false"

# Solicitar parámetros al usuario (opcional)
echo -e "${YELLOW}📝 Configuración del perfil (presiona Enter para usar valores por defecto):${NC}"
echo ""

read -p "Nombre del perfil [$PROFILE_NAME]: " input_name
PROFILE_NAME=${input_name:-$PROFILE_NAME}

read -p "Descripción [$PROFILE_DESCRIPTION]: " input_desc
PROFILE_DESCRIPTION=${input_desc:-$PROFILE_DESCRIPTION}

echo "Tipo de aplicación:"
echo "  1) Retail"
echo "  2) H2H"
echo "  3) Posint"
echo "  4) ATM"
echo "  5) Custom"
read -p "Selecciona (1-5) [1]: " app_choice

case $app_choice in
    2) APP_TYPE="H2H" ;;
    3) APP_TYPE="Posint" ;;
    4) APP_TYPE="ATM" ;;
    5) APP_TYPE="Custom" ;;
    *) APP_TYPE="Retail" ;;
esac

echo ""
echo "¿Usar cifrado KEK?"
echo "  1) No (recomendado para pruebas)"
echo "  2) Sí"
read -p "Selecciona (1-2) [1]: " kek_choice

case $kek_choice in
    2) USE_KEK="true" ;;
    *) USE_KEK="false" ;;
esac

# Generar timestamp para el nombre del archivo
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
OUTPUT_FILE="perfil_${PROFILE_NAME// /_}_${TIMESTAMP}.json"

echo ""
echo -e "${BLUE}📝 Generando perfil: $PROFILE_NAME${NC}"

# Crear el JSON del perfil con configuraciones básicas
cat > "$OUTPUT_FILE" << EOF
{
  "name": "$PROFILE_NAME",
  "description": "$PROFILE_DESCRIPTION",
  "applicationType": "$APP_TYPE",
  "useKEK": $USE_KEK,
  "selectedKEKKcv": "",
  "keyConfigurations": [
    {
      "usage": "PIN",
      "keyType": "WORKING_PIN_KEY",
      "slot": "01",
      "selectedKey": "B47475",
      "injectionMethod": "auto",
      "ksn": ""
    },
    {
      "usage": "PIN",
      "keyType": "WORKING_PIN_KEY",
      "slot": "02",
      "selectedKey": "2EB338",
      "injectionMethod": "auto",
      "ksn": ""
    },
    {
      "usage": "MAC",
      "keyType": "WORKING_MAC_KEY",
      "slot": "03",
      "selectedKey": "AA3968",
      "injectionMethod": "auto",
      "ksn": ""
    },
    {
      "usage": "DATA",
      "keyType": "WORKING_DATA_KEY",
      "slot": "04",
      "selectedKey": "7E52D4",
      "injectionMethod": "auto",
      "ksn": ""
    }
  ]
}
EOF

echo -e "${GREEN}✅ Perfil generado exitosamente!${NC}"
echo -e "${GREEN}📁 Archivo: $OUTPUT_FILE${NC}"
echo ""
echo -e "${BLUE}📊 Configuración del perfil:${NC}"
echo -e "   📝 Nombre: $PROFILE_NAME"
echo -e "   🏷️  Tipo: $APP_TYPE"
echo -e "   🔐 Usar KEK: $USE_KEK"
echo -e "   🔑 Configuraciones: 4 llaves working"
echo ""

echo -e "${YELLOW}🔑 Llaves incluidas:${NC}"
echo -e "   🔐 PIN Key (Slot 01): B47475"
echo -e "   🔐 PIN Key (Slot 02): 2EB338"
echo -e "   🔒 MAC Key (Slot 03): AA3968"
echo -e "   📄 DATA Key (Slot 04): 7E52D4"
echo ""

echo -e "${YELLOW}💡 Instrucciones:${NC}"
echo -e "   1. Asegúrate de que las llaves con estos KCVs estén en el almacén"
echo -e "   2. Importa este perfil usando la funcionalidad de importar perfiles"
echo -e "   3. El perfil estará listo para inyectar en dispositivos POS"
echo ""
echo -e "${GREEN}🎉 ¡Listo para usar!${NC}"
