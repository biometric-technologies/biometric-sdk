package net.iriscan.sdk.core.tf

import cocoapods.TFLTensorFlowLite.TFLInterpreter
import cocoapods.TFLTensorFlowLite.TFLInterpreterOptions
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.ResourceHelperFactory
import net.iriscan.sdk.utils.throwError
import platform.Foundation.NSProcessInfo

actual class InterpreterImpl actual constructor(modelPath: String, modelChecksum: Int) : Interpreter {

    private val interpreter: TFLInterpreter = throwError { errorPtr ->
        val options = TFLInterpreterOptions()
        options.numberOfThreads = NSProcessInfo.processInfo.activeProcessorCount()
        options.useXNNPACK = true
        val modelUrl = ResourceHelperFactory.getInstance()
            .cacheAndGetPath("tflite.model", modelPath, modelChecksum)
        TFLInterpreter(modelUrl, options, errorPtr)
    }

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        throwError { errorPointer ->
            interpreter.allocateTensorsWithError(errorPointer)
            inputs.forEach {
                interpreter.inputTensorAtIndex(it.key.toULong(), errorPointer)!!
                    .copyData(it.value as DataBytes, errorPointer)
            }
            interpreter.invokeWithError(errorPointer)
            outputs.keys.forEach {
                outputs[it] = interpreter.outputTensorAtIndex(it.toULong(), errorPointer)!!
                    .dataWithError(errorPointer)!!
            }
        }
    }
}