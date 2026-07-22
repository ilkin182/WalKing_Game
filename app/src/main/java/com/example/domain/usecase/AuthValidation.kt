package com.example.domain.usecase

/**
 * Shared input-validation rules for the auth UseCases. Deliberately not a repository call —
 * this is pure format validation that never needs to reach Firebase.
 */
internal object AuthValidation {
    const val MIN_PASSWORD_LENGTH = 8

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    fun isStrongEnoughPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH
}
