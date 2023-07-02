package net.iriscan.sdk.tf

import net.iriscan.sdk.core.io.HashMethod

/**
 * @author Slava Gornostal
 */
interface Interpreter {
    fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>)
}

expect class InterpreterImpl(
    modelName: String,
    modelPath: String,
    modelChecksum: String?,
    modelChecksumMethod: HashMethod?,
    overrideCacheOnWrongChecksum: Boolean?
) : Interpreter