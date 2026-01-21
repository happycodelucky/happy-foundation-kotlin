package com.happycodelucky.coroutines

import com.happycodelucky.coroutines.CoroutineReusableSharedJob.StartMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class CoroutineReusableSharedJobTest {

    private class TestWorker(
        private val workResult: Result<String> = Result.success("Success"),
        private val workDelayMillis: Long = 100,
        private val canExecute: () -> Boolean = { true }
    ) : CoroutineReusableSharedJobWorker<String> {
        override val label: String = "TestWorker"

        val executeWorkCallCount = AtomicInt(0)
        val onWorkCompletedCallCount = AtomicInt(0)
        val onWorkInvalidatedCallCount = AtomicInt(0)
        val lastWorkCompletedResult = AtomicReference<Result<String>?>(null)

        override fun canExecuteWork(): Boolean = canExecute()

        override suspend fun executeWork(): String {
            executeWorkCallCount.addAndFetch(1)
            delay(workDelayMillis)
            return workResult.getOrThrow()
        }

        override suspend fun onWorkCompleted(result: Result<String>) {
            onWorkCompletedCallCount.addAndFetch(1)
            lastWorkCompletedResult.store(result)
        }

        override suspend fun onWorkInvalidated() {
            onWorkInvalidatedCallCount.addAndFetch(1)
        }
    }

    @Test
    fun `start should execute work and return successful result`() = runTest {
        val worker = TestWorker(workResult = Result.success("Done"))
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        val result = job.start()

        assertEquals("Done", result)
        assertEquals(1, worker.executeWorkCallCount.load())
        assertEquals(1, worker.onWorkCompletedCallCount.load())
        assertEquals(0, worker.onWorkInvalidatedCallCount.load())
        assertTrue(worker.lastWorkCompletedResult.load()?.isSuccess ?: false)
        assertFalse(job.isActive)
        assertTrue(job.isComplete)
        assertFalse(job.isCancelled)
    }

    @Test
    fun `start should propagate exception from worker`() = runTest {
        val exception = IllegalStateException("Work failed")
        val worker = TestWorker(workResult = Result.failure(exception))
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        val thrown = assertFailsWith<IllegalStateException> {
            job.start()
        }

        // Due to structured concurrency, the exception thrown is not the same reference
        assertEquals(exception::class, thrown::class)
        assertEquals(exception.message, thrown.message)

        assertEquals(1, worker.executeWorkCallCount.load())
        assertEquals(1, worker.onWorkCompletedCallCount.load())
        assertEquals(1, worker.onWorkInvalidatedCallCount.load())
        assertTrue(worker.lastWorkCompletedResult.load()?.isFailure ?: false)
    }

    @Test
    fun `multiple callers should await the same job and get same result`() = runTest {
        val worker = TestWorker(workResult = Result.success("SharedResult"), workDelayMillis = 1000)
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        val deferred1 = async { job.start() }
        advanceTimeBy(100) // Ensure the first job's work has started
        val deferred2 = async { job.start() }
        val deferred3 = async { job.start() }

        val result1 = deferred1.await()
        val result2 = deferred2.await()
        val result3 = deferred3.await()

        assertEquals("SharedResult", result1)
        assertEquals(result1, result2)
        assertEquals(result1, result3)
        assertEquals(1, worker.executeWorkCallCount.load())
        assertEquals(1, worker.onWorkCompletedCallCount.load())
    }

    @Test
    fun `when canExecuteWork is false start should throw CancellationException`() = runTest {
        val worker = TestWorker(canExecute = { false })
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        assertFailsWith<CancellationException> {
            job.start()
        }

        assertEquals(0, worker.executeWorkCallCount.load())
        assertEquals(0, worker.onWorkCompletedCallCount.load())
        assertEquals(1, worker.onWorkInvalidatedCallCount.load()) // onWorkInvalidated is called on failure
    }

    @Test
    fun `RestartIfCompleted should not restart an active job`() = runTest {
        val worker = TestWorker()
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        // Start the job, but don't wait for it to finish
        val deferred1 = async { job.start() }
        runCurrent() // Let the job start

        assertTrue(job.isActive)

        // This call should just wait for the existing job
        val deferred2 = async { job.start { mode = StartMode.RestartIfCompleted } }

        deferred1.await()
        deferred2.await()

        assertEquals(1, worker.executeWorkCallCount.load())
    }

    @Test
    fun `RestartIfCompleted should restart a completed job`() = runTest {
        val worker = TestWorker()
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        // First run
        job.start()
        assertEquals(1, worker.executeWorkCallCount.load())
        assertTrue(job.isComplete)

        // Second run
        job.start { mode = StartMode.RestartIfCompleted }
        assertEquals(2, worker.executeWorkCallCount.load())
    }

    @Test
    fun `ForceRestart should cancel and restart an active job`() = runTest {
        val worker = TestWorker(workDelayMillis = 5000) // Long delay
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        // Start the first job
        val deferred1 = async { job.start() }
        advanceTimeBy(100) // Ensure the first job's work has started
        runCurrent()

        assertTrue(job.isActive)
        assertEquals(1, worker.executeWorkCallCount.load())
        assertEquals(0, worker.onWorkInvalidatedCallCount.load())

        // Restart it while it's active
        val deferred2 = async { job.start { mode = StartMode.ForceRestart } }
        advanceTimeBy(100) // Ensure the first job's work has started
        runCurrent() // Allow restart logic to execute

        // The first job should be cancelled, and onWorkInvalidated called for it
        assertEquals(0, worker.onWorkCompletedCallCount.load())
        assertEquals(1, worker.onWorkInvalidatedCallCount.load())

        // The second job should complete successfully
        val result2 = deferred2.await()
        assertEquals("Success", result2)
        assertEquals(2, worker.executeWorkCallCount.load())
    }

    @Test
    fun `ForceRestart allows existing awaiters to receive restarted result`() = runTest {
        var resultValue = "First"
        val worker = TestWorker(workDelayMillis = 5000)
        val job = CoroutineReusableSharedJob(
            object : CoroutineReusableSharedJobWorker<String> {
                override val label: String = "DynamicWorker"
                override fun canExecuteWork(): Boolean = true
                override suspend fun executeWork(): String {
                    delay(1000)
                    return resultValue
                }
                override suspend fun onWorkCompleted(result: Result<String>) {}
                override suspend fun onWorkInvalidated() {}
            },
            scope = CoroutineScope(coroutineContext)
        )

        // Start the first awaiter
        val deferred1 = async { job.start() }
        advanceTimeBy(100) // Let the job start
        runCurrent()

        assertTrue(job.isActive)

        // Change the result for the restart
        resultValue = "Restarted"

        // ForceRestart should cancel the first job and start a new one
        // Both awaiters should receive the new result (not a CancellationException)
        val deferred2 = async { job.start { mode = StartMode.ForceRestart } }
        advanceTimeBy(100)
        runCurrent()

        // Both awaiters should receive the restarted result
        val result1 = deferred1.await()
        val result2 = deferred2.await()

        assertEquals("Restarted", result1, "First awaiter should receive the restarted result")
        assertEquals("Restarted", result2, "Second awaiter should receive the restarted result")
    }

    @Test
    fun `ForceRestart on completed job returns cached result`() = runTest {
        // Note: ForceRestart on a completed job does NOT restart - it returns the cached result.
        // This differs from RestartIfCompleted which explicitly checks isCompleted and clears the deferred.
        // ForceRestart is designed for active jobs, not completed ones.
        val worker = TestWorker()
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        // First run to completion
        job.start()
        assertEquals(1, worker.executeWorkCallCount.load())
        assertTrue(job.isComplete)

        // ForceRestart on a completed job does NOT restart - returns cached result
        val result = job.start { mode = StartMode.ForceRestart }
        assertEquals("Success", result) // Returns cached result
        assertEquals(1, worker.executeWorkCallCount.load()) // Work was NOT re-executed
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun `cancelForAll should cancel an active job for all callers`() = runTest {
        val worker = TestWorker(workDelayMillis = 5000) // Long delay
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        val deferred1 = async { job.start() }
        val deferred2 = async { job.start() }
        advanceTimeBy(100) // Let the job start
        runCurrent()

        assertTrue(job.isActive)

        job.cancelForAll()
        yield() // Allow cancellation to propagate

        assertTrue(job.isCancelled)
        assertTrue(job.isComplete)
        assertFalse(job.isActive)
        assertFailsWith<CancellationException> { deferred1.await() }
        assertFailsWith<CancellationException> { deferred2.await() }

        // onWorkCompleted is called with failure, then onWorkInvalidated
        assertEquals(1, worker.onWorkCompletedCallCount.load())
        assertNotNull(worker.lastWorkCompletedResult.load())
        assertTrue(worker.lastWorkCompletedResult.load()!!.isFailure)
        assertEquals(1, worker.onWorkInvalidatedCallCount.load())
    }

    @Test
    fun `canStart returns true when worker can execute and job is not active`() {
        val worker = TestWorker(canExecute = { true })
        val job = CoroutineReusableSharedJob(worker)
        assertTrue(job.canStart())
    }

    @Test
    fun `canStart returns false when worker cannot execute and job is not active`() {
        val worker = TestWorker(canExecute = { false })
        val job = CoroutineReusableSharedJob(worker)
        assertFalse(job.canStart())
    }

    @Test
    fun `canStart returns true when job is active even if worker cannot execute`() = runTest {
        val canExecute = AtomicInt(1)
        val worker = TestWorker(canExecute = { canExecute.load() == 1 })
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        val deferred = async {
            job.start()
        }
        runCurrent() // Start the job
        assertTrue(job.isActive)
        canExecute.store(0) // Simulate state change where worker can no longer execute

        assertTrue(job.canStart(), "canStart should be true for an active job")

        deferred.await()
    }

    @Test
    fun `canStart returns correct value after job is completed`() = runTest {
        val canExecute = AtomicInt(1)
        val worker = TestWorker(canExecute = { canExecute.load() == 1 })
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        job.start() // Run to completion

        assertFalse(job.isActive)
        assertTrue(job.isComplete)
        assertTrue(job.canStart(), "Should be able to start again if worker allows")

        canExecute.store(0)
        assertFalse(job.canStart(), "Should not be able to start again if worker disallows")
    }

    @Test
    fun `work job is cancelled when single caller cancels`() = runTest {
        val worker = TestWorker(workDelayMillis = 5000)
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        val deferred = async { job.start() }
        runCurrent() // Start the job

        assertTrue(job.isActive)
        assertEquals(1, worker.executeWorkCallCount.load())

        deferred.cancel() // Cancel the caller
        runCurrent() // Allow cancellation to propagate

        // The work job should be cancelled because there are no more awaiters
        assertTrue(job.isCancelled, "Job should be cancelled")
        assertTrue(job.isComplete, "Job should be complete")
        assertEquals(1, worker.onWorkInvalidatedCallCount.load(), "onWorkInvalidated should be called")
        assertEquals(1, worker.onWorkCompletedCallCount.load(), "onWorkCompleted should be called on failure")
        assertTrue(worker.lastWorkCompletedResult.load()?.isFailure ?: false, "Work result should be failure")
    }

    @Test
    fun `work job is cancelled when all multiple callers cancel`() = runTest {
        val worker = TestWorker(workDelayMillis = 5000)
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        val deferred1 = async { job.start() }
        val deferred2 = async { job.start() }
        runCurrent() // Start the job

        assertTrue(job.isActive)
        assertEquals(1, worker.executeWorkCallCount.load())

        deferred1.cancel()
        deferred2.cancel()
        runCurrent() // Allow cancellation to propagate

        // The work job should be cancelled because there are no more awaiters
        assertTrue(job.isCancelled, "Job should be cancelled")
        assertTrue(job.isComplete, "Job should be complete")
        assertEquals(1, worker.onWorkInvalidatedCallCount.load(), "onWorkInvalidated should be called")
        assertEquals(1, worker.onWorkCompletedCallCount.load(), "onWorkCompleted should be called on failure")
        assertTrue(worker.lastWorkCompletedResult.load()?.isFailure ?: false, "Work result should be failure")
    }

    @Test
    fun `work job is not cancelled if one of multiple callers remains active`() = runTest {
        val worker = TestWorker(workDelayMillis = 1000)
        val job = CoroutineReusableSharedJob(worker, scope = CoroutineScope(coroutineContext))

        val deferred1 = async { job.start() }
        val deferred2 = async { job.start() }
        runCurrent() // Start the job

        assertTrue(job.isActive)

        deferred1.cancel() // Cancel one caller
        runCurrent()

        // The job should remain active as deferred2 is still awaiting
        assertTrue(job.isActive)
        assertEquals(0, worker.onWorkInvalidatedCallCount.load())

        // The remaining caller should get the result
        val result = deferred2.await()
        assertEquals("Success", result)
        assertTrue(job.isComplete)
        assertFalse(job.isCancelled)
        assertEquals(1, worker.onWorkCompletedCallCount.load())
        assertEquals(0, worker.onWorkInvalidatedCallCount.load())
    }

    @Test
    fun `exception in onWorkCompleted on failure path is caught and logged`() = runTest {
        val workException = IllegalStateException("Work failed")
        val callbackException = RuntimeException("Callback failed")
        val job = CoroutineReusableSharedJob(
            object : CoroutineReusableSharedJobWorker<String> {
                override val label: String = "ThrowingCallbackWorker"
                override fun canExecuteWork(): Boolean = true
                override suspend fun executeWork(): String = throw workException
                override suspend fun onWorkCompleted(result: Result<String>) {
                    throw callbackException // This should be caught and logged
                }
                override suspend fun onWorkInvalidated() {}
            },
            scope = CoroutineScope(coroutineContext)
        )

        // The original work exception should propagate, not the callback exception
        val thrown = assertFailsWith<IllegalStateException> {
            job.start()
        }

        assertEquals(workException.message, thrown.message)
    }

    @Test
    fun `exception in onWorkInvalidated on failure path is caught and logged`() = runTest {
        val workException = IllegalStateException("Work failed")
        val invalidatedCallCount = AtomicInt(0)
        val job = CoroutineReusableSharedJob(
            object : CoroutineReusableSharedJobWorker<String> {
                override val label: String = "ThrowingInvalidatedWorker"
                override fun canExecuteWork(): Boolean = true
                override suspend fun executeWork(): String = throw workException
                override suspend fun onWorkCompleted(result: Result<String>) {}
                override suspend fun onWorkInvalidated() {
                    invalidatedCallCount.addAndFetch(1)
                    throw RuntimeException("Invalidated callback failed") // Should be caught
                }
            },
            scope = CoroutineScope(coroutineContext)
        )

        // The original work exception should propagate
        val thrown = assertFailsWith<IllegalStateException> {
            job.start()
        }

        assertEquals(workException.message, thrown.message)
        assertEquals(1, invalidatedCallCount.load()) // onWorkInvalidated should have been called
    }
}