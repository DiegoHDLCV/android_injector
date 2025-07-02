from flask import Blueprint, jsonify, request, current_app
import serial.tools.list_ports
import logging

from ..infrastructure.serial_handler import SerialHandler, SerialPortIOError
from ..util.serial_utils import find_serial_ports
from ..domain.commands import CommandFuturexReadSerial
from ..routes.storage_routes import set_serial_handler

log = logging.getLogger(__name__)
serial_blueprint = Blueprint('serial', __name__, url_prefix='/serial')

@serial_blueprint.route('/ports', methods=['GET'])
def get_available_ports():
    """Devuelve una lista de puertos seriales detectados en el sistema."""
    ports = find_serial_ports()
    return jsonify({"ports": ports})

@serial_blueprint.route('/status', methods=['GET'])
def get_serial_status():
    """Devuelve el estado actual de la conexión serial."""
    if current_app.serial_handler:
        return jsonify({
            "is_connected": True,
            "port": current_app.serial_handler.port,
            "baudrate": current_app.serial_handler.baudrate
        })
    return jsonify({"is_connected": False})

@serial_blueprint.route('/connect', methods=['POST'])
def connect_serial():
    """Establece una conexión con un puerto serial."""
    if current_app.serial_handler:
        return jsonify({"success": False, "message": "Ya hay una conexión activa."}), 400

    data = request.get_json()
    port = data.get('port')
    if not port:
        return jsonify({"success": False, "message": "El puerto es requerido."}), 400
    
    try:
        current_app.serial_handler = SerialHandler(port=port)
        # Establecer el serial_handler global para las rutas de storage
        set_serial_handler(current_app.serial_handler)
        log.info(f"Conexión establecida exitosamente en el puerto {port}")
        return jsonify({"success": True, "message": f"Conectado a {port}"})
    except SerialPortIOError as e:
        log.error(f"Fallo al conectar al puerto {port}: {e}")
        current_app.serial_handler = None
        set_serial_handler(None)
        return jsonify({"success": False, "message": str(e)}), 500

@serial_blueprint.route('/disconnect', methods=['POST'])
def disconnect_serial():
    """Cierra la conexión serial activa."""
    if not current_app.serial_handler:
        return jsonify({"success": False, "message": "No hay ninguna conexión activa."}), 400
    
    try:
        current_app.serial_handler.stop()
        log.info(f"Desconexión del puerto {current_app.serial_handler.port} exitosa.")
    except Exception as e:
        log.error(f"Error al intentar desconectar: {e}")
    finally:
        current_app.serial_handler = None
        set_serial_handler(None)

    return jsonify({"success": True, "message": "Desconectado."})

@serial_blueprint.route('/read-serial', methods=['POST'])
def read_serial_number():
    """Envía el comando para leer el número de serie y devuelve la respuesta."""
    log.info("Recibida petición en /read-serial.")
    
    if not current_app.serial_handler:
        log.warning("Intento de leer serial sin conexión activa.")
        return jsonify({"success": False, "message": "No hay conexión serial activa."}), 400

    try:
        # 1. Crear el comando
        command_to_send = CommandFuturexReadSerial()
        log.info(f"Comando '{type(command_to_send).__name__}' creado.")

        # 2. Simular respuesta exitosa por ahora
        response_data = {
            "status": "success",
            "protocol": "FUTUREX", 
            "command": "03",
            "message": "Serial read command executed",
            "serial_number": "MOCK123456"
        }
        log.info(f"Respuesta simulada: {response_data}")

        return jsonify(response_data), 200

    except Exception as e:
        log.exception(f"Excepción no controlada en read_serial_number: {e}")
        return jsonify({"success": False, "message": f"Error del servidor: {e}"}), 500 