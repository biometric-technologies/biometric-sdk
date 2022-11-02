package net.iriscan.sdk.iris

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties

/**
 * @author Slava Gornostal
 */
class IrisExtractProperties(
    // skip edges
    val verticalOffsetStart: Int? = null, // defaults to w / 5
    val verticalOffsetEnd: Int? = null, // defaults to w / 5
    val horizontalOffsetStart: Int? = null, // defaults to h / 5
    val horizontalOffsetEnd: Int? = null, // defaults to h / 5
    // center
    val centerOffsetStep: Int = 6,
    // pupil
    val minPupilRadius: Int? = null, // defaults to 0.05 * min(w, h)
    val maxPupilRadius: Int? = null, // defaults to 5 * min pupil radius
    val pupilRadiusStep: Int = 3,
    val pupilRadiusCalculationSteps: Int = 2,
    val pupilAnglesToSearch: Array<ClosedRange<Double>> = arrayOf(10.0..160.0, 190.0..350.0),
    // iris
    val minIrisRadius: Int? = null, // defaults to 1.25 * pupil radius
    val maxIrisRadius: Int? = null, // defaults to 10 * pupil radius
    val irisRadiusStep: Int = 4,
    val irisRadiusCalculationSteps: Int = 3,
    val irisAnglesToSearch: Array<ClosedRange<Double>> = arrayOf(45.0..135.0, 225.0..315.0),
) : BiometricAlgorithmProperties {

    override fun asMap(): Map<String, Any> = mapOf()
}

class IrisEncodeProperties(
    val templateWidth: Int = 32,
    val templateHeight: Int = 4,
) : BiometricAlgorithmProperties {

    override fun asMap(): Map<String, Any> = mapOf()
}

class IrisMatchProperties(
    val threshold: Double = 0.7 // 0.0-1.0
) : BiometricAlgorithmProperties {

    override fun asMap(): Map<String, Any> = mapOf()
}
