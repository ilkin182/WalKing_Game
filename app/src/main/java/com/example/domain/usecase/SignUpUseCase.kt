package com.example.domain.usecase

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.Countries
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.UserStatsRepository

/**
 * Creates the account, and records which country the new player belongs to.
 *
 * The country is kept with the player's own stats rather than pushed into the auth backend: it is
 * what the leaderboard groups by, and it has to be readable on a device whose auth is Firebase,
 * local prefs or nothing at all. Written only after the account exists, so a rejected sign-up does
 * not leave a country behind for whoever signs in next.
 */
class SignUpUseCase(
    private val repository: AuthRepository,
    private val profile: UserStatsRepository
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        confirmPassword: String,
        countryCode: String
    ): Result<User> {
        if (!AuthValidation.isValidEmail(email)) {
            return Result.failure(AuthException(AuthFailure.InvalidEmailFormat))
        }
        if (!AuthValidation.isStrongEnoughPassword(password)) {
            return Result.failure(AuthException(AuthFailure.WeakPassword))
        }
        if (password != confirmPassword) {
            return Result.failure(AuthException(AuthFailure.PasswordsDoNotMatch))
        }
        val country = Countries.byCode(countryCode)
            ?: return Result.failure(AuthException(AuthFailure.CountryNotSelected))

        return repository.signUp(email.trim(), password).map { user ->
            profile.updateCountry(country.code)
            user.copy(countryCode = country.code)
        }
    }
}
