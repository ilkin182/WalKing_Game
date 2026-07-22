package com.example.ui.auth

import com.example.BuildConfig
import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.User

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Success(val user: User) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

internal fun Throwable.toAuthUiMessage(): String {
    return when (val failure = (this as? AuthException)?.failure) {
        AuthFailure.InvalidEmailFormat -> "Please enter a valid email address."
        AuthFailure.WeakPassword -> "Password must be at least 8 characters."
        AuthFailure.PasswordsDoNotMatch -> "Passwords do not match."
        AuthFailure.InvalidCredentials -> "Incorrect email or password."
        AuthFailure.EmailAlreadyInUse -> "An account with this email already exists."
        AuthFailure.NetworkError -> "Network error. Please check your connection and try again."
        // Debug builds surface the real diagnostic (e.g. "Firebase is not initialized") instead of
        // masking config errors behind a generic message that looks identical to any other failure.
        is AuthFailure.Unknown ->
            if (BuildConfig.DEBUG && failure.message != null) failure.message else "Something went wrong. Please try again."
        null -> "Something went wrong. Please try again."
    }
}
