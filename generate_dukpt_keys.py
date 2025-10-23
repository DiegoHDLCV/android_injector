#!/usr/bin/env python3
"""
Script para generar llaves DUKPT (IPEK) a partir de BDK.

Uso:
    python3 generate_dukpt_keys.py

Genera:
    - BDK (Base Derivation Key) AES-128/192/256
    - IPEK (Initial PIN Encryption Key) derivada de BDK
    - KSN (Key Serial Number) inicial
    - KCV (Key Check Value) para cada llave
"""

import os
import hashlib
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend
from typing import Tuple

# ========== CONFIGURACIÓN ==========
# Cambia estos valores según tus necesidades
DUKPT_TYPE = "AES128"  # Opciones: AES128, AES192, AES256, 3DES
KSN_PREFIX = "FFFF9876543210"  # Primeros 14 dígitos hex (7 bytes)

# ========== FUNCIONES AUXILIARES ==========

def bytes_to_hex(data: bytes) -> str:
    """Convierte bytes a string hexadecimal"""
    return data.hex().upper()

def hex_to_bytes(hex_str: str) -> bytes:
    """Convierte string hexadecimal a bytes"""
    return bytes.fromhex(hex_str)

def calculate_kcv(key: bytes, algorithm: str = "AES") -> str:
    """
    Calcula el KCV (Key Check Value) de una llave.

    KCV = primeros 6 caracteres hex del cifrado de zeros con la llave
    """
    if algorithm.startswith("AES"):
        # Para AES: cifrar 16 bytes de zeros
        cipher = Cipher(
            algorithms.AES(key),
            modes.ECB(),
            backend=default_backend()
        )
        encryptor = cipher.encryptor()
        plaintext = b'\x00' * 16
        ciphertext = encryptor.update(plaintext) + encryptor.finalize()
        return bytes_to_hex(ciphertext)[:6]
    else:
        # Para 3DES: cifrar 8 bytes de zeros
        from cryptography.hazmat.primitives.ciphers import algorithms as alg
        cipher = Cipher(
            alg.TripleDES(key),
            modes.ECB(),
            backend=default_backend()
        )
        encryptor = cipher.encryptor()
        plaintext = b'\x00' * 8
        ciphertext = encryptor.update(plaintext) + encryptor.finalize()
        return bytes_to_hex(ciphertext)[:6]

def generate_bdk(key_size: int) -> bytes:
    """
    Genera una BDK (Base Derivation Key) aleatoria.

    Args:
        key_size: Tamaño en bytes (16=AES128, 24=AES192, 32=AES256)

    Returns:
        BDK como bytes
    """
    return os.urandom(key_size)

def derive_ipek_aes(bdk: bytes, ksn: bytes) -> bytes:
    """
    Deriva la IPEK (Initial PIN Encryption Key) desde BDK usando KSN.

    Algoritmo DUKPT estándar para AES:
    1. Tomar primeros 8 bytes del KSN
    2. Limpiar los últimos 21 bits (poner en 0)
    3. Cifrar el KSN modificado con BDK
    4. XOR del resultado con BDK

    Args:
        bdk: Base Derivation Key (16/24/32 bytes)
        ksn: Key Serial Number (10 bytes)

    Returns:
        IPEK (mismo tamaño que BDK)
    """
    # Tomar primeros 8 bytes del KSN
    ksn_partial = ksn[:8]

    # Limpiar últimos 21 bits (poner últimos 3 bytes en 0 excepto primeros 3 bits)
    ksn_modified = bytearray(ksn_partial)
    ksn_modified[5] &= 0xE0  # Limpiar últimos 5 bits del byte 5
    ksn_modified[6] = 0x00   # Byte 6 a cero
    ksn_modified[7] = 0x00   # Byte 7 a cero

    # Pad con zeros hasta llegar al tamaño del bloque AES (16 bytes)
    plaintext = bytes(ksn_modified) + b'\x00' * (16 - len(ksn_modified))

    # Cifrar con BDK
    cipher = Cipher(
        algorithms.AES(bdk),
        modes.ECB(),
        backend=default_backend()
    )
    encryptor = cipher.encryptor()
    encrypted = encryptor.update(plaintext[:16]) + encryptor.finalize()

    # XOR del resultado con BDK
    ipek = bytes(a ^ b for a, b in zip(encrypted[:len(bdk)], bdk))

    return ipek

def derive_ipek_3des(bdk: bytes, ksn: bytes) -> bytes:
    """
    Deriva la IPEK para 3DES (algoritmo diferente a AES).

    Args:
        bdk: Base Derivation Key (16 o 24 bytes para 3DES)
        ksn: Key Serial Number (10 bytes)

    Returns:
        IPEK (16 o 24 bytes)
    """
    from cryptography.hazmat.primitives.ciphers import algorithms as alg

    # Tomar primeros 8 bytes del KSN y limpiar últimos 21 bits
    ksn_partial = ksn[:8]
    ksn_modified = bytearray(ksn_partial)
    ksn_modified[5] &= 0xE0
    ksn_modified[6] = 0x00
    ksn_modified[7] = 0x00

    # Cifrar con 3DES
    cipher = Cipher(
        alg.TripleDES(bdk),
        modes.ECB(),
        backend=default_backend()
    )
    encryptor = cipher.encryptor()
    encrypted = encryptor.update(bytes(ksn_modified)) + encryptor.finalize()

    # XOR con BDK
    ipek = bytes(a ^ b for a, b in zip(encrypted[:len(bdk)], bdk))

    return ipek

def generate_ksn(prefix: str = None) -> Tuple[bytes, str]:
    """
    Genera un KSN (Key Serial Number) de 10 bytes.

    Formato KSN (ANSI X9.24):
    - Bytes 0-4: BDK ID (5 bytes)
    - Bytes 5-7: Device ID (3 bytes)
    - Bytes 8-9: Transaction Counter (2 bytes) - inicia en 0000

    Args:
        prefix: Primeros 14 caracteres hex (7 bytes). Si None, se genera aleatorio.

    Returns:
        (KSN como bytes, KSN como string hex de 20 caracteres)
    """
    if prefix:
        # Usar prefix proporcionado + contador inicial 0000
        if len(prefix) != 14:
            raise ValueError("Prefix debe tener exactamente 14 caracteres hex (7 bytes)")
        ksn_hex = prefix + "000000"  # Agregar 3 bytes de contador (6 caracteres hex)
    else:
        # Generar KSN completamente aleatorio
        ksn_bytes = os.urandom(10)
        # Poner contador inicial en 0 (últimos 3 bytes)
        ksn_array = bytearray(ksn_bytes)
        ksn_array[7] = 0x00
        ksn_array[8] = 0x00
        ksn_array[9] = 0x00
        ksn_hex = bytes_to_hex(bytes(ksn_array))

    ksn_bytes = hex_to_bytes(ksn_hex)
    return ksn_bytes, ksn_hex

# ========== FUNCIÓN PRINCIPAL ==========

def generate_dukpt_keys(dukpt_type: str = "AES128", ksn_prefix: str = None):
    """
    Genera un conjunto completo de llaves DUKPT.

    Args:
        dukpt_type: Tipo de DUKPT (AES128, AES192, AES256, 3DES)
        ksn_prefix: Prefijo opcional para KSN (14 caracteres hex)
    """
    print("=" * 80)
    print("🔐 GENERADOR DE LLAVES DUKPT")
    print("=" * 80)
    print()

    # Determinar tamaño de llave
    if dukpt_type == "AES128":
        key_size = 16
        algorithm = "AES"
    elif dukpt_type == "AES192":
        key_size = 24
        algorithm = "AES"
    elif dukpt_type == "AES256":
        key_size = 32
        algorithm = "AES"
    elif dukpt_type == "3DES":
        key_size = 24  # 3DES 3-key
        algorithm = "3DES"
    else:
        raise ValueError(f"Tipo DUKPT no soportado: {dukpt_type}")

    print(f"📊 Configuración:")
    print(f"   - Tipo DUKPT: {dukpt_type}")
    print(f"   - Tamaño de llave: {key_size} bytes ({key_size * 8} bits)")
    print(f"   - Algoritmo: {algorithm}")
    print()

    # 1. Generar BDK
    print("1️⃣  Generando BDK (Base Derivation Key)...")
    bdk = generate_bdk(key_size)
    bdk_hex = bytes_to_hex(bdk)
    bdk_kcv = calculate_kcv(bdk, algorithm)
    print(f"   ✓ BDK generada:")
    print(f"     Hex: {bdk_hex}")
    print(f"     KCV: {bdk_kcv}")
    print()

    # 2. Generar KSN
    print("2️⃣  Generando KSN (Key Serial Number)...")
    ksn_bytes, ksn_hex = generate_ksn(ksn_prefix)
    print(f"   ✓ KSN generado:")
    print(f"     Hex: {ksn_hex} ({len(ksn_hex)} caracteres)")
    print(f"     Bytes: {ksn_bytes.hex().upper()}")
    print()

    # 3. Derivar IPEK desde BDK
    print("3️⃣  Derivando IPEK desde BDK...")
    if algorithm == "AES":
        ipek = derive_ipek_aes(bdk, ksn_bytes)
    else:
        ipek = derive_ipek_3des(bdk, ksn_bytes)

    ipek_hex = bytes_to_hex(ipek)
    ipek_kcv = calculate_kcv(ipek, algorithm)
    print(f"   ✓ IPEK derivada:")
    print(f"     Hex: {ipek_hex}")
    print(f"     KCV: {ipek_kcv}")
    print()

    # 4. Generar resumen
    print("=" * 80)
    print("📋 RESUMEN DE LLAVES GENERADAS")
    print("=" * 80)
    print()
    print(f"🔑 BDK ({dukpt_type}):")
    print(f"   Llave:  {bdk_hex}")
    print(f"   KCV:    {bdk_kcv}")
    print(f"   Uso:    No inyectar - solo para derivar IPEK")
    print()
    print(f"🔑 IPEK ({dukpt_type}):")
    print(f"   Llave:  {ipek_hex}")
    print(f"   KCV:    {ipek_kcv}")
    print(f"   KSN:    {ksn_hex}")
    print(f"   Uso:    Inyectar en PED con createDukptAESKey()")
    print()

    # 5. Generar comandos para inyectar
    print("=" * 80)
    print("🚀 COMANDOS PARA INYECCIÓN")
    print("=" * 80)
    print()
    print("📱 Configuración del Perfil:")
    print("   - Nombre: DUKPT AES Test")
    print("   - Tipo: Retail")
    print("   - useKTK: NO (DUKPT plaintext)")
    print()
    print("📝 Llave a agregar al perfil:")
    print(f"   - Tipo: DUKPT Initial Key (IPEK)")
    print(f"   - Slot: 01 (hexadecimal)")
    print(f"   - KSN: {ksn_hex}")
    print(f"   - Llave seleccionada: [Crear llave en almacén]")
    print()
    print("⚙️  Datos para crear llave en almacén de llaves:")
    print(f"   - Nombre: IPEK {dukpt_type} Test")
    print(f"   - Algoritmo: {dukpt_type.replace('3DES', 'DES_TRIPLE')}")
    print(f"   - Key Data: {ipek_hex}")
    print(f"   - KCV: {ipek_kcv}")
    print(f"   - Tipo: DUKPT_INITIAL_KEY")
    print()

    # 6. Comando Futurex (si se usa)
    print("=" * 80)
    print("📡 COMANDO FUTUREX (EncryptionType '05' - DUKPT Plaintext)")
    print("=" * 80)
    print()

    # Calcular longitud de la llave en caracteres hex
    key_length_hex = f"{key_size:03d}"  # 3 dígitos: 016, 024, 032

    # Construir comando
    payload = "02"  # Comando: Inyección de llave simétrica
    payload += "01"  # Versión
    payload += "01"  # KeySlot (ejemplo: slot 1)
    payload += "00"  # KtkSlot (no usado para DUKPT plaintext)
    payload += "05"  # KeyType: 05 = DUKPT IPEK
    payload += "05"  # EncryptionType: 05 = DUKPT Plaintext (NUEVO)

    # Determinar KeyAlgorithm
    if dukpt_type == "AES128":
        key_algorithm = "04"  # AES-128
    elif dukpt_type == "AES192":
        key_algorithm = "05"  # AES-192
    elif dukpt_type == "AES256":
        key_algorithm = "06"  # AES-256
    else:  # 3DES
        key_algorithm = "02"  # DES_TRIPLE

    payload += key_algorithm  # KeyAlgorithm
    payload += "00"  # KeySubType
    payload += ipek_kcv[:4]  # KeyChecksum (4 caracteres)
    payload += "0000"  # KtkChecksum (no usado)
    payload += ksn_hex  # KSN (20 caracteres)
    payload += key_length_hex  # KeyLength (3 dígitos)
    payload += ipek_hex  # KeyHex (datos de la llave)

    print(f"Payload completo:")
    print(f"{payload}")
    print()
    print(f"Frame completo (con STX/ETX):")
    # Calcular LRC (XOR de todos los bytes del payload)
    lrc = 0
    for i in range(0, len(payload), 2):
        lrc ^= int(payload[i:i+2], 16)
    lrc_hex = f"{lrc:02X}"

    frame = f"02{payload}03{lrc_hex}"
    print(f"{frame}")
    print()

    # 7. Guardar en archivo
    output_file = f"dukpt_{dukpt_type.lower()}_keys.txt"
    with open(output_file, 'w') as f:
        f.write("=" * 80 + "\n")
        f.write("LLAVES DUKPT GENERADAS\n")
        f.write("=" * 80 + "\n\n")
        f.write(f"Tipo: {dukpt_type}\n")
        f.write(f"Fecha: {__import__('datetime').datetime.now()}\n\n")
        f.write(f"BDK:\n")
        f.write(f"  Hex: {bdk_hex}\n")
        f.write(f"  KCV: {bdk_kcv}\n\n")
        f.write(f"IPEK:\n")
        f.write(f"  Hex: {ipek_hex}\n")
        f.write(f"  KCV: {ipek_kcv}\n\n")
        f.write(f"KSN:\n")
        f.write(f"  Hex: {ksn_hex}\n\n")
        f.write(f"Comando Futurex:\n")
        f.write(f"  {frame}\n")

    print(f"✅ Llaves guardadas en: {output_file}")
    print()
    print("=" * 80)
    print("✅ GENERACIÓN COMPLETADA")
    print("=" * 80)
    print()
    print("📌 Próximos pasos:")
    print("   1. Inyectar IPEK en el PED usando EncryptionType '05'")
    print("   2. Verificar que el KCV coincida")
    print("   3. Probar derivación de llaves de sesión con transacciones")
    print()

if __name__ == "__main__":
    # Ejecutar generador con configuración por defecto
    generate_dukpt_keys(DUKPT_TYPE, KSN_PREFIX)
