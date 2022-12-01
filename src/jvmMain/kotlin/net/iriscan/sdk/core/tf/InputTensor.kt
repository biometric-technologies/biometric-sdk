package net.iriscan.sdk.core.tf

import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.IntPointer

/**
 * @author Slava Gornostal
 */
actual class IntInputTensor(private val tensor: IntPointer) : InputTensor<Int> {
    override fun putData(value: Int) {
        tensor.put(value)
    }

    override fun putArrayData(value: Array<Int>) {
        tensor.put(value.toIntArray(), 0, value.size)
    }
}

actual class FloatInputTensor(private val tensor: FloatPointer) : InputTensor<Float> {
    override fun putData(value: Float) {
        tensor.put(value)
    }

    override fun putArrayData(value: Array<Float>) {
        tensor.put(value.toFloatArray(), 0, value.size)
    }
}