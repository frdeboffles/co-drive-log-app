package com.codrivelog.app.service

import com.codrivelog.app.location.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectLocationWithRestartTest {

    @Test
    fun `location updates are retried after they end or fail`() = runTest {
        // Review finding 2: location off at start (the flow ends), then the
        // permission is refused once (the flow fails), then location works.
        val fix = LatLng(39.74, -104.99, 4f)
        val attempts = mutableListOf(
            emptyFlow(),
            flow<LatLng> { throw SecurityException("refused") },
            flowOf(fix),
        )
        val errors = mutableListOf<Exception>()
        val fixes = mutableListOf<LatLng>()

        val job = launch {
            collectLocationWithRestart(
                updates        = { attempts.removeFirstOrNull() ?: emptyFlow() },
                restartDelayMs = 10_000L,
                onError        = { errors += it },
            ) { fixes += it }
        }
        runCurrent()
        advanceTimeBy(10_001)
        advanceTimeBy(10_001)
        job.cancel()

        assertEquals(listOf(fix), fixes)
        assertEquals(1, errors.size)
    }

    @Test
    fun `a cancellation from inside the flow is retried while the drive runs`() = runTest {
        // Review finding 8.
        val fix = LatLng(39.74, -104.99, 4f)
        val attempts = mutableListOf(
            flow<LatLng> { throw kotlinx.coroutines.CancellationException("provider gone") },
            flowOf(fix),
        )
        val fixes = mutableListOf<LatLng>()
        var beforeAttempts = 0

        val job = launch {
            collectLocationWithRestart(
                updates        = { attempts.removeFirstOrNull() ?: emptyFlow() },
                restartDelayMs = 10_000L,
                onError        = {},
                beforeAttempt  = { beforeAttempts++ },
            ) { fixes += it }
        }
        runCurrent()
        advanceTimeBy(10_001)
        job.cancel()

        assertEquals(listOf(fix), fixes)
        // Review finding 7: the foreground type is checked before every attempt.
        assertEquals(2, beforeAttempts)
    }
}
