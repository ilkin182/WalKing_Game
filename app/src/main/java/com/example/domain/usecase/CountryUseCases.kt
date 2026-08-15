package com.example.domain.usecase

import com.example.domain.model.Countries
import com.example.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow

class ObserveCountryUseCase(private val repository: UserStatsRepository) {
    operator fun invoke(): Flow<String?> = repository.countryCode
}

/**
 * Sets the player's country, ignoring anything that is not a real ISO code.
 *
 * The picker can only produce valid codes; this guards the path where an older account is being
 * filled in from a stored value that predates the list.
 */
class UpdateCountryUseCase(private val repository: UserStatsRepository) {
    operator fun invoke(code: String) {
        val country = Countries.byCode(code) ?: return
        repository.updateCountry(country.code)
    }
}
