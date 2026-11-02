package net.iriscan.sdk.iris

/**
 * @author Slava Gornostal
 *
 * Iris segmentation and normalization settings.
 *
 * Segmentation uses Daugman's integro-differential operator: the pupil and the
 * iris (limbus) boundaries are searched as circles with independent centers.
 * The iris region is then unwrapped with the rubber-sheet model to a
 * [normalizedWidth] x [normalizedHeight] texture. Occluded samples (eyelids,
 * eyelashes, reflections, image borders) are marked with -1 in the texture.
 *
 * Radii are in pixels of the input image; null means "derive from image size".
 */
class IrisExtractProperties(
    /** Segmentation runs on a copy downscaled so that its shorter side is about this size. */
    val workingSize: Int = 240,
    val minPupilRadius: Int? = null, // defaults to 0.03 * min(w, h)
    val maxPupilRadius: Int? = null, // defaults to 0.22 * min(w, h)
    val minIrisRadius: Int? = null, // defaults to 0.12 * min(w, h)
    val maxIrisRadius: Int? = null, // defaults to 0.60 * min(w, h)
    /** Allowed pupil/iris radius ratio. */
    val minPupilIrisRatio: Double = 0.15,
    val maxPupilIrisRatio: Double = 0.75,
    /** Angular sectors (degrees, 0 = right, 90 = down) used to find the limbus; avoids eyelids. */
    val irisAnglesToSearch: Array<ClosedRange<Double>> = arrayOf(-40.0..40.0, 140.0..220.0),
    /** Unwrapped texture size. Width must be a power of two. */
    val normalizedWidth: Int = 256,
    val normalizedHeight: Int = 64,
    /** Detect upper and lower eyelids and mask the iris area they cover. */
    val detectEyelids: Boolean = true,
    /** Samples further than this many robust standard deviations from the iris median are masked. */
    val outlierSigma: Double = 3.0,
) {
    init {
        require(normalizedWidth >= 64 && normalizedWidth and (normalizedWidth - 1) == 0) {
            "normalizedWidth must be a power of two >= 64"
        }
        require(normalizedHeight >= 8)
        require(workingSize >= 80)
    }

    companion object
}

/**
 * Iris encoding settings.
 *
 * Each row of the unwrapped texture is filtered with a 1D log-Gabor filter and
 * the phase of the response is quantized to two bits (sign of the real and of
 * the imaginary part). Bits from occluded areas and fragile bits (responses
 * close to zero) are excluded through a mask stored in the template.
 */
class IrisEncodeProperties(
    /** Template rows: radial bands, averaged from the unwrapped texture. */
    val templateRows: Int = 16,
    /** Template columns: angular positions. Must be a multiple of 32. */
    val templateCols: Int = 128,
    /** Log-Gabor center wavelength in pixels of the unwrapped texture. */
    val wavelength: Double = 18.0,
    /** Log-Gabor bandwidth parameter sigma/f0. */
    val sigmaOnF: Double = 0.5,
    /** Share of the weakest responses masked as fragile bits, per bit plane. */
    val fragileBitFraction: Double = 0.15,
    @Deprecated("Not used by the v2 encoder") val templateWidth: Int = 32,
    @Deprecated("Not used by the v2 encoder") val templateHeight: Int = 4,
) {
    init {
        require(templateCols % 32 == 0) { "templateCols must be a multiple of 32" }
        require(templateRows in 1..255 && templateCols in 32..2048)
        require(wavelength > 2.0)
        require(sigmaOnF in 0.1..0.95)
        require(fragileBitFraction in 0.0..0.5)
    }

    companion object
}

/**
 * Iris matching settings.
 *
 * Templates are compared with the masked fractional Hamming distance (HD),
 * minimized over circular shifts that compensate eye rotation, and normalized
 * for the number of bits compared (Daugman):
 * HDnorm = 0.5 - (0.5 - HDraw) * sqrt(n / [normalizationBits]).
 * The similarity score reported by the matcher is 1 - HDnorm.
 */
class IrisMatchProperties(
    /** Similarity threshold (1 - HD). 0.67 corresponds to HD 0.33. Calibrate on your own data. */
    val threshold: Double = 0.67, // 0.0-1.0
    /** Maximum rotation compensation, in template columns (each column = 360 / templateCols degrees). */
    val maxShift: Int = 8,
    /** Minimum share of bits that must be valid in both templates for a comparison to count. */
    val minComparedBitsFraction: Double = 0.15,
    /** Reference number of compared bits for score normalization; null = 40% of template bits. */
    val normalizationBits: Int? = null,
    /** Threshold used for templates created by SDK versions before 0.7 (byte-equality score). */
    val legacyThreshold: Double = 0.7,
) {
    init {
        require(threshold in 0.0..1.0)
        require(maxShift >= 0)
        require(minComparedBitsFraction in 0.0..1.0)
    }
}
