# api/domain/base_message.py
import logging
from .. import config

log = logging.getLogger(__name__)

class Message:
    """
    Clase base para tu protocolo original que usa un separador.
    (No se requieren cambios aquí)
    """
    STX = b'\x02'
    ETX = b'\x03'
    SEPARATOR = b'|'

    def __init__(self, command: bytes, data: bytes):
        if not isinstance(command, bytes): raise TypeError("El comando debe ser bytes")
        if not isinstance(data, bytes): raise TypeError("Los datos deben ser bytes")
        self.command = command
        self.data = data

    def encode(self) -> bytes:
        body = self.command + self.SEPARATOR + self.data
        message_core = self.STX + body + self.ETX
        
        final_message = message_core
        if config.LRC_SEND:
            lrc = 0
            for byte_val in body + self.ETX: 
                lrc ^= byte_val
            final_message += bytes([lrc])
        
        log.debug(f"Mensaje (Message) codificado (HEX): {final_message.hex().upper()}")
        return final_message

    @staticmethod
    def decode(raw_bytes: bytes) -> "Message":
        log.debug(f"Intentando decodificar (HEX): {raw_bytes.hex().upper()}")

        if not raw_bytes.startswith(Message.STX) or not raw_bytes.find(Message.ETX):
            raise ValueError(f"Mensaje sin STX o ETX. Recibido: {raw_bytes!r}")
        
        etx_index = raw_bytes.find(Message.ETX)
        
        if config.LRC_CHECK:
            if len(raw_bytes) < etx_index + 2:
                raise ValueError(f"Mensaje corto, falta LRC después de ETX.")
            lrc_received = raw_bytes[etx_index + 1]
            bytes_for_lrc = raw_bytes[1:etx_index + 1]
            lrc_calculated = 0
            for byte_val in bytes_for_lrc: lrc_calculated ^= byte_val
            if lrc_calculated != lrc_received:
                raise ValueError(f"LRC inválido. Calculado: {lrc_calculated:#04x}, Recibido: {lrc_received:#04x}.")

        full_body_content = raw_bytes[1:etx_index]
        parts = full_body_content.split(Message.SEPARATOR, 1)
        
        command = parts[0]
        data = parts[1] if len(parts) > 1 else b''
        
        return Message(command, data)

class FuturexMessage(Message):
    """
    Clase base para mensajes del protocolo Futurex.
    El formato es <STX><PAYLOAD><ETX>[LRC], sin separador de comando.
    """
    def __init__(self, payload: bytes):
        # El payload completo es todo el mensaje. Command y data se usan internamente.
        super().__init__(command=b'', data=payload)

    def encode(self) -> bytes:
        """Codifica el mensaje en el formato Futurex."""
        body = self.data  # El payload completo está en 'data'
        message_core = self.STX + body + self.ETX
        
        final_message = message_core
        if config.LRC_SEND:
            lrc = 0
            # El LRC de Futurex se calcula sobre el payload + ETX
            for byte_val in body + self.ETX:
                lrc ^= byte_val
            final_message += bytes([lrc])
        
        log.debug(f"FuturexMessage Payload: ASCII='{body.decode('ascii', 'ignore')}', HEX='{body.hex(' ').upper()}'")
        log.debug(f"Mensaje (FuturexMessage) codificado (HEX): {final_message.hex(' ').upper()}")
        return final_message

    @staticmethod
    def decode(raw_bytes: bytes) -> "FuturexMessage":
        """Decodifica un mensaje raw en formato Futurex."""
        log.debug(f"Intentando decodificar (Futurex HEX): {raw_bytes.hex().upper()}")

        if not raw_bytes.startswith(Message.STX) or not raw_bytes.find(Message.ETX):
            raise ValueError(f"Mensaje Futurex sin STX o ETX. Recibido: {raw_bytes!r}")
        
        etx_index = raw_bytes.find(Message.ETX)
        
        if config.LRC_CHECK:
            if len(raw_bytes) < etx_index + 2:
                raise ValueError("Mensaje Futurex corto, falta LRC.")
            lrc_received = raw_bytes[etx_index + 1]
            bytes_for_lrc = raw_bytes[1:etx_index + 1] # Payload + ETX
            lrc_calculated = 0
            for byte_val in bytes_for_lrc: lrc_calculated ^= byte_val
            if lrc_calculated != lrc_received:
                raise ValueError(f"LRC de Futurex inválido. Calculado: {lrc_calculated:#04x}, Recibido: {lrc_received:#04x}.")

        payload = raw_bytes[1:etx_index]
        return FuturexMessage(payload=payload)
