package com.vigatec.injector.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.injector.data.local.entity.User
import com.vigatec.injector.data.local.preferences.UserPreferencesManager
import com.vigatec.injector.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() {

    companion object {
        private const val TAG = "LoginViewModel"
    }

    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordVisible by mutableStateOf(false)
    var rememberUser by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var loginSuccess by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var loggedInUser by mutableStateOf<User?>(null)
        private set

    init {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "LoginViewModel inicializado")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        loadSavedUsername()
    }

    private fun loadSavedUsername() {
        viewModelScope.launch {
            Log.d(TAG, "Cargando usuario guardado...")
            val savedRememberUser = userPreferencesManager.getRememberUser().first()
            Log.d(TAG, "  - RememberUser guardado: $savedRememberUser")
            
            if (savedRememberUser) {
                val savedUsername = userPreferencesManager.getLastUsername().first()
                Log.d(TAG, "  - Username guardado: '$savedUsername'")
                
                if (!savedUsername.isNullOrEmpty()) {
                    username = savedUsername
                    rememberUser = true
                    Log.d(TAG, "✓ Usuario cargado desde preferencias: '$savedUsername'")
                } else {
                    Log.d(TAG, "⚠ Username guardado está vacío")
                }
            } else {
                Log.d(TAG, "No hay usuario guardado en preferencias")
            }
        }
    }

    fun onUsernameChange(newUsername: String) {
        username = newUsername
    }

    fun onPasswordChange(newPassword: String) {
        password = newPassword
    }
    
    fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
    }

    fun onRememberUserChange(remember: Boolean) {
        rememberUser = remember
    }

    fun login() {
        viewModelScope.launch {
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            Log.d(TAG, "Iniciando proceso de LOGIN")
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            Log.d(TAG, "Credenciales ingresadas:")
            Log.d(TAG, "  - Username: '$username' (length: ${username.length})")
            Log.d(TAG, "  - Password: '$password' (length: ${password.length})")
            Log.d(TAG, "  - RememberUser: $rememberUser")
            
            isLoading = true
            loginError = null
            
            // DEBUG: Listar todos los usuarios en BD antes de intentar login
            Log.d(TAG, "")
            Log.d(TAG, ">>> Listando usuarios en BD (DEBUG) <<<")
            userRepository.debugListAllUsers()
            Log.d(TAG, "")
            
            Log.d(TAG, "Llamando a userRepository.login()...")
            val user = userRepository.login(username, password)
            
            if (user != null) {
                Log.i(TAG, "✓ Usuario autenticado exitosamente")
                Log.d(TAG, "  - User ID: ${user.id}")
                Log.d(TAG, "  - Username: ${user.username}")
                Log.d(TAG, "  - Role: ${user.role}")
                Log.d(TAG, "  - FullName: ${user.fullName}")
                Log.d(TAG, "  - IsActive: ${user.isActive}")
                Log.d(TAG, "  - CreatedAt: ${user.createdAt}")
                
                // NOTA: Ya NO usamos setActiveUser() para sesión única
                // El campo isActive ahora sirve SOLO para habilitar/deshabilitar acceso
                Log.d(TAG, "✓ Campo isActive usado SOLO para control de acceso (habilitado/deshabilitado)")
                
                loggedInUser = user
                loginSuccess = true

                // Guardar preferencias si se marcó "recordar usuario"
                if (rememberUser) {
                    Log.d(TAG, "Guardando preferencias de usuario...")
                    userPreferencesManager.saveLastUsername(username)
                    userPreferencesManager.saveRememberUser(true)
                    Log.d(TAG, "✓ Preferencias guardadas")
                } else {
                    Log.d(TAG, "Limpiando preferencias de usuario...")
                    userPreferencesManager.clearUserPreferences()
                    Log.d(TAG, "✓ Preferencias limpiadas")
                }
                
                Log.i(TAG, "✓✓✓ LOGIN EXITOSO ✓✓✓")
            } else {
                Log.e(TAG, "✗ Autenticación FALLIDA")
                Log.e(TAG, "  Usuario devuelto por repository: NULL")
                Log.e(TAG, "  Posibles causas:")
                Log.e(TAG, "    1. Usuario no existe en BD")
                Log.e(TAG, "    2. Contraseña incorrecta")
                Log.e(TAG, "    3. Usuario deshabilitado por administrador (isActive = false)")
                loginError = "Credenciales inválidas o usuario deshabilitado"
            }
            
            isLoading = false
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
            Log.d(TAG, "Fin del proceso de LOGIN (success: $loginSuccess)")
            Log.d(TAG, "═══════════════════════════════════════════════════════════")
        }
    }
}