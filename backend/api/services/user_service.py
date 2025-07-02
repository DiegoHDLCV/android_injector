# api/services/user_service.py
import json
import os
from threading import RLock
import bcrypt
from ..domain.user import User
import logging

log = logging.getLogger(__name__)
USERS_FILE = os.path.join('data', 'users.json')

class UserService:
    def __init__(self):
        self._lock = RLock()
        self._users = self._load_users()
        self.initialized = True

    def _load_users(self):
        with self._lock:
            try:
                with open(USERS_FILE, 'r') as f:
                    users_data = json.load(f)
                    log.info(f"Cargados {len(users_data)} usuarios desde {USERS_FILE}")
                    return [User(**data) for data in users_data]
            except (FileNotFoundError, json.JSONDecodeError) as e:
                log.error(f"No se pudo cargar el archivo de usuarios en {USERS_FILE}: {e}")
                return []

    def _save_users(self):
        with self._lock:
            users_data = [u.to_dict() for u in self._users]
            with open(USERS_FILE, 'w') as f:
                json.dump(users_data, f, indent=4)
            log.info(f"Guardados {len(self._users)} usuarios en {USERS_FILE}")

    def get_all_users(self) -> list[User]:
        return self._users

    def find_by_username(self, username: str) -> User or None:
        log.debug(f"Buscando usuario: '{username}'")
        user = next((user for user in self._users if user.username == username), None)
        if user:
            log.debug(f"Usuario '{username}' encontrado.")
        else:
            log.warning(f"Usuario '{username}' no encontrado.")
        return user

    def get_by_id(self, user_id: str) -> User or None:
        return next((user for user in self._users if user.id == user_id), None)

    def create_user(self, username, password, role) -> tuple[bool, str]:
        with self._lock:
            if self.find_by_username(username):
                return False, f"El usuario '{username}' ya existe."
            
            # Generar nuevo ID
            max_id = max([int(u.id) for u in self._users], default=0)
            new_id = str(max_id + 1)
            
            # Hashear la contraseña
            password_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
            
            new_user = User(id=new_id, username=username, password_hash=password_hash, role=role)
            
            self._users.append(new_user)
            self._save_users()
            return True, f"Usuario '{username}' creado exitosamente."

    def delete_user(self, user_id) -> tuple[bool, str]:
        with self._lock:
            user_to_delete = self.get_by_id(user_id)
            if not user_to_delete:
                return False, "Usuario no encontrado."
            
            self._users = [user for user in self._users if user.id != user_id]
            self._save_users()
            return True, f"Usuario '{user_to_delete.username}' eliminado exitosamente."

    def update_user_password(self, user_id: str, new_password: str) -> tuple[bool, str]:
        with self._lock:
            user_to_update = self.get_by_id(user_id)
            if not user_to_update:
                return False, "Usuario no encontrado."
            
            user_to_update.set_password(new_password)
            self._save_users()
            log.info(f"Contraseña del usuario '{user_to_update.username}' (ID: {user_id}) actualizada exitosamente.")
            return True, f"Contraseña del usuario '{user_to_update.username}' actualizada exitosamente."

# Singleton instance
user_service = UserService() 