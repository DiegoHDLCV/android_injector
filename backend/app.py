#!/usr/bin/env python3
# app.py - Aplicación principal Flask

import logging
from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_login import LoginManager, login_required, current_user
from api import config
from api.domain.user import User
from api.services.user_service import user_service
import os

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

log = logging.getLogger(__name__)

def create_app():
    """Factory function para crear la aplicación Flask."""
    app = Flask(__name__)
    
    # Configuración de la aplicación
    app.config['SECRET_KEY'] = config.SECRET_KEY
    app.config['DEBUG'] = config.DEBUG
    app.config['TIMEZONE'] = config.TIMEZONE
    
    # Configurar CORS con configuración completa
    CORS(app, 
         origins=['http://localhost:3000'], 
         supports_credentials=True,
         allow_headers=['Content-Type', 'Authorization'],
         methods=['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'])
    
    # Configurar Flask-Login
    login_manager = LoginManager()
    login_manager.init_app(app)
    login_manager.login_view = 'auth.login'
    login_manager.login_message = 'Por favor inicia sesión para acceder a esta página.'
    
    @login_manager.user_loader
    def load_user(user_id):
        """Carga un usuario por su ID para Flask-Login."""
        users = user_service.get_all_users()
        for user in users:
            if user.id == user_id:
                return user
        return None
    
    # Asegurar que existe el directorio de datos
    os.makedirs('data', exist_ok=True)
    
    # Crear usuario administrador por defecto si no existe
    try:
        admin_user = user_service.find_by_username('admin')
        if not admin_user:
            success, message = user_service.create_user('admin', 'admin123', 'admin')
            if success:
                log.info("Usuario administrador por defecto creado (admin/admin123)")
            else:
                log.warning(f"No se pudo crear usuario administrador: {message}")
        else:
            log.info("Usuario administrador ya existe")
    except Exception as e:
        log.error(f"Error creando usuario administrador: {e}")
    
    # Registrar todos los blueprints
    from api.routes.auth_routes import auth_blueprint
    from api.routes.ceremony_routes import ceremony_blueprint
    from api.routes.key_routes import key_blueprint
    from api.routes.main_routes import main_blueprint
    from api.routes.profile_routes import profile_blueprint
    from api.routes.serial_routes import serial_blueprint
    from api.routes.storage_routes import storage_blueprint
    from api.routes.user_routes import user_blueprint
    app.register_blueprint(main_blueprint)
    app.register_blueprint(auth_blueprint)
    app.register_blueprint(ceremony_blueprint)
    app.register_blueprint(key_blueprint)
    app.register_blueprint(profile_blueprint)
    app.register_blueprint(serial_blueprint)
    app.register_blueprint(storage_blueprint)
    app.register_blueprint(user_blueprint)

    # Inicializar variables globales de la aplicación
    app.serial_handler = None
    app.user_service = user_service
    
    # Rutas básicas
    @app.route('/')
    def index():
        """Endpoint principal del backend."""
        return jsonify({
            "status": "success",
            "message": "API de Inyector de Llaves - Backend funcionando",
            "protocol": config.COMM_PROTOCOL,
            "version": "1.0.0",
            "endpoints": {
                "auth": "/auth/*",
                "ceremony": "/ceremony/*",
                "keys": "/keys/*",
                "profiles": "/profiles/*",
                "serial": "/serial/*",
                "storage": "/storage/*",
                "users": "/users/*"
            }
        })
    
    @app.route('/health')
    def health():
        """Health check endpoint."""
        return jsonify({
            "status": "healthy",
            "message": "Backend funcionando correctamente"
        })
    
    @app.route('/api/status')
    def api_status():
        """Endpoint de estado de la API."""
        return jsonify({
            "status": "success",
            "api_version": "1.0.0",
            "protocol": config.COMM_PROTOCOL,
            "features": {
                "serial_communication": True,
                "key_injection": True,
                "futurex_protocol": True,
                "user_management": True,
                "profile_management": True,
                "ceremony_support": True,
                "injection_logging": True
            },
            "endpoints_available": len([
                rule.rule for rule in app.url_map.iter_rules() 
                if rule.endpoint != 'static'
            ])
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
    
    # Endpoint para probar la funcionalidad del SerialHandler 
    @app.route('/api/serial/info', methods=['GET'])
    def serial_info():
        try:
            from api.infrastructure.serial_handler import SerialHandler
            from api.util.exceptions import SerialPortIOError
            
            # Lista de puertos comunes para testing
            common_ports = ['/dev/ttyUSB0', '/dev/ttyUSB1', 'COM1', 'COM2', 'COM3']
            
            return jsonify({
                "message": "SerialHandler está disponible",
                "available_classes": [
                    "SerialHandler",
                    "SerialCommsError",
                    "AckTimeoutError", 
                    "ResponseTimeoutError"
                ],
                "common_serial_ports": common_ports,
                "note": "Para usar el SerialHandler, debe especificar un puerto serie válido"
            })
        except Exception as e:
            log.error(f"Error al importar SerialHandler: {e}")
            return jsonify({"error": str(e)}), 500
    
    # Endpoint para crear un comando de prueba sin necesidad de puerto serie
    @app.route('/api/futurex/encode', methods=['POST'])
    def encode_futurex_command():
        try:
            from api.domain.commands import CommandFuturexReadSerial, CommandFuturexInjectSymmetricKey
            
            data = request.get_json() or {}
            command_type = data.get('command_type', 'read_serial')
            
            if command_type == 'read_serial':
                cmd = CommandFuturexReadSerial()
                result = {
                    "command_type": "ReadSerial",
                    "command_code": "03",
                    "encoded_hex": cmd.encode().hex().upper(),
                    "payload_ascii": cmd.data.decode('ascii', 'ignore')
                }
            elif command_type == 'inject_key':
                # Parámetros de ejemplo para inyección de clave
                cmd = CommandFuturexInjectSymmetricKey(
                    key_slot=1,
                    ktk_slot=0,
                    key_type="01",
                    encryption_type="00",
                    key_checksum="ABCD",
                    ksn="00000000000000000000",
                    key_hex="0123456789ABCDEF0123456789ABCDEF"
                )
                result = {
                    "command_type": "InjectSymmetricKey",
                    "command_code": "02", 
                    "encoded_hex": cmd.encode().hex().upper(),
                    "payload_ascii": cmd.data.decode('ascii', 'ignore')
                }
            else:
                return jsonify({"error": "Tipo de comando no soportado"}), 400
                
            return jsonify({
                "message": "Comando codificado exitosamente",
                "result": result
            })
            
        except Exception as e:
            log.error(f"Error al codificar comando: {e}")
            return jsonify({"error": str(e)}), 500
    
    @app.errorhandler(404)
    def not_found(error):
        """Manejo de error 404."""
        return jsonify({
            "status": "error",
            "message": "Endpoint no encontrado",
            "code": 404
        }), 404
    
    @app.errorhandler(500)
    def internal_error(error):
        """Manejo de error 500."""
        return jsonify({
            "status": "error",
            "message": "Error interno del servidor",
            "code": 500
        }), 500
    
    log.info("Aplicación Flask inicializada correctamente")
    return app

# Crear la aplicación
app = create_app()

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    host = os.environ.get('HOST', '127.0.0.1')
    
    print(f"\n🚀 Iniciando Backend del Inyector de Llaves...")
    print(f"📡 Protocolo: {config.COMM_PROTOCOL}")
    print(f"🌐 Servidor: http://{host}:{port}")
    print(f"🔧 Debug: {config.DEBUG}")
    print(f"📁 Directorio de datos: ./data/")
    print(f"🔐 Usuario admin por defecto: admin/admin123")
    print("\n📋 Rutas registradas:")
    for rule in app.url_map.iter_rules():
        if rule.endpoint != 'static':
            print(f"  {rule.methods} {rule.rule}")
    print("\n✅ Backend listo para recibir peticiones\n")
    
    app.run(
        host=host,
        port=port,
        debug=config.DEBUG
    )
