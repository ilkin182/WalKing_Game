package com.example.domain.usecase

import com.example.domain.model.User
import com.example.domain.repository.AuthRepository

class GetCurrentUserUseCase(private val repository: AuthRepository) {
    operator fun invoke(): User? = repository.getCurrentUser()
}
