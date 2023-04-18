package net.iriscan.sdk.core.tf

import cocoapods.TFLTensorFlowLite.TFLInterpreter
import cocoapods.TFLTensorFlowLite.TFLInterpreterOptions
import io.github.aakira.napier.Napier
import net.iriscan.sdk.SdkInitializeException
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.ResourceHelperFactory
import net.iriscan.sdk.utils.NSErrorException
import net.iriscan.sdk.utils.throwError
import platform.Foundation.NSProcessInfo

actual class InterpreterImpl actual constructor(
    private val modelPath: String,
    private val modelChecksum: Int
) : Interpreter {

    private val interpreter: TFLInterpreter = try {
        getInterpreter()
    } catch (e: NSErrorException) {
        Napier.e("Interpreter initialize error", e)
        throw SdkInitializeException("Could not initialize interpreter: ${e.message}")
    }

    private fun getInterpreter() =
        throwError { errorPtr ->
            val options = TFLInterpreterOptions()
            options.numberOfThreads = NSProcessInfo.processInfo.activeProcessorCount()
            options.useXNNPACK = true
            val modelUrl = ResourceHelperFactory.getInstance()
                .cacheAndGetPath("tflite.model", modelPath, modelChecksum)
            TFLInterpreter(modelUrl, options, errorPtr)
        }

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
        try {
            doInvoke(inputs, outputs)
        } catch (e: NSErrorException) {
            Napier.e("Interpreter invoke error", e)
            throw RuntimeException("Error performing tflite invoke function: ${e.message}")
        }
    }

    private fun doInvoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>) {
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