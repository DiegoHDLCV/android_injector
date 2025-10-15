package com.vigatec.injector.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                userRepository.getAllUsers().collect { users ->
                    _uiState.value = _uiState.value.copy(
                        users = users,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
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
        role: String
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

                val result = userRepository.insertUser(newUser)
                if (result > 0) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Usuario creado exitosamente"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "No se pudo crear el usuario"
                    )
                }
            } catch (e: Exception) {
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
