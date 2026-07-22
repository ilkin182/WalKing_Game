package com.example.data.repository

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository

/**
 * Used when Firebase couldn't be initialized (typically: no real google-services.json has been
 * dropped in yet). Every operation fails gracefully with a clear message instead of the app
 * crashing at launch on `FirebaseAuth.getInstance()`'s `IllegalStateException`.
 */
class UnavailableAuthRepository : AuthRepository {
    private fun unavailable() = Result.failure<User>(
        AuthException(
            AuthFailure.Unknown("Authentication is not configured. Add a valid google-services.json and rebuild."),
            "Firebase is not initialized"
        )
    )

    override fun getCurrentUser(): User? = null

    override suspend fun login(email: String, password: String): Result<User> = unavailable()

    override suspend fun signUp(email: String, password: String): Result<User> = unavailable()

    override fun logout() = Unit

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(unavailable().exceptionOrNull()!!)
}
