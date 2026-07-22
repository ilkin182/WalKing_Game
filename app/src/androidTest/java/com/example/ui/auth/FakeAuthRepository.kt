package com.example.ui.auth

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository

/** Controllable in-memory AuthRepository so UI tests exercise real UseCases without hitting Firebase. */
class FakeAuthRepository : AuthRepository {
    var storedUser: User? = null
    var loginResult: Result<User> = Result.failure(AuthException(AuthFailure.Unknown(null)))
    var signUpResult: Result<User> = Result.failure(AuthException(AuthFailure.Unknown(null)))
    var sendPasswordResetResult: Result<Unit> = Result.success(Unit)
    var logoutCallCount = 0

    override fun getCurrentUser(): User? = storedUser

    override suspend fun login(email: String, password: String): Result<User> = loginResult

    override suspend fun signUp(email: String, password: String): Result<User> = signUpResult

    override fun logout() {
        logoutCallCount++
        storedUser = null
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = sendPasswordResetResult
}
