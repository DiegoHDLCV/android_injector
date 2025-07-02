'use client';

import { useEffect } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { usePOSConnection } from '@/hooks/usePOSConnection';
import { useNotification } from '@/hooks/useNotification';
import ConnectionCard from '@/components/pos/ConnectionCard';

export default function ConnectionPage() {
    const { user, isAuthenticated, isLoading: authLoading } = useAuth();
    const { isConnected, currentPort, getConnectionInfo } = usePOSConnection();
    const { showNotification } = useNotification();

    // Mostrar notificación de bienvenida una vez
    useEffect(() => {
        // if (isAuthenticated && user && !authLoading) {
        //     showNotification(
        //         '¡Bienvenido!', 
        //         `Hola ${user.username}, sistema de conexión POS listo`, 
        //         'info',
        //         3000
        //     );
        // }
    }, [isAuthenticated, user, authLoading, showNotification]);

    // Mostrar loading mientras se autentica
    if (authLoading) {
        return (
            <div className="min-h-screen bg-gray-900 flex items-center justify-center">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-400 mx-auto mb-4"></div>
                    <p className="text-gray-300">Verificando autenticación...</p>
                </div>
            </div>
        );
    }

    // Redirigir si no está autenticado (el hook se encarga de esto)
    if (!isAuthenticated) {
        return null;
    }

    return (
        <div className="min-h-screen bg-gray-900">
            {/* Header */}
            <header className="bg-gray-800 border-b border-gray-700">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex items-center justify-between h-16">
                        <div className="flex items-center">
                            <h1 className="text-xl font-bold text-white">
                                Key Injector - Conexión POS
                            </h1>
                        </div>
                        <div className="flex items-center space-x-4">
                            <ConnectionStatusBadge />
                            <UserInfo user={user} />
                        </div>
                    </div>
                </div>
            </header>

            {/* Main Content */}
            <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Conexión Principal */}
                    <div className="lg:col-span-2">
                        <ConnectionCard showLogs={true} />
                    </div>

                    {/* Panel Lateral */}
                    <div className="space-y-6">
                        {/* Info del Usuario */}
                        <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
                            <h3 className="text-lg font-semibold mb-4 text-white">Información de Sesión</h3>
                            <div className="space-y-3">
                                <div className="flex justify-between">
                                    <span className="text-gray-400">Usuario:</span>
                                    <span className="text-white font-medium">{user?.username}</span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-gray-400">Rol:</span>
                                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                                        user?.role === 'admin' 
                                            ? 'bg-red-100 text-red-800' 
                                            : 'bg-blue-100 text-blue-800'
                                    }`}>
                                        {user?.role}
                                    </span>
                                </div>
                                <div className="flex justify-between">
                                    <span className="text-gray-400">Estado POS:</span>
                                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                                        isConnected 
                                            ? 'bg-green-100 text-green-800' 
                                            : 'bg-gray-100 text-gray-800'
                                    }`}>
                                        {isConnected ? `Conectado (${currentPort})` : 'Desconectado'}
                                    </span>
                                </div>
                            </div>
                        </div>

                        {/* Acciones Rápidas */}
                        <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
                            <h3 className="text-lg font-semibold mb-4 text-white">Acciones Rápidas</h3>
                            <div className="space-y-3">
                                <button 
                                    onClick={() => window.location.href = '/profiles'}
                                    className="w-full bg-purple-600 hover:bg-purple-700 text-white py-2 px-4 rounded-md text-sm transition-colors flex items-center"
                                >
                                    <svg className="h-4 w-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                        <path d="M4 4a2 2 0 00-2 2v8a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2H4zm0 2h12v8H4V6z"/>
                                        <path d="M6 8h2v2H6V8zm4 0h4v1h-4V8zm0 3h4v1h-4v-1z"/>
                                    </svg>
                                    Gestionar Perfiles
                                </button>
                                <button 
                                    onClick={() => window.location.href = '/stored_keys'}
                                    className="w-full bg-green-600 hover:bg-green-700 text-white py-2 px-4 rounded-md text-sm transition-colors flex items-center"
                                >
                                    <svg className="h-4 w-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1117 9z" clipRule="evenodd" />
                                    </svg>
                                    Ver Llaves Almacenadas
                                </button>
                                <button 
                                    onClick={() => window.location.href = '/injection_logs'}
                                    className="w-full bg-yellow-600 hover:bg-yellow-700 text-white py-2 px-4 rounded-md text-sm transition-colors flex items-center"
                                >
                                    <svg className="h-4 w-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.06 0l4.25-5.85z" clipRule="evenodd" />
                                    </svg>
                                    Ver Logs de Inyección
                                </button>
                                {user?.role === 'admin' && (
                                    <>
                                        <button 
                                            onClick={() => window.location.href = '/ceremony'}
                                            className="w-full bg-orange-600 hover:bg-orange-700 text-white py-2 px-4 rounded-md text-sm transition-colors flex items-center"
                                        >
                                            <svg className="h-4 w-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                                <path fillRule="evenodd" d="M11.533 2.06a1.5 1.5 0 00-3.066 0l-1.833 6.874h6.732l-1.833-6.874zm-2.6-1.55a3 3 0 015.4 1.08l2.25 8.438a.75.75 0 01-.68.982H3.698a.75.75 0 01-.68-.982l2.25-8.437a3 3 0 015.4-1.08z" clipRule="evenodd" />
                                            </svg>
                                            Ceremonia Criptográfica
                                        </button>
                                        <button 
                                            onClick={() => window.location.href = '/user_management'}
                                            className="w-full bg-red-600 hover:bg-red-700 text-white py-2 px-4 rounded-md text-sm transition-colors flex items-center"
                                        >
                                            <svg className="h-4 w-4 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                                <path d="M10 8a3 3 0 100-6 3 3 0 000 6zm-3.465 6.493a1.23 1.23 0 00.41 1.412A9.957 9.957 0 0010 18c2.31 0 4.438-.784 6.131-2.095a1.23 1.23 0 00.41-1.412A9.957 9.957 0 0010 12c-2.31 0-4.438.784-6.131 2.095z" />
                                            </svg>
                                            Gestión de Usuarios
                                        </button>
                                    </>
                                )}
                            </div>
                        </div>

                        {/* Información del Sistema */}
                        <SystemInfo />
                    </div>
                </div>
            </main>
        </div>
    );
}

function ConnectionStatusBadge() {
    const { isConnected, currentPort } = usePOSConnection();
    
    return (
        <div className="flex items-center">
            <div className={`w-2 h-2 rounded-full mr-2 ${isConnected ? 'bg-green-400' : 'bg-red-400'}`} />
            <span className="text-sm text-gray-300">
                POS: {isConnected ? `${currentPort}` : 'Desconectado'}
            </span>
        </div>
    );
}

function UserInfo({ user }: { user: any }) {
    const { logout } = useAuth();
    
    return (
        <div className="flex items-center space-x-3">
            <div className="text-sm">
                <div className="text-white font-medium">{user?.username}</div>
                <div className="text-gray-400 text-xs">{user?.role}</div>
            </div>
            <button 
                onClick={logout}
                className="bg-red-600 hover:bg-red-700 text-white px-3 py-1 rounded text-sm transition-colors"
            >
                Salir
            </button>
        </div>
    );
}

function SystemInfo() {
    const connectionInfo = usePOSConnection().getConnectionInfo();
    
    return (
        <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
            <h3 className="text-lg font-semibold mb-4 text-white">Estado del Sistema</h3>
            <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                    <span className="text-gray-400">Estado:</span>
                    <span className="text-green-400">✅ Operativo</span>
                </div>
                <div className="flex justify-between">
                    <span className="text-gray-400">Frontend:</span>
                    <span className="text-blue-400">Next.js 15.3.4</span>
                </div>
                <div className="flex justify-between">
                    <span className="text-gray-400">Sistema:</span>
                    <span className="text-purple-400">Modular React</span>
                </div>
                <div className="flex justify-between">
                    <span className="text-gray-400">Conexión:</span>
                    <span className={connectionInfo.isConnected ? 'text-green-400' : 'text-gray-400'}>
                        {connectionInfo.isConnected ? '🟢 Activa' : '🔴 Inactiva'}
                    </span>
                </div>
            </div>
        </div>
    );
} 