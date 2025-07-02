#!/usr/bin/env python3
# app.py - Aplicación principal Flask

import logging
from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_login import LoginManager, login_required, current_user
from api import config
from api.domain.user import User

# Configurar logging
logging.basicConfig(
    level=getattr(logging, config.LOG_LEVEL),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

log = logging.getLogger(__name__)

def create_app():
    """Factory function para crear la aplicación Flask."""
    app = Flask(__name__)
    
    # Configuración de la aplicación
    app.config['SECRET_KEY'] = config.SECRET_KEY
    app.config['DEBUG'] = config.DEBUG
    
    # Configurar CORS
    CORS(app, origins=config.CORS_ORIGINS)
    
    # Configurar Flask-Login
    login_manager = LoginManager()
    login_manager.init_app(app)
    login_manager.login_view = 'auth.login'
    
    @login_manager.user_loader
    def load_user(user_id):
        # TODO: Implementar carga de usuario desde base de datos
        # Por ahora, usuario demo para pruebas
        if user_id == "1":
            return User(1, "admin", "$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/lewM8MlBxHT8VBOwa", "admin")
        return None
    
    # Rutas básicas
    @app.route('/')
    def index():
        return jsonify({
            "message": "Injector API - Backend funcionando correctamente",
            "status": "ok",
            "version": "1.0.0"
        })
    
    @app.route('/health')
    def health():
        return jsonify({
            "status": "healthy",
            "message": "El servicio está funcionando correctamente"
        })
    
    @app.route('/api/status')
    def api_status():
        return jsonify({
            "api": "Injector API",
            "status": "running",
            "features": [
                "Protocolo Futurex",
                "Inyección de claves simétricas",
                "Gestión de números de serie",
                "Eliminación de claves"
            ]
        })
    
    # Ruta protegida de ejemplo
    @app.route('/api/protected')
    @login_required
    def protected():
        return jsonify({
            "message": f"Hola {current_user.username}!",
            "user_role": current_user.role,
            "is_admin": current_user.is_admin
        })
    
    # Endpoint para probar los comandos Futurex
    @app.route('/api/futurex/test', methods=['POST'])
    def test_futurex():
        try:
            from api.domain.commands import CommandFuturexReadSerial
            
            # Crear comando de ejemplo
            cmd = CommandFuturexReadSerial()
            encoded_message = cmd.encode()
            
            return jsonify({
                "message": "Comando Futurex creado exitosamente",
                "command": "ReadSerial (03)",
                "payload_hex": encoded_message.hex().upper(),
                "payload_ascii": cmd.data.decode('ascii', 'ignore')
            })
        except Exception as e:
            log.error(f"Error al crear comando Futurex: {e}")
            return jsonify({"error": str(e)}), 500
    
    log.info("Aplicación Flask inicializada correctamente")
    return app

# Crear la aplicación
app = create_app()

if __name__ == '__main__':
    log.info("Iniciando servidor Flask...")
    app.run(
        host='0.0.0.0',
        port=5000,
        debug=config.DEBUG
    ) 