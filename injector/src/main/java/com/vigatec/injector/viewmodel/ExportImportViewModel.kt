package com.vigatec.injector.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persistence.entities.InjectedKeyEntity
import com.example.persistence.repository.InjectedKeyRepository
import com.vigatec.injector.data.local.preferences.SessionManager
import com.vigatec.utils.security.ExportKeyData
import com.vigatec.utils.security.KeyExportManager
import com.vigatec.utils.security.StorageKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExportImportState(
    val isLoading: Boolean = false,
    val currentTab: Int = 0, // 0: Exportar, 1: Importar

    // Exportación
    val exportPassphrase: String = "",
    val exportPassphraseConfirm: String = "",
    val showExportPassphrase: Boolean = false,
    val exportPassphraseError: String? = null,
    val exportSuccess: Boolean = false,
    val exportedFilePath: String = "",
    val exportedKeyCount: Int = 0,
    val showAdminPasswordDialogForExport: Boolean = false,  // Diálogo para pedir contraseña de admin antes de exportar
    val adminPasswordForExport: String = "",
    val adminPasswordErrorForExport: String? = null,

    // Importación
    val importPassphrase: String = "",
    val showImportPassphrase: Boolean = false,
    val importPassphraseError: String? = null,
    val selectedImportFile: String? = null,
    val importSuccess: Boolean = false,
    val importedKeyCount: Int = 0,
    val skippedDuplicates: Int = 0,
    val showAdminPasswordDialogForImport: Boolean = false,  // Diálogo para pedir contraseña de admin antes de importar
    val adminPasswordForImport: String = "",
    val adminPasswordErrorForImport: String? = null,

    // Drag & Drop
    val isDraggingFile: Boolean = false,
    val draggedFileName: String? = null,
    val importFileContent: String? = null,

    // Estado general
    val hasKEKStorage: Boolean = false,
    val totalKeysInVault: Int = 0,
    val errorMessage: String? = null,
    val isAdmin: Boolean = false
)

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val injectedKeyRepository: InjectedKeyRepository,
    private val sessionManager: SessionManager,
    private val userRepository: com.vigatec.injector.repository.UserRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ExportImportViewModel"
    }

    private val _uiState = MutableStateFlow(ExportImportState())
    val uiState: StateFlow<ExportImportState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadInitialState()
        }
    }

    private suspend fun loadInitialState() {
        Log.d(TAG, "Cargando estado inicial...")

        // Verificar KEK Storage
        val hasKEK = StorageKeyManager.hasStorageKEK()

        // Cargar usuario actual
        val session = sessionManager.getCurrentSession()
        val isAdmin = session?.let { (_, _, role) -> role == "ADMIN" } ?: false

        // Contar llaves en el vault
        val keys = injectedKeyRepository.getAllInjectedKeys().first()
        val totalKeys = keys.size

        _uiState.value = _uiState.value.copy(
            hasKEKStorage = hasKEK,
            totalKeysInVault = totalKeys,
            isAdmin = isAdmin
        )

        Log.d(TAG, "Estado inicial cargado:")
        Log.d(TAG, "  - KEK Storage: $hasKEK")
        Log.d(TAG, "  - Es Admin: $isAdmin")
        Log.d(TAG, "  - Total llaves: $totalKeys")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXPORTACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    fun onExportPassphraseChange(passphrase: String) {
        _uiState.value = _uiState.value.copy(
            exportPassphrase = passphrase,
            exportPassphraseError = null
        )
    }

    fun onExportPassphraseConfirmChange(passphrase: String) {
        _uiState.value = _uiState.value.copy(
            exportPassphraseConfirm = passphrase,
            exportPassphraseError = null
        )
    }

    fun onToggleExportPassphraseVisibility() {
        _uiState.value = _uiState.value.copy(
            showExportPassphrase = !_uiState.value.showExportPassphrase
        )
    }

    fun onRequestExport() {
        // Primero pedir contraseña de administrador
        _uiState.value = _uiState.value.copy(
            showAdminPasswordDialogForExport = true,
            adminPasswordForExport = "",
            adminPasswordErrorForExport = null
        )
    }

    fun onAdminPasswordEnteredForExport(password: String) {
        viewModelScope.launch {
            try {
                val session = sessionManager.getCurrentSession()
                if (session == null) {
                    _uiState.value = _uiState.value.copy(
                        adminPasswordErrorForExport = "No hay sesión activa"
                    )
                    return@launch
                }

                val (userId, _, _) = session
                val user = userRepository.findById(userId.toInt())

                if (user == null) {
                    _uiState.value = _uiState.value.copy(
                        adminPasswordErrorForExport = "Usuario no encontrado"
                    )
                    return@launch
                }

                // Verificar contraseña
                if (user.pass != password) {
                    _uiState.value = _uiState.value.copy(
                        adminPasswordErrorForExport = "Contraseña incorrecta"
                    )
                    return@launch
                }

                // Contraseña correcta - proceder con exportación
                _uiState.value = _uiState.value.copy(
                    showAdminPasswordDialogForExport = false,
                    adminPasswordForExport = password,
                    adminPasswordErrorForExport = null
                )
                exportKeys()
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando contraseña de admin para exportar", e)
                _uiState.value = _uiState.value.copy(
                    adminPasswordErrorForExport = "Error al verificar contraseña: ${e.message}"
                )
            }
        }
    }

    fun onDismissAdminPasswordDialogForExport() {
        _uiState.value = _uiState.value.copy(
            showAdminPasswordDialogForExport = false,
            adminPasswordForExport = "",
            adminPasswordErrorForExport = null
        )
    }

    fun exportKeys() {
        viewModelScope.launch {
            Log.i(TAG, "═══════════════════════════════════════════════════════════")
            Log.i(TAG, "Iniciando exportación de llaves...")
            Log.i(TAG, "═══════════════════════════════════════════════════════════")

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                exportPassphraseError = null,
                exportSuccess = false
            )

            try {
                // Validación 1: Verificar que es administrador
                if (!_uiState.value.isAdmin) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Solo los administradores pueden exportar llaves"
                    )
                    return@launch
                }

                // Validación 2: Verificar passphrases
                val passphrase = _uiState.value.exportPassphrase
                val passphraseConfirm = _uiState.value.exportPassphraseConfirm

                if (passphrase != passphraseConfirm) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        exportPassphraseError = "Las passphrases no coinciden"
                    )
                    return@launch
                }

                // Validación 3: Validar fortaleza de passphrase
                val (isValid, validationMessage) = KeyExportManager.validatePassphrase(passphrase)
                if (!isValid) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        exportPassphraseError = validationMessage
                    )
                    return@launch
                }

                // Validación 4: Verificar KEK Storage
                if (!_uiState.value.hasKEKStorage) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No existe KEK Storage. No se pueden exportar llaves."
                    )
                    return@launch
                }

                // Obtener todas las llaves del almacén
                val keys = injectedKeyRepository.getAllInjectedKeys().first()

                if (keys.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No hay llaves para exportar"
                    )
                    return@launch
                }

                Log.d(TAG, "Preparando ${keys.size} llaves para exportación")

                // Convertir llaves a formato de exportación
                val exportKeys = keys.map { key ->
                    ExportKeyData(
                        kcv = key.kcv,
                        keySlot = key.keySlot,
                        keyType = key.keyType,
                        keyAlgorithm = key.keyAlgorithm,
                        customName = key.customName,
                        kekType = key.kekType,
                        encryptedKeyData = key.encryptedKeyData,
                        encryptionIV = key.encryptionIV,
                        encryptionAuthTag = key.encryptionAuthTag,
                        status = key.status,
                        injectionTimestamp = key.injectionTimestamp
                    )
                }

                // Obtener username del usuario actual
                val session = sessionManager.getCurrentSession()
                val username = session?.let { (_, user, _) -> user } ?: "unknown"

                // Obtener device ID (puedes usar un hash del Android ID)
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )

                // Exportar llaves
                val result = KeyExportManager.exportKeys(
                    keys = exportKeys,
                    passphrase = passphrase,
                    exportedBy = username,
                    deviceId = deviceId
                )

                if (!result.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.errorMessage
                    )
                    return@launch
                }

                // Guardar archivo en el directorio de la app (no requiere permisos especiales)
                val timestamp = System.currentTimeMillis()
                val fileName = "keystore_export_$timestamp.json"

                // Usar getExternalFilesDir que no requiere permisos en Android 10+
                val exportDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir // Fallback al almacenamiento interno

                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val exportFile = File(exportDir, fileName)
                exportFile.writeText(result.exportedJson)

                Log.i(TAG, "✓ Exportación completada exitosamente")
                Log.i(TAG, "  - Archivo: ${exportFile.absolutePath}")
                Log.i(TAG, "  - Llaves exportadas: ${result.keyCount}")
                Log.i(TAG, "═══════════════════════════════════════════════════════════")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    exportSuccess = true,
                    exportedFilePath = exportFile.absolutePath,
                    exportedKeyCount = result.keyCount
                )

            } catch (e: Exception) {
                Log.e(TAG, "✗ Error durante exportación", e)
                Log.e(TAG, "═══════════════════════════════════════════════════════════")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al exportar: ${e.message}"
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // IMPORTACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    fun onImportPassphraseChange(passphrase: String) {
        _uiState.value = _uiState.value.copy(
            importPassphrase = passphrase,
            importPassphraseError = null
        )
    }

    fun onToggleImportPassphraseVisibility() {
        _uiState.value = _uiState.value.copy(
            showImportPassphrase = !_uiState.value.showImportPassphrase
        )
    }

    fun onSelectImportFile(filePath: String) {
        _uiState.value = _uiState.value.copy(
            selectedImportFile = filePath,
            errorMessage = null
        )
    }

    fun onSelectFileFromBluetooth() {
        // Abrir selector de archivos que incluye Bluetooth como opción
        // Esto se manejará desde la UI usando un Intent
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }

    fun onRequestImport() {
        // Primero pedir contraseña de administrador
        _uiState.value = _uiState.value.copy(
            showAdminPasswordDialogForImport = true,
            adminPasswordForImport = "",
            adminPasswordErrorForImport = null
        )
    }

    fun onAdminPasswordEnteredForImport(password: String) {
        viewModelScope.launch {
            try {
                val session = sessionManager.getCurrentSession()
                if (session == null) {
                    _uiState.value = _uiState.value.copy(
                        adminPasswordErrorForImport = "No hay sesión activa"
                    )
                    return@launch
                }

                val (userId, _, _) = session
                val user = userRepository.findById(userId.toInt())

                if (user == null) {
                    _uiState.value = _uiState.value.copy(
                        adminPasswordErrorForImport = "Usuario no encontrado"
                    )
                    return@launch
                }

                // Verificar contraseña
                if (user.pass != password) {
                    _uiState.value = _uiState.value.copy(
                        adminPasswordErrorForImport = "Contraseña incorrecta"
                    )
                    return@launch
                }

                // Contraseña correcta - proceder con importación
                _uiState.value = _uiState.value.copy(
                    showAdminPasswordDialogForImport = false,
                    adminPasswordForImport = password,
                    adminPasswordErrorForImport = null
                )
                importKeys()
            } catch (e: Exception) {
                Log.e(TAG, "Error verificando contraseña de admin para importar", e)
                _uiState.value = _uiState.value.copy(
                    adminPasswordErrorForImport = "Error al verificar contraseña: ${e.message}"
                )
            }
        }
    }

    fun onDismissAdminPasswordDialogForImport() {
        _uiState.value = _uiState.value.copy(
            showAdminPasswordDialogForImport = false,
            adminPasswordForImport = "",
            adminPasswordErrorForImport = null
        )
    }

    fun importKeys() {
        viewModelScope.launch {
            Log.i(TAG, "═══════════════════════════════════════════════════════════")
            Log.i(TAG, "Iniciando importación de llaves...")
            Log.i(TAG, "═══════════════════════════════════════════════════════════")

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                importPassphraseError = null,
                importSuccess = false
            )

            try {
                // Validación 1: Verificar que es administrador
                if (!_uiState.value.isAdmin) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Solo los administradores pueden importar llaves"
                    )
                    return@launch
                }

                // Validación 2: Verificar KEK Storage
                if (!_uiState.value.hasKEKStorage) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No existe KEK Storage en este dispositivo. " +
                                "Debe crear una KEK Storage primero mediante ceremonia."
                    )
                    return@launch
                }

                // Validación 3: Verificar archivo seleccionado
                val filePath = _uiState.value.selectedImportFile
                if (filePath == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Debe seleccionar un archivo para importar"
                    )
                    return@launch
                }

                // Validación 4: Verificar passphrase
                val passphrase = _uiState.value.importPassphrase
                if (passphrase.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        importPassphraseError = "Debe ingresar la passphrase de exportación"
                    )
                    return@launch
                }

                // Leer archivo
                val importFile = File(filePath)
                if (!importFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "El archivo seleccionado no existe"
                    )
                    return@launch
                }

                val exportedJson = importFile.readText()
                Log.d(TAG, "Archivo leído: ${exportedJson.length} caracteres")

                // Obtener KCVs existentes para detectar duplicados
                val existingKeys = injectedKeyRepository.getAllInjectedKeys().first()
                val existingKcvs = existingKeys.map { it.kcv }.toSet()
                Log.d(TAG, "KCVs existentes: ${existingKcvs.size}")

                // Importar llaves
                val result = KeyExportManager.importKeys(
                    exportedJson = exportedJson,
                    passphrase = passphrase,
                    existingKcvs = existingKcvs
                )

                if (!result.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.errorMessage
                    )
                    return@launch
                }

                // Insertar llaves importadas en la base de datos
                var insertedCount = 0
                for (keyData in result.importedKeys) {
                    val entity = InjectedKeyEntity(
                        keySlot = keyData.keySlot,
                        keyType = keyData.keyType,
                        keyAlgorithm = keyData.keyAlgorithm,
                        kcv = keyData.kcv,
                        encryptedKeyData = keyData.encryptedKeyData,
                        encryptionIV = keyData.encryptionIV,
                        encryptionAuthTag = keyData.encryptionAuthTag,
                        status = "IMPORTED",
                        kekType = keyData.kekType,
                        customName = keyData.customName,
                        injectionTimestamp = System.currentTimeMillis()
                    )

                    injectedKeyRepository.insertOrUpdate(entity)
                    insertedCount++
                }

                Log.i(TAG, "✓ Importación completada exitosamente")
                Log.i(TAG, "  - Llaves importadas: $insertedCount")
                Log.i(TAG, "  - Duplicados omitidos: ${result.skippedDuplicates}")
                Log.i(TAG, "═══════════════════════════════════════════════════════════")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importSuccess = true,
                    importedKeyCount = insertedCount,
                    skippedDuplicates = result.skippedDuplicates
                )

                // Actualizar contador de llaves
                loadInitialState()

            } catch (e: Exception) {
                Log.e(TAG, "✗ Error durante importación", e)
                Log.e(TAG, "═══════════════════════════════════════════════════════════")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al importar: ${e.message}"
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NAVEGACIÓN Y UTILIDADES
    // ═══════════════════════════════════════════════════════════════════════

    fun onTabChange(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(
            currentTab = tabIndex,
            errorMessage = null,
            exportSuccess = false,
            importSuccess = false
        )
    }

    fun clearExportSuccess() {
        _uiState.value = _uiState.value.copy(
            exportSuccess = false,
            exportPassphrase = "",
            exportPassphraseConfirm = ""
        )
    }

    fun clearImportSuccess() {
        _uiState.value = _uiState.value.copy(
            importSuccess = false,
            importPassphrase = "",
            selectedImportFile = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            exportPassphraseError = null,
            importPassphraseError = null
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DRAG & DROP - Manejo de archivos arrastrados
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Marca que el usuario está arrastrando un archivo sobre el área de drop
     */
    fun setDraggingFile(isDragging: Boolean) {
        _uiState.value = _uiState.value.copy(isDraggingFile = isDragging)
    }

    /**
     * Procesa un archivo JSON arrastrado
     * Realiza validación y carga el contenido del archivo
     */
    fun handleDroppedFile(fileName: String, fileContent: String) {
        Log.d(TAG, "=== ARCHIVO ARRASTRADO ===")
        Log.d(TAG, "Nombre: $fileName")
        Log.d(TAG, "Tamaño: ${fileContent.length} caracteres")

        // Reiniciar estados previos
        _uiState.value = _uiState.value.copy(
            isDraggingFile = false,
            errorMessage = null,
            importPassphraseError = null
        )

        // Validar que sea archivo JSON
        if (!fileName.endsWith(".json", ignoreCase = true)) {
            Log.w(TAG, "❌ Archivo no es JSON: $fileName")
            _uiState.value = _uiState.value.copy(
                errorMessage = "El archivo debe ser JSON. Recibido: $fileName"
            )
            return
        }

        // Validar estructura JSON
        val validationResult = validateJsonStructure(fileContent)
        if (!validationResult.isValid) {
            Log.w(TAG, "❌ JSON inválido: ${validationResult.errorMessage}")
            _uiState.value = _uiState.value.copy(
                errorMessage = validationResult.errorMessage ?: "Archivo JSON inválido"
            )
            return
        }

        // Éxito - guardar información del archivo
        Log.i(TAG, "✓ Archivo JSON válido cargado")
        _uiState.value = _uiState.value.copy(
            draggedFileName = fileName,
            importFileContent = fileContent,
            selectedImportFile = fileName,
            importSuccess = false,
            importPassphrase = "" // Limpiar passphrase anterior
        )
    }

    /**
     * Valida la estructura del archivo JSON exportado
     * Verifica que tenga los campos requeridos
     */
    private fun validateJsonStructure(fileContent: String): ValidationResult {
        return try {
            // Parsear JSON
            val json = org.json.JSONObject(fileContent)

            // Verificar campos requeridos del nivel externo
            val requiredFields = listOf("version", "encryptedPayload", "salt", "iv", "authTag")
            for (field in requiredFields) {
                if (!json.has(field)) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Campo obligatorio faltante: $field"
                    )
                }
            }

            // Validar versión
            val version = json.getInt("version")
            if (version != 1) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Versión no soportada: $version (se espera versión 1)"
                )
            }

            // Validar que encryptedPayload sea válido HEX
            val encryptedPayload = json.getString("encryptedPayload")
            if (!encryptedPayload.matches(Regex("^[0-9A-Fa-f]*$"))) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "encryptedPayload no es válido hexadecimal"
                )
            }

            ValidationResult(isValid = true)
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "Error parsing JSON: ${e.message}")
            ValidationResult(
                isValid = false,
                errorMessage = "JSON malformado: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error validating JSON: ${e.message}")
            ValidationResult(
                isValid = false,
                errorMessage = "Error validando archivo: ${e.message}"
            )
        }
    }

    /**
     * Inicia el proceso de importación usando el archivo cargado vía drag & drop
     */
    fun importFromDroppedFile() {
        val fileContent = _uiState.value.importFileContent
        val passphrase = _uiState.value.importPassphrase

        if (fileContent == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "No hay archivo cargado"
            )
            return
        }

        if (passphrase.isBlank()) {
            _uiState.value = _uiState.value.copy(
                importPassphraseError = "Ingrese la contraseña"
            )
            return
        }

        // Actualizar el state con el contenido del archivo
        // Para que importKeys() pueda procesarlo
        _uiState.value = _uiState.value.copy(
            selectedImportFile = _uiState.value.draggedFileName ?: "dropped_file.json"
        )

        // Delegar a la función de importación existente
        importKeys()
    }

    /**
     * Limpia el archivo arrastrado
     */
    fun clearDroppedFile() {
        _uiState.value = _uiState.value.copy(
            draggedFileName = null,
            importFileContent = null,
            selectedImportFile = null,
            isDraggingFile = false
        )
    }

    /**
     * Resultado de validación de JSON
     */
    private data class ValidationResult(
        val isValid: Boolean = false,
        val errorMessage: String? = null
    )
}
