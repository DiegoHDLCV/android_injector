# api/services/injection_log_service.py
import json
import logging
import os
from datetime import datetime
import pytz
from threading import RLock
from flask import current_app

log = logging.getLogger(__name__)

INJECTION_LOG_PATH = os.path.join('data', 'injection_logs.json')

class InjectionLogService:
    """
    Servicio para gestionar el registro de inyecciones exitosas.
    Mantiene un historial detallado de qué llaves fueron inyectadas, 
    dónde y cuándo.
    """
    _instance = None
    
    def __new__(cls):
        if not cls._instance:
            cls._instance = super(InjectionLogService, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        if not hasattr(self, 'initialized'):
            self._lock = RLock()
            self.initialized = True
            log.info(f"InjectionLogService inicializado. Log: {INJECTION_LOG_PATH}")
            if not os.path.exists(INJECTION_LOG_PATH):
                self._write_logs([])

    def _read_logs(self) -> list:
        with self._lock:
            try:
                with open(INJECTION_LOG_PATH, 'r') as f:
                    return json.load(f)
            except (FileNotFoundError, json.JSONDecodeError):
                return []

    def _write_logs(self, logs: list):
        with self._lock:
            # Mantener solo los últimos 1000 registros para evitar que el archivo crezca mucho
            if len(logs) > 1000:
                logs = logs[-1000:]
            
            with open(INJECTION_LOG_PATH, 'w') as f:
                json.dump(logs, f, indent=2)

    def log_profile_injection(self, profile_data: dict, device_serial: str, injected_keys: list):
        """
        Registra una inyección exitosa de perfil completo.
        
        Args:
            profile_data: Datos del perfil inyectado
            device_serial: Serial del dispositivo donde se inyectó
            injected_keys: Lista de llaves que se inyectaron exitosamente
        """
        with self._lock:
            try:
                tz_str = current_app.config.get('TIMEZONE', 'UTC')
                timezone = pytz.timezone(tz_str)
            except pytz.UnknownTimeZoneError:
                timezone = pytz.utc
            
            timestamp = datetime.now(timezone).isoformat()
            
            injection_record = {
                "id": self._generate_log_id(),
                "timestamp": timestamp,
                "type": "profile_injection",
                "profile": {
                    "id": profile_data.get('id'),
                    "name": profile_data.get('name'),
                    "application_type": profile_data.get('application_type'),
                    "description": profile_data.get('description')
                },
                "device": {
                    "serial": device_serial,
                    "connection_timestamp": timestamp
                },
                "injected_keys": injected_keys,
                "summary": {
                    "total_keys": len(injected_keys),
                    "profile_complete": len(injected_keys) == len(profile_data.get('keys', [])),
                    "slots_used": [key['slot'] for key in injected_keys]
                }
            }
            
            logs = self._read_logs()
            logs.append(injection_record)
            self._write_logs(logs)
            
            log.info(f"Inyección registrada: Perfil '{profile_data['name']}' con {len(injected_keys)} llaves en dispositivo {device_serial}")
            
            return injection_record

    def log_individual_key_injection(self, key_data: dict, slot: str, key_type: str, device_serial: str, profile_context: dict = None):
        """
        Registra una inyección exitosa de llave individual.
        
        Args:
            key_data: Datos de la llave inyectada
            slot: Slot donde se inyectó
            key_type: Tipo de llave usado para la inyección
            device_serial: Serial del dispositivo
            profile_context: Contexto del perfil si aplica
        """
        with self._lock:
            try:
                tz_str = current_app.config.get('TIMEZONE', 'UTC')
                timezone = pytz.timezone(tz_str)
            except pytz.UnknownTimeZoneError:
                timezone = pytz.utc
            
            timestamp = datetime.now(timezone).isoformat()
            
            injection_record = {
                "id": self._generate_log_id(),
                "timestamp": timestamp,
                "type": "individual_key_injection",
                "key": {
                    "id": key_data.get('id'),
                    "kcv": key_data.get('kcv'),
                    "is_universal": key_data.get('is_universal', False),
                    "original_type": key_data.get('key_type'),
                    "injection_type": key_type,
                    "slot": slot
                },
                "device": {
                    "serial": device_serial,
                    "connection_timestamp": timestamp
                },
                "profile_context": profile_context,
                "summary": {
                    "total_keys": 1,
                    "slots_used": [slot]
                }
            }
            
            logs = self._read_logs()
            logs.append(injection_record)
            self._write_logs(logs)
            
            log.info(f"Inyección individual registrada: Llave {key_data['kcv']} en slot {slot} del dispositivo {device_serial}")
            
            return injection_record

    def get_injection_history(self, limit: int = 100) -> list:
        """Obtiene el historial de inyecciones más recientes."""
        logs = self._read_logs()
        return logs[-limit:] if logs else []

    def get_device_injections(self, device_serial: str) -> list:
        """Obtiene todas las inyecciones de un dispositivo específico."""
        logs = self._read_logs()
        return [log for log in logs if log.get('device', {}).get('serial') == device_serial]

    def get_key_injection_history(self, kcv: str) -> list:
        """Obtiene el historial de inyecciones de una llave específica."""
        logs = self._read_logs()
        result = []
        
        for log_entry in logs:
            if log_entry.get('type') == 'profile_injection':
                for key in log_entry.get('injected_keys', []):
                    if key.get('kcv') == kcv:
                        result.append(log_entry)
                        break
            elif log_entry.get('type') == 'individual_key_injection':
                if log_entry.get('key', {}).get('kcv') == kcv:
                    result.append(log_entry)
        
        return result

    def get_profile_injection_history(self, profile_id: int) -> list:
        """Obtiene el historial de inyecciones de un perfil específico."""
        logs = self._read_logs()
        return [log for log in logs if log.get('profile', {}).get('id') == profile_id]

    def _generate_log_id(self) -> str:
        """Genera un ID único para el registro de log."""
        logs = self._read_logs()
        if not logs:
            return "INJ_0001"
        
        # Obtener el último ID y incrementarlo
        last_id = logs[-1].get('id', 'INJ_0000')
        try:
            number = int(last_id.split('_')[1]) + 1
            return f"INJ_{number:04d}"
        except (IndexError, ValueError):
            return f"INJ_{len(logs) + 1:04d}"

    def clear_logs(self):
        """Limpia todos los logs de inyección."""
        with self._lock:
            log.warning("Limpiando todos los logs de inyección")
            self._write_logs([])

# Instancia singleton
injection_log_service = InjectionLogService() 