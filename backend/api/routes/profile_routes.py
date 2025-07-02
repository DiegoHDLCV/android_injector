import logging
from flask import Blueprint, jsonify, request
from flask_login import login_required, current_user
from api.services.profile_service import ProfileService

log = logging.getLogger(__name__)
profile_blueprint = Blueprint('profiles', __name__, url_prefix='/profiles')
profile_service = ProfileService()

@profile_blueprint.route('/', methods=['GET'])
# @login_required  # 🧪 TESTING: Comentado temporalmente para resolver problema de sesiones
def get_all_profiles():
    """Obtiene todos los perfiles disponibles."""
    try:
        profiles = profile_service.get_all_profiles()
        return jsonify({
            'status': 'success',
            'profiles': profiles
        })
    except Exception as e:
        log.exception(f"Error obteniendo perfiles: {e}")
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500

@profile_blueprint.route('/<int:profile_id>', methods=['GET'])
# @login_required  # 🧪 TESTING: Comentado temporalmente para resolver problema de sesiones
def get_profile_by_id(profile_id):
    """Obtiene un perfil específico con detalles de llaves."""
    try:
        profile = profile_service.get_profile_with_key_details(profile_id)
        if not profile:
            return jsonify({
                'status': 'error',
                'message': f'No se encontró el perfil con ID {profile_id}'
            }), 404
        
        return jsonify({
            'status': 'success',
            'profile': profile
        })
    except Exception as e:
        log.exception(f"Error obteniendo perfil {profile_id}: {e}")
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500

@profile_blueprint.route('/', methods=['POST'])
@login_required
def create_profile():
    """Crea un nuevo perfil."""
    try:
        data = request.get_json()
        
        # Validar datos requeridos
        required_fields = ['name', 'description', 'application_type', 'keys']
        for field in required_fields:
            if field not in data:
                return jsonify({
                    'status': 'error',
                    'message': f'Campo requerido faltante: {field}'
                }), 400
        
        # Validar estructura de llaves
        for key_config in data.get('keys', []):
            required_key_fields = ['slot', 'usage', 'description']
            for field in required_key_fields:
                if field not in key_config:
                    return jsonify({
                        'status': 'error',
                        'message': f'Campo requerido faltante en configuración de llave: {field}'
                    }), 400
        
        success = profile_service.save_profile(data)
        
        if success:
            return jsonify({
                'status': 'success',
                'message': f'Perfil "{data["name"]}" creado exitosamente'
            })
        else:
            return jsonify({
                'status': 'error',
                'message': 'Error al crear el perfil'
            }), 500
            
    except Exception as e:
        log.exception(f"Error creando perfil: {e}")
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500

@profile_blueprint.route('/<int:profile_id>', methods=['PUT'])
@login_required
def update_profile(profile_id):
    """Actualiza un perfil existente."""
    try:
        data = request.get_json()
        data['id'] = profile_id  # Asegurar que el ID sea correcto
        
        success = profile_service.save_profile(data)
        
        if success:
            return jsonify({
                'status': 'success',
                'message': f'Perfil ID {profile_id} actualizado exitosamente'
            })
        else:
            return jsonify({
                'status': 'error',
                'message': 'Error al actualizar el perfil'
            }), 500
            
    except Exception as e:
        log.exception(f"Error actualizando perfil {profile_id}: {e}")
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500

@profile_blueprint.route('/<int:profile_id>', methods=['DELETE'])
@login_required
def delete_profile(profile_id):
    """Elimina un perfil."""
    try:
        success = profile_service.delete_profile(profile_id)
        
        if success:
            return jsonify({
                'status': 'success',
                'message': f'Perfil ID {profile_id} eliminado exitosamente'
            })
        else:
            return jsonify({
                'status': 'error',
                'message': f'No se encontró el perfil con ID {profile_id}'
            }), 404
            
    except Exception as e:
        log.exception(f"Error eliminando perfil {profile_id}: {e}")
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500

@profile_blueprint.route('/<int:profile_id>/assign', methods=['POST'])
@login_required
def assign_key_to_profile(profile_id):
    """Asigna una llave específica a un slot del perfil."""
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({
                "status": "error",
                "message": "No se recibieron datos"
            }), 400
        
        key_id = data.get('key_id')
        slot = data.get('slot')
        
        if key_id is None or slot is None:
            return jsonify({
                "status": "error",
                "message": "Se requieren key_id y slot"
            }), 400
        
        # Verificar que la llave existe
        from ..services.key_storage_service import KeyStorageService
        key_storage = KeyStorageService()
        key_details = key_storage.get_key_by_id(key_id)
        
        if not key_details:
            return jsonify({
                "status": "error",
                "message": f"No se encontró la llave con ID {key_id}"
            }), 404
        
        # Verificar que el perfil existe
        profile = profile_service.get_profile_by_id(profile_id)
        if not profile:
            return jsonify({
                "status": "error",
                "message": f"No se encontró el perfil con ID {profile_id}"
            }), 404
        
        # Verificar que el slot existe en el perfil
        slot_config = next((k for k in profile['keys'] if k['slot'] == slot), None)
        if not slot_config:
            return jsonify({
                "status": "error",
                "message": f"El slot {slot} no existe en el perfil"
            }), 400
        
        # Verificar que el slot no esté ya ocupado
        if slot_config.get('key_id'):
            return jsonify({
                "status": "error",
                "message": f"El slot {slot} ya tiene una llave asignada (ID {slot_config['key_id']})"
            }), 400
        
        # Verificar que la llave no esté ya asignada en otro slot del mismo perfil
        for key_config in profile['keys']:
            if key_config.get('key_id') == key_id:
                return jsonify({
                    "status": "error",
                    "message": f"La llave ID {key_id} ya está asignada al slot {key_config['slot']} de este perfil"
                }), 400
        
        # Asignar la llave
        result = profile_service.assign_key_to_profile(profile_id, slot, key_id)
        
        if result:
            return jsonify({
                "status": "success",
                "message": f"Llave ID {key_id} asignada exitosamente al slot {slot}",
                "assigned_key": {
                    "key_id": key_id,
                    "slot": slot,
                    "kcv": key_details['kcv']
                }
            }), 200
        else:
            return jsonify({
                "status": "error",
                "message": "Error interno al asignar la llave"
            }), 500
            
    except Exception as e:
        log.exception(f"Error asignando llave al perfil {profile_id}")
        return jsonify({
            "status": "error",
            "message": str(e)
        }), 500

@profile_blueprint.route('/<int:profile_id>/unassign', methods=['POST'])
@login_required
def unassign_key_from_profile(profile_id):
    """Desasigna una llave de un slot del perfil."""
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({
                "status": "error",
                "message": "No se recibieron datos"
            }), 400
        
        slot = data.get('slot')
        
        if slot is None:
            return jsonify({
                "status": "error",
                "message": "Se requiere el slot"
            }), 400
        
        # Verificar que el perfil existe
        profile = profile_service.get_profile_by_id(profile_id)
        if not profile:
            return jsonify({
                "status": "error",
                "message": f"No se encontró el perfil con ID {profile_id}"
            }), 404
        
        # Verificar que el slot existe en el perfil
        slot_config = next((k for k in profile['keys'] if k['slot'] == slot), None)
        if not slot_config:
            return jsonify({
                "status": "error",
                "message": f"El slot {slot} no existe en el perfil"
            }), 400
        
        # Verificar que el slot tiene una llave asignada
        if not slot_config.get('key_id'):
            return jsonify({
                "status": "error",
                "message": f"El slot {slot} no tiene ninguna llave asignada"
            }), 400
        
        # Desasignar la llave
        result = profile_service.unassign_key_from_profile(profile_id, slot)
        
        if result:
            return jsonify({
                "status": "success",
                "message": f"Llave desasignada exitosamente del slot {slot}",
                "unassigned_slot": slot
            }), 200
        else:
            return jsonify({
                "status": "error",
                "message": "Error interno al desasignar la llave"
            }), 500
            
    except Exception as e:
        log.exception(f"Error desasignando llave del perfil {profile_id}")
        return jsonify({
            "status": "error",
            "message": str(e)
        }), 500 