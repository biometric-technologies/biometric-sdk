package net.iriscan.sdk.core.tf

import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.core.io.ResourceHelperFactory
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.tensorflowlite.BuiltinOpResolver
import org.bytedeco.tensorflowlite.FlatBufferModel
import org.bytedeco.tensorflowlite.InterpreterBuilder
import java.nio.ByteBuffer

/**
 * @author Slava Gornostal
 */
actual class InterpreterImpl actual constructor(modelName: String,
                                                modelPath: String,
                                                modelChecksum: String?,
                                                modelChecksumMethod: HashMethod?,
                                                overrideCacheOnWrongChecksum: Boolean?) : Interpreter {

    private val interpreter = org.bytedeco.tensorflowlite.Interpreter(null as Pointer?)

    init {
        val modelBytes = // TODO
        val builder = InterpreterBuilder(
            FlatBufferModel.BuildFromBuffer(BytePointer(ByteBuffer.wrap(modelBytes)), modelBytes.size.toLong()),
            BuiltinOpResolver()
        )
        builder.apply(interpreter)
        interpreter.AllocateTensors()
    }

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        inputs.keys.forEach {
            val data = inputs[it]!! as ByteArray
            interpreter.input_tensor(it.toLong())
                .data()
                .raw()
                .put(data, 0, data.size)
        }
        interpreter.Invoke()
            .tfIfErrorThrow("Could not invoke model")
        outputs.keys.forEach {
            val out = outputs[it]!! as ByteArray
            interpreter.output_tensor(it.toLong())
                .data()
                .raw()
                .get(out)
        }
        interpreter.close()
    }

}