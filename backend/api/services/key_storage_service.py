# api/services/key_storage_service.py
import json
import logging
from threading import RLock
from datetime import datetime
import os
import pytz
from flask import current_app

log = logging.getLogger(__name__)

KEY_STORAGE_PATH = os.path.join('data', 'keys.json')

class KeyStorageService:
    _instance = None
    
    def __new__(cls):
        if not cls._instance:
            cls._instance = super(KeyStorageService, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        """Inicializador."""
        if not hasattr(self, 'initialized'):
            self._lock = RLock()
            self.initialized = True
            log.info(f"KeyStorageService inicializado. Almacén: {KEY_STORAGE_PATH}")
            if not os.path.exists(KEY_STORAGE_PATH):
                self._write_keys([])

    def _read_keys(self) -> list:
        with self._lock:
            print(f"🔍 [TERMINAL] KEY_SERVICE: _read_keys() - Archivo: {KEY_STORAGE_PATH}")
            log.info(f"🔍 [KEY_SERVICE] _read_keys() - Intentando leer archivo: {KEY_STORAGE_PATH}")
            
            try:
                # Verificar que el archivo existe
                if not os.path.exists(KEY_STORAGE_PATH):
                    print(f"🔍 [TERMINAL] KEY_SERVICE: ❌ ARCHIVO NO EXISTE: {KEY_STORAGE_PATH}")
                    log.warning(f"🔍 [KEY_SERVICE] ⚠️ Archivo no existe: {KEY_STORAGE_PATH}")
                    return []
                
                # Verificar tamaño del archivo
                file_size = os.path.getsize(KEY_STORAGE_PATH)
                print(f"🔍 [TERMINAL] KEY_SERVICE: ✅ Archivo existe, tamaño: {file_size} bytes")
                log.info(f"🔍 [KEY_SERVICE] Archivo existe, tamaño: {file_size} bytes")
                
                # Leer archivo
                with open(KEY_STORAGE_PATH, 'r') as f:
                    content = f.read()
                    print(f"🔍 [TERMINAL] KEY_SERVICE: Contenido leído ({len(content)} caracteres)")
                    log.info(f"🔍 [KEY_SERVICE] Contenido leído ({len(content)} caracteres)")
                    
                    if content.strip():
                        print(f"🔍 [TERMINAL] KEY_SERVICE: Primeros 200 caracteres: {content[:200]}")
                        log.info(f"🔍 [KEY_SERVICE] Primeros 200 caracteres: {content[:200]}")
                        
                        # Parsear JSON
                        keys = json.loads(content)
                        print(f"🔍 [TERMINAL] KEY_SERVICE: ✅ JSON parseado exitosamente: {len(keys)} llaves")
                        log.info(f"🔍 [KEY_SERVICE] JSON parseado exitosamente: {len(keys)} llaves")
                        return keys
                    else:
                        print(f"🔍 [TERMINAL] KEY_SERVICE: ⚠️ ARCHIVO VACÍO")
                        log.warning(f"🔍 [KEY_SERVICE] ⚠️ Archivo vacío")
                        return []
                        
            except FileNotFoundError as e:
                print(f"🔍 [TERMINAL] KEY_SERVICE: ❌ ARCHIVO NO ENCONTRADO: {e}")
                log.error(f"🔍 [KEY_SERVICE] ❌ Archivo no encontrado: {e}")
                return []
            except json.JSONDecodeError as e:
                print(f"🔍 [TERMINAL] KEY_SERVICE: ❌ ERROR PARSEANDO JSON: {e}")
                log.error(f"🔍 [KEY_SERVICE] ❌ Error parseando JSON: {e}")
                log.error(f"🔍 [KEY_SERVICE] Contenido problemático: {content if 'content' in locals() else 'No disponible'}")
                return []
            except Exception as e:
                print(f"🔍 [TERMINAL] KEY_SERVICE: ❌ ERROR INESPERADO: {e}")
                log.error(f"🔍 [KEY_SERVICE] ❌ Error inesperado leyendo archivo: {e}")
                return []

    def _write_keys(self, keys: list):
        with self._lock:
            log.info(f"🔍 [KEY_SERVICE] _write_keys() - Escribiendo {len(keys)} llaves a: {KEY_STORAGE_PATH}")
            
            try:
                # Preparar JSON
                json_content = json.dumps(keys, indent=4)
                log.info(f"🔍 [KEY_SERVICE] JSON generado ({len(json_content)} caracteres)")
                
                # Escribir archivo
                with open(KEY_STORAGE_PATH, 'w') as f:
                    f.write(json_content)
                    f.flush()  # Forzar escritura al disco
                
                log.info(f"🔍 [KEY_SERVICE] ✅ Archivo escrito exitosamente")
                
                # Verificar que se escribió correctamente
                if os.path.exists(KEY_STORAGE_PATH):
                    file_size = os.path.getsize(KEY_STORAGE_PATH)
                    log.info(f"🔍 [KEY_SERVICE] Verificación: archivo existe, tamaño: {file_size} bytes")
                else:
                    log.error(f"🔍 [KEY_SERVICE] ❌ ERROR: archivo no existe después de escribir")
                    
            except Exception as e:
                log.error(f"🔍 [KEY_SERVICE] ❌ Error escribiendo archivo: {e}")
                raise

    def _generate_unique_id(self, existing_keys: list) -> int:
        """Genera un ID único numérico para una nueva llave."""
        existing_ids = {key.get('id', 0) for key in existing_keys if 'id' in key}
        
        # Empezar desde 1 y encontrar el primer ID disponible
        new_id = 1
        while new_id in existing_ids:
            new_id += 1
        
        return new_id

    def get_all_keys(self) -> list:
        print("\n🔍 [TERMINAL] KEY_SERVICE: get_all_keys() INICIADO")
        log.info("🔍 [KEY_SERVICE] get_all_keys() iniciado")
        
        try:
            print("🔍 [TERMINAL] KEY_SERVICE: Llamando _read_keys()...")
            keys = self._read_keys()
            
            print(f"🔍 [TERMINAL] KEY_SERVICE: _read_keys() retornó {len(keys) if keys else 0} llaves")
            log.info(f"🔍 [KEY_SERVICE] _read_keys() retornó {len(keys) if keys else 0} llaves")
            
            if keys:
                print("🔍 [TERMINAL] KEY_SERVICE: LLAVES LEÍDAS DEL ARCHIVO:")
                log.info("🔍 [KEY_SERVICE] Primeras 2 llaves leídas:")
                for i, key in enumerate(keys[:2]):
                    filtered_key = {k: v for k, v in key.items() if k != 'key_hex'}
                    print(f"  {i+1}. {filtered_key}")
                    log.info(f"🔍 [KEY_SERVICE]   Llave {i+1}: {filtered_key}")
            else:
                print("🔍 [TERMINAL] KEY_SERVICE: NO SE ENCONTRARON LLAVES EN EL ARCHIVO")
            
            # Migrar llaves que no tienen ID (compatibilidad con versiones anteriores)
            keys_modified = False
            for key in keys:
                if 'id' not in key:
                    key['id'] = self._generate_unique_id(keys)
                    keys_modified = True
                    print(f"🔍 [TERMINAL] KEY_SERVICE: Asignando ID {key['id']} a llave con KCV {key.get('kcv')}")
                    log.info(f"Asignando ID {key['id']} a llave existente con KCV {key.get('kcv')}")
            
            # Guardar cambios si se modificaron las llaves
            if keys_modified:
                self._write_keys(keys)
                print("🔍 [TERMINAL] KEY_SERVICE: Migración de IDs completada")
                log.info("Migración de IDs completada para llaves existentes")
            
            print(f"🔍 [TERMINAL] KEY_SERVICE: RETORNANDO {len(keys)} llaves al endpoint")
            log.info(f"🔍 [KEY_SERVICE] get_all_keys() completado - retornando {len(keys)} llaves")
            return keys
            
        except Exception as e:
            print(f"🔍 [TERMINAL] KEY_SERVICE: ❌ ERROR en get_all_keys(): {e}")
            log.error(f"🔍 [KEY_SERVICE] ❌ ERROR en get_all_keys(): {e}")
            log.exception("🔍 [KEY_SERVICE] Stack trace:")
            return []

    def save_key(self, key_data: dict) -> bool:
        with self._lock:
            log.info(f"🔍 [KEY_SERVICE] save_key() iniciado para KCV: {key_data.get('kcv')}")
            log.info(f"🔍 [KEY_SERVICE] Datos a guardar: {key_data}")
            
            try:
                # 1. Leer llaves existentes
                keys = self._read_keys()
                log.info(f"🔍 [KEY_SERVICE] Llaves existentes leídas: {len(keys)}")
                
                # 2. Verificar duplicados
                key_found = False
                for i, key in enumerate(keys):
                    if key.get('kcv') == key_data.get('kcv'):
                        log.warning(f"🔍 [KEY_SERVICE] ⚠️ KCV duplicado encontrado ({key_data['kcv']}). Se actualizará la llave existente.")
                        # Mantener el ID existente si ya lo tiene
                        if 'id' in key:
                            key_data['id'] = key['id']
                            log.info(f"🔍 [KEY_SERVICE] Manteniendo ID existente: {key['id']}")
                        keys[i] = key_data
                        key_found = True
                        break
                
                if not key_found:
                    # 3. Asignar un nuevo ID único para llaves nuevas
                    new_id = self._generate_unique_id(keys)
                    key_data['id'] = new_id
                    log.info(f"🔍 [KEY_SERVICE] Asignando nuevo ID: {new_id}")
                    
                    keys.append(key_data)
                    log.info(f"🔍 [KEY_SERVICE] Llave agregada a la lista. Total ahora: {len(keys)}")
                
                # 4. Escribir al archivo
                log.info(f"🔍 [KEY_SERVICE] Intentando escribir {len(keys)} llaves al archivo...")
                self._write_keys(keys)
                log.info(f"🔍 [KEY_SERVICE] ✅ Escritura completada exitosamente")
                
                # 5. Verificación post-escritura
                verification_keys = self._read_keys()
                log.info(f"🔍 [KEY_SERVICE] Verificación: {len(verification_keys)} llaves en archivo después de guardar")
                
                log.info(f"🔍 [KEY_SERVICE] ✅ Llave con KCV {key_data['kcv']} guardada/actualizada exitosamente con ID {key_data.get('id')}.")
                return True
                
            except Exception as e:
                log.error(f"🔍 [KEY_SERVICE] ❌ Error crítico al guardar la llave con KCV {key_data.get('kcv')}: {e}")
                log.exception("🔍 [KEY_SERVICE] Stack trace completo:")
                return False
            finally:
                log.info(f"🔍 [KEY_SERVICE] save_key() finalizado para KCV: {key_data.get('kcv')}")

    def get_key_by_kcv(self, kcv: str) -> dict or None:
        keys = self.get_all_keys()
        return next((key for key in keys if key.get('kcv') == kcv), None)

    def get_key_by_id(self, key_id: int) -> dict or None:
        """Busca una llave por su ID único."""
        keys = self.get_all_keys()
        return next((key for key in keys if key.get('id') == key_id), None)

    def add_serial_to_key(self, kcv: str, serial_number: str):
        """Añade o actualiza el número de serie de un POS a una llave existente."""
        with self._lock:
            keys = self._read_keys()
            key_found = False
            for key in keys:
                if key.get('kcv') == kcv:
                    key['pos_serial'] = serial_number
                    key['last_injection_date'] = datetime.now(pytz.timezone(current_app.config.get('TIMEZONE', 'UTC'))).isoformat()
                    key_found = True
                    break
            
            if key_found:
                self._write_keys(keys)
                log.info(f"Serial '{serial_number}' asociado a la llave con KCV {kcv}.")
                return True
            
            log.warning(f"No se encontró la llave con KCV {kcv} para asociarle un serial.")
            return False

    def update_injection_date(self, kcv: str):
        """Actualiza la fecha de última inyección de una llave."""
        with self._lock:
            keys = self._read_keys()
            key_found = False
            for key in keys:
                if key.get('kcv') == kcv:
                    key['last_injection_date'] = datetime.now(pytz.timezone(current_app.config.get('TIMEZONE', 'UTC'))).isoformat()
                    key_found = True
                    break
            
            if key_found:
                self._write_keys(keys)
                log.info(f"Fecha de inyección actualizada para la llave con KCV {kcv}.")
                return True
            
            log.warning(f"No se encontró la llave con KCV {kcv} para actualizar la fecha de inyección.")
            return False

    def clear_all_keys(self):
        with self._lock:
            log.info("KeyStorageService: Solicitud para borrar todas las llaves.")
            try:
                self._write_keys([])
                log.info("Todas las llaves han sido borradas del archivo.")
                return True
            except Exception as e:
                log.error(f"Error al intentar borrar todas las llaves: {e}")
                return False

    def delete_key_by_kcv(self, kcv: str) -> bool:
        """Elimina una llave específica del almacén local por su KCV."""
        with self._lock:
            log.info(f"KeyStorageService: Solicitud para eliminar llave con KCV {kcv}")
            try:
                keys = self._read_keys()
                original_count = len(keys)
                
                # Filtrar las llaves manteniendo todas excepto la que coincide con el KCV
                keys = [key for key in keys if key.get('kcv') != kcv]
                new_count = len(keys)
                
                if original_count == new_count:
                    log.warning(f"No se encontró llave con KCV {kcv} para eliminar")
                    return False
                
                self._write_keys(keys)
                log.info(f"Llave con KCV {kcv} eliminada exitosamente del almacén local")
                return True
                
            except Exception as e:
                log.error(f"Error al intentar eliminar llave con KCV {kcv}: {e}")
                return False

    def delete_key_by_id(self, key_id: int) -> dict or None:
        """Elimina una llave específica del almacén local por su ID y retorna los datos de la llave eliminada."""
        with self._lock:
            log.info(f"KeyStorageService: Solicitud para eliminar llave con ID {key_id}")
            try:
                keys = self._read_keys()
                key_to_delete = None
                
                # Buscar la llave por ID y eliminarla
                new_keys = []
                for key in keys:
                    if key.get('id') == key_id:
                        key_to_delete = key
                        log.info(f"Llave encontrada: ID={key_id}, KCV={key.get('kcv')}")
                    else:
                        new_keys.append(key)
                
                if key_to_delete is None:
                    log.warning(f"No se encontró llave con ID {key_id} para eliminar")
                    return None
                
                self._write_keys(new_keys)
                log.info(f"Llave con ID {key_id} eliminada exitosamente del almacén local")
                return key_to_delete
                
            except Exception as e:
                log.error(f"Error al intentar eliminar llave con ID {key_id}: {e}")
                return None

key_storage_service = KeyStorageService() 