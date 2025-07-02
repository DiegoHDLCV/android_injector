# api/config.py
import os

# Configuración del protocolo de comunicación
COMM_PROTOCOL = os.getenv('COMM_PROTOCOL', 'FUTUREX')
LRC_SEND = os.getenv('LRC_SEND', 'True').lower() == 'true'
LRC_CHECK = os.getenv('LRC_CHECK', 'True').lower() == 'true'

# Configuración de Flask
SECRET_KEY = os.getenv('SECRET_KEY', 'dev-secret-key-change-in-production')
DEBUG = os.getenv('DEBUG', 'True').lower() == 'true'

# Configuración de base de datos (para futuro uso)
DATABASE_URL = os.getenv('DATABASE_URL', 'sqlite:///injector.db')

# Configuración de CORS
CORS_ORIGINS = os.getenv('CORS_ORIGINS', 'http://localhost:3000').split(',')

# Configuración de logging
LOG_LEVEL = os.getenv('LOG_LEVEL', 'DEBUG')

# Configuración de zona horaria
TIMEZONE = os.getenv('TIMEZONE', 'UTC')

# Configuración de puertos seriales
SERIAL_PORT = os.getenv('SERIAL_PORT', '/dev/ttyUSB0')
SERIAL_BAUDRATE = int(os.getenv('SERIAL_BAUDRATE', '9600'))
SERIAL_TIMEOUT = float(os.getenv('SERIAL_TIMEOUT', '2.0'))
SERIAL_PARITY = os.getenv('SERIAL_PARITY', 'N')
SERIAL_STOPBITS = int(os.getenv('SERIAL_STOPBITS', '1'))
SERIAL_BYTESIZE = int(os.getenv('SERIAL_BYTESIZE', '8'))

# Configuración de timeouts
COMMAND_TIMEOUT = float(os.getenv('COMMAND_TIMEOUT', '5.0'))
RESPONSE_TIMEOUT = float(os.getenv('RESPONSE_TIMEOUT', '3.0'))
TIMEOUT_ACK = float(os.getenv('TIMEOUT_ACK', '1.0'))
TIMEOUT_RESPONSE = float(os.getenv('TIMEOUT_RESPONSE', '3.0'))
TIMEOUT_COMMAND = float(os.getenv('TIMEOUT_COMMAND', '5.0'))
