package net.iriscan.sdk.core.tf

import java.nio.ByteBuffer

actual class InterpreterImpl actual constructor(model: ByteArray) : Interpreter {
    private val wrappedModel = ByteBuffer.wrap(model)
    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        org.tensorflow.lite.Interpreter(wrappedModel).use {
            it.runForMultipleInputsOutputs(Array(inputs.size) { i -> inputs[i] }, outputs)
        }
    }
}