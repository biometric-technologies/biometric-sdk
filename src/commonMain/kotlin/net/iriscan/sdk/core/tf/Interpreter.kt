package net.iriscan.sdk.core.tf

/**
 * @author Slava Gornostal
 */
interface Interpreter {
    fun allocateTensors()
    fun invoke()
    fun getInputTensor(index: Int): InputTensor
    fun getOutputTensor(index: Int): OutputTensor
}

expect class InterpreterImpl(model: ByteArray) : Interpreter