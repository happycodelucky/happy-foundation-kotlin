package com.happycodelucky.coroutines.flow

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

/**
 * Does not produce the same value in a raw, so respect "distinct until changed emissions"
 * */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class DerivedStateFlow<T>(
    private val getValue: () -> T,
    private val flow: Flow<T>,
) : StateFlow<T> {
    override val replayCache: List<T>
        get() = listOf(value)

    override val value: T
        get() = getValue()

    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        coroutineScope { flow.distinctUntilChanged().stateIn(this).collect(collector) }
    }
}

@Deprecated("Prefer using map instead with explicit starting")
fun <T1, R> StateFlow<T1>.mapState(transform: (a: T1) -> R): StateFlow<R> =
    DerivedStateFlow(
        getValue = { transform(this.value) },
        flow = this.map { a -> transform(a) },
    )

@Deprecated("Prefer using mapNotNull instead with explicit starting")
fun <T1, R> StateFlow<T1>.mapNotNullState(transform: (a: T1) -> R): StateFlow<R> =
    DerivedStateFlow(
        getValue = { transform(this.value) },
        flow = this.mapNotNull { a -> transform(a) },
    )

@Deprecated("Prefer using mapLatest instead with explicit starting")
@OptIn(ExperimentalCoroutinesApi::class)
fun <T1, R> StateFlow<T1>.mapLatestState(transform: (a: T1) -> R): StateFlow<R> =
    DerivedStateFlow(
        getValue = { transform(this.value) },
        flow = this.mapLatest { a -> transform(a) },
    )

@Deprecated("Prefer using combine instead with explicit starting")
inline fun <reified T, R> combineStates(
    vararg flows: StateFlow<T>,
    crossinline transform: (Array<T>) -> R,
): StateFlow<R> =
    DerivedStateFlow(
        getValue = { transform(flows.map { it.value }.toTypedArray()) },
        flow = combine(flows = flows) { arr -> transform(arr) },
    )

@Deprecated("Prefer using combine instead with explicit starting")
fun <T1, T2, R> combineStates(
    flow: StateFlow<T1>,
    flow2: StateFlow<T2>,
    transform: (a: T1, b: T2) -> R,
): StateFlow<R> =
    DerivedStateFlow(
        getValue = { transform(flow.value, flow2.value) },
        flow = combine(flow, flow2) { a, b -> transform(a, b) },
    )

@Deprecated("Prefer using combine instead with explicit starting")
fun <T1, T2, T3, R> combineStates(
    flow: StateFlow<T1>,
    flow2: StateFlow<T2>,
    flow3: StateFlow<T3>,
    transform: (a: T1, b: T2, c: T3) -> R,
): StateFlow<R> =
    DerivedStateFlow(
        getValue = { transform(flow.value, flow2.value, flow3.value) },
        flow = combine(flow, flow2, flow3) { a, b, c -> transform(a, b, c) },
    )

@Deprecated("Prefer using combine instead with explicit starting")
fun <T1, T2, T3, T4, R> combineStates(
    flow: StateFlow<T1>,
    flow2: StateFlow<T2>,
    flow3: StateFlow<T3>,
    flow4: StateFlow<T4>,
    transform: (a: T1, b: T2, c: T3, d: T4) -> R,
): StateFlow<R> =
    DerivedStateFlow(
        getValue = { transform(flow.value, flow2.value, flow3.value, flow4.value) },
        flow = combine(flow, flow2, flow3, flow4) { a, b, c, d -> transform(a, b, c, d) },
    )

@Deprecated("Prefer using combine instead with explicit starting")
fun <T1, T2, T3, T4, T5, R> combineStates(
    flow: StateFlow<T1>,
    flow2: StateFlow<T2>,
    flow3: StateFlow<T3>,
    flow4: StateFlow<T4>,
    flow5: StateFlow<T5>,
    transform: (a: T1, b: T2, c: T3, d: T4, e: T5) -> R,
): StateFlow<R> =
    DerivedStateFlow(
        getValue = { transform(flow.value, flow2.value, flow3.value, flow4.value, flow5.value) },
        flow = combine(flow, flow2, flow3, flow4, flow5) { a, b, c, d, e -> transform(a, b, c, d, e) },
    )

// TODO: Add more support as needed
