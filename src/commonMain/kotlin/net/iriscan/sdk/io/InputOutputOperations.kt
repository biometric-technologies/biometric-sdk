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
 * @see InputOutputImageConvertOperations
 */
interface InputOutputOperations : InputOutputRecordOperations, InputOutputImageOperations,
    InputOutputImageConvertOperations

internal class InputOutputOperationsImpl :
    InputOutputImageOperations by InputOutputImageOperationsImpl(),
    InputOutputImageConvertOperations by InputOutputImageConvertOperationsImpl(),
    InputOutputRecordOperations by InputOutputRecordOperationsImpl(),
    InputOutputOperations
