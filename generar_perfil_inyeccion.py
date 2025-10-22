
import json
import sys
from datetime import datetime

# --- Mapeo de Tipos de Llave a Usos del Perfil ---
# Esto se puede personalizar según la lógica de la aplicación.
USAGE_MAPPING = {
    "WORKING_PIN_KEY": "PIN",
    "WORKING_MAC_KEY": "MAC",
    "WORKING_DATA_KEY": "DATA",
    "DUKPT_BDK": "DUKPT",
    "GENERIC": "MASTER" # Asumiendo que las genéricas se usan como maestras
}

def create_injection_profile(keys_filepath):
    """Crea un JSON de perfil de inyección a partir de un archivo de llaves."""
    try:
        with open(keys_filepath, 'r') as f:
            keys_data = json.load(f)
    except FileNotFoundError:
        print(f"Error: El archivo de llaves '{keys_filepath}' no fue encontrado.")
        sys.exit(1)
    except json.JSONDecodeError:
        print(f"Error: El archivo '{keys_filepath}' no es un JSON válido.")
        sys.exit(1)

    # 1. Encontrar la KEK y su KCV
    kek = next((key for key in keys_data['keys'] if key['keyType'] == 'KEK_STORAGE'), None)
    if not kek:
        print("Error: No se encontró una 'KEK_STORAGE' en el archivo de llaves.")
        sys.exit(1)
    
    kek_kcv = kek['kcv']

    # 2. Inicializar la estructura del perfil
    profile = {
        "name": f"Perfil de Inyección - {datetime.now().strftime('%Y%m%d')}",
        "description": f"Perfil generado automáticamente desde {os.path.basename(keys_filepath)}",
        "applicationType": "Retail",
        "useKEK": True, # Usamos la KEK para desencriptar las demás llaves
        "selectedKEKKcv": kek_kcv,
        "keyConfigurations": []
    }

    # 3. Iterar sobre las llaves de trabajo y crear sus configuraciones
    slot_counter = 1
    for key in keys_data['keys']:
        if key['keyType'] == 'KEK_STORAGE':
            continue # No incluimos la KEK en la lista de inyección directa

        usage = USAGE_MAPPING.get(key['keyType'], "UNKNOWN")
        
        key_config = {
            "usage": usage,
            "keyType": key['keyType'],
            "slot": f"{slot_counter:02X}", # Formato hexadecimal de dos dígitos (01, 02, ..., 0A, etc.)
            "selectedKey": key['kcv'], # El KCV identifica la llave a inyectar
            "injectionMethod": "auto",
            "ksn": ""
        }

        # Si es una llave DUKPT, añadir un KSN de ejemplo
        if key['keyType'] == 'DUKPT_BDK':
            key_config["ksn"] = "FFFF9876543210E00000" # KSN de ejemplo

        profile["keyConfigurations"].append(key_config)
        slot_counter += 1

    # 4. Guardar el nuevo perfil en un archivo JSON
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_filename = f"perfil_inyeccion_{timestamp}.json"
    
    with open(output_filename, 'w') as f:
        json.dump(profile, f, indent=2)

    print(f"Perfil de inyección generado exitosamente: {output_filename}")
    print(f"Total de configuraciones de llave: {len(profile['keyConfigurations'])}")

def main():
    if len(sys.argv) < 2:
        print("Uso: python generar_perfil_inyeccion.py <ruta_al_archivo_de_llaves.json>")
        sys.exit(1)
    
    keys_filepath = sys.argv[1]
    create_injection_profile(keys_filepath)

if __name__ == "__main__":
    # Necesario para obtener el nombre del archivo base
    import os
    main()

