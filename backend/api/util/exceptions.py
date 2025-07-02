"""
Excepciones personalizadas para el manejo de comunicación serial y protocolos.
"""

class SerialCommsError(Exception):
    """Excepción base para errores de comunicación serial."""
    pass

class AckTimeoutError(SerialCommsError):
    """Se lanza cuando no se recibe ACK en el tiempo esperado."""
    pass

class ResponseTimeoutError(SerialCommsError):
    """Se lanza cuando no se recibe una respuesta en el tiempo esperado."""
    pass

class NakReceivedError(SerialCommsError):
    """Se lanza cuando se recibe un NAK del dispositivo."""
    pass

class InvalidMessageError(SerialCommsError):
    """Se lanza cuando se recibe un mensaje con formato inválido."""
    pass

class SerialPortIOError(SerialCommsError):
    """Se lanza cuando hay errores de entrada/salida en el puerto serial."""
    pass

class ProtocolError(Exception):
    """Excepción base para errores de protocolo."""
    pass

class FuturexProtocolError(ProtocolError):
    """Excepción específica para errores del protocolo Futurex."""
    pass 