package net.iriscan.sdk.core.tf

/**
 * @author Slava Gornostal
 */
interface Interpreter {
    fun allocateTensors()
    fun invoke()

    fun getTensor(index: Int): Tensor

    fun getInputTensorInt(index: Int): IntInputTensor
    fun getInputTensorFloat(index: Int): FloatInputTensor

    fun getOutputTensorInt(index: Int): IntOutputTensor
    fun getOutputTensorFloat(index: Int): FloatOutputTensor
}

expect class InterpreterImpl(model: ByteArray) : Interpreter