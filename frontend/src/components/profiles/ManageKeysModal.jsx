import React, { useState, useEffect } from 'react';
import { useNotification } from '@/hooks/useNotification';
import apiClient from '@/lib/api-client';

const ManageKeysModal = ({ isOpen, onClose, profile, availableKeys, onKeysUpdated }) => {
  const { showNotification } = useNotification();
  const [profileKeys, setProfileKeys] = useState([]);
  const [draggedKey, setDraggedKey] = useState(null);

  if (!isOpen || !profile) return null;

  useEffect(() => {
    if (profile) {
      setProfileKeys(profile.key_configurations || []);
    }
  }, [profile]);

  const handleDragStart = (e, key) => {
    setDraggedKey(key);
    e.dataTransfer.effectAllowed = 'move';
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  };

  const handleDrop = async (e, slotIndex) => {
    e.preventDefault();
    
    if (!draggedKey) return;

    try {
      // Actualizar la configuración del perfil
      const updatedKeys = [...profileKeys];
      if (updatedKeys[slotIndex]) {
        updatedKeys[slotIndex].selectedKey = draggedKey.kcv;
      }

      // Aquí puedes agregar la lógica para actualizar el perfil en el backend
      await apiClient.profiles().updateProfile(profile.id, {
        ...profile,
        key_configurations: updatedKeys
      });

      setProfileKeys(updatedKeys);
      showNotification('Éxito', 'Llave asignada correctamente', 'success');
      onKeysUpdated();
    } catch (error) {
      showNotification('Error', 'No se pudo asignar la llave: ' + error.message, 'error');
    }
    
    setDraggedKey(null);
  };

  const handleUnassignKey = async (slotIndex) => {
    try {
      const updatedKeys = [...profileKeys];
      if (updatedKeys[slotIndex]) {
        updatedKeys[slotIndex].selectedKey = '';
      }

      await apiClient.profiles().updateProfile(profile.id, {
        ...profile,
        key_configurations: updatedKeys
      });

      setProfileKeys(updatedKeys);
      showNotification('Éxito', 'Llave desasignada correctamente', 'success');
      onKeysUpdated();
    } catch (error) {
      showNotification('Error', 'No se pudo desasignar la llave: ' + error.message, 'error');
    }
  };

  const getUsageIcon = (usage) => {
    switch (usage?.toLowerCase()) {
      case 'pin': return '🔐';
      case 'mac': return '🔒';
      case 'data': return '📊';
      case 'kek': return '🗝️';
      default: return '🔑';
    }
  };

  return (
    <div className="fixed inset-0 bg-gray-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="relative w-full max-w-6xl max-h-[90vh] bg-gray-800 rounded-2xl shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between p-6 border-b border-gray-700">
          <h3 className="text-xl font-bold text-green-400">
            Gestionar Llaves del Perfil: {profile.name}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-white">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        
        <div className="p-6 overflow-y-auto max-h-[calc(90vh-140px)]">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Configuración del perfil */}
            <div className="bg-gray-700 rounded-lg p-4">
              <h4 className="text-lg font-medium text-green-400 mb-4">🔧 Configuración del Perfil</h4>
              
              {/* Instrucciones */}
              <div className="bg-blue-900 bg-opacity-30 border border-blue-500 rounded-lg p-3 mb-4">
                <div className="flex items-center mb-2">
                  <svg className="w-5 h-5 text-blue-400 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A.75.75 0 009 13.75a.75.75 0 00.75.75h.5a.75.75 0 00.75-.75v-.75a.75.75 0 00-.75-.75h-.253a.25.25 0 01-.244-.304l.459-2.066A.75.75 0 0011 9.25a.75.75 0 00-.75-.75H9z" clipRule="evenodd" />
                  </svg>
                  <span className="text-sm font-medium text-blue-400">Cómo usar:</span>
                </div>
                <div className="text-xs text-blue-200 space-y-1">
                  <p>• <strong>Arrastra</strong> llaves desde la derecha a los slots de la izquierda</p>
                  <p>• <strong>Haz clic en "Desasignar"</strong> para liberar un slot ocupado</p>
                </div>
              </div>
              
              <div className="space-y-3">
                {profileKeys.map((config, index) => {
                  const assignedKey = availableKeys.find(key => key.kcv === config.selectedKey);
                  return (
                    <div
                      key={index}
                      className={`border-2 border-dashed rounded-lg p-4 transition-all duration-200 ${
                        assignedKey ? 'border-green-500 bg-green-900/20' : 'border-gray-500 bg-gray-600/20'
                      }`}
                      onDragOver={handleDragOver}
                      onDrop={(e) => handleDrop(e, index)}
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center space-x-3">
                          <span className="text-2xl">{getUsageIcon(config.usage)}</span>
                          <div>
                            <h5 className="text-sm font-medium text-white">
                              {config.usage || 'Sin uso definido'} - Slot {config.slot || 'N/A'}
                            </h5>
                            <p className="text-xs text-gray-400">
                              Tipo: {config.keyType || 'No definido'}
                            </p>
                          </div>
                        </div>
                        
                        {assignedKey ? (
                          <div className="flex items-center space-x-2">
                            <div className="text-right">
                              <p className="text-sm font-medium text-green-400">
                                {assignedKey.kcv}
                              </p>
                              <p className="text-xs text-gray-400">
                                ID: {assignedKey.id}
                              </p>
                            </div>
                            <button
                              onClick={() => handleUnassignKey(index)}
                              className="text-red-400 hover:text-red-300 p-1"
                              title="Desasignar llave"
                            >
                              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
                              </svg>
                            </button>
                          </div>
                        ) : (
                          <div className="text-center text-gray-400">
                            <svg className="w-8 h-8 mx-auto mb-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
                            </svg>
                            <p className="text-xs">Arrastra una llave aquí</p>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
            
            {/* Llaves disponibles */}
            <div className="bg-gray-700 rounded-lg p-4">
              <h4 className="text-lg font-medium text-green-400 mb-4">📦 Llaves Disponibles</h4>
              <div className="max-h-96 overflow-y-auto space-y-2">
                {availableKeys.length === 0 ? (
                  <div className="text-center py-8">
                    <svg className="w-12 h-12 mx-auto text-gray-400 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1221 9z"></path>
                    </svg>
                    <p className="text-gray-400">No hay llaves disponibles</p>
                  </div>
                ) : (
                  availableKeys.map((key) => (
                    <div
                      key={key.kcv}
                      draggable
                      onDragStart={(e) => handleDragStart(e, key)}
                      className="bg-gray-600 border border-gray-500 rounded-lg p-3 cursor-grab active:cursor-grabbing hover:border-gray-400 transition-all duration-200 hover:shadow-lg"
                    >
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-medium text-white">
                            {key.kcv}
                          </p>
                          <p className="text-xs text-gray-400">
                            ID: {key.id} | Tipo: {key.key_type || 'TDES'}
                          </p>
                        </div>
                        <div className="flex items-center space-x-2">
                          <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"></path>
                          </svg>
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
        
        <div className="flex justify-end space-x-3 p-6 border-t border-gray-700">
          <button 
            onClick={onClose}
            className="bg-gray-600 hover:bg-gray-700 text-white font-medium py-2 px-4 rounded-lg transition-colors"
          >
            Cerrar
          </button>
        </div>
      </div>
    </div>
  );
};

export default ManageKeysModal; 