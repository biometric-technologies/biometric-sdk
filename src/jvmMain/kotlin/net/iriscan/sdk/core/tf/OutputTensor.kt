package net.iriscan.sdk.core.tf

import org.bytedeco.javacpp.FloatPointer
import org.bytedeco.javacpp.IntPointer

/**
 * @author Slava Gornostal
 */
actual class IntOutputTensor(private val tensor: IntPointer) : OutputTensor<Int> {
    override fun getData(): Int = tensor.get(0)
    override fun getArrayData(size: Int): Array<Int> =
        Array(size) { i -> tensor.get(i.toLong()) }
}

actual class FloatOutputTensor(private val tensor: FloatPointer) : OutputTensor<Float> {
    override fun getData(): Float = tensor.get(0)
    override fun getArrayData(size: Int): Array<Float> =
        Array(size) { i -> tensor.get(i.toLong()) }
}