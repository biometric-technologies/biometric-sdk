package net.iriscan.sdk.io

/**
 * @author Slava Gornostal
 *
 * Input/Output operations
 * All functions should be thread-safe
 *
 * @see InputOutputBiometricOperations
 * @see InputOutputImageOperations
 */
interface InputOutputOperations : InputOutputBiometricOperations, InputOutputImageOperations
