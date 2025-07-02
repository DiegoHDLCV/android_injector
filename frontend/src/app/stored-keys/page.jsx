'use client';

import { useState, useEffect } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { useNotification } from '@/hooks/useNotification';
import apiClient from '@/lib/api-client';

export default function StoredKeysPage() {
  const { requireAuth } = useAuth();
  const { success, error } = useNotification();
  const [keys, setKeys] = useState([]);
  const [loading, setLoading] = useState(false);
  const [currentView, setCurrentView] = useState('cards'); // 'cards' o 'table'
  const [connectionStatus, setConnectionStatus] = useState(null);
  const [selectedKey, setSelectedKey] = useState(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showViewModal, setShowViewModal] = useState(false);

  useEffect(() => {
    requireAuth();
    loadKeys();
    updateConnectionStatus();
    // Auto-refresh cada 30 segundos
    const interval = setInterval(updateConnectionStatus, 30000);
    return () => clearInterval(interval);
  }, [requireAuth]);

  const loadKeys = async () => {
    setLoading(true);
    try {
      const data = await apiClient.storage().getKeys();
      setKeys(data.keys || []);
      // if (data.keys?.length > 0) {
      //   success('Datos cargados', `${data.keys.length} llaves encontradas`);
      // }
    } catch (err) {
      error('Error', 'No se pudieron cargar las llaves: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const updateConnectionStatus = async () => {
    try {
      const data = await apiClient.serial().getStatus();
      setConnectionStatus(data);
    } catch (err) {
      console.error('Error actualizando estado de conexión:', err);
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleString('es-ES', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const clearAllKeys = async () => {
    if (!confirm('¿Estás seguro de que quieres eliminar TODAS las llaves? Esta acción no se puede deshacer.')) {
      return;
    }
    
    try {
      await apiClient.storage().clearAllKeys();
      success('Éxito', 'Todas las llaves han sido eliminadas');
      loadKeys();
    } catch (err) {
      error('Error', 'No se pudieron eliminar las llaves: ' + err.message);
    }
  };

  const deleteKey = async (kcv) => {
    try {
      await apiClient.storage().deleteKey(kcv);
      success('Éxito', 'Llave eliminada correctamente');
      setShowDeleteModal(false);
      setSelectedKey(null);
      loadKeys();
    } catch (err) {
      error('Error', 'No se pudo eliminar la llave: ' + err.message);
    }
  };

  const handleDeleteKey = (key) => {
    setSelectedKey(key);
    setShowDeleteModal(true);
  };

  const handleViewKey = (key) => {
    setSelectedKey(key);
    setShowViewModal(true);
  };

  const toggleView = () => {
    setCurrentView(currentView === 'cards' ? 'table' : 'cards');
  };

  const createKeyCard = (key) => {
    const isInjected = key.last_injection_date ? true : false;
    const creationDate = formatDate(key.timestamp);
    const injectionDate = formatDate(key.last_injection_date);

    const statusBadge = isInjected ? 
      'bg-green-900 text-green-200 border-green-600' :
      'bg-yellow-900 text-yellow-200 border-yellow-600';

    return (
      <div key={key.kcv} className="bg-gray-800 border border-gray-700 rounded-xl p-4 hover:border-gray-600 transition-all duration-200">
        {/* Header de la tarjeta */}
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center space-x-3">
            <div className={`w-10 h-10 bg-gradient-to-br ${isInjected ? 'from-green-400 to-green-600' : 'from-gray-400 to-gray-600'} rounded-lg flex items-center justify-center text-white font-bold text-sm`}>
              #{key.id || '?'}
            </div>
            <div>
              <h3 className="font-mono text-lg font-bold text-white">{key.kcv}</h3>
              <div className="flex items-center space-x-2">
                <span className={`inline-flex items-center px-2 py-1 text-xs font-medium rounded-full border ${statusBadge}`}>
                  {isInjected ? '✓ Inyectada' : '⏳ Pendiente'}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Información de la llave */}
        <div className="space-y-2 mb-4">
          <div className="flex justify-between items-center">
            <span className="text-sm text-gray-400">Creación:</span>
            <span className="text-sm text-white">{creationDate}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm text-gray-400">Inyección:</span>
            <span className="text-sm font-mono text-white">{isInjected ? 'Completada' : 'Pendiente'}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm text-gray-400">Última Inyección:</span>
            <span className="text-sm text-white">{injectionDate}</span>
          </div>
        </div>

        {/* Botones de acción */}
        <div className="flex space-x-3">
          <button 
            onClick={() => handleViewKey(key)}
            className="group flex-1 bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 focus:ring-4 focus:ring-blue-300 text-white text-sm font-medium py-2.5 px-4 rounded-lg transition-all duration-200 shadow-lg hover:shadow-xl flex items-center justify-center"
          >
            <svg className="w-4 h-4 mr-2 group-hover:scale-110 transition-transform duration-200" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
              <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
              <path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.022 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" />
            </svg>
            <span className="font-semibold">Ver Detalles</span>
          </button>
          <button 
            onClick={() => handleDeleteKey(key)}
            className="group bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 focus:ring-4 focus:ring-red-300 text-white text-sm font-medium py-2.5 px-4 rounded-lg transition-all duration-200 shadow-lg hover:shadow-xl flex items-center justify-center"
          >
            <svg className="w-4 h-4 group-hover:scale-110 transition-transform duration-200" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M8.75 1A2.75 2.75 0 006 3.75v.443c-.795.077-1.584.176-2.365.298a.75.75 0 10.23 1.482l.149-.022.841 10.518A2.75 2.75 0 007.596 19h4.807a2.75 2.75 0 002.742-2.53l.841-10.52.149.023a.75.75 0 00.23-1.482A41.03 41.03 0 0014 4.193V3.75A2.75 2.75 0 0011.25 1h-2.5z" clipRule="evenodd" />
            </svg>
          </button>
        </div>
      </div>
    );
  };

  const injectedKeys = keys.filter(k => k.last_injection_date).length;
  const pendingKeys = keys.length - injectedKeys;

  // Encontrar la última inyección
  let lastInjection = 'N/A';
  const injectedKeysWithDate = keys.filter(key => key.last_injection_date);
  if (injectedKeysWithDate.length > 0) {
    const latest = injectedKeysWithDate.reduce((latest, current) => {
      return new Date(current.last_injection_date) > new Date(latest.last_injection_date) ? current : latest;
    });
    lastInjection = formatDate(latest.last_injection_date);
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white">
      {/* Header */}
      <div className="sticky top-16 z-40 bg-gray-900 border-b border-gray-700 px-4 py-4 md:px-6">
        <div className="flex flex-col space-y-3 md:flex-row md:items-center md:justify-between md:space-y-0">
          {/* Título y subtítulo */}
          <div className="flex items-center space-x-3">
            <div className="flex-shrink-0">
              <div className="w-10 h-10 bg-gradient-to-br from-green-400 to-blue-500 rounded-lg flex items-center justify-center">
                <svg className="w-6 h-6 text-white" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1221 9z" clipRule="evenodd" />
                </svg>
              </div>
            </div>
            <div>
              <h1 className="text-xl font-bold text-white md:text-2xl">Almacén de Llaves</h1>
              <p className="text-sm text-gray-400 hidden md:block">Gestión y visualización de llaves criptográficas</p>
            </div>
          </div>

          {/* Botones de acción */}
          <div className="flex flex-wrap gap-2 md:flex-nowrap md:space-x-3">
            <button 
              onClick={loadKeys}
              disabled={loading}
              className="group flex-1 md:flex-none inline-flex items-center justify-center px-4 py-2.5 bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 focus:ring-4 focus:ring-blue-300 text-white text-sm font-medium rounded-lg transition-all duration-200 shadow-lg hover:shadow-xl min-h-[44px] md:min-h-[40px] disabled:opacity-50"
            >
              <svg className={`w-4 h-4 mr-2 transition-transform duration-500 ${loading ? 'animate-spin' : 'group-hover:rotate-180'}`} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M15.312 11.424a5.5 5.5 0 01-9.201 2.466l-.312-.311h2.433a.75.75 0 000-1.5H3.989a.75.75 0 00-.75.75v4.242a.75.75 0 001.5 0v-2.43l.31.31a7 7 0 0011.712-3.138.75.75 0 00-1.449-.39z" clipRule="evenodd" />
              </svg>
              <span className="hidden sm:inline">Refrescar Datos</span>
              <span className="sm:hidden">Refrescar</span>
            </button>
            
            <button 
              onClick={clearAllKeys}
              className="group flex-1 md:flex-none inline-flex items-center justify-center px-4 py-2.5 bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 focus:ring-4 focus:ring-red-300 text-white text-sm font-medium rounded-lg transition-all duration-200 shadow-lg hover:shadow-xl min-h-[44px] md:min-h-[40px]"
            >
              <svg className="w-4 h-4 mr-2 group-hover:scale-110 transition-transform duration-200" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M8.75 1A2.75 2.75 0 006 3.75v.443c-.795.077-1.584.176-2.365.298a.75.75 0 10.23 1.482l.149-.022.841 10.518A2.75 2.75 0 007.596 19h4.807a2.75 2.75 0 002.742-2.53l.841-10.52.149.023a.75.75 0 00.23-1.482A41.03 41.03 0 0014 4.193V3.75A2.75 2.75 0 0011.25 1h-2.5z" clipRule="evenodd" />
              </svg>
              <span className="hidden sm:inline">Limpiar Almacén</span>
              <span className="sm:hidden">Limpiar</span>
            </button>
          </div>
        </div>
      </div>

      {/* Estado de Conexión Banner */}
      {connectionStatus && !connectionStatus.is_connected && (
        <div className="mx-4 mt-4 p-4 rounded-lg border-l-4 border-red-500 bg-red-900 bg-opacity-20">
          <div className="flex items-center justify-between">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <svg className="h-6 w-6 text-red-500" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A.75.75 0 009 13.75a.75.75 0 00.75.75h.5a.75.75 0 00.75-.75v-.75a.75.75 0 00-.75-.75h-.253a.25.25 0 01-.244-.304l.459-2.066A.75.75 0 0011 9.25a.75.75 0 00-.75-.75H9z" clipRule="evenodd" />
                </svg>
              </div>
              <div className="ml-3">
                <p className="text-sm font-medium text-red-200">No hay dispositivo conectado. Conecta un dispositivo para inyectar llaves.</p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Estadísticas */}
      <div className="p-4 md:p-6">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 md:gap-4 mb-6">
          {/* Total de Llaves */}
          <div className="bg-gray-800 rounded-xl p-4 border border-gray-700 hover:border-gray-600 transition-colors">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <div className="w-10 h-10 bg-blue-600 rounded-lg flex items-center justify-center">
                  <svg className="w-5 h-5 text-white" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1221 9z" clipRule="evenodd" />
                  </svg>
                </div>
              </div>
              <div className="ml-3 min-w-0 flex-1">
                <p className="text-xs text-gray-400 font-medium truncate">Total</p>
                <p className="text-lg font-bold text-white">{keys.length}</p>
              </div>
            </div>
          </div>

          {/* Inyectadas */}
          <div className="bg-gray-800 rounded-xl p-4 border border-gray-700 hover:border-gray-600 transition-colors">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <div className="w-10 h-10 bg-green-600 rounded-lg flex items-center justify-center">
                  <svg className="w-5 h-5 text-white" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.06 0l4.25-5.85z" clipRule="evenodd" />
                  </svg>
                </div>
              </div>
              <div className="ml-3 min-w-0 flex-1">
                <p className="text-xs text-gray-400 font-medium truncate">Inyectadas</p>
                <p className="text-lg font-bold text-white">{injectedKeys}</p>
              </div>
            </div>
          </div>

          {/* Pendientes */}
          <div className="bg-gray-800 rounded-xl p-4 border border-gray-700 hover:border-gray-600 transition-colors">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <div className="w-10 h-10 bg-yellow-600 rounded-lg flex items-center justify-center">
                  <svg className="w-5 h-5 text-white" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A.75.75 0 009 13.75a.75.75 0 00.75.75h.5a.75.75 0 00.75-.75v-.75a.75.75 0 00-.75-.75h-.253a.25.25 0 01-.244-.304l.459-2.066A.75.75 0 0011 9.25a.75.75 0 00-.75-.75H9z" clipRule="evenodd" />
                  </svg>
                </div>
              </div>
              <div className="ml-3 min-w-0 flex-1">
                <p className="text-xs text-gray-400 font-medium truncate">Pendientes</p>
                <p className="text-lg font-bold text-white">{pendingKeys}</p>
              </div>
            </div>
          </div>

          {/* Última Inyección */}
          <div className="bg-gray-800 rounded-xl p-4 border border-gray-700 hover:border-gray-600 transition-colors col-span-2 lg:col-span-1">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <div className="w-10 h-10 bg-purple-600 rounded-lg flex items-center justify-center">
                  <svg className="w-5 h-5 text-white" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path d="M10 2a5 5 0 00-5 5v2a2 2 0 00-2 2v5a2 2 0 002 2h10a2 2 0 002-2v-5a2 2 0 00-2-2H7V7a3 3 0 015.905-.75 1 1 0 001.937-.5A5.002 5.002 0 0010 2z" />
                  </svg>
                </div>
              </div>
              <div className="ml-3 min-w-0 flex-1">
                <p className="text-xs text-gray-400 font-medium truncate">Última Inyección</p>
                <p className="text-sm font-bold text-white truncate">{lastInjection}</p>
              </div>
            </div>
          </div>
        </div>

        {/* Controles de Vista */}
        <div className="flex justify-end mb-6">
          <button 
            onClick={toggleView}
            className="inline-flex items-center px-3 py-2 bg-gray-700 hover:bg-gray-600 text-white text-sm font-medium rounded-lg transition-colors"
          >
            <svg className="w-4 h-4 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
              {currentView === 'cards' ? (
                <path d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM3 10a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H4a1 1 0 01-1-1v-6zM14 9a1 1 0 00-1 1v6a1 1 0 001 1h2a1 1 0 001-1v-6a1 1 0 00-1-1h-2z" />
              ) : (
                <path fillRule="evenodd" d="M4.25 2A2.25 2.25 0 002 4.25v2.5A2.25 2.25 0 004.25 9h2.5A2.25 2.25 0 009 6.75v-2.5A2.25 2.25 0 006.75 2h-2.5zm0 9A2.25 2.25 0 002 13.25v2.5A2.25 2.25 0 004.25 18h2.5A2.25 2.25 0 009 15.75v-2.5A2.25 2.25 0 006.75 11h-2.5zm9-9A2.25 2.25 0 0011 4.25v2.5A2.25 2.25 0 0013.25 9h2.5A2.25 2.25 0 0018 6.75v-2.5A2.25 2.25 0 0015.75 2h-2.5zm0 9A2.25 2.25 0 0011 13.25v2.5A2.25 2.25 0 0013.25 18h2.5A2.25 2.25 0 0018 15.75v-2.5A2.25 2.25 0 0015.75 11h-2.5z" clipRule="evenodd" />
              )}
            </svg>
            <span>{currentView === 'cards' ? 'Tabla' : 'Tarjetas'}</span>
          </button>
        </div>

        {/* Vista de Tarjetas */}
        {currentView === 'cards' && (
          <div className="space-y-4">
            {keys.length === 0 ? (
              <div className="text-center py-12">
                <div className="max-w-md mx-auto">
                  <div className="w-20 h-20 bg-gray-800 rounded-full flex items-center justify-center mx-auto mb-4">
                    <svg className="w-10 h-10 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                      <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" clipRule="evenodd" />
                    </svg>
                  </div>
                  <h3 className="text-lg font-medium text-gray-300 mb-2">No hay llaves almacenadas</h3>
                  <p className="text-sm text-gray-500 mb-6">Comienza generando llaves usando la ceremonia.</p>
                </div>
              </div>
            ) : (
              <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                {keys.map(createKeyCard)}
              </div>
            )}
          </div>
        )}

        {/* Vista de Tabla */}
        {currentView === 'table' && keys.length > 0 && (
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left text-gray-500 dark:text-gray-400">
                <thead className="text-xs text-gray-700 uppercase bg-gray-50 dark:bg-gray-700 dark:text-gray-400">
                  <tr>
                    <th className="px-6 py-4 font-medium">ID</th>
                    <th className="px-6 py-4 font-medium">KCV</th>
                    <th className="px-6 py-4 font-medium">Creación</th>
                    <th className="px-6 py-4 font-medium">Última Inyección</th>
                    <th className="px-6 py-4 font-medium">Estado</th>
                    <th className="px-6 py-4 font-medium">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                  {keys.map((key) => {
                    const isInjected = key.last_injection_date ? true : false;
                    return (
                      <tr key={key.kcv} className="bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors">
                        <td className="px-6 py-4 font-medium text-gray-900 dark:text-white">#{key.id || 'N/A'}</td>
                        <td className="px-6 py-4 font-mono text-sm font-semibold text-gray-900 dark:text-white">{key.kcv}</td>
                        <td className="px-6 py-4 text-sm text-gray-500 dark:text-gray-400">{formatDate(key.timestamp)}</td>
                        <td className="px-6 py-4 text-sm text-gray-500 dark:text-gray-400">{formatDate(key.last_injection_date)}</td>
                        <td className="px-6 py-4">
                          <span className={`inline-flex items-center px-2 py-1 text-xs font-medium rounded-full ${isInjected ? 'bg-green-900 text-green-200' : 'bg-yellow-900 text-yellow-200'}`}>
                            {isInjected ? 'Inyectada' : 'Pendiente'}
                          </span>
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-center space-x-2">
                            <button 
                              onClick={() => handleViewKey(key)}
                              className="inline-flex items-center px-3 py-2 text-sm font-medium text-center text-white bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 focus:ring-4 focus:outline-none focus:ring-blue-300 rounded-lg"
                            >
                              Ver
                            </button>
                            <button 
                              onClick={() => handleDeleteKey(key)}
                              className="inline-flex items-center px-3 py-2 text-sm font-medium text-center text-white bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800 focus:ring-4 focus:outline-none focus:ring-red-300 rounded-lg"
                            >
                              Eliminar
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {/* Modal para Ver Detalles */}
      {showViewModal && selectedKey && (
        <div className="fixed inset-0 bg-gray-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="relative w-full max-w-2xl max-h-[90vh] bg-white dark:bg-gray-800 rounded-2xl shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
              <h3 className="text-xl font-semibold text-gray-900 dark:text-white">Detalles de Llave</h3>
              <button 
                onClick={() => setShowViewModal(false)}
                className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>
            <div className="p-6">
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">KCV</label>
                  <p className="mt-1 text-lg font-mono font-bold text-gray-900 dark:text-white">{selectedKey.kcv}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">ID</label>
                  <p className="mt-1 text-sm text-gray-900 dark:text-white">#{selectedKey.id || 'N/A'}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">Fecha de Creación</label>
                  <p className="mt-1 text-sm text-gray-900 dark:text-white">{formatDate(selectedKey.timestamp)}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">Última Inyección</label>
                  <p className="mt-1 text-sm text-gray-900 dark:text-white">{formatDate(selectedKey.last_injection_date)}</p>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">Estado</label>
                  <span className={`inline-flex items-center px-2 py-1 text-xs font-medium rounded-full ${selectedKey.last_injection_date ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'}`}>
                    {selectedKey.last_injection_date ? 'Inyectada' : 'Pendiente'}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modal para Eliminar */}
      {showDeleteModal && selectedKey && (
        <div className="fixed inset-0 bg-gray-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="relative w-full max-w-lg bg-white dark:bg-gray-800 rounded-2xl shadow-2xl">
            <div className="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
              <h3 className="text-lg font-semibold text-red-600">Eliminar Llave</h3>
              <button 
                onClick={() => setShowDeleteModal(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>
            <div className="p-6">
              <p className="text-gray-700 dark:text-gray-300 mb-4">
                ¿Estás seguro de que quieres eliminar la llave con KCV <span className="font-mono font-bold">{selectedKey.kcv}</span>?
              </p>
              <div className="flex justify-end space-x-3">
                <button 
                  onClick={() => setShowDeleteModal(false)}
                  className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-300 hover:bg-gray-400 rounded-lg"
                >
                  Cancelar
                </button>
                <button 
                  onClick={() => deleteKey(selectedKey.kcv)}
                  className="px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded-lg"
                >
                  Eliminar
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
} 