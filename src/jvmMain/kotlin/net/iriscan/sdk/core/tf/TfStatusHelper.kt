package net.iriscan.sdk.core.tf

/**
 * @author Slava Gornostal
 */
internal fun Int.tfIfErrorThrow(message: String) = when (this) {
    0 -> {}
    1 -> throw TensorflowException("$message: error in the runtime (i.e. interpreter)")
    2 -> throw TensorflowException("$message: error from a TfLiteDelegate itself")
    3 -> throw TensorflowException("$message: incompatibility between runtime and delegate, e.g.")
    4 -> throw TensorflowException("$message: serialized delegate data not being found")
    5 -> throw TensorflowException("$message: data-writing issues in delegate serialization")
    6 -> throw TensorflowException("$message: data-reading issues in delegate serialization")
    7 -> throw TensorflowException(
        "$message: issues when the TF Lite model has ops that cannot be resolved in runtime"
    )

    else -> throw TensorflowException("$message: unknown exception status: $this")
}