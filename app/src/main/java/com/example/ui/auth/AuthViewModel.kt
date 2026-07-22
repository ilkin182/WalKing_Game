package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val useCases: AuthUseCases) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _passwordResetEmailSent = MutableStateFlow(false)
    val passwordResetEmailSent: StateFlow<Boolean> = _passwordResetEmailSent.asStateFlow()

    fun currentUser(): User? = useCases.getCurrentUser()

    fun login(email: String, password: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            useCases.login(email, password)
                .onSuccess { _uiState.value = AuthUiState.Success(it) }
                .onFailure { _uiState.value = AuthUiState.Error(it.toAuthUiMessage()) }
        }
    }

    fun signUp(email: String, password: String, confirmPassword: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            useCases.signUp(email, password, confirmPassword)
                .onSuccess { _uiState.value = AuthUiState.Success(it) }
                .onFailure { _uiState.value = AuthUiState.Error(it.toAuthUiMessage()) }
        }
    }

    fun sendPasswordReset(email: String) {
        _passwordResetEmailSent.value = false
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            useCases.sendPasswordReset(email)
                .onSuccess {
                    _passwordResetEmailSent.value = true
                    _uiState.value = AuthUiState.Idle
                }
                .onFailure { _uiState.value = AuthUiState.Error(it.toAuthUiMessage()) }
        }
    }

    fun logout() {
        useCases.logout()
        _uiState.value = AuthUiState.Idle
    }

    fun consumeError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }
}

class AuthViewModelFactory(private val useCases: AuthUseCases) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(useCases) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
