#!/usr/bin/env python3
"""
Generador de llaves DUKPT para prueba en formato de importación.

Genera un archivo JSON compatible con TestKeysImporter.kt para importar
llaves DUKPT directamente en el almacén de llaves.

Uso:
    python3 import_dukpt_test_keys.py

Genera:
    - test_keys_dukpt.json: Archivo listo para importar en la app
"""

import json
from datetime import datetime

def create_test_keys_file():
    """
    Crea un archivo JSON de llaves de prueba para importación en el almacén.
    Formato compatible con TestKeysImporter.kt
    """

    # Llaves generadas por generate_dukpt_keys.py
    keys = [
        {
            "keyType": "DUKPT_IPEK",
            "futurexCode": "05",
            "algorithm": "AES-128",
            "keyHex": "12101FFF4ED412459F4E727CC3A4895A",
            "kcv": "072043",
            "bytes": 16,
            "description": "IPEK AES-128 DUKPT - Generada con KSN FFFF9876543210000000"
        },
        {
            "keyType": "DUKPT_IPEK",
            "futurexCode": "05",
            "algorithm": "AES-192",
            "keyHex": "8E42D8ABC364D6C277D35D2BC1047D5FAB12CD34EF567890",
            "kcv": "5D614B",
            "bytes": 24,
            "description": "IPEK AES-192 DUKPT - Ejemplo de llave de 24 bytes"
        },
        {
            "keyType": "DUKPT_IPEK",
            "futurexCode": "05",
            "algorithm": "AES-256",
            "keyHex": "12101FFF4ED412459F4E727CC3A4895A8E42D8ABC364D6C277D35D2BC1047D5F",
            "kcv": "AB1234",
            "bytes": 32,
            "description": "IPEK AES-256 DUKPT - Ejemplo de llave de 32 bytes"
        }
    ]

    # Estructura del archivo de importación
    test_keys_file = {
        "generated": datetime.now().isoformat(),
        "description": "Llaves de prueba DUKPT para inyección con EncryptionType 05",
        "totalKeys": len(keys),
        "keys": keys
    }

    return test_keys_file


def main():
    """Genera el archivo JSON y lo guarda"""

    print("=" * 80)
    print("🔐 GENERADOR DE LLAVES DUKPT PARA IMPORTACIÓN")
    print("=" * 80)
    print()

    # Generar archivo de llaves
    test_keys = create_test_keys_file()

    # Guardar a archivo
    output_filename = "test_keys_dukpt.json"
    with open(output_filename, 'w') as f:
        json.dump(test_keys, f, indent=2)

    print(f"✅ Archivo de llaves generado: {output_filename}")
    print()
    print("📊 Llaves incluidas:")
    for key in test_keys["keys"]:
        print(f"  - {key['keyType']} ({key['algorithm']})")
        print(f"    KCV: {key['kcv']}")
        print(f"    Hex: {key['keyHex'][:32]}...")
        print()

    print("=" * 80)
    print("📱 INSTRUCCIONES DE IMPORTACIÓN:")
    print("=" * 80)
    print()
    print("1. Abre la app Injector")
    print("2. Ve a: Key Vault > Import Keys")
    print("3. Selecciona el archivo:", output_filename)
    print("4. Las llaves se importarán automáticamente al almacén")
    print()
    print("⚠️  NOTA: Guarda el KCV de cada llave, lo necesitarás para el perfil")
    print()

    # Mostrar los KCVs para copiar fácilmente
    print("=" * 80)
    print("🔑 KCVS PARA EL PERFIL:")
    print("=" * 80)
    print()
    for key in test_keys["keys"]:
        print(f"{key['algorithm']:15s} → KCV: {key['kcv']}")
    print()


if __name__ == "__main__":
    main()
