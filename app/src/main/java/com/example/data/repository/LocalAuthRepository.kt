package com.example.data.repository

import android.content.Context
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository

/**
 * Local-only stand-in for [AuthRepositoryImpl] while no real google-services.json is configured.
 * Persists a "logged in" flag via SharedPreferences instead of calling Firebase. Email/password
 * are already format-validated by LoginUseCase/SignUpUseCase before reaching here, so any
 * well-formed request just signs the user in locally -- no credential check against a stored
 * password, since this is a temporary bridge, not a real auth backend.
 *
 * Implements the same [AuthRepository] interface as [AuthRepositoryImpl], so switching back to
 * Firebase later (see AppContainer) is a one-line change that touches no UseCases or ViewModels.
 */
class LocalAuthRepository(context: Context) : AuthRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getCurrentUser(): User? =
        prefs.getString(KEY_EMAIL, null)?.let { User(uid = it, email = it) }

    override suspend fun login(email: String, password: String): Result<User> = signInLocally(email)

    override suspend fun signUp(email: String, password: String): Result<User> = signInLocally(email)

    override fun logout() {
        prefs.edit().remove(KEY_EMAIL).apply()
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)

    private fun signInLocally(email: String): Result<User> {
        prefs.edit().putString(KEY_EMAIL, email).apply()
        return Result.success(User(uid = email, email = email))
    }

    private companion object {
        const val PREFS_NAME = "local_auth"
        const val KEY_EMAIL = "logged_in_email"
    }
}
