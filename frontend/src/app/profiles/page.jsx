'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { useNotification } from '@/hooks/useNotification';
import apiClient from '@/lib/api-client';
import ProfileCard from '@/components/profiles/ProfileCard';
import CreateProfileModal from '@/components/profiles/CreateProfileModal';
import ManageKeysModal from '@/components/profiles/ManageKeysModal';
import InjectProfileModal from '@/components/profiles/InjectProfileModal';

export default function ProfilesPage() {
  const renderCount = useRef(0);
  renderCount.current += 1;
  
  console.log('[PROFILES] 🔄 Render #', renderCount.current, '- timestamp:', Date.now());

  // Estados principales
  const [profiles, setProfiles] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedProfile, setSelectedProfile] = useState(null);
  const [availableKeys, setAvailableKeys] = useState([]);
  
  // Estados para modales
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showManageKeysModal, setShowManageKeysModal] = useState(false);
  const [showInjectModal, setShowInjectModal] = useState(false);
  
  // Estado para el formulario del modal
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    appType: '',
    keyConfigurations: []
  });
  
  // Hooks
  const { user, isAuthenticated, requireAuth, isAdmin } = useAuth();
  const { showNotification } = useNotification();

  console.log('[PROFILES] 🔍 User check - User:', user?.username, 'renderCount:', renderCount.current);

  // Cargar perfiles
  const loadProfiles = useCallback(async () => {
    try {
      setIsLoading(true);
      console.log('[PROFILES] 📋 Cargando perfiles...');
      
      const response = await apiClient.profiles().getProfiles();
      console.log('[PROFILES] 📊 Respuesta del servidor:', response);
      
      if (response && response.profiles) {
        setProfiles(response.profiles);
        console.log('[PROFILES] ✅ Perfiles cargados:', response.profiles.length);
      } else {
        console.log('[PROFILES] ⚠️ No se encontraron perfiles');
        setProfiles([]);
      }
    } catch (error) {
      console.error('[PROFILES] ❌ Error cargando perfiles:', error);
      showNotification('Error', 'No se pudieron cargar los perfiles', 'error');
      setProfiles([]);
    } finally {
      setIsLoading(false);
    }
  }, [showNotification]);

  // Cargar llaves disponibles
  const loadAvailableKeys = useCallback(async () => {
    try {
      console.log('[PROFILES] 🔑 Cargando llaves disponibles...');
      const response = await apiClient.storage().getKeys();
      if (response && response.keys) {
        setAvailableKeys(response.keys);
        console.log('[PROFILES] ✅ Llaves disponibles cargadas:', response.keys.length);
      } else {
        setAvailableKeys([]);
      }
    } catch (error) {
      console.error('[PROFILES] ❌ Error cargando llaves:', error);
      setAvailableKeys([]);
    }
  }, []);

  // Cargar perfiles y llaves al montar el componente
  useEffect(() => {
    console.log('[PROFILES] 🚀 useEffect inicial - cargando datos');
    loadProfiles();
    loadAvailableKeys();
  }, [loadProfiles, loadAvailableKeys]);

  // Verificar autenticación
  useEffect(() => {
    console.log('[PROFILES] 🔐 Verificando autenticación...');
    if (!requireAuth()) {
      console.log('[PROFILES] ❌ Usuario no autenticado, redirigiendo...');
      return;
    }
  }, [requireAuth]);

  // Handlers para modales
  const handleCreateProfile = () => {
    // Resetear formulario para nuevo perfil
    setFormData({
      name: '',
      description: '',
      appType: '',
      keyConfigurations: []
    });
    setSelectedProfile(null);
    setShowCreateModal(true);
  };

  const handleEditProfile = (profile) => {
    // Cargar datos del perfil en el formulario
    setFormData({
      name: profile.name || '',
      description: profile.description || '',
      appType: profile.app_type || '',
      keyConfigurations: profile.key_configurations || []
    });
    setSelectedProfile(profile);
    setShowCreateModal(true);
  };

  const handleDeleteProfile = async (profile) => {
    if (!window.confirm(`¿Estás seguro de que quieres eliminar el perfil "${profile.name}"?`)) {
      return;
    }

    try {
      await apiClient.profiles().deleteProfile(profile.id);
      showNotification('Éxito', `Perfil "${profile.name}" eliminado correctamente`, 'success');
      loadProfiles();
    } catch (error) {
      showNotification('Error', `No se pudo eliminar el perfil: ${error.message}`, 'error');
    }
  };

  const handleManageKeys = (profile) => {
    setSelectedProfile(profile);
    setShowManageKeysModal(true);
  };

  const handleInjectProfile = (profile) => {
    setSelectedProfile(profile);
    setShowInjectModal(true);
  };

  const handleProfileSaved = () => {
    loadProfiles();
    setShowCreateModal(false);
    setSelectedProfile(null);
    // Resetear formulario
    setFormData({
      name: '',
      description: '',
      appType: '',
      keyConfigurations: []
    });
  };

  const handleKeysUpdated = () => {
    loadProfiles();
    setShowManageKeysModal(false);
    setSelectedProfile(null);
  };

  const handleInjectionComplete = () => {
    setShowInjectModal(false);
    setSelectedProfile(null);
    loadProfiles();
  };

  // Helper para permisos
  const getUserPermissionsBadge = (profile) => {
    if (isAdmin()) {
      return (
        <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800">
          <svg className="w-3 h-3 mr-1" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M11.3 1.046A1 1 0 0112 2v5h4a1 1 0 01.82 1.573l-7 10A1 1 0 018 18v-5H4a1 1 0 01-.82-1.573l7-10a1 1 0 011.12-.38z" clipRule="evenodd" />
          </svg>
          Admin
        </span>
      );
    }
    return (
      <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
        <svg className="w-3 h-3 mr-1" fill="currentColor" viewBox="0 0 20 20">
          <path fillRule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clipRule="evenodd" />
        </svg>
        Usuario
      </span>
    );
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-green-400 mx-auto mb-4"></div>
          <p className="text-gray-300">Cargando perfiles...</p>
        </div>
      </div>
    );
  }

      return (
      <div className="min-h-screen bg-gray-900 text-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-bold text-green-400">Gestión de Perfiles</h1>
            <p className="text-gray-400 mt-2">
              Administra perfiles de aplicaciones y sus configuraciones de llaves
            </p>
          </div>
          
          <div className="flex items-center space-x-4">
            {getUserPermissionsBadge()}
            <button
              onClick={handleCreateProfile}
              className="inline-flex items-center px-6 py-3 bg-green-600 hover:bg-green-700 text-white font-medium rounded-lg transition-colors duration-200"
            >
              <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
              </svg>
              Crear Perfil
            </button>
          </div>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <div className="bg-gray-800 rounded-lg p-6">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <svg className="h-8 w-8 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                </svg>
              </div>
              <div className="ml-5 w-0 flex-1">
                <dl>
                  <dt className="text-sm font-medium text-gray-400 truncate">Total Perfiles</dt>
                  <dd className="text-lg font-medium text-white">{profiles.length}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="bg-gray-800 rounded-lg p-6">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <svg className="h-8 w-8 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1221 9z" />
                </svg>
              </div>
              <div className="ml-5 w-0 flex-1">
                <dl>
                  <dt className="text-sm font-medium text-gray-400 truncate">Perfiles Configurados</dt>
                  <dd className="text-lg font-medium text-white">
                    {profiles.filter(p => p.keys && p.keys.some(k => k.key_id)).length}
                  </dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="bg-gray-800 rounded-lg p-6">
            <div className="flex items-center">
              <div className="flex-shrink-0">
                <svg className="h-8 w-8 text-yellow-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
              </div>
              <div className="ml-5 w-0 flex-1">
                <dl>
                  <dt className="text-sm font-medium text-gray-400 truncate">Listos para Inyección</dt>
                  <dd className="text-lg font-medium text-white">
                    {profiles.filter(p => p.keys && p.keys.every(k => k.key_id)).length}
                  </dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        {/* Lista de perfiles */}
        {profiles.length === 0 ? (
          <div className="text-center py-12">
            <div className="mx-auto h-24 w-24 text-gray-400">
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
            </div>
            <h3 className="mt-4 text-lg font-medium text-gray-300">No hay perfiles configurados</h3>
            <p className="mt-2 text-gray-400">
              Comienza creando un nuevo perfil de aplicación.
            </p>
            <div className="mt-6">
              <button
                onClick={handleCreateProfile}
                className="inline-flex items-center px-6 py-3 bg-green-600 hover:bg-green-700 text-white font-medium rounded-lg transition-colors"
              >
                <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
                Crear Primer Perfil
              </button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
            {profiles.map((profile) => (
              <ProfileCard
                key={profile.id}
                profile={profile}
                currentUser={user}
                onEdit={() => handleEditProfile(profile)}
                onDelete={() => handleDeleteProfile(profile)}
                onManageKeys={() => handleManageKeys(profile)}
                onInject={() => handleInjectProfile(profile)}
                canEdit={true}
                canDelete={isAdmin()}
                canManageKeys={true}
                canInject={true}
                getUserPermissionsBadge={getUserPermissionsBadge}
              />
            ))}
          </div>
        )}
      </div>

      {/* Modales */}
      <CreateProfileModal
        isOpen={showCreateModal}
        onClose={() => {
          setShowCreateModal(false);
          setSelectedProfile(null);
          setFormData({
            name: '',
            description: '',
            appType: '',
            keyConfigurations: []
          });
        }}
        onSave={handleProfileSaved}
        profile={selectedProfile}
        formData={formData}
        setFormData={setFormData}
        availableKeys={availableKeys}
      />

      <ManageKeysModal
        isOpen={showManageKeysModal}
        onClose={() => {
          setShowManageKeysModal(false);
          setSelectedProfile(null);
        }}
        profile={selectedProfile}
        onKeysUpdated={handleKeysUpdated}
      />

      <InjectProfileModal
        isOpen={showInjectModal}
        onClose={() => {
          setShowInjectModal(false);
          setSelectedProfile(null);
        }}
        profile={selectedProfile}
        connectionStatus={{ is_connected: false }} // TODO: Get real connection status
        onInjectionComplete={handleInjectionComplete}
      />
    </div>
  );
} 