package com.example.domain.usecase

import app.cash.turbine.test
import com.example.domain.repository.StepCounterRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StepCounterUseCasesTest {
    private val repository: StepCounterRepository = mockk()

    @Test
    fun `ObserveStepCountUseCase delegates to the repository flow`() = runTest {
        every { repository.stepCount } returns flowOf(150)

        ObserveStepCountUseCase(repository)().test {
            assertEquals(150, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `StartStepCounterUseCase delegates to the repository`() {
        every { repository.start() } returns Unit

        StartStepCounterUseCase(repository)()

        verify(exactly = 1) { repository.start() }
    }
}
