package com.vigatec.injector.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.injector.data.local.entity.Permission
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserManagementUiState(
    val users: List<User> = emptyList(),
    val allPermissions: List<Permission> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    companion object {
        private const val TAG = "UserManagementVM"
    }

    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "UserManagementViewModel inicializado")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        loadUsers()
        loadPermissions()
    }
    
    private fun loadPermissions() {
        viewModelScope.launch {
            try {
                userRepository.getAllPermissions().collect { permissions ->
                    Log.d(TAG, "✓ ${permissions.size} permisos cargados")
                    _uiState.value = _uiState.value.copy(allPermissions = permissions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error al cargar permisos", e)
            }
        }
    }

    private fun loadUsers() {
        viewModelScope.launch {
            Log.d(TAG, "─────────────────────────────────────────────────────────────")
            Log.d(TAG, "Cargando lista de usuarios desde BD...")
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                userRepository.getAllUsers().collect { users ->
                    Log.d(TAG, "✓ Usuarios cargados: ${users.size}")
                    Log.d(TAG, "")
                    Log.d(TAG, "═══ DETALLE DE USUARIOS EN GESTIÓN ═══")
                    users.forEachIndexed { index, user ->
                        Log.d(TAG, "Usuario #${index + 1}:")
                        Log.d(TAG, "  - ID: ${user.id}")
                        Log.d(TAG, "  - Username: '${user.username}'")
                        Log.d(TAG, "  - FullName: '${user.fullName}'")
                        Log.d(TAG, "  - Role: ${user.role}")
                        Log.d(TAG, "  - IsActive: ${user.isActive} ${if (user.isActive) "✓ ACTIVO" else "✗ INACTIVO"}")
                        Log.d(TAG, "")
                    }
                    Log.d(TAG, "═════════════════════════════════════════════════")
                    Log.d(TAG, "")
                    Log.w(TAG, "═══ CONTROL DE ACCESO CON isActive ═══")
                    Log.w(TAG, "")
                    Log.w(TAG, "El campo 'isActive' controla si un usuario puede acceder:")
                    Log.w(TAG, "  • isActive = true  → Usuario HABILITADO (puede hacer login)")
                    Log.w(TAG, "  • isActive = false → Usuario DESHABILITADO (login bloqueado)")
                    Log.w(TAG, "")
                    Log.w(TAG, "Usa el switch en esta pantalla para habilitar/deshabilitar usuarios.")
                    Log.w(TAG, "Los usuarios deshabilitados NO podrán iniciar sesión.")
                    Log.d(TAG, "─────────────────────────────────────────────────────────────")
                    
                    _uiState.value = _uiState.value.copy(
                        users = users,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error al cargar usuarios", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al cargar usuarios: ${e.message}"
                )
            }
        }
    }

    fun createUser(
        username: String,
        password: String,
        fullName: String,
        role: String,
        selectedPermissions: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                // Validar que el username no esté vacío
                if (username.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "El nombre de usuario no puede estar vacío"
                    )
                    return@launch
                }

                // Validar que la contraseña no esté vacía
                if (password.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "La contraseña no puede estar vacía"
                    )
                    return@launch
                }

                // Verificar si el usuario ya existe
                val existingUser = userRepository.findByUsername(username)
                if (existingUser != null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "El usuario '$username' ya existe"
                    )
                    return@launch
                }

                val newUser = User(
                    username = username,
                    pass = password,
                    fullName = fullName,
                    role = role,
                    isActive = true
                )

                val userId = userRepository.insertUser(newUser)
                if (userId > 0) {
                    // Asignar permisos
                    if (role == "ADMIN") {
                        // ADMIN obtiene TODOS los permisos automáticamente
                        val allPermissionIds = _uiState.value.allPermissions.map { it.id }
                        userRepository.updateUserPermissions(userId.toInt(), allPermissionIds)
                        Log.d(TAG, "✓ Usuario ADMIN creado con TODOS los permisos")
                    } else {
                        // USER obtiene solo los permisos seleccionados
                        userRepository.updateUserPermissions(userId.toInt(), selectedPermissions)
                        Log.d(TAG, "✓ Usuario USER creado con ${selectedPermissions.size} permisos")
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Usuario creado exitosamente"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "No se pudo crear el usuario"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al crear usuario", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al crear usuario: ${e.message}"
                )
            }
        }
    }

    fun updateUser(user: User) {
        viewModelScope.launch {
            try {
                userRepository.updateUser(user)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Usuario actualizado exitosamente"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al actualizar usuario: ${e.message}"
                )
            }
        }
    }
    
    fun updateUserWithPermissions(user: User, selectedPermissions: List<String>) {
        viewModelScope.launch {
            try {
                // Actualizar usuario
                userRepository.updateUser(user)
                
                // Actualizar permisos
                if (user.role == "ADMIN") {
                    // ADMIN siempre tiene todos los permisos (no editables)
                    val allPermissionIds = _uiState.value.allPermissions.map { it.id }
                    userRepository.updateUserPermissions(user.id, allPermissionIds)
                    Log.d(TAG, "✓ Usuario ADMIN actualizado con TODOS los permisos")
                } else {
                    // USER obtiene los permisos seleccionados
                    userRepository.updateUserPermissions(user.id, selectedPermissions)
                    Log.d(TAG, "✓ Usuario USER actualizado con ${selectedPermissions.size} permisos")
                }
                
                _uiState.value = _uiState.value.copy(
                    successMessage = "Usuario y permisos actualizados exitosamente"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error al actualizar usuario y permisos", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al actualizar usuario: ${e.message}"
                )
            }
        }
    }
    
    suspend fun getUserPermissions(userId: Int): List<Permission> {
        return try {
            userRepository.getUserPermissionsSync(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener permisos del usuario", e)
            emptyList()
        }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch {
            try {
                // Verificar que no sea el último admin
                if (user.role == "ADMIN") {
                    val adminCount = userRepository.getAdminCount()
                    if (adminCount <= 1) {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "No se puede eliminar el último administrador del sistema"
                        )
                        return@launch
                    }
                }

                userRepository.deleteUser(user)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Usuario eliminado exitosamente"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al eliminar usuario: ${e.message}"
                )
            }
        }
    }

    fun toggleUserActiveStatus(user: User) {
        viewModelScope.launch {
            try {
                // Si es el último admin activo, no permitir desactivarlo
                if (user.role == "ADMIN" && user.isActive) {
                    val adminCount = userRepository.getAdminCount()
                    if (adminCount <= 1) {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "No se puede desactivar el último administrador del sistema"
                        )
                        return@launch
                    }
                }

                userRepository.updateUserActiveStatus(user.id, !user.isActive)
                _uiState.value = _uiState.value.copy(
                    successMessage = if (!user.isActive) "Usuario activado" else "Usuario desactivado"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al cambiar estado del usuario: ${e.message}"
                )
            }
        }
    }

    fun updateUserPassword(userId: Int, newPassword: String) {
        viewModelScope.launch {
            try {
                if (newPassword.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "La contraseña no puede estar vacía"
                    )
                    return@launch
                }

                userRepository.updateUserPassword(userId, newPassword)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Contraseña actualizada exitosamente"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al actualizar contraseña: ${e.message}"
                )
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
