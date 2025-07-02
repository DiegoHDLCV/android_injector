import logging
from flask import Blueprint, request, jsonify, current_app

# Dependencias de la aplicación
from .. import config
from ..domain.commands import (
    CommandFuturexReadSerial,
    CommandFuturexInjectSymmetricKey
)
from ..util.exceptions import (
    SerialCommsError, ResponseTimeoutError, InvalidMessageError, SerialPortIOError
)
from ..util.crypto_utils import calculate_key_checksum

# --- Configuración del Blueprint ---
key_blueprint = Blueprint('key', __name__, url_prefix='/keys')
log = logging.getLogger(__name__)

# --- ENDPOINTS ---

@key_blueprint.route('/read_serial', methods=['POST'])
def read_serial_number():
    """Endpoint para leer el número de serie usando el protocolo Futurex."""
    serial_handler = current_app.serial_handler
    if not serial_handler:
        return jsonify({"status": "error", "message": "Serial handler no inicializado."}), 503
        
    if config.COMM_PROTOCOL != 'FUTUREX':
        return jsonify({"status": "error", "message": "Endpoint solo para protocolo 'FUTUREX'."}), 400

    try:
        command = CommandFuturexReadSerial()
        # Simular respuesta exitosa por ahora
        response_data = {
            "status": "success",
            "protocol": "FUTUREX",
            "command": "03",
            "message": "Serial number read successfully",
            "serial_number": "TEST123456"
        }
        
        return jsonify(response_data), 200

    except Exception as e:
        log.exception("Error inesperado en /keys/read_serial")
        return jsonify({"status": "error", "type": "UnexpectedServerError", "message": f"Error interno: {e}"}), 500

@key_blueprint.route('/write_serial', methods=['POST'])
def write_serial_number():
    """Endpoint para escribir un número de serie usando el protocolo Futurex."""
    serial_handler = current_app.serial_handler
    if not serial_handler: return jsonify({"status": "error", "message": "Serial handler no inicializado."}), 503
    if config.COMM_PROTOCOL != 'FUTUREX': return jsonify({"status": "error", "message": "Endpoint solo para 'FUTUREX'."}), 400
    data = request.get_json()
    if not data or 'serial_number' not in data: return jsonify({"status": "error", "message": "Falta 'serial_number'."}), 400

    try:
        # Simular escritura exitosa
        response_data = {
            "status": "success",
            "protocol": "FUTUREX",
            "command": "04",
            "message": f"Serial number '{data['serial_number']}' written successfully"
        }
        return jsonify(response_data), 200
    except ValueError as e:
        return jsonify({"status": "error", "type": "ValidationError", "message": str(e)}), 400
    except Exception as e:
        log.exception("Error inesperado en /keys/write_serial")
        return jsonify({"status": "error", "type": "UnexpectedServerError", "message": str(e)}), 500

@key_blueprint.route('/inject_symmetric', methods=['POST'])
def inject_symmetric_key():
    """Endpoint para inyectar una clave simétrica usando el comando '02' de Futurex."""
    serial_handler = current_app.serial_handler
    if not serial_handler: return jsonify({"status": "error", "message": "Serial handler no inicializado."}), 503
    if config.COMM_PROTOCOL != 'FUTUREX': return jsonify({"status": "error", "message": "Endpoint solo para 'FUTUREX'."}), 400
    data = request.get_json()
    if not data: return jsonify({"status": "error", "message": "Request body debe ser JSON."}), 400

    required = ["key_slot", "ktk_slot", "key_type", "encryption_type", "key_hex"]
    if not all(k in data for k in required):
        return jsonify({"status": "error", "message": f"Faltan campos requeridos: {required}"}), 400

    try:
        # Los checksums se calculan en el backend para mayor seguridad y simplicidad.
        key_checksum = calculate_key_checksum(data["key_hex"])
        ktk_checksum = calculate_key_checksum(data["ktk_hex"]) if data.get("ktk_hex") else "0000"

        command = CommandFuturexInjectSymmetricKey(
            key_slot=int(data["key_slot"]),
            ktk_slot=int(data["ktk_slot"]),
            key_type=data["key_type"],
            encryption_type=data["encryption_type"],
            key_checksum=key_checksum,
            ktk_checksum=ktk_checksum,
            ksn=data.get("ksn", ""),
            key_hex=data["key_hex"],
            ktk_hex=data.get("ktk_hex", "")
        )
        
        # Simular inyección exitosa
        response_data = {
            "status": "success",
            "protocol": "FUTUREX",
            "command": "02",
            "message": "Symmetric key injected successfully",
            "key_slot": data["key_slot"],
            "key_checksum": key_checksum
        }
        
        return jsonify(response_data), 200

    except (ValueError, TypeError) as e:
        return jsonify({"status": "error", "type": "ValidationError", "message": str(e)}), 400
    except Exception as e:
        log.exception("Error inesperado en /keys/inject_symmetric")
        return jsonify({"status": "error", "type": "UnexpectedServerError", "message": str(e)}), 500

@key_blueprint.route('/delete_all', methods=['POST'])
def delete_all_keys():
    """Endpoint para borrar todas las claves del dispositivo (Comando '05')."""
    serial_handler = current_app.serial_handler
    if not serial_handler:
        return jsonify({"status": "error", "message": "Serial handler no inicializado."}), 503

    try:
        # Simular eliminación exitosa
        response_data = {
            "status": "success",
            "protocol": "FUTUREX",
            "command": "05",
            "message": "All keys deleted successfully"
        }
        
        return jsonify(response_data), 200
    except Exception as e:
        log.exception("Error inesperado en /keys/delete_all")
        return jsonify({"status": "error", "type": "UnexpectedServerError", "message": f"Error interno: {e}"}), 500 