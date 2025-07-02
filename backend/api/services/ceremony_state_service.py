# api/services/ceremony_state_service.py
import logging
from threading import RLock
from datetime import datetime
import pytz
from flask import current_app
from ..util.crypto_utils import xor_hex_strings, calculate_key_checksum, derive_ipek
from .key_storage_service import KeyStorageService

log = logging.getLogger(__name__)

# Tipos de llave que son BDK y requieren derivación de IPEK
DUKPT_BDK_TYPES = ["03", "08", "0B", "10"]

class CeremonyStateService:
    """
    Gestiona el estado de una ceremonia de creación de llaves en curso.
    Es una clase singleton para mantener un único estado en toda la app.
    """
    _instance = None
    _lock = RLock()

    def __new__(cls, *args, **kwargs):
        if not cls._instance:
            with cls._lock:
                if not cls._instance:
                    cls._instance = super(CeremonyStateService, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        # El init puede ser llamado múltiples veces, usamos hasattr para inicializar solo una vez.
        if not hasattr(self, 'initialized'):
            with self._lock:
                self.reset()
                self.storage_service = KeyStorageService()
                self.initialized = True
    
    def reset(self):
        """Resetea el estado de la ceremonia a sus valores iniciales."""
        with self._lock:
            log.info("CeremonyStateService: Reseteando estado de la ceremonia.")
            self.params = {}
            self.components = []
            self.partial_kcvs = []
            self.is_finalized = False
            self.error = None
            self.num_custodians = 2  # Por defecto 2 custodians
            self.current_custodian = 0

    def start(self, params: dict):
        """Inicia una nueva ceremonia con los parámetros dados."""
        self.reset()
        with self._lock:
            self.num_custodians = params.get('num_custodians', 2)
            if self.num_custodians < 2 or self.num_custodians > 3:
                raise ValueError("El número de custodios debe ser entre 2 y 3")
            
            self.params = {
                "universal_key": True  # Marca que es una llave universal
            }
            self.current_custodian = 1
            log.info(f"Ceremonia iniciada para llave universal con {self.num_custodians} custodios")

    def add_component(self, component_hex: str) -> dict:
        """Añade un componente y calcula el KCV del componente individual."""
        with self._lock:
            if self.is_finalized:
                return {"error": "La ceremonia ya ha sido finalizada."}
            
            # Verificar que no se excedan los custodios configurados
            if len(self.components) >= self.num_custodians:
                return {"error": f"Ya se han ingresado todos los componentes requeridos ({self.num_custodians} custodios)."}

            self.components.append(component_hex)
            component_number = len(self.components)
            log.info(f"Añadido componente #{component_number}.")

            # Calcular el KCV del componente INDIVIDUAL que se acaba de agregar
            component_kcv = calculate_key_checksum(component_hex)
            self.partial_kcvs.append(component_kcv)
            log.info(f"KCV del componente #{component_number}: {component_kcv}")
            
            # Incrementar el custodio actual
            self.current_custodian = min(self.current_custodian + 1, self.num_custodians)

            return {"kcv": component_kcv, "component_count": component_number}

    def finalize(self) -> dict:
        """Finaliza la ceremonia generando una llave universal."""
        with self._lock:
            if self.is_finalized:
                return {"error": "La ceremonia ya ha sido finalizada."}
            if len(self.components) != self.num_custodians:
                return {"error": f"Se necesitan exactamente {self.num_custodians} componentes para finalizar. Actualmente hay {len(self.components)}."}

            log.info("Finalizando ceremonia de llave universal...")
            
            # Generar llave combinando todos los componentes con XOR
            combined_key = xor_hex_strings(self.components)
            final_kcv = calculate_key_checksum(combined_key)
            
            log.info(f"Llave universal generada con KCV: {final_kcv}")

            try:
                tz_str = current_app.config.get('TIMEZONE', 'UTC')
                timezone = pytz.timezone(tz_str)
            except pytz.UnknownTimeZoneError:
                log.warning(f"Zona horaria '{tz_str}' desconocida. Usando UTC.")
                timezone = pytz.utc
            
            timestamp = datetime.now(timezone).isoformat()

            # Guardar como llave universal sin tipo específico
            key_data = {
                "kcv": final_kcv,
                "key_hex": combined_key,
                "is_universal": True,  # Marca que es una llave universal
                "timestamp": timestamp
            }
            
            log.info(f"🔍 [CEREMONY] Intentando guardar llave con KCV {final_kcv}...")
            log.info(f"🔍 [CEREMONY] Datos de la llave: {key_data}")
            
            save_result = self.storage_service.save_key(key_data)
            log.info(f"🔍 [CEREMONY] Resultado de save_key(): {save_result}")
            
            if save_result:
                log.info("🔍 [CEREMONY] ✅ Llave universal guardada exitosamente en el almacén.")
                self.is_finalized = True
                
                # Guardar KCV antes de resetear para devolver en la respuesta
                final_kcv_response = final_kcv
                
                # Resetear el estado después de finalizar exitosamente para permitir nueva ceremonia
                log.info("🔍 [CEREMONY] Ceremonia finalizada exitosamente. Reseteando estado para permitir nueva ceremonia.")
                self.reset()
                
                return {"kcv": final_kcv_response, "message": "Llave universal guardada exitosamente."}
            else:
                log.error("🔍 [CEREMONY] ❌ Error al guardar la llave. KCV duplicado o error de archivo.")
                return {"error": "No se pudo guardar la llave (KCV duplicado o error de archivo)."}

    def get_state(self):
        """Devuelve el estado actual de la ceremonia."""
        with self._lock:
            state = {
                "params": self.params,
                "component_count": len(self.components),
                "partial_kcvs": self.partial_kcvs,
                "is_finalized": self.is_finalized,
                "error": self.error,
                "num_custodians": getattr(self, 'num_custodians', 2),
                "current_custodian": getattr(self, 'current_custodian', 0),
                "active": bool(self.params)
            }
            return state

# Instancia Singleton para ser usada en la app
ceremony_state_service = CeremonyStateService() 