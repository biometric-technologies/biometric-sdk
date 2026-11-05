package net.iriscan.sdk.tf

import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.io.ResourceIOFactory
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.tensorflowlite.BuiltinOpResolver
import org.bytedeco.tensorflowlite.FlatBufferModel
import org.bytedeco.tensorflowlite.InterpreterBuilder
import org.bytedeco.tensorflowlite.TfLiteTensor
import org.bytedeco.tensorflowlite.global.tensorflowlite.TfLiteTypeGetName
import org.bytedeco.tensorflowlite.global.tensorflowlite.kTfLiteFloat32
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

    /**
     * Copies [inputs] into the model's input tensors, runs the model and copies its output
     * tensors into [outputs] (a [FloatArray] is filled, a [Float] is replaced by the first value).
     *
     * @throws IllegalArgumentException before anything is copied, when a tensor is not float32 or
     * an input or output [FloatArray] does not have exactly as many values as its tensor, that is
     * when the sizes in the model configuration do not match the model
     */
    @Synchronized
    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>, traceId: String?) {
        // JavaCPP's put and get copy as many values as the array has, whatever the size of the
        // tensor behind the pointer. Without these checks a larger input was written past the
        // input tensor into native memory, a smaller one left the rest of the tensor from the
        // previous call, and an output array of another length came back cut short or with values
        // read from past the tensor. Android's TensorFlow Lite API and iOS copyData check the
        // sizes themselves.
        inputs.forEach { (index, data) ->
            requireFloatTensor("input", index, interpreter.input_tensor(index.toLong()), (data as FloatArray).size)
        }
        outputs.forEach { (index, out) ->
            if (out is FloatArray) {
                requireFloatTensor("output", index, interpreter.output_tensor(index.toLong()), out.size)
            }
        }
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

    /**
     * Fails unless [tensor] is float32 and holds exactly [values] values, the number the SDK
     * copies. The message gives the model's shape, so the configuration can be corrected from it.
     */
    private fun requireFloatTensor(role: String, index: Int, tensor: TfLiteTensor, values: Int) {
        // typed_input_tensor_float and typed_output_tensor_float return null for other types
        require(tensor.type() == kTfLiteFloat32) {
            "Model $role $index is ${TfLiteTypeGetName(tensor.type()).string}, " +
                    "but the SDK exchanges only float32 values with a model"
        }
        val dims = tensor.dims()
        val shape = IntArray(dims.size()) { dims.data(it) }
        val modelValues = shape.fold(1L) { count, dim -> count * dim }
        require(modelValues == values.toLong()) {
            "Model $role $index holds $modelValues float32 values (shape ${shape.contentToString()}), " +
                    "but the SDK ${if (role == "input") "passes" else "reads"} $values: " +
                    "the sizes in the model configuration do not match the model"
        }
    }
}
