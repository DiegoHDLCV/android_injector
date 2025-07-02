# api/util/serial_utils.py
import serial.tools.list_ports
import platform
import logging

log = logging.getLogger(__name__)

def find_serial_ports():
    """
    Encuentra y devuelve una lista de puertos seriales disponibles.
    Incluye dispositivos USB, TTY y seriales potencialmente relevantes para POS.
    """
    ports = []
    try:
        # Intentar encontrar puertos físicos
        available_ports = serial.tools.list_ports.comports()
        
        for port in available_ports:
            device = port.device
            description = port.description or "Sin descripción"
            vid_pid = port.hwid or ""
            
            # Filtrar puertos relevantes para POS/dispositivos seriales
            include_port = False
            
            if platform.system() == "Darwin":  # macOS
                # Incluir puertos cu.usbmodem, cu.usbserial, tty.usbserial, tty.usbmodem
                # Excluir bluetooth y debug-console por defecto
                if any(pattern in device for pattern in [
                    'usbmodem', 'usbserial', 'tty.wchusbserial', 'tty.SLAB_USBtoUART', 'tty.usbserial', 'cu.usbserial'
                ]):
                    include_port = True
                elif 'Bluetooth' not in description and 'Bluetooth' not in device and 'debug-console' not in device:
                    # Incluir otros puertos que no sean bluetooth o debug
                    include_port = True
            elif platform.system() == "Windows":
                # En Windows incluir COM ports
                if device.startswith('COM'):
                    include_port = True
            else:  # Linux
                # En Linux incluir ttyUSB, ttyACM, ttyS
                if any(pattern in device for pattern in ['ttyUSB', 'ttyACM', 'ttyS']):
                    include_port = True
            
            if include_port:
                ports.append(device)
                log.info(f"Puerto serial encontrado: {device} - {description} ({vid_pid})")
        
        # Si no hay puertos físicos relevantes, agregar algunos para testing
        if not ports:
            log.warning("No se encontraron puertos seriales relevantes para POS.")
            if platform.system() == "Windows":
                simulated_ports = ["COM1", "COM3", "COM4"]
            elif platform.system() == "Darwin":  # macOS
                simulated_ports = ["/dev/tty.usbserial-POS1", "/dev/cu.usbmodem-POS2", "/dev/tty.SLAB_USBtoUART"]
            else:  # Linux y otros
                simulated_ports = ["/dev/ttyUSB0", "/dev/ttyUSB1", "/dev/ttyACM0"]
            
            log.info(f"Agregando puertos simulados para desarrollo: {simulated_ports}")
            ports.extend(simulated_ports)
            
    except Exception as e:
        log.error(f"Error buscando puertos seriales: {e}")
        # Fallback con puertos simulados
        simulated_ports = ["/dev/ttyUSB0", "/dev/ttyUSB1", "COM1", "COM3"]
        log.info(f"Usando puertos de fallback: {simulated_ports}")
        ports = simulated_ports
    
    log.info(f"Total de puertos disponibles: {len(ports)} - {ports}")
    return ports 