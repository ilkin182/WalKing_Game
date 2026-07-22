package com.example.domain.usecase

import com.example.domain.repository.AuthRepository

class LogoutUseCase(private val repository: AuthRepository) {
    operator fun invoke() {
        repository.logout()
    }
}
