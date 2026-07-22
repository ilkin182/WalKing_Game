package com.example.domain.repository

import com.example.domain.model.User

interface AuthRepository {
    fun getCurrentUser(): User?
    suspend fun login(email: String, password: String): Result<User>
    suspend fun signUp(email: String, password: String): Result<User>
    fun logout()
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
}
