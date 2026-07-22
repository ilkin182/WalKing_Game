package com.example.domain.usecase

import com.example.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow

class ObserveNicknameUseCase(private val repository: UserStatsRepository) {
    operator fun invoke(): Flow<String> = repository.nickname
}
