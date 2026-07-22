package com.example.domain.model

sealed class AuthFailure {
    object InvalidEmailFormat : AuthFailure()
    object WeakPassword : AuthFailure()
    object PasswordsDoNotMatch : AuthFailure()
    object InvalidCredentials : AuthFailure()
    object EmailAlreadyInUse : AuthFailure()
    object NetworkError : AuthFailure()
    data class Unknown(val message: String?) : AuthFailure()
}

/**
 * The only exception type auth UseCases/repositories throw or wrap in [Result.failure].
 * Keeps SDK-specific exceptions (Firebase, etc.) from leaking past the data layer.
 */
class AuthException(val failure: AuthFailure, message: String? = null) : Exception(message)
