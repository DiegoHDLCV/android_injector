package com.vigatec.injector.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigatec.injector.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    var username by mutableStateOf("admin")
    var password by mutableStateOf("admin")
    var loginError by mutableStateOf<String?>(null)
    var loginSuccess by mutableStateOf(false)
    var isLoading by mutableStateOf(false)

    fun onUsernameChange(newUsername: String) {
        username = newUsername
    }

    fun onPasswordChange(newPassword: String) {
        password = newPassword
    }

    fun login() {
        viewModelScope.launch {
            isLoading = true
            loginError = null
            val success = userRepository.login(username, password)
            if (success) {
                loginSuccess = true
            } else {
                loginError = "Credenciales inválidas"
            }
            isLoading = false
        }
    }
}