package net.iriscan.sdk.finger

/**
 * @property dpi resolution of the images given to [FingerEncoder.extractAndEncode] without a dpi.
 * Images are scaled to 500 dpi before extraction, so a wrong value lowers the scores.
 */
class FingerEncodeProperties(val dpi: Double = 500.0) {
    init {
        require(dpi in 20.0..20_000.0) { "dpi must be between 20 and 20000" }
    }
}

/**
 * @property threshold score from which two templates count as the same finger. The SourceAFIS
 * scale: 40 is a false match rate of 0.01%, 30 of 0.1%, 20 of 1%.
 */
class FingerMatchProperties(val threshold: Double = 40.0) {
    init {
        require(threshold >= 0) { "threshold must not be negative" }
    }
}
