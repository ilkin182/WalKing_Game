package com.example.data.repository

import android.content.Context
import com.example.data.local.StepCounterState
import com.example.domain.repository.StepCounterRepository
import com.example.steps.StepCounterService
import kotlinx.coroutines.flow.Flow

class StepCounterRepositoryImpl(private val context: Context) : StepCounterRepository {
    override val stepCount: Flow<Int> = StepCounterState.stepCount

    override fun start() {
        StepCounterService.start(context)
    }
}
