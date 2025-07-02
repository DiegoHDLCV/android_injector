'use client';

import { createContext, useContext, useEffect, useState, useCallback, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import apiClient from '@/lib/api-client';
import { useNotification } from './useNotification';

const AuthContext = createContext();

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const router = useRouter();
    const { showNotification } = useNotification();
    
    console.log('[AUTH_PROVIDER] 🔄 Provider renderizado - Auth:', isAuthenticated, 'Loading:', isLoading, 'User:', user?.username);

    const checkAuthStatus = useCallback(async () => {
        console.log('[AUTH] 🔐 checkAuthStatus() INICIADO');
        try {
            console.log('[AUTH] 🌐 Obteniendo authApi para status...');
            const authApi = await apiClient.auth();
            console.log('[AUTH] 📡 Enviando petición getStatus...');
            const response = await authApi.getStatus();
            console.log('[AUTH] 📊 Respuesta getStatus:', response);
            
            if (response.is_authenticated && response.user) {
                console.log('[AUTH] ✅ Usuario autenticado detectado:', response.user.username);
                setUser(response.user);
                setIsAuthenticated(true);
            } else {
                console.log('[AUTH] ❌ Usuario no autenticado detectado');
                setUser(null);
                setIsAuthenticated(false);
            }
        } catch (error) {
            console.log('[AUTH] 💥 Error verificando autenticación:', error.message);
            console.log('[AUTH] 💥 Error completo:', error);
            setUser(null);
            setIsAuthenticated(false);
        } finally {
            console.log('[AUTH] 🏁 checkAuthStatus FINALIZADO - estableciendo loading=false');
            setIsLoading(false);
        }
    }, []);

    const login = useCallback(async (credentials) => {
        console.log('[AUTH] 🚀 login() iniciado con:', { username: credentials.username });
        try {
            console.log('[AUTH] 🌐 Obteniendo authApi...');
            const authApi = await apiClient.auth();
            console.log('[AUTH] 📡 Enviando petición de login...');
            const response = await authApi.login(credentials);
            console.log('[AUTH] 📊 Respuesta del servidor:', response);
            
            if (response.success && response.user) {
                console.log('[AUTH] ✅ Login exitoso, estableciendo estado...');
                setUser(response.user);
                setIsAuthenticated(true);
                // if (showNotification) {
                //     showNotification('¡Bienvenido!', `Hola ${response.user.username}`, 'success');
                // }
                console.log('[AUTH] 🔀 Redirigiendo a /profiles...');
                // 🧪 TESTING: Cambiar a /profiles en lugar de /connection
                router.push('/profiles');
                return { success: true };
            } else {
                console.log('[AUTH] ❌ Login fallido:', response.message);
                if (showNotification) {
                    showNotification('Error de login', response.message || 'Credenciales inválidas', 'error');
                }
                return { success: false, message: response.message };
            }
        } catch (error) {
            console.log('[AUTH] 💥 Error en login:', error);
            const errorMsg = 'Error de conexión durante el login';
            if (showNotification) {
                showNotification('Error', errorMsg, 'error');
            }
            return { success: false, message: errorMsg };
        }
    }, [showNotification, router]);

    const logout = useCallback(async () => {
        try {
            const authApi = await apiClient.auth();
            await authApi.logout();
            setUser(null);
            setIsAuthenticated(false);
            if (showNotification) {
                showNotification('Sesión cerrada', 'Has cerrado sesión exitosamente', 'info');
            }
            router.push('/login');
        } catch (error) {
            console.error('Error en logout:', error);
            // Aun si hay error, limpiar estado local
            setUser(null);
            setIsAuthenticated(false);
            router.push('/login');
        }
    }, [showNotification, router]);

    const isAdmin = useCallback(() => {
        return user?.role === 'admin';
    }, [user?.role]);

    const requireAuth = useCallback(() => {
        if (!isAuthenticated && !isLoading) {
            router.push('/login');
            return false;
        }
        return true;
    }, [isAuthenticated, isLoading, router]);

    const requireAdmin = useCallback(() => {
        if (!isAdmin()) {
            if (showNotification) {
                showNotification('Acceso denegado', 'Necesitas permisos de administrador', 'error');
            }
            router.push('/connection');
            return false;
        }
        return true;
    }, [isAdmin, showNotification, router]);

    useEffect(() => {
        console.log('[AUTH] 🚀 useEffect inicial - verificando autenticación');
        checkAuthStatus();
    }, []);

    // Auto-redirigir a login si no está autenticado (excepto en ciertas rutas)
    useEffect(() => {
        console.log('[AUTH] 🔄 useEffect redirección - Auth:', isAuthenticated, 'Loading:', isLoading);
        if (!isAuthenticated && !isLoading) {
            const currentPath = window.location.pathname;
            const publicPaths = ['/', '/login'];
            
            if (!publicPaths.includes(currentPath)) {
                console.log('[AUTH] 🔀 Redirigiendo a login desde:', currentPath);
                router.push('/login');
            }
        }
    }, [isAuthenticated, isLoading, router]);

    const value = useMemo(() => ({
        user,
        isAuthenticated,
        isLoading,
        login,
        logout,
        isAdmin,
        requireAuth,
        requireAdmin,
        checkAuthStatus
    }), [user, isAuthenticated, isLoading, login, logout, isAdmin, requireAuth, requireAdmin, checkAuthStatus]);

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth debe usarse dentro de AuthProvider');
    }
    return context;
} 