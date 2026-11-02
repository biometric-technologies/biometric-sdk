package net.iriscan.sdk.iris

import net.iriscan.sdk.core.image.Image

/**
 * Iris image quality assessment.
 *
 * The metrics follow the components of ISO/IEC 29794-6 (usable iris area,
 * contrasts, grey scale utilisation, pupil/iris geometry, margins, sharpness).
 * Formulas are documented on [IrisQuality]; this is not a certified
 * implementation of the standard, and thresholds should be confirmed on your
 * own sensors.
 */
interface IrisQualityAssessor {
    /**
     * Assesses an eye image.
     * @return metrics, or null when no iris is found (failure to segment)
     */
    fun assess(sample: Image): IrisQuality?
}

/**
 * @property usableIrisArea share of the iris annulus not covered by eyelids, eyelashes or reflections, 0-100
 * @property irisScleraContrast 100 * (S - I) / (S + I), medians of sclera and outer iris in the lateral sectors
 * @property irisPupilContrast 100 * (I - P) / (I + P), medians of inner iris and pupil
 * @property greyScaleUtilisation entropy of the image histogram, bits (0-8)
 * @property irisRadius limbus radius, pixels
 * @property pupilIrisRatio 100 * pupil radius / iris radius
 * @property irisPupilConcentricity 100 * (1 - distance between centers / iris radius)
 * @property marginAdequacy 100 * worst margin relative to 0.6 iris radius (left, right) and 0.2 iris radius (top, bottom), capped at 100
 * @property sharpness focus score 0-100 from the high-frequency power of the iris region (Daugman's 8x8 kernel)
 * @property score combined quality 0-100: usable area reduced by every other metric that is out of range
 * @property issues metrics that fail the thresholds of [IrisQualityProperties]
 */
data class IrisQuality(
    val usableIrisArea: Double,
    val irisScleraContrast: Double,
    val irisPupilContrast: Double,
    val greyScaleUtilisation: Double,
    val irisRadius: Double,
    val pupilIrisRatio: Double,
    val irisPupilConcentricity: Double,
    val marginAdequacy: Double,
    val sharpness: Double,
    val score: Int,
    val issues: List<IrisQualityIssue>,
) {
    val isAcceptable: Boolean get() = issues.isEmpty()
}

enum class IrisQualityIssue {
    LOW_USABLE_IRIS_AREA,
    LOW_IRIS_SCLERA_CONTRAST,
    LOW_IRIS_PUPIL_CONTRAST,
    LOW_GREY_SCALE_UTILISATION,
    SMALL_IRIS,
    PUPIL_TOO_SMALL_OR_LARGE,
    LOW_CONCENTRICITY,
    IRIS_TOO_CLOSE_TO_EDGE,
    OUT_OF_FOCUS,
}

/**
 * Acceptance thresholds. Defaults follow the values commonly used with
 * ISO/IEC 29794-6; adjust them for your capture devices.
 */
class IrisQualityProperties(
    val minUsableIrisArea: Double = 70.0,
    val minIrisScleraContrast: Double = 5.0,
    val minIrisPupilContrast: Double = 30.0,
    val minGreyScaleUtilisation: Double = 6.0,
    val minIrisRadius: Double = 75.0,
    val pupilIrisRatioRange: ClosedRange<Double> = 20.0..70.0,
    val minIrisPupilConcentricity: Double = 90.0,
    val minMarginAdequacy: Double = 80.0,
    val minSharpness: Double = 50.0,
)
