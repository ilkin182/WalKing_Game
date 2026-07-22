package com.example.domain.usecase

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (!AuthValidation.isValidEmail(email)) {
            return Result.failure(AuthException(AuthFailure.InvalidEmailFormat))
        }
        if (!AuthValidation.isStrongEnoughPassword(password)) {
            return Result.failure(AuthException(AuthFailure.WeakPassword))
        }
        return repository.login(email.trim(), password)
    }
}
