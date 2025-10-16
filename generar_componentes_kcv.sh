#!/bin/bash

###############################################################################
# GENERADOR DE COMPONENTES PARA CEREMONIA DE LLAVES
#
# Este script genera componentes aleatorios para ceremonias de custodios
# y calcula el KCV (Key Check Value) de la llave final resultante del XOR.
#
# Uso: ./generar_componentes_kcv.sh [numero_custodios] [algoritmo]
#   numero_custodios: 2 o 3 (default: 2)
#   algoritmo: 3DES, AES-128, AES-192, AES-256 (default: AES-256)
###############################################################################

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Parámetros
NUM_CUSTODIOS=${1:-2}
ALGORITMO=${2:-AES-256}

# Validar número de custodios
if [ "$NUM_CUSTODIOS" -lt 2 ] || [ "$NUM_CUSTODIOS" -gt 3 ]; then
    echo -e "${RED}Error: El número de custodios debe ser 2 o 3${NC}"
    exit 1
fi

# Determinar longitud de llave según algoritmo
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
        echo -e "${RED}Error: Algoritmo no soportado. Use: 3DES, AES-128, AES-192, AES-256${NC}"
        exit 1
        ;;
esac

KEY_HEX_LENGTH=$((KEY_BYTES * 2))

# Banner
echo ""
echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  GENERADOR DE COMPONENTES PARA CEREMONIA DE LLAVES        ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BLUE}Configuración:${NC}"
echo -e "  • Algoritmo: ${GREEN}$ALGORITMO${NC} (${KEY_BITS} bits / ${KEY_BYTES} bytes)"
echo -e "  • Número de custodios: ${GREEN}$NUM_CUSTODIOS${NC}"
echo -e "  • Longitud de componente: ${GREEN}${KEY_HEX_LENGTH} caracteres hex${NC}"
echo ""

# Generar componentes
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}COMPONENTES GENERADOS (Entregar a cada custodio)${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo ""

declare -a COMPONENTES

for i in $(seq 1 $NUM_CUSTODIOS); do
    COMP=$(openssl rand -hex $KEY_BYTES | tr '[:lower:]' '[:upper:]')
    COMPONENTES+=("$COMP")

    echo -e "${GREEN}┌─ CUSTODIO $i ──────────────────────────────────────────────────┐${NC}"
    echo -e "${GREEN}│${NC}"

    # Imprimir componente en líneas de 32 caracteres para mejor legibilidad
    for ((j=0; j<${#COMP}; j+=32)); do
        LINE="${COMP:$j:32}"
        if [ -n "$LINE" ]; then
            echo -e "${GREEN}│${NC}  $LINE"
        fi
    done

    echo -e "${GREEN}│${NC}"
    echo -e "${GREEN}└────────────────────────────────────────────────────────────┘${NC}"
    echo ""
done

# Calcular XOR de todos los componentes y KCV usando Python
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}LLAVE FINAL Y VERIFICACIÓN${NC}"
echo -e "${YELLOW}═══════════════════════════════════════════════════════════${NC}"
echo ""

python3 << PYTHON_SCRIPT
import sys

try:
    from Crypto.Cipher import AES, DES3
    import binascii

    # Componentes
    componentes = [
        $(for comp in "${COMPONENTES[@]}"; do echo "\"$comp\","; done)
    ]

    # Calcular XOR de todos los componentes
    result = bytes.fromhex(componentes[0])
    for comp in componentes[1:]:
        comp_bytes = bytes.fromhex(comp)
        result = bytes(a ^ b for a, b in zip(result, comp_bytes))

    final_key_hex = binascii.hexlify(result).decode().upper()

    print("\033[0;36m┌─ LLAVE FINAL (XOR de componentes) ──────────────────────────┐\033[0m")
    print("\033[0;36m│\033[0m")

    # Imprimir llave en líneas de 32 caracteres
    for i in range(0, len(final_key_hex), 32):
        line = final_key_hex[i:i+32]
        print(f"\033[0;36m│\033[0m  {line}")

    print("\033[0;36m│\033[0m")
    print("\033[0;36m└────────────────────────────────────────────────────────────┘\033[0m")
    print()

    # Calcular KCV
    key_length = len(result)

    if key_length == 16 and "$ALGORITMO" == "3DES":
        cipher = DES3.new(result, DES3.MODE_ECB)
        zeros = b'\x00' * 8
    elif key_length == 24:
        cipher = DES3.new(result, DES3.MODE_ECB)
        zeros = b'\x00' * 8
    elif key_length in [16, 32]:
        cipher = AES.new(result, AES.MODE_ECB)
        zeros = b'\x00' * 16
    else:
        print("\033[0;31mError: Longitud de llave no soportada\033[0m")
        sys.exit(1)

    encrypted = cipher.encrypt(zeros)
    kcv_full = binascii.hexlify(encrypted).decode().upper()
    kcv = kcv_full[:6]

    print("\033[0;32m┌─ KCV (Key Check Value) ─────────────────────────────────────┐\033[0m")
    print("\033[0;32m│\033[0m")
    print(f"\033[0;32m│\033[0m  KCV (3 bytes):     \033[1;33m{kcv}\033[0m")
    print(f"\033[0;32m│\033[0m  KCV completo:      {kcv_full}")
    print("\033[0;32m│\033[0m")
    print("\033[0;32m└────────────────────────────────────────────────────────────┘\033[0m")
    print()

except ImportError:
    print("\033[0;31m✗ Error: pycryptodome no está instalado\033[0m")
    print("\033[0;33mInstala con: pip3 install pycryptodome\033[0m")
    sys.exit(1)
except Exception as e:
    print(f"\033[0;31m✗ Error al calcular KCV: {e}\033[0m")
    sys.exit(1)

PYTHON_SCRIPT

if [ $? -eq 0 ]; then
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}INSTRUCCIONES PARA LA CEREMONIA${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "${BLUE}1.${NC} Guarda esta salida de forma segura"
    echo -e "${BLUE}2.${NC} Entrega cada componente al custodio correspondiente"
    echo -e "${BLUE}3.${NC} En la app, selecciona:"
    echo -e "   • Tipo de llave: ${GREEN}$ALGORITMO${NC}"
    echo -e "   • Número de custodios: ${GREEN}$NUM_CUSTODIOS${NC}"
    echo -e "   • Marca ${GREEN}\"Esta es KEK Storage\"${NC} si corresponde"
    echo -e "${BLUE}4.${NC} Cada custodio ingresa su componente en la app"
    echo -e "${BLUE}5.${NC} Verifica que el KCV mostrado coincida con: ${YELLOW}[Ver arriba]${NC}"
    echo -e "${BLUE}6.${NC} Si coincide: ${GREEN}✓ Ceremonia exitosa${NC}"
    echo -e "${BLUE}7.${NC} Si NO coincide: ${RED}✗ Revisar componentes${NC}"
    echo ""
    echo -e "${YELLOW}⚠️  ADVERTENCIA DE SEGURIDAD:${NC}"
    echo -e "   • NO compartas todos los componentes con una sola persona"
    echo -e "   • Guarda cada componente en medios físicos separados"
    echo -e "   • NO envíes componentes por correo o mensajería"
    echo -e "   • Considera imprimir y guardar en cajas fuertes separadas"
    echo ""

    # Ofrecer guardar en archivo
    read -p "¿Guardar resultado en archivo? (s/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[SsYy]$ ]]; then
        TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        FILENAME="ceremonia_${ALGORITMO//-/}_${NUM_CUSTODIOS}custodios_${TIMESTAMP}.txt"

        {
            echo "════════════════════════════════════════════════════════════"
            echo "CEREMONIA DE LLAVES - $(date '+%Y-%m-%d %H:%M:%S')"
            echo "════════════════════════════════════════════════════════════"
            echo ""
            echo "Configuración:"
            echo "  • Algoritmo: $ALGORITMO ($KEY_BITS bits / $KEY_BYTES bytes)"
            echo "  • Número de custodios: $NUM_CUSTODIOS"
            echo ""
            echo "COMPONENTES:"
            echo "════════════════════════════════════════════════════════════"
            for i in $(seq 1 $NUM_CUSTODIOS); do
                echo ""
                echo "CUSTODIO $i:"
                echo "${COMPONENTES[$((i-1))]}"
            done
            echo ""
            echo "════════════════════════════════════════════════════════════"
            echo "VERIFICACIÓN:"
            echo "════════════════════════════════════════════════════════════"
            echo ""
            echo "El KCV esperado debe mostrarse en la app al finalizar."
            echo "Compare con el KCV calculado arriba."
            echo ""
            echo "════════════════════════════════════════════════════════════"
        } > "$FILENAME"

        echo -e "${GREEN}✓ Guardado en: $FILENAME${NC}"
        echo ""
    fi
else
    echo -e "${RED}✗ Error al generar componentes${NC}"
    exit 1
fi

echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  GENERACIÓN COMPLETADA                                     ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
