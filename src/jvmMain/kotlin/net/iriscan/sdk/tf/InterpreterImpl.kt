package net.iriscan.sdk.tf

import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.io.ResourceIOFactory
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.tensorflowlite.BuiltinOpResolver
import org.bytedeco.tensorflowlite.FlatBufferModel
import org.bytedeco.tensorflowlite.InterpreterBuilder
import java.nio.ByteBuffer

/**
 * @author Slava Gornostal
 */
internal class InterpreterImpl(
    modelName: String,
    modelPath: String,
    modelChecksum: String?,
    modelChecksumMethod: HashMethod?,
    overrideCacheOnWrongChecksum: Boolean?
) : Interpreter {

    private val model: BytePointer
    private val modelLen: Long

    init {
        val modelBytes =
            if (modelChecksum != null && modelChecksumMethod != null && overrideCacheOnWrongChecksum != null) {
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
        model = BytePointer(ByteBuffer.wrap(modelBytes))
        modelLen = modelBytes.size.toLong()
    }

    // The model and interpreter are built once; rebuilding them on every call was slow and
    // leaked the native FlatBufferModel. TFLite interpreters are not thread-safe, so calls are serialized.
    private val flatBufferModel = FlatBufferModel.BuildFromBuffer(model, modelLen)
    private val interpreter: org.bytedeco.tensorflowlite.Interpreter by lazy {
        val interpreter = org.bytedeco.tensorflowlite.Interpreter(null as Pointer?)
        val modelBuilder = InterpreterBuilder(flatBufferModel, BuiltinOpResolver())
        modelBuilder.apply(interpreter, Runtime.getRuntime().availableProcessors())
        modelBuilder.close()
        interpreter.AllocateTensors()
        interpreter
    }

    @Synchronized
    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>, traceId: String?) {
        inputs.keys.forEach {
            val data = inputs[it]!! as FloatArray
            interpreter.typed_input_tensor_float(it)
                .put(data, 0, data.size)
        }
        interpreter.Invoke()
            .tfIfErrorThrow("Could not invoke model")
        outputs.keys.forEach {
            when (val out = outputs[it]!!) {
                is Float -> {
                    outputs[it] = interpreter.typed_output_tensor_float(it)
                        .get()
                }

                is FloatArray -> {
                    interpreter.typed_output_tensor_float(it)
                        .get(out)
                }
            }
        }
    }

}
