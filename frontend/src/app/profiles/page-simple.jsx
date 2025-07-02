'use client';

import { useState, useEffect } from 'react';

export default function ProfilesPageSimple() {
  console.log('[PROFILES_SIMPLE] 🔄 Componente renderizado');
  
  const [profiles, setProfiles] = useState([]);
  const [loading, setLoading] = useState(false);

  const loadProfiles = async () => {
    console.log('[PROFILES_SIMPLE] 📋 loadProfiles() llamado');
    setLoading(true);
    try {
      const response = await fetch('/profiles');
      const data = await response.json();
      console.log('[PROFILES_SIMPLE] ✅ Perfiles cargados:', data.profiles?.length || 0);
      setProfiles(data.profiles || []);
    } catch (err) {
      console.log('[PROFILES_SIMPLE] ❌ Error cargando perfiles:', err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    console.log('[PROFILES_SIMPLE] 🚀 useEffect inicial ejecutado');
    loadProfiles();
  }, []);

  return (
    <div className="min-h-screen bg-gray-900 text-white p-6">
      <h1 className="text-3xl font-bold text-green-400 mb-6">
        Perfiles - Versión Simple
      </h1>
      
      <button 
        onClick={loadProfiles}
        disabled={loading}
        className="bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white font-bold py-2 px-4 rounded mb-6"
      >
        {loading ? 'Cargando...' : 'Refrescar Perfiles'}
      </button>

      <div className="bg-gray-800 rounded-lg p-6">
        <h2 className="text-xl font-semibold mb-4">
          Perfiles encontrados: {profiles.length}
        </h2>
        
        {profiles.length === 0 ? (
          <p className="text-gray-400">No hay perfiles disponibles</p>
        ) : (
          <div className="space-y-4">
            {profiles.map((profile, index) => (
              <div key={profile.id || index} className="bg-gray-700 p-4 rounded">
                <h3 className="font-semibold">{profile.name}</h3>
                <p className="text-gray-300">{profile.description}</p>
                <span className="text-xs text-gray-400">
                  Tipo: {profile.app_type}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
} 