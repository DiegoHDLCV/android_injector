from flask_login import UserMixin
import bcrypt
from datetime import datetime

class User(UserMixin):
    def __init__(self, id, username, password_hash, role, **kwargs):
        self.id = id
        self.username = username
        self.password_hash = password_hash
        self.role = role
        
        # Campos opcionales con valores por defecto
        self.created_at = kwargs.get('created_at', datetime.now().isoformat())
        self.password_updated_at = kwargs.get('password_updated_at', None)
        self.last_login = kwargs.get('last_login', None)
        
        # Permitir otros campos adicionales
        for key, value in kwargs.items():
            if not hasattr(self, key):
                setattr(self, key, value)

    def check_password(self, password):
        """Verifica la contraseña hasheada."""
        return bcrypt.checkpw(password.encode('utf-8'), self.password_hash.encode('utf-8'))

    def set_password(self, password):
        """Hashea la contraseña y la guarda."""
        self.password_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
        self.password_updated_at = datetime.now().isoformat()

    @staticmethod
    def hash_password(password):
        """Hashea una contraseña (método estático)."""
        return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

    @property
    def is_admin(self):
        return self.role == 'admin'
    
    def to_dict(self):
        """Convierte el objeto User a un diccionario para serialización."""
        base_dict = {
            "id": self.id,
            "username": self.username,
            "password_hash": self.password_hash,
            "role": self.role,
            "created_at": self.created_at
        }
        
        # Agregar campos opcionales si existen
        if self.password_updated_at:
            base_dict["password_updated_at"] = self.password_updated_at
        if self.last_login:
            base_dict["last_login"] = self.last_login
            
        return base_dict
    
    def get_id(self):
        """Retorna el ID del usuario como string (requerido por Flask-Login)."""
        return str(self.id)
