package net.iriscan.sdk.utils

import platform.Foundation.NSLock
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_async
import platform.darwin.dispatch_group_create
import platform.darwin.dispatch_group_enter
import platform.darwin.dispatch_group_leave
import platform.darwin.dispatch_group_wait
import platform.darwin.dispatch_queue_create
import platform.posix.usleep
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The iOS interpreter runs every model call inside [withLock], because a TFLInterpreter is not
 * thread-safe and the face encoder and the photo liveness check keep one for all their calls.
 * The interpreter itself cannot run here (the iOS test executables do not link TensorFlow Lite),
 * so these tests check the two things it relies on: calls from several threads run one at a time,
 * and a call that throws leaves the lock free for the next one.
 */
@OptIn(ExperimentalAtomicApi::class)
class LockExtTest {

    @Test
    fun callsFromSeveralThreadsRunOneAtATime() {
        val lock = NSLock()
        val callsInside = AtomicInt(0)
        val mostCallsInside = AtomicInt(0)
        val callsDone = AtomicInt(0)
        val group = dispatch_group_create()
        repeat(THREADS) { thread ->
            // one serial queue per caller, so that the callers run on different threads at once
            val queue = dispatch_queue_create("net.iriscan.sdk.test.lock-$thread", null)
            dispatch_group_enter(group)
            dispatch_async(queue) {
                try {
                    repeat(CALLS_PER_THREAD) {
                        lock.withLock {
                            val now = callsInside.addAndFetch(1)
                            while (true) {
                                val most = mostCallsInside.load()
                                if (now <= most || mostCallsInside.compareAndSet(most, now)) break
                            }
                            // a model call holds the interpreter for milliseconds
                            usleep(HOLD_MICROSECONDS)
                            callsInside.addAndFetch(-1)
                        }
                        callsDone.addAndFetch(1)
                    }
                } finally {
                    dispatch_group_leave(group)
                }
            }
        }
        dispatch_group_wait(group, DISPATCH_TIME_FOREVER)
        assertEquals(THREADS * CALLS_PER_THREAD, callsDone.load(), "calls that finished")
        assertEquals(1, mostCallsInside.load(), "calls inside the lock at the same time")
    }

    @Test
    fun aCallThatThrowsLeavesTheLockFree() {
        val lock = NSLock()
        assertFailsWith<IllegalStateException> {
            lock.withLock { error("the model failed") }
        }
        assertTrue(lock.tryLock(), "the lock is still held after the call threw")
        lock.unlock()
    }

    @Test
    fun returnsWhatTheCallReturns() {
        assertEquals(42, NSLock().withLock { 42 })
    }

    private companion object {
        const val THREADS = 4
        const val CALLS_PER_THREAD = 10
        const val HOLD_MICROSECONDS = 1_000u
    }
}
