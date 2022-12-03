package net.iriscan.sdk.core.tf

import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.tensorflowlite.TfLiteTensor

/**
 * @author Slava Gornostal
 */
actual class IntOutputTensor(private val tensor: TfLiteTensor, private val pointer: IntPointer) :
    Tensor by TensorImpl(tensor), OutputTensor<Int> {
    override fun getData(): Int = pointer.get(0)
    override fun getArrayData(): Array<Int> {
        val size = getShape().reduce { acc, el -> acc * el }
        return Array(size) { i -> pointer.get(i.toLong()) }
    }
}

actual class FloatOutputTensor(private val tensor: TfLiteTensor, private val pointer: FloatPointer) :
    Tensor by TensorImpl(tensor), OutputTensor<Float> {
    override fun getData(): Float = pointer.get(0)
    override fun getArrayData(): Array<Float> {
        val size = getShape().reduce { acc, el -> acc * el }
        return Array(size) { i -> pointer.get(i.toLong()) }
    }
}