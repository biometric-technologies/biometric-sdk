package net.iriscan.sdk.core.tf

import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.tensorflowlite.TfLiteTensor

/**
 * @author Slava Gornostal
 */
actual class IntInputTensor(private val tensor: TfLiteTensor, private val pointer: IntPointer) :
    Tensor by TensorImpl(tensor), InputTensor<Int> {
    override fun putData(value: Int) {
        pointer.put(value)
    }

    override fun putArrayData(value: Array<Int>) {
        pointer.put(value.toIntArray(), 0, value.size)
    }
}

actual class FloatInputTensor(private val tensor: TfLiteTensor, private val pointer: FloatPointer) :
    Tensor by TensorImpl(tensor), InputTensor<Float> {
    override fun putData(value: Float) {
        pointer.put(value)
    }

    override fun putArrayData(value: Array<Float>) {
        pointer.put(value.toFloatArray(), 0, value.size)
    }
}