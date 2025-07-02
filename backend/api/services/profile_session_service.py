# api/services/profile_session_service.py
import logging
import threading
import time
from typing import Dict, Optional, Any

log = logging.getLogger(__name__)

class ProfileSessionService:
    """
    Servicio para manejar sesiones temporales durante la inyección de perfiles.
    Almacena temporalmente key_hex de KTKs para permitir cifrado local de working keys.
    """
    
    def __init__(self):
        self._sessions: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.Lock()
        self._session_timeout = 3600  # 1 hora
    
    def start_profile_session(self, profile_id: str, profile_name: str) -> str:
        """
        Inicia una nueva sesión de inyección para un perfil.
        
        Args:
            profile_id: ID del perfil
            profile_name: Nombre del perfil
            
        Returns:
            session_id: ID único de la sesión
        """
        session_id = f"{profile_id}_session"
        
        with self._lock:
            # Si ya existe una sesión activa para este perfil, finalizarla primero
            if session_id in self._sessions:
                log.warning(f"⚠️ Sesión existente encontrada para perfil {profile_id}, finalizando...")
                old_ktk_count = len(self._sessions[session_id]['ktk_data'])
                self._sessions[session_id]['ktk_data'].clear()
                del self._sessions[session_id]
                log.warning(f"🧹 Sesión anterior limpiada: {old_ktk_count} KTKs eliminadas")
            
            self._sessions[session_id] = {
                'profile_id': profile_id,
                'profile_name': profile_name,
                'ktk_data': {},
                'created_at': time.time(),
                'status': 'active'
            }
        
        log.info(f"🎯 Sesión de perfil iniciada: {session_id} para perfil '{profile_name}'")
        return session_id
    
    def store_ktk_data(self, session_id: str, ktk_slot: str, ktk_hex: str, ktk_kcv: str, ktk_usage: str):
        """
        Almacena temporalmente los datos de una KTK durante la sesión.
        
        Args:
            session_id: ID de la sesión
            ktk_slot: Slot de la KTK
            ktk_hex: Valor hexadecimal de la KTK (SENSIBLE)
            ktk_kcv: KCV de la KTK
            ktk_usage: Uso de la KTK (KTK, KEK, MASTER)
        """
        with self._lock:
            if session_id not in self._sessions:
                log.error(f"❌ Sesión no encontrada: {session_id}")
                return False
                
            self._sessions[session_id]['ktk_data'][ktk_slot] = {
                'ktk_hex': ktk_hex,
                'ktk_kcv': ktk_kcv,
                'ktk_usage': ktk_usage,
                'stored_at': time.time()
            }
        
        log.warning(f"🔐 KTK almacenada temporalmente en sesión {session_id}: Slot {ktk_slot}, KCV {ktk_kcv}")
        log.warning(f"⚠️ DATOS SENSIBLES EN MEMORIA - Se limpiarán al finalizar la sesión")
        return True
    
    def get_ktk_data(self, session_id: str, ktk_slot: str) -> Optional[Dict[str, str]]:
        """
        Recupera los datos de una KTK de la sesión.
        
        Args:
            session_id: ID de la sesión
            ktk_slot: Slot de la KTK a recuperar
            
        Returns:
            Dict con ktk_hex, ktk_kcv, ktk_usage o None si no se encuentra
        """
        with self._lock:
            session = self._sessions.get(session_id)
            if not session:
                log.error(f"❌ Sesión no encontrada: {session_id}")
                return None
                
            ktk_data = session['ktk_data'].get(ktk_slot)
            if not ktk_data:
                log.warning(f"⚠️ KTK no encontrada en sesión {session_id}: Slot {ktk_slot}")
                return None
                
            log.info(f"🔑 KTK recuperada de sesión {session_id}: Slot {ktk_slot}, KCV {ktk_data['ktk_kcv']}")
            return ktk_data
    
    def get_session_ktks(self, session_id: str) -> Dict[str, Dict[str, str]]:
        """
        Obtiene todas las KTKs almacenadas en una sesión.
        
        Args:
            session_id: ID de la sesión
            
        Returns:
            Dict con todas las KTKs de la sesión
        """
        with self._lock:
            session = self._sessions.get(session_id)
            if not session:
                return {}
            
            return session['ktk_data'].copy()
    
    def end_profile_session(self, session_id: str):
        """
        Finaliza una sesión de perfil y limpia todos los datos sensibles.
        
        Args:
            session_id: ID de la sesión a finalizar
        """
        with self._lock:
            session = self._sessions.get(session_id)
            if not session:
                log.warning(f"⚠️ Intento de finalizar sesión inexistente: {session_id}")
                return
                
            # Limpiar datos sensibles
            ktk_count = len(session['ktk_data'])
            session['ktk_data'].clear()
            session['status'] = 'completed'
            
            # Eliminar sesión completamente
            del self._sessions[session_id]
        
        log.warning(f"🧹 Sesión finalizada y datos sensibles limpiados: {session_id}")
        log.warning(f"🔒 Se eliminaron {ktk_count} KTKs temporales de la memoria")
    
    def cleanup_expired_sessions(self):
        """
        Limpia sesiones expiradas automáticamente.
        """
        current_time = time.time()
        expired_sessions = []
        
        with self._lock:
            for session_id, session in self._sessions.items():
                if current_time - session['created_at'] > self._session_timeout:
                    expired_sessions.append(session_id)
        
        for session_id in expired_sessions:
            log.warning(f"⏰ Limpiando sesión expirada: {session_id}")
            self.end_profile_session(session_id)
    
    def get_session_info(self, session_id: str) -> Optional[Dict[str, Any]]:
        """
        Obtiene información básica de una sesión (sin datos sensibles).
        
        Args:
            session_id: ID de la sesión
            
        Returns:
            Dict con información de la sesión o None si no existe
        """
        with self._lock:
            session = self._sessions.get(session_id)
            if not session:
                return None
                
            return {
                'profile_id': session['profile_id'],
                'profile_name': session['profile_name'],
                'created_at': session['created_at'],
                'status': session['status'],
                'ktk_slots': list(session['ktk_data'].keys()),
                'ktk_count': len(session['ktk_data'])
            }

# Instancia global del servicio de sesión
profile_session_service = ProfileSessionService() 