package net.iriscan.sdk.core.tf

/**
 * @author Slava Gornostal
 */
interface Interpreter {
    fun allocateTensors()
    fun invoke()
    fun getTensor(index: Int): Tensor
    fun <T : Any > getInputTypedTensor(index: Int): InputTensor<T>
    fun <T : Any> getOutputTypedTensor(index: Int): OutputTensor<T>
}

expect class InterpreterImpl(model: ByteArray) : Interpreter