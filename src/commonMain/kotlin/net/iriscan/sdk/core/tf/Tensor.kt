package net.iriscan.sdk.core.tf

/**
 * @author Slava Gornostal
 */
sealed interface Tensor {
    fun getShape(): IntArray
    fun setShape(shape: IntArray)
}

interface InputTensor : Tensor {
    fun setBytes(value: ByteArray)
    fun setInt(value: Int)
    fun setIntArray(value: IntArray)
    fun setLong(value: Long)
    fun setLongArray(value: LongArray)
    fun setFloat(value: Float)
    fun setFloatArray(value: FloatArray)
}

interface OutputTensor : Tensor {
    fun getBytes(): ByteArray
    fun getInt(): Int
    fun getIntArray(): IntArray
    fun getLong(): Long
    fun getLongArray(): LongArray
    fun getFloat(): Float
    fun getFloatArray(): FloatArray
}

expect class InputTensorImpl : InputTensor

expect class OutputTensorImpl : OutputTensor