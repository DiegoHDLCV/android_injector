import hashlib
import logging
from typing import List
from Crypto.Cipher import DES, DES3
from Crypto.Util.Padding import pad, unpad

log = logging.getLogger(__name__)

def calculate_key_checksum(key_hex):
    """
    Calcula el KCV (Key Checksum Value) según estándar GlobalPlatform/GSMA.
    
    El KCV se calcula encriptando un bloque de 8 bytes con valor '00' usando
    DES/3DES y tomando los primeros 6 dígitos hexadecimales (3 bytes) del resultado.
    
    Args:
        key_hex: Llave en formato hexadecimal (16, 32 o 48 caracteres)
    
    Returns:
        KCV como string hexadecimal de 6 caracteres (ej: "970CDC")
    """
    if not key_hex:
        return "000000"
    
    try:
        # Convertir hex a bytes
        key_bytes = bytes.fromhex(key_hex)
        
        # Bloque de 8 bytes con valor '00' para DES/3DES según estándar
        plaintext = b'\x00' * 8
        
        # Determinar el tipo de cifrado según la longitud de la llave
        if len(key_bytes) == 8:
            # DES (8 bytes / 16 caracteres hex)
            cipher = DES.new(key_bytes, DES.MODE_ECB)
            log.debug(f"Calculando KCV con DES para llave de {len(key_bytes)} bytes")
        elif len(key_bytes) == 16:
            # 3DES con 2 llaves (16 bytes / 32 caracteres hex) -> se repite la primera llave: K1, K2, K1
            key_3des = key_bytes + key_bytes[:8]
            try:
                cipher = DES3.new(key_3des, DES3.MODE_ECB)
                log.debug(f"Calculando KCV con 3DES-2K para llave de {len(key_bytes)} bytes")
            except ValueError as e:
                if "degenerates to single DES" in str(e):
                    # Si K1=K2, usar solo DES con la primera llave
                    log.debug("3DES degenera a DES, usando DES simple")
                    cipher = DES.new(key_bytes[:8], DES.MODE_ECB)
                else:
                    raise
        elif len(key_bytes) == 24:
            # 3DES con 3 llaves (24 bytes / 48 caracteres hex)
            try:
                cipher = DES3.new(key_bytes, DES3.MODE_ECB)
                log.debug(f"Calculando KCV con 3DES-3K para llave de {len(key_bytes)} bytes")
            except ValueError as e:
                if "degenerates to single DES" in str(e):
                    # Si todas las llaves son iguales, usar solo DES con la primera llave
                    log.debug("3DES degenera a DES, usando DES simple")
                    cipher = DES.new(key_bytes[:8], DES.MODE_ECB)
                else:
                    raise
        else:
            # Longitud no válida para DES/3DES, usar SHA-1 como fallback
            log.warning(f"Longitud de llave no válida para DES/3DES ({len(key_bytes)} bytes), usando fallback SHA-1")
            sha1_hash = hashlib.sha1(key_bytes).digest()
            return sha1_hash[:3].hex().upper()
        
        # Encriptar el bloque de ceros
        encrypted = cipher.encrypt(plaintext)
        
        # Tomar los primeros 3 bytes (6 dígitos hex) como KCV según estándar
        kcv = encrypted[:3].hex().upper()
        
        log.debug(f"KCV calculado: {kcv} para llave {key_hex[:8]}...")
        return kcv
        
    except Exception as e:
        log.error(f"Error calculando KCV para llave {key_hex[:8] if key_hex else 'None'}...: {e}")
        return "000000"

def xor_hex_strings(hex_strings: List[str]) -> str:
    """
    Combina múltiples strings hexadecimales usando XOR.
    
    Args:
        hex_strings: Lista de strings hexadecimales a combinar
    
    Returns:
        String hexadecimal resultado del XOR
    """
    if not hex_strings:
        return ""
    
    try:
        # Convertir el primer string a bytes
        result_bytes = bytes.fromhex(hex_strings[0])
        
        # XOR con cada uno de los siguientes strings
        for hex_string in hex_strings[1:]:
            current_bytes = bytes.fromhex(hex_string)
            
            # Tomar la longitud mínima para evitar errores
            min_length = min(len(result_bytes), len(current_bytes))
            
            # Realizar XOR byte por byte
            result_bytes = bytes(a ^ b for a, b in zip(result_bytes[:min_length], current_bytes[:min_length]))
        
        return result_bytes.hex().upper()
        
    except Exception as e:
        log.error(f"Error en XOR de strings hex: {e}")
        return hex_strings[0] if hex_strings else ""

def derive_ipek(bdk_hex: str, ksn_hex: str) -> str:
    """
    Deriva una IPEK (Initial PIN Encryption Key) desde un BDK y KSN.
    
    Args:
        bdk_hex: Base Derivation Key en formato hexadecimal
        ksn_hex: Key Serial Number en formato hexadecimal
    
    Returns:
        IPEK derivada en formato hexadecimal
    """
    try:
        # Implementación simplificada de derivación IPEK
        # En producción se usaría el algoritmo DUKPT completo
        
        bdk_bytes = bytes.fromhex(bdk_hex)
        ksn_bytes = bytes.fromhex(ksn_hex)
        
        # Simular derivación combinando BDK y KSN
        combined = bdk_bytes + ksn_bytes
        hash_result = hashlib.sha256(combined).digest()
        
        # Tomar 16 bytes (128 bits) para la IPEK
        ipek = hash_result[:16]
        
        log.info(f"IPEK derivada desde BDK {bdk_hex[:8]}... y KSN {ksn_hex}")
        return ipek.hex().upper()
        
    except Exception as e:
        log.error(f"Error derivando IPEK: {e}")
        return bdk_hex  # Fallback al BDK original

class LocalCryptoUtils:
    """
    Utilidades para cifrado local de llaves antes del envío al dispositivo.
    Proporciona mayor seguridad al cifrar working keys con KTKs antes de la transmisión.
    """
    
    @staticmethod
    def encrypt_working_key_3des(working_key_hex: str, ktk_hex: str) -> str:
        """
        Cifra una working key usando 3DES con una KTK.
        
        Args:
            working_key_hex: Working key en formato hexadecimal (16 o 32 caracteres)
            ktk_hex: Key Transfer Key en formato hexadecimal (32 caracteres)
        
        Returns:
            Working key cifrada en formato hexadecimal
        """
        try:
            log.info("LocalCryptoUtils: Iniciando cifrado 3DES local")
            log.info(f"Working key length: {len(working_key_hex)} caracteres")
            log.info(f"KTK length: {len(ktk_hex)} caracteres")
            
            # Convertir a bytes
            working_key_bytes = bytes.fromhex(working_key_hex)
            ktk_bytes = bytes.fromhex(ktk_hex)
            
            # Ajustar KTK a 24 bytes para 3DES (Triple DES)
            if len(ktk_bytes) == 16:
                # DES3 con 2 llaves: K1, K2, K1
                ktk_3des = ktk_bytes + ktk_bytes[:8]
            elif len(ktk_bytes) == 24:
                # DES3 con 3 llaves: K1, K2, K3
                ktk_3des = ktk_bytes
            else:
                # Ajustar a 24 bytes
                ktk_3des = (ktk_bytes * 3)[:24]
            
            log.info(f"KTK ajustada para 3DES: {len(ktk_3des)} bytes")
            
            # Crear cifrador 3DES en modo ECB
            cipher = DES3.new(ktk_3des, DES3.MODE_ECB)
            
            # Aplicar padding PKCS7 si es necesario
            if len(working_key_bytes) % 8 != 0:
                working_key_padded = pad(working_key_bytes, 8)
                log.info(f"Padding aplicado: {len(working_key_bytes)} -> {len(working_key_padded)} bytes")
            else:
                working_key_padded = working_key_bytes
                log.info("No se requiere padding")
            
            # Cifrar
            encrypted_bytes = cipher.encrypt(working_key_padded)
            encrypted_hex = encrypted_bytes.hex().upper()
            
            log.info(f"Cifrado completado: {len(encrypted_hex)} caracteres hex")
            log.info("LocalCryptoUtils: Cifrado 3DES local exitoso")
            
            return encrypted_hex
            
        except Exception as e:
            log.exception(f"Error en cifrado 3DES local: {e}")
            # En caso de error, devolver la llave original sin cifrar
            log.warning("Devolviendo working key sin cifrar debido al error")
            return working_key_hex
    
    @staticmethod
    def decrypt_working_key_3des(encrypted_key_hex: str, ktk_hex: str) -> str:
        """
        Descifra una working key usando 3DES con una KTK.
        
        Args:
            encrypted_key_hex: Working key cifrada en formato hexadecimal
            ktk_hex: Key Transfer Key en formato hexadecimal
        
        Returns:
            Working key descifrada en formato hexadecimal
        """
        try:
            log.info("LocalCryptoUtils: Iniciando descifrado 3DES local")
            
            # Convertir a bytes
            encrypted_bytes = bytes.fromhex(encrypted_key_hex)
            ktk_bytes = bytes.fromhex(ktk_hex)
            
            # Ajustar KTK a 24 bytes para 3DES
            if len(ktk_bytes) == 16:
                ktk_3des = ktk_bytes + ktk_bytes[:8]
            elif len(ktk_bytes) == 24:
                ktk_3des = ktk_bytes
            else:
                ktk_3des = (ktk_bytes * 3)[:24]
            
            # Crear descifrador 3DES en modo ECB
            cipher = DES3.new(ktk_3des, DES3.MODE_ECB)
            
            # Descifrar
            decrypted_bytes = cipher.decrypt(encrypted_bytes)
            
            # Remover padding si es necesario
            try:
                decrypted_unpadded = unpad(decrypted_bytes, 8)
            except ValueError:
                # Si no hay padding válido, usar los bytes tal como están
                decrypted_unpadded = decrypted_bytes
            
            decrypted_hex = decrypted_unpadded.hex().upper()
            
            log.info("LocalCryptoUtils: Descifrado 3DES local exitoso")
            return decrypted_hex
            
        except Exception as e:
            log.exception(f"Error en descifrado 3DES local: {e}")
            # En caso de error, devolver la llave cifrada
            log.warning("Devolviendo llave cifrada debido al error en descifrado")
            return encrypted_key_hex

def generate_random_key(length_bytes: int = 16) -> str:
    """
    Genera una llave criptográfica aleatoria.
    
    Args:
        length_bytes: Longitud de la llave en bytes (default: 16 = 128 bits)
    
    Returns:
        Llave aleatoria en formato hexadecimal
    """
    import secrets
    return secrets.token_hex(length_bytes).upper()

def validate_hex_string(hex_string: str, expected_length: int = None) -> bool:
    """
    Valida que un string sea hexadecimal válido.
    
    Args:
        hex_string: String a validar
        expected_length: Longitud esperada en caracteres (opcional)
    
    Returns:
        True si es válido, False en caso contrario
    """
    if not hex_string:
        return False
    
    try:
        bytes.fromhex(hex_string)
        if expected_length and len(hex_string) != expected_length:
            return False
        return True
    except ValueError:
        return False 