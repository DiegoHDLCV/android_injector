# Arquitectura Next.js - Key Injector

Este documento explica cómo se ha adaptado toda la lógica JavaScript modular del proyecto original a Next.js con React.

## 📁 Estructura del Proyecto

```
frontend/
├── src/
│   ├── app/                      # App Router de Next.js
│   │   ├── layout.tsx           # Layout principal con providers
│   │   ├── page.tsx             # Homepage con landing page
│   │   └── connection/
│   │       └── page.tsx         # Página de conexión POS
│   ├── components/               # Componentes React reutilizables
│   │   └── pos/
│   │       └── ConnectionCard.jsx  # Componente principal de conexión
│   ├── hooks/                    # Custom hooks de React
│   │   ├── useAuth.js           # Hook de autenticación
│   │   ├── useNotification.js   # Hook de notificaciones
│   │   └── usePOSConnection.js  # Hook de conexión POS
│   └── lib/
│       └── api-client.js        # Cliente API moderno
```

## 🔄 Mapeo de Archivos Originales

| Archivo Original | Adaptación Next.js | Tipo |
|-----------------|-------------------|------|
| `api-service.js` | `lib/api-client.js` | Cliente API |
| `auth-service.js` | `hooks/useAuth.js` | Custom Hook + Context |
| `notification-service.js` | `hooks/useNotification.js` | Custom Hook + Context |
| `connection-page.js` | `hooks/usePOSConnection.js` + `components/pos/ConnectionCard.jsx` | Hook + Componente |
| `pos-connection-component.js` | `components/pos/ConnectionCard.jsx` | Componente React |
| `base-page.js` | Patrones distribuidos en hooks y componentes | Funcionalidad distribuida |
| `main.js` | `app/layout.tsx` | Layout con providers |

## 🎯 Hooks Principales

### useAuth()
```javascript
import { useAuth } from '@/hooks/useAuth';

function MyComponent() {
    const { 
        user, 
        isAuthenticated, 
        login, 
        logout, 
        isAdmin, 
        requireAuth 
    } = useAuth();
    
    // Uso del hook...
}
```

### useNotification()
```javascript
import { useNotification } from '@/hooks/useNotification';

function MyComponent() {
    const { 
        showNotification, 
        success, 
        error, 
        warning, 
        info 
    } = useNotification();
    
    const handleAction = () => {
        success('Éxito', 'Operación completada');
    };
}
```

### usePOSConnection()
```javascript
import { usePOSConnection } from '@/hooks/usePOSConnection';

function MyComponent() {
    const {
        isConnected,
        currentPort,
        availablePorts,
        connect,
        disconnect,
        readSerial,
        connectionLogs
    } = usePOSConnection();
}
```

## 🧩 Componentes Principales

### ConnectionCard
```jsx
import ConnectionCard from '@/components/pos/ConnectionCard';

function MyPage() {
    return (
        <ConnectionCard 
            showLogs={true}
            compactMode={false}
            hideWhenConnected={false}
        />
    );
}
```

## 📡 Cliente API

```javascript
import apiClient from '@/lib/api-client';

// Llamadas directas
const response = await apiClient.get('/endpoint');
const result = await apiClient.post('/endpoint', data);

// APIs específicas
const authApi = await apiClient.auth();
const serialApi = await apiClient.serial();
const profilesApi = await apiClient.profiles();
```

## 🔧 Configuración del Layout

```jsx
// app/layout.tsx
import { NotificationProvider, NotificationContainer } from '@/hooks/useNotification';
import { AuthProvider } from '@/hooks/useAuth';
import { POSConnectionProvider } from '@/hooks/usePOSConnection';

export default function RootLayout({ children }) {
    return (
        <html lang="es">
            <body>
                <NotificationProvider>
                    <AuthProvider>
                        <POSConnectionProvider>
                            {children}
                            <NotificationContainer />
                        </POSConnectionProvider>
                    </AuthProvider>
                </NotificationProvider>
            </body>
        </html>
    );
}
```

## 🚀 Páginas Ejemplo

### Página de Conexión Completa
```jsx
'use client';

import { useAuth } from '@/hooks/useAuth';
import { usePOSConnection } from '@/hooks/usePOSConnection';
import ConnectionCard from '@/components/pos/ConnectionCard';

export default function ConnectionPage() {
    const { user, isAuthenticated } = useAuth();
    const { isConnected, currentPort } = usePOSConnection();

    if (!isAuthenticated) return null;

    return (
        <div className="min-h-screen bg-gray-900">
            <header className="bg-gray-800">
                <h1>Key Injector - {user?.username}</h1>
                <div>Estado: {isConnected ? currentPort : 'Desconectado'}</div>
            </header>
            
            <main className="container mx-auto p-8">
                <ConnectionCard showLogs={true} />
            </main>
        </div>
    );
}
```

### Uso de Notificaciones
```jsx
'use client';

import { useNotification } from '@/hooks/useNotification';

export default function MyComponent() {
    const { success, error, warning, info } = useNotification();

    const handleSuccess = () => {
        success('¡Éxito!', 'Operación completada correctamente');
    };

    const handleError = () => {
        error('Error', 'Algo salió mal');
    };

    return (
        <div>
            <button onClick={handleSuccess}>Mostrar Éxito</button>
            <button onClick={handleError}>Mostrar Error</button>
        </div>
    );
}
```

## 🔐 Protección de Rutas

```jsx
'use client';

import { useAuth } from '@/hooks/useAuth';
import { useEffect } from 'react';

export default function ProtectedPage() {
    const { isAuthenticated, isLoading, requireAuth, requireAdmin } = useAuth();

    useEffect(() => {
        // Requiere autenticación
        requireAuth();
        
        // O requiere permisos de admin
        // requireAdmin();
    }, []);

    if (isLoading) return <div>Cargando...</div>;
    if (!isAuthenticated) return null;

    return <div>Contenido protegido</div>;
}
```

## 🛠 Creación de Nuevos Componentes

### 1. Componente Simple
```jsx
'use client';

import { usePOSConnection } from '@/hooks/usePOSConnection';

export default function POSStatusBadge() {
    const { isConnected, currentPort } = usePOSConnection();
    
    return (
        <div className={`badge ${isConnected ? 'success' : 'error'}`}>
            {isConnected ? `Conectado a ${currentPort}` : 'Desconectado'}
        </div>
    );
}
```

### 2. Componente con Estado Local
```jsx
'use client';

import { useState } from 'react';
import { useNotification } from '@/hooks/useNotification';
import { usePOSConnection } from '@/hooks/usePOSConnection';

export default function SerialReader() {
    const [serialNumber, setSerialNumber] = useState('');
    const { readSerial, isLoading } = usePOSConnection();
    const { success, error } = useNotification();

    const handleReadSerial = async () => {
        try {
            const serial = await readSerial();
            if (serial) {
                setSerialNumber(serial);
                success('Serial leído', `Número: ${serial}`);
            }
        } catch (err) {
            error('Error', 'No se pudo leer el serial');
        }
    };

    return (
        <div>
            <button onClick={handleReadSerial} disabled={isLoading}>
                {isLoading ? 'Leyendo...' : 'Leer Serial'}
            </button>
            {serialNumber && <div>Serial: {serialNumber}</div>}
        </div>
    );
}
```

## 📊 Ventajas de esta Arquitectura

### ✅ Beneficios
- **Estado Centralizado**: Todos los providers manejan estado global
- **Reutilización**: Hooks y componentes completamente reutilizables  
- **Tipado**: Soporte completo de TypeScript
- **Performance**: React optimizations (useMemo, useCallback)
- **SSR Ready**: Compatible con Next.js App Router
- **Mantenibilidad**: Separación clara de responsabilidades

### 🔄 Adaptaciones Realizadas
- **Eventos DOM → React Events**: Event listeners convertidos a props
- **Clases → Hooks**: Lógica de clases convertida a custom hooks
- **Vanilla JS → React**: Manipulación directa del DOM a componentes React
- **Global State → Context**: Estado global usando React Context
- **Module System → Next.js**: Importaciones adaptadas al sistema de Next.js

## 🚀 Próximos Pasos

### Para Agregar Nuevas Páginas:
1. Crear el hook específico (ej: `useProfiles()`)
2. Crear los componentes necesarios 
3. Crear la página en `app/nueva-pagina/page.tsx`
4. Usar los hooks existentes (`useAuth`, `useNotification`, etc.)

### Para Nuevas Funcionalidades:
1. Agregar endpoints al `api-client.js`
2. Crear hook personalizado si es necesario
3. Crear componentes reutilizables
4. Integrar con los sistemas existentes

## 🔗 Patrones de Uso Recomendados

```jsx
// ✅ Patrón recomendado
'use client';

import { useAuth } from '@/hooks/useAuth';
import { useNotification } from '@/hooks/useNotification';

export default function MyPage() {
    const { isAuthenticated, user } = useAuth();
    const { success } = useNotification();

    // Lógica de la página...
    
    return (
        <div>
            {isAuthenticated && <div>Bienvenido {user.username}</div>}
        </div>
    );
}
```

Esta arquitectura mantiene toda la funcionalidad del sistema original pero adaptada completamente a los patrones modernos de React y Next.js. 