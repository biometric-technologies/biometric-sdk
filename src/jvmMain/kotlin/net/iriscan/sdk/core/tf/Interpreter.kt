package net.iriscan.sdk.core.tf

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.tensorflowlite.BuiltinOpResolver
import org.bytedeco.tensorflowlite.FlatBufferModel
import org.bytedeco.tensorflowlite.InterpreterBuilder
import java.nio.ByteBuffer

/**
 * @author Slava Gornostal
 */
actual class InterpreterImpl actual constructor(model: ByteArray) : Interpreter {

    private val builder = InterpreterBuilder(
        FlatBufferModel.BuildFromBuffer(BytePointer(ByteBuffer.wrap(model)), model.size.toLong()),
        BuiltinOpResolver()
    )

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        val interpreter = org.bytedeco.tensorflowlite.Interpreter(null as Pointer?)
        builder.apply(interpreter)
        inputs.keys.forEach {
            when (val data = inputs[it]) {
                is Int -> interpreter.typed_input_tensor_int(it).put(data)
                is Float -> interpreter.typed_input_tensor_float(it).put(data)
                is IntArray -> interpreter.typed_input_tensor_int(it).put(data, 0, data.size)
                is FloatArray -> interpreter.typed_input_tensor_float(it).put(data, 0, data.size)
            }
        }
        interpreter.Invoke()
            .tfIfErrorThrow("Could not invoke model")
        outputs.keys.forEach {
            outputs[it] = when (val out = outputs[it]) {
                is Int -> interpreter.typed_output_tensor_int(it).get()
                is Float -> interpreter.typed_output_tensor_float(it).get()
                is IntArray -> {
                    val ptr = interpreter.typed_output_tensor_int(it)
                    IntArray(out.size) { i -> ptr.get(i.toLong()) }
                }

                is FloatArray -> {
                    val ptr = interpreter.typed_output_tensor_float(it)
                    FloatArray(out.size) { i -> ptr.get(i.toLong()) }
                }

                else -> throw IllegalArgumentException("Unsupported tensor type")
            }
        }
        interpreter.close()
    }

}