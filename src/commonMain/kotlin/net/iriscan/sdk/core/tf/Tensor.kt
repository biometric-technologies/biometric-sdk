package net.iriscan.sdk.core.tf

/**
 * @author Slava Gornostal
 */
interface Tensor {
    fun getType(): TensorType
    fun getShape(): IntArray
}

interface TypedTensor<T>

interface InputTensor<T > : TypedTensor<T> {
    fun putData(value: T)
    fun putArrayData(value: Array<T>)
}

interface OutputTensor<T : Any> : TypedTensor<T> {
    fun getData(): T
    fun getArrayData(size: Int): Array<T>
}

expect class TensorImpl : Tensor

expect class IntInputTensor : InputTensor<Int>
expect class FloatInputTensor : InputTensor<Float>

expect class IntOutputTensor : OutputTensor<Int>
expect class FloatOutputTensor : OutputTensor<Float>