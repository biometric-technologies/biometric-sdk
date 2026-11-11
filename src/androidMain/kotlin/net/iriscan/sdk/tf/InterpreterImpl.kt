package net.iriscan.sdk.tf

import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.io.ResourceIOFactory
import org.tensorflow.lite.InterpreterApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs a TensorFlow Lite model with the Android TensorFlow Lite runtime.
 *
 * @param interpreter the loaded model; the face encoder and the photo liveness check keep one
 * for all their calls
 */
internal class InterpreterImpl(private val interpreter: InterpreterApi) : Interpreter {

    /** A model loaded through ResourceIO: assets, the classpath, a file or a cached download. */
    constructor(
        modelName: String,
        modelPath: String,
        modelChecksum: String?,
        modelChecksumMethod: HashMethod?,
        overrideCacheOnWrongChecksum: Boolean?
    ) : this(loadInterpreter(modelName, modelPath, modelChecksum, modelChecksumMethod, overrideCacheOnWrongChecksum))

    /**
     * Synchronized because a TensorFlow Lite interpreter is not thread-safe (the Javadoc of
     * `org.tensorflow.lite.Interpreter` says so). One call copies its input into the input tensor,
     * runs the model and copies the output tensor out; a call from another thread in between
     * overwrites the input or runs the model at the same time, and one of the two calls returns
     * the result for the other call's image, or the native code fails. The JVM implementation is
     * synchronized for the same reason.
     */
    @Synchronized
    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>, traceId: String?) {
        interpreter.runForMultipleInputsOutputs(Array(inputs.size) { i -> inputs[i] }, outputs)
    }
}

private fun loadInterpreter(
    modelName: String,
    modelPath: String,
    modelChecksum: String?,
    modelChecksumMethod: HashMethod?,
    overrideCacheOnWrongChecksum: Boolean?
): InterpreterApi {
    val model = if (modelChecksum != null && modelChecksumMethod != null && overrideCacheOnWrongChecksum != null) {
        ResourceIOFactory.getInstance()
            .readOrCacheLoadData(
                modelName,
                modelPath,
                modelChecksum,
                modelChecksumMethod,
                overrideCacheOnWrongChecksum
            )
    } else {
        ResourceIOFactory.getInstance()
            .readOrCacheLoadData(modelName, modelPath)
    }
    val modelBuffer = ByteBuffer.allocateDirect(model.size)
    modelBuffer.order(ByteOrder.nativeOrder())
    modelBuffer.put(model)
    val options = org.tensorflow.lite.Interpreter.Options().apply {
        this.numThreads = Runtime.getRuntime().availableProcessors()
    }
    return org.tensorflow.lite.Interpreter(modelBuffer, options)
}
