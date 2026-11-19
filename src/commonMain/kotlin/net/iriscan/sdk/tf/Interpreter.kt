package net.iriscan.sdk.tf

/**
 * @author Slava Gornostal
 *
 * A TensorFlow Lite model, loaded once and run for every call of the operation that owns it.
 *
 * Applications may call SDK operations from several threads, and a TensorFlow Lite interpreter is
 * not thread-safe, so every implementation runs one [invoke] at a time.
 */
interface Interpreter {
    fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>, traceId: String? = null)
}
