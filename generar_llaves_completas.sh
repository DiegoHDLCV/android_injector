#!/bin/bash

# Script de shell para generar un archivo de llaves criptográficas.
# Utiliza un script de Python embebido para garantizar la correcta y segura
# ejecución de las operaciones criptográficas.

# --- Verificación de Dependencias ---
command -v python3 >/dev/null 2>&1 || { echo >&2 "Se requiere Python 3, pero no está instalado. Abortando."; exit 1; }

# Intenta importar pycryptodome. Si falla, muestra instrucciones.
python3 -c "from Crypto.Cipher import AES" >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "La librería 'pycryptodome' no está instalada."
    echo "Por favor, ejecute: pip install pycryptodome"
    exit 1
fi

echo "Dependencias verificadas. Ejecutando el generador de llaves..."

# --- Ejecución del Script de Python Embebido ---
python3 - <<'END_PYTHON'

import json
import os
from datetime import datetime
from Crypto.Cipher import AES, DES3
from Crypto.Random import get_random_bytes

# --- Funciones de Ayuda Criptográfica ---

def calculate_kcv(key_bytes, algorithm):
    """Calcula el Key Check Value (KCV) para una llave dada."""
    try:
        if 'DES3' in algorithm:
            cipher = DES3.new(key_bytes, DES3.MODE_ECB)
            kcv = cipher.encrypt(b'\x00' * 8)
        elif 'AES' in algorithm:
            cipher = AES.new(key_bytes, AES.MODE_ECB)
            kcv = cipher.encrypt(b'\x00' * 16)
        else:
            return "000000"
        return kcv[:3].hex().upper()
    except Exception as e:
        print(f"Error calculando KCV para {algorithm}: {e}")
        return "ERROR"

def encrypt_key_with_kek(key_to_encrypt_hex, kek_bytes):
    """Encripta una llave (en formato hexadecimal) usando la KEK con AES-GCM."""
    key_to_encrypt_bytes = bytes.fromhex(key_to_encrypt_hex)
    # Usamos un nonce de 12 bytes, común para GCM
    nonce = get_random_bytes(12)
    cipher = AES.new(kek_bytes, AES.MODE_GCM, nonce=nonce)
    ciphertext, tag = cipher.encrypt_and_digest(key_to_encrypt_bytes)
    return {
        "encryptedKeyHex": ciphertext.hex().upper(),
        "nonce": nonce.hex().upper(),
        "tag": tag.hex().upper()
    }

# --- Generadores de Llaves ---

def generate_key(key_type, algorithm, description, kek_bytes=None):
    """Genera una llave, la encripta si se proporciona una KEK y la formatea."""
    key_info = {
        "keyType": key_type,
        "algorithm": algorithm,
        "description": description
    }
    
    size_map = {
        "3DES-16": 16, "3DES-24": 24,
        "AES-128": 16, "AES-192": 24, "AES-256": 32
    }
    
    if algorithm not in size_map:
        raise ValueError(f"Algoritmo no soportado: {algorithm}")
        
    key_bytes = get_random_bytes(size_map[algorithm])
    
    if "DES" in algorithm:
        key_bytes = DES3.adjust_key_parity(key_bytes)

    key_hex = key_bytes.hex().upper()
    
    key_info["kcv"] = calculate_kcv(key_bytes, algorithm)
    key_info["bytes"] = len(key_bytes)

    if kek_bytes:
        encrypted_data = encrypt_key_with_kek(key_hex, kek_bytes)
        key_info.update(encrypted_data)
    else:
        key_info["keyHex"] = key_hex
        
    return key_info

# --- Script Principal ---

def main():
    """Función principal para generar el archivo de llaves."""
    
    kek_storage_bytes = get_random_bytes(32) # AES-256
    kek_storage = {
        "keyType": "KEK_STORAGE",
        "algorithm": "AES-256",
        "keyHex": kek_storage_bytes.hex().upper(),
        "kcv": calculate_kcv(kek_storage_bytes, "AES-256"),
        "bytes": 32,
        "description": "KEK de almacenamiento generada dinámicamente para encriptar otras llaves."
    }

    keys_to_generate = [
        ("WORKING_PIN_KEY", "3DES-16", "Llave de PIN (3DES-128)"),
        ("WORKING_PIN_KEY", "AES-128", "Llave de PIN (AES-128)"),
        ("WORKING_PIN_KEY", "AES-256", "Llave de PIN (AES-256)"),
        ("WORKING_MAC_KEY", "3DES-16", "Llave de MAC (3DES-128)"),
        ("WORKING_MAC_KEY", "AES-256", "Llave de MAC (AES-256)"),
        ("WORKING_DATA_KEY", "3DES-24", "Llave de Datos (3DES-192)"),
        ("WORKING_DATA_KEY", "AES-256", "Llave de Datos (AES-256)"),
        ("DUKPT_BDK", "3DES-16", "BDK para derivación de llaves DUKPT"),
        ("GENERIC", "AES-256", "Llave genérica AES-256"),
    ]

    generated_keys = [kek_storage]
    for key_type, algorithm, description in keys_to_generate:
        try:
            key = generate_key(key_type, algorithm, description, kek_bytes=kek_storage_bytes)
            generated_keys.append(key)
        except ValueError as e:
            print(f"Omitiendo llave debido a un error: {e}")

    output_data = {
        "generated": datetime.now().isoformat(),
        "description": "Archivo de llaves completo con KEK y llaves de trabajo encriptadas.",
        "totalKeys": len(generated_keys),
        "keys": generated_keys
    }

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"llaves_generadas_{timestamp}.json"
    
    with open(filename, 'w') as f:
        json.dump(output_data, f, indent=2)

    print(f"\nArchivo de llaves generado exitosamente: {filename}")
    print(f"Total de llaves en el archivo: {len(generated_keys)}")

if __name__ == "__main__":
    main()

END_PYTHON
