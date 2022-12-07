package net.iriscan.sdk.core.tf

import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class InterpreterImpl actual constructor(model: ByteArray) : Interpreter {
    private val modelBuffer = ByteBuffer.allocateDirect(model.size)

    init {
        modelBuffer.order(ByteOrder.nativeOrder())
        modelBuffer.put(model)
    }

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        org.tensorflow.lite.Interpreter(modelBuffer).use {
            it.runForMultipleInputsOutputs(Array(inputs.size) { i -> inputs[i] }, outputs)
        }
    }
}