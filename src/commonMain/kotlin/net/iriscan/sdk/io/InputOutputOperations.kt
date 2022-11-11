package net.iriscan.sdk.io

import net.iriscan.sdk.io.image.impl.InputOutputImageOperationsImpl
import net.iriscan.sdk.io.record.impl.InputOutputRecordOperationsImpl

/**
 * @author Slava Gornostal
 *
 * Input/Output operations
 * All functions are thread-safe
 *
 * @see InputOutputRecordOperations
 * @see InputOutputImageOperations
 */
interface InputOutputOperations : InputOutputRecordOperations, InputOutputImageOperations

internal class InputOutputOperationsImpl :
    InputOutputImageOperations by InputOutputImageOperationsImpl(),
    InputOutputRecordOperations by InputOutputRecordOperationsImpl(),
    InputOutputOperations
