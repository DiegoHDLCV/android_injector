import React, { useState } from 'react';
import { useNotification } from '@/hooks/useNotification';

const InjectProfileModal = ({ isOpen, onClose, profile, connectionStatus, onInjectionComplete }) => {
  const { showNotification } = useNotification();
  const [injectionType, setInjectionType] = useState('auto');
  const [isInjecting, setIsInjecting] = useState(false);
  const [injectionProgress, setInjectionProgress] = useState(0);

  if (!isOpen || !profile) return null;

  const keysToInject = profile.key_configurations?.filter(config => config.selectedKey) || [];

  const handleStartInjection = async () => {
    if (!connectionStatus?.is_connected) {
      showNotification('Error', 'No hay dispositivo conectado', 'error');
      return;
    }

    if (keysToInject.length === 0) {
      showNotification('Error', 'No hay llaves configuradas para inyectar', 'error');
      return;
    }

    setIsInjecting(true);
    setInjectionProgress(0);

    try {
      // Simular inyección progresiva
      for (let i = 0; i < keysToInject.length; i++) {
        setInjectionProgress(((i + 1) / keysToInject.length) * 100);
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      showNotification('Éxito', `Perfil inyectado completamente. ${keysToInject.length} llaves inyectadas.`, 'success');
      onInjectionComplete();
    } catch (error) {
      showNotification('Error', `Error durante la inyección: ${error.message}`, 'error');
    } finally {
      setIsInjecting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-gray-900/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="relative w-full max-w-4xl max-h-[95vh] bg-gray-800 rounded-2xl shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between p-6 border-b border-gray-700">
          <h3 className="text-xl font-bold text-green-400">
            🚀 Inyectar Perfil: {profile.name}
          </h3>
          <button onClick={onClose} disabled={isInjecting} className="text-gray-400 hover:text-white disabled:opacity-50">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        
        <div className="p-6 overflow-y-auto max-h-[calc(95vh-140px)]">
          {/* Estado de conexión */}
          <div className="mb-6">
            {connectionStatus?.is_connected ? (
              <div className="p-4 rounded-lg border-l-4 border-green-500 bg-green-900 bg-opacity-20">
                <div className="flex items-center">
                  <svg className="h-6 w-6 text-green-500 mr-3" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                  </svg>
                  <p className="text-sm font-medium text-green-200">
                    Dispositivo conectado: {connectionStatus.port || 'Puerto desconocido'}
                  </p>
                </div>
              </div>
            ) : (
              <div className="p-4 rounded-lg border-l-4 border-red-500 bg-red-900 bg-opacity-20">
                <div className="flex items-center">
                  <svg className="h-6 w-6 text-red-500 mr-3" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                  </svg>
                  <p className="text-sm font-medium text-red-200">No hay dispositivo conectado</p>
                </div>
              </div>
            )}
          </div>

          {/* Vista previa */}
          <div className="mb-6">
            <h4 className="text-lg font-medium text-green-400 mb-4">📋 Vista Previa de Inyección</h4>
            <div className="bg-gray-700 rounded-lg p-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <h5 className="text-sm font-medium text-gray-300 mb-2">Información del Perfil:</h5>
                  <div className="space-y-1 text-sm">
                    <p><span className="text-gray-400">Nombre:</span> <span className="text-white font-medium">{profile.name}</span></p>
                    <p><span className="text-gray-400">Tipo:</span> <span className="text-white">{profile.app_type}</span></p>
                    <p><span className="text-gray-400">Total de llaves:</span> <span className="text-green-400 font-bold">{keysToInject.length}</span></p>
                  </div>
                </div>
                <div>
                  <h5 className="text-sm font-medium text-gray-300 mb-2">Configuración:</h5>
                  <select 
                    value={injectionType}
                    onChange={(e) => setInjectionType(e.target.value)}
                    disabled={isInjecting}
                    className="w-full bg-gray-600 border border-gray-500 rounded px-2 py-1 text-white text-sm disabled:opacity-50"
                  >
                    <option value="auto">🧠 Automático</option>
                    <option value="ktk">🔐 Cifrada con KTK</option>
                    <option value="clear">🔓 En Claro</option>
                    <option value="tr31">📦 Formato TR-31</option>
                  </select>
                </div>
              </div>
            </div>
          </div>

          {/* Lista de llaves */}
          <div className="mb-6">
            <h4 className="text-lg font-medium text-green-400 mb-4">🔑 Llaves a Inyectar</h4>
            <div className="bg-gray-700 rounded-lg overflow-hidden">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-800 text-xs uppercase text-gray-400">
                  <tr>
                    <th className="py-2 px-4">Slot</th>
                    <th className="py-2 px-4">Uso</th>
                    <th className="py-2 px-4">KCV</th>
                    <th className="py-2 px-4">Tipo</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-600">
                  {keysToInject.map((config, index) => (
                    <tr key={index}>
                      <td className="py-2 px-4 font-mono">{config.slot}</td>
                      <td className="py-2 px-4">{config.usage}</td>
                      <td className="py-2 px-4 font-mono text-xs">{config.selectedKey?.slice(0, 8)}...</td>
                      <td className="py-2 px-4">{config.keyType}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Progreso */}
          {isInjecting && (
            <div className="mb-6">
              <h4 className="text-lg font-medium text-green-400 mb-4">⚡ Progreso de Inyección</h4>
              <div className="bg-gray-700 rounded-lg p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-medium text-gray-300">Progreso:</span>
                  <span className="text-sm font-bold text-green-400">{Math.round(injectionProgress)}%</span>
                </div>
                <div className="w-full bg-gray-600 rounded-full h-3">
                  <div 
                    className="h-3 bg-gradient-to-r from-green-500 to-green-600 rounded-full transition-all duration-500" 
                    style={{ width: `${injectionProgress}%` }}
                  />
                </div>
              </div>
            </div>
          )}
        </div>
        
        <div className="flex justify-end space-x-3 p-6 border-t border-gray-700">
          <button 
            onClick={onClose}
            disabled={isInjecting}
            className="bg-gray-600 hover:bg-gray-700 disabled:opacity-50 text-white font-medium py-2 px-4 rounded-lg transition-colors"
          >
            {isInjecting ? 'Inyectando...' : 'Cancelar'}
          </button>
          <button 
            onClick={handleStartInjection}
            disabled={isInjecting || !connectionStatus?.is_connected || keysToInject.length === 0}
            className="bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white font-medium py-2 px-4 rounded-lg transition-colors flex items-center"
          >
            {isInjecting ? (
              <>
                <svg className="animate-spin -ml-1 mr-3 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                </svg>
                Inyectando...
              </>
            ) : (
              <>
                <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
                Iniciar Inyección
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

export default InjectProfileModal; 