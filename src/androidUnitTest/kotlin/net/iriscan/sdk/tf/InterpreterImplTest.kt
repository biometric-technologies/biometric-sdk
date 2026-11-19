package net.iriscan.sdk.tf

import org.tensorflow.lite.InterpreterApi
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The face encoder and the photo liveness check keep one [InterpreterImpl] for all their calls,
 * and an application may call them from several threads, while a TensorFlow Lite interpreter is
 * not thread-safe. The real interpreter needs the native TensorFlow Lite library, which local unit
 * tests do not have, so these tests run [InterpreterImpl] over [StandInModel].
 */
class InterpreterImplTest {

    @Test
    fun callsFromSeveralThreadsRunOneAtATimeAndGetTheirOwnResults() {
        val model = StandInModel()
        val interpreter = InterpreterImpl(model.api)
        val pool = Executors.newFixedThreadPool(THREADS)
        val start = CountDownLatch(1)
        val wrongResults = AtomicInteger()
        val tasks = (0 until THREADS).map { thread ->
            pool.submit {
                start.await()
                repeat(CALLS_PER_THREAD) { call ->
                    val input = (thread * CALLS_PER_THREAD + call).toFloat()
                    val output = FloatArray(1)
                    interpreter.invoke(mapOf(0 to floatArrayOf(input)), mutableMapOf(0 to output))
                    if (output[0] != StandInModel.result(input)) wrongResults.incrementAndGet()
                }
            }
        }
        start.countDown()
        tasks.forEach { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(1, model.mostCallsInside.get(), "calls inside the interpreter at the same time")
        assertEquals(0, wrongResults.get(), "calls that returned the result for another call's input")
    }

    @Test
    fun aCallThatFailsDoesNotBlockLaterCalls() {
        val model = StandInModel()
        val interpreter = InterpreterImpl(model.api)
        assertFailsWith<IllegalArgumentException> {
            interpreter.invoke(mapOf(0 to floatArrayOf(StandInModel.FAILING_INPUT)), mutableMapOf(0 to FloatArray(1)))
        }
        // From another thread: a lock left held by the failed call would still let its own thread in
        val output = FloatArray(1)
        val other = Executors.newSingleThreadExecutor()
        other.submit { interpreter.invoke(mapOf(0 to floatArrayOf(3f)), mutableMapOf(0 to output)) }
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        other.shutdown()
        assertEquals(StandInModel.result(3f), output[0])
    }

    /**
     * Stands in for the TensorFlow Lite interpreter the way it shares state between calls: one input
     * and one output tensor, filled and read in separate steps with the computation in between, as
     * the native interpreter does. The model doubles its input and counts the calls inside it.
     * Only `runForMultipleInputsOutputs`, the method [InterpreterImpl] calls, is implemented.
     */
    private class StandInModel {
        val mostCallsInside = AtomicInteger()
        private val callsInside = AtomicInteger()

        @Volatile
        private var inputTensor = 0f

        @Volatile
        private var outputTensor = 0f

        val api = Proxy.newProxyInstance(
            InterpreterApi::class.java.classLoader,
            arrayOf(InterpreterApi::class.java)
        ) { _, method, args ->
            check(method.name == "runForMultipleInputsOutputs") { "InterpreterImpl does not call ${method.name}" }
            @Suppress("UNCHECKED_CAST")
            run(args[0] as Array<Any?>, args[1] as Map<Int, Any>)
            null
        } as InterpreterApi

        private fun run(inputs: Array<Any?>, outputs: Map<Int, Any>) {
            val now = callsInside.incrementAndGet()
            mostCallsInside.accumulateAndGet(now) { most, current -> maxOf(most, current) }
            try {
                inputTensor = (inputs[0] as FloatArray)[0]
                require(inputTensor != FAILING_INPUT) { "the model failed" }
                // A model computes for milliseconds; a call that comes in meanwhile overwrites the tensors
                Thread.sleep(1)
                outputTensor = result(inputTensor)
                Thread.sleep(1)
                (outputs[0] as FloatArray)[0] = outputTensor
            } finally {
                callsInside.decrementAndGet()
            }
        }

        companion object {
            const val FAILING_INPUT = -1f

            fun result(input: Float) = 2 * input
        }
    }

    private companion object {
        const val THREADS = 4
        const val CALLS_PER_THREAD = 20
        const val TIMEOUT_SECONDS = 30L
    }
}
