package com.example.data.repository

import com.example.data.mapper.toDomain
import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl(private val firebaseAuth: FirebaseAuth) : AuthRepository {

    override fun getCurrentUser(): User? = firebaseAuth.currentUser?.toDomain()

    override suspend fun login(email: String, password: String): Result<User> = runCatching {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        result.user?.toDomain() ?: throw AuthException(AuthFailure.Unknown("No user returned by Firebase"))
    }.recoverAuthFailure()

    override suspend fun signUp(email: String, password: String): Result<User> = runCatching {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        result.user?.toDomain() ?: throw AuthException(AuthFailure.Unknown("No user returned by Firebase"))
    }.recoverAuthFailure()

    override fun logout() {
        firebaseAuth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Unit
    }.recoverAuthFailure()

    /** Maps Firebase/network exceptions to [AuthException] so no SDK type or stack trace reaches the UI. */
    private fun <T> Result<T>.recoverAuthFailure(): Result<T> = recoverCatching { throwable ->
        throw when (throwable) {
            is AuthException -> throwable
            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException -> AuthException(AuthFailure.InvalidCredentials, throwable.message)
            is FirebaseAuthUserCollisionException -> AuthException(AuthFailure.EmailAlreadyInUse, throwable.message)
            is FirebaseAuthWeakPasswordException -> AuthException(AuthFailure.WeakPassword, throwable.message)
            is FirebaseNetworkException -> AuthException(AuthFailure.NetworkError, throwable.message)
            else -> AuthException(AuthFailure.Unknown(throwable.message), throwable.message)
        }
    }
}
