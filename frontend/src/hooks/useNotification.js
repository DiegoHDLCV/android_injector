'use client';

import { createContext, useContext, useState, useCallback, useMemo } from 'react';

const NotificationContext = createContext();

export function NotificationProvider({ children }) {
    const [notifications, setNotifications] = useState([]);
    
    console.log('[NOTIFICATION_PROVIDER] 🔄 Provider renderizado - Notifications:', notifications.length);

    const removeNotification = useCallback((id) => {
        setNotifications(prev => prev.filter(notification => notification.id !== id));
    }, []);

    const showNotification = useCallback((title, message, type = 'info', duration = 5000) => {
        const id = Math.random().toString(36).substr(2, 9);
        const notification = {
            id,
            title,
            message,
            type,
            timestamp: Date.now(),
        };

        setNotifications(prev => [...prev, notification]);

        // Auto-remover después del tiempo especificado
        if (duration > 0) {
            const timeoutId = setTimeout(() => {
                removeNotification(id);
            }, duration);
            
            // Return cleanup function
            return () => clearTimeout(timeoutId);
        }

        return id;
    }, [removeNotification]);

    const clearAll = useCallback(() => {
        setNotifications([]);
    }, []);

    // Métodos de conveniencia
    const success = useCallback((title, message, duration) => {
        return showNotification(title, message, 'success', duration);
    }, [showNotification]);

    const error = useCallback((title, message, duration) => {
        return showNotification(title, message, 'error', duration);
    }, [showNotification]);

    const warning = useCallback((title, message, duration) => {
        return showNotification(title, message, 'warning', duration);
    }, [showNotification]);

    const info = useCallback((title, message, duration) => {
        return showNotification(title, message, 'info', duration);
    }, [showNotification]);

    const value = useMemo(() => ({
        notifications,
        showNotification,
        removeNotification,
        clearAll,
        success,
        error,
        warning,
        info
    }), [notifications, showNotification, removeNotification, clearAll, success, error, warning, info]);

    return (
        <NotificationContext.Provider value={value}>
            {children}
        </NotificationContext.Provider>
    );
}

export function useNotification() {
    const context = useContext(NotificationContext);
    if (!context) {
        throw new Error('useNotification debe usarse dentro de NotificationProvider');
    }
    return context;
}

// Componente de notificaciones para renderizar
export function NotificationContainer() {
    const { notifications, removeNotification } = useNotification();

    if (notifications.length === 0) return null;

    return (
        <div className="fixed top-4 right-4 z-50 space-y-2">
            {notifications.map((notification) => (
                <NotificationItem
                    key={notification.id}
                    notification={notification}
                    onClose={() => removeNotification(notification.id)}
                />
            ))}
        </div>
    );
}

function NotificationItem({ notification, onClose }) {
    const { type, title, message } = notification;

    const typeStyles = {
        success: {
            bg: 'bg-green-600',
            icon: (
                <svg className="h-5 w-5 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.06 0l4.25-5.85z" clipRule="evenodd" />
                </svg>
            )
        },
        error: {
            bg: 'bg-red-600',
            icon: (
                <svg className="h-5 w-5 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clipRule="evenodd" />
                </svg>
            )
        },
        warning: {
            bg: 'bg-yellow-600',
            icon: (
                <svg className="h-5 w-5 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" />
                </svg>
            )
        },
        info: {
            bg: 'bg-blue-600',
            icon: (
                <svg className="h-5 w-5 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A.75.75 0 009 13.75a.75.75 0 00.75.75h.5a.75.75 0 00.75-.75v-.75a.75.75 0 00-.75-.75h-.253a.25.25 0 01-.244-.304l.459-2.066A.75.75 0 0011 9.25a.75.75 0 00-.75-.75H9z" clipRule="evenodd" />
                </svg>
            )
        }
    };

    const style = typeStyles[type] || typeStyles.info;

    return (
        <div className={`transform transition-all duration-300 ease-in-out p-4 rounded-lg shadow-lg max-w-sm ${style.bg} text-white`}>
            <div className="flex items-start">
                <div className="flex-shrink-0">
                    {style.icon}
                </div>
                <div className="ml-3 flex-1">
                    <p className="text-sm font-medium">{title}</p>
                    <p className="text-sm opacity-90">{message}</p>
                </div>
                <div className="ml-auto pl-3">
                    <button 
                        onClick={onClose}
                        className="text-white hover:text-gray-200 transition-colors"
                    >
                        <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
                            <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
                        </svg>
                    </button>
                </div>
            </div>
        </div>
    );
} 