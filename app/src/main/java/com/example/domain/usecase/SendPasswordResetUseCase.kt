package com.example.domain.usecase

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.repository.AuthRepository

class SendPasswordResetUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Result<Unit> {
        if (!AuthValidation.isValidEmail(email)) {
            return Result.failure(AuthException(AuthFailure.InvalidEmailFormat))
        }
        return repository.sendPasswordResetEmail(email.trim())
    }
}
