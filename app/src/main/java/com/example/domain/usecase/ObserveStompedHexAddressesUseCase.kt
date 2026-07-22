package com.example.domain.usecase

import com.example.domain.repository.StompedHexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class ObserveStompedHexAddressesUseCase(private val repository: StompedHexRepository) {
    operator fun invoke(): Flow<Set<String>> =
        repository.stompedHexes
            .map { hexes -> hexes.map { it.hexAddress }.toSet() }
            // A Room query failure here would otherwise crash the collecting coroutine
            // (stateIn's WhileSubscribed sharing has no built-in recovery); degrade instead.
            .catch { emit(emptySet()) }
}
