package com.example.domain.repository

import kotlinx.coroutines.flow.Flow

interface StepCounterRepository {
    val stepCount: Flow<Int>

    fun start()
}
