package net.iriscan.sdk.core.tf

import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class InterpreterImpl actual constructor(model: ByteArray) : Interpreter {
    private val interpreter: org.tensorflow.lite.Interpreter

    init {
        val modelBuffer = ByteBuffer.allocateDirect(model.size)
        modelBuffer.order(ByteOrder.nativeOrder())
        modelBuffer.put(model)
        interpreter = org.tensorflow.lite.Interpreter(modelBuffer)
    }

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        interpreter.use {
            it.runForMultipleInputsOutputs(Array(inputs.size) { i -> inputs[i] }, outputs)
        }
    }
}