'use client';

import { useState } from 'react';
import { usePOSConnection } from '@/hooks/usePOSConnection';

export default function ConnectionCard({ 
    compactMode = false, 
    showLogs = true,
    hideWhenConnected = false 
}) {
    const {
        isConnected,
        currentPort,
        currentBaudrate,
        availablePorts,
        isLoading,
        connectionLogs,
        connect,
        disconnect,
        readSerial,
        refreshPorts,
        clearLogs
    } = usePOSConnection();
    
    const [selectedPort, setSelectedPort] = useState('');
    const [serialNumber, setSerialNumber] = useState('');

    const handleConnect = async () => {
        if (await connect(selectedPort)) {
            setSelectedPort('');
        }
    };

    const handleReadSerial = async () => {
        const serial = await readSerial();
        if (serial) {
            setSerialNumber(serial);
        }
    };

    const cardClass = compactMode ? 'p-3 rounded-md' : 'p-6 rounded-lg';
    
    // Ocultar formulario si está conectado y la opción está activada
    const shouldHideForm = hideWhenConnected && isConnected;

    return (
        <div className={`bg-gray-800 text-white ${cardClass} shadow-lg border border-gray-700`}>
            {/* Header */}
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center">
                    <svg className="h-6 w-6 mr-2 text-blue-400" fill="currentColor" viewBox="0 0 20 20">
                        <path d="M12.232 4.232a2.5 2.5 0 0 1 3.536 3.536l-1.225 1.224a.75.75 0 0 0 1.061 1.06l1.224-1.224a4 4 0 0 0-5.656-5.656l-3 3a4 4 0 0 0 .225 5.865.75.75 0 0 0 .977-1.138 2.5 2.5 0 0 1-.142-3.665l3-3Z" />
                        <path d="M8.603 14.53a2.5 2.5 0 0 1-3.536-3.536l1.225-1.224a.75.75 0 0 0-1.061-1.06l-1.224 1.224a4 4 0 0 0 5.656 5.656l3-3a4 4 0 0 0-.225-5.865.75.75 0 0 0-.977 1.138 2.5 2.5 0 0 1 .142 3.665l-3 3Z" />
                    </svg>
                    <h3 className="text-lg font-semibold">Conexión POS</h3>
                </div>
                <ConnectionStatus isConnected={isConnected} port={currentPort} baudrate={currentBaudrate} />
            </div>

            {/* Connection Form */}
            {!shouldHideForm && (
                <div className="space-y-4 mb-4">
                    {/* Información de puertos disponibles */}
                    <div className="mb-4 p-3 bg-gray-900 border border-gray-600 rounded-md">
                        <div className="flex items-center justify-between mb-2">
                            <h4 className="text-sm font-medium text-gray-300">Puertos detectados:</h4>
                            <div className="flex items-center space-x-2">
                                <span className="text-xs text-green-400">{availablePorts.length} disponible(s)</span>
                                <button
                                    onClick={refreshPorts}
                                    disabled={isLoading}
                                    className="text-xs text-blue-400 hover:text-blue-300 disabled:opacity-50"
                                    title="Refrescar lista"
                                >
                                    🔄
                                </button>
                            </div>
                        </div>
                        {availablePorts.length > 0 ? (
                            <div className="space-y-2">
                                {availablePorts.map(port => {
                                    const isPOSCandidate = port.includes('usbmodem') || port.includes('usbserial') || 
                                                         port.includes('ttyUSB') || port.includes('ttyACM') || 
                                                         port.includes('SLAB_USBtoUART');
                                    return (
                                        <div 
                                            key={port}
                                            className={`flex items-center justify-between p-2 rounded text-xs ${
                                                isPOSCandidate 
                                                    ? 'bg-green-600 text-green-100 border border-green-500' 
                                                    : 'bg-blue-600 text-blue-100'
                                            }`}
                                        >
                                            <div className="flex items-center">
                                                <svg className="h-3 w-3 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                                    <path d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" />
                                                </svg>
                                                <span className="font-mono">{port}</span>
                                            </div>
                                            {isPOSCandidate && (
                                                <span className="px-1 py-0.5 bg-green-700 rounded text-xs">
                                                    📟 POS
                                                </span>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <div className="text-center py-4">
                                <div className="text-gray-400 text-sm mb-2">
                                    {isLoading ? 'Buscando dispositivos...' : 'No hay puertos disponibles'}
                                </div>
                                <button
                                    onClick={refreshPorts}
                                    disabled={isLoading}
                                    className="text-xs text-blue-400 hover:text-blue-300 disabled:opacity-50"
                                >
                                    🔄 Buscar dispositivos
                                </button>
                            </div>
                        )}
                    </div>

                    <div className="flex items-end space-x-3">
                        <div className="flex-1">
                            <label className="block text-sm font-medium mb-1">Puerto Serie</label>
                            <select 
                                value={selectedPort}
                                onChange={(e) => setSelectedPort(e.target.value)}
                                className="w-full bg-gray-900 border border-gray-600 rounded-md p-2 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                disabled={isConnected || isLoading}
                            >
                                <option value="">
                                    {availablePorts.length === 0 ? 'No hay puertos disponibles' : 'Seleccionar puerto...'}
                                </option>
                                {availablePorts.map(port => (
                                    <option key={port} value={port}>{port}</option>
                                ))}
                            </select>
                        </div>
                        <button 
                            onClick={refreshPorts}
                            disabled={isLoading}
                            className="p-2 bg-gray-600 hover:bg-gray-700 disabled:opacity-50 rounded-md transition-colors flex items-center justify-center" 
                            title="Refrescar puertos"
                        >
                            <svg className={`h-5 w-5 ${isLoading ? 'animate-spin' : ''}`} fill="currentColor" viewBox="0 0 20 20">
                                <path fillRule="evenodd" d="M15.312 11.424a5.5 5.5 0 01-9.201 2.466l-.312-.311h2.433a.75.75 0 000-1.5H3.989a.75.75 0 00-.75.75v4.242a.75.75 0 001.5 0v-2.43l.31.31a7 7 0 0011.712-3.138.75.75 0 00-1.449-.39zm1.23-3.723a.75.75 0 00.219-.53V2.929a.75.75 0 00-1.5 0V5.36l-.31-.31A7 7 0 003.239 8.188a.75.75 0 101.448.389A5.5 5.5 0 0113.89 6.11l.311.31h-2.432a.75.75 0 000 1.5h4.243a.75.75 0 00.53-.219z" clipRule="evenodd" />
                            </svg>
                        </button>
                    </div>

                    <div className="flex space-x-2">
                        <button 
                            onClick={handleConnect}
                            disabled={!selectedPort || isConnected || isLoading}
                            className="flex-1 inline-flex items-center justify-center bg-green-600 hover:bg-green-700 disabled:bg-gray-500 disabled:cursor-not-allowed text-white font-medium py-2 px-4 rounded-md text-sm transition-colors"
                        >
                            {isLoading && !isConnected ? (
                                <>
                                    <svg className="animate-spin -ml-1 mr-2 h-4 w-4" fill="none" viewBox="0 0 24 24">
                                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                    </svg>
                                    Conectando...
                                </>
                            ) : (
                                <>
                                    <svg className="h-4 w-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                        <path d="M12.232 4.232a2.5 2.5 0 0 1 3.536 3.536l-1.225 1.224a.75.75 0 0 0 1.061 1.06l1.224-1.224a4 4 0 0 0-5.656-5.656l-3 3a4 4 0 0 0 .225 5.865.75.75 0 0 0 .977-1.138 2.5 2.5 0 0 1-.142-3.665l3-3Z" />
                                        <path d="M8.603 14.53a2.5 2.5 0 0 1-3.536-3.536l1.225-1.224a.75.75 0 0 0-1.061-1.06l-1.224 1.224a4 4 0 0 0 5.656 5.656l3-3a4 4 0 0 0-.225-5.865.75.75 0 0 0-.977 1.138 2.5 2.5 0 0 1 .142 3.665l-3 3Z" />
                                    </svg>
                                    Conectar
                                </>
                            )}
                        </button>
                        <button 
                            onClick={disconnect}
                            disabled={!isConnected || isLoading}
                            className="flex-1 inline-flex items-center justify-center bg-red-600 hover:bg-red-700 disabled:bg-gray-500 disabled:cursor-not-allowed text-white font-medium py-2 px-4 rounded-md text-sm transition-colors"
                        >
                            {isLoading && isConnected ? (
                                <>
                                    <svg className="animate-spin -ml-1 mr-2 h-4 w-4" fill="none" viewBox="0 0 24 24">
                                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                    </svg>
                                    Desconectando...
                                </>
                            ) : (
                                <>
                                    <svg className="h-4 w-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM8.28 7.22a.75.75 0 0 0-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 1 0 1.06 1.06L10 11.06l1.72 1.72a.75.75 0 1 0 1.06-1.06L11.06 10l1.72-1.72a.75.75 0 0 0-1.06-1.06L10 8.94 8.28 7.22Z" clipRule="evenodd" />
                                    </svg>
                                    Desconectar
                                </>
                            )}
                        </button>
                    </div>
                </div>
            )}

            {/* Connected Status Info */}
            {isConnected && (
                <div className="mb-4 p-3 bg-green-900 bg-opacity-30 border border-green-600 rounded-md">
                    <div className="flex items-center">
                        <svg className="h-5 w-5 mr-2 text-green-400" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm3.857-9.809a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.06 0l4.25-5.85Z" clipRule="evenodd" />
                        </svg>
                        <div>
                            <p className="text-sm font-medium text-green-100">Dispositivo conectado</p>
                            <p className="text-xs text-green-200">Puerto: {currentPort} ({currentBaudrate} baud)</p>
                        </div>
                    </div>
                </div>
            )}

            {/* Serial Number Section */}
            {isConnected && (
                <div className="space-y-3 mb-4">
                    <button 
                        onClick={handleReadSerial}
                        disabled={isLoading}
                        className="w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white font-medium py-2 px-4 rounded-md text-sm transition-colors flex items-center justify-center"
                    >
                        {isLoading ? (
                            <>
                                <svg className="animate-spin -ml-1 mr-2 h-4 w-4" fill="none" viewBox="0 0 24 24">
                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                </svg>
                                Leyendo...
                            </>
                        ) : (
                            <>
                                <svg className="h-4 w-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                    <path d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM3 10a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H4a1 1 0 01-1-1v-6zM14 9a1 1 0 00-1 1v6a1 1 0 001 1h2a1 1 0 001-1v-6a1 1 0 00-1-1h-2z" />
                                </svg>
                                Leer Número de Serie
                            </>
                        )}
                    </button>
                    
                    {serialNumber && (
                        <div className="p-3 bg-gray-900 border border-gray-600 rounded-md">
                            <label className="block text-xs font-medium text-gray-400 mb-1">Número de Serie:</label>
                            <div className="font-mono text-sm text-green-400">{serialNumber}</div>
                        </div>
                    )}
                </div>
            )}

            {/* Connection Logs */}
            {showLogs && (
                <div className="mt-4">
                    <div className="flex items-center justify-between mb-2">
                        <h4 className="text-sm font-medium">Log de conexión</h4>
                        <button 
                            onClick={clearLogs}
                            className="text-xs text-gray-400 hover:text-white transition-colors"
                        >
                            Limpiar
                        </button>
                    </div>
                    <div className="bg-gray-900 border border-gray-600 rounded p-3 h-32 overflow-y-auto text-xs font-mono">
                        {connectionLogs.length === 0 ? (
                            <div className="text-gray-500">Listo para conectar...</div>
                        ) : (
                            connectionLogs.map(log => (
                                <LogEntry key={log.id} log={log} />
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

function ConnectionStatus({ isConnected, port, baudrate }) {
    return (
        <div className="flex items-center">
            <div className={`w-3 h-3 rounded-full mr-2 ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
            <span className="text-sm font-medium">
                {isConnected ? `Conectado${port ? ` a ${port}` : ''}` : 'Desconectado'}
            </span>
        </div>
    );
}

function LogEntry({ log }) {
    const typeColors = {
        info: 'text-gray-300',
        warning: 'text-yellow-400',
        error: 'text-red-400',
        success: 'text-green-400'
    };

    return (
        <div className={`${typeColors[log.type] || 'text-gray-300'} mb-1`}>
            [{log.timestamp}] {log.message}
        </div>
    );
} 