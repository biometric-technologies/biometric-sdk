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
actual class InterpreterImpl actual constructor(
    modelName: String,
    modelPath: String,
    modelChecksum: String?,
    modelChecksumMethod: HashMethod?,
    overrideCacheOnWrongChecksum: Boolean?
) : Interpreter {

    private val modelBuilder: InterpreterBuilder

    init {
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
        modelBuilder = InterpreterBuilder(
            FlatBufferModel.BuildFromBuffer(BytePointer(ByteBuffer.wrap(model)), model.size.toLong()),
            BuiltinOpResolver()
        )
    }

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        val interpreter = org.bytedeco.tensorflowlite.Interpreter(null as Pointer?)
        modelBuilder.apply(interpreter)
        interpreter.AllocateTensors()
        inputs.keys.forEach {
            val data = inputs[it]!! as FloatArray
            interpreter.typed_input_tensor_float(it)
                .put(data, 0, data.size)
        }
        interpreter.Invoke()
            .tfIfErrorThrow("Could not invoke model")
        outputs.keys.forEach {
            val out = outputs[it]!! as FloatArray
            interpreter.typed_output_tensor_float(it)
                .get(out)
        }
        interpreter.close()
    }

}