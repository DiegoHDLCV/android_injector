'use client';

import { useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { usePOSConnection } from '@/hooks/usePOSConnection';
import { useRouter, usePathname } from 'next/navigation';

export default function Navbar() {
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const { user, logout, isAuthenticated } = useAuth();
    const { isConnected, currentPort } = usePOSConnection();
    const router = useRouter();
    const pathname = usePathname();

    // No mostrar navbar en login o si no está autenticado
    if (!isAuthenticated || pathname === '/login') {
        return null;
    }

    const navItems = [
        { name: 'Dashboard', href: '/dashboard', icon: '🏠' },
        { name: 'Conexión POS', href: '/connection', icon: '🔌' },
        { name: 'Perfiles', href: '/profiles', icon: '🏷️' },
        { name: 'Llaves', href: '/stored-keys', icon: '🔑' },
    ];

    // Agregar items de admin si el usuario es admin
    if (user?.role === 'admin') {
        navItems.push(
            { name: 'Ceremonia', href: '/ceremony', icon: '🛡️' },
            { name: 'Usuarios', href: '/users', icon: '👥' }
        );
    }

    const handleLogout = () => {
        logout();
        setIsMenuOpen(false);
    };

    return (
        <div className="sticky top-0 z-50">
            {/* Primera Navbar - Navegación Principal */}
            <nav className="bg-gray-800 border-b border-gray-700">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex items-center justify-between h-16">
                        {/* Logo y título */}
                        <div className="flex items-center">
                            <div 
                                className="flex items-center cursor-pointer" 
                                onClick={() => router.push('/dashboard')}
                            >
                                <svg className="h-8 w-8 text-blue-400 mr-3" fill="currentColor" viewBox="0 0 20 20">
                                    <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1117 9z" clipRule="evenodd" />
                                </svg>
                                <span className="text-xl font-bold text-white">Key Injector</span>
                            </div>
                        </div>

                        {/* Navegación Desktop */}
                        <div className="hidden md:flex items-center space-x-4">
                            {navItems.map((item) => (
                                <button
                                    key={item.name}
                                    onClick={() => router.push(item.href)}
                                    className={`px-3 py-2 rounded-md text-sm font-medium transition-colors flex items-center ${
                                        pathname === item.href
                                            ? 'bg-gray-700 text-white'
                                            : 'text-gray-300 hover:bg-gray-700 hover:text-white'
                                    }`}
                                >
                                    <span className="mr-2">{item.icon}</span>
                                    {item.name}
                                </button>
                            ))}
                        </div>

                        {/* Botón de Menú Mobile */}
                        <div className="md:hidden">
                            <button
                                onClick={() => setIsMenuOpen(!isMenuOpen)}
                                className="p-2 rounded-md text-gray-400 hover:text-white hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-white"
                            >
                                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                                </svg>
                            </button>
                        </div>
                    </div>

                    {/* Menú Mobile */}
                    {isMenuOpen && (
                        <div className="md:hidden">
                            <div className="px-2 pt-2 pb-3 space-y-1 sm:px-3 bg-gray-800 border-t border-gray-700">
                                {/* Enlaces de Navegación - Mobile */}
                                {navItems.map((item) => (
                                    <button
                                        key={item.name}
                                        onClick={() => {
                                            router.push(item.href);
                                            setIsMenuOpen(false);
                                        }}
                                        className={`w-full text-left flex items-center space-x-3 px-3 py-2 rounded-md text-base font-medium transition-colors ${
                                            pathname === item.href
                                                ? 'bg-gray-700 text-white'
                                                : 'text-gray-300 hover:bg-gray-700 hover:text-white'
                                        }`}
                                    >
                                        <span>{item.icon}</span>
                                        <span>{item.name}</span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            </nav>

            {/* Segunda Navbar - Información de Estado */}
            <div className="bg-gray-700 border-b border-gray-600">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex items-center justify-between h-12">
                        {/* Estado POS */}
                        <div className="flex items-center">
                            <div className={`w-2 h-2 rounded-full mr-2 ${isConnected ? 'bg-green-400' : 'bg-red-400'}`} />
                            <span className="text-sm text-gray-300">
                                POS: {isConnected ? currentPort : 'Desconectado'}
                            </span>
                        </div>

                        {/* Info del usuario y logout */}
                        <div className="flex items-center space-x-3">
                            <div className="text-sm">
                                <span className="text-white font-medium">{user?.username}</span>
                                <span className="text-gray-400 ml-2">({user?.role})</span>
                            </div>
                            <button 
                                onClick={handleLogout}
                                className="bg-red-600 hover:bg-red-700 text-white px-3 py-1 rounded text-sm transition-colors"
                            >
                                Salir
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
} 