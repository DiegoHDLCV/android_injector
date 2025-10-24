#!/usr/bin/env python3
"""
Generador de perfil DUKPT para prueba de inyección.

Crea un archivo JSON de perfil compatible con ProfileViewModel.kt
para importar un perfil listo para inyectar llaves DUKPT.

Uso:
    python3 import_dukpt_profile.py

Genera:
    - dukpt_test_profile.json: Perfil listo para importar
"""

import json
from datetime import datetime


def create_dukpt_profile():
    """
    Crea un perfil DUKPT compatible con ProfileViewModel.

    Estructura esperada por parseProfileJson():
    - name: Nombre del perfil
    - description: Descripción
    - applicationType: Tipo de aplicación
    - useKEK: Si usa KEK
    - selectedKEKKcv: KCV de la KEK (vacío para DUKPT plaintext)
    - keyConfigurations: Lista de configuraciones de llaves
      * usage: Uso de la llave
      * keyType: Tipo de llave
      * slot: Slot en hexadecimal (2 dígitos)
      * selectedKey: KCV de la llave
      * injectionMethod: Método de inyección
      * ksn: KSN para DUKPT (20 caracteres hex)
    """

    profile = {
        "name": "DUKPT AES-128 Test",
        "description": "Perfil para prueba de inyección DUKPT AES-128 con EncryptionType 05",
        "applicationType": "Retail",
        "useKEK": False,  # DUKPT plaintext no requiere KTK/KEK
        "selectedKEKKcv": "",
        "keyConfigurations": [
            {
                "usage": "DUKPT",
                "keyType": "DUKPT Initial Key (IPEK)",
                "slot": "01",  # Slot 01 en hexadecimal
                "selectedKey": "072043",  # KCV de IPEK AES-128
                "injectionMethod": "auto",
                "ksn": "FFFF9876543210000000"  # KSN de 20 caracteres hex
            }
        ]
    }

    return profile


def create_dukpt_profile_multikey():
    """
    Crea un perfil DUKPT con múltiples llaves (una por algoritmo).
    Incluye AES y 3DES.
    """

    profile = {
        "name": "DUKPT Multi-Algorithm Test",
        "description": "Perfil con llaves DUKPT AES-128/192/256 y 3DES (2TDEA/3TDEA) para pruebas",
        "applicationType": "Retail",
        "useKEK": False,
        "selectedKEKKcv": "",
        "keyConfigurations": [
            {
                "usage": "DUKPT",
                "keyType": "DUKPT Initial Key (IPEK)",
                "slot": "01",
                "selectedKey": "072043",  # AES-128
                "injectionMethod": "auto",
                "ksn": "FFFF9876543210000000"
            },
            {
                "usage": "DUKPT",
                "keyType": "DUKPT Initial Key (IPEK)",
                "slot": "02",
                "selectedKey": "5D614B",  # AES-192
                "injectionMethod": "auto",
                "ksn": "FFFF9876543210000001"
            },
            {
                "usage": "DUKPT",
                "keyType": "DUKPT Initial Key (IPEK)",
                "slot": "03",
                "selectedKey": "AB1234",  # AES-256
                "injectionMethod": "auto",
                "ksn": "FFFF9876543210000002"
            },
            {
                "usage": "DUKPT",
                "keyType": "DUKPT Initial Key (IPEK)",
                "slot": "04",
                "selectedKey": "3F8D42",  # 3DES 2TDEA
                "injectionMethod": "auto",
                "ksn": "FFFF9876543210000001"
            },
            {
                "usage": "DUKPT",
                "keyType": "DUKPT Initial Key (IPEK)",
                "slot": "05",
                "selectedKey": "7B5E9C",  # 3DES 3TDEA
                "injectionMethod": "auto",
                "ksn": "FFFF9876543210000002"
            }
        ]
    }

    return profile


def create_dukpt_profile_2tdea():
    """
    Crea un perfil DUKPT con 3DES 2TDEA (Double-length).
    """

    profile = {
        "name": "DUKPT 2TDEA Test",
        "description": "Perfil para prueba de inyección DUKPT 2TDEA (TripleDES Double-length) con EncryptionType 05",
        "applicationType": "Retail",
        "useKEK": False,
        "selectedKEKKcv": "",
        "keyConfigurations": [
            {
                "usage": "DUKPT",
                "keyType": "DUKPT Initial Key (IPEK)",
                "slot": "01",
                "selectedKey": "3F8D42",  # KCV de 3DES 2TDEA
                "injectionMethod": "auto",
                "ksn": "FFFF9876543210000001"
            }
        ]
    }

    return profile


def create_dukpt_profile_3tdea():
    """
    Crea un perfil DUKPT con 3DES 3TDEA (Triple-length).
    """

    profile = {
        "name": "DUKPT 3TDEA Test",
        "description": "Perfil para prueba de inyección DUKPT 3TDEA (TripleDES Triple-length) con EncryptionType 05",
        "applicationType": "Retail",
        "useKEK": False,
        "selectedKEKKcv": "",
        "keyConfigurations": [
            {
                "usage": "DUKPT",
                "keyType": "DUKPT Initial Key (IPEK)",
                "slot": "01",
                "selectedKey": "7B5E9C",  # KCV de 3DES 3TDEA
                "injectionMethod": "auto",
                "ksn": "FFFF9876543210000002"
            }
        ]
    }

    return profile


def main():
    """Genera los archivos de perfil"""

    print("=" * 80)
    print("📋 GENERADOR DE PERFILES DUKPT")
    print("=" * 80)
    print()

    # Generar perfil simple
    print("1️⃣  Generando perfil simple (AES-128)...")
    simple_profile = create_dukpt_profile()
    simple_filename = "dukpt_test_profile.json"
    with open(simple_filename, 'w') as f:
        json.dump(simple_profile, f, indent=2)
    print(f"   ✅ {simple_filename}")
    print()

    # Generar perfil multi-key
    print("2️⃣  Generando perfil multi-key (AES-128/192/256 + 3DES)...")
    multi_profile = create_dukpt_profile_multikey()
    multi_filename = "dukpt_multikey_profile.json"
    with open(multi_filename, 'w') as f:
        json.dump(multi_profile, f, indent=2)
    print(f"   ✅ {multi_filename}")
    print()

    # Generar perfil 2TDEA
    print("3️⃣  Generando perfil 2TDEA (TripleDES Double-length)...")
    tdea2_profile = create_dukpt_profile_2tdea()
    tdea2_filename = "dukpt_2tdea_profile.json"
    with open(tdea2_filename, 'w') as f:
        json.dump(tdea2_profile, f, indent=2)
    print(f"   ✅ {tdea2_filename}")
    print()

    # Generar perfil 3TDEA
    print("4️⃣  Generando perfil 3TDEA (TripleDES Triple-length)...")
    tdea3_profile = create_dukpt_profile_3tdea()
    tdea3_filename = "dukpt_3tdea_profile.json"
    with open(tdea3_filename, 'w') as f:
        json.dump(tdea3_profile, f, indent=2)
    print(f"   ✅ {tdea3_filename}")
    print()

    print("=" * 80)
    print("📱 INSTRUCCIONES DE IMPORTACIÓN:")
    print("=" * 80)
    print()
    print("OPCIÓN 1: Perfil Simple AES-128 (recomendado para primera prueba)")
    print("-" * 80)
    print("1. Abre la app Injector")
    print("2. Ve a: Profiles > Import Profile")
    print("3. Selecciona:", simple_filename)
    print("4. El perfil se importará y estará listo para usar")
    print()

    print("OPCIÓN 2: Perfil Multi-Algorithm (5 algoritmos: AES-128/192/256 + 3DES)")
    print("-" * 80)
    print("1. Abre la app Injector")
    print("2. Ve a: Profiles > Import Profile")
    print("3. Selecciona:", multi_filename)
    print("4. Permite probar 5 algoritmos diferentes en la misma prueba")
    print()

    print("OPCIÓN 3: Perfil Individual 2TDEA")
    print("-" * 80)
    print("1. Abre la app Injector")
    print("2. Ve a: Profiles > Import Profile")
    print("3. Selecciona:", tdea2_filename)
    print("4. Para pruebas aisladas de 3DES Double-length")
    print()

    print("OPCIÓN 4: Perfil Individual 3TDEA")
    print("-" * 80)
    print("1. Abre la app Injector")
    print("2. Ve a: Profiles > Import Profile")
    print("3. Selecciona:", tdea3_filename)
    print("4. Para pruebas aisladas de 3DES Triple-length")
    print()

    print("=" * 80)
    print("⚠️  REQUISITOS PREVIOS:")
    print("=" * 80)
    print()
    print("1. ✅ Importar las llaves primero:")
    print("   - Ejecuta: python3 import_dukpt_test_keys.py")
    print("   - Importa el archivo test_keys_dukpt.json en Injector")
    print()
    print("2. ✅ Luego importar el perfil:")
    print("   - Ejecuta este script")
    print("   - Importa el archivo de perfil en Injector")
    print()
    print("3. ✅ Finalmente, probar la inyección:")
    print("   - Abre KeyReceiver y conecta cable USB")
    print("   - Abre Injector > Raw Data Listener")
    print("   - Copia el comando Futurex de generate_dukpt_keys.py")
    print("   - Envía el comando")
    print()

    print("=" * 80)
    print("📝 ESTRUCTURA DEL PERFIL SIMPLE:")
    print("=" * 80)
    print()
    print(json.dumps(simple_profile, indent=2))
    print()


if __name__ == "__main__":
    main()
