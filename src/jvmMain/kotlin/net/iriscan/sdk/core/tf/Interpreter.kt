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

    private val interpreter: org.bytedeco.tensorflowlite.Interpreter =
        org.bytedeco.tensorflowlite.Interpreter(null as Pointer?)

    init {
        InterpreterBuilder(
            FlatBufferModel.BuildFromBuffer(BytePointer(ByteBuffer.wrap(model)), model.size.toLong()),
            BuiltinOpResolver()
        ).apply(interpreter)
    }

    override fun allocateTensors() {
        interpreter.AllocateTensors()
            .tfIfErrorThrow("Could not allocate tensors")
    }

    override fun invoke() {
        interpreter.Invoke()
            .tfIfErrorThrow("Could not invoke model")
    }

    override fun getTensor(index: Int): Tensor = TensorImpl(interpreter.tensor(index))

    override fun getInputTensorInt(index: Int): IntInputTensor =
        IntInputTensor(interpreter.input_tensor(index.toLong()), interpreter.typed_input_tensor_int(index))

    override fun getInputTensorFloat(index: Int): FloatInputTensor =
        FloatInputTensor(interpreter.input_tensor(index.toLong()), interpreter.typed_input_tensor_float(index))

    override fun getOutputTensorInt(index: Int): IntOutputTensor =
        IntOutputTensor(interpreter.output_tensor(index.toLong()), interpreter.typed_output_tensor_int(index))

    override fun getOutputTensorFloat(index: Int): FloatOutputTensor =
        FloatOutputTensor(interpreter.output_tensor(index.toLong()), interpreter.typed_output_tensor_float(index))

}