package com.infusory.tutarapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class LoginViewModel : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _loginState.value = LoginState.Loading

                // Simulate API call delay
                delay(2000)

                _loginState.value=LoginState.Success("success")

                // Simple validation for demo purposes
                // Replace this with actual authentication logic
//                when {
//                    email == "teacher@tutar.com" && password == "password123" -> {
//                        _loginState.value = LoginState.Success("Login successful!")
//                        Timber.d("Login successful for user: $email")
//                    }
//                    email == "admin@tutar.com" && password == "admin123" -> {
//                        _loginState.value = LoginState.Success("Admin login successful!")
//                        Timber.d("Admin login successful for user: $email")
//                    }
//                    !isValidEmail(email) -> {
//                        _loginState.value = LoginState.Error("Please enter a valid email address")
//                    }
//                    password.length < 6 -> {
//                        _loginState.value = LoginState.Error("Password must be at least 6 characters")
//                    }
//                    else -> {
//                        _loginState.value = LoginState.Error("Invalid email or password. Try teacher@tutar.com / password123")
//                    }
//                }

            } catch (e: Exception) {
                _loginState.value = LoginState.Error("An error occurred: ${e.message}")
                Timber.e(e, "Login error")
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}

// Login states
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val message: String) : LoginState()
    data class Error(val message: String) : LoginState()
}