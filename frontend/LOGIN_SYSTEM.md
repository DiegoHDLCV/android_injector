# Sistema de Login - Key Injector (Next.js)

Este documento explica el sistema de autenticación completo adaptado de la plantilla HTML original a Next.js con React.

## 🚀 Funcionalidades Implementadas

### ✅ **Login Completo**
- Formulario de autenticación con validación
- Estados de carga y feedback visual
- Redirección automática tras login exitoso
- Manejo de errores con notificaciones

### ✅ **Reset de Contraseña Admin**
- Funcionalidad para reiniciar contraseña de administrador
- Validación de entrada
- Confirmación mediante prompt nativo

### ✅ **Protección Automática de Rutas**
- Redirección automática a login si no está autenticado
- Mantenimiento de rutas públicas (/, /login)
- Verificación de estado de autenticación en tiempo real

## 📁 Archivos del Sistema

```
src/
├── app/
│   ├── login/
│   │   └── page.tsx              # Página de login principal
│   ├── layout.tsx               # Layout con AuthProvider
│   └── page.tsx                 # Homepage con redirección
├── hooks/
│   └── useAuth.js              # Hook de autenticación
└── lib/
    └── api-client.js           # Cliente API con endpoints auth
```

## 🎯 Uso del Sistema

### **Acceso a la Página de Login**
```
http://localhost:3000/login
```

### **Hook de Autenticación**
```jsx
import { useAuth } from '@/hooks/useAuth';

function MyComponent() {
    const { 
        user,                    // Datos del usuario actual
        isAuthenticated,         // Estado de autenticación
        isLoading,              // Estado de carga
        login,                  // Función de login
        logout,                 // Función de logout
        isAdmin,                // Verificar si es admin
        requireAuth,            // Requerir autenticación
        requireAdmin            // Requerir permisos admin
    } = useAuth();
}
```

## 🔐 Flujo de Autenticación

### **1. Usuario No Autenticado**
```
HomePage (/) → Muestra landing page
    ↓ Click "Iniciar Sesión"
LoginPage (/login) → Formulario de login
    ↓ Login exitoso
ConnectionPage (/connection) → Dashboard principal
```

### **2. Usuario Ya Autenticado**
```
Cualquier página → Verificación automática → Dashboard
HomePage (/) → Auto-redirección → ConnectionPage (/connection)
LoginPage (/login) → Auto-redirección → ConnectionPage (/connection)
```

### **3. Acceso a Ruta Protegida Sin Auth**
```
/connection → Auto-redirección → /login
/profiles → Auto-redirección → /login
Cualquier ruta protegida → Auto-redirección → /login
```

## 🎨 Componentes UI del Login

### **Formulario Principal**
- ✅ Campos de usuario y contraseña
- ✅ Validación en tiempo real
- ✅ Estados de carga con spinners
- ✅ Foco automático y accesibilidad
- ✅ Diseño responsive

### **Reset de Contraseña Admin**
- ✅ Botón secundario claramente identificado
- ✅ Modal de confirmación (prompt)
- ✅ Validación de longitud mínima
- ✅ Feedback visual de éxito/error

### **Elementos Visuales**
- ✅ Icono de llave en header
- ✅ Animaciones suaves de hover
- ✅ Colores consistentes con el tema
- ✅ Tipografía Inter modern

## 🔧 API Integration

### **Endpoints Utilizados**
```javascript
// Login
POST /auth/login
{
    "username": "admin",
    "password": "password"
}

// Verificar estado
GET /auth/status

// Logout
POST /auth/logout

// Reset contraseña admin
POST /admin/reset-password
{
    "new_password": "nueva_contraseña"
}
```

### **Manejo de Respuestas**
```javascript
// Login exitoso
{
    "success": true,
    "user": {
        "username": "admin",
        "role": "admin"
    }
}

// Login fallido
{
    "success": false,
    "message": "Credenciales inválidas"
}
```

## 🔄 Estados de la Aplicación

### **isLoading: true**
- Muestra spinner de verificación
- Deshabilita formularios
- No permite interacciones

### **isAuthenticated: false**
- Muestra formulario de login
- Redirección automática desde rutas protegidas
- Acceso solo a rutas públicas

### **isAuthenticated: true**
- Acceso completo al sistema
- Redirección automática desde login
- Funcionalidades según rol de usuario

## 🎯 Ejemplos de Uso

### **Proteger una Página**
```jsx
'use client';

import { useAuth } from '@/hooks/useAuth';

export default function ProtectedPage() {
    const { isAuthenticated, isLoading, user } = useAuth();

    if (isLoading) {
        return <div>Verificando autenticación...</div>;
    }

    if (!isAuthenticated) {
        return null; // useAuth redirige automáticamente
    }

    return (
        <div>
            <h1>Bienvenido {user.username}</h1>
            <p>Esta es una página protegida</p>
        </div>
    );
}
```

### **Login Programático**
```jsx
'use client';

import { useAuth } from '@/hooks/useAuth';

export default function CustomLoginForm() {
    const { login } = useAuth();
    
    const handleLogin = async () => {
        const result = await login({
            username: 'admin',
            password: 'password'
        });
        
        if (result.success) {
            // Login exitoso - redirección automática
            console.log('Login exitoso');
        } else {
            // Manejar error
            console.error(result.message);
        }
    };
}
```

### **Verificar Roles**
```jsx
'use client';

import { useAuth } from '@/hooks/useAuth';

export default function AdminPanel() {
    const { isAdmin, requireAdmin } = useAuth();

    useEffect(() => {
        requireAdmin(); // Redirección automática si no es admin
    }, []);

    if (!isAdmin()) {
        return null;
    }

    return <div>Panel de administración</div>;
}
```

## 🚀 Funcionalidades Avanzadas

### **Auto-Renovación de Sesión**
El hook `useAuth` verifica automáticamente el estado cada vez que se monta un componente.

### **Notificaciones Integradas**
```jsx
// Automático en useAuth
showNotification('¡Bienvenido!', `Hola ${user.username}`, 'success');
showNotification('Error de login', 'Credenciales inválidas', 'error');
```

### **Rutas Públicas Configurables**
```javascript
// En useAuth.js
const publicPaths = ['/', '/login', '/about'];
```

## 🎨 Personalización de Estilos

### **Colores del Tema**
```css
/* En globals.css */
:root {
  --background: #111827;     /* Fondo principal */
  --foreground: #f9fafb;     /* Texto principal */
}
```

### **Componentes TailwindCSS**
```jsx
// Botón principal
className="bg-green-600 hover:bg-green-700 text-white..."

// Formulario
className="bg-gray-700 border-gray-600 text-white..."

// Estado de carga
className="animate-spin h-5 w-5..."
```

## 🔍 Debugging y Desarrollo

### **Console Logs Útiles**
```javascript
// En useAuth
console.log('Estado auth:', { isAuthenticated, user, isLoading });

// En login page
console.log('Resultado login:', result);
```

### **Verificar Estado Global**
```javascript
// En cualquier componente
const authState = useAuth();
console.log('Estado completo:', authState);
```

## 🚀 Próximas Mejoras

### **Posibles Extensiones**
- [ ] Recordar usuario (localStorage)
- [ ] Login con 2FA
- [ ] Recuperación de contraseña por email
- [ ] Sessions timeout configurable
- [ ] Historial de logins

### **Optimizaciones**
- [ ] Lazy loading de componentes
- [ ] Cache de estado de auth
- [ ] Preload de recursos post-login
- [ ] Animaciones más sofisticadas

---

## 🎉 ¡Sistema de Login Completamente Funcional!

El sistema está **100% integrado** con:
- ✅ Next.js App Router
- ✅ TailwindCSS v4
- ✅ React Hooks modernos
- ✅ TypeScript ready
- ✅ Responsive design
- ✅ Accesibilidad completa

**¡Listo para producción!** 🚀 