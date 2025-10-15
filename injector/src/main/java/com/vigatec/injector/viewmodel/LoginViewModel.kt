package com.vigatec.injector.viewmodel

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

    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var rememberUser by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var loginSuccess by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var loggedInUser by mutableStateOf<User?>(null)
        private set

    init {
        loadSavedUsername()
    }

    private fun loadSavedUsername() {
        viewModelScope.launch {
            val savedRememberUser = userPreferencesManager.getRememberUser().first()
            if (savedRememberUser) {
                val savedUsername = userPreferencesManager.getLastUsername().first()
                if (!savedUsername.isNullOrEmpty()) {
                    username = savedUsername
                    rememberUser = true
                }
            }
        }
    }

    fun onUsernameChange(newUsername: String) {
        username = newUsername
    }

    fun onPasswordChange(newPassword: String) {
        password = newPassword
    }

    fun onRememberUserChange(remember: Boolean) {
        rememberUser = remember
    }

    fun login() {
        viewModelScope.launch {
            isLoading = true
            loginError = null
            val user = userRepository.login(username, password)
            if (user != null) {
                loggedInUser = user
                loginSuccess = true

                // Guardar preferencias si se marcó "recordar usuario"
                if (rememberUser) {
                    userPreferencesManager.saveLastUsername(username)
                    userPreferencesManager.saveRememberUser(true)
                } else {
                    userPreferencesManager.clearUserPreferences()
                }
            } else {
                loginError = "Credenciales inválidas o usuario inactivo"
            }
            isLoading = false
        }
    }
}