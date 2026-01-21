package com.happycodelucky.coroutines

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.measureTime

/**
 * Defines the worker interface for use with [CoroutineReusableSharedJob].
 *
 * Implement this interface to provide the logic for executing work, handling completion, and managing invalidation.
 * The worker is responsible for ensuring proper cleanup and resource management during job execution and invalidation.
 *
 * **Key Responsibilities:**
 * - Determine if the work can be executed at the current time.
 * - Execute the actual work asynchronously.
 * - Handle completion of the work, whether successful or failed.
 * - Manage invalidation when the job is restarted or fails.
 *
 * **Lifecycle Notes:**
 * - [canExecuteWork] is checked before starting the job.
 * - [executeWork] is called to perform the actual work.
 * - [onWorkCompleted] is invoked after the work finishes, regardless of success or failure.
 * - [onWorkInvalidated] is called when the job is restarted or fails, allowing for cleanup.
 *
 * **Cancellation:**
 * Ensure that [executeWork] checks for coroutine cancellation to avoid unnecessary processing during job reuse or restart.
 */
interface CoroutineReusableSharedJobWorker<T> {
    /**
     * A label used for debugging and logging purposes.
     * This can help identify the worker instance during execution.
     */
    val label: String?

    /**
     * Determines whether the work can be executed at the current time.
     *
     * If this returns `false`, [executeWork] and [onWorkCompleted] will not be called.
     * Use this to implement conditions for job execution, such as resource availability or state checks.
     *
     * @return `true` if the work can be executed, `false` otherwise.
     */
    fun canExecuteWork(): Boolean = true

    /**
     * Executes the actual work asynchronously.
     *
     * Implement this method to perform the required task. Throw an exception if the work cannot be completed.
     * Ensure that cancellation is handled properly to support job reuse or restarting.
     *
     * @return The result of the work.
     * @throws Exception If the work cannot be completed.
     */
    suspend fun executeWork(): T

    /**
     * Handles the completion of the work.
     *
     * This method is called after the work finishes, regardless of success or failure.
     * Use this to process the result or handle errors.
     *
     * @param result The result of the work, wrapped in a [Result] object.
     */
    suspend fun onWorkCompleted(result: Result<T>)

    /**
     * Called when the work is invalidated.
     *
     * Invalidation occurs when the job is restarted or fails. Use this method to clean up resources
     * or reset the state before the next execution.
     *
     * Note: This is called before [onWorkCompleted] during a restart or after [onWorkCompleted] in case of failure.
     */
    suspend fun onWorkInvalidated()
}

/**
 * Creates a shared synchronized job that is executed only ONCE while the job is active, regardless
 * of how many callers await the result. All callers share a common outcome and can await the job's
 * result - success, cancellation, or other exceptions thrown.
 *
 * **Cancellation:**
 * Each caller is responsible for its own cancellation. If all callers cancel their own context/scope,
 * the job will be cancelled as no interested parties remain. You may force cancellation for all
 * callers using [cancelForAll]. Use this with care, as all awaiting callers will receive a
 * cancellation exception.
 *
 * **Restarting:**
 * Any caller can restart the job by calling [start] with [StartMode.ForceRestart]. This will
 * invalidate (cancel) the existing active job and restart it, without throwing a cancellation
 * exception to all awaiting caller. Restarting is useful when new information invalidates the
 * prior job's starting conditions. This ensures all awaiting callers get the most up-to-date result
 * without having to monitor and restart all conditions itself.
 *
 * @property worker Callbacks for the actual work to be performed. These are called on the same coroutine context.
 * @param scope An optional coroutine scope to align cancellation to
 */
class CoroutineReusableSharedJob<T: Any>(
    private val worker: CoroutineReusableSharedJobWorker<T>,
    scope: CoroutineScope? = null
) {
    companion object {
        private const val TAG = "CoroutineReusableSharedJob"

        private var jobSerialCounter: AtomicLong = AtomicLong(0)
    }

    /**
     * Serial ID of the job, used for debugging and logging
     */
    private val jobSerialId: Long = jobSerialCounter.addAndFetch(1)

    /**
     * Logging tag
     */
    private val TAG: String = "${CoroutineReusableSharedJob.TAG}-$jobSerialId"

    /**
     * Worker label used for debugging and logging
     */
    private val workerLabel: String = "${worker.label ?: "CoroutineReusableSharedJob"}-$jobSerialId"

    /**
     * Synchronization mutex for critical sync sections
     */
    private val mutex: Mutex = Mutex()

    /**
     * Scope to use when performing the work
     */
    private val coroutineScope = (scope ?: CoroutineScope(Dispatchers.Default)) + CoroutineName(workerLabel)


    /**
     * Result of the shared job
     */
    private var sharedDeferredCompletable: CompletableDeferred<T>? = null

    /**
     * When internally restarting we do not want to complete [sharedDeferredCompletable]
     * exceptionally. This signals across coroutine contexts that we're restarting.
     */
    private val restartSignal: AtomicBoolean = AtomicBoolean(false)

    /**
     * Number of active callers awaiting the result from [start]
     *
     * Once this goes to zero any outstanding job will be cancelled
     */
    private val awaiterCounter = AtomicInt(0)

    /**
     * Actual job that is running the work
     *
     * Tracked to cancel the job when needing to force a restart
     */
    private var workJob: Job? = null

    //
    // Public properties
    //

    /**
     * Is the job currently active and running
     */
    val isActive: Boolean
        get() = sharedDeferredCompletable?.isActive ?: false

    /**
     * Has the job completed successfully, or with an exception
     */
    val isComplete: Boolean
        get() = sharedDeferredCompletable?.isCompleted ?: false

    /**
     * Has the job been cancelled, either by the caller or by the system
     */
    val isCancelled: Boolean
        get() = sharedDeferredCompletable?.isCancelled ?: false

    //
    // Constructor
    //

    /**
     * @param worker Callbacks for the actual work to be performed. These are called on the same coroutine context.
     * @param dispatcher Context for the worker, defaults to [Dispatchers.Default].
     */
    constructor(worker: CoroutineReusableSharedJobWorker<T>, dispatcher: CoroutineDispatcher) :
            this(worker = worker, scope = CoroutineScope(dispatcher))

    //
    // Public functions
    //

    enum class StartMode {
        /**
         * Only restarts if the reusable job if it has already completed, either successfully or
         * with an exception
         */
        RestartIfCompleted,

        /**
         * Always restart the shard job regardless of its state, of running, complete, or cancelled
         */
        ForceRestart,
    }

    interface StartOptionsBuilder {
        /**
         * Starting mode for the job.
         *
         * @default [StartMode.RestartIfCompleted]
         */
        var mode: StartMode
    }

    /**
     * Determines if the the shared job can start (execute it's work)
     *
     * If any caller of [start] is trying to recover from a cancellation or failure, check this to
     * ensure you do not end up in a loop of starting the job when it is not ready to be.
     */
    fun canStart(): Boolean {
        // Check if the worker can execute work, or if the work is already active
        //
        // Note: isActive is checked because once work begins a worker could be in a state where
        //       it reports it cannot execute work. But if work is active, allow other callers to
        //       join and await the result.
        return isActive || worker.canExecuteWork()
    }

    /**
     * Starts the job, or joins the existing job if it is already running
     *
     * Use the start options to control the behaviour of the job, including if the job should be
     * restarted
     *
     * @param options Optional start options to control the behaviour of the job
     */
    suspend fun start(options: (StartOptionsBuilder.() -> Unit)? = null): T {
        Logger.v(TAG) { "🔐Locking mutex" }

        val deferredResult = mutex.withLock {
            Logger.d(TAG) { "Scheduling job $workerLabel" }

            val resolvedOptions = object : StartOptionsBuilder {
                override var mode: StartMode = StartMode.RestartIfCompleted
            }.apply {
                options?.invoke(this)
            }

            // Check if we should restart the job
            var existingDeferred = sharedDeferredCompletable
            if (existingDeferred != null) {
                when (resolvedOptions.mode) {
                    StartMode.ForceRestart -> {
                        Logger.d(TAG) { "Restarting already active work for $workerLabel" }

                        // We're restarting the job
                        restartSignal.store(true)

                        measureTime {
                            // Cancel all the child jobs to prepare for restart
                            workJob?.cancelAndJoin() //CancellationException("Job was invalidated and being restarted"))
                            workJob = null
                        }.also { elapsedTime ->
                            Logger.d(TAG) { "Cancelled existing work for $workerLabel in ${elapsedTime.inWholeMilliseconds}ms" }
                        }

                        // Restarting, invalidate the existing work
                        // NOT REQUIRED: This is handled in the failure case for cancellations
                        //               Also doesn't require context switch to worker context
                        // worker.onWorkInvalidated()
                    }

                    StartMode.RestartIfCompleted -> {
                        if (existingDeferred.isCompleted) {
                            existingDeferred = null // Clear the deferred result to allow a new job to be started
                        } else {
                            Logger.d(TAG) { "Reusing existing active work for $workerLabel" }

                            // If we are reusing an active job, we can just await the result
                            return@withLock existingDeferred // we await after unlocking the mutex
                        }
                    }
                }
            }

            Logger.d(TAG) { "Starting active work for $workerLabel" }

            // If we have a existing deferred we'll reuse this as all other callers will be
            // waiting on it for a result.
            val newDeferred = existingDeferred ?: CompletableDeferred()
            sharedDeferredCompletable = newDeferred

            // If we are here, we have a deferred result to work with
            // We can now start the job and perform the work
            workJob = coroutineScope.launch {
                var markedAsCompleted = false
                try {
                    // Reset the restart signal, we're restarting
                    restartSignal.store(false)

                    // Check there is work to perform
                    if (!worker.canExecuteWork()) {
                        Logger.w(TAG) { "Unable to execute work for $workerLabel as its reporting it cannot be started" }

                        // No work to perform, so we mark it as completed and not call [onWorkCompleted]
                        // in failure handler
                        markedAsCompleted = true

                        // When there is no work to execute, there's nothing to do
                        // We intentionally do not call callbacks.onWorkCompleted() as callbacks.doWork is not called
                        throw CancellationException("There was no work to be performed")
                    }

                    Logger.d(TAG) { "Executing work for $workerLabel" }

                    lateinit var result: T
                    measureTime {
                        result = worker.executeWork()
                    }.also { elapsedTime ->
                        Logger.d(TAG) { "Work completed for $workerLabel in ${elapsedTime.inWholeMilliseconds}ms" }
                    }
                    // Just in case we have bad worker implementation
                    ensureActive()

                    worker.onWorkCompleted(Result.success(result))
                    markedAsCompleted = true

                    // Even though we've done everything successfully, cancellation is still a
                    // cancellation. Prevent any further work from being done
                    ensureActive()

                    // If a restart was requested, do not complete the deferred yet
                    if (!restartSignal.load()) {
                        // Late thing, mark the deferred as complete
                        newDeferred.complete(result)

                        Logger.d(TAG) { "Work complete successfully for $workerLabel" }
                    }
                } catch (e: Exception) {
                    // When restarting, we need to ensure we handle failures gracefully because they
                    // are not regular cancellations
                    if (restartSignal.load()) {
                        Logger.d(TAG) { "Work aborted due to restart $workerLabel. Cleaning up." }

                        // Perform invalidation (aka clean up)
                        runCatching {
                            worker.onWorkInvalidated()
                        }.onFailure {
                            Logger.e(TAG, it) { "Failed to complete onWorkInvalidated for $workerLabel" }
                        }

                        // Note: Skipping completion of newDeferred as we're restarting
                    } else {
                        Logger.d(TAG, e) { "Work failing to complete for $workerLabel" }

                        if (!markedAsCompleted) {
                            runCatching {
                                worker.onWorkCompleted(Result.failure(e))
                            }.onFailure {
                                Logger.e(TAG, it) { "Failed to complete onWorkCompleted for $workerLabel" }
                            }
                        }

                        // Perform invalidation (aka clean up)
                        runCatching {
                            worker.onWorkInvalidated()
                        }.onFailure {
                            Logger.e(TAG, it) { "Failed to complete onWorkInvalidated for $workerLabel" }
                        }

                        // Last thing, mark the deferred as failed
                        newDeferred.completeExceptionally(e)
                    }
                }
            }

            newDeferred
        }

        Logger.v(TAG) { "🔓Unlocked mutex" }

        // Maintain a counter of awaiting callers. This allows us to cancel the job if no one is
        // awaiting the result from [start]
        awaiterCounter.incrementAndFetch()
        try {
            return deferredResult.await()
        } finally {
            if (awaiterCounter.decrementAndFetch() == 0 && !deferredResult.isCompleted) {
                mutex.withLock {
                    // Re-check after locking
                    if (awaiterCounter.load() == 0 && sharedDeferredCompletable?.isCompleted == false) {
                        Logger.i(TAG) { "All callers cancelled, cancelling work job $workerLabel" }
                        workJob?.cancelAndJoin()
                        workJob = null
                    }
                }
            }
        }
    }

    /**
     * Cancels the shared job for **all** awaiting callers
     *
     * Use this with caution as other callers awaiting will be notified of the cancellation and
     * manually have to restart the job if they depend on the output
     *
     * @see StartMode.ForceRestart
     */
    @DelicateCoroutinesApi
    suspend fun cancelForAll() {
        Logger.i(TAG) { "Cancelling job for all awaiting callers: $workerLabel" }

        mutex.withLock {
            sharedDeferredCompletable?.cancel(
                cause = CancellationException("Job $workerLabel explicitly cancelled for all awaiting callers")
            )

            // Clear, just in case
            restartSignal.store(false)

            // Cancel the actual work outstanding
            workJob?.cancelAndJoin()
            workJob = null
        }
    }
}
