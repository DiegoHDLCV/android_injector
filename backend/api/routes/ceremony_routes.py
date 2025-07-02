import logging
from flask import Blueprint, jsonify, request
from api.services.ceremony_state_service import ceremony_state_service

log = logging.getLogger(__name__)
ceremony_blueprint = Blueprint('ceremony', __name__, url_prefix='/ceremony')

@ceremony_blueprint.route('/start', methods=['POST'])
def start_ceremony():
    """Inicia o resetea una ceremonia con los parámetros iniciales."""
    data = request.get_json()
    required = ['num_custodians']
    if not data or not all(k in data for k in required):
        return jsonify({"error": f"Faltan parámetros requeridos: {required}"}), 400
    
    try:
        ceremony_state_service.start(data)
        return jsonify(ceremony_state_service.get_state())
    except ValueError as e:
        return jsonify({"error": str(e)}), 400

@ceremony_blueprint.route('/add_component', methods=['POST'])
def add_component():
    """Añade un componente y devuelve el KCV parcial."""
    data = request.get_json()
    component = data.get('component')
    if not component:
        return jsonify({"error": "No se proporcionó ningún componente."}), 400
    
    result = ceremony_state_service.add_component(component)
    if "error" in result:
        return jsonify(result), 400
    return jsonify(result)

@ceremony_blueprint.route('/finalize', methods=['POST'])
def finalize_ceremony():
    """Finaliza la ceremonia y guarda la llave."""
    result = ceremony_state_service.finalize()
    if "error" in result:
        return jsonify(result), 400
    return jsonify(result)

@ceremony_blueprint.route('/state', methods=['GET'])
def get_ceremony_state():
    """Obtiene el estado actual de la ceremonia."""
    return jsonify(ceremony_state_service.get_state())

@ceremony_blueprint.route('/reset', methods=['POST'])
def reset_ceremony():
    """Cancela la ceremonia actual."""
    ceremony_state_service.reset()
    return jsonify({"message": "Ceremonia reseteada."})

@ceremony_blueprint.route('/cancel', methods=['POST'])
def cancel_ceremony():
    """Cancela la ceremonia actual (alias de reset)."""
    ceremony_state_service.reset()
    return jsonify({"message": "Ceremonia cancelada."}) 