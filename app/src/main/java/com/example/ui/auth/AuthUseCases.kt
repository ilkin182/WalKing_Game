package com.example.ui.auth

import com.example.domain.usecase.GetCurrentUserUseCase
import com.example.domain.usecase.LoginUseCase
import com.example.domain.usecase.LogoutUseCase
import com.example.domain.usecase.SendPasswordResetUseCase
import com.example.domain.usecase.SignUpUseCase

data class AuthUseCases(
    val login: LoginUseCase,
    val signUp: SignUpUseCase,
    val logout: LogoutUseCase,
    val getCurrentUser: GetCurrentUserUseCase,
    val sendPasswordReset: SendPasswordResetUseCase
)
