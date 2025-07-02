from flask import Blueprint, jsonify, request, current_app
from flask_login import login_required, current_user
import logging

log = logging.getLogger(__name__)
user_blueprint = Blueprint('user', __name__, url_prefix='/users')

def admin_required(fn):
    """Decorador para restringir el acceso solo a administradores."""
    @login_required
    def wrapper(*args, **kwargs):
        if current_user.role != 'admin':
            return jsonify({"success": False, "message": "Acceso denegado. Se requiere rol de administrador."}), 403
        return fn(*args, **kwargs)
    wrapper.__name__ = fn.__name__
    return wrapper

@user_blueprint.route('/', methods=['GET'])
@admin_required
def get_users():
    """Devuelve una lista de todos los usuarios."""
    user_service = current_app.user_service
    users = user_service.get_all_users()
    # No enviar los hashes de contraseña al frontend
    users_data = [{"id": u.id, "username": u.username, "role": u.role} for u in users]
    return jsonify(users_data)

@user_blueprint.route('/', methods=['POST'])
@admin_required
def create_user():
    """Crea un nuevo usuario."""
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    role = data.get('role', 'operator')

    if not username or not password:
        return jsonify({"success": False, "message": "El nombre de usuario y la contraseña son requeridos."}), 400

    if role not in ['admin', 'operator']:
        return jsonify({"success": False, "message": "El rol debe ser 'admin' u 'operator'."}), 400

    user_service = current_app.user_service
    success, message = user_service.create_user(username, password, role)

    if success:
        log.info(f"Usuario '{username}' creado exitosamente por '{current_user.username}'.")
        return jsonify({"success": True, "message": message})
    else:
        return jsonify({"success": False, "message": message}), 409

@user_blueprint.route('/<user_id>', methods=['DELETE'])
@admin_required
def delete_user(user_id):
    """Elimina un usuario por su ID."""
    # Evitar que un admin se elimine a sí mismo
    if current_user.id == user_id:
        return jsonify({"success": False, "message": "No puedes eliminarte a ti mismo."}), 400

    user_service = current_app.user_service
    success, message = user_service.delete_user(user_id)

    if success:
        log.info(f"Usuario con ID '{user_id}' eliminado por '{current_user.username}'.")
        return jsonify({"success": True, "message": message})
    else:
        return jsonify({"success": False, "message": message}), 404 