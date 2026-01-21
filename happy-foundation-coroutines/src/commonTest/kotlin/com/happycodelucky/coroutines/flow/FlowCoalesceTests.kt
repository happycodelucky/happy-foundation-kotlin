package com.happycodelucky.coroutines.flow

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * Tests for the [coalesce] flow extension function.
 *
 * These tests use [TestTimeSource] which allows controlling time advancement
 * without real delays, making tests fast and deterministic.
 */
class FlowCoalesceTests {

    @Test
    fun `empty flow emits nothing`() = runTest {
        val timeSource = TestTimeSource()
        val result = emptyFlow<Int>().coalesce(100.milliseconds, timeSource).toList()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `single item emits as single-item list on completion`() = runTest {
        val timeSource = TestTimeSource()
        val result = flowOf(1).coalesce(100.milliseconds, timeSource).toList()

        assertEquals(listOf(listOf(1)), result)
    }

    @Test
    fun `items within window are batched together on completion`() = runTest {
        val timeSource = TestTimeSource()
        // All items emitted instantly, well within any reasonable window
        val result = flowOf(1, 2, 3).coalesce(1000.milliseconds, timeSource).toList()

        assertEquals(listOf(listOf(1, 2, 3)), result)
    }

    @Test
    fun `items emitted after window expires are in separate batches`() = runTest {
        val timeSource = TestTimeSource()
        val window = 50.milliseconds

        val result = flow {
            emit(1)
            emit(2)
            timeSource += 70.milliseconds // Exceed the window
            emit(3) // This triggers the first batch emission, then starts a new window
            emit(4)
        }.coalesce(window, timeSource).toList()

        // First batch: [1, 2, 3] - emitted when item 3 arrives after window expired
        // Second batch: [4] - remaining items emitted on completion
        assertEquals(2, result.size)
        assertEquals(listOf(1, 2, 3), result[0])
        assertEquals(listOf(4), result[1])
    }

    @Test
    fun `window resets after each emission allowing multiple batches`() = runTest {
        val timeSource = TestTimeSource()
        val window = 50.milliseconds

        val result = flow {
            // First window
            emit(1)
            timeSource += 70.milliseconds
            emit(2) // Triggers batch [1, 2]

            // Second window should start fresh
            timeSource += 70.milliseconds
            emit(3) // Triggers batch [3]

            // Third window
            emit(4)
            emit(5)
        }.coalesce(window, timeSource).toList()

        // Should have 3 batches demonstrating window reset works
        assertEquals(3, result.size)
        assertEquals(listOf(1, 2), result[0])
        assertEquals(listOf(3), result[1])
        assertEquals(listOf(4, 5), result[2])
    }

    @Test
    fun `remaining items emitted when upstream completes`() = runTest {
        val timeSource = TestTimeSource()

        val result = flow {
            emit(1)
            emit(2)
            // Flow completes without exceeding window
        }.coalesce(1000.milliseconds, timeSource).toList()

        assertEquals(listOf(listOf(1, 2)), result)
    }

    @Test
    fun `rapid emissions within window are all batched`() = runTest {
        val timeSource = TestTimeSource()
        val items = (1..100).toList()
        val result = flowOf(*items.toTypedArray()).coalesce(1000.milliseconds, timeSource).toList()

        // All 100 items should be in a single batch since they emit instantly
        assertEquals(1, result.size)
        assertEquals(items, result[0])
    }
}