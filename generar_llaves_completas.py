import json
import os
from datetime import datetime
from Crypto.Cipher import AES, DES3
from Crypto.Random import get_random_bytes

# --- Función de Ayuda Criptográfica ---

def calculate_kcv(key_bytes, algorithm):
    """Calcula el Key Check Value (KCV) para una llave dada."""
    try:
        if 'DES' in algorithm:
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

# --- Generador de Llaves ---

def generate_key(key_type, algorithm, description):
    """Genera una llave con su KCV, todo en texto plano."""
    key_info = {
        "keyType": key_type,
        "algorithm": algorithm,
        "description": description,
        "futurexCode": "00" # Código de ejemplo, ajustar si es necesario
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
    
    key_info["keyHex"] = key_hex
    key_info["kcv"] = calculate_kcv(key_bytes, algorithm)
    key_info["bytes"] = len(key_bytes)
        
    return key_info

# --- Script Principal ---

def main():
    """Función principal para generar el archivo de llaves en texto plano."""
    
    # Modificado para generar solo llaves maestras y la KEK
    keys_to_generate = [
        ("KEK_STORAGE", "AES-256", "KEK de almacenamiento generada dinámicamente."),
        ("MASTER_KEY", "3DES-16", "Master Key (3DES-128)"),
        ("MASTER_KEY", "3DES-24", "Master Key (3DES-192)"),
        ("MASTER_KEY", "AES-128", "Master Key (AES-128)"),
        ("MASTER_KEY", "AES-192", "Master Key (AES-192)"),
        ("MASTER_KEY", "AES-256", "Master Key (AES-256)"),
        ("DUKPT_BDK", "3DES-16", "BDK para derivación de llaves DUKPT (Master)"),
    ]

    generated_keys = []
    for key_type, algorithm, description in keys_to_generate:
        try:
            key = generate_key(key_type, algorithm, description)
            generated_keys.append(key)
        except ValueError as e:
            print(f"Omitiendo llave debido a un error: {e}")

    output_data = {
        "generated": datetime.now().isoformat(),
        "description": "Archivo de llaves maestras (todas en texto plano) para importación.",
        "totalKeys": len(generated_keys),
        "keys": generated_keys
    }

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = f"llaves_maestras_plaintext_{timestamp}.json"
    
    with open(filename, 'w') as f:
        json.dump(output_data, f, indent=2)

    print(f"\nArchivo de llaves maestras en texto plano generado exitosamente: {filename}")
    print(f"Total de llaves en el archivo: {len(generated_keys)}")

if __name__ == "__main__":
    main()