package net.iriscan.sdk.core.tf

import net.iriscan.sdk.core.io.ResourceHelperFactory
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class InterpreterImpl actual constructor(modelPath: String) : Interpreter {
    private val interpreter: org.tensorflow.lite.Interpreter

    init {
        val model = ResourceHelperFactory.getInstance()
            .cacheAndRead("tflite.model", modelPath)
        val modelBuffer = ByteBuffer.allocateDirect(model.size)
        modelBuffer.order(ByteOrder.nativeOrder())
        modelBuffer.put(model)
        val options = org.tensorflow.lite.Interpreter.Options().apply {
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                this.addDelegate(GpuDelegate())
            } else {
                this.numThreads = Runtime.getRuntime().availableProcessors()
            }
        }
        interpreter = org.tensorflow.lite.Interpreter(modelBuffer, options)
    }

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        interpreter.runForMultipleInputsOutputs(Array(inputs.size) { i -> inputs[i] }, outputs)
    }
}