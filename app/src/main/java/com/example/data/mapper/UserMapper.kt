package com.example.data.mapper

import com.example.domain.model.User
import com.google.firebase.auth.FirebaseUser

fun FirebaseUser.toDomain(): User = User(
    uid = uid,
    email = email.orEmpty()
)
