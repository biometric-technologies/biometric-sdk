package net.iriscan.sdk.core.tf

/**
 * @author Slava Gornostal
 */
interface Interpreter {
    fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>)
}

expect class InterpreterImpl(modelPath: String) : Interpreter