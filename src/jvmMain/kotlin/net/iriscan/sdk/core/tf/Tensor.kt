package net.iriscan.sdk.core.tf

import org.bytedeco.tensorflowlite.TfLiteTensor

/**
 * @author Slava Gornostal
 */
actual class TensorImpl(private val tensor: TfLiteTensor) : Tensor {
    override fun getType(): TensorType = when (tensor.type()) {
        0 -> TensorType.NoType
        1 -> TensorType.Float32
        2 -> TensorType.Int32
        3 -> TensorType.UInt8
        4 -> TensorType.Int64
        5 -> TensorType.String
        6 -> TensorType.Bool
        7 -> TensorType.Int16
        8 -> TensorType.Complex64
        9 -> TensorType.Int8
        10 -> TensorType.Float16
        11 -> TensorType.Float64
        12 -> TensorType.Complex128
        13 -> TensorType.UInt64
        14 -> TensorType.Resource
        15 -> TensorType.Variant
        16 -> TensorType.UInt32
        17 -> TensorType.UInt16
        else -> TensorType.Unknown
    }

    override fun getShape(): IntArray {
        val dims = tensor.dims()
        return IntArray(dims.size()) { i -> tensor.dims().data(i) }
    }

}