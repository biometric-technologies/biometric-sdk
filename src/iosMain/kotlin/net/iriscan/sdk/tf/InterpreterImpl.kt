package net.iriscan.sdk.tf

import cocoapods.TFLTensorFlowLite.TFLInterpreter
import cocoapods.TFLTensorFlowLite.TFLInterpreterOptions
import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.io.DataBytes
import net.iriscan.sdk.core.io.HashMethod
import net.iriscan.sdk.exception.SdkInitializationException
import net.iriscan.sdk.io.ResourceIOFactory
import net.iriscan.sdk.utils.NSErrorException
import net.iriscan.sdk.utils.throwError
import platform.Foundation.NSProcessInfo

actual class InterpreterImpl actual constructor(
    modelName: String,
    modelPath: String,
    modelChecksum: String?,
    modelChecksumMethod: HashMethod?,
    overrideCacheOnWrongChecksum: Boolean?
) : Interpreter {

    private val interpreter: TFLInterpreter

    init {
        interpreter = try {
            throwError { errorPtr ->
                val options = TFLInterpreterOptions()
                options.numberOfThreads = NSProcessInfo.processInfo.activeProcessorCount()
                options.useXNNPACK = true
                val modelUrl =
                    if (modelChecksum != null && modelChecksumMethod != null && overrideCacheOnWrongChecksum != null) {
                        ResourceIOFactory.getInstance()
                            .readOrCacheLoadPath(
                                modelName,
                                modelPath,
                                modelChecksum,
                                modelChecksumMethod,
                                overrideCacheOnWrongChecksum
                            )
                    } else {
                        ResourceIOFactory.getInstance()
                            .readOrCacheLoadPath(modelName, modelPath)
                    }
                TFLInterpreter(modelUrl, options, errorPtr)
            }
        } catch (e: NSErrorException) {
            Napier.e("Interpreter initialize error", e)
            throw SdkInitializationException("Could not initialize interpreter: ${e.message}", e)
        }
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