# api/util/mappings.py

KEY_TYPE_MAPPINGS = {
    '01': 'Master Session Key',
    '04': 'MAC Key',
    '05': 'PIN Encryption Key',
    '06': 'Key Transfer Key',
    '08': 'DUKPT 3DES BDK',
    '03': 'DUKPT AES BDK',
    '0B': 'DUKPT AES Key',
    '10': 'DUKPT AES Key'
}

def get_key_type_name(key_type_code):
    """Obtiene el nombre legible de un tipo de llave."""
    return KEY_TYPE_MAPPINGS.get(key_type_code, f'Tipo Desconocido ({key_type_code})') 