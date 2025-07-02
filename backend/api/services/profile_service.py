# api/services/profile_service.py
import json
import logging
from threading import RLock
from datetime import datetime
import os
import pytz
from flask import current_app

log = logging.getLogger(__name__)

PROFILES_STORAGE_PATH = os.path.join('data', 'profiles.json')

class ProfileService:
    _instance = None
    
    def __new__(cls):
        if not cls._instance:
            cls._instance = super(ProfileService, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        """Inicializador."""
        if not hasattr(self, 'initialized'):
            self._lock = RLock()
            self.initialized = True
            log.info(f"ProfileService inicializado. Almacén: {PROFILES_STORAGE_PATH}")
            if not os.path.exists(PROFILES_STORAGE_PATH):
                self._write_profiles([])

    def _read_profiles(self) -> list:
        with self._lock:
            try:
                with open(PROFILES_STORAGE_PATH, 'r') as f:
                    return json.load(f)
            except (FileNotFoundError, json.JSONDecodeError):
                return []

    def _write_profiles(self, profiles: list):
        with self._lock:
            with open(PROFILES_STORAGE_PATH, 'w') as f:
                json.dump(profiles, f, indent=4)

    def _generate_unique_id(self, existing_profiles: list) -> int:
        """Genera un ID único numérico para un nuevo perfil."""
        existing_ids = {profile.get('id', 0) for profile in existing_profiles if 'id' in profile}
        
        # Empezar desde 1 y encontrar el primer ID disponible
        new_id = 1
        while new_id in existing_ids:
            new_id += 1
        
        return new_id

    def get_all_profiles(self) -> list:
        """Obtiene todos los perfiles disponibles."""
        return self._read_profiles()

    def get_profile_by_id(self, profile_id: int) -> dict or None:
        """Busca un perfil por su ID único."""
        profiles = self.get_all_profiles()
        return next((profile for profile in profiles if profile.get('id') == profile_id), None)

    def save_profile(self, profile_data: dict) -> bool:
        """Guarda un nuevo perfil o actualiza uno existente."""
        with self._lock:
            log.debug(f"ProfileService: Lock adquirido para save_profile (Nombre: {profile_data.get('name')}).")
            try:
                profiles = self._read_profiles()
                
                profile_found = False
                if 'id' in profile_data and profile_data['id']:
                    # Actualizar perfil existente
                    for i, profile in enumerate(profiles):
                        if profile.get('id') == profile_data.get('id'):
                            profile_data['last_modified'] = datetime.now(pytz.timezone(current_app.config.get('TIMEZONE', 'UTC'))).isoformat()
                            profiles[i] = profile_data
                            profile_found = True
                            break
                
                if not profile_found:
                    # Crear nuevo perfil
                    profile_data['id'] = self._generate_unique_id(profiles)
                    profile_data['created_date'] = datetime.now(pytz.timezone(current_app.config.get('TIMEZONE', 'UTC'))).isoformat()
                    profiles.append(profile_data)
                
                self._write_profiles(profiles)
                log.info(f"Perfil '{profile_data['name']}' guardado/actualizado exitosamente con ID {profile_data.get('id')}.")
                return True
            except Exception as e:
                log.exception(f"Error crítico al guardar el perfil '{profile_data.get('name')}'.")
                return False
            finally:
                log.debug(f"ProfileService: Lock liberado de save_profile (Nombre: {profile_data.get('name')}).")

    def delete_profile(self, profile_id: int) -> bool:
        """Elimina un perfil específico."""
        with self._lock:
            log.info(f"ProfileService: Solicitud para eliminar perfil con ID {profile_id}")
            try:
                profiles = self._read_profiles()
                original_count = len(profiles)
                
                # Filtrar los perfiles manteniendo todos excepto el que coincide con el ID
                profiles = [profile for profile in profiles if profile.get('id') != profile_id]
                new_count = len(profiles)
                
                if original_count == new_count:
                    log.warning(f"No se encontró perfil con ID {profile_id} para eliminar")
                    return False
                
                self._write_profiles(profiles)
                log.info(f"Perfil con ID {profile_id} eliminado exitosamente")
                return True
                
            except Exception as e:
                log.error(f"Error al intentar eliminar perfil con ID {profile_id}: {e}")
                return False

    def assign_key_to_profile(self, profile_id: int, slot: str, key_id: int) -> bool:
        """Asigna una llave específica a un slot en un perfil."""
        with self._lock:
            try:
                profiles = self._read_profiles()
                
                for profile in profiles:
                    if profile.get('id') == profile_id:
                        # Buscar el slot en las llaves del perfil
                        for key_config in profile.get('keys', []):
                            if key_config.get('slot') == slot:
                                key_config['key_id'] = key_id
                                key_config['assigned_date'] = datetime.now(pytz.timezone(current_app.config.get('TIMEZONE', 'UTC'))).isoformat()
                                break
                        
                        profile['last_modified'] = datetime.now(pytz.timezone(current_app.config.get('TIMEZONE', 'UTC'))).isoformat()
                        self._write_profiles(profiles)
                        log.info(f"Llave ID {key_id} asignada al slot {slot} del perfil {profile_id}")
                        return True
                
                log.warning(f"No se encontró el perfil {profile_id} o el slot {slot}")
                return False
                
            except Exception as e:
                log.error(f"Error asignando llave al perfil: {e}")
                return False

    def unassign_key_from_profile(self, profile_id: int, slot: str) -> bool:
        """Desasigna una llave de un slot en un perfil."""
        with self._lock:
            try:
                profiles = self._read_profiles()
                
                for profile in profiles:
                    if profile.get('id') == profile_id:
                        # Buscar el slot en las llaves del perfil
                        for key_config in profile.get('keys', []):
                            if key_config.get('slot') == slot:
                                key_config['key_id'] = None
                                if 'assigned_date' in key_config:
                                    del key_config['assigned_date']
                                break
                        
                        profile['last_modified'] = datetime.now(pytz.timezone(current_app.config.get('TIMEZONE', 'UTC'))).isoformat()
                        self._write_profiles(profiles)
                        log.info(f"Llave desasignada del slot {slot} del perfil {profile_id}")
                        return True
                
                log.warning(f"No se encontró el perfil {profile_id} o el slot {slot}")
                return False
                
            except Exception as e:
                log.error(f"Error desasignando llave del perfil: {e}")
                return False

    def get_profile_with_key_details(self, profile_id: int) -> dict or None:
        """Obtiene un perfil con los detalles completos de las llaves asignadas."""
        from .key_storage_service import KeyStorageService
        
        profile = self.get_profile_by_id(profile_id)
        if not profile:
            return None
        
        key_storage_service = KeyStorageService()
        
        # Enriquecer los datos de las llaves
        enriched_keys = []
        for key_config in profile.get('keys', []):
            enriched_key = key_config.copy()
            
            if key_config.get('key_id'):
                key_details = key_storage_service.get_key_by_id(key_config['key_id'])
                if key_details:
                    enriched_key['key_details'] = {
                        'kcv': key_details.get('kcv'),
                        'key_type': key_details.get('key_type'),
                        'ksn': key_details.get('ksn'),
                        'timestamp': key_details.get('timestamp')
                    }
            
            enriched_keys.append(enriched_key)
        
        profile_enriched = profile.copy()
        profile_enriched['keys'] = enriched_keys
        
        return profile_enriched

profile_service = ProfileService() 