import React from 'react';

const ProfileCard = ({ 
  profile, 
  currentUser, 
  onEdit, 
  onDelete, 
  onManageKeys, 
  onInject, 
  canEdit, 
  canDelete, 
  canManageKeys, 
  canInject,
  getUserPermissionsBadge 
}) => {

  const getAppTypeIcon = (appType) => {
    switch (appType) {
      case 'Retail':
        return '🏪';
      case 'H2H':
        return '🔗';
      case 'Posint':
        return '💳';
      case 'ATM':
        return '🏧';
      default:
        return '⚙️';
    }
  };

  const getUsageIcon = (usage) => {
    switch (usage?.toLowerCase()) {
      case 'pin':
        return '🔐';
      case 'mac':
        return '🔒';
      case 'data':
        return '📊';
      case 'kek':
        return '🗝️';
      default:
        return '🔑';
    }
  };

  const getAppTypeBadgeColor = (appType) => {
    switch (appType) {
      case 'Retail':
        return 'bg-blue-100 text-blue-800';
      case 'H2H':
        return 'bg-purple-100 text-purple-800';
      case 'Posint':
        return 'bg-green-100 text-green-800';
      case 'ATM':
        return 'bg-yellow-100 text-yellow-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="bg-gray-800 rounded-xl p-6 border border-gray-700 hover:border-gray-600 transition-all duration-200 hover:shadow-lg">
      <div className="flex items-start justify-between">
        <div className="flex items-start space-x-4 flex-1">
          {/* Avatar/Icon del perfil */}
          <div className="w-14 h-14 bg-gradient-to-br from-purple-500 to-pink-600 rounded-xl flex items-center justify-center text-2xl shadow-lg">
            {getAppTypeIcon(profile.app_type)}
          </div>
          
          <div className="flex-1">
            {/* Header del perfil */}
            <div className="flex items-start justify-between mb-3">
              <div>
                <h3 className="text-xl font-bold text-white mb-1">{profile.name}</h3>
                <p className="text-gray-400 text-sm">{profile.description}</p>
              </div>
              {getUserPermissionsBadge(profile)}
            </div>
            
            {/* Información del perfil */}
            <div className="flex flex-wrap items-center gap-3 mb-4">
              <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium ${getAppTypeBadgeColor(profile.app_type)}`}>
                {getAppTypeIcon(profile.app_type)} {profile.app_type}
              </span>
              <span className="text-sm text-gray-400">
                {profile.key_configurations?.length || 0} configuraciones de llaves
              </span>
              {profile.created_by && (
                <span className="text-xs text-gray-500">
                  por {profile.created_by}
                </span>
              )}
              {profile.created_date && (
                <span className="text-xs text-gray-500">
                  {new Date(profile.created_date).toLocaleDateString('es-ES')}
                </span>
              )}
            </div>

            {/* Estado del perfil */}
            <div className="flex items-center space-x-4 mb-4">
              <div className="flex items-center">
                <div className={`w-2 h-2 rounded-full mr-2 ${
                  profile.key_configurations?.length > 0 ? 'bg-green-500' : 'bg-yellow-500'
                }`}></div>
                <span className="text-xs text-gray-400">
                  {profile.key_configurations?.length > 0 ? 'Configurado' : 'Pendiente configuración'}
                </span>
              </div>
              {profile.is_public && (
                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                  <svg className="w-3 h-3 mr-1" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
                    <path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" />
                  </svg>
                  Público
                </span>
              )}
            </div>
          </div>
        </div>
        
        {/* Botones de acción */}
        <div className="flex flex-col space-y-2 ml-4">
          {canInject && (
            <button 
              onClick={onInject}
              className="inline-flex items-center px-3 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-medium rounded-lg transition-colors"
              title="Inyectar perfil completo"
            >
              <svg className="w-4 h-4 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                <path d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" stroke="currentColor" strokeWidth="2" fill="none"/>
              </svg>
              Inyectar
            </button>
          )}
          
          <div className="flex space-x-1">
            {canManageKeys && (
              <button 
                onClick={onManageKeys}
                className="inline-flex items-center px-2 py-1 bg-purple-600 hover:bg-purple-700 text-white text-xs font-medium rounded transition-colors"
                title="Gestionar llaves del perfil"
              >
                <svg className="w-3 h-3 mr-1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1221 9z" clipRule="evenodd" />
                </svg>
                Llaves
              </button>
            )}
            
            {canEdit && (
              <button 
                onClick={onEdit}
                className="inline-flex items-center px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white text-xs font-medium rounded transition-colors"
                title="Editar perfil"
              >
                <svg className="w-3 h-3 mr-1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                  <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                </svg>
                Editar
              </button>
            )}
            
            {canDelete && (
              <button 
                onClick={onDelete}
                className="inline-flex items-center px-2 py-1 bg-red-600 hover:bg-red-700 text-white text-xs font-medium rounded transition-colors"
                title="Eliminar perfil"
              >
                <svg className="w-3 h-3 mr-1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M9 2a1 1 0 000 2h2a1 1 0 100-2H9z" clipRule="evenodd" />
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414L9 12.414l3.293-3.293a1 1 0 000-1.414z" clipRule="evenodd" />
                </svg>
                Eliminar
              </button>
            )}
          </div>
        </div>
      </div>
      
      {/* Mostrar configuraciones de llaves */}
      {profile.key_configurations && profile.key_configurations.length > 0 && (
        <div className="mt-6 pt-4 border-t border-gray-700">
          <h4 className="text-sm font-medium text-gray-300 mb-3 flex items-center">
            <svg className="w-4 h-4 mr-2 text-green-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M18 8a6 6 0 01-7.743 5.743L10 14l-1 1-1 1H6v2H4v2H2v-4l4.257-4.257A6 6 0 1118 8zm-6-4a1 1 0 100 2 2 2 0 012 2 1 1 0 102 0 4 4 0 00-4-4z" clipRule="evenodd" />
            </svg>
            Configuraciones de Llaves
          </h4>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {profile.key_configurations.map((config, index) => (
              <div key={index} className="bg-gray-700 rounded-lg p-3 border border-gray-600 hover:border-gray-500 transition-colors">
                <div className="flex items-center space-x-2 mb-2">
                  <span className="text-lg">{getUsageIcon(config.usage)}</span>
                  <span className="text-sm font-medium text-white">{config.usage || 'Sin uso definido'}</span>
                </div>
                <div className="text-xs text-gray-400 space-y-1">
                  <div className="flex justify-between">
                    <span className="font-medium">Tipo:</span>
                    <span>{config.keyType || 'No definido'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="font-medium">Slot:</span>
                    <span className="font-mono bg-gray-600 px-1 rounded">{config.slot || 'No asignado'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="font-medium">Llave:</span>
                    <span className="font-mono text-xs">
                      {config.selectedKey ? `${config.selectedKey.slice(0, 8)}...` : 'No asignada'}
                    </span>
                  </div>
                  {config.injectionMethod && (
                    <div className="flex justify-between">
                      <span className="font-medium">Método:</span>
                      <span className="text-green-400">{config.injectionMethod}</span>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default ProfileCard; 