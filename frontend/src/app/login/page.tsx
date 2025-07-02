'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { useNotification } from '@/hooks/useNotification';
import apiClient from '@/lib/api-client';

export default function LoginPage() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isResetting, setIsResetting] = useState(false);
    
    console.log('[LOGIN_PAGE] 🔄 Componente renderizado');
    
    const { login, isAuthenticated, isLoading: authLoading } = useAuth();
    console.log('[LOGIN_PAGE] 🔐 Estado auth:', { isAuthenticated, authLoading });
    
    const { error: showError, success: showSuccess, warning: showWarning } = useNotification();
    const router = useRouter();

    // 🧪 TESTING: Redirigir a /profiles en lugar de /connection
    useEffect(() => {
        console.log('[LOGIN_PAGE] 🔄 useEffect - isAuthenticated cambió:', isAuthenticated, 'authLoading:', authLoading);
        if (isAuthenticated && !authLoading) {
            console.log('[LOGIN_PAGE] ✅ Usuario autenticado, redirigiendo a /profiles...');
            router.push('/profiles');
        }
    }, [isAuthenticated, authLoading, router]);

    // Mostrar loading durante verificación de auth
    if (authLoading) {
        return (
            <div className="min-h-screen bg-gray-900 flex items-center justify-center">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-green-400 mx-auto mb-4"></div>
                    <p className="text-gray-300">Verificando sesión...</p>
                </div>
            </div>
        );
    }

    // No mostrar nada si está autenticado (se redirige)
    if (isAuthenticated) {
        return null;
    }

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        console.log('[LOGIN_PAGE] 🚀 handleSubmit iniciado');
        console.log('[LOGIN_PAGE] 📝 Datos:', { username: username.trim(), passwordLength: password.length });
        
        if (!username.trim() || !password.trim()) {
            console.log('[LOGIN_PAGE] ❌ Campos vacíos');
            showError('Campos requeridos', 'Por favor ingresa usuario y contraseña');
            return;
        }

        console.log('[LOGIN_PAGE] ⏳ Iniciando proceso de login...');
        setIsLoading(true);

        try {
            console.log('[LOGIN_PAGE] 🌐 Llamando función login...');
            const result = await login({ username: username.trim(), password });
            console.log('[LOGIN_PAGE] 📊 Resultado del login:', result);
            
            if (!result.success) {
                console.log('[LOGIN_PAGE] ❌ Login fallido:', result.message);
                showError('Error de autenticación', result.message || 'Credenciales inválidas');
            } else {
                console.log('[LOGIN_PAGE] ✅ Login exitoso!');
                showSuccess('Login exitoso', 'Bienvenido al sistema');
            }
            // Si es exitoso, el hook useAuth maneja la redirección
        } catch (error) {
            console.log('[LOGIN_PAGE] 💥 Error en login:', error);
            showError('Error de conexión', 'No se pudo conectar con el servidor');
        } finally {
            console.log('[LOGIN_PAGE] 🏁 Finalizando proceso de login');
            setIsLoading(false);
        }
    };

    const handleResetAdminPassword = async () => {
        const newPassword = prompt('Ingresa la nueva contraseña para admin:');
        
        if (!newPassword) {
            return; // Usuario canceló
        }

        if (newPassword.length < 4) {
            showWarning('Contraseña muy corta', 'La contraseña debe tener al menos 4 caracteres');
            return;
        }

        setIsResetting(true);

        try {
            const result = await apiClient.post('/admin/reset-password', { 
                new_password: newPassword 
            });

            if (result && !result.error) {
                showSuccess('Contraseña cambiada', 'La contraseña de admin se ha actualizado exitosamente');
            } else {
                showError('Error al cambiar contraseña', result?.message || 'Error desconocido');
            }
        } catch (error) {
            showError('Error de conexión', 'No se pudo conectar con el servidor para cambiar la contraseña');
        } finally {
            setIsResetting(false);
        }
    };

    return (
        <div className="min-h-screen bg-gray-900 flex items-center justify-center">
            <div className="w-full max-w-md p-8 space-y-8 bg-gray-800 rounded-lg shadow-lg">
                {/* Header */}
                <div className="text-center">
                    <div className="inline-flex items-center justify-center w-16 h-16 bg-green-600 rounded-full mb-4">
                        <svg className="h-8 w-8 text-white" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1117 9z" clipRule="evenodd" />
                        </svg>
                    </div>
                    <h2 className="text-3xl font-extrabold text-white">
                        Iniciar Sesión
                    </h2>
                    <p className="mt-2 text-sm text-gray-400">
                        Key Injector - Sistema de Inyección de Llaves
                    </p>
                </div>

                {/* Login Form */}
                <form onSubmit={handleSubmit} className="mt-8 space-y-6">
                    <div className="rounded-md shadow-sm -space-y-px">
                        <div>
                            <label htmlFor="username" className="sr-only">Usuario</label>
                            <input
                                id="username"
                                name="username"
                                type="text"
                                required
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                className="relative block w-full px-3 py-2 border border-gray-600 placeholder-gray-400 text-white rounded-t-md bg-gray-700 focus:outline-none focus:ring-green-500 focus:border-green-500 focus:z-10 sm:text-sm"
                                placeholder="Usuario"
                                disabled={isLoading}
                            />
                        </div>
                        <div>
                            <label htmlFor="password" className="sr-only">Contraseña</label>
                            <input
                                id="password"
                                name="password"
                                type="password"
                                required
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                className="relative block w-full px-3 py-2 border border-gray-600 placeholder-gray-400 text-white rounded-b-md bg-gray-700 focus:outline-none focus:ring-green-500 focus:border-green-500 focus:z-10 sm:text-sm"
                                placeholder="Contraseña"
                                disabled={isLoading}
                            />
                        </div>
                    </div>

                    <div>
                        <button
                            type="submit"
                            disabled={isLoading}
                            className="group relative w-full flex justify-center py-2 px-4 border border-transparent text-sm font-medium rounded-md text-white bg-green-600 hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-green-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200"
                        >
                            {isLoading ? (
                                <>
                                    <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                    </svg>
                                    Iniciando sesión...
                                </>
                            ) : (
                                <>
                                    <svg className="h-5 w-5 mr-2 group-hover:translate-x-1 transition-transform duration-200" fill="currentColor" viewBox="0 0 20 20">
                                        <path fillRule="evenodd" d="M3 4.25A2.25 2.25 0 015.25 2h5.5A2.25 2.25 0 0113 4.25v2a.75.75 0 01-1.5 0v-2a.75.75 0 00-.75-.75h-5.5a.75.75 0 00-.75.75v11.5c0 .414.336.75.75.75h5.5a.75.75 0 00.75-.75v-2a.75.75 0 011.5 0v2A2.25 2.25 0 0110.75 18h-5.5A2.25 2.25 0 013 15.75V4.25z" clipRule="evenodd" />
                                        <path fillRule="evenodd" d="M6 10a.75.75 0 01.75-.75h9.546l-1.048-1.047a.75.75 0 111.06-1.06l2.5 2.5a.75.75 0 010 1.06l-2.5 2.5a.75.75 0 11-1.06-1.06L16.296 10.75H6.75A.75.75 0 016 10z" clipRule="evenodd" />
                                    </svg>
                                    Ingresar
                                </>
                            )}
                        </button>
                    </div>
                </form>

                {/* Reset Admin Password Section */}
                <div className="mt-8 pt-6 border-t border-gray-700">
                    <p className="text-gray-400 text-sm mb-4 text-center">
                        ¿Olvidaste la contraseña de administrador?
                    </p>
                    <button
                        onClick={handleResetAdminPassword}
                        disabled={isResetting || isLoading}
                        className="w-full inline-flex items-center justify-center bg-yellow-600 hover:bg-yellow-700 disabled:opacity-50 disabled:cursor-not-allowed text-white font-bold py-2 px-4 rounded-md transition duration-200"
                    >
                        {isResetting ? (
                            <>
                                <svg className="animate-spin -ml-1 mr-2 h-5 w-5" fill="none" viewBox="0 0 24 24">
                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                </svg>
                                Reiniciando...
                            </>
                        ) : (
                            <>
                                <svg className="h-5 w-5 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                    <path fillRule="evenodd" d="M10 1a4.5 4.5 0 00-4.5 4.5V9a2.5 2.5 0 00-2.5 2.5v4.25A2.25 2.25 0 005.25 18h9.5A2.25 2.25 0 0017 15.75V11.5a2.5 2.5 0 00-2.5-2.5V5.5A4.5 4.5 0 0010 1zm3.5 8.25a.75.75 0 00-1.5 0v-3.5a.75.75 0 00-.75-.75H9.25a.75.75 0 00-.75.75v3.5a.75.75 0 00-1.5 0V5.5a3 3 0 116 0v3.75z" clipRule="evenodd" />
                                </svg>
                                Reiniciar Contraseña de Admin
                            </>
                        )}
                    </button>
                </div>

                {/* Back to Home */}
                <div className="text-center">
                    <button
                        onClick={() => router.push('/')}
                        className="text-sm text-gray-400 hover:text-white transition-colors duration-200"
                    >
                        ← Volver al inicio
                    </button>
                </div>
            </div>
        </div>
    );
} 