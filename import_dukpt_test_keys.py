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

    IMPORTANTE: Siempre debe incluir una KEK STORAGE como llave maestra
    """

    # Llaves generadas por generate_dukpt_keys.py
    keys = [
        # OBLIGATORIO: KEK STORAGE (llave maestra del sistema)
        {
            "keyType": "KEK_STORAGE",
            "futurexCode": "00",
            "algorithm": "AES-256",
            "keyHex": "E14007267311EBDA872B46AF9B1A086AE9348938BA25AF8CBD69DD5A7F896838",
            "kcv": "112A8B",
            "bytes": 32,
            "description": "KEK STORAGE - Llave maestra del sistema para DUKPT"
        },
        # DUKPT IPEK Llaves AES
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
        },
        # DUKPT IPEK Llaves 3DES (TripleDES)
        {
            "keyType": "DUKPT_IPEK",
            "futurexCode": "05",
            "algorithm": "DES_DOUBLE",
            "keyHex": "C4F5B7A9D2E8F3A6B9C2D5E8F1A4B7C9",
            "kcv": "3F8D42",
            "bytes": 16,
            "description": "IPEK 2TDEA DUKPT - Generada con KSN FFFF9876543210000001"
        },
        {
            "keyType": "DUKPT_IPEK",
            "futurexCode": "05",
            "algorithm": "DES_TRIPLE",
            "keyHex": "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3F00",
            "kcv": "7B5E9C",
            "bytes": 24,
            "description": "IPEK 3TDEA DUKPT - Generada con KSN FFFF9876543210000002"
        }
    ]

    # Estructura del archivo de importación
    test_keys_file = {
        "generated": datetime.now().isoformat(),
        "description": "Llaves de prueba DUKPT para inyección con EncryptionType 05 - Incluye AES (128/192/256) y 3DES (2TDEA/3TDEA) - (incluye KEK STORAGE obligatoria)",
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
        key_type_display = f"{key['keyType']} ({key['algorithm']})"
        print(f"  - {key_type_display}")
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
    print("⚠️  NOTA: Guarda los KCVs de cada llave, los necesitarás para el perfil")
    print()

    # Mostrar los KCVs para copiar fácilmente
    print("=" * 80)
    print("🔑 KCVS IMPORTANTES:")
    print("=" * 80)
    print()
    print("KEK STORAGE (obligatoria):")
    kek = next((k for k in test_keys["keys"] if k["keyType"] == "KEK_STORAGE"), None)
    if kek:
        print(f"  KEK STORAGE → KCV: {kek['kcv']}")
    print()
    print("DUKPT IPEK (para inyección):")
    for key in test_keys["keys"]:
        if key["keyType"] == "DUKPT_IPEK":
            print(f"  {key['algorithm']:12s} → KCV: {key['kcv']}")
    print()


if __name__ == "__main__":
    main()
