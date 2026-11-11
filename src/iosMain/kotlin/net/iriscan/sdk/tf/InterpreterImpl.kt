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
import net.iriscan.sdk.utils.withLock
import platform.Foundation.NSLock
import platform.Foundation.NSProcessInfo

internal class InterpreterImpl(
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

    /**
     * A TFLInterpreter is not thread-safe ("Interpreter instances are *not* thread-safe",
     * TFLInterpreter.h). [doInvoke] copies the inputs into the input tensors, runs the model and
     * reads the output tensors; a call from another thread in between overwrites the tensors or
     * runs the model at the same time, and one of the two calls returns the result for the other
     * call's image, or the native code fails. So calls run one at a time, as on the JVM and Android.
     */
    private val invokeLock = NSLock()

    override fun invoke(inputs: Map<Int, Any>, outputs: MutableMap<Int, Any>, traceId: String?) {
        invokeLock.withLock {
            try {
                doInvoke(inputs, outputs)
            } catch (e: NSErrorException) {
                Napier.e(tag = traceId, throwable = e) { "Interpreter invoke error" }
                throw RuntimeException("Error performing tflite invoke function: ${e.message}")
            }
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