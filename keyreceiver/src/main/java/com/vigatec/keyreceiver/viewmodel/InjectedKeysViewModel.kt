// Archivo: com/vigatec/android_injector/viewmodel/InjectedKeysViewModel.kt

package com.vigatec.keyreceiver.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.manufacturer.KeySDKManager
import com.example.manufacturer.base.models.KeyType
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.repository.InjectedKeyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InjectedKeysViewModel @Inject constructor(
    private val injectedKeyRepository: InjectedKeyRepository
) : ViewModel() {

    private val TAG = "InjectedKeysViewModel"

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _keys = MutableStateFlow<List<InjectedKeyEntity>>(emptyList())
    val keys: StateFlow<List<InjectedKeyEntity>> = _keys.asStateFlow()

    private val _showDeleteModal = MutableStateFlow(false)
    val showDeleteModal: StateFlow<Boolean> = _showDeleteModal.asStateFlow()

    private val _selectedKeyForDeletion = MutableStateFlow<InjectedKeyEntity?>(null)
    val selectedKeyForDeletion: StateFlow<InjectedKeyEntity?> = _selectedKeyForDeletion.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    // Estados para filtros
    private val _filterAlgorithm = MutableStateFlow("Todos")
    val filterAlgorithm: StateFlow<String> = _filterAlgorithm.asStateFlow()

    private val _filterStatus = MutableStateFlow("Todos")
    val filterStatus: StateFlow<String> = _filterStatus.asStateFlow()

    private val _filterKTKType = MutableStateFlow("Todas")
    val filterKTKType: StateFlow<String> = _filterKTKType.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _filteredKeys = MutableStateFlow<List<InjectedKeyEntity>>(emptyList())
    val filteredKeys: StateFlow<List<InjectedKeyEntity>> = _filteredKeys.asStateFlow()

    init {
        loadKeys()
    }

    private fun loadKeys() {
        viewModelScope.launch {
            _loading.value = true
            try {
                Log.d(TAG, "=== CARGANDO LLAVES DESDE BD ===")
                injectedKeyRepository.getAllInjectedKeys()
                    .collect { keys ->
                        Log.d(TAG, "Llaves cargadas desde BD: ${keys.size}")
                        keys.forEachIndexed { index, key ->
                            Log.d(TAG, "BD Llave $index: Slot=${key.keySlot}, Tipo=${key.keyType}, isKEK=${key.isKEK}, kekType='${key.kekType}', Art=${key.keyAlgorithm}, KCV=${key.kcv}, Status=${key.status}")
                        }
                        
                        // Log especial para KTKs
                        val ktks = keys.filter { it.isKEK && it.kekType == "KEK_TRANSPORT" }
                        Log.d(TAG, "=== ANÁLISIS ESPECIAL DE KTKs ===")
                        Log.d(TAG, "Total de llaves: ${keys.size}")
                        Log.d(TAG, "KTKs encontradas: ${ktks.size}")
                        ktks.forEachIndexed { index, ktk ->
                            Log.d(TAG, "KTK $index: Slot=${ktk.keySlot}, KCV=${ktk.kcv}, kekType='${ktk.kekType}', isKEK=${ktk.isKEK}")
                        }
                        Log.d(TAG, "=== FIN ANÁLISIS KTKs ===")
                        
                        _keys.value = keys
                        applyFilters() // Aplicar filtros cuando se cargan las llaves
                        _loading.value = false
                        Log.d(TAG, "=== FIN CARGA DE LLAVES ===")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error al cargar las llaves", e)
                _snackbarMessage.emit("Error al cargar las llaves: ${e.message}")
                _loading.value = false
            }
        }
    }

    fun refreshKeys() {
        loadKeys()
    }

    fun onDeleteKey(key: InjectedKeyEntity) {
        _selectedKeyForDeletion.value = key
        _showDeleteModal.value = true
    }

    fun confirmDeleteKey() {
        viewModelScope.launch {
            try {
                val key = _selectedKeyForDeletion.value
                if (key != null) {
                    // Marcar como eliminando
                    val updatedKey = key.copy(status = "DELETING")
                    injectedKeyRepository.insertOrUpdate(updatedKey)
                    
                    // Simular el proceso de eliminación
                    kotlinx.coroutines.delay(1000)
                    
                    // Eliminar de la base de datos
                    injectedKeyRepository.deleteKey(key.id)
                    _snackbarMessage.emit("Llave eliminada exitosamente")
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Error al eliminar la llave: ${e.message}")
            } finally {
                dismissDeleteModal()
            }
        }
    }

    fun dismissDeleteModal() {
        _showDeleteModal.value = false
        _selectedKeyForDeletion.value = null
    }

    fun clearAllKeys() {
        viewModelScope.launch {
            try {
                val currentKeys = _keys.value
                currentKeys.forEach { key ->
                    val updatedKey = key.copy(status = "DELETING")
                    injectedKeyRepository.insertOrUpdate(updatedKey)
                }
                
                // Simular el proceso de eliminación
                kotlinx.coroutines.delay(1500)
                
                // Eliminar todas las llaves
                injectedKeyRepository.deleteAllKeys()
                _snackbarMessage.emit("Todas las llaves han sido eliminadas")
            } catch (e: Exception) {
                _snackbarMessage.emit("Error al eliminar las llaves: ${e.message}")
            }
        }
    }

    fun deleteKey(key: InjectedKeyEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "deleteKey: Proceso de borrado por estados iniciado para la llave con id ${key.id} en slot ${key.keySlot}.")

            // --- PASO 1: MARCAR LA LLAVE ESPECÍFICA COMO "BORRANDO" ---
            Log.d(TAG, "deleteKey: 1. Actualizando estado a 'DELETING' en la BD para la llave id ${key.id}.")
            injectedKeyRepository.updateKeyStatus(key, "DELETING")
            _snackbarMessage.emit("Borrando llave en slot ${key.keySlot}...")

            // --- PASO 2: INTENTAR BORRAR DEL HARDWARE ---
            var hardwareSuccess = false
            try {
                val pedController = KeySDKManager.getPedController()
                if (pedController != null) {
                    val genericKeyType = mapStringToGenericKeyType(key.keyType)
                    Log.d(TAG, "deleteKey: 2. Intentando borrar del hardware (Slot: ${key.keySlot}, Tipo: $genericKeyType)...")
                    hardwareSuccess = pedController.deleteKey(key.keySlot, genericKeyType)
                    Log.i(TAG, "deleteKey: 2. Respuesta del hardware: $hardwareSuccess")
                } else {
                    Log.e(TAG, "deleteKey: 2. Controlador PED no disponible.")
                    _snackbarMessage.emit("Error: Controlador no disponible.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteKey: 2. Excepción durante el borrado de hardware.", e)
                _snackbarMessage.emit("El proceso de borrado fue interrumpido.")
            }

            // --- PASO 3: FINALIZAR LA OPERACIÓN BASADO EN EL RESULTADO ---
            if (hardwareSuccess) {
                // SI EL HARDWARE BORRÓ, QUITAR EL REGISTRO DE LA BD
                Log.d(TAG, "deleteKey: 3. Éxito en hardware. Borrando llave id ${key.id} de la base de datos local.")
                injectedKeyRepository.deleteKey(key)
                _snackbarMessage.emit("Llave en slot ${key.keySlot} borrada con éxito.")
            } else {
                // SI EL HARDWARE FALLÓ, REVERTIR EL ESTADO A "SUCCESSFUL"
                Log.w(TAG, "deleteKey: 3. Fallo/Cancelación. Reviertiendo estado a 'SUCCESSFUL' para la llave id ${key.id}.")
                injectedKeyRepository.updateKeyStatus(key, "SUCCESSFUL")
                _snackbarMessage.emit("Fallo al borrar llave en slot ${key.keySlot}. Se revirtieron los cambios.")
            }
        }
    }

    // --- FUNCIÓN DE BORRADO TOTAL REESCRITA CON MANEJO DE ESTADOS ---
    fun deleteAllKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "deleteAllKeys: Proceso de borrado por estados iniciado.")

            if (keys.value.isEmpty()) {
                Log.i(TAG, "deleteAllKeys: No hay llaves para borrar.")
                _snackbarMessage.emit("No hay llaves para borrar.")
                return@launch
            }

            // --- PASO 1: MARCAR TODAS LAS LLAVES COMO "BORRANDO" ---
            Log.d(TAG, "deleteAllKeys: 1. Actualizando estado a 'DELETING' en la BD.")
            injectedKeyRepository.updateStatusForAllKeys("DELETING")
            _snackbarMessage.emit("Iniciando borrado en el dispositivo...")

            // --- PASO 2: INTENTAR BORRAR DEL HARDWARE ---
            var hardwareSuccess = false
            try {
                val pedController = KeySDKManager.getPedController()
                if (pedController != null) {
                    Log.d(TAG, "deleteAllKeys: 2. Intentando borrar del hardware...")
                    hardwareSuccess = pedController.deleteAllKeys()
                    Log.i(TAG, "deleteAllKeys: 2. Respuesta del hardware: $hardwareSuccess")
                } else {
                    Log.e(TAG, "deleteAllKeys: 2. Controlador PED no disponible.")
                    _snackbarMessage.emit("Error: Controlador no disponible.")
                }
            } catch (e: Exception) {
                // Captura de JobCancellationException y otros errores.
                Log.e(TAG, "deleteAllKeys: 2. Excepción durante el borrado de hardware.", e)
                _snackbarMessage.emit("El proceso de borrado fue interrumpido.")
                // hardwareSuccess permanece 'false' para que se ejecute la lógica de reversión.
            }

            // --- PASO 3: FINALIZAR LA OPERACIÓN BASADO EN EL RESULTADO ---
            if (hardwareSuccess) {
                // SI EL HARDWARE SE LIMPIÓ, LIMPIAR LA BASE DE DATOS LOCAL
                Log.d(TAG, "deleteAllKeys: 3. Éxito en hardware. Limpiando la base de datos local.")
                injectedKeyRepository.deleteAllKeys()
                _snackbarMessage.emit("Dispositivo y base de datos limpiados con éxito.")
            } else {
                // SI EL HARDWARE FALLÓ O LA TAREA FUE CANCELADA, REVERTIR EL ESTADO
                Log.w(TAG, "deleteAllKeys: 3. Fallo/Cancelación en hardware. Reviertiendo estado a 'SUCCESSFUL'.")
                // Asumimos que las llaves estaban 'SUCCESSFUL' antes de esto.
                // Una lógica más compleja podría guardar y restaurar el estado previo de cada llave.
                injectedKeyRepository.updateStatusForAllKeys("SUCCESSFUL")
                _snackbarMessage.emit("Error: No se pudo borrar del dispositivo. Se revirtieron los cambios.")
            }
        }
    }

    private fun mapStringToGenericKeyType(keyTypeString: String): KeyType {
        return try {
            KeyType.valueOf(keyTypeString)
        } catch (e: IllegalArgumentException) {
            if (keyTypeString.contains("MASTER") || keyTypeString.contains("TRANSPORT")) {
                KeyType.MASTER_KEY
            } else {
                KeyType.WORKING_PIN_KEY
            }
        }
    }

    // === FUNCIONES DE FILTROS ===

    fun updateFilterAlgorithm(algorithm: String) {
        _filterAlgorithm.value = algorithm
        applyFilters()
    }

    fun updateFilterStatus(status: String) {
        _filterStatus.value = status
        applyFilters()
    }

    fun updateFilterKTKType(ktkType: String) {
        _filterKTKType.value = ktkType
        applyFilters()
    }

    fun updateSearchText(text: String) {
        _searchText.value = text
        applyFilters()
    }

    private fun applyFilters() {
        val allKeys = _keys.value
        var filtered = allKeys
        
        Log.d(TAG, "=== APLICANDO FILTROS ===")
        Log.d(TAG, "Total de llaves antes de filtrar: ${filtered.size}")
        Log.d(TAG, "Filtros activos:")
        Log.d(TAG, "  - Algoritmo: ${_filterAlgorithm.value}")
        Log.d(TAG, "  - Estado: ${_filterStatus.value}")
        Log.d(TAG, "  - Tipo KTK: ${_filterKTKType.value}")
        Log.d(TAG, "  - Búsqueda: '${_searchText.value}'")
        
        // Log detallado de todas las llaves antes de filtrar
        filtered.forEachIndexed { index, key ->
            Log.d(TAG, "Llave $index: Slot=${key.keySlot}, Tipo=${key.keyType}, isKEK=${key.isKEK}, kekType='${key.kekType}', Art=${key.keyAlgorithm}, KCV=${key.kcv}")
        }

        // Filtro por algoritmo
        if (_filterAlgorithm.value != "Todos") {
            val beforeCount = filtered.size
            filtered = filtered.filter { key ->
                when (_filterAlgorithm.value) {
                    "3DES" -> key.keyAlgorithm.contains("3DES", ignoreCase = true)
                    "AES-128" -> key.keyAlgorithm.contains("AES") && key.keyData.length == 32 // 16 bytes = 32 hex chars
                    "AES-192" -> key.keyAlgorithm.contains("AES") && key.keyData.length == 48 // 24 bytes = 48 hex chars
                    "AES-256" -> key.keyAlgorithm.contains("AES") && key.keyData.length == 64 // 32 bytes = 64 hex chars
                    else -> true
                }
            }
            Log.d(TAG, "Filtro algoritmo '${_filterAlgorithm.value}': $beforeCount → ${filtered.size}")
        }

        // Filtro por estado
        if (_filterStatus.value != "Todos") {
            val beforeCount = filtered.size
            filtered = filtered.filter { it.status == _filterStatus.value }
            Log.d(TAG, "Filtro estado '${_filterStatus.value}': $beforeCount → ${filtered.size}")
        }

        // Filtro por tipo KTK
        when (_filterKTKType.value) {
            "Solo KTK" -> {
                val beforeCount = filtered.size
                filtered = filtered.filter { it.isKEK && it.kekType == "KEK_TRANSPORT" }
                Log.d(TAG, "Filtro 'Solo KTK': $beforeCount → ${filtered.size}")
                Log.d(TAG, "Llaves KTK encontradas:")
                filtered.forEach { key ->
                    Log.d(TAG, "  - Slot=${key.keySlot}, kekType='${key.kekType}', KCV=${key.kcv}")
                }
            }
            "Solo Operacionales" -> {
                val beforeCount = filtered.size
                filtered = filtered.filter { !it.isKEK }
                Log.d(TAG, "Filtro 'Solo Operacionales': $beforeCount → ${filtered.size}")
            }
            "Todas" -> { 
                Log.d(TAG, "Filtro 'Todas': No se aplica filtro de KTK")
                Log.d(TAG, "Desglose por tipo KTK:")
                val ktkCount = filtered.count { it.isKEK && it.kekType == "KEK_TRANSPORT" }
                val kekStorageCount = filtered.count { it.isKEK && it.kekType == "KEK_STORAGE" }
                val otherKekCount = filtered.count { it.isKEK && it.kekType !in listOf("KEK_TRANSPORT", "KEK_STORAGE") }
                val operationalCount = filtered.count { !it.isKEK }
                Log.d(TAG, "  - KTK (KEK_TRANSPORT): $ktkCount")
                Log.d(TAG, "  - KEK Storage (KEK_STORAGE): $kekStorageCount")
                Log.d(TAG, "  - Otros KEK: $otherKekCount")
                Log.d(TAG, "  - Operacionales: $operationalCount")
                
                // Log especial si se detectan KTKs
                if (ktkCount > 0) {
                    Log.d(TAG, "🚨 SE DETECTARON KTKs EN FILTRO 'TODAS' - DEBERÍAN SER VISIBLES")
                    filtered.filter { it.isKEK && it.kekType == "KEK_TRANSPORT" }.forEach { ktk ->
                        Log.d(TAG, "  🎯 KTK Detectada: Slot=${ktk.keySlot}, KCV=${ktk.kcv}, kekType='${ktk.kekType}', isKEK=${ktk.isKEK}")
                    }
                }
            }
        }

        // Filtro por búsqueda de texto
        if (_searchText.value.isNotEmpty()) {
            val beforeCount = filtered.size
            val searchLower = _searchText.value.lowercase()
            filtered = filtered.filter { key ->
                key.kcv.lowercase().contains(searchLower) ||
                key.customName.lowercase().contains(searchLower) ||
                key.keyType.lowercase().contains(searchLower)
            }
            Log.d(TAG, "Filtro búsqueda '${_searchText.value}': $beforeCount → ${filtered.size}")
        }

        Log.d(TAG, "Total de llaves después de filtrar: ${filtered.size}")
        
        // Log especial para ver qué se está enviando a la UI
        Log.d(TAG, "=== LLAVES ENVIADAS A LA UI ===")
        filtered.forEachIndexed { index, key ->
            Log.d(TAG, "UI Llave $index: Slot=${key.keySlot}, Tipo=${key.keyType}, isKEK=${key.isKEK}, kekType='${key.kekType}', KCV=${key.kcv}")
        }
        Log.d(TAG, "=== FIN LLAVES ENVIADAS A LA UI ===")
        
        Log.d(TAG, "=== FIN APLICACIÓN DE FILTROS ===")

        _filteredKeys.value = filtered
    }

    // === FUNCIONES DE GESTIÓN DE KTK ===

    fun setAsKTK(key: InjectedKeyEntity) {
        viewModelScope.launch {
            try {
                // Validar que sea AES-256
                if (!key.keyAlgorithm.contains("AES", ignoreCase = true) || key.keyData.length != 64) {
                    _snackbarMessage.emit("Solo las llaves AES-256 pueden ser KTK")
                    return@launch
                }

                Log.i(TAG, "Estableciendo llave como KTK: KCV=${key.kcv}")
                injectedKeyRepository.setKeyAsKTK(key.kcv)
                _snackbarMessage.emit("Llave ${key.kcv} establecida como KTK activa")
                
                // Recargar para reflejar cambios
                loadKeys()
            } catch (e: Exception) {
                Log.e(TAG, "Error al establecer KTK", e)
                _snackbarMessage.emit("Error al establecer KTK: ${e.message}")
            }
        }
    }

    fun removeAsKTK(key: InjectedKeyEntity) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Quitando flag KTK: KCV=${key.kcv}")
                injectedKeyRepository.removeKeyAsKTK(key.kcv)
                _snackbarMessage.emit("Llave ${key.kcv} ya no es KTK activa")
                
                // Recargar para reflejar cambios
                loadKeys()
            } catch (e: Exception) {
                Log.e(TAG, "Error al quitar flag KTK", e)
                _snackbarMessage.emit("Error al quitar flag KTK: ${e.message}")
            }
        }
    }

    suspend fun getCurrentKTK(): InjectedKeyEntity? {
        return try {
            injectedKeyRepository.getCurrentKTK()
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener KTK actual", e)
            null
        }
    }
}