import serial
import time
import logging
from threading import Lock
from typing import Union, Type

from ..domain.base_message import Message, FuturexMessage
from ..domain.commands import CommandResponseGeneric
from ..util.exceptions import (
    SerialCommsError, AckTimeoutError, ResponseTimeoutError,
    NakReceivedError, InvalidMessageError, SerialPortIOError
)
from .. import config

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s - %(levelname)s - %(name)s - %(threadName)s - %(message)s')
log = logging.getLogger(__name__)

class SerialHandler:
    """
    Gestiona la comunicación de bajo nivel a través del puerto serie.
    """
    DEFAULT_ACK_TIMEOUT = 5
    DEFAULT_RESPONSE_TIMEOUT = 60
    ACK = b'\x06'
    NAK = b'\x15'
    STX = b'\x02'
    ETX = b'\x03'

    def __init__(self, port: str, baudrate: int = 9600,
                 ack_timeout: int = DEFAULT_ACK_TIMEOUT,
                 response_timeout: int = DEFAULT_RESPONSE_TIMEOUT):
        self.port = port
        self.baudrate = baudrate
        self.ack_timeout = ack_timeout
        self.response_timeout = response_timeout
        self.serial = None
        self.lock = Lock()

        try:
            self.serial = serial.Serial(
                port=port,
                baudrate=baudrate,
                timeout=0.05,
                bytesize=serial.EIGHTBITS,
                parity=serial.PARITY_NONE,
                stopbits=serial.STOPBITS_ONE
            )
            log.info(f"Puerto {self.port} abierto a {self.baudrate} baudios.")
        except serial.SerialException as e:
            log.error(f"No se pudo abrir el puerto {self.port}: {e}")
            raise SerialPortIOError(e)

    def send_message(self, message: Message):
        encoded_message = message.encode()
        self._write_serial(encoded_message)
        cmd_str = getattr(message, 'command', getattr(message, 'COMMAND_CODE', b'N/A')).decode(errors='replace')
        log.info(f"Mensaje enviado (Tipo: {type(message).__name__}, Comando: {cmd_str})")

    def _write_serial(self, data: bytes):
        if not self.serial or not self.serial.is_open:
            raise SerialPortIOError("Intento de escritura en puerto cerrado.")
        try:
            self.serial.write(data)
            self.serial.flush()
            log.debug(f"SERIAL_SEND -> Datos crudos (HEX): {data.hex(' ').upper()}")
        except serial.SerialException as e:
            log.error(f"Error al escribir en {self.port}: {e}")
            raise SerialPortIOError(e)

    def _read_serial(self, num_bytes: int) -> bytes:
        if not self.serial or not self.serial.is_open:
            raise SerialPortIOError("Intento de lectura en puerto cerrado.")
        try:
            data = self.serial.read(num_bytes)
            if data:
                log.debug(f"SERIAL_RECV <- Datos crudos (HEX): {data.hex(' ').upper()}")
            return data
        except serial.SerialException as e:
            log.error(f"Error al leer de {self.port}: {e}")
            raise SerialPortIOError(e)

    def _wait_for_response(self, timeout: int, decoder_class: Type[Message] = Message) -> Message:
        start_time = time.monotonic()
        buffer = b""
        log.debug(f"Esperando respuesta (decoder: {decoder_class.__name__}, timeout: {timeout}s)...")

        while time.monotonic() - start_time < timeout:
            try:
                if self.serial and self.serial.is_open and self.serial.in_waiting > 0:
                    buffer += self._read_serial(self.serial.in_waiting)
                
                stx_index = buffer.find(self.STX)
                if stx_index == -1: continue
                if stx_index > 0: buffer = buffer[stx_index:]

                etx_index = buffer.find(self.ETX, 1)
                if etx_index == -1: continue
                
                required_len = etx_index + 2
                
                if len(buffer) >= required_len:
                    message_bytes = buffer[:required_len]
                    try:
                        decoded_message = decoder_class.decode(message_bytes)
                        log.info(f"Mensaje de respuesta válido decodificado con {decoder_class.__name__}.")
                        return decoded_message
                    except ValueError as e:
                        log.error(f"Error de formato/LRC: {e}. Descartando mensaje inválido...")
                        buffer = buffer[etx_index + 1:]
                
            except Exception as e:
                log.exception(f"Excepción en _wait_for_response: {e}")
                buffer = buffer[1:] if buffer else b""
            
            time.sleep(0.02)

        raise ResponseTimeoutError("No se recibió una respuesta válida en el tiempo especificado.")

    def send_and_wait_futurex(self, message: FuturexMessage) -> FuturexMessage:
        if not self.serial or not self.serial.is_open:
            raise SerialCommsError("El puerto serial no está abierto.")

        with self.lock:
            log.info(f"--- Iniciando transacción FUTUREX ---")
            try:
                self.serial.reset_input_buffer()
                self.send_message(message)
                response_msg = self._wait_for_response(self.response_timeout, decoder_class=FuturexMessage)
                log.info(f"--- Transacción FUTUREX completada ---")
                return response_msg
            except Exception as e:
                log.error(f"Error en transacción FUTUREX: {type(e).__name__} - {e}")
                raise

    def stop(self):
        if self.serial and self.serial.is_open:
            self.serial.close()
            log.info(f"Puerto {self.port} cerrado.") 