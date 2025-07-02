'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { usePOSConnection } from '@/hooks/usePOSConnection';
import { useNotification } from '@/hooks/useNotification';
import ConnectionCard from '@/components/pos/ConnectionCard';

export default function Dashboard() {
    const { user, isAuthenticated, isLoading: authLoading } = useAuth();
    const { isConnected, currentPort, connectionLogs } = usePOSConnection();
    const { showNotification } = useNotification();
    const [systemStats, setSystemStats] = useState({
        profilesCount: 0,
        keysCount: 0,
        injectionsToday: 0,
        usersCount: 0
    });

    // Cargar estadísticas del sistema
    useEffect(() => {
        const loadStats = async () => {
            try {
                // Simular carga de estadísticas (se puede conectar con APIs reales)
                setSystemStats({
                    profilesCount: 8,
                    keysCount: 42,
                    injectionsToday: 15,
                    usersCount: 3
                });
            } catch (error) {
                console.error('Error cargando estadísticas:', error);
            }
        };

        if (isAuthenticated) {
            loadStats();
        }
    }, [isAuthenticated]);

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

    // Redirigir si no está autenticado
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
                                Dashboard - Key Injector System
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
                <div className="space-y-6">
                    {/* Bienvenida */}
                    <div className="bg-gradient-to-r from-blue-600 to-purple-600 rounded-lg p-6 text-white">
                        <h2 className="text-2xl font-bold mb-2">¡Bienvenido, {user?.username}!</h2>
                        <p className="text-blue-100">
                            Sistema de inyección de llaves criptográficas para POS - Panel de control principal
                        </p>
                    </div>

                    {/* Estadísticas rápidas */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                        <StatCard 
                            title="Perfiles Activos" 
                            value={systemStats.profilesCount}
                            icon="🏷️"
                            color="bg-green-600"
                        />
                        <StatCard 
                            title="Llaves Almacenadas" 
                            value={systemStats.keysCount}
                            icon="🔑"
                            color="bg-blue-600"
                        />
                        <StatCard 
                            title="Inyecciones Hoy" 
                            value={systemStats.injectionsToday}
                            icon="📊"
                            color="bg-yellow-600"
                        />
                        <StatCard 
                            title="Usuarios" 
                            value={systemStats.usersCount}
                            icon="👥"
                            color="bg-purple-600"
                        />
                    </div>

                    {/* Grid principal */}
                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                        {/* Columna izquierda - Conexión POS */}
                        <div className="lg:col-span-2">
                            <ConnectionCard showLogs={true} />
                        </div>

                        {/* Columna derecha - Panel de acciones */}
                        <div className="space-y-6">
                            {/* Estado del Sistema */}
                            <SystemStatusCard />

                            {/* Acciones Rápidas */}
                            <QuickActionsCard user={user} />
                        </div>
                    </div>

                    {/* Actividad reciente */}
                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                        <RecentActivityCard connectionLogs={connectionLogs} />
                        <SystemHealthCard isConnected={isConnected} currentPort={currentPort} />
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

function UserInfo({ user }) {
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

function StatCard({ title, value, icon, color }) {
    return (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
            <div className="flex items-center">
                <div className={`${color} rounded-lg p-3 mr-4`}>
                    <span className="text-2xl">{icon}</span>
                </div>
                <div>
                    <p className="text-gray-400 text-sm">{title}</p>
                    <p className="text-white text-2xl font-bold">{value}</p>
                </div>
            </div>
        </div>
    );
}

function SystemStatusCard() {
    return (
        <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
            <h3 className="text-lg font-semibold mb-4 text-white">Estado del Sistema</h3>
            <div className="space-y-3">
                <StatusItem label="Frontend" status="✅ Operativo" color="text-green-400" />
                <StatusItem label="Backend" status="✅ Conectado" color="text-green-400" />
                <StatusItem label="Base de Datos" status="✅ Sincronizada" color="text-green-400" />
                <StatusItem label="Seguridad" status="🔒 Activa" color="text-blue-400" />
            </div>
        </div>
    );
}

function StatusItem({ label, status, color }) {
    return (
        <div className="flex justify-between items-center">
            <span className="text-gray-400 text-sm">{label}:</span>
            <span className={`text-sm ${color}`}>{status}</span>
        </div>
    );
}

function QuickActionsCard({ user }) {
    const actions = [
        {
            title: 'Gestionar Perfiles',
            description: 'Crear y configurar perfiles de inyección',
            href: '/profiles',
            icon: '🏷️',
            color: 'bg-purple-600 hover:bg-purple-700'
        },
        {
            title: 'Ver Llaves',
            description: 'Revisar llaves almacenadas en el sistema',
            href: '/stored-keys',
            icon: '🔑',
            color: 'bg-green-600 hover:bg-green-700'
        },
        {
            title: 'Conexión POS',
            description: 'Configurar conexión con dispositivos POS',
            href: '/connection',
            icon: '🔌',
            color: 'bg-blue-600 hover:bg-blue-700'
        }
    ];

    if (user?.role === 'admin') {
        actions.push(
            {
                title: 'Ceremonia Crypto',
                description: 'Gestión avanzada de llaves maestras',
                href: '/ceremony',
                icon: '🛡️',
                color: 'bg-orange-600 hover:bg-orange-700'
            },
            {
                title: 'Usuarios',
                description: 'Administrar usuarios del sistema',
                href: '/users',
                icon: '👥',
                color: 'bg-red-600 hover:bg-red-700'
            }
        );
    }

    return (
        <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
            <h3 className="text-lg font-semibold mb-4 text-white">Acciones Rápidas</h3>
            <div className="space-y-3">
                {actions.map((action, index) => (
                    <button
                        key={index}
                        onClick={() => window.location.href = action.href}
                        className={`w-full text-left p-3 rounded-lg transition-colors ${action.color}`}
                    >
                        <div className="flex items-center">
                            <span className="text-xl mr-3">{action.icon}</span>
                            <div>
                                <div className="text-white font-medium text-sm">{action.title}</div>
                                <div className="text-gray-200 text-xs">{action.description}</div>
                            </div>
                        </div>
                    </button>
                ))}
            </div>
        </div>
    );
}

function RecentActivityCard({ connectionLogs }) {
    const recentLogs = connectionLogs.slice(-5).reverse();

    return (
        <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
            <h3 className="text-lg font-semibold mb-4 text-white">Actividad Reciente</h3>
            <div className="space-y-3">
                {recentLogs.length === 0 ? (
                    <p className="text-gray-400 text-sm">No hay actividad reciente</p>
                ) : (
                    recentLogs.map((log, index) => (
                        <div key={log.id} className="flex items-start space-x-3">
                            <div className="flex-shrink-0 w-2 h-2 rounded-full bg-blue-400 mt-2"></div>
                            <div className="min-w-0 flex-1">
                                <p className="text-sm text-gray-300 truncate">{log.message}</p>
                                <p className="text-xs text-gray-500">{log.timestamp}</p>
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
}

function SystemHealthCard({ isConnected, currentPort }) {
    const healthItems = [
        {
            name: 'Conexión POS',
            status: isConnected ? 'Conectado' : 'Desconectado',
            details: isConnected ? `Puerto: ${currentPort}` : 'Sin dispositivo',
            healthy: isConnected
        },
        {
            name: 'Autenticación',
            status: 'Activa',
            details: 'Sesión válida',
            healthy: true
        },
        {
            name: 'Comunicación API',
            status: 'Operativa',
            details: 'Backend disponible',
            healthy: true
        }
    ];

    return (
        <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
            <h3 className="text-lg font-semibold mb-4 text-white">Salud del Sistema</h3>
            <div className="space-y-4">
                {healthItems.map((item, index) => (
                    <div key={index} className="flex items-center justify-between">
                        <div className="flex items-center space-x-3">
                            <div className={`w-3 h-3 rounded-full ${item.healthy ? 'bg-green-400' : 'bg-red-400'}`}></div>
                            <div>
                                <p className="text-sm font-medium text-white">{item.name}</p>
                                <p className="text-xs text-gray-400">{item.details}</p>
                            </div>
                        </div>
                        <span className={`text-sm ${item.healthy ? 'text-green-400' : 'text-red-400'}`}>
                            {item.status}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
} 