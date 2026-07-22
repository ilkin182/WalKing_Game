package com.example.domain.usecase

import com.example.domain.repository.UserStatsRepository

class UpdateNicknameUseCase(private val repository: UserStatsRepository) {
    operator fun invoke(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            repository.updateNickname(trimmed)
        }
    }
}
