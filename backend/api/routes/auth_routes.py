from flask import Blueprint, request, jsonify, current_app
from flask_login import login_user, logout_user, login_required, current_user
import logging
from api.services.user_service import user_service

log = logging.getLogger(__name__)

auth_blueprint = Blueprint('auth', __name__, url_prefix='/auth')

@auth_blueprint.route('/login', methods=['POST'])
def login():
    log.info(f"Login API endpoint accessed - Method: {request.method}")
    
    # Obtener datos del JSON
    data = request.get_json()
    if not data:
        return jsonify({"error": "Datos JSON requeridos"}), 400
    
    username = data.get('username')
    password = data.get('password')
    
    log.info(f"POST login attempt - Username: {username}")
    
    if not username or not password:
        log.warning("Login attempt with missing username or password")
        return jsonify({"error": "Por favor ingresa usuario y contraseña"}), 400
    
    user = user_service.find_by_username(username)
    log.info(f"User found: {user is not None}")
    
    if user and user.check_password(password):
        login_user(user)
        log.info(f"Usuario '{username}' ha iniciado sesión exitosamente.")
        return jsonify({
            "success": True,
            "message": "Inicio de sesión exitoso",
            "user": {
                "username": user.username,
                "role": user.role
            }
        }), 200
    else:
        log.warning(f"Intento de inicio de sesión fallido para el usuario '{username}'.")
        return jsonify({"error": "Credenciales inválidas"}), 401

@auth_blueprint.route('/logout', methods=['POST'])
def logout():
    logout_user()
    log.info("Usuario ha cerrado sesión.")
    return jsonify({"success": True, "message": "Sesión cerrada exitosamente."})

@auth_blueprint.route('/status', methods=['GET'])
def auth_status():
    if current_user.is_authenticated:
        return jsonify({
            "is_authenticated": True, 
            "user": {
                "username": current_user.username, 
                "role": current_user.role
            }
        })
    return jsonify({"is_authenticated": False})

@auth_blueprint.route('/reset_admin_password', methods=['POST'])
def reset_admin_password():
    data = request.get_json()
    new_password = data.get('new_password')

    if not new_password:
        return jsonify({"error": "Se requiere la nueva contraseña."}), 400
    
    # ID del usuario admin es '1' según data/users.json
    success, message = user_service.update_user_password(user_id="1", new_password=new_password)

    if success:
        return jsonify({"success": True, "message": message}), 200
    else:
        return jsonify({"error": message}), 400 