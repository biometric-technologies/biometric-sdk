package net.iriscan.sdk.core.tf

import net.iriscan.sdk.SdkNotInitializedException
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.tensorflowlite.BuiltinOpResolver
import org.bytedeco.tensorflowlite.FlatBufferModel
import org.bytedeco.tensorflowlite.InterpreterBuilder
import java.nio.ByteBuffer

/**
 * @author Slava Gornostal
 */
actual class InterpreterImpl actual constructor(modelPath: String, modelChecksum: Int) : Interpreter {

    private val interpreter = org.bytedeco.tensorflowlite.Interpreter(null as Pointer?)

    init {
        val modelBytes = javaClass.getResourceAsStream(modelPath)?.readBytes()
            ?: throw SdkNotInitializedException("Model $modelPath not found")
        val builder = InterpreterBuilder(
            FlatBufferModel.BuildFromBuffer(BytePointer(ByteBuffer.wrap(modelBytes)), modelBytes.size.toLong()),
            BuiltinOpResolver()
        )
        builder.apply(interpreter)
    }

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        inputs.keys.forEach {
            val data = inputs[it]!! as ByteArray
            interpreter.typed_input_tensor_byte(it).put(data, 0, data.size)
        }
        interpreter.Invoke()
            .tfIfErrorThrow("Could not invoke model")
        outputs.keys.forEach {
            val out = outputs[it]!! as ByteArray
            interpreter.typed_output_tensor_byte(it).get(out, 0, out.size)
        }
        interpreter.close()
    }

}