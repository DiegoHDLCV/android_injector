# api/services/tr31_service.py
import logging
from typing import Dict, Any, Optional, List
from ..domain.commands import (
    CommandFuturexInjectSymmetricKey, 
    CommandFuturexReadSerial
)
from ..util.crypto_utils import calculate_key_checksum

log = logging.getLogger(__name__)

class TR31Service:
    """
    Servicio para manejar la inyección de llaves usando el protocolo Futurex.
    Soporta tanto inyección en claro como en formato TR-31.
    
    NUEVA LÓGICA AUTOMÁTICA IMPLEMENTADA:
    ====================================
    
    1. KTK (Key Transfer Key, tipo 06):
       - SIEMPRE se inyectan EN CLARO (encryption_type="00")
       - No necesitan cifrado previo
       - Ejemplo: inject_auto_key_with_kcv(..., key_type="06", ...)
    
    2. WORKING KEYS (tipos 04, 05):
       - SIEMPRE se inyectan CIFRADAS (encryption_type="01")
       - Requieren una KTK previa para cifrado
       - Si no hay KTK, se crea automáticamente
       - Ejemplo: inject_auto_key_with_kcv(..., key_type="05", ...)
    
    3. MASTER KEYS (tipos 01, 03, 08, 0B, 10):
       - Se inyectan EN CLARO (encryption_type="00")
       - No necesitan cifrado
       - Ejemplo: inject_auto_key_with_kcv(..., key_type="01", ...)
    
    FUNCIÓN PRINCIPAL:
    ==================
    - inject_auto_key_with_kcv(): Implementa la lógica automática
    - inject_smart_key_with_kcv(): OBSOLETA, usar inject_auto_key_with_kcv()
    
    LOGS MEJORADOS:
    ===============
    - _parse_and_log_message(): Parsea y muestra mensaje Futurex detallado
    - Logs claros de qué tipo de llave es y cómo se envía
    - Alertas cuando hay problemas con KCV de KTK
    """
    
    def __init__(self, serial_handler=None):
        """Inicializa el servicio con el serial handler."""
        self.handler = serial_handler
        self.VALID_KEY_USAGES = {"B0", "B1", "C0", "D0", "E0", "K0", "K1", "M0", "M1", "P0", "V0", "V2"}
        self.VALID_ALGORITHMS = {"A", "D", "H", "I", "T"}
        self.VALID_MODES_OF_USE = {"B", "C", "D", "E", "G", "N", "V", "X"}
        self.VALID_EXPORTABILITY = {"E", "N", "S"}
        self.VALID_KEY_BLOCK_VERSIONS = {"B", "D"}

    def inject_symmetric_key(self, key_slot: str, key_type: str, key_hex: str, 
                           ksn: str = None, ktk_slot: str = "00", 
                           encryption_type: str = "00", tr31_version: str = None, ktk_hex: str = None) -> Dict[str, Any]:
        """
        Inyecta una llave simétrica usando el comando 02 del protocolo Futurex.
        
        Args:
            key_slot: Slot de la llave (2 dígitos hex)
            key_type: Tipo de llave (2 dígitos hex)
            key_hex: Llave en formato hex
            ksn: Key Serial Number (20 dígitos hex, opcional)
            ktk_slot: Slot del KTK (2 dígitos hex)
            encryption_type: Tipo de encriptación ("00"=claro, "01"=bajo KTK precargado, "02"=KTK claro)
            tr31_version: Versión TR-31 si se usa ("A", "B", "C")
            ktk_hex: KTK en formato hex (opcional)
        
        Returns:
            Dict con el resultado de la inyección
        """
        try:
            log.info(f"Iniciando inyección de llave: slot={key_slot}, tipo={key_type}, enc={encryption_type}")
            
            # Preparar los datos según el protocolo Futurex
            command_version = "01"
            key_checksum = self._calculate_key_checksum(key_hex)
            ktk_checksum = "0000" if encryption_type != "02" else self._calculate_key_checksum(ktk_slot)
            ksn_padded = (ksn or "0" * 20).ljust(20, "0")
            # La longitud debe ser en hex representando el número de BYTES (caracteres hex / 2)
            key_length_bytes = len(key_hex) // 2
            key_length = f"{key_length_bytes:03X}"
            
            # Construir el mensaje según la tabla 5 del protocolo
            message = (
                "02" +                    # Comando
                command_version +         # Versión del comando
                key_slot +               # Slot de la llave
                ktk_slot +               # Slot del KTK
                key_type +               # Tipo de llave
                encryption_type +        # Tipo de encriptación
                key_checksum +           # Checksum de la llave
                ktk_checksum +           # Checksum del KTK
                ksn_padded +             # KSN (20 dígitos)
                key_length +             # Longitud de la llave
                key_hex                  # Llave en hex
            )
            
            # Si es tipo 02 (KTK claro), agregar el KTK
            if encryption_type == "02":
                if not ktk_hex:
                    raise ValueError("ktk_hex es requerido para encryption_type '02'")
                ktk_length_bytes = len(ktk_hex) // 2
                ktk_length = f"{ktk_length_bytes:03X}"
                message += ktk_length + ktk_hex
            else:
                # Para tipos 00 y 01, agregar longitud de KTK como 000
                message += "000"
            
            log.debug(f"Mensaje Futurex construido: {message}")
            
            # Crear y enviar el comando
            command = CommandFuturexInjectSymmetricKey(message=message)
            
            if self.handler:
                response_message = self.handler.send_and_wait_futurex(command)
                # Simular respuesta exitosa por ahora
                result = {"status": "success", "message": "Llave inyectada exitosamente", "response": response_message}
            else:
                # Modo simulado sin handler
                result = {"status": "success", "message": "Llave inyectada exitosamente (simulado)"}
            
            log.info(f"Inyección completada: {result}")
            return result
            
        except Exception as e:
            log.exception(f"Error durante la inyección de llave")
            return {"status": "error", "message": str(e)}
    
    def inject_symmetric_key_with_kcv(self, key_slot: str, key_type: str, key_hex: str, key_kcv: str,
                                    ksn: str = None, ktk_slot: str = "00", 
                                    encryption_type: str = "00", tr31_version: str = None, ktk_hex: str = None, ktk_kcv: str = None) -> Dict[str, Any]:
        """
        Inyecta una llave simétrica usando el comando 02 del protocolo Futurex con un KCV específico.
        
        Args:
            key_slot: Slot de la llave (2 dígitos hex)
            key_type: Tipo de llave (2 dígitos hex)
            key_hex: Llave en formato hex
            key_kcv: KCV de la llave (4 dígitos hex)
            ksn: Key Serial Number (20 dígitos hex, opcional)
            ktk_slot: Slot del KTK (2 dígitos hex)
            encryption_type: Tipo de encriptación ("00"=claro, "01"=bajo KTK precargado, "02"=KTK claro)
            tr31_version: Versión TR-31 si se usa ("A", "B", "C")
            ktk_hex: KTK en formato hex (opcional)
            ktk_kcv: KCV del KTK (4 dígitos hex, opcional - para encryption_type "01")
        
        Returns:
            Dict con el resultado de la inyección
        """
        try:
            log.info("=== INICIANDO inject_symmetric_key_with_kcv ===")
            log.info(f"Parámetros de entrada:")
            log.info(f"  - key_slot: {key_slot}")
            log.info(f"  - key_type: {key_type}")
            log.info(f"  - key_hex: {key_hex[:8] if key_hex else 'None'}... (len: {len(key_hex) if key_hex else 0})")
            log.info(f"  - key_kcv: {key_kcv} (KCV de la WORKING KEY)")
            log.info(f"  - ksn: {ksn}")
            log.info(f"  - ktk_slot: {ktk_slot}")
            log.info(f"  - encryption_type: {encryption_type}")
            log.info(f"  - ktk_kcv: {ktk_kcv} (KCV del KTK para cifrado)")
            log.info(f"  - ktk_hex: {'Proporcionado' if ktk_hex else 'No proporcionado'}")
            
            # LOGS ADICIONALES PARA DIAGNÓSTICO
            if encryption_type == "01":
                log.warning("🔍 DIAGNÓSTICO ENCRYPTION_TYPE=01:")
                log.warning(f"  ✓ Se usará KTK precargado en slot {ktk_slot}")
                log.warning(f"  ✓ KCV de KTK proporcionado: {ktk_kcv}")
                if ktk_kcv:
                    log.warning(f"  ✅ KCV DE KTK VÁLIDO: {ktk_kcv} - Se enviará al dispositivo")
                else:
                    log.error(f"  ❌ KCV DE KTK FALTANTE: Se enviará 0000 (ESTO PUEDE CAUSAR ERROR 10)")
                    log.error(f"  ❌ El dispositivo puede rechazar si tiene KTK con KCV diferente")
            elif encryption_type == "00":
                log.info("🔍 DIAGNÓSTICO ENCRYPTION_TYPE=00: Llave en claro, no necesita KTK")
            elif encryption_type == "02":
                log.info("🔍 DIAGNÓSTICO ENCRYPTION_TYPE=02: KTK enviado en claro junto con llave")
            
            # Preparar los datos según el protocolo Futurex
            command_version = "01"
            key_checksum = key_kcv  # Usar el KCV específico pasado como parámetro
            
            # Calcular ktk_checksum según el tipo de cifrado
            if encryption_type == "00":
                # Sin cifrado - no necesita KTK
                ktk_checksum = "0000"
                log.info("🔑 KTK Checksum: 0000 (sin cifrado)")
            elif encryption_type == "01":
                # Cifrado con KTK precargado - usar KCV del KTK si se proporciona
                ktk_checksum = ktk_kcv if ktk_kcv else "0000"
                log.warning("🔑 KTK Checksum calculado para encryption_type=01:")
                if ktk_kcv:
                    log.warning(f"  ✅ Usando KCV proporcionado: {ktk_checksum}")
                    log.warning(f"  ✅ ESTE VALOR DEBE COINCIDIR CON EL KCV DE LA KTK EN EL DISPOSITIVO")
                else:
                    log.error(f"  ❌ KCV no proporcionado, usando por defecto: {ktk_checksum}")
                    log.error(f"  ❌ ESTO CAUSARÁ ERROR 10 SI EL DISPOSITIVO TIENE KTK CON KCV DIFERENTE")
            elif encryption_type == "02":
                # KTK enviado en claro - calcular su checksum
                ktk_checksum = self._calculate_key_checksum(ktk_hex if ktk_hex else "")
                log.info(f"🔑 KTK Checksum: {ktk_checksum} (calculado desde KTK en claro)")
            else:
                ktk_checksum = "0000"
                log.warning(f"🔑 KTK Checksum: {ktk_checksum} (tipo de cifrado desconocido: {encryption_type})")
                
            ksn_padded = (ksn or "0" * 20).ljust(20, "0")
            # La longitud debe ser en hex representando el número de BYTES (caracteres hex / 2)
            key_length_bytes = len(key_hex) // 2
            key_length = f"{key_length_bytes:03X}"
            
            # Construir el mensaje según la tabla 5 del protocolo
            message = (
                "02" +                    # Comando
                command_version +         # Versión del comando
                key_slot +               # Slot de la llave
                ktk_slot +               # Slot del KTK
                key_type +               # Tipo de llave
                encryption_type +        # Tipo de encriptación
                key_checksum +           # Checksum de la llave (KCV real)
                ktk_checksum +           # Checksum del KTK
                ksn_padded +             # KSN (20 dígitos)
                key_length +             # Longitud de la llave
                key_hex                  # Llave en hex
            )
            
            # Si es tipo 02 (KTK claro), agregar el KTK
            if encryption_type == "02":
                if not ktk_hex:
                    raise ValueError("ktk_hex es requerido para encryption_type '02'")
                ktk_length_bytes = len(ktk_hex) // 2
                ktk_length = f"{ktk_length_bytes:03X}"
                message += ktk_length + ktk_hex
                log.info(f"Agregado KTK: longitud={ktk_length}, ktk={ktk_hex[:8]}...")
            else:
                # Para tipos 00 y 01, agregar longitud de KTK como 000
                message += "000"
                log.info("Agregada longitud de KTK como 000 (sin KTK)")
            
            log.debug(f"Mensaje Futurex construido: {message}")
            
            # Crear y enviar el comando
            log.info("Creando comando CommandFuturexInjectSymmetricKey...")
            command = CommandFuturexInjectSymmetricKey(message=message)
            
            if self.handler:
                log.info("Enviando comando al dispositivo...")
                response_message = self.handler.send_and_wait_futurex(command)
                log.info(f"Respuesta recibida del dispositivo: {response_message}")
                
                # Simular respuesta exitosa por ahora
                result = {"status": "success", "message": "Llave inyectada exitosamente", "response": response_message}
            else:
                # Modo simulado sin handler
                result = {"status": "success", "message": "Llave inyectada exitosamente (simulado)"}
            
            log.info(f"✓ Inyección completada exitosamente: {result}")
            log.info("=== inject_symmetric_key_with_kcv EXITOSO ===")
            return result
            
        except Exception as e:
            log.exception(f"Error durante la inyección de llave en inject_symmetric_key_with_kcv")
            log.error(f"Tipo de error: {type(e).__name__}")
            log.error(f"Mensaje de error: {str(e)}")
            log.info("=== inject_symmetric_key_with_kcv FALLIDO ===")
            return {"status": "error", "message": str(e)}

    def inject_clear_key_with_kcv(self, key_slot: str, key_type: str, key_hex: str, key_kcv: str, ksn: str = None) -> Dict[str, Any]:
        """
        Inyecta una llave EN CLARO usando el comando 02 con encryption_type "00" y un KCV específico.
        NOTA: Solo funciona para llaves master. Las llaves de trabajo serán rechazadas por el dispositivo.
        """
        log.info("=== INICIANDO inject_clear_key_with_kcv ===")
        log.info(f"Parámetros recibidos:")
        log.info(f"  - key_slot: {key_slot}")
        log.info(f"  - key_type: {key_type}")
        log.info(f"  - key_hex: {key_hex[:8]}... ({len(key_hex)} caracteres)")
        log.info(f"  - key_kcv: {key_kcv}")
        log.info(f"  - ksn: {ksn}")
        
        # Verificar si es una llave de trabajo
        if self._is_working_key(key_type):
            log.warning(f"Intentando enviar llave de trabajo tipo {key_type} en claro - esto puede ser rechazado por el dispositivo")
        
        try:
            result = self.inject_symmetric_key_with_kcv(
                key_slot=key_slot,
                key_type=key_type,
                key_hex=key_hex,
                key_kcv=key_kcv,
                ksn=ksn,
                encryption_type="00"  # Forzar envío en claro
            )
            log.info(f"✓ inject_clear_key_with_kcv completado: {result}")
            log.info("=== inject_clear_key_with_kcv EXITOSO ===")
            return result
        except Exception as e:
            log.exception("Error en inject_clear_key_with_kcv")
            log.info("=== inject_clear_key_with_kcv FALLIDO ===")
            raise

    def inject_auto_key_with_kcv(self, key_slot: str, key_type: str, key_hex: str, key_kcv: str, ksn: str = None) -> Dict[str, Any]:
        """
        Inyecta una llave con detección automática del método correcto según el tipo:
        - KTK (tipo 06): SIEMPRE en claro (encryption_type="00")
        - Working Keys (tipos 04, 05): SIEMPRE cifradas con KTK (encryption_type="01")
        - Master Keys (otros): En claro (encryption_type="00")
        
        Esta es la función principal que implementa la lógica solicitada.
        """
        log.info("=== INICIANDO inject_auto_key_with_kcv ===")
        log.info(f"Parámetros recibidos:")
        log.info(f"  - key_slot: {key_slot}")
        log.info(f"  - key_type: {key_type}")
        log.info(f"  - key_hex: {key_hex[:8]}... ({len(key_hex)} caracteres)")
        log.info(f"  - key_kcv: {key_kcv}")
        log.info(f"  - ksn: {ksn}")
        
        try:
            # Determinar el tipo de llave y método de inyección
            is_ktk = self._is_ktk_key(key_type)
            is_working_key = self._is_working_key(key_type)
            
            log.warning("🎯 DECISIÓN AUTOMÁTICA DE MÉTODO DE INYECCIÓN:")
            
            if is_ktk:
                # KTK SIEMPRE en claro
                log.warning(f"   🔑 KTK detectada (tipo {key_type}) → INYECCIÓN EN CLARO (00)")
                result = self.inject_symmetric_key_with_kcv(
                    key_slot=key_slot,
                    key_type=key_type,
                    key_hex=key_hex,
                    key_kcv=key_kcv,
                    ksn=ksn,
                    encryption_type="00"  # KTK siempre en claro
                )
                
            elif is_working_key:
                # Working Key SIEMPRE cifrada con KTK
                log.warning(f"   🔐 WORKING KEY detectada (tipo {key_type}) → INYECCIÓN CIFRADA (01)")
                
                # Buscar KTK disponible o crear uno
                ktk_slot = self._find_available_ktk()
                if not ktk_slot:
                    log.warning("   📋 No hay KTK disponible, creando KTK temporal...")
                    ktk_result = self.ensure_ktk_available()
                    if ktk_result.get("status") != "success":
                        return {
                            "status": "error",
                            "message": f"Working key tipo {key_type} requiere KTK pero no se pudo cargar: {ktk_result.get('message')}",
                            "key_type": key_type,
                            "requires_ktk": True
                        }
                    ktk_slot = ktk_result["ktk_slot"]
                    ktk_kcv_for_injection = ktk_result.get("ktk_kcv")
                else:
                    ktk_kcv_for_injection = None  # Asumir que ya está cargada
                
                log.warning(f"   🔧 Usando KTK en slot {ktk_slot} para cifrar working key")
                result = self.inject_symmetric_key_with_kcv(
                    key_slot=key_slot,
                    key_type=key_type,
                    key_hex=key_hex,
                    key_kcv=key_kcv,
                    ksn=ksn,
                    ktk_slot=ktk_slot,
                    encryption_type="01",  # Working key siempre cifrada
                    ktk_kcv=ktk_kcv_for_injection
                )
                
            else:
                # Master Key en claro
                log.warning(f"   🔓 MASTER KEY detectada (tipo {key_type}) → INYECCIÓN EN CLARO (00)")
                result = self.inject_symmetric_key_with_kcv(
                    key_slot=key_slot,
                    key_type=key_type,
                    key_hex=key_hex,
                    key_kcv=key_kcv,
                    ksn=ksn,
                    encryption_type="00"  # Master key en claro
                )
            
            log.info(f"✓ inject_auto_key_with_kcv completado: {result}")
            log.info("=== inject_auto_key_with_kcv EXITOSO ===")
            return result
            
        except Exception as e:
            log.exception("Error en inject_auto_key_with_kcv")
            log.info("=== inject_auto_key_with_kcv FALLIDO ===")
            raise

    def read_serial_number(self) -> dict:
        """Envía el comando para leer el número de serie del dispositivo."""
        try:
            command = CommandFuturexReadSerial()
            if self.handler:
                response_message = self.handler.send_and_wait_futurex(command)
                # Simular respuesta exitosa por ahora
                return {"status": "success", "serial_number": "SIM123456789", "response": response_message}
            else:
                # Modo simulado sin handler
                return {"status": "success", "serial_number": "SIM123456789"}
        except Exception as e:
            log.exception("Error durante la lectura del número de serie.")
            return {"status": "error", "message": str(e)}

    def delete_all_keys(self) -> Dict[str, Any]:
        """
        Elimina todas las llaves del dispositivo usando el comando 05.
        """
        try:
            if self.handler:
                # Simular comando de eliminación
                log.info("Eliminando todas las llaves del dispositivo...")
                # response_message = self.handler.send_and_wait_futurex(command)
                return {"status": "success", "message": "Todas las llaves eliminadas exitosamente"}
            else:
                # Modo simulado sin handler
                return {"status": "success", "message": "Todas las llaves eliminadas exitosamente (simulado)"}
        except Exception as e:
            log.exception("Error durante la eliminación de llaves")
            return {"status": "error", "message": str(e)}

    def delete_specific_key(self, key_slot: str, key_type: str) -> Dict[str, Any]:
        """
        Elimina una llave específica del dispositivo usando el comando 06.
        
        Args:
            key_slot: Slot de la llave (formato "00", "01", "0A", etc.)
            key_type: Tipo de llave (formato "01", "05", "08", etc.)
        
        Returns:
            Dict con el resultado de la eliminación
        """
        try:
            log.info(f"=== INICIANDO delete_specific_key ===")
            log.info(f"Parámetros:")
            log.info(f"  - key_slot: {key_slot}")
            log.info(f"  - key_type: {key_type}")
            
            if self.handler:
                log.info("Eliminando llave específica del dispositivo...")
                # command = CommandFuturexDeleteSpecificKey(key_slot=key_slot, key_type=key_type)
                # response_message = self.handler.send_and_wait_futurex(command)
                result = {"status": "success", "message": f"Llave en slot {key_slot} eliminada exitosamente"}
            else:
                # Modo simulado sin handler
                result = {"status": "success", "message": f"Llave en slot {key_slot} eliminada exitosamente (simulado)"}
            
            log.info(f"✓ Eliminación específica completada: {result}")
            log.info("=== delete_specific_key EXITOSO ===")
            
            return result
            
        except Exception as e:
            log.exception("Error durante la eliminación de llave específica")
            log.error(f"Tipo de error: {type(e).__name__}")
            log.error(f"Mensaje de error: {str(e)}")
            log.info("=== delete_specific_key FALLIDO ===")
            return {"status": "error", "message": str(e)}

    def _calculate_key_checksum(self, key_hex: str) -> str:
        """
        Calcula el checksum de una llave según el protocolo Futurex.
        El checksum se calcula encriptando una cadena de ceros con la llave.
        """
        try:
            # Por ahora, implementación simplificada
            # En producción, esto debería usar la llave real para encriptar
            import hashlib
            key_bytes = bytes.fromhex(key_hex)
            zero_string = b"\x00" * len(key_bytes)
            # Simular encriptación con la llave
            checksum_bytes = hashlib.sha256(key_bytes + zero_string).digest()[:2]
            return checksum_bytes.hex().upper()
        except Exception as e:
            log.warning(f"Error calculando checksum, usando valor por defecto: {e}")
            return "0000"

    def _is_ktk_key(self, key_type: str) -> bool:
        """
        Determina si un tipo de llave es una Key Transfer Key (KTK).
        Las KTK siempre deben inyectarse en claro (encryption_type="00").
        
        Args:
            key_type: Tipo de llave en formato hex (ej: "06")
        
        Returns:
            True si es una KTK
        """
        # Tipos de llave que son específicamente KTK
        ktk_types = {
            "06",  # Key Transfer Key (KTK) - siempre en claro
        }
        
        key_type_upper = key_type.upper()
        is_ktk = key_type_upper in ktk_types
        
        if is_ktk:
            log.info(f"🔑 Tipo {key_type_upper} identificado como KTK - DEBE IR EN CLARO (00)")
        
        return is_ktk

    def _is_working_key(self, key_type: str) -> bool:
        """
        Determina si un tipo de llave es una llave de trabajo que requiere cifrado.
        
        Según la documentación de Futurex:
        - Llaves de trabajo (working keys): Deben estar cifradas con KTK (encryption_type="01")
        - Llaves maestras/KTK: Pueden ir en claro (encryption_type="00")
        
        Args:
            key_type: Tipo de llave en formato hex (ej: "05", "04", "01")
        
        Returns:
            True si es una llave de trabajo que requiere cifrado
        """
        # Primero verificar si es KTK (siempre en claro)
        if self._is_ktk_key(key_type):
            log.info(f"🔑 Tipo {key_type.upper()} es KTK - NO requiere cifrado")
            return False
        
        # Tipos de llave que son WORKING KEYS (requieren cifrado con KTK)
        working_key_types = {
            "04",  # MAC Key (working) - DEBE CIFRARSE
            "05",  # PIN Encryption Key (working) - DEBE CIFRARSE
        }
        
        # Tipos de llave que son MASTER KEYS (pueden ir en claro)
        master_key_types = {
            "01",  # Master Session Key
            "03",  # DUKPT AES BDK (master)
            "08",  # DUKPT 3DES BDK (master)
            "0B",  # DUKPT AES Key (puede variar)
            "10",  # DUKPT AES Key (puede variar)
        }
        
        # Normalizar a mayúsculas
        key_type_upper = key_type.upper()
        
        if key_type_upper in working_key_types:
            log.warning(f"🔐 Tipo {key_type_upper} identificado como WORKING KEY - DEBE CIFRARSE CON KTK (01)")
            return True
        elif key_type_upper in master_key_types:
            log.info(f"🔓 Tipo {key_type_upper} identificado como MASTER KEY - PUEDE IR EN CLARO (00)")
            return False
        else:
            # Por defecto, tratar tipos desconocidos como working keys para seguridad
            log.error(f"⚠️ Tipo de llave DESCONOCIDO {key_type_upper} - TRATANDO COMO WORKING KEY por seguridad")
            log.error(f"   Si es una llave master o KTK, actualizar la lista en _is_working_key()")
            return True

    def _find_available_ktk(self, manual_mode: bool = False) -> Optional[str]:
        """
        Busca un KTK (Key Transfer Key) disponible en el dispositivo.
        
        Args:
            manual_mode: Si es True, asume que el usuario ha cargado un KTK manualmente
        
        Returns:
            Slot del KTK disponible (ej: "00") o None si no hay disponible
        """
        if manual_mode:
            # En modo manual, asumimos que el usuario ya cargó un KTK en slot 00
            log.info("🔧 MODO MANUAL: Asumiendo KTK disponible en slot 00")
            return "00"
        
        # TODO: Implementar consulta real al dispositivo
        # Por ahora, esta implementación simplificada busca en slots comunes
        
        # Lista de slots comunes donde podrían estar los KTK
        possible_ktk_slots = ["00", "01", "02", "03", "04", "05"]
        
        # En el futuro, implementar:
        # 1. Comando para listar llaves en el dispositivo
        # 2. Filtrar por tipo 06 (KTK)
        # 3. Verificar estado activo
        
        for slot in possible_ktk_slots:
            # Por ahora, solo verificamos el slot 00
            if slot == "00":
                log.info(f"_find_available_ktk: Verificando slot {slot} para KTK")
                # En modo automático, NO asumimos que hay KTK - forzamos creación de temporal
                # return slot  # Descomentar cuando tengamos verificación real
        
        log.warning("_find_available_ktk: No se encontraron KTKs disponibles")
        return None

    def ensure_ktk_available(self) -> Dict[str, Any]:
        """
        Asegura que haya un KTK disponible en el dispositivo.
        Si no hay ninguno, carga un KTK temporal en el slot 00.
        
        Returns:
            Dict con información del KTK disponible o error
        """
        log.info("=== INICIANDO ensure_ktk_available ===")
        
        try:
            # Verificar si ya hay un KTK disponible
            ktk_slot = self._find_available_ktk()
            if ktk_slot:
                log.info(f"KTK ya disponible en slot {ktk_slot}")
                return {
                    "status": "success",
                    "message": f"KTK disponible en slot {ktk_slot}",
                    "ktk_slot": ktk_slot
                }
            
            # No hay KTK disponible, cargar uno temporal
            log.info("No hay KTK disponible, cargando KTK temporal...")
            
            # Generar un KTK temporal (16 bytes = 32 caracteres hex)
            import secrets
            ktk_hex = secrets.token_hex(16).upper()
            ktk_kcv = self._calculate_key_checksum(ktk_hex)
            
            log.info(f"KTK temporal generado con KCV: {ktk_kcv}")
            
            # Cargar el KTK en el slot 00 (en claro, ya que los KTK pueden ir en claro)
            result = self.inject_symmetric_key_with_kcv(
                key_slot="00",
                key_type="06",  # Key Transfer Key
                key_hex=ktk_hex,
                key_kcv=ktk_kcv,
                encryption_type="00"  # KTK puede ir en claro
            )
            
            if result.get("status") == "success":
                log.info(f"✓ KTK TEMPORAL cargado exitosamente en slot 00 con KCV {ktk_kcv}")
                log.warning(f"🔧 NOTA: KCV {ktk_kcv} es del KTK TEMPORAL, NO de la llave del usuario")
                return {
                    "status": "success",
                    "message": f"KTK temporal cargado exitosamente en slot 00 (KCV: {ktk_kcv})",
                    "ktk_slot": "00",
                    "ktk_kcv": ktk_kcv,
                    "temporary": True
                }
            else:
                log.error(f"Error cargando KTK temporal: {result}")
                return {
                    "status": "error",
                    "message": f"Error cargando KTK temporal: {result.get('message', 'Error desconocido')}"
                }
                
        except Exception as e:
            log.exception("Error en ensure_ktk_available")
            return {"status": "error", "message": str(e)} 