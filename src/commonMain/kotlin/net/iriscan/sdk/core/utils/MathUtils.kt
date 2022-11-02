package net.iriscan.sdk.core.utils

/**
 * @author Slava Gornostal
 */
internal object Math {

    private const val ANG_TO_DEGREE = 57.29577951308232
    const val PI = 3.141592653589793

    internal inline fun toDegrees(angrad: Double): Double = angrad * ANG_TO_DEGREE
}
