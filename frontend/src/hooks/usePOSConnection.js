'use client';

import { createContext, useContext, useEffect, useState, useCallback, useMemo } from 'react';
import apiClient from '@/lib/api-client';
import { useNotification } from './useNotification';

const POSConnectionContext = createContext();

export function POSConnectionProvider({ children }) {
    const [isConnected, setIsConnected] = useState(false);
    const [currentPort, setCurrentPort] = useState(null);
    const [currentBaudrate, setCurrentBaudrate] = useState(null);
    const [availablePorts, setAvailablePorts] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [connectionLogs, setConnectionLogs] = useState([]);
    const { showNotification } = useNotification();
    
    console.log('[POS_CONNECTION_PROVIDER] 🔄 Provider renderizado - Connected:', isConnected, 'Loading:', isLoading);

    // Agregar mensaje al log
    const addLog = useCallback((message, type = 'info') => {
        const logEntry = {
            id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`, // ID único combinando timestamp y random
            message,
            type,
            timestamp: new Date().toLocaleTimeString()
        };
        setConnectionLogs(prev => [...prev.slice(-49), logEntry]); // Mantener últimos 50 logs
    }, []);

    // Limpiar logs
    const clearLogs = useCallback(() => {
        setConnectionLogs([]);
        addLog('Log limpiado');
    }, [addLog]);

    // Actualizar estado de conexión
    const updateConnectionStatus = useCallback(async () => {
        console.log('[POS_CONNECTION] 🔌 updateConnectionStatus() llamado');
        try {
            const serialApi = await apiClient.serial();
            const response = await serialApi.status();
            
            if (response.is_connected) {
                console.log('[POS_CONNECTION] ✅ Dispositivo conectado:', response.port);
                setIsConnected(true);
                setCurrentPort(response.port);
                setCurrentBaudrate(response.baudrate);
                addLog(`🟢 Conectado a ${response.port} (${response.baudrate} baud)`);
            } else {
                console.log('[POS_CONNECTION] ❌ Dispositivo desconectado');
                setIsConnected(false);
                setCurrentPort(null);
                setCurrentBaudrate(null);
                addLog('🔴 Desconectado');
            }
            
            return response;
        } catch (error) {
            console.log('[POS_CONNECTION] ❌ Error verificando estado:', error.message);
            addLog(`❌ Error verificando estado: ${error.message}`, 'error');
            return { error: true, message: error.message };
        }
    }, [addLog]);

    // Refrescar puertos disponibles
    const refreshPorts = useCallback(async () => {
        console.log('[POS_CONNECTION] 📡 refreshPorts() llamado');
        try {
            addLog('🔄 Refrescando puertos...');
            const serialApi = await apiClient.serial();
            const response = await serialApi.ports();
            
            if (response && response.ports) {
                setAvailablePorts(response.ports);
                addLog(`📡 Puertos encontrados: ${response.ports.join(', ')}`);
                console.log('[POS_CONNECTION] ✅ Puertos obtenidos:', response.ports.length);
                return response.ports;
            } else {
                setAvailablePorts([]);
                addLog('⚠️ No se encontraron puertos seriales', 'warning');
                console.log('[POS_CONNECTION] ⚠️ No se encontraron puertos');
                return [];
            }
        } catch (error) {
            console.log('[POS_CONNECTION] ❌ Error refrescando puertos:', error.message);
            addLog(`❌ Error refrescando puertos: ${error.message}`, 'error');
            setAvailablePorts([]);
            return [];
        }
    }, [addLog]);

    // Conectar a puerto específico
    const connect = useCallback(async (port) => {
        if (!port) {
            showNotification('Error', 'Por favor selecciona un puerto', 'error');
            return false;
        }

        setIsLoading(true);
        addLog(`🔌 Intentando conectar a ${port}...`);

        try {
            const serialApi = await apiClient.serial();
            const response = await serialApi.connect(port);
            
            if (response && response.success) {
                addLog(`✅ Conectado a ${port} con éxito`);
                showNotification('Conexión exitosa', `Conectado a ${port}`, 'success');
                await updateConnectionStatus();
                return true;
            } else {
                const errorMsg = response?.message || 'Error desconocido';
                addLog(`❌ Error al conectar: ${errorMsg}`, 'error');
                showNotification('Error de conexión', errorMsg, 'error');
                return false;
            }
        } catch (error) {
            addLog(`💥 Excepción al conectar: ${error.message}`, 'error');
            showNotification('Error de conexión', error.message, 'error');
            return false;
        } finally {
            setIsLoading(false);
        }
    }, [addLog, showNotification, updateConnectionStatus]);

    // Desconectar
    const disconnect = useCallback(async () => {
        setIsLoading(true);
        addLog('🔌 Desconectando...');

        try {
            const serialApi = await apiClient.serial();
            const response = await serialApi.disconnect();
            
            addLog('✅ Desconectado correctamente');
            showNotification('Desconectado', 'Dispositivo desconectado', 'info');
            await updateConnectionStatus();
            return true;
        } catch (error) {
            addLog(`❌ Error al desconectar: ${error.message}`, 'error');
            showNotification('Error', 'No se pudo desconectar correctamente', 'error');
            return false;
        } finally {
            setIsLoading(false);
        }
    }, [addLog, showNotification, updateConnectionStatus]);

    // Leer número de serie del dispositivo
    const readSerial = useCallback(async () => {
        if (!isConnected) {
            showNotification('Error', 'No hay dispositivo conectado', 'error');
            return null;
        }

        setIsLoading(true);
        addLog('📖 Enviando comando para leer número de serie...');

        try {
            const serialApi = await apiClient.serial();
            const response = await serialApi.readSerial();
            
            if (response && response.serial_number && response.status === 'success') {
                const message = `✅ Número de serie: ${response.serial_number}`;
                addLog(message);
                showNotification('Serial leído', `Número de serie: ${response.serial_number}`, 'success');
                return response.serial_number;
            } else {
                const errorMessage = response?.details || response?.message || 'Error desconocido';
                addLog(`❌ Error al leer el serial: ${errorMessage}`, 'error');
                showNotification('Error de lectura', errorMessage, 'error');
                return null;
            }
        } catch (error) {
            const errorMsg = "Error de conexión durante la lectura del serial";
            addLog(`💥 ${errorMsg}: ${error.message}`, 'error');
            showNotification('Error de conexión', error.message, 'error');
            return null;
        } finally {
            setIsLoading(false);
        }
    }, [isConnected, addLog, showNotification]);

    // Auto-refrescar estado cada 10 segundos (solo en página de conexión)
    useEffect(() => {
        // Solo activar el intervalo en la página de conexión
        const currentPath = window.location.pathname;
        if (currentPath !== '/connection') {
            console.log('[POS_CONNECTION] ⏸️ Intervalo deshabilitado - no estamos en /connection');
            return;
        }

        console.log('[POS_CONNECTION] ⏰ Configurando intervalo de verificación (10s)');
        const interval = setInterval(() => {
            console.log('[POS_CONNECTION] ⏰ Intervalo ejecutado - verificando estado');
            updateConnectionStatus();
        }, 10000);

        return () => {
            console.log('[POS_CONNECTION] 🛑 Limpiando intervalo');
            clearInterval(interval);
        };
    }, []); // Dependencias vacías para evitar recrear el intervalo

    // Inicialización
    useEffect(() => {
        console.log('[POS_CONNECTION] 🚀 useEffect de inicialización ejecutado');
        const initialize = async () => {
            console.log('[POS_CONNECTION] 🚀 Inicializando sistema POS...');
            addLog('🚀 Sistema de conexión POS inicializado');
            
            // Solo hacer llamadas iniciales en la página de conexión
            const currentPath = window.location.pathname;
            if (currentPath === '/connection') {
                console.log('[POS_CONNECTION] 📡 Ejecutando inicialización completa en página de conexión');
                await refreshPorts();
                await updateConnectionStatus();
            } else {
                console.log('[POS_CONNECTION] ⏸️ Inicialización ligera - no estamos en /connection');
                // Solo verificar estado sin refrescar puertos
                await updateConnectionStatus();
            }
            
            console.log('[POS_CONNECTION] ✅ Inicialización completada');
        };

        initialize();
    }, []); // Solo ejecutar una vez al montar el componente

    const value = useMemo(() => ({
        // Estado
        isConnected,
        currentPort,
        currentBaudrate,
        availablePorts,
        isLoading,
        connectionLogs,
        
        // Acciones
        connect,
        disconnect,
        readSerial,
        refreshPorts,
        updateConnectionStatus,
        clearLogs,
        
        // Helpers
        getConnectionInfo: () => ({
            isConnected,
            port: currentPort,
            baudrate: currentBaudrate
        })
    }), [isConnected, currentPort, currentBaudrate, availablePorts, isLoading, connectionLogs, connect, disconnect, readSerial, refreshPorts, updateConnectionStatus, clearLogs]);

    return (
        <POSConnectionContext.Provider value={value}>
            {children}
        </POSConnectionContext.Provider>
    );
}

export function usePOSConnection() {
    const context = useContext(POSConnectionContext);
    if (!context) {
        throw new Error('usePOSConnection debe usarse dentro de POSConnectionProvider');
    }
    return context;
} 