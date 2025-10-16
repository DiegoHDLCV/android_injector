#!/bin/bash

###############################################################################
# GENERADOR SIMPLE DE COMPONENTES PARA CEREMONIA
# Solo requiere OpenSSL (ya instalado por defecto en Mac)
###############################################################################

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Parámetros
NUM_CUSTODIOS=${1:-2}
ALGORITMO=${2:-AES-256}

# Validar número de custodios
if [ "$NUM_CUSTODIOS" -lt 2 ] || [ "$NUM_CUSTODIOS" -gt 3 ]; then
    echo -e "${RED}Error: El número de custodios debe ser 2 o 3${NC}"
    echo "Uso: $0 [numero_custodios] [algoritmo]"
    echo "  Ejemplo: $0 2 AES-256"
    exit 1
fi

# Determinar longitud según algoritmo
case "$ALGORITMO" in
    "3DES"|"DES-TRIPLE")
        KEY_BYTES=16
        KEY_BITS=128
        ;;
    "AES-128")
        KEY_BYTES=16
        KEY_BITS=128
        ;;
    "AES-192")
        KEY_BYTES=24
        KEY_BITS=192
        ;;
    "AES-256")
        KEY_BYTES=32
        KEY_BITS=256
        ;;
    *)
        echo -e "${RED}Error: Algoritmo no soportado${NC}"
        echo "Algoritmos válidos: 3DES, AES-128, AES-192, AES-256"
        exit 1
        ;;
esac

KEY_HEX_LENGTH=$((KEY_BYTES * 2))

# Banner
clear
echo ""
echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║     GENERADOR DE COMPONENTES PARA CEREMONIA DE LLAVES     ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BLUE}Configuración:${NC}"
echo -e "  • Algoritmo: ${GREEN}$ALGORITMO${NC} (${KEY_BITS} bits / ${KEY_BYTES} bytes)"
echo -e "  • Número de custodios: ${GREEN}$NUM_CUSTODIOS${NC}"
echo -e "  • Longitud de componente: ${GREEN}${KEY_HEX_LENGTH} caracteres hex${NC}"
echo ""

# Generar componentes
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}  COMPONENTES GENERADOS (Entregar a cada custodio)${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo ""

declare -a COMPONENTES

for i in $(seq 1 $NUM_CUSTODIOS); do
    COMP=$(openssl rand -hex $KEY_BYTES | tr '[:lower:]' '[:upper:]')
    COMPONENTES+=("$COMP")

    echo -e "${GREEN}╔═ CUSTODIO $i ═══════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║${NC}"
    echo -e "${GREEN}║${NC}  ${YELLOW}$COMP${NC}"
    echo -e "${GREEN}║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
done

# Calcular XOR de componentes
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}  CÁLCULO DE LLAVE FINAL (XOR)${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo ""

# Función para hacer XOR de dos strings hexadecimales
xor_hex() {
    local hex1=$1
    local hex2=$2
    local result=""

    for ((i=0; i<${#hex1}; i+=2)); do
        local byte1="0x${hex1:$i:2}"
        local byte2="0x${hex2:$i:2}"
        local xor_byte=$((byte1 ^ byte2))
        result+=$(printf "%02X" $xor_byte)
    done

    echo "$result"
}

# Calcular XOR de todos los componentes
FINAL_KEY="${COMPONENTES[0]}"
for ((i=1; i<${#COMPONENTES[@]}; i++)); do
    FINAL_KEY=$(xor_hex "$FINAL_KEY" "${COMPONENTES[$i]}")
done

echo -e "${CYAN}╔═ LLAVE FINAL (Resultado del XOR) ══════════════════════════════╗${NC}"
echo -e "${CYAN}║${NC}"
echo -e "${CYAN}║${NC}  ${YELLOW}$FINAL_KEY${NC}"
echo -e "${CYAN}║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Calcular KCV usando OpenSSL
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}  CÁLCULO DE KCV (Key Check Value)${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo ""

# Crear archivo temporal con la llave
TEMP_KEY_FILE=$(mktemp)
echo -n "$FINAL_KEY" > "$TEMP_KEY_FILE"

# Determinar algoritmo OpenSSL y tamaño de bloque
if [ "$ALGORITMO" = "3DES" ] || [ "$ALGORITMO" = "DES-TRIPLE" ]; then
    if [ $KEY_BYTES -eq 16 ]; then
        OPENSSL_ALGO="des-ede-cbc"
    else
        OPENSSL_ALGO="des-ede3-cbc"
    fi
    BLOCK_SIZE=8
else
    case "$ALGORITMO" in
        "AES-128") OPENSSL_ALGO="aes-128-cbc" ;;
        "AES-192") OPENSSL_ALGO="aes-192-cbc" ;;
        "AES-256") OPENSSL_ALGO="aes-256-cbc" ;;
    esac
    BLOCK_SIZE=16
fi

# Crear bloque de ceros
ZEROS=$(printf '00%.0s' $(seq 1 $BLOCK_SIZE))

# Calcular KCV
KCV_FULL=$(echo -n "$ZEROS" | xxd -r -p | openssl enc -$OPENSSL_ALGO -K "$FINAL_KEY" -iv 00000000000000000000000000000000 -nopad 2>/dev/null | xxd -p -c 256 | tr '[:lower:]' '[:upper:]')
KCV="${KCV_FULL:0:6}"

# Limpiar archivo temporal
rm -f "$TEMP_KEY_FILE"

echo -e "${GREEN}╔═ KCV CALCULADO ════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║${NC}                                                              ${GREEN}║${NC}"
echo -e "${GREEN}║${NC}  KCV (3 bytes):     ${YELLOW}${KCV}${NC}                                     ${GREEN}║${NC}"
echo -e "${GREEN}║${NC}  KCV completo:      ${KCV_FULL}  ${GREEN}║${NC}"
echo -e "${GREEN}║${NC}                                                              ${GREEN}║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Instrucciones
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  INSTRUCCIONES PARA LA CEREMONIA EN LA APP${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${BLUE}1.${NC} En la app, ve a ${CYAN}Ceremonia de Llaves${NC}"
echo -e "${BLUE}2.${NC} Selecciona:"
echo -e "   • Tipo de llave: ${GREEN}$ALGORITMO${NC}"
echo -e "   • Número de custodios: ${GREEN}$NUM_CUSTODIOS${NC}"
echo -e "   • ${YELLOW}[✓]${NC} Marca ${GREEN}\"Esta es KEK Storage\"${NC} si vas a usarla para cifrar el almacén"
echo -e "${BLUE}3.${NC} Inicia la ceremonia"
echo -e "${BLUE}4.${NC} Cada custodio ingresa su componente (mostrado arriba)"
echo -e "${BLUE}5.${NC} Verifica el componente con el botón ${CYAN}\"Verificar KCV\"${NC}"
echo -e "${BLUE}6.${NC} Pasa al siguiente custodio"
echo -e "${BLUE}7.${NC} Al finalizar, la app mostrará el KCV final"
echo -e "${BLUE}8.${NC} ${YELLOW}VERIFICA${NC} que el KCV de la app coincida con: ${YELLOW}${KCV}${NC}"
echo ""
echo -e "${GREEN}✓ Si coincide: Ceremonia exitosa${NC}"
echo -e "${RED}✗ Si NO coincide: Revisar componentes ingresados${NC}"
echo ""

# Advertencias de seguridad
echo -e "${RED}═══════════════════════════════════════════════════════════${NC}"
echo -e "${RED}  ⚠️  ADVERTENCIAS DE SEGURIDAD${NC}"
echo -e "${RED}═══════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}•${NC} NO compartas todos los componentes con una sola persona"
echo -e "${YELLOW}•${NC} Guarda cada componente en ${CYAN}medios físicos separados${NC}"
echo -e "${YELLOW}•${NC} NO envíes componentes por correo o mensajería"
echo -e "${YELLOW}•${NC} Considera ${CYAN}imprimir y guardar en cajas fuertes separadas${NC}"
echo -e "${YELLOW}•${NC} Los custodios deben ${CYAN}proteger sus componentes${NC} como contraseñas"
echo ""

# Ofrecer guardar
echo -ne "${BLUE}¿Guardar resultado en archivo? (s/n):${NC} "
read -r -n 1 SAVE_RESPONSE
echo ""

if [[ $SAVE_RESPONSE =~ ^[SsYy]$ ]]; then
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    FILENAME="ceremonia_${ALGORITMO//-/}_${NUM_CUSTODIOS}custodios_${TIMESTAMP}.txt"

    {
        echo "════════════════════════════════════════════════════════════"
        echo "  CEREMONIA DE LLAVES - $(date '+%Y-%m-%d %H:%M:%S')"
        echo "════════════════════════════════════════════════════════════"
        echo ""
        echo "CONFIGURACIÓN:"
        echo "  • Algoritmo: $ALGORITMO ($KEY_BITS bits / $KEY_BYTES bytes)"
        echo "  • Número de custodios: $NUM_CUSTODIOS"
        echo "  • Longitud de componente: $KEY_HEX_LENGTH caracteres hex"
        echo ""
        echo "════════════════════════════════════════════════════════════"
        echo "  COMPONENTES (Entregar a cada custodio)"
        echo "════════════════════════════════════════════════════════════"
        for i in $(seq 1 $NUM_CUSTODIOS); do
            echo ""
            echo "CUSTODIO $i:"
            echo "${COMPONENTES[$((i-1))]}"
        done
        echo ""
        echo "════════════════════════════════════════════════════════════"
        echo "  VERIFICACIÓN"
        echo "════════════════════════════════════════════════════════════"
        echo ""
        echo "KCV esperado: $KCV"
        echo ""
        echo "INSTRUCCIONES:"
        echo "1. Ingresa los componentes en la app"
        echo "2. Verifica que el KCV final coincida con: $KCV"
        echo "3. Si coincide: ✓ Ceremonia exitosa"
        echo ""
        echo "════════════════════════════════════════════════════════════"
        echo "  IMPORTANTE"
        echo "════════════════════════════════════════════════════════════"
        echo ""
        echo "• Guarda este archivo en un lugar seguro"
        echo "• NO compartas por medios electrónicos inseguros"
        echo "• Elimina este archivo después de completar la ceremonia"
        echo ""
    } > "$FILENAME"

    echo -e "${GREEN}✓ Guardado en: $FILENAME${NC}"
    echo ""
fi

echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  GENERACIÓN COMPLETADA - Usa los componentes arriba        ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
