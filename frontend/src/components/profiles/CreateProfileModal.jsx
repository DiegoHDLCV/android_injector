import React from 'react';
import { useNotification } from '@/hooks/useNotification';
import apiClient from '@/lib/api-client';

const CreateProfileModal = ({ 
  isOpen, 
  onClose, 
  profile, 
  formData, 
  setFormData, 
  availableKeys, 
  onSave 
}) => {
  const { showNotification } = useNotification();

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Validación básica
    if (!formData.name.trim()) {
      showNotification('Error', 'El nombre del perfil es requerido', 'error');
      return;
    }

    try {
      const profileData = {
        name: formData.name.trim(),
        description: formData.description.trim(),
        app_type: formData.appType,
        key_configurations: formData.keyConfigurations.map(config => ({
          usage: config.usage,
          key_type: config.keyType,
          slot: config.slot,
          selected_key: config.selectedKey,
          injection_method: config.injectionMethod || 'auto'
        }))
      };

      if (profile) {
        await apiClient.profiles().updateProfile(profile.id, profileData);
        showNotification('Éxito', 'Perfil actualizado correctamente', 'success');
      } else {
        await apiClient.profiles().createProfile(profileData);
        showNotification('Éxito', 'Perfil creado correctamente', 'success');
      }
      
      onSave();
    } catch (err) {
      showNotification('Error', `No se pudo guardar el perfil: ${err.message}`, 'error');
    }
  };

  const addKeyConfiguration = () => {
    setFormData({
      ...formData,
      keyConfigurations: [
        ...formData.keyConfigurations,
        {
          id: Date.now(),
          usage: '',
          keyType: '',
          slot: '',
          selectedKey: '',
          injectionMethod: 'auto'
        }
      ]
    });
  };

  const removeKeyConfiguration = (configId) => {
    setFormData({
      ...formData,
      keyConfigurations: formData.keyConfigurations.filter(config => config.id !== configId)
    });
  };

  const updateKeyConfiguration = (configId, field, value) => {
    setFormData({
      ...formData,
      keyConfigurations: formData.keyConfigurations.map(config =>
        config.id === configId ? { ...config, [field]: value } : config
      )
    });
  };

  const loadTemplate = (appType) => {
    let templateConfigs = [];
    
    switch (appType) {
      case 'Retail':
        templateConfigs = [
          { id: Date.now() + 1, usage: 'PIN', keyType: 'TDES', slot: '01', selectedKey: '', injectionMethod: 'auto' },
          { id: Date.now() + 2, usage: 'MAC', keyType: 'TDES', slot: '02', selectedKey: '', injectionMethod: 'auto' },
          { id: Date.now() + 3, usage: 'DATA', keyType: 'TDES', slot: '03', selectedKey: '', injectionMethod: 'auto' }
        ];
        break;
      case 'H2H':
        templateConfigs = [
          { id: Date.now() + 1, usage: 'KEK', keyType: 'TDES', slot: '00', selectedKey: '', injectionMethod: 'clear' },
          { id: Date.now() + 2, usage: 'MAC', keyType: 'TDES', slot: '01', selectedKey: '', injectionMethod: 'ktk' },
          { id: Date.now() + 3, usage: 'DATA', keyType: 'TDES', slot: '02', selectedKey: '', injectionMethod: 'ktk' }
        ];
        break;
      case 'ATM':
        templateConfigs = [
          { id: Date.now() + 1, usage: 'PIN', keyType: 'TDES', slot: '01', selectedKey: '', injectionMethod: 'auto' },
          { id: Date.now() + 2, usage: 'MAC', keyType: 'TDES', slot: '02', selectedKey: '', injectionMethod: 'auto' }
        ];
        break;
      default:
        break;
    }
    
    if (templateConfigs.length > 0) {
      setFormData({
        ...formData,
        keyConfigurations: templateConfigs
      });
      showNotification('Plantilla cargada', `Se cargaron ${templateConfigs.length} configuraciones predefinidas para ${appType}`, 'success');
    }
  };

  const getInjectionMethodBadge = (method) => {
    switch (method) {
      case 'auto':
        return <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs rounded-full">🧠 Automático</span>;
      case 'clear':
        return <span className="px-2 py-1 bg-green-100 text-green-800 text-xs rounded-full">🔓 En Claro</span>;
      case 'ktk':
        return <span className="px-2 py-1 bg-purple-100 text-purple-800 text-xs rounded-full">🔐 Con KTK</span>;
      case 'tr31':
        return <span className="px-2 py-1 bg-orange-100 text-orange-800 text-xs rounded-full">📦 TR-31</span>;
      default:
        return <span className="px-2 py-1 bg-gray-100 text-gray-800 text-xs rounded-full">⚙️ Manual</span>;
    }
  };

  return (
    <div className="fixed inset-0 bg-gray-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="relative w-full max-w-4xl max-h-[95vh] bg-gray-800 rounded-2xl shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between p-6 border-b border-gray-700">
          <h3 className="text-xl font-bold text-green-400">
            {profile ? 'Editar Perfil' : 'Crear Perfil'}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-white">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        
        <form onSubmit={handleSubmit} className="p-6 overflow-y-auto max-h-[calc(95vh-140px)]">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-gray-700 rounded-lg p-4">
              <h4 className="text-lg font-medium text-green-400 mb-4">📋 Información Básica</h4>
              
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-300 mb-2">Nombre del Perfil:</label>
                <input 
                  type="text" 
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  className="w-full bg-gray-600 border border-gray-500 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-green-400" 
                  placeholder="ej: Perfil Retail Principal"
                  required
                />
              </div>
              
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-300 mb-2">Descripción:</label>
                <textarea 
                  value={formData.description}
                  onChange={(e) => setFormData({...formData, description: e.target.value})}
                  className="w-full bg-gray-600 border border-gray-500 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-green-400 h-20 resize-none" 
                  placeholder="Describe el propósito de este perfil..."
                />
              </div>
              
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-300 mb-2">Tipo de Aplicación:</label>
                <select 
                  value={formData.appType}
                  onChange={(e) => setFormData({...formData, appType: e.target.value})}
                  className="w-full bg-gray-600 border border-gray-500 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-green-400"
                >
                  <option value="">Seleccionar tipo...</option>
                  <option value="Retail">🏪 Retail</option>
                  <option value="H2H">🔗 Host to Host (H2H)</option>
                  <option value="Posint">💳 POSINT</option>
                  <option value="ATM">🏧 ATM</option>
                  <option value="Custom">⚙️ Personalizado</option>
                </select>
              </div>
            </div>
            
            <div className="bg-gray-700 rounded-lg p-4">
              <div className="flex items-center justify-between mb-4">
                <h4 className="text-lg font-medium text-green-400">🔑 Configuración de Llaves</h4>
              </div>
              
              <div className="space-y-4">
                {formData.keyConfigurations.map((config) => (
                  <div key={config.id} className="border border-gray-600 rounded-lg p-3">
                    <div className="flex justify-between items-center mb-3">
                      <h5 className="text-sm font-medium text-white">Configuración de Llave</h5>
                      <button 
                        type="button"
                        onClick={() => removeKeyConfiguration(config.id)}
                        className="text-red-400 hover:text-red-300"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
                        </svg>
                      </button>
                    </div>
                    
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-medium text-gray-400 mb-1">Uso:</label>
                        <select 
                          value={config.usage}
                          onChange={(e) => updateKeyConfiguration(config.id, 'usage', e.target.value)}
                          className="w-full bg-gray-600 border border-gray-500 rounded px-2 py-1 text-white text-sm"
                        >
                          <option value="">Seleccionar...</option>
                          <option value="PIN">🔐 PIN</option>
                          <option value="MAC">🔒 MAC</option>
                          <option value="DATA">📊 DATA</option>
                          <option value="KEK">🗝️ KEK</option>
                        </select>
                      </div>
                      
                      <div>
                        <label className="block text-xs font-medium text-gray-400 mb-1">Tipo:</label>
                        <select 
                          value={config.keyType}
                          onChange={(e) => updateKeyConfiguration(config.id, 'keyType', e.target.value)}
                          className="w-full bg-gray-600 border border-gray-500 rounded px-2 py-1 text-white text-sm"
                        >
                          <option value="">Seleccionar...</option>
                          <option value="TDES">TDES</option>
                          <option value="AES">AES</option>
                        </select>
                      </div>
                      
                      <div>
                        <label className="block text-xs font-medium text-gray-400 mb-1">Slot:</label>
                        <input 
                          type="text" 
                          value={config.slot}
                          onChange={(e) => updateKeyConfiguration(config.id, 'slot', e.target.value)}
                          className="w-full bg-gray-600 border border-gray-500 rounded px-2 py-1 text-white text-sm"
                          placeholder="ej: 01"
                        />
                      </div>
                      
                      <div>
                        <label className="block text-xs font-medium text-gray-400 mb-1">Llave:</label>
                        <select 
                          value={config.selectedKey}
                          onChange={(e) => updateKeyConfiguration(config.id, 'selectedKey', e.target.value)}
                          className="w-full bg-gray-600 border border-gray-500 rounded px-2 py-1 text-white text-sm"
                        >
                          <option value="">Seleccionar llave...</option>
                          {availableKeys.map((key) => (
                            <option key={key.kcv} value={key.kcv}>
                              {key.kcv} (#{key.id})
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>
                  </div>
                ))}
                
                <button 
                  type="button"
                  onClick={addKeyConfiguration}
                  className="w-full inline-flex items-center justify-center px-3 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors border-2 border-dashed border-blue-500"
                >
                  <svg className="w-4 h-4 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm.75-11.25a.75.75 0 0 0-1.5 0v2.5h-2.5a.75.75 0 0 0 0 1.5h2.5v2.5a.75.75 0 0 0 1.5 0v-2.5h2.5a.75.75 0 0 0 0-1.5h-2.5v-2.5Z" clipRule="evenodd" />
                  </svg>
                  Agregar Configuración de Llave
                </button>
              </div>
            </div>
          </div>
          
          <div className="flex justify-end space-x-3 mt-6 pt-4 border-t border-gray-600">
            <button 
              type="button"
              onClick={onClose}
              className="bg-gray-600 hover:bg-gray-700 text-white font-medium py-2 px-4 rounded-lg transition-colors"
            >
              Cancelar
            </button>
            <button 
              type="submit"
              className="bg-green-600 hover:bg-green-700 text-white font-medium py-2 px-4 rounded-lg transition-colors"
            >
              {profile ? 'Actualizar Perfil' : 'Guardar Perfil'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateProfileModal; 