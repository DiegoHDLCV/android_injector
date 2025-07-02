'use client';

import { useEffect } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { useRouter } from 'next/navigation';

export default function HomePage() {
  const { isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  // Redirigir automáticamente si está autenticado
  useEffect(() => {
    if (isAuthenticated && !isLoading) {
      router.push('/dashboard');
    }
  }, [isAuthenticated, isLoading, router]);

  // Mostrar loading mientras verifica autenticación
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-16 w-16 border-b-2 border-blue-400 mx-auto mb-6"></div>
          <h2 className="text-xl font-semibold text-white mb-2">Key Injector</h2>
          <p className="text-gray-400">Inicializando sistema...</p>
        </div>
      </div>
    );
  }

  // Si está autenticado, no mostrar nada (se redirige automáticamente)
  if (isAuthenticated) {
    return null;
  }

  // Landing page para usuarios no autenticados
  return (
    <div className="min-h-screen bg-gray-900 flex flex-col">
      {/* Header */}
      <header className="bg-gray-800 border-b border-gray-700">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <svg className="h-8 w-8 text-blue-400" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1117 9z" clipRule="evenodd" />
                </svg>
              </div>
              <div className="ml-3">
                <h1 className="text-xl font-bold text-white">Key Injector</h1>
                <p className="text-sm text-gray-400">Sistema de Inyección de Llaves</p>
              </div>
            </div>
            <div>
              <button 
                onClick={() => router.push('/login')}
                className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium transition-colors"
              >
                Iniciar Sesión
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 flex items-center justify-center">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <div className="mb-12">
            <div className="inline-flex items-center justify-center w-20 h-20 bg-blue-600 rounded-full mb-6">
              <svg className="h-10 w-10 text-white" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1117 9z" clipRule="evenodd" />
              </svg>
            </div>
            <h2 className="text-4xl font-bold text-white mb-6">
              Bienvenido a Key Injector
            </h2>
            <p className="text-xl text-gray-300 mb-8 max-w-3xl mx-auto">
              Sistema completo para la gestión e inyección de llaves criptográficas en dispositivos POS. 
              Seguro, moderno y fácil de usar.
            </p>
          </div>

          {/* Features Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-12">
            <FeatureCard
              icon={
                <svg className="h-8 w-8" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M12.232 4.232a2.5 2.5 0 0 1 3.536 3.536l-1.225 1.224a.75.75 0 0 0 1.061 1.06l1.224-1.224a4 4 0 0 0-5.656-5.656l-3 3a4 4 0 0 0 .225 5.865.75.75 0 0 0 .977-1.138 2.5 2.5 0 0 1-.142-3.665l3-3Z" />
                  <path d="M8.603 14.53a2.5 2.5 0 0 1-3.536-3.536l1.225-1.224a.75.75 0 0 0-1.061-1.06l-1.224 1.224a4 4 0 0 0 5.656 5.656l3-3a4 4 0 0 0-.225-5.865.75.75 0 0 0-.977 1.138 2.5 2.5 0 0 1 .142 3.665l-3 3Z" />
                </svg>
              }
              title="Conexión POS"
              description="Conecta y gestiona dispositivos POS a través de puertos serie con monitoreo en tiempo real."
              color="text-blue-400"
            />
            <FeatureCard
              icon={
                <svg className="h-8 w-8" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M4 4a2 2 0 00-2 2v8a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2H4zm0 2h12v8H4V6z"/>
                  <path d="M6 8h2v2H6V8zm4 0h4v1h-4V8zm0 3h4v1h-4v-1z"/>
                </svg>
              }
              title="Gestión de Perfiles"
              description="Crea y administra perfiles de comercio con configuraciones específicas de llaves."
              color="text-purple-400"
            />
            <FeatureCard
              icon={
                <svg className="h-8 w-8" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1117 9z" clipRule="evenodd" />
                </svg>
              }
              title="Almacén de Llaves"
              description="Almacena y gestiona llaves criptográficas de forma segura con encriptación avanzada."
              color="text-green-400"
            />
          </div>

          {/* CTA */}
          <div className="bg-gray-800 rounded-lg p-8 border border-gray-700">
            <h3 className="text-2xl font-bold text-white mb-4">¿Listo para comenzar?</h3>
            <p className="text-gray-300 mb-6">
              Inicia sesión para acceder al sistema completo de gestión de llaves.
            </p>
            <button 
              onClick={() => router.push('/login')}
              className="bg-blue-600 hover:bg-blue-700 text-white px-8 py-3 rounded-md text-lg font-medium transition-colors inline-flex items-center"
            >
              <svg className="h-5 w-5 mr-2" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M3 4.25A2.25 2.25 0 015.25 2h5.5A2.25 2.25 0 0113 4.25v2a.75.75 0 01-1.5 0v-2a.75.75 0 00-.75-.75h-5.5a.75.75 0 00-.75.75v11.5c0 .414.336.75.75.75h5.5a.75.75 0 00.75-.75v-2a.75.75 0 011.5 0v2A2.25 2.25 0 0110.75 18h-5.5A2.25 2.25 0 013 15.75V4.25z" clipRule="evenodd" />
                <path fillRule="evenodd" d="M6 10a.75.75 0 01.75-.75h9.546l-1.048-1.047a.75.75 0 111.06-1.06l2.5 2.5a.75.75 0 010 1.06l-2.5 2.5a.75.75 0 11-1.06-1.06L16.296 10.75H6.75A.75.75 0 016 10z" clipRule="evenodd" />
              </svg>
              Acceder al Sistema
            </button>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="bg-gray-800 border-t border-gray-700">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex items-center justify-between">
            <div className="text-sm text-gray-400">
              © 2024 Key Injector. Sistema de inyección de llaves criptográficas.
            </div>
            <div className="flex items-center space-x-4 text-sm text-gray-400">
              <span>Frontend: Next.js 15.3.4</span>
              <span>•</span>
              <span>Backend: Flask Python</span>
              <span>•</span>
              <span>UI: TailwindCSS v4</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

function FeatureCard({ icon, title, description, color }: {
  icon: React.ReactNode;
  title: string;
  description: string;
  color: string;
}) {
  return (
    <div className="bg-gray-800 rounded-lg p-6 border border-gray-700 hover:border-gray-600 transition-colors">
      <div className={`${color} mb-4`}>
        {icon}
      </div>
      <h3 className="text-lg font-semibold text-white mb-3">{title}</h3>
      <p className="text-gray-400 text-sm leading-relaxed">{description}</p>
    </div>
  );
}
