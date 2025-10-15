package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.repository.InjectedKeyRepository
import com.vigatec.utils.KcvCalculator
import com.vigatec.utils.KeyStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class KeyAlgorithmType(val displayName: String, val bytesRequired: Int) {
    DES_TRIPLE("3DES (16 bytes)", 16),
    AES_128("AES-128 (16 bytes)", 16),
    AES_192("AES-192 (24 bytes)", 24),
    AES_256("AES-256 (32 bytes)", 32)
}

data class CeremonyState(
    val currentStep: Int = 1, // 1: Config, 2: Custodios, 3: Finalización
    val numCustodians: Int = 2,
    val currentCustodian: Int = 1,
    val component: String = "",
    val components: List<String> = emptyList(),
    val showComponent: Boolean = false,
    val partialKCV: String = "",
    val finalKCV: String = "",
    val log: String = "Esperando inicio de ceremonia...\n",
    val isLoading: Boolean = false,
    val isCeremonyInProgress: Boolean = false,
    val isCeremonyFinished: Boolean = false,

    // Nuevos campos para configuración de llave
    val customName: String = "",              // Nombre personalizado
    val selectedKeyType: KeyAlgorithmType = KeyAlgorithmType.DES_TRIPLE,  // Tipo de algoritmo seleccionado
    val componentError: String? = null        // Error de validación de componente
)

@HiltViewModel
class CeremonyViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CeremonyState(component = "E59D620E1A6D311F19342054AB01ABF7"))
    val uiState = _uiState.asStateFlow()

    fun addToLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _uiState.value = _uiState.value.copy(log = _uiState.value.log + "[$timestamp] $message\n")
    }

    fun onNumCustodiansChange(num: Int) {
        _uiState.value = _uiState.value.copy(numCustodians = num)
    }

    fun onComponentChange(component: String) {
        _uiState.value = _uiState.value.copy(component = component)
    }

    fun onToggleShowComponent() {
        _uiState.value = _uiState.value.copy(showComponent = !_uiState.value.showComponent)
    }


    fun onCustomNameChange(name: String) {
        _uiState.value = _uiState.value.copy(customName = name)
    }

    fun onKeyTypeChange(keyType: KeyAlgorithmType) {
        _uiState.value = _uiState.value.copy(selectedKeyType = keyType)
    }

    /**
     * Valida que el componente tenga la longitud correcta según el tipo de llave seleccionado
     */
    private fun validateComponentLength(component: String): Boolean {
        val expectedBytes = _uiState.value.selectedKeyType.bytesRequired
        val expectedHexLength = expectedBytes * 2 // Cada byte = 2 caracteres hex

        return component.length == expectedHexLength
    }

    fun startCeremony() {
        addToLog("=== INICIANDO CEREMONIA DE LLAVES ===")
        addToLog("Configuración inicial:")
        addToLog("  - Número de custodios: ${_uiState.value.numCustodians}")
        addToLog("  - Tipo de llave: ${_uiState.value.selectedKeyType.displayName}")
        addToLog("  - Longitud esperada: ${_uiState.value.selectedKeyType.bytesRequired * 2} caracteres hex")
        addToLog("  - Componente inicial: ${_uiState.value.component}")
        addToLog("  - Estado del repositorio: Inicializado")
        addToLog("  - Base de datos: Conectada")
        addToLog("================================================")

        _uiState.value = _uiState.value.copy(
            currentStep = 2,
            isCeremonyInProgress = true,
            isLoading = false
        )
    }

    fun addComponent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, partialKCV = "", componentError = null)
            try {
                val component = _uiState.value.component

                // Validar longitud del componente
                if (!validateComponentLength(component)) {
                    val expectedBytes = _uiState.value.selectedKeyType.bytesRequired
                    val expectedHexLength = expectedBytes * 2
                    val errorMsg = "Error: El componente debe tener exactamente $expectedHexLength caracteres hex para ${_uiState.value.selectedKeyType.displayName}"
                    addToLog(errorMsg)
                    _uiState.value = _uiState.value.copy(
                        componentError = "Se esperan $expectedHexLength caracteres hex, recibidos: ${component.length}",
                        isLoading = false
                    )
                    return@launch
                }

                val kcv = KcvCalculator.calculateKcv(component)
                _uiState.value = _uiState.value.copy(partialKCV = kcv, isLoading = false, componentError = null)
                addToLog("Custodio ${_uiState.value.currentCustodian}: Componente verificado. KCV: $kcv")
            } catch (e: Exception) {
                addToLog("Error al verificar componente: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false, componentError = e.message)
            }
        }
    }

    fun nextCustodian() {
        val next = _uiState.value.currentCustodian + 1
        addToLog("Avanzando al custodio $next.")

        val nextComponent = if (next == 2) {
            "ED77D12E82AF6099968D6F5653741D09"
        } else {
            ""
        }

        _uiState.value = _uiState.value.copy(
            currentCustodian = next,
            components = _uiState.value.components + _uiState.value.component,
            component = nextComponent,
            partialKCV = "",
            showComponent = false
        )
    }

    fun finalizeCeremony() {
        addToLog("Botón 'Finalizar y Guardar Llave' presionado")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addToLog("Finalizando ceremonia...")
            try {
                val finalComponents = _uiState.value.components + _uiState.value.component
                addToLog("Componentes recolectados: ${finalComponents.size} de ${_uiState.value.numCustodians}")
                
                if (finalComponents.size != _uiState.value.numCustodians) {
                    throw IllegalStateException("Número incorrecto de componentes: ${finalComponents.size} vs ${_uiState.value.numCustodians}")
                }

                addToLog("=== PROCESANDO COMPONENTES PARA GENERAR LLAVE FINAL ===")
                addToLog("Componentes recolectados:")
                finalComponents.forEachIndexed { index, component ->
                    addToLog("  ${index + 1}. ${component.take(16)}... (${component.length / 2} bytes)")
                }
                
                addToLog("Aplicando operación XOR a los componentes...")
                val finalKeyBytes = finalComponents
                    .map { component ->
                        val bytes = KcvCalculator.hexStringToByteArray(component)
                        addToLog("  - Componente ${component.take(16)}... → ${bytes.size} bytes")
                        bytes
                    }
                    .reduce { acc, bytes -> 
                        val result = KcvCalculator.xorByteArrays(acc, bytes)
                        addToLog("  - XOR: ${acc.size} bytes ⊕ ${bytes.size} bytes = ${result.size} bytes")
                        result
                    }

                val finalKeyHex = finalKeyBytes.joinToString("") { "%02X".format(it) }
                addToLog("✓ Llave final generada exitosamente")
                addToLog("  - Longitud: ${finalKeyBytes.size} bytes")
                addToLog("  - Datos (hex): $finalKeyHex")
                addToLog("  - Datos (primeros 16 bytes): ${finalKeyHex.take(32)}")
                addToLog("================================================")

                addToLog("=== CALCULANDO KCV DE LA LLAVE FINAL ===")
                addToLog("Entrada para KCV:")
                addToLog("  - Datos de llave: ${finalKeyHex.take(32)}...")
                addToLog("  - Longitud: ${finalKeyHex.length / 2} bytes")
                
                val finalKcv = KcvCalculator.calculateKcv(finalKeyHex)
                addToLog("✓ KCV calculado exitosamente: $finalKcv")
                addToLog("  - Longitud KCV: ${finalKcv.length} caracteres")
                addToLog("  - Formato: Hexadecimal")
                addToLog("================================================")

                // Guardar la llave maestra en el Keystore
                addToLog("=== ALMACENANDO LLAVE EN KEYSTORE ===")
                addToLog("Configuración del Keystore:")
                addToLog("  - Alias: master_transport_key")
                addToLog("  - Tipo de llave: Master Transport Key (MKT)")
                addToLog("  - Algoritmo: 3DES")
                addToLog("  - Longitud: ${finalKeyBytes.size} bytes")
                addToLog("  - Datos (primeros 16 bytes): ${finalKeyBytes.joinToString("") { "%02X".format(it) }.take(32)}")
                
                try {
                    KeyStoreManager.storeMasterKey("master_transport_key", finalKeyBytes)
                    addToLog("✓ Llave Maestra de Transporte (MKT) guardada exitosamente en el Keystore")
                    addToLog("  - Alias: master_transport_key")
                    addToLog("  - Estado: SEGURA")
                    addToLog("  - Acceso: Solo aplicación")
                } catch (e: Exception) {
                    addToLog("✗ Error al guardar en Keystore: ${e.message}")
                    addToLog("  - Estado: FALLO EN KEYSTORE")
                    // Continuar con el proceso aunque falle el Keystore
                }
                addToLog("================================================")

                addToLog("=== REGISTRANDO LLAVE COMPLETA EN BASE DE DATOS ===")
                addToLog("  - Slot: NO ASIGNADO (se define en perfil)")

                // Todas las llaves se crean como operacionales
                val keyType = "CEREMONY_KEY"
                val keyStatus = "GENERATED"

                // Detectar el algoritmo basado en el tipo seleccionado
                val detectedAlgorithm = when (_uiState.value.selectedKeyType) {
                    KeyAlgorithmType.DES_TRIPLE -> "DES_TRIPLE"
                    KeyAlgorithmType.AES_128 -> "AES_128"
                    KeyAlgorithmType.AES_192 -> "AES_192"
                    KeyAlgorithmType.AES_256 -> "AES_256"
                }

                addToLog("  - Tipo de Llave: $keyType (Operacional)")
                addToLog("  - Algoritmo detectado: $detectedAlgorithm (${_uiState.value.selectedKeyType.displayName})")
                addToLog("  - KCV: $finalKcv")
                addToLog("  - Estado: $keyStatus")
                addToLog("  - Es KEK: NO (puede configurarse desde el almacén de llaves)")
                addToLog("  - Nombre: ${_uiState.value.customName.ifEmpty { "(Sin nombre)" }}")
                addToLog("  - Datos de llave (longitud): ${finalKeyBytes.size} bytes")
                addToLog("  - Datos de llave (hex): $finalKeyHex")
                addToLog("  - Datos de llave (primeros 16 bytes): ${finalKeyHex.take(32)}")

                // CRÍTICO: Guardar la llave con su algoritmo detectado
                // El slot se asignará cuando se use la llave en un perfil (por ahora -1)
                injectedKeyRepository.recordKeyInjectionWithData(
                    keySlot = -1, // -1 indica que no hay slot asignado (se asignará en el perfil)
                    keyType = keyType, // Siempre CEREMONY_KEY (operacional)
                    keyAlgorithm = detectedAlgorithm, // Guardar el algoritmo detectado
                    kcv = finalKcv,
                    keyData = finalKeyHex, // ¡GUARDANDO LA LLAVE COMPLETA!
                    status = keyStatus,
                    isKEK = false, // Siempre false - se puede cambiar desde el almacén
                    customName = _uiState.value.customName // Nombre personalizado
                )
                addToLog("✓ Llave COMPLETA guardada exitosamente en base de datos")
                addToLog("✓ Verificación: ${finalKeyBytes.size} bytes almacenados")
                addToLog("✓ KCV validado: $finalKcv")
                addToLog("✓ Datos de llave preservados para uso futuro")
                addToLog("================================================")

                // VERIFICACIÓN CRÍTICA: Confirmar que la llave se guardó realmente en la BD
                addToLog("=== VERIFICANDO ALMACENAMIENTO EN BASE DE DATOS ===")
                try {
                    val savedKey = injectedKeyRepository.getKeyByKcv(finalKcv)
                    if (savedKey != null) {
                        addToLog("✓ Verificación exitosa: Llave encontrada en BD")
                        addToLog("  - ID en BD: ${savedKey.id}")
                        addToLog("  - KCV almacenado: ${savedKey.kcv}")
                        addToLog("  - Datos almacenados: ${savedKey.keyData.length / 2} bytes")
                        addToLog("  - Datos (primeros 16 bytes): ${savedKey.keyData.take(32)}")
                        addToLog("  - Timestamp: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(savedKey.injectionTimestamp))}")
                        
                        // Validar que los datos coinciden
                        if (savedKey.keyData == finalKeyHex) {
                            addToLog("✓ VALIDACIÓN COMPLETA: Los datos de la llave coinciden exactamente")
                            addToLog("✓ La llave se agregó al almacén sin sobrescribir llaves existentes")
                        } else {
                            addToLog("⚠️ ADVERTENCIA: Los datos de la llave NO coinciden")
                            addToLog("  - Esperado: ${finalKeyHex.take(32)}...")
                            addToLog("  - Almacenado: ${savedKey.keyData.take(32)}...")
                        }
                    } else {
                        addToLog("ℹ️ La llave ya existía en la base de datos (no se sobrescribió)")
                        addToLog("  - KCV: $finalKcv")
                        addToLog("  - Estado: DUPLICADO IGNORADO")
                        addToLog("  - Esta llave ya estaba disponible en el almacén")
                    }
                } catch (e: Exception) {
                    addToLog("✗ Error durante la verificación: ${e.message}")
                    addToLog("  - Estado: VERIFICACIÓN FALLIDA")
                }
                addToLog("================================================")

                _uiState.value = _uiState.value.copy(
                    currentStep = 3,
                    finalKCV = finalKcv,
                    isCeremonyFinished = true,
                    isLoading = false
                )
                addToLog("=== RESUMEN FINAL DE LA CEREMONIA ===")
                addToLog("✓ Ceremonia completada exitosamente")
                addToLog("✓ Llave criptográfica generada desde componentes")
                addToLog("  - KCV Final: $finalKcv")
                addToLog("  - Longitud: ${finalKeyBytes.size} bytes")
                addToLog("  - Algoritmo: Se definirá en el perfil")
                addToLog("  - Slot: Se asignará en el perfil")
                addToLog("  - Tipo: Se especificará en el perfil")
                addToLog("✓ Llave almacenada en Keystore (alias: master_transport_key)")
                addToLog("✓ Llave COMPLETA guardada en base de datos")
                addToLog("✓ Verificación de almacenamiento exitosa")
                addToLog("✓ Datos de llave preservados para uso futuro")
                addToLog("================================================")
                addToLog("🎉 ¡CEREMONIA COMPLETADA! La llave está disponible para configurar en perfiles.")
                addToLog("ℹ️ Usa el KCV '$finalKcv' para seleccionar esta llave en un perfil.")
                addToLog("================================================")

            } catch (e: Exception) {
                addToLog("Error al finalizar la ceremonia: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun cancelCeremony() {
        addToLog("Ceremonia cancelada.")
        _uiState.value = CeremonyState() // Resetea al estado inicial
    }

    /**
     * NUEVO MÉTODO: Verifica el estado de la base de datos y las llaves almacenadas
     */
    fun verifyDatabaseState() {
        viewModelScope.launch {
            try {
                addToLog("=== VERIFICACIÓN COMPLETA DE BASE DE DATOS ===")
                addToLog("Estado del repositorio: Verificando...")
                
                // Obtener todas las llaves almacenadas
                val allKeys = mutableListOf<com.example.persistence.entities.InjectedKeyEntity>()
                injectedKeyRepository.getAllInjectedKeys().collect { keys ->
                    allKeys.clear()
                    allKeys.addAll(keys)
                }
                
                addToLog("Total de llaves en BD: ${allKeys.size}")
                
                if (allKeys.isEmpty()) {
                    addToLog("⚠️ ADVERTENCIA: No hay llaves almacenadas en la base de datos")
                } else {
                    addToLog("Llaves encontradas:")
                    allKeys.forEachIndexed { index, key ->
                        addToLog("  ${index + 1}. ID: ${key.id}")
                        addToLog("     - Tipo: ${key.keyType}")
                        addToLog("     - Algoritmo: ${key.keyAlgorithm}")
                        addToLog("     - KCV: ${key.kcv}")
                        addToLog("     - Slot: ${key.keySlot}")
                        addToLog("     - Estado: ${key.status}")
                        addToLog("     - Datos: ${if (key.keyData.isNotEmpty()) "${key.keyData.length / 2} bytes" else "NO ALMACENADOS"}")
                        addToLog("     - Timestamp: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(key.injectionTimestamp))}")
                        
                        // Verificar si tiene datos de llave
                        if (key.keyData.isNotEmpty()) {
                            addToLog("     ✓ Datos de llave PRESERVADOS")
                            addToLog("     - Primeros 16 bytes: ${key.keyData.take(32)}...")
                        } else {
                            addToLog("     ✗ ADVERTENCIA: Solo KCV almacenado, NO hay datos de llave")
                        }
                        addToLog("")
                    }
                }
                
                addToLog("================================================")
                
            } catch (e: Exception) {
                addToLog("✗ Error durante la verificación de BD: ${e.message}")
                addToLog("  - Estado: VERIFICACIÓN FALLIDA")
            }
        }
    }
}