import logging
from flask import Blueprint, jsonify, request, current_app
from flask_login import login_required, current_user
from api.services.key_storage_service import KeyStorageService
from api.services.injection_log_service import injection_log_service
from api.services.profile_session_service import profile_session_service
from api.domain.commands import CommandFuturexInjectSymmetricKey
from ..services.tr31_service import TR31Service
from ..util.mappings import get_key_type_name
from ..infrastructure.serial_handler import SerialHandler

log = logging.getLogger(__name__)
storage_blueprint = Blueprint('storage', __name__, url_prefix='/storage')
key_storage_service = KeyStorageService()

# Variable global para el serial handler
serial_handler = None

def set_serial_handler(handler):
    """Establece el serial handler global."""
    global serial_handler
    serial_handler = handler

@storage_blueprint.route('/keys', methods=['GET'])
def get_stored_keys():
    """Devuelve la lista de llaves almacenadas con nombres de tipo de llave legibles."""
    
    print("\n" + "="*80)
    print("🚀 [TERMINAL] ENDPOINT /storage/keys SOLICITADO")
    print(f"🚀 [TERMINAL] Método: {request.method}")
    print(f"🚀 [TERMINAL] URL: {request.url}")
    print(f"🚀 [TERMINAL] User-Agent: {request.headers.get('User-Agent', 'No especificado')}")
    print("="*80)
    
    log.info("🔍 [DEBUG] GET /storage/keys - Iniciando obtención de llaves...")
    
    try:
        # 1. Verificar acceso al servicio
        print("📋 [TERMINAL] Verificando KeyStorageService...")
        log.info(f"🔍 [DEBUG] KeyStorageService disponible: {key_storage_service is not None}")
        
        # 2. Obtener llaves del servicio
        print("📋 [TERMINAL] Llamando a key_storage_service.get_all_keys()...")
        keys = key_storage_service.get_all_keys()
        
        print(f"📊 [TERMINAL] RESULTADO: {len(keys) if keys else 0} llaves obtenidas del servicio")
        log.info(f"🔍 [DEBUG] Llaves obtenidas del servicio: {len(keys) if keys else 0}")
        
        if keys:
            print("📋 [TERMINAL] LLAVES ENCONTRADAS EN EL ALMACÉN:")
            log.info(f"🔍 [DEBUG] Primeras 3 llaves (sin key_hex):")
            for i, key in enumerate(keys[:3]):
                filtered_key = {k: v for k, v in key.items() if k != 'key_hex'}
                print(f"  {i+1}. {filtered_key}")
                log.info(f"🔍 [DEBUG]   Llave {i+1}: {filtered_key}")
        else:
            print("❌ [TERMINAL] NO SE ENCONTRARON LLAVES EN EL ALMACÉN")
            log.warning("🔍 [DEBUG] ⚠️ No se encontraron llaves en el almacén")
        
        # 3. Enriquecer los datos de la llave para el frontend
        enriched_keys = []
        for i, key in enumerate(keys):
            try:
                # Solo agregar key_type_name si la llave tiene key_type (para compatibilidad con llaves legacy)
                if key.get('key_type'):
                    key['key_type_name'] = get_key_type_name(key.get('key_type'))
                
                # Filtrar key_hex para no enviarla al frontend
                enriched_key = {k: v for k, v in key.items() if k != 'key_hex'}
                enriched_keys.append(enriched_key)
                
                log.info(f"🔍 [DEBUG] Llave {i+1} enriquecida exitosamente")
                
            except Exception as e:
                log.error(f"🔍 [DEBUG] ❌ Error enriqueciendo llave {i+1}: {e}")
                continue
        
        log.info(f"🔍 [DEBUG] Total de llaves enriquecidas: {len(enriched_keys)}")
        
        # 4. Crear mappings de tipos de llave para el frontend
        key_type_mappings = {
            '01': 'Master Session Key',
            '04': 'MAC Key',
            '05': 'PIN Encryption Key',
            '06': 'Key Transfer Key',
            '08': 'DUKPT 3DES BDK',
            '03': 'DUKPT AES BDK',
            '0B': 'DUKPT AES Key',
            '10': 'DUKPT AES Key'
        }
        
        # 5. Construir respuesta
        response_data = {
            'keys': enriched_keys,
            'mappings': key_type_mappings
        }
        
        print(f"✅ [TERMINAL] RESPUESTA CONSTRUIDA:")
        print(f"   - Llaves enriquecidas: {len(enriched_keys)}")
        print(f"   - Mappings disponibles: {len(key_type_mappings)}")
        print(f"   - Estructura de respuesta: {list(response_data.keys())}")
        
        if enriched_keys:
            print("📋 [TERMINAL] LLAVES QUE SE ENVÍAN AL FRONTEND:")
            for i, key in enumerate(enriched_keys):
                print(f"  {i+1}. ID: {key.get('id')}, KCV: {key.get('kcv')}, Timestamp: {key.get('timestamp')}")
        else:
            print("📭 [TERMINAL] NO HAY LLAVES PARA ENVIAR AL FRONTEND")
        
        log.info(f"🔍 [DEBUG] Respuesta construida con {len(enriched_keys)} llaves")
        log.info(f"🔍 [DEBUG] Enviando respuesta JSON al frontend...")
        
        print("="*80)
        print("🎉 [TERMINAL] RESPUESTA JSON ENVIADA AL FRONTEND")
        print("="*80 + "\n")
        
        return jsonify(response_data)
        
    except Exception as e:
        print("❌ [TERMINAL] ERROR CRÍTICO EN ENDPOINT /storage/keys")
        print(f"❌ [TERMINAL] Error: {e}")
        print("="*80 + "\n")
        
        log.error(f"🔍 [DEBUG] ❌ ERROR CRÍTICO en get_stored_keys: {e}")
        log.exception("🔍 [DEBUG] Stack trace completo:")
        
        # Respuesta de error
        return jsonify({
            'error': True,
            'message': f'Error interno del servidor: {str(e)}',
            'keys': [],
            'mappings': {}
        }), 500

@storage_blueprint.route('/keys/inject', methods=['POST'])
def inject_stored_key():
    """Inyecta una llave almacenada, validando la conexión primero."""
    log.error("🚨🚨🚨 CÓDIGO MODIFICADO SE ESTÁ EJECUTANDO 🚨🚨🚨")
    log.error("🚨🚨🚨 ENDPOINT /keys/inject INICIADO CON CAMBIOS 🚨🚨🚨")
    log.info("=== INICIANDO ENDPOINT /storage/keys/inject ===")
    
    # Log de la petición completa
    log.info(f"Método: {request.method}")
    log.info(f"Headers: {dict(request.headers)}")
    log.info(f"Content-Type: {request.content_type}")
    
    # Verificar conexión serial
    if not serial_handler or not hasattr(serial_handler, 'serial') or not serial_handler.serial or not serial_handler.serial.is_open:
        log.error("FALLO: No hay dispositivo conectado")
        return jsonify({
            "status": "error", 
            "message": "No hay un dispositivo conectado. Por favor, conéctese primero.",
            "requires_connection": True
        }), 400
    
    log.info(f"✓ Dispositivo conectado en puerto: {serial_handler.serial.port}")
        
    # Obtener y validar datos
    try:
        data = request.get_json()
        log.error(f"🔥🔥🔥 DATOS COMPLETOS RECIBIDOS DEL FRONTEND: {data}")
        
    except Exception as e:
        log.error(f"Error parseando JSON: {e}")
        return jsonify({"status": "error", "message": "JSON inválido"}), 400
    
    kcv = data.get('kcv') if data else None
    injection_type = data.get('injection_type', 'clear') if data else 'clear'
    ktk_slot = data.get('ktk_slot', '00') if data else '00'
    
    log.info(f"Parámetros extraídos:")
    log.info(f"  - KCV: {kcv}")
    log.info(f"  - Tipo de inyección: {injection_type}")
    log.info(f"  - KTK Slot: {ktk_slot}")
    
    if not kcv:
        log.error("FALLO: Falta el parámetro KCV")
        return jsonify({"status": "error", "message": "Falta el KCV de la llave a inyectar."}), 400

    # Buscar la llave en el almacén
    log.info(f"Buscando llave con KCV {kcv} en el almacén local...")
    key_to_inject = key_storage_service.get_key_by_kcv(kcv)
    if not key_to_inject:
        log.error(f"FALLO: No se encontró la llave con KCV {kcv}")
        return jsonify({"status": "error", "message": f"No se encontró la llave con KCV {kcv}."}), 404

    # Obtener slot desde los datos de la petición
    target_slot = data.get('target_slot', '00')  # Slot por defecto si no se especifica
    
    # Determinar el tipo de llave para inyección
    profile_key_type = data.get('profile_key_type') if data else None
    
    if profile_key_type:
        key_type_for_injection = profile_key_type
        log.info(f"Usando tipo definido en el perfil: {profile_key_type}")
    else:
        # Fallback: usar el tipo de la llave o uno por defecto
        key_type_for_injection = key_to_inject.get('key_type')
        
        if key_to_inject.get('is_universal') or not key_type_for_injection:
            key_type_for_injection = '01'  # Master Session Key como tipo por defecto
            log.info("Llave universal detectada, usando tipo '01' (Master Session Key) para inyección")
    
    log.info(f"✓ LLAVE DEL USUARIO encontrada:")
    log.info(f"  - KCV USUARIO: {key_to_inject.get('kcv')} ⬅️ ESTA ES LA LLAVE REAL DEL USUARIO")
    log.info(f"  - Tipo original: {key_to_inject.get('key_type', 'N/A (Universal)')}")
    log.info(f"  - Tipo para inyección: {key_type_for_injection}")
    log.info(f"  - Fuente del tipo: {'Perfil' if profile_key_type else 'Auto-determinado'}")
    log.info(f"  - KSN: {key_to_inject.get('ksn', 'N/A')}")
    log.info(f"  - Hex length: {len(key_to_inject.get('key_hex', '')) if key_to_inject.get('key_hex') else 0} caracteres")
    log.info(f"  - Slot destino: {target_slot}")
    log.info(f"  - Es universal: {key_to_inject.get('is_universal', False)}")

    try:
        log.info(f"Iniciando inyección de llave con KCV {kcv} en slot {target_slot}")
        
        tr31_service = TR31Service(serial_handler)
        log.info("✓ TR31Service inicializado")
        
        # Validar tipo de inyección y ejecutar
        log.info(f"Procesando tipo de inyección: {injection_type}")
        
        if injection_type == 'clear':
            log.info("Ejecutando inyección en claro...")
            result = tr31_service.inject_clear_key_with_kcv(
                key_slot=target_slot,
                key_type=key_type_for_injection,
                key_hex=key_to_inject.get('key_hex'),
                key_kcv=key_to_inject.get('kcv'),
                ksn=key_to_inject.get('ksn')
            )
        else:
            log.error(f"Tipo de inyección inválido: {injection_type}")
            return jsonify({"status": "error", "message": f"Tipo de inyección no válido: {injection_type}. Opciones: clear"}), 400

        log.info(f"Resultado de la inyección para KCV USUARIO {kcv}: {result}")

        if result.get('status') == 'success':
            # Actualizar fecha de inyección
            log.info(f"✅ INYECCIÓN EXITOSA de llave usuario con KCV {kcv}")
            log.info("Actualizando fecha de última inyección...")
            key_storage_service.update_injection_date(kcv)
            
            log.info(f"✓ Llave con KCV {kcv} inyectada exitosamente")
            log.info("=== ENDPOINT COMPLETADO EXITOSAMENTE ===")
            return jsonify(result)
        else:
            log.error(f"Error en el resultado de inyección: {result.get('message', 'Error desconocido')}")
            log.error(f"Resultado completo: {result}")
            
            log.info("=== ENDPOINT COMPLETADO CON ERROR ===")
            return jsonify(result), 500

    except Exception as e:
        log.exception(f"Excepción durante la inyección de llave con KCV {kcv}")
        log.error(f"Tipo de excepción: {type(e).__name__}")
        log.error(f"Mensaje: {str(e)}")
        log.info("=== ENDPOINT COMPLETADO CON EXCEPCIÓN ===")
        return jsonify({"status": "error", "message": str(e)}), 500

@storage_blueprint.route('/keys/clear', methods=['POST'])
def clear_stored_keys():
    """Elimina todas las llaves del almacén local."""
    result = key_storage_service.clear_all_keys()
    if result:
        return jsonify({"status": "success", "message": "Todas las llaves han sido eliminadas del almacén local."}), 200
    else:
        return jsonify({"status": "error", "message": "Error al eliminar las llaves del almacén local."}), 500

@storage_blueprint.route('/keys/delete/<kcv>', methods=['DELETE'])
def delete_individual_key(kcv):
    """Elimina una llave específica del almacén local."""
    try:
        # Eliminar del almacén local
        success = key_storage_service.delete_key_by_kcv(kcv)
        
        if success:
            return jsonify({
                "status": "success",
                "message": f"Llave con KCV {kcv} eliminada exitosamente del almacén local"
            })
        else:
            return jsonify({
                "status": "error",
                "message": f"No se encontró la llave con KCV {kcv}"
            }), 404
            
    except Exception as e:
        log.exception(f"Error eliminando llave {kcv}: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500 