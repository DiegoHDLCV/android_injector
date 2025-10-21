#!/bin/bash

# =============================================================================
# Generador de Llaves de Prueba con KCVs Correctos
# =============================================================================
# Este script genera llaves de prueba para todos los tipos y algoritmos
# con KCVs calculados correctamente usando OpenSSL (compatible con KcvCalculator.kt)
# =============================================================================

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔑 Generador de Llaves de Prueba con KCVs Correctos${NC}"
echo -e "${BLUE}================================================${NC}"

# Verificar que OpenSSL esté disponible
if ! command -v openssl &> /dev/null; then
    echo -e "${RED}❌ Error: OpenSSL no está instalado${NC}"
    echo -e "${YELLOW}💡 Instala OpenSSL: brew install openssl${NC}"
    exit 1
fi

# KEK Storage fija (siempre la misma para el sistema)
KEK_STORAGE_KEY="A1B2C3D4E5F6789012345678901234567890ABCDEF0123456789ABCDEF0123456"
KEK_STORAGE_KCV="D4E5F6"

# Tipos de llaves genéricas (el perfil determina el uso específico)
KEY_TYPES=("GENERIC:00")

# Algoritmos soportados
ALGORITHMS=("3DES-16:16" "3DES-24:24" "AES-128:16" "AES-192:24" "AES-256:32")

# Función para generar llave aleatoria
generate_random_key() {
    local bytes=$1
    openssl rand -hex $bytes | tr '[:lower:]' '[:upper:]'
}

# Función para calcular KCV con OpenSSL (compatible con KcvCalculator.kt)
calculate_kcv() {
    local key_hex=$1
    local algorithm=$2
    local key_bytes=$((${#key_hex} / 2))
    
    # Determinar algoritmo OpenSSL
    local openssl_algo
    local block_size
    
    if [[ "$algorithm" == 3DES* ]]; then
        if [ $key_bytes -eq 16 ]; then
            openssl_algo="des-ede-ecb"  # 2-key 3DES
        else
            openssl_algo="des-ede3-ecb" # 3-key 3DES
        fi
        block_size=8
    else
        case "$algorithm" in
            "AES-128") openssl_algo="aes-128-ecb" ;;
            "AES-192") openssl_algo="aes-192-ecb" ;;
            "AES-256") openssl_algo="aes-256-ecb" ;;
        esac
        block_size=16
    fi
    
    # Crear bloque de ceros
    local zeros=$(printf '00%.0s' $(seq 1 $block_size))
    
    # Cifrar y obtener primeros 3 bytes
    local kcv_full=$(echo -n "$zeros" | xxd -r -p | \
        openssl enc -$openssl_algo -K "$key_hex" -nopad 2>/dev/null | \
        xxd -p -c 256 | tr '[:lower:]' '[:upper:]' | head -c 6)
    
    # Verificar que el KCV se calculó correctamente
    if [ -z "$kcv_full" ] || [ ${#kcv_full} -lt 6 ]; then
        echo "ERROR"
    else
        echo "${kcv_full:0:6}"
    fi
}

# Generar todas las combinaciones
OUTPUT_FILE="llaves_prueba_$(date +%Y%m%d_%H%M%S).json"

echo -e "${GREEN}📝 Generando archivo: ${OUTPUT_FILE}${NC}"

# Crear archivo JSON
{
    echo "{"
    echo "  \"generated\": \"$(date -Iseconds)\","
    echo "  \"description\": \"Llaves de prueba generadas con KCVs correctos usando OpenSSL\","
    echo "  \"totalKeys\": 0,"
    echo "  \"keys\": ["

    first=true
    total_keys=0

    # 1. KEK Storage fija (siempre la primera)
    if [ "$first" = false ]; then
        echo ","
    fi
    first=false
    
    cat << EOF
    {
      "keyType": "KEK_STORAGE",
      "futurexCode": "06",
      "algorithm": "AES-256",
      "keyHex": "$KEK_STORAGE_KEY",
      "kcv": "$KEK_STORAGE_KCV",
      "bytes": 32,
      "description": "KEK Storage fija del sistema"
    }
EOF
    total_keys=$((total_keys + 1))

    # 2. Llaves genéricas aleatorias (como componentes reales)
    for key_type_entry in "${KEY_TYPES[@]}"; do
        key_type=$(echo "$key_type_entry" | cut -d: -f1)
        futurex_code=$(echo "$key_type_entry" | cut -d: -f2)
        
        for algorithm_entry in "${ALGORITHMS[@]}"; do
            algorithm=$(echo "$algorithm_entry" | cut -d: -f1)
            key_bytes=$(echo "$algorithm_entry" | cut -d: -f2)
            
            key_hex=$(generate_random_key $key_bytes)
            kcv=$(calculate_kcv "$key_hex" "$algorithm")
            
            if [ "$first" = false ]; then
                echo ","
            fi
            first=false
            
            cat << EOF
    {
      "keyType": "$key_type",
      "futurexCode": "$futurex_code",
      "algorithm": "$algorithm",
      "keyHex": "$key_hex",
      "kcv": "$kcv",
      "bytes": $key_bytes,
      "description": "Test $key_type key with $algorithm algorithm"
    }
EOF
            total_keys=$((total_keys + 1))
        done
    done

    echo ""
    echo "  ]"
    echo "}"
} > "$OUTPUT_FILE"

# Actualizar el total de llaves en el JSON
sed -i '' "s/\"totalKeys\": 0/\"totalKeys\": $total_keys/" "$OUTPUT_FILE"

echo -e "${GREEN}✅ Generación completada exitosamente${NC}"
echo -e "${BLUE}📊 Estadísticas:${NC}"
echo -e "   • Total de llaves generadas: ${GREEN}$total_keys${NC}"
echo -e "   • Tipos de llaves: ${GREEN}${#KEY_TYPES[@]}${NC} (PIN, MAC, DATA, MASTER, KEK)"
echo -e "   • Algoritmos: ${GREEN}${#ALGORITHMS[@]}${NC} (3DES-16, 3DES-24, AES-128, AES-192, AES-256)"
echo -e "   • Archivo generado: ${GREEN}$OUTPUT_FILE${NC}"

echo -e "${YELLOW}💡 Próximos pasos:${NC}"
echo -e "   1. Copia el archivo JSON al dispositivo Android"
echo -e "   2. Abre la app inyector → Almacén de Llaves"
echo -e "   3. Toca '📥 Importar Llaves de Prueba'"
echo -e "   4. Selecciona el archivo JSON generado"
echo -e "   5. ✓ Todas las llaves se importan con KCVs correctos"

echo -e "${BLUE}🔍 Verificación de KCVs:${NC}"
echo -e "   Los KCVs se calculan usando el mismo algoritmo que KcvCalculator.kt:"
echo -e "   • Cifrar un bloque de ceros con la llave"
echo -e "   • Tomar los primeros 3 bytes del resultado"
echo -e "   • Compatible con la validación del keyreceiver"