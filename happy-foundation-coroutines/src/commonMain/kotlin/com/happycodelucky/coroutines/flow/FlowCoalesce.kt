package com.happycodelucky.coroutines.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Coalesces multiple emissions into batched list emissions at regular time intervals.
 *
 * Collects upstream emissions into a buffer and emits them as a list when the time
 * window expires. This process repeats for each window, effectively "debouncing"
 * rapid emissions into periodic batches.
 *
 * Use cases:
 * - Reducing UI update frequency from rapid state changes
 * - Batching events for network requests
 * - Preventing over-eventing in event-driven systems
 *
 * Example:
 * ```
 * // Emit batches every 100ms
 * rapidFlow.coalesce(100.milliseconds).collect { batch ->
 *     println("Received ${batch.size} items")
 * }
 * ```
 *
 * Note: Any remaining items in the buffer are emitted when the upstream flow completes.
 *
 * @param window The duration of each collection window
 * @param timeSource The time source used for measuring window duration. Defaults to
 *   [TimeSource.Monotonic]. Can be overridden for testing with a controllable time source.
 * @return A flow that emits lists of coalesced items at each window boundary
 */
fun <T> Flow<T>.coalesce(
    window: Duration,
    timeSource: TimeSource = TimeSource.Monotonic,
): Flow<List<T>> =
    flow {
        val buffer = mutableListOf<T>()
        var windowStart = timeSource.markNow()

        collect { value ->
            buffer.add(value)
            if (windowStart.elapsedNow() >= window) {
                emit(buffer.toList())
                buffer.clear()
                // This is still needed
                windowStart = timeSource.markNow()
            }
        }

        if (buffer.isNotEmpty()) {
            emit(buffer.toList())
        }
    }
