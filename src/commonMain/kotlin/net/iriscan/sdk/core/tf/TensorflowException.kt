package net.iriscan.sdk.core.tf

/**
 * @author Slava Gornostal
 */
class TensorflowException(val status: Int, override val message: String) : Exception(message)