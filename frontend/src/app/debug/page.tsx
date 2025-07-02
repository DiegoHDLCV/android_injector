'use client';

import { useState, useEffect, useRef } from 'react';

export default function DebugPage() {
  const renderCount = useRef(0);
  renderCount.current += 1;
  
  const [counter, setCounter] = useState(0);
  
  console.log('[DEBUG_PAGE] 🔄 Render #', renderCount.current, '- Counter:', counter);
  
  useEffect(() => {
    console.log('[DEBUG_PAGE] 🚀 useEffect ejecutado - solo una vez');
  }, []);
  
  return (
    <div className="min-h-screen bg-gray-900 text-white p-6">
      <h1 className="text-3xl font-bold text-green-400 mb-6">
        Página de Debug
      </h1>
      
      <div className="bg-gray-800 rounded-lg p-6 space-y-4">
        <p className="text-lg">
          <span className="font-semibold">Render Count:</span> Ver logs del navegador
        </p>
        
        <p className="text-lg">
          <span className="font-semibold">Counter State:</span> {counter}
        </p>
        
        <button 
          onClick={() => setCounter(prev => prev + 1)}
          className="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"
        >
          Incrementar Counter
        </button>
        
        <div className="mt-6 p-4 bg-blue-900 bg-opacity-30 border border-blue-500 rounded-lg">
          <h3 className="font-semibold text-blue-300 mb-2">Instrucciones de Testing:</h3>
          <ol className="list-decimal list-inside space-y-1 text-sm text-blue-200">
            <li>Revisa los logs del navegador para ver el Render Count</li>
            <li>Si solo ves 1-2 renders y no aumenta constantemente = ✅ Next.js funciona</li>
            <li>Si al hacer clic en "Incrementar", el contador cambia = ✅ React funciona</li>
            <li>Si no hay errores de hidratación en consola = ✅ SSR funciona</li>
          </ol>
        </div>
      </div>
    </div>
  );
} 