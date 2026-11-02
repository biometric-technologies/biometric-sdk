package net.iriscan.sdk.tf

/**
 * @author Slava Gornostal
 */
interface Interpreter {
    fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>, traceId: String? = null)
}
