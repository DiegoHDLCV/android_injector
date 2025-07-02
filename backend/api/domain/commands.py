import logging
from .base_message import Message, FuturexMessage

log = logging.getLogger(__name__)

# --- Helper Function ---
def _validate_hex(hex_str: str, field_name: str, allow_empty: bool = False, fixed_len: int = None):
    """Valida que un string sea hexadecimal y opcionalmente su longitud."""
    if not hex_str:
        if allow_empty: return
        raise ValueError(f"El campo '{field_name}' no puede estar vacío.")
    if not all(c in '0123456789abcdefABCDEF' for c in hex_str):
        raise ValueError(f"El campo '{field_name}' ('{hex_str}') no es un string hexadecimal válido.")
    if len(hex_str) % 2 != 0:
        raise ValueError(f"El campo '{field_name}' ('{hex_str}') debe tener una longitud par.")
    if fixed_len is not None and len(hex_str) != fixed_len:
        raise ValueError(f"El campo '{field_name}' debe tener {fixed_len} caracteres, pero tiene {len(hex_str)}.")

# --- CLASE AÑADIDA PARA SOLUCIONAR EL IMPORT ERROR ---
class CommandResponseGeneric(Message):
    """
    Clase genérica para parsear respuestas del protocolo legacy que usa '|' como separador.
    """
    COMMAND = None
    FIELD_RESPONSE_CODE = 0
    def __init__(self, data: bytes, expected_command_code: bytes):
        self.COMMAND = expected_command_code
        super().__init__(command=self.COMMAND, data=data)
        self.response_code_from_ped = "XX"; self.parsed_fields = []; self.raw_data_payload = ""
        try:
            data_str = self.data.decode('ascii', errors='replace')
            self.parsed_fields = data_str.split('|')
            if not self.parsed_fields or not self.parsed_fields[0]: self.raw_data_payload = data_str; return
            self.response_code_from_ped = self.parsed_fields[0]
            if len(self.parsed_fields) > 1: self.raw_data_payload = "|".join(self.parsed_fields[1:])
        except Exception: self.raw_data_payload = self.data.decode('ascii', errors='replace')
    
    def is_ok(self) -> bool: return getattr(self, 'response_code_from_ped', 'XX') == '00'
    
    def as_dict(self) -> dict:
        return {"command_received_by_ped_handler": self.COMMAND.decode(errors='replace') if self.COMMAND else "N/A",
                "response_code_from_ped": self.response_code_from_ped,
                "additional_data_payload": self.raw_data_payload,
                "full_payload_fields_received": self.parsed_fields}

class CommandFuturexReadSerial(FuturexMessage):
    """Comando '03' de Futurex para leer el número de serie del terminal."""
    COMMAND_CODE = b'03'
    COMMAND_VERSION = b'01'

    def __init__(self):
        payload = self.COMMAND_CODE + self.COMMAND_VERSION
        super().__init__(payload=payload)
        log.debug(f"Comando {self.COMMAND_CODE.decode()} construido con payload: {payload.hex(' ').upper()}")

class CommandFuturexInjectSymmetricKey(FuturexMessage):
    """Comando '02' de Futurex para inyectar una clave simétrica."""
    COMMAND_CODE = b'02'
    COMMAND_VERSION = b'01'

    def __init__(self, key_slot: int = None, ktk_slot: int = None, key_type: str = None, encryption_type: str = None,
                 key_checksum: str = None, ktk_checksum: str = None, ksn: str = None,
                 key_hex: str = None, ktk_hex: str = "", message: str = None):
        
        if message:
            payload = message.encode('ascii')
            super().__init__(payload=payload)
            log.debug(f"Comando 02 construido con mensaje predefinido: {message}")
            return

        # Constructor original con validación de parámetros
        if key_slot is None or ktk_slot is None or key_type is None or encryption_type is None or key_checksum is None or key_hex is None:
            raise ValueError("Todos los parámetros son requeridos excepto ktk_hex cuando se usa el constructor completo.")

        # Construcción del payload
        payload_parts = []
        payload_parts.append(self.COMMAND_CODE)
        payload_parts.append(self.COMMAND_VERSION)
        payload_parts.append(f"{key_slot:02X}".encode('ascii'))
        payload_parts.append(f"{ktk_slot:02X}".encode('ascii'))
        payload_parts.append(key_type.upper().encode('ascii'))
        payload_parts.append(encryption_type.encode('ascii'))
        payload_parts.append(key_checksum.upper().encode('ascii'))
        payload_parts.append((ktk_checksum.upper() if ktk_checksum else "0000").encode('ascii'))
        payload_parts.append((ksn if ksn else "0" * 20).encode('ascii'))

        key_len_in_digits = len(key_hex)
        payload_parts.append(f"{key_len_in_digits:03X}".encode('ascii'))
        payload_parts.append(key_hex.upper().encode('ascii'))

        if encryption_type == "02":
            ktk_len_in_digits = len(ktk_hex)
            payload_parts.append(f"{ktk_len_in_digits:03X}".encode('ascii'))
            payload_parts.append(ktk_hex.upper().encode('ascii'))
        else:
            payload_parts.append(b"000")

        final_payload = b"".join(payload_parts)
        super().__init__(payload=final_payload)
        log.debug(f"Payload del Comando 02 construido ({len(final_payload)} bytes): {final_payload!r}")
