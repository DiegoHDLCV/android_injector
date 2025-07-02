'use client';

import { useState, useEffect } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { useNotification } from '@/hooks/useNotification';
import apiClient from '@/lib/api-client';

export default function CeremonyPage() {
    const { user, isAuthenticated, isLoading: authLoading } = useAuth();
    const { showNotification } = useNotification();
    
    // Estados principales
    const [currentStep, setCurrentStep] = useState(1); // 1: Config, 2: Custodios, 3: Finalización
    const [numCustodians, setNumCustodians] = useState(2);
    const [currentCustodian, setCurrentCustodian] = useState(1);
    const [component, setComponent] = useState('');
    const [showComponent, setShowComponent] = useState(false);
    const [partialKCV, setPartialKCV] = useState('');
    const [finalKCV, setFinalKCV] = useState('');
    const [ceremonyLog, setCeremonyLog] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [ceremonyState, setCeremonyState] = useState(null);

    // Agregar entrada al log
    const addToLog = (message) => {
        const timestamp = new Date().toLocaleTimeString();
        setCeremonyLog(prev => prev + `[${timestamp}] ${message}\n`);
    };

    // Cargar estado de ceremonia al inicializar
    useEffect(() => {
        if (isAuthenticated) {
            loadCeremonyState();
        }
    }, [isAuthenticated]);

    const loadCeremonyState = async () => {
        try {
            const ceremonyApi = await apiClient.ceremony();
            const state = await ceremonyApi.getState();
            setCeremonyState(state);
            
            if (state.status === 'in_progress') {
                setCurrentStep(2);
                setNumCustodians(state.num_custodians);
                setCurrentCustodian(state.components_received + 1);
                addToLog(`Ceremonia en progreso: ${state.components_received}/${state.num_custodians} componentes recibidos`);
            } else if (state.status === 'completed') {
                setCurrentStep(3);
                setFinalKCV(state.final_kcv);
                addToLog('Ceremonia completada previamente');
            } else {
                addToLog('Sistema listo para nueva ceremonia');
            }
        } catch (error) {
            console.error('Error cargando estado de ceremonia:', error);
            addToLog('Error al cargar estado de ceremonia');
        }
    };

    const startCeremony = async (e) => {
        e.preventDefault();
        setIsLoading(true);

        try {
            const ceremonyApi = await apiClient.ceremony();
            const result = await ceremonyApi.start({ num_custodians: numCustodians });
            
            setCeremonyState(result);
            setCurrentStep(2);
            setCurrentCustodian(1);
            
            addToLog(`Ceremonia iniciada con ${numCustodians} custodios`);
            showNotification('Ceremonia iniciada', 'Puede proceder con el primer custodio', 'success');
        } catch (error) {
            console.error('Error iniciando ceremonia:', error);
            addToLog(`Error: ${error.message}`);
            showNotification('Error', error.message, 'error');
        } finally {
            setIsLoading(false);
        }
    };

    const addComponentToCeremony = async (e) => {
        e.preventDefault();
        if (!component.trim()) return;

        setIsLoading(true);

        try {
            const ceremonyApi = await apiClient.ceremony();
            const result = await ceremonyApi.addComponent(component.trim());
            
            // Verificar si el backend devolvió un error
            if (result.error) {
                addToLog(`Error: ${result.error}`);
                showNotification('Error', result.error, 'error');
                return;
            }
            
            setPartialKCV(result.kcv);
            addToLog(`Custodio ${currentCustodian}: Componente agregado. KCV: ${result.kcv}`);
            showNotification('Componente verificado', `KCV: ${result.kcv}`, 'success');
            
            // Limpiar el campo después de agregar pero NO resetear showComponent
            setComponent('');
            // NO limpiar partialKCV aquí - se necesita para mostrar la verificación
        } catch (error) {
            console.error('Error agregando componente:', error);
            addToLog(`Error: ${error.message}`);
            showNotification('Error', error.message, 'error');
        } finally {
            setIsLoading(false);
        }
    };

    const nextCustodian = () => {
        if (currentCustodian < numCustodians) {
            setCurrentCustodian(currentCustodian + 1);
            setPartialKCV(''); // Limpiar KCV solo al avanzar al siguiente custodian
            setShowComponent(false); // Resetear visibilidad del componente
            addToLog(`Preparando para custodio ${currentCustodian + 1}`);
        }
    };

    const finalizeCeremony = async () => {
        setIsLoading(true);

        try {
            const ceremonyApi = await apiClient.ceremony();
            const result = await ceremonyApi.finalize();
            
            // Verificar si el backend devolvió un error
            if (result.error) {
                addToLog(`Error: ${result.error}`);
                showNotification('Error', result.error, 'error');
                return;
            }
            
            setFinalKCV(result.kcv);
            setCurrentStep(3);
            
            addToLog(`Ceremonia finalizada. KCV final: ${result.kcv}`);
            addToLog('Llave guardada en el almacén seguro');
            showNotification('¡Ceremonia completada!', `Llave generada con KCV: ${result.kcv}`, 'success');
        } catch (error) {
            console.error('Error finalizando ceremonia:', error);
            addToLog(`Error: ${error.message}`);
            showNotification('Error', error.message, 'error');
        } finally {
            setIsLoading(false);
        }
    };

    const cancelCeremony = async () => {
        try {
            const ceremonyApi = await apiClient.ceremony();
            await ceremonyApi.cancel();
            
            // Resetear estados
            setCurrentStep(1);
            setCurrentCustodian(1);
            setComponent('');
            setPartialKCV('');
            setFinalKCV('');
            setCeremonyState(null);
            
            addToLog('Ceremonia cancelada por el usuario');
            showNotification('Ceremonia cancelada', 'La ceremonia ha sido cancelada', 'info');
        } catch (error) {
            console.error('Error cancelando ceremonia:', error);
            addToLog(`Error: ${error.message}`);
            showNotification('Error', error.message, 'error');
        }
    };

    const newCeremony = async () => {
        await cancelCeremony();
    };

    // Mostrar loading mientras se autentica
    if (authLoading) {
        return (
            <div className="min-h-screen bg-gray-900 flex items-center justify-center">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-400 mx-auto mb-4"></div>
                    <p className="text-gray-300">Verificando autenticación...</p>
                </div>
            </div>
        );
    }

    // Verificar autenticación y permisos de admin
    if (!isAuthenticated) {
        return null;
    }

    if (user?.role !== 'admin') {
        return (
            <div className="min-h-screen bg-gray-900 flex items-center justify-center">
                <div className="text-center">
                    <div className="text-red-400 text-6xl mb-4">🚫</div>
                    <h2 className="text-2xl font-bold text-white mb-2">Acceso Denegado</h2>
                    <p className="text-gray-400">Solo los administradores pueden acceder a la ceremonia criptográfica.</p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-900">
            {/* Header */}
            <header className="bg-gray-800 border-b border-gray-700">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex items-center justify-between h-16">
                        <div className="flex items-center">
                            <h1 className="text-xl font-bold text-white">
                                Ceremonia Criptográfica
                            </h1>
                        </div>
                        <div className="flex items-center space-x-4">
                            <div className="text-sm">
                                <div className="text-white font-medium">{user?.username}</div>
                                <div className="text-gray-400 text-xs">{user?.role}</div>
                            </div>
                        </div>
                    </div>
                </div>
            </header>

            {/* Main Content */}
            <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                <div className="bg-gray-800 text-white rounded-lg shadow-lg p-6">
                    {/* Header con ícono */}
                    <h2 className="flex items-center text-3xl font-bold mb-6 border-b border-gray-600 pb-4 text-green-400">
                        <svg className="h-10 w-10 mr-4" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                            <path fillRule="evenodd" d="M11.533 2.06a1.5 1.5 0 0 0-3.066 0l-1.833 6.874h6.732l-1.833-6.874Zm-2.6-1.55a3 3 0 0 1 5.4 1.08l2.25 8.438a.75.75 0 0 1-.68.982H3.698a.75.75 0 0 1-.68-.982l2.25-8.437a3 3 0 0 1 5.4-1.08ZM4.5 13.25a.75.75 0 0 1 .75-.75h9.5a.75.75 0 0 1 0 1.5h-9.5a.75.75 0 0 1-.75-.75Zm0 3.5a.75.75 0 0 1 .75-.75h9.5a.75.75 0 0 1 0 1.5h-9.5a.75.75 0 0 1-.75-.75Z" clipRule="evenodd" />
                        </svg>
                        Ceremonia de Inyección de Llaves
                    </h2>

                    {/* Indicador de Progreso */}
                    <ProgressIndicator currentStep={currentStep} />

                    {/* Estado de la Ceremonia */}
                    <CeremonyStatus ceremonyState={ceremonyState} />

                    {/* Paso 1: Configuración */}
                    {currentStep === 1 && (
                        <ConfigurationStep
                            numCustodians={numCustodians}
                            setNumCustodians={setNumCustodians}
                            onStart={startCeremony}
                            isLoading={isLoading}
                        />
                    )}

                    {/* Paso 2: Custodios */}
                    {currentStep === 2 && (
                        <CustodianStep
                            currentCustodian={currentCustodian}
                            totalCustodians={numCustodians}
                            component={component}
                            setComponent={setComponent}
                            showComponent={showComponent}
                            setShowComponent={setShowComponent}
                            partialKCV={partialKCV}
                            onAddComponent={addComponentToCeremony}
                            onNextCustodian={nextCustodian}
                            onFinalize={finalizeCeremony}
                            onCancel={cancelCeremony}
                            isLoading={isLoading}
                        />
                    )}

                    {/* Paso 3: Finalización */}
                    {currentStep === 3 && (
                        <FinalizationStep
                            finalKCV={finalKCV}
                            onNewCeremony={newCeremony}
                        />
                    )}

                    {/* Log de la Ceremonia */}
                    <div className="mt-8">
                        <label htmlFor="ceremony-log" className="block text-sm font-medium mb-2 text-gray-300">
                            Log de Operaciones:
                        </label>
                        <textarea
                            id="ceremony-log"
                            value={ceremonyLog}
                            readOnly
                            className="w-full h-40 bg-gray-900 border border-gray-600 rounded-lg p-3 font-mono text-sm text-gray-300 resize-none"
                        />
                    </div>
                </div>
            </main>
        </div>
    );
}

function ProgressIndicator({ currentStep }) {
    const steps = [
        { number: 1, name: 'Configuración' },
        { number: 2, name: 'Custodios' },
        { number: 3, name: 'Finalización' }
    ];

    return (
        <div className="mb-8">
            <div className="flex items-center justify-between">
                {steps.map((step, index) => (
                    <div key={step.number} className="flex items-center">
                        <div className="flex items-center">
                            <div className={`w-12 h-12 rounded-full flex items-center justify-center font-bold text-lg ${
                                step.number <= currentStep ? 'bg-green-500' : 'bg-gray-600'
                            }`}>
                                {step.number}
                            </div>
                            <span className="ml-3 font-semibold">{step.name}</span>
                        </div>
                        {index < steps.length - 1 && (
                            <div className="flex-1 h-1 bg-gray-600 mx-4"></div>
                        )}
                    </div>
                ))}
            </div>
        </div>
    );
}

function CeremonyStatus({ ceremonyState }) {
    return (
        <div className="mb-6 p-6 bg-gray-700 rounded-lg shadow-inner">
            <h3 className="flex items-center font-bold text-xl mb-3 text-blue-400">
                <svg className="h-7 w-7 mr-3" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M18 10a8 8 0 1 0-16 0 8 8 0 0 0 16 0Zm-7-4a1 1 0 1 0-2 0 1 1 0 0 0 2 0ZM9 9a.75.75 0 0 0 0 1.5h.253a.25.25 0 0 1 .244.304l-.459 2.066A.75.75 0 0 0 9 13.75a.75.75 0 0 0 .75.75h.5a.75.75 0 0 0 .75-.75v-.75a.75.75 0 0 0-.75-.75h-.253a.25.25 0 0 1-.244-.304l.459-2.066A.75.75 0 0 0 11 9.25a.75.75 0 0 0-.75-.75H9Z" clipRule="evenodd" />
                </svg>
                Estado Actual
            </h3>
            <div className="text-lg">
                {ceremonyState ? (
                    <span className={`${
                        ceremonyState.status === 'completed' ? 'text-green-400' :
                        ceremonyState.status === 'in_progress' ? 'text-yellow-400' :
                        'text-gray-400'
                    }`}>
                        {ceremonyState.status === 'completed' ? '✅ Ceremonia completada' :
                         ceremonyState.status === 'in_progress' ? '🔄 Ceremonia en progreso' :
                         '⏳ Esperando inicio de ceremonia'}
                    </span>
                ) : (
                    <span className="text-gray-400">Cargando estado...</span>
                )}
            </div>
        </div>
    );
}

function ConfigurationStep({ numCustodians, setNumCustodians, onStart, isLoading }) {
    return (
        <div className="mb-6">
            <div className="bg-gray-700 p-6 rounded-lg shadow-lg">
                <h3 className="font-bold text-2xl mb-6 text-green-400">Paso 1: Configuración Inicial</h3>
                <form onSubmit={onStart}>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                        <div>
                            <label className="block text-sm font-medium mb-2">Número de Custodios</label>
                            <div className="flex items-center space-x-4">
                                <div className="flex bg-gray-800 rounded-lg p-1">
                                    <button
                                        type="button"
                                        onClick={() => setNumCustodians(2)}
                                        className={`px-6 py-3 rounded-md font-semibold transition-all ${
                                            numCustodians === 2 ? 'bg-green-600 text-white' : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
                                        }`}
                                    >
                                        2
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setNumCustodians(3)}
                                        className={`px-6 py-3 rounded-md font-semibold transition-all ${
                                            numCustodians === 3 ? 'bg-green-600 text-white' : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
                                        }`}
                                    >
                                        3
                                    </button>
                                </div>
                                <span className="text-sm text-gray-400">
                                    <svg className="inline h-4 w-4 mr-1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                        <path fillRule="evenodd" d="M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Zm-7-4a1 1 0 1 1-2 0 1 1 0 0 1 2 0ZM9 9a.75.75 0 0 0 0 1.5h.253a.25.25 0 0 1 .244.304l-.459 2.066A.75.75 0 0 0 9 13.75a.75.75 0 0 0 .75.75h.5a.75.75 0 0 0 .75-.75v-.75a.75.75 0 0 0-.75-.75h-.253a.25.25 0 0 1-.244-.304l.459-2.066A.75.75 0 0 0 11 9.25a.75.75 0 0 0-.75-.75H9Z" clipRule="evenodd" />
                                    </svg>
                                    Recomendado: 3
                                </span>
                            </div>
                        </div>
                        <div>
                            <label className="block text-sm font-medium mb-2">Configuración de Llave</label>
                            <div className="bg-gray-800 border border-gray-600 rounded-lg p-4">
                                <div className="flex items-center mb-2">
                                    <svg className="w-5 h-5 text-green-400 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                        <path fillRule="evenodd" d="M16.704 4.153a.75.75 0 0 1 .143 1.052l-8 10.5a.75.75 0 0 1-1.127.075l-4.5-4.5a.75.75 0 0 1 1.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 0 1 1.05-.143Z" clipRule="evenodd" />
                                    </svg>
                                    <span className="font-medium text-green-400">Llave Universal</span>
                                </div>
                                <p className="text-sm text-gray-300">
                                    Se generará una llave genérica sin esquema específico. El propósito y tipo se definirán posteriormente en los <strong>Perfiles de Comercio</strong>.
                                </p>
                            </div>
                        </div>
                        <div className="bg-blue-900 bg-opacity-30 border border-blue-500 rounded-lg p-4">
                            <div className="flex items-center mb-2">
                                <svg className="w-5 h-5 text-blue-400 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                    <path fillRule="evenodd" d="M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Zm-7-4a1 1 0 1 1-2 0 1 1 0 0 1 2 0ZM9 9a.75.75 0 0 0 0 1.5h.253a.25.25 0 0 1 .244.304l-.459 2.066A.75.75 0 0 0 9 13.75a.75.75 0 0 0 .75.75h.5a.75.75 0 0 0 .75-.75v-.75a.75.75 0 0 0-.75-.75h-.253a.25.25 0 0 1-.244-.304l.459-2.066A.75.75 0 0 0 11 9.25a.75.75 0 0 0-.75-.75H9Z" clipRule="evenodd" />
                                </svg>
                                <span className="text-sm font-medium text-blue-400">Información:</span>
                            </div>
                            <p className="text-xs text-blue-200">
                                Las llaves se guardan <strong>sin slot específico</strong>. El slot y propósito se asignarán posteriormente desde los <strong>Perfiles de Comercio</strong>.
                            </p>
                        </div>
                    </div>
                    <div className="flex justify-end mt-8">
                        <button
                            type="submit"
                            disabled={isLoading}
                            className="flex items-center justify-center bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white px-8 py-3 text-lg rounded-lg font-bold tracking-wide transition-all duration-200 shadow-lg"
                        >
                            {isLoading ? (
                                <>
                                    <svg className="animate-spin h-6 w-6 mr-2" fill="none" viewBox="0 0 24 24">
                                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                    </svg>
                                    Iniciando...
                                </>
                            ) : (
                                <>
                                    <svg className="h-6 w-6 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM6.75 9.25a.75.75 0 0 0 0 1.5h4.59l-2.1 1.95a.75.75 0 0 0 1.02 1.1l3.5-3.25a.75.75 0 0 0 0-1.1l-3.5-3.25a.75.75 0 1 0-1.02 1.1l2.1 1.95H6.75Z" clipRule="evenodd" />
                                    </svg>
                                    Iniciar Ceremonia
                                </>
                            )}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

function CustodianStep({ 
    currentCustodian, 
    totalCustodians, 
    component, 
    setComponent, 
    showComponent, 
    setShowComponent, 
    partialKCV, 
    onAddComponent, 
    onNextCustodian, 
    onFinalize, 
    onCancel, 
    isLoading 
}) {
    const isLastCustodian = currentCustodian === totalCustodians;

    return (
        <div className="mb-6">
            <div className="bg-gray-700 p-6 rounded-lg shadow-lg">
                <h3 className="font-bold text-2xl mb-6 text-blue-400">
                    Paso 2: Ingreso de Componentes - Custodio {currentCustodian} de {totalCustodians}
                </h3>
                <div className="bg-yellow-900 border-l-4 border-yellow-500 p-4 mb-6">
                    <div className="flex">
                        <div className="flex-shrink-0">
                            <svg className="h-5 w-5 text-yellow-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                <path fillRule="evenodd" d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495ZM10 5a.75.75 0 0 1 .75.75v3.5a.75.75 0 0 1-1.5 0v-3.5A.75.75 0 0 1 10 5Zm0 9a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z" clipRule="evenodd" />
                            </svg>
                        </div>
                        <div className="ml-3">
                            <p className="text-sm text-yellow-200">
                                <strong>Importante:</strong> El custodio actual debe ingresar su componente de llave de forma privada. Los demás custodios no deben ver este componente.
                            </p>
                        </div>
                    </div>
                </div>
                <form onSubmit={onAddComponent}>
                    <label className="block">
                        <span className="text-sm font-medium mb-2 block">Componente de Llave (Hex)</span>
                        <div className="relative">
                            <input
                                type={showComponent ? 'text' : 'password'}
                                value={component}
                                onChange={(e) => setComponent(e.target.value)}
                                className="w-full px-4 py-3 bg-gray-800 border border-gray-600 text-white pr-12 text-lg font-mono rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all duration-200"
                                required
                                autoComplete="off"
                                placeholder="Ingrese el componente hex..."
                            />
                            <button
                                type="button"
                                onClick={() => setShowComponent(!showComponent)}
                                className="absolute inset-y-0 right-0 px-4 flex items-center text-gray-400 hover:text-white transition-colors"
                            >
                                {showComponent ? (
                                    <svg className="h-6 w-6" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                        <path fillRule="evenodd" d="M3.707 2.293a1 1 0 00-1.414 1.414l14 14a1 1 0 001.414-1.414l-1.473-1.473A10.014 10.014 0 0019.542 10C18.268 5.943 14.478 3 10 3a9.958 9.958 0 00-4.512 1.074l-1.78-1.781zm4.261 4.26l1.514 1.515a2.003 2.003 0 012.45 2.45l1.514 1.514a4 4 0 00-5.478-5.478z" clipRule="evenodd" />
                                        <path d="M12.454 16.697L9.75 13.992a4 4 0 01-3.742-3.741L2.335 6.578A9.98 9.98 0 00.458 10c1.274 4.057 5.065 7 9.542 7 .847 0 1.669-.105 2.454-.303z" />
                                    </svg>
                                ) : (
                                    <svg className="h-6 w-6" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                        <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
                                        <path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.022 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" />
                                    </svg>
                                )}
                            </button>
                        </div>
                    </label>
                    <div className="flex justify-between items-center mt-8">
                        <button
                            type="button"
                            onClick={onCancel}
                            className="flex items-center justify-center bg-red-700 hover:bg-red-800 text-white px-6 py-3 rounded-lg font-bold tracking-wide transition-all duration-200 shadow-lg"
                        >
                            <svg className="h-5 w-5 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM8.28 7.22a.75.75 0 0 0-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 1 0 1.06 1.06L10 11.06l1.72 1.72a.75.75 0 1 0 1.06-1.06L11.06 10l1.72-1.72a.75.75 0 0 0-1.06-1.06L10 8.94 8.28 7.22Z" clipRule="evenodd" />
                            </svg>
                            Cancelar Ceremonia
                        </button>
                        <button
                            type="submit"
                            disabled={isLoading || !component.trim()}
                            className="flex items-center justify-center bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-8 py-3 text-lg rounded-lg font-bold tracking-wide transition-all duration-200 shadow-lg"
                        >
                            {isLoading ? (
                                <>
                                    <svg className="animate-spin h-6 w-6 mr-2" fill="none" viewBox="0 0 24 24">
                                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                                    </svg>
                                    Verificando...
                                </>
                            ) : (
                                <>
                                    <svg className="h-6 w-6 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                        <path fillRule="evenodd" d="M16.704 4.153a.75.75 0 0 1 .143 1.052l-8 10.5a.75.75 0 0 1-1.127.075l-4.5-4.5a.75.75 0 0 1 1.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 0 1 1.05-.143Z" clipRule="evenodd" />
                                    </svg>
                                    Verificar KCV
                                </>
                            )}
                        </button>
                    </div>
                </form>

                {/* Verificación de KCV */}
                {partialKCV && (
                    <div className="mt-8 p-6 border-2 border-dashed border-blue-600 rounded-lg bg-gray-800">
                        <p className="text-lg font-semibold mb-3">KCV del Componente:</p>
                        <p className="text-4xl font-mono text-green-400 my-4 text-center bg-gray-900 p-4 rounded">
                            {partialKCV}
                        </p>
                        <p className="text-gray-300 text-sm mb-6">
                            <svg className="inline h-5 w-5 mr-1 text-yellow-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                <path fillRule="evenodd" d="M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Zm-7-4a1 1 0 1 1-2 0 1 1 0 0 1 2 0ZM9 9a.75.75 0 0 0 0 1.5h.253a.25.25 0 0 1 .244.304l-.459 2.066A.75.75 0 0 0 9 13.75a.75.75 0 0 0 .75.75h.5a.75.75 0 0 0 .75-.75v-.75a.75.75 0 0 0-.75-.75h-.253a.25.25 0 0 1-.244-.304l.459-2.066A.75.75 0 0 0 11 9.25a.75.75 0 0 0-.75-.75H9Z" clipRule="evenodd" />
                            </svg>
                            El custodio debe verificar que este KCV corresponde con su componente antes de continuar.
                        </p>
                        <div className="flex justify-end gap-4">
                            {!isLastCustodian ? (
                                <button
                                    type="button"
                                    onClick={onNextCustodian}
                                    className="flex items-center justify-center bg-green-600 hover:bg-green-700 text-white px-6 py-3 rounded-lg font-bold tracking-wide transition-all duration-200 shadow-lg"
                                >
                                    <svg className="h-5 w-5 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM6.75 9.25a.75.75 0 0 0 0 1.5h4.59l-2.1 1.95a.75.75 0 0 0 1.02 1.1l3.5-3.25a.75.75 0 0 0 0-1.1l-3.5-3.25a.75.75 0 1 0-1.02 1.1l2.1 1.95H6.75Z" clipRule="evenodd" />
                                    </svg>
                                    Siguiente Custodio
                                </button>
                            ) : (
                                <button
                                    type="button"
                                    onClick={onFinalize}
                                    className="flex items-center justify-center bg-green-600 hover:bg-green-700 text-white px-6 py-3 rounded-lg font-bold tracking-wide transition-all duration-200 shadow-lg"
                                >
                                    <svg className="h-5 w-5 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                                        <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm3.857-9.809a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.06 0l4.25-5.85Z" clipRule="evenodd" />
                                    </svg>
                                    Finalizar y Guardar Llave
                                </button>
                            )}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

function FinalizationStep({ finalKCV, onNewCeremony }) {
    return (
        <div className="mb-6">
            <div className="bg-green-800 p-6 rounded-lg shadow-lg">
                <h3 className="flex items-center font-bold text-2xl mb-6">
                    <svg className="h-8 w-8 mr-3" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm3.857-9.809a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.06 0l4.25-5.85Z" clipRule="evenodd" />
                    </svg>
                    Paso 3: ¡Ceremonia Completada!
                </h3>
                <p className="text-lg mb-4">La llave ha sido generada exitosamente mediante la combinación segura de todos los componentes.</p>
                <div className="bg-gray-900 p-6 rounded-lg">
                    <p className="text-xl font-semibold mb-2">KCV Final:</p>
                    <p className="font-mono text-4xl text-green-400 text-center">{finalKCV}</p>
                </div>
                <p className="text-gray-300 text-sm mt-4">
                    <svg className="inline h-5 w-5 mr-1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Zm-7-4a1 1 0 1 1-2 0 1 1 0 0 1 2 0ZM9 9a.75.75 0 0 0 0 1.5h.253a.25.25 0 0 1 .244.304l-.459 2.066A.75.75 0 0 0 9 13.75a.75.75 0 0 0 .75.75h.5a.75.75 0 0 0 .75-.75v-.75a.75.75 0 0 0-.75-.75h-.253a.25.25 0 0 1-.244-.304l.459-2.066A.75.75 0 0 0 11 9.25a.75.75 0 0 0-.75-.75H9Z" clipRule="evenodd" />
                    </svg>
                    La llave ha sido almacenada de forma segura. Puedes inyectarla desde la sección "Llaves Almacenadas".
                </p>
                <button
                    onClick={onNewCeremony}
                    className="mt-6 flex items-center justify-center bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-lg font-bold tracking-wide transition-all duration-200 shadow-lg"
                >
                    <svg className="h-5 w-5 mr-2" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M15.312 11.424a5.5 5.5 0 0 1-9.201 2.466l-.312-.311h2.433a.75.75 0 0 0 0-1.5H3.989a.75.75 0 0 0-.75.75v4.242a.75.75 0 0 0 1.5 0v-2.43l.31.31a7 7 0 0 0 11.712-3.138.75.75 0 0 0-1.449-.39Zm1.23-3.723a.75.75 0 0 0 .219-.53V2.929a.75.75 0 0 0-1.5 0v2.43l-.31-.31A7 7 0 0 0 3.239 8.188a.75.75 0 1 0 1.448.389A5.5 5.5 0 0 1 13.89 6.11l.311.31h-2.432a.75.75 0 0 0 0 1.5h4.243a.75.75 0 0 0 .53-.219Z" clipRule="evenodd" />
                    </svg>
                    Nueva Ceremonia
                </button>
            </div>
        </div>
    );
} 